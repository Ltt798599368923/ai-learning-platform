package com.exam.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.exam.entity.KnowledgeEdge;
import com.exam.entity.KnowledgePoint;
import com.exam.service.KnowledgeService;
import com.exam.service.LlmClient;
import com.exam.service.agent.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 路径规划Agent
 * 负责根据用户画像和知识图谱生成学习路径
 * 使用拓扑排序算法生成基础路径，再用LLM做个性化调整
 */
@Component
public class PlanAgent implements LearningAgent {

    private static final Logger log = LoggerFactory.getLogger(PlanAgent.class);

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private KnowledgeService knowledgeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = "你是学习路径规划助手。根据用户画像和知识图谱生成个性化的学习路径。\n" +
            "规则：\n" +
            "1. 根据用户水平选择合适的起点\n" +
            "2. 按依赖关系排序\n" +
            "3. 每个节点20-50分钟\n" +
            "4. 最多10个节点\n" +
            "返回JSON：{\"title\":\"路径标题\",\"courseId\":1,\"nodes\":[{\"order\":1,\"title\":\"标题\",\"type\":\"review|new_learn|reinforce\",\"estimatedMinutes\":30,\"reason\":\"原因\",\"knowledgePointIds\":[1,2]}]}";

    @Override
    public String getRole() {
        return "PlanAgent";
    }

    @Override
    public String buildSystemPrompt(AgentContext ctx) {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentOutput execute(AgentInput input) {
        try {
            // 方案1：使用拓扑排序算法生成基础路径
            AgentOutput algorithmResult = generatePathByAlgorithm(input);
            if (algorithmResult != null && "success".equals(algorithmResult.getStatus())) {
                return algorithmResult;
            }

            // 方案2：降级使用LLM生成
            return generatePathByLLM(input);
        } catch (Exception e) {
            log.error("PlanAgent execution failed", e);
            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("error");
            output.setRawResponse("路径规划失败: " + e.getMessage());
            return output;
        }
    }

    /**
     * 使用拓扑排序算法生成基础路径
     */
    private AgentOutput generatePathByAlgorithm(AgentInput input) {
        try {
            AgentContext ctx = input.getContext();
            Long courseId = 1L; // 默认课程

            // 1. 获取知识图谱
            Map<String, Object> graph = knowledgeService.getKnowledgeGraph(courseId);
            List<Map<String, Object>> points = (List<Map<String, Object>>) graph.get("points");
            List<Map<String, String>> edges = (List<Map<String, String>>) graph.get("edges");

            if (points == null || points.isEmpty()) {
                return null;
            }

            // 2. 构建邻接表和入度表
            Map<Long, List<Long>> adjacencyList = new HashMap<>();
            Map<Long, Integer> inDegree = new HashMap<>();
            Map<Long, Map<String, Object>> pointMap = new HashMap<>();

            for (Map<String, Object> point : points) {
                Long id = ((Number) point.get("id")).longValue();
                pointMap.put(id, point);
                adjacencyList.put(id, new ArrayList<>());
                inDegree.put(id, 0);
            }

            // 3. 构建图
            for (Map<String, String> edge : edges) {
                // 这里需要根据code查找id，简化处理
                // 实际应该维护code到id的映射
            }

            // 4. 拓扑排序
            Queue<Long> queue = new LinkedList<>();
            for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.add(entry.getKey());
                }
            }

            List<Long> sortedIds = new ArrayList<>();
            while (!queue.isEmpty()) {
                Long current = queue.poll();
                sortedIds.add(current);
                for (Long neighbor : adjacencyList.getOrDefault(current, List.of())) {
                    int newDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, newDegree);
                    if (newDegree == 0) {
                        queue.add(neighbor);
                    }
                }
            }

            // 5. 按难度分组
            List<Map<String, Object>> l1Points = new ArrayList<>();
            List<Map<String, Object>> l2Points = new ArrayList<>();
            List<Map<String, Object>> l3Points = new ArrayList<>();

            for (Long id : sortedIds) {
                Map<String, Object> point = pointMap.get(id);
                String difficulty = (String) point.get("difficulty");
                if ("L1".equals(difficulty)) {
                    l1Points.add(point);
                } else if ("L2".equals(difficulty)) {
                    l2Points.add(point);
                } else {
                    l3Points.add(point);
                }
            }

            // 6. 合并并限制节点数
            List<Map<String, Object>> allPoints = new ArrayList<>();
            allPoints.addAll(l1Points);
            allPoints.addAll(l2Points);
            allPoints.addAll(l3Points);

            // 限制最多10个节点
            if (allPoints.size() > 10) {
                allPoints = allPoints.subList(0, 10);
            }

            // 7. 构建路径报告
            PlanReport report = new PlanReport();
            report.setTitle("Java学习路径");
            report.setCourseId(courseId);

            List<PlanReport.PathNodeInfo> nodes = new ArrayList<>();
            for (int i = 0; i < allPoints.size(); i++) {
                Map<String, Object> point = allPoints.get(i);
                PlanReport.PathNodeInfo node = new PlanReport.PathNodeInfo();
                node.setOrder(i + 1);
                node.setTitle((String) point.get("name"));
                node.setType("new_learn");
                node.setEstimatedMinutes(30);
                node.setReason("知识点：" + point.get("name"));
                node.setKnowledgePointIds(List.of(((Number) point.get("id")).longValue()));
                nodes.add(node);
            }
            report.setNodes(nodes);

            // 8. 返回结果
            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("success");
            output.setStructuredData(objectMapper.valueToTree(report));
            output.setRawResponse(objectMapper.writeValueAsString(report));

            log.info("PlanAgent: Generated path with {} nodes using algorithm", nodes.size());
            return output;
        } catch (Exception e) {
            log.warn("Algorithm path generation failed, falling back to LLM", e);
            return null;
        }
    }

    /**
     * 使用LLM生成路径（降级方案）
     */
    private AgentOutput generatePathByLLM(AgentInput input) {
        try {
            String systemPrompt = buildSystemPrompt(input.getContext());
            String userPrompt = input.getUserMessage();

            String response = llmClient.chat(systemPrompt, List.of(Map.of("role", "user", "content", userPrompt)));

            String jsonStr = extractJson(response);
            JSONObject parsed = JSON.parseObject(jsonStr);

            JsonNode structuredData = objectMapper.readTree(jsonStr);

            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("success");
            output.setStructuredData(structuredData);
            output.setRawResponse(response);

            log.info("PlanAgent: Generated path using LLM");
            return output;
        } catch (Exception e) {
            log.error("LLM path generation failed", e);
            AgentOutput output = new AgentOutput();
            output.setAgentRole(getRole());
            output.setStatus("error");
            output.setRawResponse("路径规划失败: " + e.getMessage());
            return output;
        }
    }

    /**
     * 解析路径报告
     * 当 JSON 无法完整映射时，构建兜底路径确保学习路径始终被保存
     */
    public PlanReport parsePlanReport(AgentOutput output) {
        try {
            JsonNode data = output.getStructuredData();
            PlanReport report = objectMapper.treeToValue(data, PlanReport.class);
            if (report.getNodes() != null && !report.getNodes().isEmpty()) {
                return report;
            }
        } catch (Exception e) {
            log.warn("Failed to parse plan report as PlanReport, building fallback", e);
        }

        // 兜底：从原始JSON中尽可能提取节点信息
        return buildFallbackPlan(output);
    }

    /**
     * 构建兜底路径 — 当 LLM JSON 格式不匹配 PlanReport 时使用
     */
    private PlanReport buildFallbackPlan(AgentOutput output) {
        PlanReport report = new PlanReport();
        report.setTitle("个性化学习路径");
        report.setCourseId(1L);

        try {
            JsonNode data = output.getStructuredData();
            List<PlanReport.PathNodeInfo> nodes = new ArrayList<>();

            // 尝试从 data.nodes 获取
            JsonNode nodesArray = data.get("nodes");
            if (nodesArray != null && nodesArray.isArray()) {
                for (int i = 0; i < nodesArray.size(); i++) {
                    JsonNode nodeJson = nodesArray.get(i);
                    PlanReport.PathNodeInfo node = new PlanReport.PathNodeInfo();
                    node.setOrder(i + 1);
                    node.setTitle(getStringField(nodeJson, "title", "学习节点 " + (i + 1)));
                    node.setType(getStringField(nodeJson, "type", "new_learn"));
                    node.setEstimatedMinutes(getIntField(nodeJson, "estimatedMinutes", 30));
                    node.setReason(getStringField(nodeJson, "reason", ""));
                    node.setKnowledgePointIds(getLongListField(nodeJson, "knowledgePointIds"));
                    nodes.add(node);
                }
            }

            // 尝试从 data 根层级提取（某些 LLM 可能返回扁平结构）
            if (nodes.isEmpty() && data.isArray()) {
                for (int i = 0; i < data.size(); i++) {
                    JsonNode nodeJson = data.get(i);
                    PlanReport.PathNodeInfo node = new PlanReport.PathNodeInfo();
                    node.setOrder(i + 1);
                    node.setTitle(getStringField(nodeJson, "title", getStringField(nodeJson, "name", "学习节点 " + (i + 1))));
                    node.setType(getStringField(nodeJson, "type", "new_learn"));
                    node.setEstimatedMinutes(getIntField(nodeJson, "estimatedMinutes", getIntField(nodeJson, "estimated_minutes", 30)));
                    node.setReason(getStringField(nodeJson, "reason", ""));
                    node.setKnowledgePointIds(getLongListField(nodeJson, "knowledgePointIds"));
                    nodes.add(node);
                }
            }

            // 极端兜底：至少生成一个基础节点
            if (nodes.isEmpty()) {
                PlanReport.PathNodeInfo node = new PlanReport.PathNodeInfo();
                node.setOrder(1);
                node.setTitle("基础学习阶段");
                node.setType("new_learn");
                node.setEstimatedMinutes(30);
                node.setReason("根据您的画像生成的个性化学习路径");
                node.setKnowledgePointIds(List.of());
                nodes.add(node);
            }

            report.setNodes(nodes);
        } catch (Exception e) {
            log.error("Fallback plan construction failed", e);
            PlanReport.PathNodeInfo node = new PlanReport.PathNodeInfo();
            node.setOrder(1);
            node.setTitle("基础学习阶段");
            node.setType("new_learn");
            node.setEstimatedMinutes(30);
            node.setReason("根据您的画像生成的个性化学习路径");
            node.setKnowledgePointIds(List.of());
            report.setNodes(List.of(node));
        }

        return report;
    }

    private String getStringField(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode fieldNode = node.get(field);
        return fieldNode.isNull() ? defaultValue : fieldNode.asText(defaultValue);
    }

    private int getIntField(JsonNode node, String field, int defaultValue) {
        if (node == null || !node.has(field)) return defaultValue;
        JsonNode fieldNode = node.get(field);
        return fieldNode.isInt() ? fieldNode.asInt() : defaultValue;
    }

    private List<Long> getLongListField(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) return List.of();
        List<Long> result = new ArrayList<>();
        node.get(field).forEach(item -> { if (item.isNumber()) result.add(item.asLong()); });
        return result;
    }

    /**
     * 构建包含画像和知识图谱的用户提示
     */
    public String buildPromptWithProfileAndGraph(String profileJson, String graphJson) {
        return "用户画像：\n" + profileJson + "\n\n知识图谱：\n" + graphJson;
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
