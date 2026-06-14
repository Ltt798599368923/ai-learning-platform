package com.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.entity.*;
import com.exam.mapper.*;
import com.exam.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱Service实现类
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Autowired
    private KnowledgeEdgeMapper knowledgeEdgeMapper;

    @Override
    public Map<String, Object> getKnowledgeGraph(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            return Map.of("points", List.of(), "edges", List.of());
        }

        // 获取该课程的所有知识点
        List<KnowledgePoint> points = knowledgePointMapper.selectByCourseId(courseId);

        // 获取该课程的所有依赖关系
        List<KnowledgeEdge> edges = knowledgeEdgeMapper.selectByCourseId(courseId);

        // 构建 id → code 映射，用于 edge 的 code 转换
        Map<Long, String> idToCode = points.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getCode));

        // 为每个知识点填充前置依赖和子知识点
        Map<Long, List<KnowledgeEdge>> edgesByTarget = edges.stream()
                .collect(Collectors.groupingBy(KnowledgeEdge::getToKpId));

        // 预先按 parent_id 分组构建 children 映射，避免 N+1 查询
        Map<Long, List<KnowledgePoint>> childrenByParent = points.stream()
                .filter(p -> p.getParentId() != null)
                .collect(Collectors.groupingBy(KnowledgePoint::getParentId));

        for (KnowledgePoint kp : points) {
            // 填充前置依赖（code 列表）
            List<KnowledgeEdge> preEdges = edgesByTarget.getOrDefault(kp.getId(), Collections.emptyList());
            List<String> preCodes = preEdges.stream()
                    .map(e -> idToCode.get(e.getFromKpId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            kp.setPrerequisiteCodes(preCodes);

            // 填充子知识点（从预计算 map 中取，无子节点则为空列表）
            kp.setChildren(childrenByParent.getOrDefault(kp.getId(), Collections.emptyList()));
        }

        // 将 edges 转为契约格式（from/to 用 code 字符串）
        List<Map<String, String>> edgeList = edges.stream()
                .map(e -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("from", idToCode.get(e.getFromKpId()));
                    m.put("to", idToCode.get(e.getToKpId()));
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course.getName());
        result.put("points", points);
        result.put("edges", edgeList);

        return result;
    }

    @Override
    public List<Course> getAllCourses() {
        return courseMapper.selectList(null);
    }

    @Override
    public Course getCourseById(Long id) {
        return courseMapper.selectById(id);
    }

    @Override
    @Transactional
    public void createCourse(Course course) {
        courseMapper.insert(course);
    }

    @Override
    @Transactional
    public void updateCourse(Course course) {
        courseMapper.updateById(course);
    }

    @Override
    @Transactional
    public void deleteCourse(Long id) {
        // 检查课程下是否有知识点
        Long count = knowledgePointMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, id)
        );
        if (count > 0) {
            throw new RuntimeException("课程下还有知识点，无法删除");
        }
        courseMapper.deleteById(id);
    }

    @Override
    public List<KnowledgePoint> getKnowledgePointsByCourse(Long courseId) {
        return knowledgePointMapper.selectByCourseId(courseId);
    }

    @Override
    public KnowledgePoint getKnowledgePointById(Long id) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp != null) {
            fillKnowledgePointDetails(kp);
        }
        return kp;
    }

    @Override
    public KnowledgePoint getKnowledgePointByCode(String code) {
        KnowledgePoint kp = knowledgePointMapper.selectByCode(code);
        if (kp != null) {
            fillKnowledgePointDetails(kp);
        }
        return kp;
    }

    @Override
    public Map<String, Object> getKnowledgeTree(Long courseId) {
        // 获取课程信息
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }

        // 获取该课程的所有知识点
        List<KnowledgePoint> allPoints = knowledgePointMapper.selectByCourseId(courseId);

        // 构建树形结构
        List<KnowledgePoint> tree = buildTree(allPoints, null);

        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course.getName());
        result.put("nodes", tree);

        return result;
    }

    @Override
    public List<KnowledgePoint> getPrerequisiteChain(Long kpId) {
        List<KnowledgePoint> chain = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        collectPrerequisites(kpId, chain, visited);
        return chain;
    }

    @Override
    @Transactional
    public void createKnowledgePoint(KnowledgePoint knowledgePoint) {
        // 检查编码是否重复
        KnowledgePoint existing = knowledgePointMapper.selectByCode(knowledgePoint.getCode());
        if (existing != null) {
            throw new RuntimeException("知识点编码已存在: " + knowledgePoint.getCode());
        }
        knowledgePointMapper.insert(knowledgePoint);
    }

    @Override
    @Transactional
    public void updateKnowledgePoint(KnowledgePoint knowledgePoint) {
        knowledgePointMapper.updateById(knowledgePoint);
    }

    @Override
    @Transactional
    public void deleteKnowledgePoint(Long id) {
        // 检查是否有依赖关系
        List<KnowledgeEdge> fromEdges = knowledgeEdgeMapper.selectByFromKpId(id);
        List<KnowledgeEdge> toEdges = knowledgeEdgeMapper.selectByToKpId(id);
        if (!fromEdges.isEmpty() || !toEdges.isEmpty()) {
            throw new RuntimeException("该知识点存在依赖关系，无法删除");
        }
        knowledgePointMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void createEdge(KnowledgeEdge edge) {
        // 检查是否已存在
        Long count = knowledgeEdgeMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .eq(KnowledgeEdge::getFromKpId, edge.getFromKpId())
                        .eq(KnowledgeEdge::getToKpId, edge.getToKpId())
        );
        if (count > 0) {
            throw new RuntimeException("依赖关系已存在");
        }
        // 检查是否会形成环
        if (wouldCreateCycle(edge.getFromKpId(), edge.getToKpId())) {
            throw new RuntimeException("添加此依赖关系会形成环");
        }
        knowledgeEdgeMapper.insert(edge);
    }

    @Override
    @Transactional
    public void deleteEdge(Long fromKpId, Long toKpId) {
        knowledgeEdgeMapper.delete(
                new LambdaQueryWrapper<KnowledgeEdge>()
                        .eq(KnowledgeEdge::getFromKpId, fromKpId)
                        .eq(KnowledgeEdge::getToKpId, toKpId)
        );
    }

    @Override
    public List<KnowledgeEdge> getPrerequisites(Long kpId) {
        return knowledgeEdgeMapper.selectByToKpId(kpId);
    }

    @Override
    public List<KnowledgeEdge> getDependents(Long kpId) {
        return knowledgeEdgeMapper.selectByFromKpId(kpId);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 填充知识点详情（前置依赖、子知识点等）
     */
    private void fillKnowledgePointDetails(KnowledgePoint kp) {
        // 获取前置依赖
        List<KnowledgeEdge> prerequisites = knowledgeEdgeMapper.selectByToKpId(kp.getId());
        if (!prerequisites.isEmpty()) {
            List<Long> preKpIds = prerequisites.stream()
                    .map(KnowledgeEdge::getFromKpId)
                    .collect(Collectors.toList());
            List<KnowledgePoint> preKps = knowledgePointMapper.selectBatchIds(preKpIds);
            kp.setPrerequisites(preKps);
            kp.setPrerequisiteCodes(preKps.stream()
                    .map(KnowledgePoint::getCode)
                    .collect(Collectors.toList()));
        }

        // 获取子知识点
        List<KnowledgePoint> children = knowledgePointMapper.selectByParentId(kp.getId());
        kp.setChildren(children);

        // 获取课程名称
        Course course = courseMapper.selectById(kp.getCourseId());
        if (course != null) {
            kp.setCourseName(course.getName());
        }
    }

    /**
     * 构建树形结构
     */
    private List<KnowledgePoint> buildTree(List<KnowledgePoint> allPoints, Long parentId) {
        return allPoints.stream()
                .filter(p -> Objects.equals(p.getParentId(), parentId))
                .peek(p -> p.setChildren(buildTree(allPoints, p.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 递归收集前置依赖
     */
    private void collectPrerequisites(Long kpId, List<KnowledgePoint> chain, Set<Long> visited) {
        if (visited.contains(kpId)) {
            return;
        }
        visited.add(kpId);

        List<KnowledgeEdge> edges = knowledgeEdgeMapper.selectByToKpId(kpId);
        for (KnowledgeEdge edge : edges) {
            KnowledgePoint preKp = knowledgePointMapper.selectById(edge.getFromKpId());
            if (preKp != null) {
                chain.add(preKp);
                collectPrerequisites(preKp.getId(), chain, visited);
            }
        }
    }

    /**
     * 检查添加依赖关系是否会形成环
     */
    private boolean wouldCreateCycle(Long fromKpId, Long toKpId) {
        // 如果从toKpId能到达fromKpId，则添加from->to会形成环
        Set<Long> visited = new HashSet<>();
        return canReach(toKpId, fromKpId, visited);
    }

    /**
     * 检查从startId能否到达targetId
     */
    private boolean canReach(Long startId, Long targetId, Set<Long> visited) {
        if (startId.equals(targetId)) {
            return true;
        }
        if (visited.contains(startId)) {
            return false;
        }
        visited.add(startId);

        List<KnowledgeEdge> edges = knowledgeEdgeMapper.selectByFromKpId(startId);
        for (KnowledgeEdge edge : edges) {
            if (canReach(edge.getToKpId(), targetId, visited)) {
                return true;
            }
        }
        return false;
    }
}
