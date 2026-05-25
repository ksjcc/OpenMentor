package com.service.garphrag;

import com.ai.agent.GraphExtractionAgent;
import com.common.BizException;
import com.dto.graphrag.GraphEntityDTO;
import com.dto.graphrag.GraphExtractionResult;
import com.dto.graphrag.GraphRelationDTO;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.entity.graphrag.GraphTextUnit;
import com.mapper.GraphEntityMapper;
import com.mapper.GraphRelationshipMapper;
import com.mapper.GraphTextUnitMapper;
import com.service.JsonParseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphRagIndexerServiceTest {

  @Mock
  private GraphExtractionAgent graphExtractionAgent;

  @Mock
  private JsonParseService jsonParseService;

  @Mock
  private GraphTextUnitMapper textUnitMapper;

  @Mock
  private GraphEntityMapper entityMapper;

  @Mock
  private GraphRelationshipMapper relationshipMapper;

  @Test
  void rejectsBlankIndexContent() {
    GraphRagIndexerService service = service();

    assertThatThrownBy(() -> service.indexText(1L, "INTERVIEW", 2L, "  "))
        .isInstanceOf(BizException.class);

    verify(textUnitMapper, never()).insert(any(GraphTextUnit.class));
  }

  @Test
  void normalizesEntitiesAndUpdatesDuplicateRelationship() {
    GraphExtractionResult result = extractionResult();
    when(graphExtractionAgent.extract(anyString())).thenReturn("{}");
    when(jsonParseService.parseGraphExtraction("{}")).thenReturn(result);

    AtomicLong sequence = new AtomicLong(1);
    Map<String, GraphRagEntity> savedEntities = new HashMap<>();
    when(entityMapper.selectByUserAndName(anyLong(), anyString()))
        .thenAnswer(invocation -> savedEntities.get(invocation.getArgument(1)));
    doAnswer(invocation -> {
      GraphRagEntity entity = invocation.getArgument(0);
      entity.setId(sequence.getAndIncrement());
      savedEntities.put(entity.getName(), entity);
      return 1;
    }).when(entityMapper).insert(any(GraphRagEntity.class));

    AtomicReference<GraphRagRelationship> savedRelationship = new AtomicReference<>();
    when(relationshipMapper.selectExisting(anyLong(), anyLong(), anyLong(), anyString()))
        .thenAnswer(invocation -> savedRelationship.get());
    doAnswer(invocation -> {
      savedRelationship.set(invocation.getArgument(0));
      return 1;
    }).when(relationshipMapper).insert(any(GraphRagRelationship.class));

    service().indexText(1L, " interview ", 5L, "Redis depends on MySQL.");

    assertThat(savedEntities).containsKeys("Redis", "MySQL");
    verify(relationshipMapper).insert(any(GraphRagRelationship.class));
    verify(relationshipMapper).updateById(any(GraphRagRelationship.class));

    ArgumentCaptor<GraphTextUnit> textUnitCaptor = ArgumentCaptor.forClass(GraphTextUnit.class);
    verify(textUnitMapper).insert(textUnitCaptor.capture());
    assertThat(textUnitCaptor.getValue().getSourceType()).isEqualTo("INTERVIEW");
    assertThat(textUnitCaptor.getValue().getChunkIndex()).isZero();
  }

  @Test
  void splitsLongContentIntoMultipleTextUnits() {
    GraphExtractionResult emptyResult = new GraphExtractionResult();
    when(graphExtractionAgent.extract(anyString())).thenReturn("{}");
    when(jsonParseService.parseGraphExtraction("{}")).thenReturn(emptyResult);

    service().indexText(1L, "SUMMARY", 5L, "内容。".repeat(700));

    verify(textUnitMapper, atLeast(2)).insert(any(GraphTextUnit.class));
    verify(graphExtractionAgent, atLeast(2)).extract(anyString());
  }

  private GraphRagIndexerService service() {
    return new GraphRagIndexerService(
        graphExtractionAgent,
        jsonParseService,
        textUnitMapper,
        entityMapper,
        relationshipMapper);
  }

  private GraphExtractionResult extractionResult() {
    GraphEntityDTO redis = new GraphEntityDTO();
    redis.setName(" redis ");
    redis.setType("tech");
    redis.setDescription("Cache");

    GraphEntityDTO mysql = new GraphEntityDTO();
    mysql.setName("mysql");
    mysql.setType("TECH");
    mysql.setDescription("Database");

    GraphRelationDTO first = new GraphRelationDTO();
    first.setSource("Redis");
    first.setTarget("mysql");
    first.setType("depends on");
    first.setWeight(0.4);

    GraphRelationDTO duplicate = new GraphRelationDTO();
    duplicate.setSource(" redis ");
    duplicate.setTarget("MySQL");
    duplicate.setType("depends on");
    duplicate.setDescription("Redis relies on persistent storage.");
    duplicate.setWeight(0.8);

    GraphExtractionResult result = new GraphExtractionResult();
    result.setEntities(List.of(redis, mysql));
    result.setRelationships(List.of(first, duplicate));
    return result;
  }
}
