/**
 * MCP client (wau-java-sdk v1.3.2, per D87.9)。
 *
 * <p>⭐ v1.0.0 D87.9 W3 + W4-W5 实装(2026-07-11)。
 *
 * <p>5 SDK 共享 wire format:JSON-RPC 2.0 over HTTP at POST {baseURL}/mcp
 * (跟 WAU-core-kernel {@code internal/protocol/mcp/server.go} handleMCP 对齐)。
 *
 * <p>本 package = 8 sync MCP tool wrapper(health_check / parse_agent_card / send_message /
 * get_task / list_tasks / cancel_task / create_task_push_notification_config /
 * get_extended_agent_card)+ JSON-RPC envelope + error handling。
 * 2 SSE streaming tool(stream_message + subscribe_to_task)deferred to W5+。
 *
 * <p>协议合规:
 * <ul>
 *   <li>D60 additive: 0 改老 SDK, 新增独立 package(跟 {@code bot/} / {@code l5/} / {@code ucp/} 同级)</li>
 *   <li>D13 byte-equal: JSON wire format 5 SDK 一致(per design doc §二)</li>
 *   <li>D78/D79/D80: MCP OAuth 2.0 identity_linking bearer token(跟 UCP JWT 走同一通道)</li>
 *   <li>D87 ⭐⭐: 本子包 = D87.9 Java SDK MCP client 实装(W3-launch-SOP §3.3 拍板)</li>
 * </ul>
 *
 * <p>设计原则(跟 {@code ucp/} 1:1):
 * <ul>
 *   <li>{@link io.wau.sdk.transport.HttpTransport} 由 caller 注入(测试用 CapturingSender, 生产用真 OkHttp)</li>
 *   <li>{@link com.fasterxml.jackson.databind.ObjectMapper} 用于 JSON-RPC envelope 序列化</li>
 *   <li>错误统一抛 {@link McpException}(JSON-RPC 2.0 envelope), 跟 wau-go-sdk mcpclient.RPCError 字段对齐</li>
 *   <li>Bearer token 走 OAuth 2.0 identity_linking(W3 stub; W5+ 加 refresh flow)</li>
 * </ul>
 */
package io.wau.sdk.mcp;