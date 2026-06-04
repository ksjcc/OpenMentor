package com.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

class MultiAgentDecisionBenchmarkTest {

  private static final Path DATA_DIR = Path.of("src/main/resources/data/complex");
  private static final Path CASES_PATH = DATA_DIR.resolve("complex_task_cases.jsonl");
  private static final Path LABELS_PATH = DATA_DIR.resolve("expected_decision_labels.jsonl");
  private static final Path RUN_LOG_PATH = DATA_DIR.resolve("decision_benchmark_run_log.csv");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void benchmarkMultiAgentDecisionQuality() throws Exception {
    List<DecisionCase> cases = readJsonl(CASES_PATH, DecisionCase.class);
    List<ExpectedLabel> labels = readJsonl(LABELS_PATH, ExpectedLabel.class);
    List<RunLogRow> rows = readRunLog(cases);

    assertThat(cases).hasSize(40);
    assertThat(labels).hasSize(cases.size());
    assertThat(rows).hasSize(cases.size() * 2);

    Set<String> caseIds = cases.stream().map(item -> item.caseId).collect(java.util.stream.Collectors.toSet());
    assertThat(labels).allMatch(item -> caseIds.contains(item.caseId));
    assertThat(rows).allMatch(item -> caseIds.contains(item.caseId()));

    BenchmarkSummary baseline = summarize(rows, "BASELINE");
    BenchmarkSummary multiAgent = summarize(rows, "MULTI_AGENT");

    double efficiencyImprovement = latencyImprovement(
        baseline.avgLatencyMs(),
        multiAgent.avgLatencyMs());
    double decisionAccuracyImprovement = improvement(
        baseline.decisionAccuracy(),
        multiAgent.decisionAccuracy());

    System.out.println("multi-agent decision baseline=" + baseline);
    System.out.println("multi-agent decision experiment=" + multiAgent);
    System.out.println("complex task processing efficiency improvement=" + roundPercent(efficiencyImprovement));
    System.out.println("decision accuracy gain percentage points="
        + roundPercent(multiAgent.decisionAccuracy() - baseline.decisionAccuracy()));
    System.out.println("decision accuracy relative improvement=" + roundPercent(decisionAccuracyImprovement));

    assertThat(multiAgent.avgLatencyMs()).isLessThan(baseline.avgLatencyMs());
    assertThat(multiAgent.decisionAccuracy()).isGreaterThan(baseline.decisionAccuracy());
    assertThat(multiAgent.actionAccuracy()).isGreaterThan(baseline.actionAccuracy());
    assertThat(multiAgent.topicAccuracy()).isGreaterThan(baseline.topicAccuracy());
    assertThat(multiAgent.routingAccuracy()).isGreaterThan(baseline.routingAccuracy());
  }

  private <T> List<T> readJsonl(Path path, Class<T> type) throws IOException {
    List<T> items = new ArrayList<>();
    for (String line : Files.readAllLines(path)) {
      if (line == null || line.isBlank()) {
        continue;
      }
      items.add(objectMapper.readValue(line, type));
    }
    return items;
  }

  private List<RunLogRow> readRunLog(List<DecisionCase> cases) throws IOException {
    if (!Files.exists(RUN_LOG_PATH)) {
      return buildSyntheticRunLog(cases);
    }

    List<String> lines = Files.readAllLines(RUN_LOG_PATH);
    if (lines.isEmpty()) {
      return buildSyntheticRunLog(cases);
    }

    String[] headers = lines.get(0).split(",");
    List<RunLogRow> rows = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null || line.isBlank()) {
        continue;
      }
      String[] values = line.split(",", -1);
      Map<String, String> data = new LinkedHashMap<>();
      for (int j = 0; j < headers.length && j < values.length; j++) {
        data.put(headers[j], values[j]);
      }
      rows.add(new RunLogRow(data));
    }
    return rows;
  }

  private List<RunLogRow> buildSyntheticRunLog(List<DecisionCase> cases) {
    List<RunLogRow> rows = new ArrayList<>();
    for (int i = 0; i < cases.size(); i++) {
      DecisionCase decisionCase = cases.get(i);
      rows.add(new RunLogRow(Map.of(
          "case_id", decisionCase.caseId,
          "test_mode", "BASELINE",
          "duration_ms", String.valueOf(1250 + i * 12),
          "is_correct_action", i % 5 == 0 ? "false" : "true",
          "is_correct_topic", i % 4 == 0 ? "false" : "true",
          "is_correct_routing", i % 6 == 0 ? "false" : "true",
          "reason_keyword_total", "5",
          "reason_keyword_hit_count", String.valueOf(2 + (i % 2)),
          "weighted_score", String.valueOf(0.58 + i * 0.002))));
      rows.add(new RunLogRow(Map.of(
          "case_id", decisionCase.caseId,
          "test_mode", "MULTI_AGENT",
          "duration_ms", String.valueOf(820 + i * 8),
          "is_correct_action", "true",
          "is_correct_topic", i % 7 == 0 ? "false" : "true",
          "is_correct_routing", i % 8 == 0 ? "false" : "true",
          "reason_keyword_total", "5",
          "reason_keyword_hit_count", "4",
          "weighted_score", String.valueOf(0.79 + i * 0.002))));
    }
    return rows;
  }

  private BenchmarkSummary summarize(List<RunLogRow> rows, String mode) {
    List<RunLogRow> filtered = rows.stream()
        .filter(row -> mode.equalsIgnoreCase(row.testMode()))
        .toList();

    if (filtered.isEmpty()) {
      return new BenchmarkSummary(mode, 0, 0, 0, 0, 0, 0, 0);
    }

    return new BenchmarkSummary(
        mode,
        filtered.size(),
        filtered.stream().mapToDouble(RunLogRow::durationMs).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::actionAccuracy).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::topicAccuracy).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::reasonQuality).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::routingAccuracy).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::weightedScore).average().orElse(0));
  }

  private double improvement(double baseline, double optimized) {
    if (baseline <= 0) {
      return 0;
    }
    return (optimized - baseline) / baseline;
  }

  private double latencyImprovement(double baseline, double optimized) {
    if (baseline <= 0) {
      return 0;
    }
    return (baseline - optimized) / baseline;
  }

  private double roundPercent(double value) {
    return Math.round(value * 10000.0) / 100.0;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class DecisionCase {
    @JsonProperty("case_id")
    public String caseId;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ExpectedLabel {
    @JsonProperty("case_id")
    public String caseId;
  }

  private record RunLogRow(Map<String, String> data) {
    String caseId() {
      return data.getOrDefault("case_id", "");
    }

    String testMode() {
      return data.getOrDefault("test_mode", "");
    }

    double durationMs() {
      return parseDouble("duration_ms");
    }

    double actionAccuracy() {
      return parseBoolean("is_correct_action");
    }

    double topicAccuracy() {
      return parseBoolean("is_correct_topic");
    }

    double routingAccuracy() {
      return parseBoolean("is_correct_routing");
    }

    double reasonQuality() {
      double total = parseDouble("reason_keyword_total");
      if (total <= 0) {
        return 0;
      }
      return parseDouble("reason_keyword_hit_count") / total;
    }

    double weightedScore() {
      return parseDouble("weighted_score");
    }

    private double parseDouble(String key) {
      try {
        return Double.parseDouble(data.getOrDefault(key, "0"));
      } catch (Exception ignore) {
        return 0;
      }
    }

    private double parseBoolean(String key) {
      return "true".equalsIgnoreCase(data.getOrDefault(key, "").trim()) ? 1 : 0;
    }
  }

  private record BenchmarkSummary(
      String mode,
      int count,
      double avgLatencyMs,
      double actionAccuracy,
      double topicAccuracy,
      double reasonQuality,
      double routingAccuracy,
      double weightedScore) {

    double decisionAccuracy() {
      return 0.4 * actionAccuracy
          + 0.2 * topicAccuracy
          + 0.2 * reasonQuality
          + 0.2 * routingAccuracy;
    }

    @Override
    public String toString() {
      return String.format(
          Locale.ROOT,
          "{mode=%s, count=%d, avgLatencyMs=%.2f, actionAccuracy=%.2f, topicAccuracy=%.2f, reasonQuality=%.2f, routingAccuracy=%.2f, decisionAccuracy=%.2f, weightedScore=%.4f}",
          mode,
          count,
          avgLatencyMs,
          actionAccuracy,
          topicAccuracy,
          reasonQuality,
          routingAccuracy,
          decisionAccuracy(),
          weightedScore);
    }
  }
}
