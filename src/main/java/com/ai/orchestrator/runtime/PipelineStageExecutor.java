package com.ai.orchestrator.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ai.orchestrator.pipeline.InterviewPipelineContext;
import com.entity.Trace.TraceEvent;

@Component
public class PipelineStageExecutor {

  public void execute(
      InterviewPipelineContext context,
      PipelineStageName stage,
      Runnable action,
      StageRecoveryPolicy recoveryPolicy) {
    PipelineRuntime runtime = context.getPipelineRuntime();
    if (runtime == null) {
      action.run();
      return;
    }

    if (!runtime.canRun(stage)) {
      return;
    }

    long start = System.currentTimeMillis();
    updateState(runtime, stage, StageExecutionStatus.RUNNING, StageRecoveryAction.NONE, null, null, start, 0L);

    try {
      action.run();
      long end = System.currentTimeMillis();
      updateState(runtime, stage, StageExecutionStatus.SUCCESS, StageRecoveryAction.NONE, null, null, start, end);
      recordTrace(context, stage, StageExecutionStatus.SUCCESS, StageRecoveryAction.NONE, null, null, start, end);
    } catch (Exception exception) {
      StageRecoveryResult recovery = recoveryPolicy == null
          ? StageRecoveryResult.fail(exception)
          : recoveryPolicy.recover(context, exception);

      long end = System.currentTimeMillis();
      if (recovery.rethrow()) {
        updateState(
            runtime,
            stage,
            StageExecutionStatus.FAILED,
            recovery.action(),
            recovery.failureReason(),
            recovery.recoveryReason(),
            start,
            end);
        recordTrace(
            context,
            stage,
            StageExecutionStatus.FAILED,
            recovery.action(),
            recovery.failureReason(),
            recovery.recoveryReason(),
            start,
            end);
        throw new StageRuntimeException(stage, recovery.failureReason(), exception);
      }

      StageExecutionStatus recoveredStatus = recovery.status();
      updateState(
          runtime,
          stage,
          recoveredStatus,
          recovery.action(),
          recovery.failureReason(),
          recovery.recoveryReason(),
          start,
          end);
      recordTrace(
          context,
          stage,
          recoveredStatus,
          recovery.action(),
          recovery.failureReason(),
          recovery.recoveryReason(),
          start,
          end);
    }
  }

  private void updateState(
      PipelineRuntime runtime,
      PipelineStageName stage,
      StageExecutionStatus status,
      StageRecoveryAction recoveryAction,
      String failureReason,
      String recoveryReason,
      long start,
      long end) {
    runtime.update(StageExecutionState.builder()
        .stage(stage)
        .status(status)
        .recoveryAction(recoveryAction)
        .failureReason(failureReason)
        .recoveryReason(recoveryReason)
        .startedAt(start)
        .endedAt(end)
        .latencyMs(end <= 0 ? 0 : Math.max(0, end - start))
        .build());
  }

  private void recordTrace(
      InterviewPipelineContext context,
      PipelineStageName stage,
      StageExecutionStatus status,
      StageRecoveryAction recoveryAction,
      String failureReason,
      String recoveryReason,
      long start,
      long end) {
    if (context.getTraceContext() == null) {
      return;
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("status", status.name());
    metadata.put("recoveryAction", recoveryAction.name());
    metadata.put("failureReason", failureReason == null ? "" : failureReason);
    metadata.put("recoveryReason", recoveryReason == null ? "" : recoveryReason);

    TraceEvent event = TraceEvent.builder()
        .stage(stage.name().toLowerCase())
        .Type("runtime_state")
        .startTime(start)
        .endTime(end)
        .latencyMs(end <= 0 ? 0 : Math.max(0, end - start))
        .input(context.getQuestion() == null ? "" : context.getQuestion())
        .output(status.name())
        .metadata(metadata)
        .build();
    context.getTraceContext().addEvent(event);
  }
}
