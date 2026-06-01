package com.ai.orchestrator.runtime;

public enum PipelineStageName {
  SCORING,
  RETRIEVAL_PLANNING,
  GRAPH_RAG_ENRICHMENT,
  DECISIONING,
  AGGREGATION,
  PROMPT_ASSEMBLY,
  GENERATION
}
