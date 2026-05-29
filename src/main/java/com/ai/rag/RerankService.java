package com.ai.rag;

import java.util.List;

import com.service.garphrag.RetrievalBudget;

public interface RerankService {

  List<String> rerank(
      String query,
      List<String> candidates,
      int topK);

  default List<String> rerank(
      String query,
      List<String> candidates,
      int topK,
      RetrievalBudget budget) {
    return rerank(query, candidates, topK);
  }
}
