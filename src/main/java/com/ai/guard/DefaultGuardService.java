package com.ai.guard;

import java.lang.reflect.Field;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultGuardService implements GuardService {
  private final ObjectMapper objectMapper;

  @Override
  public <T> T validate(
      String rawOutput,
      Class<T> clazz) {
    try {
      T parsed = objectMapper.readValue(normalizeJson(rawOutput), clazz);
      validateRequiredFields(
          parsed);

      return parsed;
    } catch (Exception e) {
      throw new GuardException("invalid_llm_output",
          e);
    }
  }

  private void validateRequiredFields(Object dto) {
    for (Field field : dto.getClass()
        .getDeclaredFields()) {

      field.setAccessible(true);

      try {

        Object value = field.get(dto);

        if (value == null) {

          throw new IllegalStateException(
              "missing field:"
                  + field.getName());
        }

      } catch (IllegalAccessException e) {

        throw new RuntimeException(e);
      }
    }
  }

  private String normalizeJson(String rawOutput) {
    if (rawOutput == null || rawOutput.isBlank()) {
      return rawOutput;
    }

    String cleaned = rawOutput.trim();

    if (cleaned.startsWith("```")) {
      cleaned = cleaned
          .replaceFirst("^```(?:json)?\\s*", "")
          .replaceFirst("\\s*```$", "")
          .trim();
    }

    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return cleaned.substring(start, end + 1);
    }

    return cleaned;
  }

}
