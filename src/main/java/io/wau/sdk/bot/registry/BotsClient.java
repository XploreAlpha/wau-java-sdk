package io.wau.sdk.bot.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import io.wau.sdk.bot.common.Account;
import io.wau.sdk.bot.common.BotsService;
import io.wau.sdk.exception.BotAlreadyExistsException;
import io.wau.sdk.exception.BotInvalidArgumentException;
import io.wau.sdk.exception.BotNotFoundException;
import io.wau.sdk.exception.WauException;
import io.wau.sdk.transport.HttpTransport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP client for {@link BotsService}(per v1.3.0 W2.1, 2026-07-10)。
 *
 * <p>v1.3.0 同步 wau-go-sdk/bot/registry/client.go:5 method implementation
 * (register / get / update / list / delete),走 {@link HttpTransport} +
 * Jackson serialization。
 *
 * <p>endpoint 路径:`&lt;wauEdgeUrl&gt;/v1/registry/bots`(per WauClient.registerBot 100 line baseline)。
 *
 * <p>异常映射:
 * <ul>
 *   <li>404 → {@link BotNotFoundException}</li>
 *   <li>409 → {@link BotAlreadyExistsException}</li>
 *   <li>400 → {@link BotInvalidArgumentException}</li>
 *   <li>其他 → {@link WauException}</li>
 * </ul>
 *
 * @since 1.3.0
 */
public final class BotsClient implements BotsService {

    /** 复用 WauClient 同款 endpoint(registerBot 用的是它) */
    static final String BOT_ENDPOINT_PATH = "/v1/registry/bots";

    private final String baseUrl;
    private final HttpTransport transport;
    private final Map<String, String> authHeaders;

    /**
     * @param baseUrl 例如 {@code https://wau.example.com},不带 trailing slash
     * @param transport HTTP 客户端(测试时传带 Sender mock 的实例)
     * @param botToken  bot auth token(可空),Bearer 头注入
     */
    public BotsClient(String baseUrl, HttpTransport transport, String botToken) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.transport = Objects.requireNonNull(transport, "transport");
        this.authHeaders = new HashMap<>();
        if (botToken != null && !botToken.isEmpty()) {
            authHeaders.put("Authorization", "Bearer " + botToken);
        }
    }

    @Override
    public Account register(RegisterRequest request) throws Exception {
        if (request.tenantId() == null || request.tenantId().isEmpty()
                || request.botId() == null || request.botId().isEmpty()
                || request.ownerUserId() == null || request.ownerUserId().isEmpty()) {
            throw new BotInvalidArgumentException("tenantId, botId, ownerUserId are required");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("tenant_id", request.tenantId());
        body.put("bot_id", request.botId());
        if (request.botUuid() != null && !request.botUuid().isEmpty()) {
            body.put("bot_uuid", request.botUuid());
        }
        body.put("owner_user_id", request.ownerUserId());
        body.put("channel_type", request.channelType());
        body.put("channel_config_id", request.channelConfigId());

        return transport.postJson(baseUrl + BOT_ENDPOINT_PATH, authHeaders, body, Account.class);
    }

    @Override
    public Optional<Account> get(String publicBotId) throws Exception {
        Objects.requireNonNull(publicBotId, "publicBotId");
        // 5 SDK interface contract: get(publicBotId) → Optional<Account>
        // Maps to GET /v1/registry/bots/{publicBotId}
        Account result = transport.get(baseUrl + BOT_ENDPOINT_PATH + "/" + publicBotId, authHeaders, Account.class);
        return Optional.ofNullable(result);
    }

    @Override
    public Account update(String publicBotId, UpdateRequest request) throws Exception {
        Objects.requireNonNull(publicBotId, "publicBotId");
        if (request == null
                || (request.ownerUserId() == null && request.channelType() == null && request.channelConfigId() == null)) {
            throw new BotInvalidArgumentException("at least one field required: ownerUserId, channelType, channelConfigId");
        }
        Map<String, Object> body = new HashMap<>();
        if (request.ownerUserId() != null) body.put("owner_user_id", request.ownerUserId());
        if (request.channelType() != null) body.put("channel_type", request.channelType());
        if (request.channelConfigId() != null) body.put("channel_config_id", request.channelConfigId());

        return transport.putJson(baseUrl + BOT_ENDPOINT_PATH + "/" + publicBotId, authHeaders, body, Account.class);
    }

    @Override
    public List<Account> list(ListFilter filter) throws Exception {
        TypeReference<List<Account>> typeRef = new TypeReference<List<Account>>() {};
        return transport.get(baseUrl + BOT_ENDPOINT_PATH, authHeaders, typeRef);
    }

    @Override
    public void delete(String publicBotId) throws Exception {
        Objects.requireNonNull(publicBotId, "publicBotId");
        transport.delete(baseUrl + BOT_ENDPOINT_PATH + "/" + publicBotId, authHeaders);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
