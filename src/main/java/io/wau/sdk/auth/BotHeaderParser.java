package io.wau.sdk.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * BotHeaderParser(per D78 snake_case + D80 owner_user_id 3 向 header 注入)。
 *
 * <p>5 SDK 一致:每个 HTTP 请求带 3 个 header:
 * <ul>
 *   <li>{@code X-WAU-Bot: <bot_id>} — snake_case 值(bot:tenant:botid)</li>
 *   <li>{@code X-WAU-User: <user_id>}</li>
 *   <li>{@code X-WAU-Owner: <owner_user_id>}</li>
 * </ul>
 *
 * <p>对齐 4 SDK(Go/Python/TS/Rust)BotHeaderParser 同名类行为。
 *
 * @since 0.1.0-preview (v1.0.0 D63=A, 2026-07-08)
 */
public final class BotHeaderParser {

    public static final String HEADER_BOT = "X-WAU-Bot";
    public static final String HEADER_USER = "X-WAU-User";
    public static final String HEADER_OWNER = "X-WAU-Owner";

    private BotHeaderParser() {}

    /**
     * 构造 3 header map.
     *
     * @param botId {@code bot:<tenant>:<botid>} 公开 ID
     * @param userId 业务用户 ID
     * @param ownerUserId owner 整数 ID
     * @return snake_case header map(LinkedHashMap 保序)
     */
    public static Map<String, String> build(String botId, String userId, Long ownerUserId) {
        Objects.requireNonNull(botId, "botId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        if (botId.isBlank() || userId.isBlank()) {
            throw new IllegalArgumentException("botId and userId must be non-blank");
        }
        Map<String, String> h = new LinkedHashMap<>();
        h.put(HEADER_BOT, botId);
        h.put(HEADER_USER, userId);
        h.put(HEADER_OWNER, ownerUserId.toString());
        return h;
    }

    /**
     * 校验 bot_id 格式(per D78) — {@code bot:<tenant>:<botid>} snake_case.
     */
    public static boolean isValidBotId(String botId) {
        if (botId == null) return false;
        if (!botId.startsWith("bot:")) return false;
        String[] parts = botId.split(":");
        if (parts.length != 3) return false;
        for (String p : parts) {
            if (p.isBlank()) return false;
        }
        return true;
    }
}
