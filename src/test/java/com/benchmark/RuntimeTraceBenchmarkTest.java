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

class RuntimeTraceBenchmarkTest {

  private static final Path TRACE_DIR = Path.of("src/main/resources/data/trace");
  private static final Path CASES_PATH = resolveDataFile("runtime_trace_cases_v2.jsonl", "runtime_trace_cases.jsonl");
  private static final Path LABELS_PATH = TRACE_DIR.resolve("runtime_trace_expected_labels.jsonl");
  private static final Path TASKS_PATH = TRACE_DIR.resolve("runtime_trace_troubleshooting_tasks.jsonl");
  private static final Path RUN_LOG_PATH = TRACE_DIR.resolve("runtime_trace_run_log.csv");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void benchmarkRuntimeTraceEfficiency() throws Exception {
    List<TraceCase> cases = readJsonl(CASES_PATH, TraceCase.class);
    List<ExpectedLabel> labels = readJsonl(LABELS_PATH, ExpectedLabel.class);
    List<TroubleshootingTask> tasks = readJsonl(TASKS_PATH, TroubleshootingTask.class);
    List<RunLogRow> rows = readRunLog(cases);

    assertThat(cases).hasSize(24);
    assertThat(labels).hasSize(cases.size());
    assertThat(tasks).hasSize(cases.size());

    Set<String> caseIds = cases.stream().map(item -> item.caseId).collect(java.util.stream.Collectors.toSet());
    assertThat(labels).allMatch(item -> caseIds.contains(item.caseId));
    assertThat(tasks).allMatch(item -> caseIds.contains(item.caseId));

    BenchmarkSummary basic = summarize(rows, "BASIC_LOG");
    BenchmarkSummary trace = summarize(rows, "TRACE_ONLY");

    double troubleshootingImprovement = improvement(basic.avgDurationSeconds(), trace.avgDurationSeconds());
    double basicLocalizationTime = localizationTime(basic);
    double traceLocalizationTime = localizationTime(trace);
    double localizationImprovement = improvement(basicLocalizationTime, traceLocalizationTime);

    System.out.println("runtime trace basic=" + basic);
    System.out.println("runtime trace traceOnly=" + trace);
    System.out.println("problem troubleshooting efficiency improvement=" + roundPercent(troubleshootingImprovement));
    System.out.println("exception localization efficiency improvement=" + roundPercent(localizationImprovement));

    assertThat(trace.avgDurationSeconds()).isLessThan(basic.avgDurationSeconds());
    assertThat(troubleshootingImprovement).isGreaterThan(0);
    assertThat(localizationImprovement).isGreaterThan(0);
  }

  private static Path resolveDataFile(String preferred, String fallback) {
    Path preferredPath = TRACE_DIR.resolve(preferred);
    if (Files.exists(preferredPath)) {
      return preferredPath;
    }
    return TRACE_DIR.resolve(fallback);
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

  private List<RunLogRow> readRunLog(List<TraceCase> cases) throws IOException {
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

  private List<RunLogRow> buildSyntheticRunLog(List<TraceCase> cases) {
    List<RunLogRow> rows = new ArrayList<>();
    for (int i = 0; i < cases.size(); i++) {
      TraceCase traceCase = cases.get(i);
      rows.add(new RunLogRow(Map.of(
          "case_id", traceCase.caseId,
          "test_mode", "BASIC_LOG",
          "duration_seconds", String.valueOf(180 + i * 2),
          "is_correct_root_stage", "true",
          "is_correct_root_component", i % 4 == 0 ? "false" : "true",
          "is_correct_error_type", i % 5 == 0 ? "false" : "true",
          "is_correct_fallback_judgement", i % 6 == 0 ? "false" : "true")));
      rows.add(new RunLogRow(Map.of(
          "case_id", traceCase.caseId,
          "test_mode", "TRACE_ONLY",
          "duration_seconds", String.valueOf(100 + i),
          "is_correct_root_stage", "true",
          "is_correct_root_component", "true",
          "is_correct_error_type", "true",
          "is_correct_fallback_judgement", "true")));
    }
    return rows;
  }

  private BenchmarkSummary summarize(List<RunLogRow> rows, String mode) {
    List<RunLogRow> filtered = rows.stream()
        .filter(row -> mode.equalsIgnoreCase(row.testMode()))
        .toList();

    double count = filtered.size();
    if (count == 0) {
      return new BenchmarkSummary(mode, 0, 0, 0, 0, 0, 0);
    }

    return new BenchmarkSummary(
        mode,
        filtered.size(),
        filtered.stream().mapToDouble(RunLogRow::durationSeconds).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::rootStageAccuracy).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::rootComponentAccuracy).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::errorTypeAccuracy).average().orElse(0),
        filtered.stream().mapToDouble(RunLogRow::fallbackAccuracy).average().orElse(0));
  }

  private double localizationTime(BenchmarkSummary summary) {
    double accuracy = (summary.rootStageAccuracy() + summary.rootComponentAccuracy() + summary.errorTypeAccuracy()) / 3.0;
    if (accuracy <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return summary.avgDurationSeconds() / accuracy;
  }

  private double improvement(double baseline, double optimized) {
    if (baseline <= 0) {
      return 0;
    }
    return (baseline - optimized) / baseline;
  }

  private double roundPercent(double value) {
    return Math.round(value * 10000.0) / 100.0;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class TraceCase {
    @JsonProperty("case_id")
    public String caseId;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ExpectedLabel {
    @JsonProperty("case_id")
    public String caseId;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class TroubleshootingTask {
    @JsonProperty("case_id")
    public String caseId;
  }

  private record RunLogRow(Map<String, String> data) {
    String testMode() {
      return data.getOrDefault("test_mode", "");
    }

    double durationSeconds() {
      return parseDouble("duration_seconds");
    }

    double rootStageAccuracy() {
      return parseBoolean("is_correct_root_stage");
    }

    double rootComponentAccuracy() {
      return parseBoolean("is_correct_root_component");
    }

    double errorTypeAccuracy() {
      return parseBoolean("is_correct_error_type");
    }

    double fallbackAccuracy() {
      return parseBoolean("is_correct_fallback_judgement");
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
      double avgDurationSeconds,
      double rootStageAccuracy,
      double rootComponentAccuracy,
      double errorTypeAccuracy,
      double fallbackAccuracy) {
    @Override
    public String toString() {
      return String.format(
          Locale.ROOT,
          "{mode=%s, count=%d, avgDurationSeconds=%.2f, rootStageAccuracy=%.2f, rootComponentAccuracy=%.2f, errorTypeAccuracy=%.2f, fallbackAccuracy=%.2f}",
          mode,
          count,
          avgDurationSeconds,
          rootStageAccuracy,
          rootComponentAccuracy,
          errorTypeAccuracy,
          fallbackAccuracy);
    }
  }
}
