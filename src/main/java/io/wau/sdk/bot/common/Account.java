package io.wau.sdk.bot.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 公共 Bot Account DTO(per M10 N1 / D82=A 拍板,5 SDK 100% 一致)。
 *
 * <p>W6 (2026-07-09) W5 缺口补全:Java SDK 补 Account.java,字段与 wau-go-sdk/bot/common/account.go 一字不差。
 *
 * <p>W2.1 (2026-07-10) v1.3.0 sync:加 {@code botUuid} (UUID v4, server-assigned)
 * per D78/D79/D80 拍板;D60 additive,0 改老字段。老 BotID slug 语义不变。
 *
 * <p>公开 bot ID 格式:"bot:&lt;tenant&gt;:&lt;botid&gt;"
 * <p>例:tenant=acme, botID=weather-cn → PublicBotID="bot:acme:weather-cn"
 *
 * <p>注:此 Account 是 bot 注册中心(wau-registry-service)入口的领域对象,
 * 不是 wau-store 的 Account(财务账户),两者不应混用。
 *
 * <p>5 SDK 必须保持字段名 + 类型 100% 一致(per D13 拍板):
 * <ul>
 *   <li>wau-go-sdk/bot/common/account.go</li>
 *   <li>wau-python-sdk/src/wau_sdk/bot/common/account.py</li>
 *   <li>wau-typescript-sdk/src/bot/common/account.ts</li>
 *   <li>wau-rust-sdk/src/bot/common/account.rs</li>
 *   <li>wau-java-sdk/src/main/java/io/wau/sdk/bot/common/Account.java (本文件)</li>
 * </ul>
 *
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 * @updated 0.2.2 (W2.1 v1.3.0 sync, 2026-07-10)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Account(
        /** registry 服务端分配的 UUID(空 = 待注册,服务端回填) */
        @JsonProperty("account_id") String accountId,

        /** 多租户 ID(必填,例 "acme") */
        @JsonProperty("tenant_id") String tenantId,

        /** 本地名 / slug(必填,例 "weather-cn")。在 tenant 内唯一 */
        @JsonProperty("bot_id") String botId,

        /**
         * v1.3.0 W2.1 (2026-07-10,D78):服务端分配的 UUID v4(per tenant 全局唯一)。
         * 与 BotID slug 不同:BotID = human-readable client-supplied,BotUUID = machine-friendly server-assigned。
         * 用途:wau-edge route 寻址 / wau-channel 8 平台 adapter 寻址 / D79 JWT 4 claims 之一。
         * Register 响应返回(服务端决定,不接受 client 上传)。空 = 老 SDK v1.2.0 兼容降级路径。
         */
        @JsonProperty("bot_uuid") String botUuid,

        /** 全局公开 ID(由 TenantID + BotID 派生,D82=A) */
        @JsonProperty("public_bot_id") String publicBotId,

        /** 注册人 user_id(C 端 或 B 端 owner,谁注册谁负责) */
        @JsonProperty("owner_user_id") String ownerUserId,

        /** 绑定的 IM 平台类型(per M5 八平台):
         *  "telegram"|"discord"|"slack"|"feishu"|"dingtalk"|"qq"|"email"|"webhook" */
        @JsonProperty("channel_type") String channelType,

        /** wau-channel 内的 config ID(platform credentials 索引) */
        @JsonProperty("channel_config_id") String channelConfigId,

        /** 注册时间(UTC,服务端回填,客户端只读) */
        @JsonProperty("created_at") Instant createdAt,

        /** 最后更新时间(UTC,服务端回填,客户端只读) */
        @JsonProperty("updated_at") Instant updatedAt
) {

    /**
     * 构造 Account 并填充 PublicBotID(D82=A 规则)。
     *
     * <p>AccountID / CreatedAt / UpdatedAt 留空 → 服务端 Register 时回填。
     * 这样客户端只声明业务字段,服务端掌控元数据。
     */
    public static Account newAccount(String tenantId, String botId, String ownerUserId,
                                     String channelType, String channelConfigId) {
        Instant now = Instant.now();
        return new Account(
                "",
                tenantId,
                botId,
                "",  // v1.3.0 (W2.1):botUuid 老 caller 不传 → 留空,服务端 Register 时回填
                PublicBotIDOf(tenantId, botId),
                ownerUserId,
                channelType,
                channelConfigId,
                now,
                now
        );
    }

    /**
     * 给定 tenant + botID 计算公开 ID(纯函数,无副作用)。
     *
     * <p>等价于 {@code "bot:" + tenantID + ":" + botID}。
     * SDK 任何地方需要派生公开 ID 都走这个,避免散落 string 拼接。
     */
    public static String PublicBotIDOf(String tenantId, String botId) {
        return "bot:" + tenantId + ":" + botId;
    }
}