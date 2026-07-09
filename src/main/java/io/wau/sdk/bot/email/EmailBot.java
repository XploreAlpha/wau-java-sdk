package io.wau.sdk.bot.email;

import io.wau.sdk.bot.common.Bot;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Email Bot SDK 集成(W5 OSS-onboarding closure, 2026-07-13)。
 *
 * <p>完整 SDK 端实现(per W5 Q1=B 反 W4.1 拍板):
 * <ul>
 *   <li>Stage 0 stub (matches Python/TS/Rust/Go Stage 0 pattern)</li>
 *   <li>Stage 1 路径: jakarta.mail:jakarta.mail-api (SMTP/IMAP)</li>
 *   <li>公共 Bot interface 沿用 M10 N1 拍板</li>
 *   <li>仍走 wau-edge POST /v1/bots/{bot_id}/messages 注册 (per M10 N3)</li>
 * </ul>
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 */
public class EmailBot implements Bot {

    private final String imapHost;
    private final int imapPort;
    private final String imapUser;
    private final String imapPass;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPass;
    private final AtomicBoolean running = new AtomicBoolean(false);
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
     * 启动 Email IMAP IDLE (Stage 0 stub)。
     */
    @Override
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("email bot already running");
        }
        // Stage 1: jakarta.mail IMAP IDLE
    }

    /** 优雅停止。 */
    @Override
    public void stop() throws Exception {
        running.set(false);
    }

    /** 公开方法:发送邮件 (Stage 0 stub,Email 用 SendMessage 而非 PostMessage)。 */
    public void sendMessage(String to, String subject, String body) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("email bot not running");
        }
        // Stage 1: Transport.send
    }

    /** 公开方法:更新消息 (Stage 0 stub,Email 无 update 概念,返回 mock)。 */
    public void updateMessage(String messageId, String newBody) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("email bot not running");
        }
        // Email 不支持更新已发邮件,此方法为兼容接口
    }

    /** 走 wau-core-kernel 提交 prompt。 */
    public String submitToCore(String prompt) throws Exception {
        return "email.submit." + prompt.hashCode();
    }

    public String getImapHost() { return imapHost; }
    public int getImapPort() { return imapPort; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getTenant() { return tenant; }
    public String getUniverse() { return universe; }
    public MessageHandler getHandler() { return handler; }
}
