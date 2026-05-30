package com.service.garphrag;

import java.util.List;

import com.common.GraphRagRetrieverType;

public interface GraphRagSearchStrategy {
  GraphRagRetrieverType type();

  List<String> search(Long userId, String query, RetrievalBudget budget);
}
