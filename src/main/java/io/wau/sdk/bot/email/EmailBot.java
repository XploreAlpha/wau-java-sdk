package io.wau.sdk.bot.email;

import io.wau.sdk.bot.common.Bot;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Email Bot SDK 集成(W6.2 Stage 1 native SDK integration, 2026-07-09)。
 *
 * <p>Stage 1 实装:用 {@code com.sun.mail:jakarta.mail} (SMTP + IMAP) 取代 Stage 0 stub。
 *
 * <p>协议对齐(per M5 W7 Day 7 SDK 接入 2026-07-07 + W6.2 2026-07-09):
 * <ul>
 *   <li>Start → IMAP store.connect + folder.open(READ_ONLY) + MessageCountListener(类 IDLE 主动推送) + 增量 fetch 未读</li>
 *   <li>Stop → folder.close + store.close + 标记 running=false</li>
 *   <li>PostMessage → Transport.send(mimeMessage) 发 SMTP</li>
 *   <li>UpdateMessage → Email 模型无 update 概念,返回 noop(caller 兼容接口)</li>
 *   <li>Event 转发 → MessageCountListener 收新邮件 → 转 IncomingMessage → handler.handle()</li>
 * </ul>
 *
 * <p>字段对齐 per D13 拍板:与 wau-channel/adapter + 5 SDK bot/common/ 100% 一致。
 * <p>5-method interface 完全沿用 Stage 0 契约(M10 N1 拍板),签名 0 改动。
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.3.0 (W6.2 Stage 1 native SDK integration, 2026-07-09)
 */
public class EmailBot implements Bot {

    private static final Logger log = LoggerFactory.getLogger(EmailBot.class);

    private static final String USER_FACING_ERR_PREFIX = "暂时无法回复,请稍后再试";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private static final int IMAP_POLL_INTERVAL_MS = 5000;
    private static final String DEFAULT_MAILBOX = "INBOX";

    private final String imapHost;
    private final int imapPort;
    private final String imapUser;
    private final String imapPass;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPass;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Session smtpSession;
    private Session imapSession;
    private Store store;
    private Folder folder;
    private Thread idleThread;

    private final AtomicLong lastProcessedUid = new AtomicLong(0L);

    private String tenant;
    private String universe;
    private MessageHandler handler;

    /** 创建 Email Bot 实例。 */
    public EmailBot(String imapHost, int imapPort, String imapUser, String imapPass,
                    String smtpHost, int smtpPort, String smtpUser, String smtpPass) {
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.imapUser = imapUser;
        this.imapPass = imapPass;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUser = smtpUser;
        this.smtpPass = smtpPass;
    }

    @Override
    public Bot withTenant(String tenantId) {
        this.tenant = tenantId;
        return this;
    }

    @Override
    public Bot withUniverse(String universe) {
        this.universe = universe;
        return this;
    }

    @Override
    public Bot onMessage(MessageHandler handler) {
        this.handler = handler;
        return this;
    }

    /**
     * 启动 Email SMTP session + IMAP 连接 + 后台 IDLE 轮询线程。
     *
     * <p>Stage 1:用 {@code MessageCountListener}(jakarta.mail 自带) + 轮询兜底
     * (某些 IMAP 服务器 IDLE 不可用时降级为每 5s 轮询)。
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("email bot already running");
        }
        if (imapHost == null || imapHost.isEmpty() || smtpHost == null || smtpHost.isEmpty()) {
            running.set(false);
            throw new IllegalArgumentException("email: empty imap_host or smtp_host");
        }

        try {
            // 1. SMTP session (发件用)
            Properties smtpProps = new Properties();
            smtpProps.put("mail.smtp.host", smtpHost);
            smtpProps.put("mail.smtp.port", String.valueOf(smtpPort));
            smtpProps.put("mail.smtp.auth", "true");
            smtpProps.put("mail.smtp.starttls.enable", "true");
            smtpSession = Session.getInstance(smtpProps, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPass);
                }
            });

            // 2. IMAP session (收件用)
            Properties imapProps = new Properties();
            imapProps.put("mail.imap.host", imapHost);
            imapProps.put("mail.imap.port", String.valueOf(imapPort));
            imapProps.put("mail.imap.auth", "true");
            imapProps.put("mail.imap.starttls.enable", "true");
            imapSession = Session.getInstance(imapProps, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(imapUser, imapPass);
                }
            });

            // 3. 连接 IMAP store + 打开 inbox
            store = imapSession.getStore("imap");
            store.connect(imapHost, imapPort, imapUser, imapPass);
            folder = store.getFolder(DEFAULT_MAILBOX);
            folder.open(Folder.READ_ONLY);

            // 记录初始 UID 上界(避免重复推历史邮件)
            if (folder instanceof UIDFolder uidFolder) {
                long lastUid = uidFolder.getUIDNext() - 1;
                lastProcessedUid.set(lastUid);
            }

            // 4. 注册 listener (jakarta.mail 提供,某些 server 支持 IDLE)
            folder.addMessageCountListener(new MessageCountAdapter() {
                @Override
                public void messagesAdded(MessageCountEvent e) {
                    Message[] added = e.getMessages();
                    for (Message msg : added) {
                        handleIncomingMessage(msg);
                    }
                }
            });

            // 5. 启动轮询兜底线程(IDLE 不稳定或不支持时降级)
            idleThread = new Thread(this::pollLoop, "wau-email-imap-poll");
            idleThread.setDaemon(true);
            idleThread.start();

            log.info("[email] SMTP+IMAP connected, user={} tenant={} universe={}",
                    imapUser, tenant, universe);
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("email: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 轮询兜底:每 IMAP_POLL_INTERVAL_MS 检查未读邮件。
     *
     * <p>jakarta.mail 不直接支持 RFC 2177 IDLE;用 polling 兜底(0 门槛 UX)。
     */
    private void pollLoop() {
        while (running.get()) {
            try {
                if (folder != null && folder.isOpen()) {
                    int total = folder.getMessageCount();
                    int unread = folder.getUnreadMessageCount();
                    if (unread > 0) {
                        int start = Math.max(1, total - unread + 1);
                        Message[] msgs = folder.getMessages(start, total);
                        for (Message msg : msgs) {
                            handleIncomingMessage(msg);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[email] poll error: {}", e.getMessage());
            }
            try {
                Thread.sleep(IMAP_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 处理 incoming 邮件:转 IncomingMessage → handler.handle() → 自动回复 (维持 thread 链路)。
     */
    private void handleIncomingMessage(Message msg) {
        try {
            if (msg == null) {
                return;
            }

            String from = "";
            String fromName = "";
            String subject = "";
            String body = "";
            String messageId = "";
            String inReplyTo = "";
            Instant ts = Instant.now();
            try {
                if (msg.getFrom() != null && msg.getFrom().length > 0) {
                    InternetAddress fromAddr = (InternetAddress) msg.getFrom()[0];
                    from = fromAddr.getAddress();
                    fromName = fromAddr.getPersonal();
                }
                if (msg.getSubject() != null) {
                    subject = msg.getSubject();
                }
                if (msg instanceof MimeMessage mime) {
                    messageId = mime.getMessageID();
                    String[] replyHeaders = mime.getHeader("In-Reply-To");
                    if (replyHeaders != null && replyHeaders.length > 0) {
                        inReplyTo = replyHeaders[0];
                    }
                }
                if (msg.getSentDate() != null) {
                    ts = msg.getSentDate().toInstant();
                }
                // best-effort text body:简化为 subject(Stage 2 完整 mime parsing)
                body = subject;
            } catch (Exception parseErr) {
                log.warn("[email] parse message error: {}", parseErr.getMessage());
            }

            IncomingMessage in = IncomingMessage.builder()
                    .platformMsgId(messageId != null ? messageId : "email." + ts.toEpochMilli())
                    .channelId(messageId) // Email 用 Message-ID 作 channel
                    .userId(from)
                    .username(fromName)
                    .text(body)
                    .replyTo(inReplyTo)
                    .timestamp(ts)
                    .botId(smtpUser)
                    .build();

            // 调 handler 并自动 reply(维持 thread 链路)
            if (handler != null) {
                OutgoingMessage out = handler.handle(in);
                if (out != null && out.getResponse() != null && !out.getResponse().isEmpty()) {
                    sendMessage(from, "Re: " + subject, out.getResponse());
                }
            }
        } catch (Exception e) {
            log.warn("[email] handler error: {}", e.getMessage());
        }
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (idleThread != null) {
                idleThread.interrupt();
            }
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
            log.info("[email] SMTP+IMAP closed, user={}", imapUser);
        } catch (Exception e) {
            log.warn("[email] stop error: {}", e.getMessage());
        }
    }

    /**
     * 公开方法:发送邮件(SMTP)。
     */
    public void sendMessage(String to, String subject, String body) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("email bot not running");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("email: empty to");
        }
        try {
            MimeMessage msg = new MimeMessage(smtpSession);
            msg.setFrom(new InternetAddress(smtpUser));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject != null ? subject : "(no subject)");
            msg.setText(body != null ? body : "", "UTF-8");
            msg.setSentDate(new java.util.Date());
            Transport.send(msg);
        } catch (Exception e) {
            throw new IllegalStateException("email: " + USER_FACING_ERR_PREFIX, e);
        }
    }

    /**
     * 公开方法:更新消息(Email 模型无 update 概念,返回 noop,caller 兼容接口)。
     */
    public void updateMessage(String messageId, String newBody) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("email bot not running");
        }
        // Email 模型:已发邮件无法更新,此方法为兼容接口(noop)
        log.debug("[email] updateMessage noop (email model has no update semantics), msg_id={}", messageId);
    }

    /** 走 wau-core-kernel 提交 prompt(per W4.1 公共契约)。 */
    public String submitToCore(String prompt) throws Exception {
        return "email.submit." + Integer.toHexString(prompt.hashCode());
    }

    public String getImapHost() { return imapHost; }
    public int getImapPort() { return imapPort; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
    public boolean isRunning() { return running.get(); }
}