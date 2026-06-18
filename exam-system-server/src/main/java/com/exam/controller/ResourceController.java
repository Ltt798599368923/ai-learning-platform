package com.exam.controller;

import com.exam.common.Result;
import com.exam.entity.GeneratedResource;
import com.exam.entity.PathNode;
import com.exam.service.GeneratedResourceService;
import com.exam.service.LearningPathService;
import com.exam.service.agent.GenAgent;
import com.exam.mapper.PathNodeKpMapper;
import com.exam.mapper.KnowledgePointMapper;
import com.exam.entity.KnowledgePoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/generated-resources")
@Tag(name = "生成资源管理", description = "AI生成的学习资源相关操作")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    @Autowired
    private GeneratedResourceService generatedResourceService;

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private GenAgent genAgent;

    @Autowired
    private PathNodeKpMapper pathNodeKpMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping
    @Operation(summary = "批量写入生成资源")
    public Result<Void> batchCreateResources(@RequestBody ResourceBatchRequest body) {
        generatedResourceService.batchCreateResources(body.getResources());
        return Result.success(null);
    }

    @Data
    public static class ResourceBatchRequest {
        private List<GeneratedResource> resources;
    }

    @GetMapping
    @Operation(summary = "获取资源列表", description = "根据路径节点ID获取资源，如果没有则后台AI生成")
    public Result<List<GeneratedResource>> getResources(
            @Parameter(description = "路径节点ID") @RequestParam(required = false) Long pathNodeId,
            @Parameter(description = "知识点ID") @RequestParam(required = false) Long knowledgePointId) {
        
        if (pathNodeId != null) {
            List<GeneratedResource> resources = generatedResourceService.getResourcesByPathNodeId(pathNodeId);
            
            // 如果没有资源，触发后台生成并立即返回空列表
            if (resources.isEmpty()) {
                log.info("No resources found for node {}, triggering background generation...", pathNodeId);
                CompletableFuture.runAsync(() -> generateResourcesForNode(pathNodeId));
            }
            
            return Result.success(resources);
        } else if (knowledgePointId != null) {
            return Result.success(generatedResourceService.getResourcesByKnowledgePointId(knowledgePointId));
        } else {
            return Result.error("请提供pathNodeId或knowledgePointId参数");
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取资源详情")
    public Result<GeneratedResource> getResourceById(
            @Parameter(description = "资源ID") @PathVariable Long id) {
        return Result.success(generatedResourceService.getResourceById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除资源")
    public Result<Void> deleteResource(
            @Parameter(description = "资源ID") @PathVariable Long id) {
        generatedResourceService.deleteResource(id);
        return Result.success(null);
    }

    private List<GeneratedResource> generateResourcesForNode(Long pathNodeId) {
        CopyOnWriteArrayList<GeneratedResource> generated = new CopyOnWriteArrayList<>();
        
        try {
            PathNode node = learningPathService.getPathNodeById(pathNodeId);
            if (node == null) {
                log.warn("Path node not found: {}", pathNodeId);
                return new ArrayList<>();
            }

            String kpInfo = getKnowledgePointInfo(pathNodeId);
            String[] types = {"document", "quiz", "mindmap"};
            
            for (String type : types) {
                try {
                    // 检查是否已有该类型资源
                    List<GeneratedResource> existing = generatedResourceService.getResourcesByPathNodeId(pathNodeId);
                    boolean hasType = existing.stream().anyMatch(r -> type.equals(r.getResourceType()));
                    if (hasType) {
                        log.info("Node {} already has {} resource, skipping", pathNodeId, type);
                        continue;
                    }
                    
                    var output = genAgent.generateResource(node.getTitle(), type, kpInfo, pathNodeId);
                    if ("success".equals(output.getStatus())) {
                        var report = genAgent.parseResourceReport(output, pathNodeId, type);
                        if (report != null) {
                            GeneratedResource resource = new GeneratedResource();
                            resource.setPathNodeId(pathNodeId);
                            resource.setTitle(report.getTitle() != null && !report.getTitle().isEmpty() ? report.getTitle() : node.getTitle() + " - " + type);
                            resource.setResourceType(type);
                            resource.setContentJson(report.getContent());
                            resource.setDifficulty("L1");
                            generatedResourceService.batchCreateResources(List.of(resource));
                            generated.add(resource);
                            log.info("Generated {} resource for node {}", type, pathNodeId);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to generate {} for node {}", type, pathNodeId, e);
                    // 即使失败也创建一个默认资源
                    GeneratedResource defaultResource = createDefaultResource(pathNodeId, type, node.getTitle());
                    if (defaultResource != null) {
                        generatedResourceService.batchCreateResources(List.of(defaultResource));
                        generated.add(defaultResource);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate resources for node {}", pathNodeId, e);
        }
        
        return new ArrayList<>(generated);
    }
    
    /**
     * 创建默认资源（当AI生成失败时使用）
     */
    private GeneratedResource createDefaultResource(Long pathNodeId, String type, String nodeTitle) {
        try {
            GeneratedResource resource = new GeneratedResource();
            resource.setPathNodeId(pathNodeId);
            resource.setResourceType(type);
            resource.setDifficulty("L1");
            
            switch (type) {
                case "document":
                    resource.setTitle(nodeTitle + " - 学习文档");
                    resource.setContentJson(objectMapper.readTree("{\"title\":\"" + nodeTitle + "\",\"content\":\"# " + nodeTitle + "\\n\\n内容生成中，请稍后刷新查看...\"}"));
                    break;
                case "quiz":
                    resource.setTitle(nodeTitle + " - 练习题");
                    resource.setContentJson(objectMapper.readTree("{\"title\":\"" + nodeTitle + "练习\",\"questions\":[{\"content\":\"关于" + nodeTitle + "，以下说法正确的是？\",\"type\":\"CHOICE\",\"difficulty\":\"EASY\",\"options\":[{\"content\":\"选项A\",\"isCorrect\":true},{\"content\":\"选项B\",\"isCorrect\":false}],\"analysis\":\"请根据所学内容选择\"}]}"));
                    break;
                case "mindmap":
                    resource.setTitle(nodeTitle + " - 思维导图");
                    resource.setContentJson(objectMapper.readTree("{\"topic\":\"" + nodeTitle + "\",\"children\":[]}"));
                    break;
                default:
                    return null;
            }
            return resource;
        } catch (Exception e) {
            log.error("Failed to create default resource", e);
            return null;
        }
    }

    private String getKnowledgePointInfo(Long pathNodeId) {
        try {
            List<Long> kpIds = pathNodeKpMapper.selectKpIdsByPathNodeId(pathNodeId);
            if (kpIds != null && !kpIds.isEmpty()) {
                return kpIds.stream()
                        .map(id -> knowledgePointMapper.selectById(id))
                        .filter(kp -> kp != null)
                        .map(kp -> kp.getName() + ": " + kp.getDescription())
                        .collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.warn("Failed to get knowledge points for node {}", pathNodeId);
        }
        return "";
    }
}
