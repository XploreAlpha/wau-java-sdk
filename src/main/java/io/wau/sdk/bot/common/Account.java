package io.wau.sdk.bot.common;

import java.time.Instant;

/**
 * 公共 Bot Account DTO(per M10 N1 / D82=A 拍板,5 SDK 100% 一致)。
 *
 * <p>W6 (2026-07-09) W5 缺口补全:Java SDK 补 Account.java,字段与 wau-go-sdk/bot/common/account.go 一字不差。
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
 * @updated 0.2.1 (W6 W5 缺口补全, 2026-07-09)
 */
public record Account(
        /** registry 服务端分配的 UUID(空 = 待注册,服务端回填) */
        String accountId,

        /** 多租户 ID(必填,例 "acme") */
        String tenantId,

        /** 本地名 / slug(必填,例 "weather-cn")。在 tenant 内唯一 */
        String botId,

        /** 全局公开 ID(由 TenantID + BotID 派生,D82=A) */
        String publicBotId,

        /** 注册人 user_id(C 端 或 B 端 owner,谁注册谁负责) */
        String ownerUserId,

        /** 绑定的 IM 平台类型(per M5 八平台):
         *  "telegram"|"discord"|"slack"|"feishu"|"dingtalk"|"qq"|"email"|"webhook" */
        String channelType,

        /** wau-channel 内的 config ID(platform credentials 索引) */
        String channelConfigId,

        /** 注册时间(UTC,服务端回填,客户端只读) */
        Instant createdAt,

        /** 最后更新时间(UTC,服务端回填,客户端只读) */
        Instant updatedAt
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