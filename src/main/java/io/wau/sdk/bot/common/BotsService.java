package io.wau.sdk.bot.common;

import java.util.List;
import java.util.Optional;

/**
 * 公共 BotsService interface(per M10 N1 拍板:5 SDK 100% 一致)。
 *
 * <p>W6 (2026-07-09) W5 缺口补全:DTO 字段对齐 wau-go-sdk/bot/common/bots_service.go 风格
 * (去除原 Java preview 自有 displayName/backend 字段,改用 5 SDK 统一的 tenantId/botId/ownerUserId/channelType/channelConfigID)。
 *
 * <p>5 SDK 必须实现的方法签名:
 * <ul>
 *   <li>{@link #register(RegisterRequest)} - 注册 bot</li>
 *   <li>{@link #get(String)} - 获取 bot by public ID</li>
 *   <li>{@link #update(String, UpdateRequest)} - 更新 bot</li>
 *   <li>{@link #list(ListFilter)} - 列出 tenant 下 bots</li>
 *   <li>{@link #delete(String)} - 删除 bot</li>
 * </ul>
 *
 * <p>5 SDK 字段必须保持字节级一致(per D13 拍板):
 * <ul>
 *   <li>wau-go-sdk/bot/common/bots_service.go + account.go</li>
 *   <li>wau-python-sdk/src/wau_sdk/bot/common/bots_service.py + account.py</li>
 *   <li>wau-typescript-sdk/src/bot/common/bots_service.ts + account.ts</li>
 *   <li>wau-rust-sdk/src/bot/common/bots_service.rs + account.rs</li>
 *   <li>wau-java-sdk/src/main/java/io/wau/sdk/bot/common/BotsService.java + Account.java (本文件)</li>
 * </ul>
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.2.1 (W6 W5 缺口补全, 2026-07-09 — DTO 反转对齐 Go 风格)
 */
public interface BotsService {

    /** 注册新 bot。 */
    Account register(RegisterRequest request) throws Exception;

    /** 通过 public bot ID 获取 bot。 */
    Optional<Account> get(String publicBotId) throws Exception;

    /** 更新 bot 信息。 */
    Account update(String publicBotId, UpdateRequest request) throws Exception;

    /** 列出 tenant 下所有 bots。 */
    List<Account> list(ListFilter filter) throws Exception;

    /** 删除 bot。 */
    void delete(String publicBotId) throws Exception;

    /**
     * 注册 bot 请求(per W6 DTO 对齐 Go 风格,2026-07-09)。
     *
     * <p>对应 wau-go-sdk/bot/common/account.go RegisterBotRequest:
     * <pre>
     * TenantID        string `json:"tenant_id"`
     * BotID           string `json:"bot_id"`
     * OwnerUserID     string `json:"owner_user_id"`
     * ChannelType     string `json:"channel_type"`
     * ChannelConfigID string `json:"channel_config_id"`
     * </pre>
     */
    record RegisterRequest(
            String tenantId,
            String botId,
            String ownerUserId,
            String channelType,
            String channelConfigId
    ) {}

    /**
     * 更新 bot 请求(per W6 DTO 对齐 Go 风格,2026-07-09)。
     *
     * <p>对应 wau-go-sdk/bot/common/account.go UpdateBotRequest:
     * 只允许改 OwnerUserID / ChannelType / ChannelConfigID(账户身份 + 绑定)。
     * TenantID / BotID / PublicBotID 都是 immutable 字段。
     */
    record UpdateRequest(
            String ownerUserId,
            String channelType,
            String channelConfigId
    ) {}

    /**
     * 列表过滤器(per W6 DTO 对齐 Go 风格,2026-07-09)。
     *
     * <p>对应 wau-go-sdk/bot/common/account.go ListBotsFilter:
     * <pre>
     * TenantID    string `json:"tenant_id,omitempty"`
     * OwnerUserID string `json:"owner_user_id,omitempty"`
     * ChannelType string `json:"channel_type,omitempty"`
     * Limit       int    `json:"limit,omitempty"`
     * </pre>
     */
    record ListFilter(
            String tenantId,
            String ownerUserId,
            String channelType,
            Integer limit
    ) {}
}