package com.exam.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.exam.service.LlmClient;
import com.exam.service.agent.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 画像构建Agent
 * 负责通过对话了解用户学习水平，构建学习画像
 */
@Component
public class ProfileAgent implements LearningAgent {

    private static final Logger log = LoggerFactory.getLogger(ProfileAgent.class);

    @Autowired
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = "你是一个学习画像构建助手。通过对话了解用户的学习水平和偏好。\n" +
            "规则（必须严格遵守）：\n" +
            "1. 每次只问一个问题\n" +
            "2. 总共只问恰好3个问题，不能多也不能少\n" +
            "3. 前2个问题返回状态profiling，第3个问题用户回答后，第4次调用时必须返回status:complete\n" +
            "4. 需要了解：编程基础、学习目标、每周学习时长\n" +
            "还在提问时返回：{\"status\":\"profiling\",\"type\":\"question\",\"text\":\"问题\",\"options\":[\"选项1\",\"选项2\",\"选项3\",\"选项4\"]}\n" +
            "画像完成时（第3个问题回答后）必须返回：{\"status\":\"complete\",\"profile\":{\"level\":\"初级|中级|高级\",\"knowledge_map\":{\"java_basics\":{\"level\":0.5,\"label\":\"Java基础\"}},\"cognitive_style\":{\"type\":\"depth_first\",\"avg_session_min\":30},\"weak_points\":[{\"topic\":\"薄弱点\",\"level\":0.3}],\"learning_goal\":{\"target\":\"学习目标\"}}}";

    @Override
    public String getRole() {
        return "ProfileAgent";
    }

    @Override
    public String buildSystemPrompt(AgentContext ctx) {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        try {
            String systemPrompt = buildSystemPrompt(input.getContext());
            List<Map<String, String>> messages = input.getConversationHistory();

            String response = llmClient.chat(systemPrompt, messages);

            String jsonStr = extractJson(response);
            JSONObject parsed = JSON.parseObject(jsonStr);

            JsonNode structuredData = objectMapper.readTree(jsonStr);

            boolean isComplete = response.contains("\"status\":\"complete\"");

            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus(isComplete ? "complete" : "profiling");
            output.setStructuredData(structuredData);
            output.setRawResponse(response);

            return output;
        } catch (Exception e) {
            log.error("ProfileAgent execution failed", e);
            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("error");
            output.setRawResponse("画像构建失败: " + e.getMessage());
            return output;
        }
    }

    /**
     * 构建强制完成的画像（当对话轮次超限时使用）
     */
    public AgentOutput buildForcedProfile() {
        JSONObject forcedProfile = new JSONObject();
        forcedProfile.put("status", "complete");
        JSONObject profileData = new JSONObject();
        profileData.put("level", "初级");
        JSONObject knowledgeMap = new JSONObject();
        JSONObject javaBasics = new JSONObject();
        javaBasics.put("level", 0.5);
        javaBasics.put("label", "Java基础");
        knowledgeMap.put("java_basics", javaBasics);
        profileData.put("knowledge_map", knowledgeMap);
        JSONObject cognitiveStyle = new JSONObject();
        cognitiveStyle.put("type", "depth_first");
        cognitiveStyle.put("avg_session_min", 30);
        profileData.put("cognitive_style", cognitiveStyle);
        profileData.put("weak_points", new JSONArray());
        JSONObject learningGoal = new JSONObject();
        learningGoal.put("target", "学习Java");
        profileData.put("learning_goal", learningGoal);
        forcedProfile.put("profile", profileData);

        AgentOutput output = new AgentOutput();
        output.setAgentRole(getRole());
        output.setStatus("complete");
        try {
            output.setStructuredData(objectMapper.readTree(forcedProfile.toJSONString()));
        } catch (Exception e) {
            log.warn("Failed to parse forced profile, using empty node", e);
            try {
                output.setStructuredData(objectMapper.readTree("{}"));
            } catch (Exception ex) {
                // should never happen
                output.setStructuredData(objectMapper.createObjectNode());
            }
        }
        output.setRawResponse(forcedProfile.toJSONString());
        return output;
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String text) {
        int s = text.indexOf("```json"), e = text.lastIndexOf("```");
        if (s != -1 && e > s) return text.substring(s + 7, e).trim();
        s = text.indexOf("{");
        e = text.lastIndexOf("}");
        if (s != -1 && e > s) return text.substring(s, e + 1).trim();
        return text;
    }
}
