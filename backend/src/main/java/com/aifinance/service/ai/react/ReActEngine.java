package com.aifinance.service.ai.react;

import com.aifinance.entity.AiConfig;
import com.aifinance.service.ai.AiClient;
import com.aifinance.service.ai.ChatMessage;
import com.aifinance.service.ai.JsonExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 通用 ReAct(Reasoning + Acting)引擎。
 * <p>
 * 模型以"思考-行动-观察"循环推进: 每一步输出 JSON
 * {@code {"thought":"","action":"TOOL_NAME|FINAL","actionInput":"","final":<最终答案>}}。
 * 当 action=某工具时, 引擎调用对应的 Java 工具函数, 并把返回作为 Observation 反馈给模型;
 * 当 action=FINAL 时结束, 返回最终答案与完整轨迹。
 * <p>
 * 该实现不依赖模型的 function-calling 能力, 兼容任意 OpenAI 兼容模型。
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
     * 运行一次 ReAct episode。
     *
     * @param config       AI 配置
     * @param rolePreamble 角色与任务说明(领域相关)
     * @param task         本次具体任务与上下文
     * @param finalSchema  对 FINAL 答案格式的要求说明
     * @param tools        可用工具: 名称 -> (actionInput -> observation)
     * @param maxSteps     最大步数
     */
    public ReActResult run(AiConfig config, String rolePreamble, String task, String finalSchema,
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
                // 解析失败, 把原文作为最终答案返回
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

            boolean isFinal = action.equalsIgnoreCase("FINAL")
                    || (node.has("final") && !node.path("final").isNull()
                    && !node.path("final").isMissingNode() && action.isBlank());

            if (isFinal || node.has("final") && !node.path("final").isNull() && !node.path("final").isMissingNode()) {
                JsonNode finalNode = node.path("final");
                String finalText = finalNode.isMissingNode() || finalNode.isNull()
                        ? resp : (finalNode.isValueNode() ? finalNode.asText() : finalNode.toString());
                step.setObservation("FINAL");
                result.getSteps().add(step);
                result.setFinalAnswer(finalText);
                result.setSuccess(true);
                return result;
            }

            // 执行工具
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

        // 达到最大步数仍未 FINAL, 强制收尾
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
