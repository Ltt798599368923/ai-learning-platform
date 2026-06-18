package com.exam.controller;

import com.exam.common.Result;
import com.exam.entity.LearningPath;
import com.exam.entity.PathNode;
import com.exam.entity.GeneratedResource;
import com.exam.service.LearningPathService;
import com.exam.service.GeneratedResourceService;
import com.exam.service.agent.GenAgent;
import com.exam.mapper.PathNodeKpMapper;
import com.exam.mapper.KnowledgePointMapper;
import com.exam.entity.KnowledgePoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 学习路径控制器
 * 处理学习路径相关的HTTP请求
 */
@RestController
@RequestMapping("/api/learning-path")
@Tag(name = "学习路径管理", description = "学习路径相关操作，包括路径的创建、查询和节点管理")
public class LearningPathController {

    private static final Logger log = LoggerFactory.getLogger(LearningPathController.class);

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private GeneratedResourceService generatedResourceService;

    @Autowired
    private GenAgent genAgent;

    @Autowired
    private PathNodeKpMapper pathNodeKpMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @PostMapping
    @Operation(summary = "创建学习路径", description = "创建新的学习路径，A的Agent写入路径使用")
    public Result<Void> createLearningPath(@RequestBody LearningPath path) {
        learningPathService.createLearningPath(path);
        
        // 异步预生成资源（不阻塞响应）
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始为路径 {} 后台预生成资源...", path.getId());
                preGenerateResources(path);
            } catch (Exception e) {
                log.error("后台预生成资源失败", e);
            }
        });
        
        return Result.success(null);
    }
    
    /**
     * 后台预生成路径中所有节点的资源
     */
    private void preGenerateResources(LearningPath path) {
        if (path.getNodes() == null || path.getNodes().isEmpty()) {
            return;
        }
        
        for (PathNode node : path.getNodes()) {
            // 检查是否已有资源
            List<GeneratedResource> existing = generatedResourceService.getResourcesByPathNodeId(node.getId());
            if (!existing.isEmpty()) {
                continue;
            }
            
            // 获取知识点信息
            String kpInfo = "";
            try {
                List<Long> kpIds = pathNodeKpMapper.selectKpIdsByPathNodeId(node.getId());
                if (kpIds != null && !kpIds.isEmpty()) {
                    kpInfo = kpIds.stream()
                            .map(id -> knowledgePointMapper.selectById(id))
                            .filter(kp -> kp != null)
                            .map(kp -> kp.getName() + ": " + kp.getDescription())
                            .collect(Collectors.joining("\n"));
                }
            } catch (Exception e) {
                log.warn("获取知识点失败: {}", node.getId());
            }
            
            // 并行生成三种资源
            String finalKpInfo = kpInfo;
            CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> genAndSave(node, "document", finalKpInfo)),
                CompletableFuture.runAsync(() -> genAndSave(node, "quiz", finalKpInfo)),
                CompletableFuture.runAsync(() -> genAndSave(node, "mindmap", finalKpInfo))
            ).join();
            
            log.info("节点 {} 资源预生成完成", node.getId());
        }
    }

    @GetMapping("/{pathId}")
    @Operation(summary = "获取路径详情", description = "获取学习路径详情含节点，C的前端读取路径使用")
    public Result<LearningPath> getLearningPathDetail(
            @Parameter(description = "路径ID") @PathVariable Long pathId) {
        return Result.success(learningPathService.getLearningPathDetail(pathId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户所有路径", description = "获取指定用户的所有学习路径列表")
    public Result<List<LearningPath>> getLearningPathsByUser(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        return Result.success(learningPathService.getLearningPathsByUser(userId));
    }

    @GetMapping("/user/{userId}/course/{courseId}")
    @Operation(summary = "获取用户在某课程的活跃路径", description = "获取用户在指定课程的活跃学习路径")
    public Result<LearningPath> getActiveLearningPath(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            @Parameter(description = "课程ID") @PathVariable Long courseId) {
        return Result.success(learningPathService.getActiveLearningPath(userId, courseId));
    }

    @PutMapping("/{pathId}/status")
    @Operation(summary = "更新路径状态", description = "更新学习路径的状态")
    public Result<Void> updatePathStatus(
            @Parameter(description = "路径ID") @PathVariable Long pathId,
            @Parameter(description = "状态") @RequestParam String status) {
        learningPathService.updatePathStatus(pathId, status);
        return Result.success(null);
    }

    @PostMapping("/{pathId}/nodes")
    @Operation(summary = "添加路径节点", description = "向学习路径中添加新的节点")
    public Result<Void> addPathNode(
            @Parameter(description = "路径ID") @PathVariable Long pathId,
            @RequestBody PathNode node) {
        node.setPathId(pathId);
        learningPathService.addPathNode(node);
        return Result.success(null);
    }

    @PutMapping("/nodes/{nodeId}/status")
    @Operation(summary = "更新节点状态", description = "更新路径节点的状态")
    public Result<Void> updateNodeStatus(
            @Parameter(description = "节点ID") @PathVariable Long nodeId,
            @Parameter(description = "状态") @RequestParam String status) {
        learningPathService.updateNodeStatus(nodeId, status);
        return Result.success(null);
    }

    @DeleteMapping("/{pathId}")
    @Operation(summary = "删除学习路径", description = "删除指定的学习路径及其所有节点")
    public Result<Void> deleteLearningPath(
            @Parameter(description = "路径ID") @PathVariable Long pathId) {
        learningPathService.deleteLearningPath(pathId);
        return Result.success(null);
    }

    @PostMapping("/{pathId}/generate-resources")
    @Operation(summary = "生成学习资源", description = "为学习路径的所有节点AI生成学习资源")
    public Result<String> generateResources(
            @Parameter(description = "路径ID") @PathVariable Long pathId) {
        LearningPath detail = learningPathService.getLearningPathDetail(pathId);
        if (detail == null || detail.getNodes() == null || detail.getNodes().isEmpty()) {
            return Result.error("学习路径无节点");
        }

        int generated = 0;
        for (PathNode node : detail.getNodes()) {
            // 检查是否已有资源
            List<GeneratedResource> existing = generatedResourceService.getResourcesByPathNodeId(node.getId());
            if (!existing.isEmpty()) {
                log.info("Node {} already has {} resources, skipping", node.getId(), existing.size());
                continue;
            }

            // 获取知识点信息
            String kpInfo = "";
            try {
                List<Long> kpIds = pathNodeKpMapper.selectKpIdsByPathNodeId(node.getId());
                if (kpIds != null && !kpIds.isEmpty()) {
                    kpInfo = kpIds.stream()
                            .map(id -> knowledgePointMapper.selectById(id))
                            .filter(kp -> kp != null)
                            .map(kp -> kp.getName() + ": " + kp.getDescription())
                            .collect(Collectors.joining("\n"));
                }
            } catch (Exception e) {
                log.warn("Failed to get knowledge points for node {}", node.getId());
            }

            // 生成三种资源
            genAndSave(node, "document", kpInfo);
            genAndSave(node, "quiz", kpInfo);
            genAndSave(node, "mindmap", kpInfo);
            generated++;
        }

        return Result.success("已为 " + generated + " 个节点生成学习资源");
    }

    private void genAndSave(PathNode node, String type, String kpInfo) {
        try {
            var output = genAgent.generateResource(node.getTitle(), type, kpInfo, node.getId());
            if ("success".equals(output.getStatus())) {
                var report = genAgent.parseResourceReport(output, node.getId(), type);
                if (report != null) {
                    GeneratedResource resource = new GeneratedResource();
                    resource.setPathNodeId(node.getId());
                    resource.setTitle(report.getTitle());
                    resource.setResourceType(type);
                    resource.setContentJson(report.getContent());
                    resource.setDifficulty("L1");
                    generatedResourceService.batchCreateResources(List.of(resource));
                    log.info("Generated {} resource for node {}", type, node.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate {} resource for node {}", type, node.getId(), e);
        }
    }
}
