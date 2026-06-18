package com.exam.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.exam.entity.GeneratedResource;
import com.exam.service.GeneratedResourceService;
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

/**
 * 资源生成Agent
 * 负责根据知识点生成学习资源（文档、练习题、思维导图）
 * 优先使用预存的资源，没有再调LLM生成
 */
@Component
public class GenAgent implements LearningAgent {

    private static final Logger log = LoggerFactory.getLogger(GenAgent.class);

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private GeneratedResourceService generatedResourceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = "你是学习资源生成助手。根据要求生成学习资料。\n" +
            "文档：{\"type\":\"document\",\"title\":\"标题\",\"content\":\"# 标题\\n\\nMarkdown内容\"}\n" +
            "练习题：{\"type\":\"quiz\",\"title\":\"标题\",\"content\":{\"questions\":[{\"content\":\"题目\",\"type\":\"CHOICE\",\"difficulty\":\"EASY\",\"options\":[{\"content\":\"A\",\"isCorrect\":true}],\"analysis\":\"解析\"}]}}\n" +
            "思维导图：{\"type\":\"mindmap\",\"title\":\"标题\",\"content\":{\"topic\":\"主题\",\"children\":[{\"topic\":\"子主题\"}]}}";

    @Override
    public String getRole() {
        return "GenAgent";
    }

    @Override
    public String buildSystemPrompt(AgentContext ctx) {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        try {
            String systemPrompt = buildSystemPrompt(input.getContext());
            String userPrompt = input.getUserMessage();

            String response = llmClient.chat(systemPrompt, List.of(Map.of("role", "user", "content", userPrompt)));
            log.info("GenAgent raw response: {}", response);

            String jsonStr = extractJson(response);
            log.info("GenAgent extracted JSON: {}", jsonStr);

            JsonNode structuredData;
            try {
                structuredData = objectMapper.readTree(jsonStr);
            } catch (Exception jsonEx) {
                log.warn("JSON解析失败，尝试构建默认资源: {}", jsonEx.getMessage());
                // 构建默认资源
                structuredData = buildDefaultResource(input.getUserMessage(), jsonStr);
            }

            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("success");
            output.setStructuredData(structuredData);
            output.setRawResponse(response);

            return output;
        } catch (Exception e) {
            log.error("GenAgent execution failed", e);
            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("error");
            output.setRawResponse("资源生成失败: " + e.getMessage());
            return output;
        }
    }

    /**
     * 当JSON解析失败时，构建默认资源
     */
    private JsonNode buildDefaultResource(String prompt, String rawText) {
        try {
            // 根据prompt判断资源类型
            if (prompt.contains("练习题") || prompt.contains("quiz")) {
                JSONObject quiz = new JSONObject();
                quiz.put("title", "练习题");
                quiz.put("type", "quiz");
                JSONObject content = new JSONObject();
                JSONArray questions = new JSONArray();
                JSONObject q1 = new JSONObject();
                q1.put("content", "关于这个知识点，以下说法正确的是？");
                q1.put("type", "CHOICE");
                q1.put("difficulty", "EASY");
                JSONArray options = new JSONArray();
                options.add(new JSONObject() {{ put("content", "选项A"); put("isCorrect", true); }});
                options.add(new JSONObject() {{ put("content", "选项B"); put("isCorrect", false); }});
                q1.put("options", options);
                q1.put("analysis", "请根据实际内容选择正确答案");
                questions.add(q1);
                content.put("questions", questions);
                quiz.put("content", content);
                return objectMapper.readTree(quiz.toJSONString());
            } else if (prompt.contains("思维导图") || prompt.contains("mindmap")) {
                JSONObject mindmap = new JSONObject();
                mindmap.put("title", "思维导图");
                mindmap.put("type", "mindmap");
                JSONObject content = new JSONObject();
                content.put("topic", "知识点");
                content.put("children", new JSONArray());
                mindmap.put("content", content);
                return objectMapper.readTree(mindmap.toJSONString());
            } else {
                // 默认文档
                JSONObject doc = new JSONObject();
                doc.put("title", "学习文档");
                doc.put("type", "document");
                doc.put("content", rawText.length() > 500 ? rawText.substring(0, 500) + "..." : rawText);
                return objectMapper.readTree(doc.toJSONString());
            }
        } catch (Exception e) {
            log.error("Failed to build default resource", e);
            // 返回最基本的JSON
            try {
                return objectMapper.readTree("{\"title\":\"资源\",\"content\":\"内容生成中...\"}");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * 为指定节点生成单个资源
     * 优先使用预存资源，没有再调LLM
     */
    public AgentOutput generateResource(String nodeTitle, String resourceType, String kpInfo, Long pathNodeId) {
        // 1. 先检查是否有预存资源（根据标题匹配）
        GeneratedResource existing = findExistingResourceByTitle(nodeTitle, resourceType);
        if (existing != null) {
            log.info("Using pre-existing resource for node: {}, type: {}", nodeTitle, resourceType);
            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("success");
            output.setStructuredData(existing.getContentJson());
            output.setRawResponse("使用预存资源");
            return output;
        }

        // 2. 没有预存资源，调LLM生成
        log.info("Generating new resource for node: {}, type: {}", nodeTitle, resourceType);
        String typeDesc = switch (resourceType) {
            case "document" -> "Markdown格式的讲解文档";
            case "quiz" -> "3道练习题";
            case "mindmap" -> "思维导图树形JSON";
            default -> resourceType;
        };
        String prompt = "请为以下节点生成" + typeDesc + "：\n节点：" + nodeTitle + "\n知识点：" + (kpInfo.isEmpty() ? "无" : kpInfo);

        AgentInput input = new AgentInput();
        input.setUserMessage(prompt);
        return execute(input);
    }

    /**
     * 根据标题查找已存在的资源
     */
    private GeneratedResource findExistingResourceByTitle(String nodeTitle, String resourceType) {
        try {
            // 使用模糊匹配标题
            List<GeneratedResource> resources = generatedResourceService.getResourcesByTitle(nodeTitle);
            return resources.stream()
                    .filter(r -> resourceType.equals(r.getResourceType()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to find existing resource by title", e);
            return null;
        }
    }

    /**
     * 解析资源报告
     */
    public ResourceReport parseResourceReport(AgentOutput output, Long pathNodeId, String resourceType) {
        try {
            JsonNode data = output.getStructuredData();
            ResourceReport report = new ResourceReport();
            report.setPathNodeId(pathNodeId);
            report.setResourceType(resourceType);
            report.setTitle(data.has("title") ? data.get("title").asText() : "");
            report.setContent(data.has("content") ? data.get("content") : data);
            report.setDifficulty("L1");
            return report;
        } catch (Exception e) {
            log.error("Failed to parse resource report", e);
            return null;
        }
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 尝试从 ```json ... ``` 代码块中提取
        int s = text.indexOf("```json");
        int e = text.indexOf("```", s + 7);
        if (s != -1 && e > s) {
            return text.substring(s + 7, e).trim();
        }
        
        // 尝试从 ``` ... ``` 代码块中提取
        s = text.indexOf("```");
        e = text.indexOf("```", s + 3);
        if (s != -1 && e > s) {
            String candidate = text.substring(s + 3, e).trim();
            if (candidate.startsWith("{") || candidate.startsWith("[")) {
                return candidate;
            }
        }
        
        // 尝试找到完整的JSON对象（从第一个{到最后一个}）
        s = text.indexOf("{");
        if (s != -1) {
            // 找到匹配的结束括号
            int depth = 0;
            for (int i = s; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                if (depth == 0) {
                    return text.substring(s, i + 1).trim();
                }
            }
        }
        
        // 如果都没有找到，返回原文
        return text;
    }
}
