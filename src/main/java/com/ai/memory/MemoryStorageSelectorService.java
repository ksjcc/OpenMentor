package com.ai.memory;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dto.Retrieval.RetrievalPlanDTO;

@Service
public class MemoryStorageSelectorService {

  public List<String> select(MemoryLayer layer, RetrievalPlanDTO plan) {
    if (layer == null) {
      return List.of();
    }

    return switch (layer) {
      case SESSION -> List.of(
          MemoryStorageType.REDIS_SESSION_CACHE.name());
      case GROWTH -> List.of(
          MemoryStorageType.MEMORY_SUMMARY_MD.name(),
          MemoryStorageType.MEMORY_CHUNK.name(),
          MemoryStorageType.PGVECTOR.name(),
          MemoryStorageType.INTERVIEW_HISTORY.name());
      case PREFERENCE -> List.of(
          MemoryStorageType.MEMORY_PROFILE_JSON.name(),
          MemoryStorageType.USER_PROFILE.name());
    };
  }
}
