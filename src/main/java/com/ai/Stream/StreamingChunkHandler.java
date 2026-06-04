package com.ai.Stream;

public interface StreamingChunkHandler {
  void onStart(String requestId);

  void onChunk(String chunk);

  void onComplete(String fullContent);

  void onError(Throwable throwable);

}
