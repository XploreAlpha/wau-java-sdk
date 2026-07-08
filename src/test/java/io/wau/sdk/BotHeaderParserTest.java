package io.wau.sdk;

import io.wau.sdk.auth.BotHeaderParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * BotHeaderParser 单元测试(per D78 snake_case)。
 *
 * <p>5 测试:botId 格式 / 用户映射 / snake_case 保序 / 校验.
 */
class BotHeaderParserTest {

    @Test
    void build_3headers_snakeCase() {
        Map<String, String> h = BotHeaderParser.build("bot:default:myjava", "alice", 123L);
        assertThat(h).containsEntry("X-WAU-Bot", "bot:default:myjava");
        assertThat(h).containsEntry("X-WAU-User", "alice");
        assertThat(h).containsEntry("X-WAU-Owner", "123");
        assertThat(h).hasSize(3);
    }

    @Test
    void isValidBotId_validFormats() {
        assertThat(BotHeaderParser.isValidBotId("bot:default:myjava")).isTrue();
        assertThat(BotHeaderParser.isValidBotId("bot:acme:bot-007")).isTrue();
        assertThat(BotHeaderParser.isValidBotId("bot:tenant_with_underscore:botid")).isTrue();
    }

    @Test
    void isValidBotId_invalidFormats() {
        assertThat(BotHeaderParser.isValidBotId("Bot:default:bot")).isFalse(); // 大小写敏感(per D78)
        assertThat(BotHeaderParser.isValidBotId("bot:default")).isFalse(); // 缺 botid
        assertThat(BotHeaderParser.isValidBotId("bot::botid")).isFalse(); // 空 tenant
        assertThat(BotHeaderParser.isValidBotId("default:botid")).isFalse(); // 缺 bot: 前缀
        assertThat(BotHeaderParser.isValidBotId("")).isFalse();
        assertThat(BotHeaderParser.isValidBotId(null)).isFalse();
        assertThat(BotHeaderParser.isValidBotId("bot:default:bot:extra")).isFalse(); // 多于 3 段
    }

    @Test
    void build_blankArguments_throws() {
        assertThatThrownBy(() -> BotHeaderParser.build("", "alice", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BotHeaderParser.build("bot:d:b", "", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BotHeaderParser.build("bot:d:b", "alice", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_nullArguments_throws() {
        assertThatThrownBy(() -> BotHeaderParser.build(null, "alice", 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BotHeaderParser.build("bot:d:b", null, 1L))
                .isInstanceOf(NullPointerException.class);
    }
}
