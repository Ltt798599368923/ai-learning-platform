package com.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.entity.KnowledgePoint;
import com.exam.entity.LearningPath;
import com.exam.entity.PathNode;
import com.exam.entity.PathNodeKp;
import com.exam.mapper.KnowledgePointMapper;
import com.exam.mapper.LearningPathMapper;
import com.exam.entity.GeneratedResource;
import com.exam.mapper.GeneratedResourceMapper;
import com.exam.mapper.PathNodeMapper;
import com.exam.mapper.PathNodeKpMapper;
import com.exam.service.LearningPathService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习路径Service实现类
 */
@Service
public class LearningPathServiceImpl implements LearningPathService {

    @Autowired
    private LearningPathMapper learningPathMapper;

    @Autowired
    private PathNodeMapper pathNodeMapper;

    @Autowired
    private PathNodeKpMapper pathNodeKpMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Autowired
    private GeneratedResourceMapper generatedResourceMapper;

    @Override
    @Transactional
    public void createLearningPath(LearningPath path) {
        learningPathMapper.insert(path);

        // 如果请求中带了内联 nodes（对齐接口契约），一并创建节点和知识点关联
        if (path.getNodes() != null && !path.getNodes().isEmpty()) {
            for (PathNode node : path.getNodes()) {
                node.setPathId(path.getId());
                pathNodeMapper.insert(node);

                // 创建知识点关联
                if (node.getKnowledgePointIds() != null) {
                    for (Long kpId : node.getKnowledgePointIds()) {
                        PathNodeKp pk = new PathNodeKp();
                        pk.setPathNodeId(node.getId());
                        pk.setKnowledgePointId(kpId);
                        pathNodeKpMapper.insert(pk);
                    }
                }
            }
        }
    }

    @Override
    public LearningPath getLearningPathDetail(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }

        // 获取路径节点
        List<PathNode> nodes = pathNodeMapper.selectByPathId(pathId);

        // 收集所有节点 ID
        List<Long> nodeIds = nodes.stream().map(PathNode::getId).collect(Collectors.toList());

        // 批量查关联知识点
        if (!nodes.isEmpty()) {
            List<PathNodeKp> allKps = pathNodeKpMapper.selectList(
                    new LambdaQueryWrapper<PathNodeKp>().in(PathNodeKp::getPathNodeId, nodeIds)
            );

            if (!allKps.isEmpty()) {
                // 批量查知识点
                List<Long> kpIds = allKps.stream()
                        .map(PathNodeKp::getKnowledgePointId)
                        .distinct()
                        .collect(Collectors.toList());
                List<KnowledgePoint> knowledgePoints = knowledgePointMapper.selectBatchIds(kpIds);

                // 按节点 ID 分组
                Map<Long, List<Long>> nodeKpMap = allKps.stream()
                        .collect(Collectors.groupingBy(
                                PathNodeKp::getPathNodeId,
                                Collectors.mapping(PathNodeKp::getKnowledgePointId, Collectors.toList())
                        ));

                // 填到每个节点上
                for (PathNode node : nodes) {
                    List<Long> nodeKpIds = nodeKpMap.get(node.getId());
                    if (nodeKpIds != null) {
                        List<KnowledgePoint> nodeKps = knowledgePoints.stream()
                                .filter(kp -> nodeKpIds.contains(kp.getId()))
                                .collect(Collectors.toList());
                        node.setKnowledgePoints(nodeKps);
                    } else {
                        node.setKnowledgePoints(new ArrayList<>());
                    }
                }
            }

            // 批量查关联资源
            List<GeneratedResource> allResources = generatedResourceMapper.selectByPathNodeIds(nodeIds);

            // 按节点 ID 分组资源
            Map<Long, List<GeneratedResource>> nodeResourceMap = allResources.stream()
                    .collect(Collectors.groupingBy(GeneratedResource::getPathNodeId));

            // 填到每个节点上
            for (PathNode node : nodes) {
                List<GeneratedResource> nodeResources = nodeResourceMap.get(node.getId());
                node.setResources(nodeResources != null ? nodeResources : new ArrayList<>());
            }
        }

        path.setNodes(nodes);
        path.setNodeCount(nodes.size());

        // 统计已完成节点数
        long completedCount = nodes.stream()
                .filter(n -> "completed".equals(n.getStatus()))
                .count();
        path.setCompletedNodeCount((int) completedCount);

        return path;
    }

    @Override
    public List<LearningPath> getLearningPathsByUser(Long userId) {
        return learningPathMapper.selectByUserId(userId);
    }

    @Override
    public LearningPath getActiveLearningPath(Long userId, Long courseId) {
        return learningPathMapper.selectActiveByUserAndCourse(userId, courseId);
    }

    @Override
    @Transactional
    public void updatePathStatus(Long pathId, String status) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }
        path.setStatus(status);
        learningPathMapper.updateById(path);
    }

    @Override
    @Transactional
    public void addPathNode(PathNode node) {
        pathNodeMapper.insert(node);
    }

    @Override
    @Transactional
    public void updateNodeStatus(Long nodeId, String status) {
        PathNode node = pathNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new RuntimeException("路径节点不存在");
        }
        node.setStatus(status);
        pathNodeMapper.updateById(node);
    }

    @Override
    @Transactional
    public void deleteLearningPath(Long pathId) {
        // 删除关联的节点知识点关系
        List<PathNode> nodes = pathNodeMapper.selectByPathId(pathId);
        for (PathNode node : nodes) {
            pathNodeKpMapper.delete(
                    new LambdaQueryWrapper<PathNodeKp>()
                            .eq(PathNodeKp::getPathNodeId, node.getId())
            );
        }
        // 删除节点
        pathNodeMapper.delete(
                new LambdaQueryWrapper<PathNode>().eq(PathNode::getPathId, pathId)
        );
        // 删除路径
        learningPathMapper.deleteById(pathId);
    }

    @Override
    public PathNode getPathNodeById(Long nodeId) {
        return pathNodeMapper.selectById(nodeId);
    }
}
