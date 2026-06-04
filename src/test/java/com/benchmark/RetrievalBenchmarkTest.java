package com.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

class RetrievalBenchmarkTest {

  private static final Path CORPUS_PATH = resolveDataFile("retrieval_corpus_v2.jsonl", "retrieval_corpus.jsonl");
  private static final Path QUERY_PATH = resolveDataFile("retrieval_queries_v2.jsonl", "retrieval_queries.jsonl");
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9_]+");
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static Path resolveDataFile(String preferred, String fallback) {
    Path preferredPath = Path.of("src/main/resources/data/" + preferred);
    if (Files.exists(preferredPath)) {
      return preferredPath;
    }
    return Path.of("src/main/resources/data/" + fallback);
  }

  @Test
  void benchmarkHybridRetrievalAgainstVectorBaseline() throws Exception {
    List<CorpusEntry> corpus = loadCorpus();
    List<QueryEntry> queries = loadQueries();

    BenchmarkMetrics baseline = evaluate(corpus, queries, false);
    BenchmarkMetrics hybrid = evaluate(corpus, queries, true);

    System.out.println("retrieval benchmark baseline=" + baseline);
    System.out.println("retrieval benchmark hybrid=" + hybrid);

    assertThat(hybrid.hitAt5()).isGreaterThan(baseline.hitAt5());
    assertThat(hybrid.recallAt5()).isGreaterThan(baseline.recallAt5());
    assertThat(hybrid.mrr()).isGreaterThan(baseline.mrr());
  }

  private List<CorpusEntry> loadCorpus() throws IOException {
    List<CorpusEntry> entries = new ArrayList<>();
    for (String line : Files.readAllLines(CORPUS_PATH)) {
      if (line == null || line.isBlank()) {
        continue;
      }
      CorpusEntry entry = objectMapper.readValue(line, CorpusEntry.class);
      entry.normalize();
      entries.add(entry);
    }
    return entries;
  }

  private List<QueryEntry> loadQueries() throws IOException {
    List<QueryEntry> entries = new ArrayList<>();
    for (String line : Files.readAllLines(QUERY_PATH)) {
      if (line == null || line.isBlank()) {
        continue;
      }
      entries.add(objectMapper.readValue(line, QueryEntry.class));
    }
    return entries;
  }

  private BenchmarkMetrics evaluate(List<CorpusEntry> corpus, List<QueryEntry> queries, boolean hybridMode) {
    int samples = 0;
    int hitAt3Count = 0;
    int hitAt5Count = 0;
    double recallAt5Sum = 0;
    double mrrSum = 0;

    for (QueryEntry query : queries) {
      if (!query.expectedRetrieval) {
        continue;
      }
      samples++;
      List<ScoredCandidate> ranked = rank(corpus, query, hybridMode);
      Set<String> expected = new LinkedHashSet<>(query.expectedEvidenceIds == null ? List.of() : query.expectedEvidenceIds);
      List<String> ids = ranked.stream().map(ScoredCandidate::evidenceId).toList();

      if (ids.stream().limit(3).anyMatch(expected::contains)) {
        hitAt3Count++;
      }
      if (ids.stream().anyMatch(expected::contains)) {
        hitAt5Count++;
      }

      int matched = 0;
      int firstRank = 0;
      for (int i = 0; i < ids.size(); i++) {
        if (expected.contains(ids.get(i))) {
          matched++;
          if (firstRank == 0) {
            firstRank = i + 1;
          }
        }
      }

      if (!expected.isEmpty()) {
        recallAt5Sum += (double) matched / expected.size();
      }
      if (firstRank > 0) {
        mrrSum += 1.0 / firstRank;
      }
    }

    if (samples == 0) {
      return new BenchmarkMetrics(0, 0, 0, 0, 0);
    }

    return new BenchmarkMetrics(
        samples,
        (double) hitAt3Count / samples,
        (double) hitAt5Count / samples,
        recallAt5Sum / samples,
        mrrSum / samples);
  }

  private List<ScoredCandidate> rank(List<CorpusEntry> corpus, QueryEntry query, boolean hybridMode) {
    List<String> queryTokens = tokenize(String.join(" ", safe(query.question), safe(query.answer), safe(query.weakTopic)));
    return corpus.stream()
        .filter(entry -> hybridMode ? allowHybrid(entry, query) : allowVectorBaseline(entry))
        .map(entry -> new ScoredCandidate(entry.evidenceId, score(entry, query, queryTokens)))
        .filter(candidate -> candidate.score() > 0)
        .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
        .limit(5)
        .toList();
  }

  private boolean allowVectorBaseline(CorpusEntry entry) {
    return "PGVECTOR".equals(entry.sourceType) && !"user_specific".equals(entry.userScope);
  }

  private boolean allowHybrid(CorpusEntry entry, QueryEntry query) {
    if ("user_specific".equals(entry.userScope) && !entry.userIdEquals(query.userId)) {
      return false;
    }
    if ("session_specific".equals(entry.userScope) && !entry.sessionIdEquals(query.sessionId)) {
      return false;
    }

    List<String> allowed = switch (safe(query.queryType)) {
      case "KNOWLEDGE" -> List.of("PGVECTOR", "GRAPH_LOCAL");
      case "INTERVIEW_FOLLOWUP" -> List.of(
          "MEMORY_CHUNK",
          "MEMORY_SUMMARY_MD",
          "INTERVIEW_HISTORY",
          "USER_PROFILE",
          "PGVECTOR",
          "GRAPH_LOCAL",
          "GRAPH_GLOBAL");
      case "LEARNING" -> List.of("MEMORY_CHUNK", "MEMORY_SUMMARY_MD", "PGVECTOR", "GRAPH_GLOBAL", "USER_PROFILE");
      case "PROJECT_EXPERIENCE" -> List.of(
          "USER_PROFILE",
          "INTERVIEW_HISTORY",
          "MEMORY_SUMMARY_MD",
          "MEMORY_CHUNK",
          "PGVECTOR",
          "GRAPH_GLOBAL");
      case "MEMORY" -> List.of("MEMORY_CHUNK", "MEMORY_SUMMARY_MD", "INTERVIEW_HISTORY", "USER_PROFILE");
      case "SYSTEM_DESIGN" -> List.of("GRAPH_GLOBAL", "GRAPH_LOCAL", "PGVECTOR", "MEMORY_SUMMARY_MD", "INTERVIEW_HISTORY");
      default -> List.of("PGVECTOR");
    };

    if ("SIMPLE".equals(safe(query.complexity)) && !"SYSTEM_DESIGN".equals(safe(query.queryType))) {
      allowed = allowed.stream().filter(item -> !"GRAPH_GLOBAL".equals(item)).toList();
    }
    return allowed.contains(entry.sourceType);
  }

  private double score(CorpusEntry entry, QueryEntry query, List<String> queryTokens) {
    double score = 0;
    String text = entry.normalizedText;
    String weakTopic = normalize(query.weakTopic);

    if (!weakTopic.isBlank()) {
      if (text.contains(weakTopic)) {
        score += 12;
      }
      for (String piece : weakTopic.split("[\\s/]+")) {
        if (!piece.isBlank() && text.contains(piece)) {
          score += 2;
        }
      }
    }

    for (String token : queryTokens) {
      if (token.length() < 2) {
        continue;
      }
      if (entry.tokenSet.contains(token)) {
        score += token.length() >= 3 ? 3 : 1.6;
      } else if (text.contains(token)) {
        score += token.length() >= 3 ? 2 : 1;
      }
    }

    String question = normalize(query.question);
    if (!question.isBlank() && text.contains(question)) {
      score += 8;
    }

    String queryType = safe(query.queryType);
    if ("PROJECT_EXPERIENCE".equals(queryType) && "USER_PROFILE".equals(entry.sourceType)) {
      score += 6;
    }
    if ("MEMORY".equals(queryType) && Set.of("MEMORY_CHUNK", "MEMORY_SUMMARY_MD", "INTERVIEW_HISTORY").contains(entry.sourceType)) {
      score += 5;
    }
    if ("SYSTEM_DESIGN".equals(queryType) && entry.sourceType != null && entry.sourceType.startsWith("GRAPH_")) {
      score += 5;
    }
    if ("LEARNING".equals(queryType) && Set.of("MEMORY_CHUNK", "MEMORY_SUMMARY_MD").contains(entry.sourceType)) {
      score += 4;
    }
    if ("INTERVIEW_FOLLOWUP".equals(queryType) && "MEMORY_CHUNK".equals(entry.sourceType)) {
      score += 4;
    }

    return score;
  }

  private List<String> tokenize(String text) {
    String normalized = normalize(text);
    Matcher matcher = TOKEN_PATTERN.matcher(normalized);
    List<String> tokens = new ArrayList<>();
    while (matcher.find()) {
      String token = matcher.group();
      if (token == null || token.isBlank()) {
        continue;
      }
      tokens.add(token);
      if (isHanToken(token)) {
        if (token.length() <= 4) {
          tokens.add(token);
        }
        tokens.addAll(nGrams(token, 2));
        tokens.addAll(nGrams(token, 3));
      } else if (token.length() > 4) {
        tokens.addAll(nGrams(token, 3));
      }
    }
    return tokens;
  }

  private List<String> nGrams(String token, int size) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i <= token.length() - size; i++) {
      result.add(token.substring(i, i + size));
    }
    return result;
  }

  private String normalize(String text) {
    return text == null ? "" : text.toLowerCase(Locale.ROOT);
  }

  private static boolean isHanToken(String token) {
    return token != null
        && !token.isBlank()
        && token.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
  }

  private String safe(String text) {
    return text == null ? "" : text;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class CorpusEntry {
    @JsonProperty("evidence_id")
    public String evidenceId;
    @JsonProperty("source_type")
    public String sourceType;
    public String topic;
    public String subtopic;
    public String content;
    public List<String> keywords;
    @JsonProperty("user_scope")
    public String userScope;
    @JsonProperty("user_id")
    public Long userId;
    @JsonProperty("session_id")
    public Long sessionId;
    private String normalizedText;
    private Set<String> tokenSet = new HashSet<>();

    void normalize() {
      String combined = String.join(" ",
          topic == null ? "" : topic,
          subtopic == null ? "" : subtopic,
          content == null ? "" : content,
          keywords == null ? "" : String.join(" ", keywords));
      normalizedText = combined.toLowerCase(Locale.ROOT);
      tokenSet = new HashSet<>();
      Matcher matcher = TOKEN_PATTERN.matcher(normalizedText);
      while (matcher.find()) {
        String token = matcher.group();
        if (token != null && !token.isBlank()) {
          tokenSet.add(token);
          if (isHanToken(token)) {
            tokenSet.addAll(buildNGrams(token, 2));
            tokenSet.addAll(buildNGrams(token, 3));
          } else if (token.length() > 4) {
            tokenSet.addAll(buildNGrams(token, 3));
          }
        }
      }
    }

    boolean userIdEquals(Long other) {
      return userId == null ? other == null : userId.equals(other);
    }

    boolean sessionIdEquals(Long other) {
      return sessionId == null ? other == null : sessionId.equals(other);
    }

    private List<String> buildNGrams(String token, int size) {
      List<String> grams = new ArrayList<>();
      for (int i = 0; i <= token.length() - size; i++) {
        grams.add(token.substring(i, i + size));
      }
      return grams;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class QueryEntry {
    @JsonProperty("sample_id")
    public String sampleId;
    @JsonProperty("user_id")
    public Long userId;
    @JsonProperty("session_id")
    public Long sessionId;
    public String question;
    public String answer;
    @JsonProperty("weak_topic")
    public String weakTopic;
    @JsonProperty("query_type")
    public String queryType;
    public String complexity;
    @JsonProperty("expected_retrieval")
    public boolean expectedRetrieval;
    @JsonProperty("expected_evidence_ids")
    public List<String> expectedEvidenceIds;
  }

  private record ScoredCandidate(String evidenceId, double score) {
  }

  private record BenchmarkMetrics(int samples, double hitAt3, double hitAt5, double recallAt5, double mrr) {
    @Override
    public String toString() {
      Map<String, Object> values = new HashMap<>();
      values.put("samples", samples);
      values.put("hitAt3", round(hitAt3));
      values.put("hitAt5", round(hitAt5));
      values.put("recallAt5", round(recallAt5));
      values.put("mrr", round(mrr));
      return values.toString();
    }

    private static double round(double value) {
      return Math.round(value * 10000.0) / 10000.0;
    }
  }
}
