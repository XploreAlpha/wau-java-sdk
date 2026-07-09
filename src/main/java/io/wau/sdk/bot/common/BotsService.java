package io.wau.sdk.bot.common;

import io.wau.sdk.model.Bot;

import java.util.List;
import java.util.Optional;

/**
 * 公共 BotsService interface(per M10 N1 拍板:5 SDK 100% 一致)。
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
 * @since 0.2.0 (W5 OSS-onboarding closure, 2026-07-13)
 */
public interface BotsService {

    /** 注册新 bot。 */
    Bot register(RegisterRequest request) throws Exception;

    /** 通过 public bot ID 获取 bot。 */
    Optional<Bot> get(String publicBotId) throws Exception;

    /** 更新 bot 信息。 */
    Bot update(String publicBotId, UpdateRequest request) throws Exception;

    /** 列出 tenant 下所有 bots。 */
    List<Bot> list(ListFilter filter) throws Exception;

    /** 删除 bot。 */
    void delete(String publicBotId) throws Exception;

    /** 注册 bot 请求。 */
    record RegisterRequest(
            String tenantId,
            String universe,
            String publicBotId,
            String displayName,
            String backend
    ) {}

    /** 更新 bot 请求。 */
    record UpdateRequest(
            String displayName,
            String backend,
            Boolean enabled
    ) {}

    /** 列表过滤器。 */
    record ListFilter(
            String tenantId,
            String universe,
            Integer limit,
            String cursor
    ) {}
}
