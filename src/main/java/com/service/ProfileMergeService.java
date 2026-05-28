package com.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.StrengthDTO;
import com.dto.extraction.WeaknessDTO;
import com.dto.profile.ProfileFieldPatchDTO;
import com.dto.profile.ProfileUpdatePatchDTO;
import com.entity.MemoryProfileJson;
import com.entity.ProfileMergeMode;
import com.entity.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapper.MemoryProfileJsonMapper;
import com.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 合并面试提炼结果到长期用户画像。
 */
@Service
@RequiredArgsConstructor
public class ProfileMergeService {

  private static final int DEFAULT_ABILITY_SCORE = 50;

  private final MemoryProfileJsonMapper memoryProfileJsonMapper;
  private final UserProfileMapper userProfileMapper;
  private final ObjectMapper objectMapper;

  @Transactional(rollbackFor = Exception.class)
  public String mergeAndSave(Long userId, KnowledgeExtractionResult extraction) {
    if (userId == null) {
      throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
    }
    if (extraction == null) {
      return getLastProfileJson(userId);
    }

    MemoryProfileJson oldProfile = getLastMemoryProfile(userId);
    ObjectNode profile = parseProfile(oldProfile == null ? null : oldProfile.getProfileData());

    mergeStrengths(profile, extraction);
    mergeWeaknesses(profile, extraction);
    mergeProfileUpdate(profile, extraction);

    String mergedProfileJson = toJson(profile);
    saveMemoryProfile(userId, oldProfile, mergedProfileJson);
    saveOrUpdateUserProfile(userId, profile);
    return mergedProfileJson;
  }

  private MemoryProfileJson getLastMemoryProfile(Long userId) {
    return memoryProfileJsonMapper.selectOne(
        new LambdaQueryWrapper<MemoryProfileJson>()
            .eq(MemoryProfileJson::getUserId, userId)
            .orderByDesc(MemoryProfileJson::getVersion)
            .last("LIMIT 1"));
  }

  private String getLastProfileJson(Long userId) {
    MemoryProfileJson profile = getLastMemoryProfile(userId);
    return profile == null ? null : profile.getProfileData();
  }

  private ObjectNode parseProfile(String profileJson) {
    if (isBlank(profileJson)) {
      return objectMapper.createObjectNode();
    }

    try {
      JsonNode root = objectMapper.readTree(profileJson);
      return root != null && root.isObject()
          ? (ObjectNode) root
          : objectMapper.createObjectNode();
    } catch (Exception e) {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("rawProfile", profileJson);
      return root;
    }
  }

  private void mergeByPolicy(MemoryProfileJson profile, String field, Object oldValue, Object newValue) {
    ProfileMergeMode mode = ProfileMergeRules.getMode(field);
    switch (mode) {
      case INIT_ONLY -> {
      }
      case EXPLICIT_OVERRIDE -> {
        if (newValue != null) {
          applyOverride(
              profile,
              field,
              newValue);
        }
      }

      case UNION_BY_NAME -> {
        mergeCollectionField(
            profile,
            field,
            oldValue,
            newValue);
      }

      case CONFIDENCE_WEIGHTED_SCORE -> {
        mergeAbilityField(
            profile,
            oldValue,
            newValue);
      }

      case APPEND_EVIDENCE -> {
        appendEvidence(
            profile,
            field,
            newValue);
      }
    }
  }

  }

  private void mergeStrengths(ObjectNode profile, KnowledgeExtractionResult extraction) {
    if (extraction.getStrengths() == null) {
      return;
    }

    ArrayNode strengths = ensureArray(profile, "strengths");
    ObjectNode abilityMap = ensureObject(profile, "abilityMap");

    for (StrengthDTO strength : extraction.getStrengths()) {
      if (strength == null || isBlank(strength.getName())) {
        continue;
      }

      ObjectNode merged = findOrCreateByName(strengths, strength.getName());
      putIfNotBlank(merged, "evidence", strength.getEvidence());
      increaseOccurrence(merged);
      adjustAbility(abilityMap, strength.getName(), 10);
    }
  }

  private void mergeWeaknesses(ObjectNode profile, KnowledgeExtractionResult extraction) {
    if (extraction.getWeaknesses() == null) {
      return;
    }

    ArrayNode weaknesses = ensureArray(profile, "weaknesses");
    ObjectNode abilityMap = ensureObject(profile, "abilityMap");

    for (WeaknessDTO weakness : extraction.getWeaknesses()) {
      if (weakness == null || isBlank(weakness.getName())) {
        continue;
      }

      int level = weakness.getLevel() == null ? 1 : weakness.getLevel();
      ObjectNode merged = findOrCreateByName(weaknesses, weakness.getName());
      merged.put("level", Math.max(level, merged.path("level").asInt(0)));
      putIfNotBlank(merged, "evidence", weakness.getEvidence());
      increaseOccurrence(merged);
      adjustAbility(abilityMap, weakness.getName(), -Math.max(5, level * 10));
    }
  }

  private void mergeProfileUpdate(ObjectNode profile, KnowledgeExtractionResult extraction) {
    if (extraction.getMemory() == null || isBlank(extraction.getMemory().getProfileUpdate())) {
      return;
    }

    String profileUpdate = extraction.getMemory().getProfileUpdate();

    try {

      ProfileUpdatePatchDTO patch = objectMapper.readValue(
          profileUpdate,
          ProfileUpdatePatchDTO.class);

      applyPatch(
          profile,
          patch);

    } catch (Exception e) {

      // fallback 老格式兼容
      try {

        JsonNode update = objectMapper.readTree(
            profileUpdate);

        if (update.isObject()) {

          mergeExplicitAbilityMap(
              profile,
              update.get(
                  "abilityMap"));

          mergeExplicitNamedArray(
              profile,
              "strengths",
              update.get(
                  "strengths"));

          mergeExplicitNamedArray(
              profile,
              "weaknesses",
              update.get(
                  "weaknesses"));
        }

      } catch (Exception ignored) {

        profile.put(
            "lastProfileUpdate",
            profileUpdate);
      }
    }
  }

  private void applyPatch(
      ObjectNode profile,
      ProfileUpdatePatchDTO patch) {

    if (patch == null
        || patch.getUpdates() == null) {
      return;
    }

    for (ProfileFieldPatchDTO u : patch.getUpdates()) {

      if (u == null
          || isBlank(
              u.getField())
          || isBlank(
              u.getOp())) {
        continue;
      }

      switch (u.getField()) {

        case "abilityMap" ->
          applyAbilityPatch(
              profile,
              u);

        case "techStack" ->
          applyTechStackPatch(
              profile,
              u);

        case "targetRole",
            "experienceLevel",
            "learningPreference" ->
          applyScalarPatch(
              profile,
              u);

        default -> {
        }
      }
    }
  }

  private void applyScalarPatch(
      ObjectNode profile,
      ProfileFieldPatchDTO patch) {

    if (!"replace".equalsIgnoreCase(
        patch.getOp())
        || isBlank(
            patch.getValue())) {
      return;
    }

    String field = patch.getField();

    ProfileMergeMode mode = ProfileMergeRules.getMode(
        field);

    switch (mode) {

      case INIT_ONLY -> {
        return;
      }

      case EXPLICIT_OVERRIDE -> {

        double confidence = patch.getConfidence() == null
            ? 0.0
            : patch.getConfidence();

        if (confidence < 0.8) {
          return;
        }

        String oldValue = profile.path(field)
            .asText("");

        if (isBlank(oldValue)) {

          profile.put(
              field,
              patch.getValue());

          profile.put(
              field + "UpdatedAt",
              LocalDateTime.now()
                  .toString());

        } else if (!oldValue.equals(
            patch.getValue())) {

          profile.put(
              field,
              patch.getValue());

          profile.put(
              field + "UpdatedAt",
              LocalDateTime.now()
                  .toString());

          profile.put(
              field + "Confidence",
              confidence);

          if (!isBlank(
              patch.getEvidence())) {

            profile.put(
                field + "Evidence",
                patch.getEvidence());
          }
        }
      }

      default -> {
      }
    }
  }

  private void applyTechStackPatch(ObjectNode profile, ProfileFieldPatchDTO patch) {
    if (!"add".equalsIgnoreCase(
        patch.getOp())
        || isBlank(
            patch.getValue())) {
      return;
    }
    ArrayNode techStack = ensureArray(profile, "techStack");
    String normalized = normalize(patch.getValue());
    boolean exists = false;
    for (JsonNode item : techStack) {
      String old = item.isTextual() ? item.asText() : item.path("name").asText();
      if (normalized.equals(old)) {
        exists = true;
        break;
      }
    }
    if (!exists) {

      ObjectNode node = objectMapper.createObjectNode();

      node.put(
          "name",
          patch.getValue());

      node.put(
          "source",
          "profile_patch");

      node.put(
          "updatedAt",
          LocalDateTime.now()
              .toString());

      if (patch.getConfidence() != null) {

        node.put(
            "confidence",
            patch.getConfidence());
      }

      if (!isBlank(
          patch.getEvidence())) {

        node.put(
            "evidence",
            patch.getEvidence());
      }

      techStack.add(node);
    }
  }

  private void applyAbilityPatch(
      ObjectNode profile,
      ProfileFieldPatchDTO patch) {

    if (!"adjust".equals(
        patch.getOp())
        || isBlank(
            patch.getName())
        || patch.getDelta() == null) {
      return;
    }

    ObjectNode abilityMap = ensureObject(
        profile,
        "abilityMap");

    int oldScore = abilityMap.path(
        patch.getName())
        .asInt(
            DEFAULT_ABILITY_SCORE);

    double confidence = patch.getConfidence() == null
        ? 0.7
        : patch.getConfidence();

    int delta = (int) Math.round(
        patch.getDelta()
            * confidence);

    delta = Math.max(
        -12,
        Math.min(
            12,
            delta));

    abilityMap.put(
        patch.getName(),
        clamp(
            oldScore
                + delta));
  }

  private void mergeScalarField(
      ObjectNode profile,
      String field,
      JsonNode node) {

    if (node == null
        || node.isNull()
        || isBlank(node.asText())) {
      return;
    }

    ProfileMergeMode mode = ProfileMergeRules.getMode(
        field);

    switch (mode) {

      case INIT_ONLY -> {
      }

      case EXPLICIT_OVERRIDE -> {

        if (!profile.has(field)
            || isBlank(
                profile.path(field)
                    .asText())) {

          profile.put(
              field,
              node.asText());
        }
      }

      default -> {
      }
    }
  }

  private void mergeExplicitAbilityMap(ObjectNode profile, JsonNode updateAbilityMap) {
    if (updateAbilityMap == null || !updateAbilityMap.isObject()) {
      return;
    }

    ObjectNode abilityMap = ensureObject(profile, "abilityMap");
    Iterator<Map.Entry<String, JsonNode>> fields = updateAbilityMap.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (isBlank(field.getKey()) && !field.getValue().isNumber()) {
        continue;
      }
      int observedScore = field.getValue().asInt();
      int oldScore = abilityMap.path(field.getKey()).asInt(DEFAULT_ABILITY_SCORE);
      int merged = smoothScore(
          oldScore,
          observedScore);

      abilityMap.put(
          field.getKey(),
          merged);
    }
  }

  private int smoothScore(
      int oldScore,
      int observedScore) {

    double score = oldScore * 0.8
        + observedScore * 0.2;

    return Math.max(
        0,
        Math.min(
            100,
            (int) Math.round(score)));
  }

  private void mergeExplicitNamedArray(ObjectNode profile, String fieldName, JsonNode updateArray) {
    if (updateArray == null || !updateArray.isArray()) {
      return;
    }

    ArrayNode target = ensureArray(profile, fieldName);
    for (JsonNode item : updateArray) {
      String name = getName(item);
      if (isBlank(name)) {
        continue;
      }

      ObjectNode merged = findOrCreateByName(target, name);
      if (item.isObject()) {
        item.fields().forEachRemaining(field -> merged.set(field.getKey(), field.getValue()));
      }
      increaseOccurrence(merged);
    }
  }

  private ArrayNode ensureArray(ObjectNode root, String fieldName) {
    JsonNode existing = root.get(fieldName);
    if (existing != null && existing.isArray()) {
      return (ArrayNode) existing;
    }

    ArrayNode array = objectMapper.createArrayNode();
    root.set(fieldName, array);
    return array;
  }

  private ObjectNode ensureObject(ObjectNode root, String fieldName) {
    JsonNode existing = root.get(fieldName);
    if (existing != null && existing.isObject()) {
      return (ObjectNode) existing;
    }

    ObjectNode object = objectMapper.createObjectNode();
    root.set(fieldName, object);
    return object;
  }

  private ObjectNode findOrCreateByName(ArrayNode array, String name) {
    String normalizedName = normalize(name);
    for (JsonNode item : array) {
      if (normalizedName.equals(normalize(getName(item)))) {
        if (item.isObject()) {
          return (ObjectNode) item;
        }
        break;
      }
    }

    ObjectNode created = objectMapper.createObjectNode();
    created.put("name", name.trim());
    array.add(created);
    return created;
  }

  private String getName(JsonNode node) {
    if (node == null || node.isNull()) {
      return "";
    }
    if (node.isTextual()) {
      return node.asText();
    }
    return node.path("name").asText("");
  }

  private void increaseOccurrence(ObjectNode node) {
    node.put("occurrenceCount", node.path("occurrenceCount").asInt(0) + 1);
    node.put("updatedAt", LocalDateTime.now().toString());
  }

  private void adjustAbility(ObjectNode abilityMap, String skill, int delta) {
    int current = abilityMap.path(skill).asInt(DEFAULT_ABILITY_SCORE);
    abilityMap.put(skill, clamp(current + delta));
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(100, value));
  }

  private void putIfNotBlank(ObjectNode node, String fieldName, String value) {
    if (!isBlank(value)) {
      node.put(fieldName, value);
    }
  }

  private void saveMemoryProfile(Long userId, MemoryProfileJson oldProfile, String profileJson) {
    MemoryProfileJson memoryProfile = new MemoryProfileJson();
    memoryProfile.setUserId(userId);
    memoryProfile.setProfileData(profileJson);
    int oldVersion = oldProfile == null || oldProfile.getVersion() == null ? 0 : oldProfile.getVersion();
    memoryProfile.setVersion(oldVersion + 1);
    memoryProfileJsonMapper.insert(memoryProfile);
  }

  private void saveOrUpdateUserProfile(Long userId, ObjectNode profileJson) {
    UserProfile old = userProfileMapper.selectOne(
        new LambdaQueryWrapper<UserProfile>()
            .eq(UserProfile::getUserId, userId)
            .last("LIMIT 1"));

    UserProfile profile = old == null ? new UserProfile() : old;
    profile.setUserId(userId);
    profile.setTargetRole(text(profileJson, "targetRole"));
    profile.setExperienceLevel(text(profileJson, "experienceLevel"));
    profile.setLearningPreference(text(profileJson, "learningPreference"));
    /*
     * mergeByPolicy(profile,
     * "targetRole",
     * profile.getTargetRole(),
     * update.getTargetRole());
     * 
     * mergeByPolicy(
     * profile,
     * "experienceLevel",
     * profile.getExperienceLevel(),
     * update.getExperienceLevel());
     * 
     * mergeByPolicy(
     * profile,
     * "learningPreference",
     * profile.getLearningPreference(),
     * update.getLearningPreference());
     */
    profile.setTechStack(json(profileJson, "techStack", "[]"));
    profile.setAbilityMap(json(profileJson, "abilityMap", "{}"));
    profile.setWeaknesses(json(profileJson, "weaknesses", "[]"));
    profile.setStrengths(json(profileJson, "strengths", "[]"));

    if (old == null) {
      userProfileMapper.insert(profile);
    } else {
      userProfileMapper.updateById(profile);
    }
  }

  private String text(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    return node == null || node.isNull() ? "" : node.asText("");
  }

  private String json(JsonNode root, String fieldName, String defaultValue) {
    JsonNode node = root.get(fieldName);
    return node == null || node.isNull() ? defaultValue : node.toString();
  }

  private String toJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "生成用户画像JSON失败");
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
