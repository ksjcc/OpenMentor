package com.service.garphrag;

import com.ai.agent.CommunityReportAgent;
import com.dto.graphrag.CommunityReportResult;
import com.entity.graphrag.GraphCommunity;
import com.entity.graphrag.GraphCommunityEntity;
import com.entity.graphrag.GraphCommunityReport;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.GraphCommunityEntityMapper;
import com.mapper.GraphCommunityMapper;
import com.mapper.GraphCommunityReportMapper;
import com.mapper.GraphEntityMapper;
import com.mapper.GraphRelationshipMapper;
import com.service.JsonParseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphCommunityServiceTest {

  @Mock
  private GraphEntityMapper entityMapper;
  @Mock
  private GraphRelationshipMapper relationshipMapper;
  @Mock
  private GraphCommunityMapper communityMapper;
  @Mock
  private GraphCommunityEntityMapper communityEntityMapper;
  @Mock
  private GraphCommunityReportMapper reportMapper;
  @Mock
  private CommunityReportAgent communityReportAgent;
  @Mock
  private JsonParseService jsonParseService;

  @Test
  void clearsSnapshotAndGroupsConnectedEntities() {
    GraphRagEntity redis = entity(1L, "Redis");
    GraphRagEntity cache = entity(2L, "缓存一致性");
    GraphRagEntity jvm = entity(3L, "JVM");
    GraphRagRelationship edge = new GraphRagRelationship();
    edge.setSourceEntityId(1L);
    edge.setTargetEntityId(2L);
    edge.setWeight(0.9);
    when(entityMapper.selectByUserId(7L)).thenReturn(List.of(redis, cache, jvm));
    when(relationshipMapper.selectByUserId(7L)).thenReturn(List.of(edge));
    when(communityReportAgent.generateReport(any())).thenReturn("{}");
    when(jsonParseService.parseCommunityReport("{}")).thenReturn(new CommunityReportResult());

    AtomicLong ids = new AtomicLong(10);
    doAnswer(invocation -> {
      GraphCommunity community = invocation.getArgument(0);
      community.setId(ids.getAndIncrement());
      return 1;
    }).when(communityMapper).insert(any(GraphCommunity.class));

    service().rebuildCommunities(7L);

    verify(reportMapper).delete(any());
    verify(communityEntityMapper).delete(any());
    verify(communityMapper).delete(any());
    verify(communityMapper, times(2)).insert(any(GraphCommunity.class));
    verify(communityEntityMapper, times(3)).insert(any(GraphCommunityEntity.class));
    verify(reportMapper, times(2)).insert(any(GraphCommunityReport.class));
  }

  private GraphCommunityService service() {
    return new GraphCommunityService(
        entityMapper,
        relationshipMapper,
        communityMapper,
        communityEntityMapper,
        reportMapper,
        communityReportAgent,
        jsonParseService,
        new ObjectMapper());
  }

  private GraphRagEntity entity(Long id, String name) {
    GraphRagEntity entity = new GraphRagEntity();
    entity.setId(id);
    entity.setName(name);
    entity.setType("TECH");
    entity.setOccurrenceCount(1);
    return entity;
  }
}
