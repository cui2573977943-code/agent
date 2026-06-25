package com.aifinance.service.ai.react;

import com.aifinance.entity.AiConfig;
import com.aifinance.service.ai.AiClient;
import com.aifinance.service.ai.ChatMessage;
import com.aifinance.service.ai.JsonExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 通用 ReAct(Reasoning + Acting)引擎。
 * <p>
 * 两种执行模式:
 * <ul>
 *   <li><b>function-calling 模式</b>: 若模型/服务支持原生工具调用, 则用 LangChain4j 的
 *       {@link ToolSpecification} 让模型自行决定调用哪个工具, 引擎执行后通过
 *       {@link ToolExecutionResultMessage} 回灌观察结果, 直至模型给出最终答案;</li>
 *   <li><b>手动模式(回退)</b>: 若不支持工具调用, 则用 JSON 协议模拟"思考-行动-观察"循环,
 *       兼容任意 OpenAI 兼容模型。</li>
 * </ul>
 * 调用方无需关心使用了哪种模式, 引擎会自动探测并回退。
 */
@Component
public class ReActEngine {

    private static final Logger log = LoggerFactory.getLogger(ReActEngine.class);

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public ReActEngine(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行一次 ReAct episode。优先使用 function-calling, 不支持或失败则回退手动模式。
     *
     * @param config           AI 配置
     * @param rolePreamble     角色与任务说明
     * @param task             本次任务与上下文
     * @param finalSchema      最终答案格式要求
     * @param tools            工具: 名称 -> (入参 -> 观察结果)
     * @param toolDescriptions 工具描述(供 function-calling 的 schema 使用)
     * @param maxSteps         最大步数
     */
    public ReActResult run(AiConfig config, String rolePreamble, String task, String finalSchema,
                           Map<String, Function<String, String>> tools,
                           Map<String, String> toolDescriptions, int maxSteps) {
        if (!tools.isEmpty()) {
            try {
                if (aiClient.supportsToolCalling(config)) {
                    log.debug("使用 function-calling 模式执行 ReAct");
                    return runFunctionCalling(config, rolePreamble, task, finalSchema,
                            tools, toolDescriptions, maxSteps);
                }
            } catch (Exception e) {
                log.warn("function-calling 执行失败, 回退手动 ReAct: {}", e.getMessage());
            }
        }
        log.debug("使用手动 ReAct 模式执行");
        return runManual(config, rolePreamble, task, finalSchema, tools, maxSteps);
    }

    // ============================================================
    // 模式一: 原生 function-calling
    // ============================================================

    private ReActResult runFunctionCalling(AiConfig config, String rolePreamble, String task,
                                           String finalSchema, Map<String, Function<String, String>> tools,
                                           Map<String, String> toolDescriptions, int maxSteps) {
        ReActResult result = new ReActResult();

        List<ToolSpecification> specs = new ArrayList<>();
        for (String name : tools.keySet()) {
            String desc = toolDescriptions == null ? null : toolDescriptions.get(name);
            specs.add(ToolSpecification.builder()
                    .name(name)
                    .description(desc == null ? (name + " 工具") : desc)
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("input", "工具入参(检索/查询关键词)")
                            .required(List.of("input"))
                            .build())
                    .build());
        }

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(rolePreamble + "\n\n"
                + "你可以调用提供的工具来收集证据(每次调用传入 input 参数)。"
                + "当证据已足够时, 不要再调用工具, 直接输出最终答案。"
                + "最终答案必须是且仅是一个 JSON, 满足: " + finalSchema));
        messages.add(UserMessage.from(task));

        for (int i = 1; i <= maxSteps; i++) {
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(specs)
                    .build();
            ChatResponse response = aiClient.chat(config, request);
            AiMessage ai = response.aiMessage();
            messages.add(ai);

            if (ai.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                    String input = extractInput(req.arguments());
                    Function<String, String> tool = matchTool(tools, req.name());
                    String observation;
                    if (tool == null) {
                        observation = "未知工具 '" + req.name() + "'";
                    } else {
                        try {
                            observation = clip(tool.apply(input), 1500);
                        } catch (Exception e) {
                            observation = "工具执行异常: " + e.getMessage();
                        }
                    }
                    ReActStep step = new ReActStep();
                    step.setStep(result.getSteps().size() + 1);
                    step.setThought(ai.text());
                    step.setAction(req.name());
                    step.setActionInput(input);
                    step.setObservation(observation);
                    result.getSteps().add(step);

                    messages.add(ToolExecutionResultMessage.from(req, observation));
                }
                continue;
            }

            // 无工具调用 -> 视为最终答案
            ReActStep step = new ReActStep();
            step.setStep(result.getSteps().size() + 1);
            step.setThought(ai.text());
            step.setAction("FINAL");
            step.setObservation("FINAL");
            result.getSteps().add(step);
            result.setFinalAnswer(ai.text());
            result.setSuccess(true);
            return result;
        }

        // 达到最大步数, 不带工具再要一次最终答案
        messages.add(UserMessage.from("已达最大步数, 请立即只输出最终答案 JSON, 满足: " + finalSchema));
        ChatRequest finalReq = ChatRequest.builder().messages(messages).build();
        ChatResponse finalResp = aiClient.chat(config, finalReq);
        result.setFinalAnswer(finalResp.aiMessage().text());
        result.setSuccess(true);
        ReActStep step = new ReActStep();
        step.setStep(result.getSteps().size() + 1);
        step.setAction("FINAL");
        step.setObservation("FINAL(强制收尾)");
        result.getSteps().add(step);
        return result;
    }

    private String extractInput(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(arguments);
            if (node.has("input")) {
                return node.path("input").asText("");
            }
            // 取第一个字符串字段
            var it = node.fields();
            if (it.hasNext()) {
                return it.next().getValue().asText("");
            }
        } catch (Exception ignored) {
            // 参数非 JSON, 直接当作输入
        }
        return arguments;
    }

    // ============================================================
    // 模式二: 手动 JSON 协议 ReAct(回退)
    // ============================================================

    private ReActResult runManual(AiConfig config, String rolePreamble, String task, String finalSchema,
                                  Map<String, Function<String, String>> tools, int maxSteps) {
        ReActResult result = new ReActResult();

        String toolDesc = tools.keySet().isEmpty()
                ? "(无可用工具, 请直接基于已知信息推理)"
                : String.join(", ", tools.keySet());

        String sys = rolePreamble + "\n\n"
                + "你必须使用 ReAct(思考-行动-观察)方式逐步推理。"
                + "每一步只输出一个 JSON 对象, 不要输出多余文字, 结构如下:\n"
                + "{\"thought\":\"你的思考\",\"action\":\"工具名或FINAL\",\"actionInput\":\"工具入参(检索/查询关键词)\",\"final\":<当action=FINAL时给出最终答案>}\n"
                + "可用工具: " + toolDesc + "。\n"
                + "当你已收集足够证据时, 令 action=FINAL, 并在 final 字段给出最终答案。\n"
                + "FINAL 的 final 字段必须满足: " + finalSchema;

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(sys));
        conversation.add(ChatMessage.user(task + "\n\n请开始第 1 步 (输出单个 JSON)。"));

        for (int i = 1; i <= maxSteps; i++) {
            String resp;
            try {
                resp = aiClient.chat(config, conversation);
            } catch (Exception e) {
                log.warn("ReAct 第 {} 步调用失败: {}", i, e.getMessage());
                ReActStep failStep = new ReActStep();
                failStep.setStep(i);
                failStep.setThought("(AI 调用失败)");
                failStep.setObservation("调用异常: " + e.getMessage());
                result.getSteps().add(failStep);
                break;
            }
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            ReActStep step = new ReActStep();
            step.setStep(i);
            if (node == null) {
                step.setThought("(无法解析为 JSON)");
                step.setObservation(clip(resp, 300));
                result.getSteps().add(step);
                result.setFinalAnswer(resp);
                result.setSuccess(false);
                return result;
            }

            String thought = node.path("thought").asText("");
            String action = node.path("action").asText("").trim();
            String actionInput = node.path("actionInput").asText("");
            step.setThought(thought);
            step.setAction(action);
            step.setActionInput(actionInput);

            boolean hasFinal = node.has("final") && !node.path("final").isNull()
                    && !node.path("final").isMissingNode();
            if (action.equalsIgnoreCase("FINAL") || hasFinal) {
                JsonNode finalNode = node.path("final");
                String finalText = finalNode.isMissingNode() || finalNode.isNull()
                        ? resp : (finalNode.isValueNode() ? finalNode.asText() : finalNode.toString());
                step.setObservation("FINAL");
                result.getSteps().add(step);
                result.setFinalAnswer(finalText);
                result.setSuccess(true);
                return result;
            }

            Function<String, String> tool = matchTool(tools, action);
            String observation;
            if (tool == null) {
                observation = "未知工具 '" + action + "'。可用: " + toolDesc + "。请改用可用工具或给出 FINAL。";
            } else {
                try {
                    observation = clip(tool.apply(actionInput), 1500);
                } catch (Exception e) {
                    observation = "工具执行异常: " + e.getMessage();
                }
            }
            step.setObservation(observation);
            result.getSteps().add(step);

            conversation.add(ChatMessage.assistant(resp));
            conversation.add(ChatMessage.user("Observation: " + observation
                    + "\n请继续下一步 (输出单个 JSON; 若证据已足够请给出 action=FINAL)。"));
        }

        conversation.add(ChatMessage.user("已达最大步数, 请立即只输出 FINAL 的 JSON: "
                + "{\"action\":\"FINAL\",\"final\":<满足要求的最终答案>}。要求: " + finalSchema));
        try {
            String resp = aiClient.chat(config, conversation);
            JsonNode node = JsonExtractor.extract(objectMapper, resp);
            ReActStep step = new ReActStep();
            step.setStep(result.getSteps().size() + 1);
            step.setAction("FINAL");
            if (node != null && node.has("final") && !node.path("final").isNull()) {
                JsonNode finalNode = node.path("final");
                result.setFinalAnswer(finalNode.isValueNode() ? finalNode.asText() : finalNode.toString());
            } else {
                result.setFinalAnswer(resp);
            }
            step.setObservation("FINAL(强制收尾)");
            result.getSteps().add(step);
            result.setSuccess(true);
        } catch (Exception e) {
            log.warn("ReAct 强制收尾失败: {}", e.getMessage());
            result.setSuccess(false);
        }
        return result;
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private Function<String, String> matchTool(Map<String, Function<String, String>> tools, String action) {
        if (action == null) return null;
        for (Map.Entry<String, Function<String, String>> e : tools.entrySet()) {
            if (e.getKey().equalsIgnoreCase(action)) {
                return e.getValue();
            }
        }
        return null;
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
