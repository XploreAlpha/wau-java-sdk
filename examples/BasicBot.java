import io.wau.sdk.WauClient;
import io.wau.sdk.WauConfig;
import io.wau.sdk.model.Bot;
import io.wau.sdk.model.CostEvent;
import io.wau.sdk.model.IncomingMessage;
import io.wau.sdk.model.OutgoingMessage;
import io.wau.sdk.model.SampleRequest;
import io.wau.sdk.model.SampleResponse;

import java.time.Instant;

/**
 * BasicBot — wau-java-sdk 最小可用示例(per D63 启动备忘 §6.6)。
 *
 * <p>演示 5 核心方法:
 * <ol>
 *   <li>注册 bot 到 wau-registry</li>
 *   <li>LLM 决策采样</li>
 *   <li>发消息</li>
 *   <li>拉 agent 元数据</li>
 *   <li>记录成本</li>
 * </ol>
 *
 * <p>运行(配 Maven classpath):
 * <pre>
 *   mvn compile
 *   mvn exec:java -Dexec.mainClass="BasicBot" -Dexec.classpathScope=runtime
 * </pre>
 */
public final class BasicBot {

    public static void main(String[] args) {
        // 1. 配置 SDK
        WauConfig cfg = new WauConfig.Builder()
                .wauEdgeUrl("http://localhost:18402")
                .botId("bot:demo:basicbot-java")
                .ownerUserId("100")
                .botToken("demo-token-change-in-prod")
                .build();

        WauClient client = new WauClient(cfg);

        // === 2. registerBot(per M10 N3.3) ===
        try {
            Bot registeredBot = client.registerBot(
                    "bot:demo:basicbot-java",
                    "demo-token-change-in-prod");
            System.out.println("✓ Registered: " + registeredBot.getBotId()
                    + " enabled=" + registeredBot.isEnabled());
        } catch (Exception e) {
            System.out.println("(register bot skipped: " + e.getMessage() + ")");
        }

        // === 3. sampleLLM(per M3 W3) ===
        SampleRequest req = SampleRequest.builder()
                .prompt("Summarize today's WAU network status")
                .userId("alice")
                .tenantId("demo")
                .universeLabel("earth")
                .build();
        SampleResponse sampled = client.sampleLLM(req);
        System.out.println("✓ Sampled provider: " + sampled.getProvider()
                + " estimate_cost_usd=" + sampled.getEstimatedCostUsd());

        // === 4. sendMessage(per M5 八平台) ===
        IncomingMessage msg = IncomingMessage.builder()
                .platformMsgId("msg-001")
                .channelId("console")
                .userId("alice")
                .username("alice")
                .text("hello from Java SDK")
                .timestamp(Instant.now())
                .build();
        OutgoingMessage out = client.sendMessage(msg);
        System.out.println("✓ Sent message, reply: " + out.getResponse()
                + " tokens_used=" + out.getTokensUsed());

        // === 5. getAgent(per M8 W4) ===
        try {
            io.wau.sdk.model.Agent a = client.getAgent("agent-demo-001");
            System.out.println("✓ Agent: " + a.getName() + " endpoint=" + a.getEndpoint());
        } catch (Exception e) {
            System.out.println("(getAgent skipped: " + e.getMessage() + ")");
        }

        // === 6. recordCost(per M3 §3.1) ===
        CostEvent event = CostEvent.builder()
                .userId("alice")
                .tenantId("demo")
                .provider(sampled.getProvider())
                .model(sampled.getModel())
                .tokensInput(100)
                .tokensOutput(50)
                .costUsd(sampled.getEstimatedCostUsd())
                .latencyMs(120)
                .timestamp(Instant.now())
                .build();
        client.recordCost(event);
        System.out.println("✓ Recorded cost: " + event.getCostUsd() + " USD");

        System.out.println("\n=== wau-java-sdk preview 0.1.0 demo completed ===");
    }
}
