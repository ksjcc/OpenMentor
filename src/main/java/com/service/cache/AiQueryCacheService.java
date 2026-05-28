package com.service.cache;

import javax.xml.datatype.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiQueryCacheService {
  private final RedisTemplate<String, String> redisTemplate;

  public String getHyde(String query) {
    return redisTemplate.opsForValue()
        .get(buildHydeKey(query));
  }

  public void putHyde(
      String query,
      String value,
      Duration ttl) {

    redisTemplate.opsForValue().set(
        buildHydeKey(query),
        value,
        ttl);
  }

  private String buildHydeKey(String query) {
    return "ai:hyde:" + DigestUtils.md5DigestAsHex(
        query.getBytes());
  }
}
