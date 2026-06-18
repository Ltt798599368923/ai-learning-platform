package com.exam.service;

import com.exam.entity.LearningPath;
import com.exam.entity.PathNode;

import java.util.List;

/**
 * 学习路径Service接口
 */
public interface LearningPathService {

    /**
     * 创建学习路径
     */
    void createLearningPath(LearningPath path);

    /**
     * 获取路径详情含节点
     */
    LearningPath getLearningPathDetail(Long pathId);

    /**
     * 获取用户所有路径列表
     */
    List<LearningPath> getLearningPathsByUser(Long userId);

    /**
     * 获取用户在某课程的活跃路径
     */
    LearningPath getActiveLearningPath(Long userId, Long courseId);

    /**
     * 更新路径状态
     */
    void updatePathStatus(Long pathId, String status);

    /**
     * 添加路径节点
     */
    void addPathNode(PathNode node);

    /**
     * 更新节点状态
     */
    void updateNodeStatus(Long nodeId, String status);

    /**
     * 删除学习路径
     */
    void deleteLearningPath(Long pathId);

    /**
     * 根据节点ID获取节点信息
     */
    PathNode getPathNodeById(Long nodeId);
}
