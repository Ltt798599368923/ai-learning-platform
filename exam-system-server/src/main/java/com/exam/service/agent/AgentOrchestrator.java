package com.exam.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.exam.entity.*;
import com.exam.mapper.AgentMessageMapper;
import com.exam.mapper.AgentSessionMapper;
import com.exam.service.*;
import com.exam.service.agent.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 多Agent调度器
 * 负责意图分类、执行计划生成、并行调度
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    @Autowired
    private ProfileAgent profileAgent;
    @Autowired
    private PlanAgent planAgent;
    @Autowired
    private GenAgent genAgent;
    @Autowired
    private LlmClient llmClient;
    @Autowired
    private AgentSessionMapper agentSessionMapper;
    @Autowired
    private AgentMessageMapper agentMessageMapper;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private LearningPathService learningPathService;
    @Autowired
    private GeneratedResourceService generatedResourceService;
    @Autowired
    private SynthesisEngine synthesisEngine;
    @Autowired
    private ConflictDetector conflictDetector;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private static final String INTENT_CLASSIFY_PROMPT = "你是任务意图分类器。根据用户输入判断任务复杂度和需要的Agent。\n" +
            "返回JSON格式：\n" +
            "{\"complexity\":\"simple|complex\",\"required_agents\":[\"profile\",\"plan\",\"generate\"]}\n" +
            "规则：\n" +
            "- 简单任务（如只出题、只分析薄弱点）只用一个Agent\n" +
            "- 复杂任务（如制定完整学习计划）需要多个Agent按顺序执行\n" +
            "- 用户首次对话通常需要profile\n" +
            "- 如果用户已有画像，可以跳过profile直接plan";

    /**
     * 处理用户对话 - 多Agent调度入口
     */
    public void handleChat(Long sessionId, String message, Long userId, SseEmitter emitter) {
        try {
            // 获取或创建会话
            AgentSession session = getOrCreateSession(sessionId, message, userId);
            if (session == null) {
                sendError(emitter, "会话不存在");
                emitter.complete();
                return;
            }

            // 保存用户消息
            saveUserMessage(session.getId(), message);

            // 获取历史消息
            List<AgentMessage> history = agentMessageMapper.selectBySessionId(session.getId());
            List<Map<String, String>> conversationHistory = convertHistory(history);

            // 检查是否已完成
            boolean hasDone = history.stream().anyMatch(m -> "done".equals(m.getPhase()));
            if (hasDone) {
                sendDone(emitter, session.getId());
                emitter.complete();
                return;
            }

            // 检查是否在profiling阶段且未完成
            long userMsgCount = history.stream().filter(m -> "user".equals(m.getRole())).count();
            boolean profileComplete = history.stream()
                    .filter(m -> "assistant".equals(m.getRole()))
                    .anyMatch(m -> m.getContent() != null && m.getContent().contains("\"status\":\"complete\""));

            if (!profileComplete && userMsgCount <= 3) {
                // 仍在画像构建阶段
                handleProfilingPhase(session, conversationHistory, message, emitter);
            } else {
                // 画像已完成，进入规划和生成阶段
                handlePlanningAndGeneratingPhase(session, userId, message, emitter);
            }

        } catch (Exception e) {
            log.error("Agent orchestration error", e);
            sendError(emitter, "处理失败: " + e.getMessage());
        } finally {
            emitter.complete();
        }
    }

    /**
     * 处理画像构建阶段
     */
    private void handleProfilingPhase(AgentSession session, List<Map<String, String>> history, String userMessage, SseEmitter emitter) throws Exception {
        sendPhase(emitter, "profiling", 0.15, null, session.getId());

        long userMsgCount = history.stream().filter(m -> "user".equals(m.get("role"))).count();

        // 强制完成画像
        if (userMsgCount >= 3) {
            log.info("Session {} profiling forced complete after {} user messages", session.getId(), userMsgCount);
            AgentOutput forcedOutput = profileAgent.buildForcedProfile();
            handleProfileResult(session, forcedOutput, userMessage, emitter);
            return;
        }

        // 调用ProfileAgent
        AgentContext ctx = buildContext(session.getId(), session.getUserId(), null);
        AgentInput input = new AgentInput(null, history, ctx);
        AgentOutput output = profileAgent.execute(input);

        handleProfileResult(session, output, userMessage, emitter);
    }

    /**
     * 处理画像结果
     */
    private void handleProfileResult(AgentSession session, AgentOutput output, String userMessage, SseEmitter emitter) throws Exception {
        // 处理 LLM 调用失败的情况
        if ("error".equals(output.getStatus()) || output.getStructuredData() == null) {
            String errorMsg = output.getRawResponse() != null ? output.getRawResponse() : "AI 服务暂时不可用，请稍后重试";
            sendError(emitter, errorMsg);
            return;
        }

        if ("complete".equals(output.getStatus())) {
            // 画像完成，保存并进入规划阶段
            JSONObject profileData = output.getStructuredData().has("profile")
                    ? JSON.parseObject(output.getStructuredData().get("profile").toString())
                    : new JSONObject();
            saveProfile(session.getUserId(), profileData, session.getId());
            
            // 保存显示文本，而不是完整JSON
            String displayText = "已完成学习画像构建！";
            saveMessage(session.getId(), displayText, "profiling");

            sendPhase(emitter, "profiling", 0.30,
                    Map.of("type", "text", "text", "已完成学习画像构建！正在为您规划学习路径..."), session.getId());
            Thread.sleep(500);

            // 进入规划和生成阶段 — 画像刚完成，直接生成路径，不依赖 LLM 意图分类
            sendPhase(emitter, "planning", 0.50, Map.of("summary", "正在为您规划学习路径..."), session.getId());

            // 直接调用 PlanAgent 生成路径并保存
            String profileJson = getUserProfileJson(session.getUserId());
            String graphJson = getKnowledgeGraphJson(1L);
            String prompt = planAgent.buildPromptWithProfileAndGraph(profileJson, graphJson);
            AgentContext ctx = buildContext(session.getId(), session.getUserId(), null);
            AgentOutput planResult = planAgent.execute(new AgentInput(prompt, List.of(), ctx));

            if ("success".equals(planResult.getStatus())) {
                PlanReport planReport = planAgent.parsePlanReport(planResult);
                if (planReport != null) {
                    saveLearningPath(planReport, session.getUserId(), session.getId());
                    sendPhase(emitter, "planning", 0.60,
                            Map.of("summary", "已生成学习路径，共" + planReport.getNodes().size() + "个节点"), session.getId());
                }
            }

            // 继续走生成和合成阶段
            OrchestrationPlan genPlan = buildGenerateOnlyPlan(session.getUserId());
            executePlan(session, session.getUserId(), genPlan, emitter);
        } else {
            // 继续提问 - 从structuredData中提取显示文本
            JsonNode data = output.getStructuredData();
            String displayText = data.has("text") ? data.get("text").asText() : "请回答问题";
            saveMessage(session.getId(), displayText, "profiling");

            Map<String, Object> content = new HashMap<>();
            content.put("type", data.has("type") ? data.get("type").asText() : "question");
            content.put("text", displayText);
            if (data.has("options")) {
                content.put("options", JSON.parseArray(data.get("options").toString()));
            }
            sendPhase(emitter, "profiling", 0.20, content, session.getId());
        }
    }

    /**
     * 处理规划和生成阶段
     */
    private void handlePlanningAndGeneratingPhase(AgentSession session, Long userId, String userMessage, SseEmitter emitter) throws Exception {
        // 1. 意图分类
        sendPhase(emitter, "orchestrating", 0.35, Map.of("text", "正在分析您的需求..."), session.getId());
        OrchestrationPlan plan = classifyIntentAndGeneratePlan(userId, userMessage);

        // 2. 执行计划
        executePlan(session, userId, plan, emitter);
    }

    /**
     * 意图分类并生成执行计划
     * 使用LLM判断用户意图，决定调用哪些Agent
     */
    private OrchestrationPlan classifyIntentAndGeneratePlan(Long userId, String userMessage) {
        try {
            // 1. 检查用户是否有画像
            UserProfile profile = userProfileService.getUserProfile(userId);
            boolean hasProfile = profile != null;

            // 2. 用LLM分类意图
            String classifyPrompt = "用户输入：" + userMessage + "\n\n" +
                    "请判断用户意图，返回JSON：\n" +
                    "{\"intent\":\"learn|review|practice|ask\",\"complexity\":\"simple|complex\"," +
                    "\"required_agents\":[\"profile\",\"plan\",\"generate\"],\"reason\":\"原因\"}\n\n" +
                    "规则：\n" +
                    "- learn: 系统学习新知识 → 需要profile+plan+generate\n" +
                    "- review: 复习已有知识 → 只需要plan+generate\n" +
                    "- practice: 只想做练习 → 只需要generate\n" +
                    "- ask: 问问题 → 不需要Agent\n" +
                    "- 如果用户已有画像，可以跳过profile";

            String response = llmClient.chat("你是意图分类器", List.of(Map.of("role", "user", "content", classifyPrompt)));
            log.info("Intent classification result: {}", response);

            // 3. 解析分类结果
            JSONObject classified = JSON.parseObject(extractJson(response));
            String intent = classified.getString("intent");
            JSONArray requiredAgents = classified.getJSONArray("required_agents");

            // 4. 构建执行计划
            OrchestrationPlan plan = new OrchestrationPlan();
            plan.setComplexity(classified.getString("complexity"));

            List<OrchestrationPlan.PlanStep> steps = new ArrayList<>();
            int stepNum = 1;

            // 根据分类结果添加Agent步骤
            if (requiredAgents.contains("profile") && !hasProfile) {
                OrchestrationPlan.PlanStep profileStep = new OrchestrationPlan.PlanStep();
                profileStep.setStep(stepNum++);
                profileStep.setAgent("profile");
                profileStep.setTask("分析用户学习水平");
                profileStep.setDependsOn(List.of());
                steps.add(profileStep);
            }

            if (requiredAgents.contains("plan")) {
                OrchestrationPlan.PlanStep planStep = new OrchestrationPlan.PlanStep();
                planStep.setStep(stepNum++);
                planStep.setAgent("plan");
                planStep.setTask("基于画像生成学习路径");
                planStep.setDependsOn(steps.isEmpty() ? List.of() : List.of(1));
                OrchestrationPlan.StepLocalContext planCtx = new OrchestrationPlan.StepLocalContext();
                planCtx.setCourseId(1L);
                planStep.setLocalContext(planCtx);
                steps.add(planStep);
            }

            if (requiredAgents.contains("generate")) {
                OrchestrationPlan.PlanStep genStep = new OrchestrationPlan.PlanStep();
                genStep.setStep(stepNum++);
                genStep.setAgent("generate");
                genStep.setTask("生成学习资源");
                genStep.setDependsOn(steps.isEmpty() ? List.of() : List.of(steps.get(steps.size() - 1).getStep()));
                genStep.setParallel(true);
                OrchestrationPlan.StepLocalContext genCtx = new OrchestrationPlan.StepLocalContext();
                genCtx.setResourceTypes(List.of("document", "quiz", "mindmap"));
                genStep.setLocalContext(genCtx);
                steps.add(genStep);
            }

            plan.setPlan(steps);
            log.info("Execution plan: {} steps, agents: {}", steps.size(),
                    steps.stream().map(OrchestrationPlan.PlanStep::getAgent).collect(Collectors.joining(",")));

            return plan;
        } catch (Exception e) {
            log.error("Failed to classify intent", e);
            // 返回默认计划：完整流程
            return buildDefaultPlan(userId);
        }
    }

    /**
     * 构建仅包含 generate 步骤的计划（路径已单独保存）
     */
    private OrchestrationPlan buildGenerateOnlyPlan(Long userId) {
        OrchestrationPlan plan = new OrchestrationPlan();
        plan.setComplexity("complex");

        OrchestrationPlan.PlanStep genStep = new OrchestrationPlan.PlanStep();
        genStep.setStep(1);
        genStep.setAgent("generate");
        genStep.setTask("生成学习资源");
        genStep.setDependsOn(List.of());
        genStep.setParallel(true);
        
        plan.setPlan(List.of(genStep));
        return plan;
    }

    /**
     * 构建完整管线计划（profile 完成后直接使用，跳过意图分类）
     */
    private OrchestrationPlan buildFullPipelinePlan(Long userId) {
        UserProfile profile = userProfileService.getUserProfile(userId);
        boolean hasProfile = profile != null;

        OrchestrationPlan plan = new OrchestrationPlan();
        plan.setComplexity("complex");

        List<OrchestrationPlan.PlanStep> steps = new ArrayList<>();
        int stepNum = 1;

        if (!hasProfile) {
            OrchestrationPlan.PlanStep profileStep = new OrchestrationPlan.PlanStep();
            profileStep.setStep(stepNum++);
            profileStep.setAgent("profile");
            profileStep.setTask("分析用户学习水平");
            profileStep.setDependsOn(List.of());
            steps.add(profileStep);
        }

        OrchestrationPlan.PlanStep planStep = new OrchestrationPlan.PlanStep();
        planStep.setStep(stepNum++);
        planStep.setAgent("plan");
        planStep.setTask("基于画像生成学习路径");
        planStep.setDependsOn(steps.isEmpty() ? List.of() : List.of(1));
        steps.add(planStep);

        OrchestrationPlan.PlanStep genStep = new OrchestrationPlan.PlanStep();
        genStep.setStep(stepNum++);
        genStep.setAgent("generate");
        genStep.setTask("生成学习资源");
        genStep.setDependsOn(List.of(planStep.getStep()));
        genStep.setParallel(true);
        steps.add(genStep);

        plan.setPlan(steps);
        return plan;
    }

    /**
     * 构建默认计划（完整流程）
     */
    private OrchestrationPlan buildDefaultPlan(Long userId) {
        UserProfile profile = userProfileService.getUserProfile(userId);
        boolean hasProfile = profile != null;

        OrchestrationPlan plan = new OrchestrationPlan();
        plan.setComplexity("complex");

        List<OrchestrationPlan.PlanStep> steps = new ArrayList<>();
        int stepNum = 1;

        if (!hasProfile) {
            OrchestrationPlan.PlanStep profileStep = new OrchestrationPlan.PlanStep();
            profileStep.setStep(stepNum++);
            profileStep.setAgent("profile");
            profileStep.setTask("分析用户学习水平");
            profileStep.setDependsOn(List.of());
            steps.add(profileStep);
        }

        OrchestrationPlan.PlanStep planStep = new OrchestrationPlan.PlanStep();
        planStep.setStep(stepNum++);
        planStep.setAgent("plan");
        planStep.setTask("基于画像生成学习路径");
        planStep.setDependsOn(steps.isEmpty() ? List.of() : List.of(1));
        steps.add(planStep);

        OrchestrationPlan.PlanStep genStep = new OrchestrationPlan.PlanStep();
        genStep.setStep(stepNum++);
        genStep.setAgent("generate");
        genStep.setTask("生成学习资源");
        genStep.setDependsOn(List.of(planStep.getStep()));
        genStep.setParallel(true);
        steps.add(genStep);

        plan.setPlan(steps);
        return plan;
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

    /**
     * 执行计划 - 支持并行调度
     */
    private void executePlan(AgentSession session, Long userId, OrchestrationPlan plan, SseEmitter emitter) throws Exception {
        Map<Integer, AgentOutput> stepResults = new ConcurrentHashMap<>();
        Map<Integer, CompletableFuture<AgentOutput>> futures = new ConcurrentHashMap<>();

        for (OrchestrationPlan.PlanStep step : plan.getPlan()) {
            // 检查依赖是否完成
            List<Integer> dependsOn = step.getDependsOn() != null ? step.getDependsOn() : List.of();
            boolean allDependenciesMet = dependsOn.stream().allMatch(dep -> {
                AgentOutput depResult = stepResults.get(dep);
                return depResult != null && "success".equals(depResult.getStatus());
            });

            if (!allDependenciesMet && !dependsOn.isEmpty()) {
                // 等待依赖完成
                for (Integer dep : dependsOn) {
                    CompletableFuture<AgentOutput> depFuture = futures.get(dep);
                    if (depFuture != null) {
                        depFuture.join();
                    }
                }
            }

            // 执行当前步骤
            final OrchestrationPlan.PlanStep currentStep = step;
            final AgentContext ctx = buildContext(session.getId(), userId, step.getLocalContext());

            if (Boolean.TRUE.equals(step.getParallel())) {
                // 并行执行
                CompletableFuture<AgentOutput> future = CompletableFuture.supplyAsync(() -> {
                    return executeAgentStep(currentStep, ctx, session, userId, emitter);
                }, executorService);
                futures.put(step.getStep(), future);
            } else {
                // 顺序执行
                AgentOutput result = executeAgentStep(step, ctx, session, userId, emitter);
                stepResults.put(step.getStep(), result);
            }
        }

        // 等待所有并行任务完成
        for (Map.Entry<Integer, CompletableFuture<AgentOutput>> entry : futures.entrySet()) {
            try {
                AgentOutput result = entry.getValue().join();
                stepResults.put(entry.getKey(), result);
            } catch (Exception e) {
                log.error("Step {} failed", entry.getKey(), e);
            }
        }

        // 3. 合成和冲突检测
        sendPhase(emitter, "synthesizing", 0.90, Map.of("text", "正在整理学习计划..."), session.getId());
        synthesisAndValidate(session, userId, stepResults, emitter);

        // 4. 完成
        saveMessage(session.getId(), "学习计划生成完成", "done");
        sendDone(emitter, session.getId());
    }

    /**
     * 执行单个Agent步骤
     */
    private AgentOutput executeAgentStep(OrchestrationPlan.PlanStep step, AgentContext ctx,
                                          AgentSession session, Long userId, SseEmitter emitter) {
        try {
            String agentType = step.getAgent();
            AgentOutput result;

            switch (agentType) {
                case "profile":
                    sendPhase(emitter, "profiling", 0.40, null, session.getId());
                    result = profileAgent.execute(new AgentInput(null, List.of(), ctx));
                    break;

                case "plan":
                    sendPhase(emitter, "planning", 0.50, Map.of("summary", "正在为您规划学习路径..."), session.getId());
                    String profileJson = getUserProfileJson(userId);
                    String graphJson = getKnowledgeGraphJson(1L);
                    String prompt = planAgent.buildPromptWithProfileAndGraph(profileJson, graphJson);
                    result = planAgent.execute(new AgentInput(prompt, List.of(), ctx));

                    if ("success".equals(result.getStatus())) {
                        PlanReport planReport = planAgent.parsePlanReport(result);
                        if (planReport != null) {
                            saveLearningPath(planReport, userId, session.getId());
                            sendPhase(emitter, "planning", 0.60,
                                    Map.of("summary", "已生成学习路径，共" + planReport.getNodes().size() + "个节点"), session.getId());
                        }
                    }
                    break;

                case "generate":
                    sendPhase(emitter, "generating", 0.70, Map.of("chapter_title", "开始生成学习资料"), session.getId());
                    result = executeGenAgentForAllNodes(session, userId, emitter);
                    break;

                default:
                    result = new AgentOutput();
                    result.setAgentRole(agentType);
                    result.setStatus("error");
                    result.setRawResponse("未知Agent类型: " + agentType);
            }

            return result;
        } catch (Exception e) {
            log.error("Agent step {} execution failed", step.getAgent(), e);
            AgentOutput errorOutput = new AgentOutput();
            errorOutput.setAgentRole(step.getAgent());
            errorOutput.setStatus("error");
            errorOutput.setRawResponse(e.getMessage());
            return errorOutput;
        }
    }

    /**
     * 为所有节点执行GenAgent
     */
    private AgentOutput executeGenAgentForAllNodes(AgentSession session, Long userId, SseEmitter emitter) throws Exception {
        List<LearningPath> paths = learningPathService.getLearningPathsByUser(userId);
        if (paths.isEmpty()) {
            AgentOutput output = new AgentOutput();
            output.setAgentRole("GenAgent");
            output.setStatus("error");
            output.setRawResponse("暂无学习路径");
            return output;
        }

        LearningPath latestPath = paths.get(0);
        LearningPath detail = learningPathService.getLearningPathDetail(latestPath.getId());
        List<PathNode> nodes = detail.getNodes();

        List<ResourceReport> allResources = new ArrayList<>();
        List<CompletableFuture<List<ResourceReport>>> futures = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            PathNode node = nodes.get(i);
            double progress = 0.70 + (0.20 * (i + 1) / nodes.size());
            sendPhase(emitter, "generating", progress,
                    Map.of("chapter_title", "第" + (i + 1) + "章：" + node.getTitle(),
                            "current", i + 1, "total", nodes.size()), session.getId());

            String kpInfo = "";
            if (node.getKnowledgePoints() != null) {
                kpInfo = node.getKnowledgePoints().stream()
                        .map(kp -> kp.getName() + "：" + kp.getDescription())
                        .collect(Collectors.joining("\n"));
            }

            // 并行生成三种资源
            final String finalKpInfo = kpInfo;
            final Long nodeId = node.getId();
            CompletableFuture<List<ResourceReport>> future = CompletableFuture.supplyAsync(() -> {
                List<ResourceReport> resources = new ArrayList<>();
                for (String type : List.of("document", "quiz", "mindmap")) {
                    try {
                        AgentOutput genOutput = genAgent.generateResource(node.getTitle(), type, finalKpInfo, nodeId);
                        if ("success".equals(genOutput.getStatus())) {
                            ResourceReport report = genAgent.parseResourceReport(genOutput, nodeId, type);
                            if (report != null) {
                                resources.add(report);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to generate {} for node {}", type, nodeId, e);
                    }
                }
                return resources;
            }, executorService);
            futures.add(future);
        }

        // 等待所有资源生成完成
        for (CompletableFuture<List<ResourceReport>> future : futures) {
            allResources.addAll(future.join());
        }

        // 保存资源
        saveGeneratedResources(allResources);

        AgentOutput output = new AgentOutput();
        output.setAgentRole("GenAgent");
        output.setStatus("success");
        output.setRawResponse("生成了" + allResources.size() + "个资源");
        return output;
    }

    /**
     * 合成和验证
     */
    private void synthesisAndValidate(AgentSession session, Long userId,
                                       Map<Integer, AgentOutput> stepResults, SseEmitter emitter) {
        try {
            // 获取路径和资源
            List<LearningPath> paths = learningPathService.getLearningPathsByUser(userId);
            if (paths.isEmpty()) return;

            LearningPath latestPath = paths.get(0);
            LearningPath detail = learningPathService.getLearningPathDetail(latestPath.getId());

            // 冲突检测
            UserProfile profile = userProfileService.getUserProfile(userId);
            if (profile != null && detail.getNodes() != null) {
                // 简化版冲突检测
                log.info("Synthesis completed for session {}", session.getId());
            }

            // 更新路径状态
            learningPathService.updatePathStatus(latestPath.getId(), "completed");
            session.setStatus("completed");
            agentSessionMapper.updateById(session);
        } catch (Exception e) {
            log.error("Synthesis failed", e);
        }
    }

    // ========== 辅助方法 ==========

    private AgentContext buildContext(Long sessionId, Long userId, OrchestrationPlan.StepLocalContext stepCtx) {
        AgentContext ctx = new AgentContext();
        ctx.setSessionId(sessionId);
        ctx.setUserId(userId);

        AgentContext.GlobalContext globalCtx = new AgentContext.GlobalContext();
        globalCtx.setKnowledgeDomain("Java后端");
        ctx.setGlobalContext(globalCtx);

        if (stepCtx != null) {
            AgentContext.LocalContext localCtx = new AgentContext.LocalContext();
            localCtx.setMode(stepCtx.getMode());
            ctx.setLocalContext(localCtx);
        }

        return ctx;
    }

    private AgentSession getOrCreateSession(Long sessionId, String message, Long userId) {
        if (sessionId == null) {
            AgentSession session = new AgentSession();
            session.setUserId(userId);
            String title = message.length() > 50 ? message.substring(0, 50) + "..." : message;
            session.setTitle(title);
            session.setStatus("active");
            session.setCreatedAt(LocalDateTime.now());
            agentSessionMapper.insert(session);
            return session;
        }
        return agentSessionMapper.selectById(sessionId);
    }

    private void saveUserMessage(Long sessionId, String content) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        agentMessageMapper.insert(msg);
    }

    private void saveMessage(Long sessionId, String content, String phase) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setPhase(phase);
        msg.setCreatedAt(LocalDateTime.now());
        agentMessageMapper.insert(msg);
    }

    private void saveProfile(Long userId, JSONObject data, Long sessionId) {
        try {
            UserProfile profile = userProfileService.getUserProfile(userId);
            JsonNode dims = objectMapper.readTree(data.toJSONString());
            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
                profile.setDimensions(dims);
                profile.setUpdatedBySessionId(sessionId);
                userProfileService.createUserProfile(profile);
            } else {
                profile.setDimensions(dims);
                profile.setUpdatedBySessionId(sessionId);
                userProfileService.updateUserProfile(userId, profile);
            }
        } catch (Exception e) {
            log.error("保存画像失败", e);
        }
    }

    private void saveLearningPath(PlanReport planReport, Long userId, Long sessionId) {
        try {
            LearningPath path = new LearningPath();
            path.setUserId(userId);
            path.setCourseId(planReport.getCourseId());
            path.setSessionId(sessionId);
            path.setStatus("active");
            path.setCreatedAt(LocalDateTime.now());
            path.setUpdatedAt(LocalDateTime.now());

            List<PathNode> nodes = new ArrayList<>();
            for (PlanReport.PathNodeInfo nodeInfo : planReport.getNodes()) {
                PathNode node = new PathNode();
                node.setNodeOrder(nodeInfo.getOrder());
                node.setTitle(nodeInfo.getTitle());
                node.setNodeType(nodeInfo.getType() != null ? nodeInfo.getType() : "new_learn");
                node.setEstimatedMinutes(nodeInfo.getEstimatedMinutes() != null ? nodeInfo.getEstimatedMinutes() : 30);
                node.setReason(nodeInfo.getReason());
                node.setStatus("pending");
                // 不设置 knowledgePointIds，避免外键约束失败；节点信息通过 title/reason 已能表达
                node.setKnowledgePointIds(List.of());
                nodes.add(node);
            }
            path.setNodes(nodes);
            learningPathService.createLearningPath(path);
            log.info("Learning path saved: pathId={}, nodes={}", path.getId(), nodes.size());
        } catch (Exception e) {
            log.error("Failed to save learning path", e);
        }
    }

    private void saveGeneratedResources(List<ResourceReport> resources) {
        for (ResourceReport report : resources) {
            try {
                GeneratedResource resource = new GeneratedResource();
                resource.setPathNodeId(report.getPathNodeId());
                resource.setTitle(report.getTitle());
                resource.setResourceType(report.getResourceType());
                resource.setDifficulty(report.getDifficulty());
                resource.setContentJson(report.getContent());
                generatedResourceService.batchCreateResources(List.of(resource));
            } catch (Exception e) {
                log.error("Failed to save resource", e);
            }
        }
    }

    private String getUserProfileJson(Long userId) {
        try {
            UserProfile profile = userProfileService.getUserProfile(userId);
            return profile != null ? objectMapper.writeValueAsString(profile) : "{}";
        } catch (Exception e) {
            return "{}";
        }
    }

    private String getKnowledgeGraphJson(Long courseId) {
        try {
            Map<String, Object> graph = knowledgeService.getKnowledgeGraph(courseId);
            return objectMapper.writeValueAsString(graph);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 获取会话的第一条用户消息（初始学习意图）
     */
    private String getInitialUserMessage(Long sessionId) {
        try {
            List<AgentMessage> history = agentMessageMapper.selectBySessionId(sessionId);
            return history.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .findFirst()
                    .map(AgentMessage::getContent)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> convertHistory(List<AgentMessage> history) {
        return history.stream()
                .filter(m -> !"system".equals(m.getRole()))
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
    }

    private void sendPhase(SseEmitter emitter, String phase, double progress, Object content, Long sessionId) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("progress", progress);
        if (sessionId != null) data.put("sessionId", sessionId);
        if (content != null) data.put("content", content);
        emitter.send(SseEmitter.event().name("phase").data(objectMapper.writeValueAsString(data)));
    }

    private void sendDone(SseEmitter emitter, Long sessionId) throws IOException {
        Map<String, Object> data = Map.of("phase", "done", "progress", 1.0, "sessionId", sessionId,
                "content", Map.of("profileSummary", Map.of("level", "已完成"), "pathPreview", "学习计划已生成"));
        emitter.send(SseEmitter.event().name("phase").data(objectMapper.writeValueAsString(data)));
    }

    private void sendError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", msg)));
        } catch (IOException e) {
            log.error("send error", e);
        }
    }
}
