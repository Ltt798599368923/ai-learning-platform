package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.GeneratedResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI生成的个性化学习资源Mapper接口
 */
@Mapper
public interface GeneratedResourceMapper extends BaseMapper<GeneratedResource> {

    /**
     * 根据路径节点ID获取资源列表
     */
    List<GeneratedResource> selectByPathNodeId(@Param("pathNodeId") Long pathNodeId);

    /**
     * 根据知识点ID获取资源列表
     */
    List<GeneratedResource> selectByKnowledgePointId(@Param("kpId") Long kpId);

    /**
     * 根据多个路径节点ID批量获取资源列表
     */
    List<GeneratedResource> selectByPathNodeIds(@Param("pathNodeIds") List<Long> pathNodeIds);
}
