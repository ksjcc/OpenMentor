/**
 * 面试报告页。
 *
 * 数据来源：GET /api/interview/{sessionId}（真实复盘）。
 * 进来的方式有两种：
 * 1. 刚结束面试 → store.session.sessionId 就是这一场；
 * 2. 从概览/列表点「查看报告」→ 会先把 sessionId 写进 store。
 * 都没有时取最近一场已结束的面试；一场都没有就展示空状态，绝不编数据。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { badge, barList, esc, fmt, icon, ring, toast } from "../ui.js";

/* ------------------------------ 数据加载 ------------------------------ */

async function resolveSessionId() {
  if (store.session.sessionId) {
    return store.session.sessionId;
  }
  const list = (await api.interviewList(store.settings.userId, 20)) || [];
  if (!list.length) return null;
  const finished = list.find((item) => item.status === "FINISHED");
  return (finished || list[0]).sessionId;
}

async function loadReport() {
  const sessionId = await resolveSessionId();
  if (!sessionId) return null;
  return api.interviewReport(sessionId, store.settings.userId);
}

/* -------------------------------- 渲染 -------------------------------- */

function listBlock(title, items, tone, iconName) {
  return `<div class="card">
    <div class="card-head">
      <div class="icon-box ${tone === "ok" ? "accent" : ""}">${icon(iconName, 15)}</div>
      <div class="card-title">${esc(title)}</div>
    </div>
    <div class="card-body tight">
      ${
        items?.length
          ? `<ul style="margin:0;padding:16px 18px 16px 34px;display:grid;gap:10px;font-size:13px;color:var(--ink-2)">
              ${items.map((text) => `<li>${esc(text)}</li>`).join("")}
            </ul>`
          : `<div class="empty" style="padding:26px"><div class="empty-title">暂无</div></div>`
      }
    </div>
  </div>`;
}

function flashcardBlock(cards) {
  if (!cards?.length) {
    return `<div class="card"><div class="card-body"><div class="empty" style="padding:26px">
      <div class="empty-title">本场没有生成闪卡</div>
      <div class="empty-desc">复盘完成后，薄弱点会自动进入复习训练队列。</div>
    </div></div></div>`;
  }
  return `<div class="grid grid-3">${cards
    .map(
      (card) => `<details class="card" style="overflow:hidden">
        <summary style="cursor:pointer;list-style:none;padding:16px 18px">
          <div class="row" style="margin-bottom:6px">${badge(card.tag || "闪卡", "accent")}</div>
          <div style="font-size:13px;font-weight:560;line-height:1.6">${esc(card.q)}</div>
          <div class="hint" style="margin-top:8px">点击查看答案</div>
        </summary>
        <div style="padding:0 18px 16px;border-top:1px solid var(--line);padding-top:14px">
          <div style="font-size:12.5px;line-height:1.75;color:var(--ink-2)">${esc(card.a)}</div>
        </div>
      </details>`,
    )
    .join("")}</div>`;
}

function transcriptBlock(messages) {
  if (!messages?.length) return "";
  return `<details class="card" style="margin-bottom:16px">
    <summary style="cursor:pointer;list-style:none;padding:16px 18px">
      <div class="row">
        <div class="icon-box accent">${icon("interview", 15)}</div>
        <div>
          <div class="card-title">完整对话</div>
          <div class="card-desc">共 ${messages.length} 条 · 展开回看当时的问答</div>
        </div>
        <span class="spacer"></span>
        <span class="hint">展开</span>
      </div>
    </summary>
    <div class="card-body tight" style="border-top:1px solid var(--line)">
      <div class="list">
        ${messages
          .map((message) => {
            const isAi = message.role === "ai";
            return `<div class="list-item" style="align-items:flex-start">
              <span class="dot ${isAi ? "ok" : ""}" style="margin-top:7px"></span>
              <div class="li-main">
                <div class="li-title">${isAi ? "面试官" : "我"}${
                  !isAi && message.score != null ? ` · ${message.score} 分` : ""
                }</div>
                <div class="li-meta" style="white-space:pre-wrap;line-height:1.75">${esc(
                  message.content,
                )}</div>
              </div>
            </div>`;
          })
          .join("")}
      </div>
    </div>
  </details>`;
}

function emptyPageHtml(reason) {
  return `<div class="page-head">
      <div>
        <h1 class="page-title">面试报告</h1>
        <p class="page-sub">每场面试结束后，这里会生成复盘、闪卡与下一步建议。</p>
      </div>
      <div class="page-actions">
        <button class="btn btn-primary" data-go="#/interview">${icon("play", 15)}开始面试</button>
      </div>
    </div>
    <div class="card"><div class="card-body">
      <div class="empty">
        <div class="empty-title">还没有可复盘的面试</div>
        <div class="empty-desc">${esc(reason)}</div>
        <button class="btn btn-primary" data-go="#/interview">去面试</button>
      </div>
    </div></div>`;
}

function toMarkdown(report) {
  const lines = [
    `# 面试复盘 · ${report.topic || "未命名主题"}`,
    "",
    `- 目标岗位：${report.targetRole || "—"}`,
    `- 得分：${report.score ?? "—"}`,
    `- 轮次：${report.turns ?? "—"}`,
    `- 时长：${fmt.dur(report.durationSec)}`,
    "",
    "## 总体评价",
    "",
    report.summary || "—",
    "",
    "## 亮点",
    "",
    ...(report.strengths || []).map((text) => `- ${text}`),
    "",
    "## 待改进",
    "",
    ...(report.weaknesses || []).map((text) => `- ${text}`),
    "",
    "## 复习闪卡",
    "",
    ...(report.flashcards || []).flatMap((card) => [`### ${card.q}`, "", card.a, ""]),
  ];
  return lines.join("\n");
}

function download(name, content, type = "text/markdown;charset=utf-8") {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

function reportPageHtml(report) {
  const passed = (report.score ?? 0) >= 75;
  return `
    <div class="page-head">
      <div>
        <h1 class="page-title">面试报告</h1>
        <p class="page-sub">${esc(report.topic || "未命名主题")} · ${esc(
          report.targetRole || "未填岗位",
        )} · ${esc(fmt.date(report.startedAt || report.endedAt))}</p>
      </div>
      <div class="page-actions">
        ${report.status === "IN_PROGRESS" ? badge("进行中", "accent") : badge("已结束", "ok")}
        <button class="btn" id="export">${icon("download", 15)}导出 Markdown</button>
        <button class="btn" data-go="#/overview">返回概览</button>
        <button class="btn btn-primary" data-go="#/interview">${icon("play", 15)}再面一场</button>
      </div>
    </div>

    <div class="grid grid-2-1" style="margin-bottom:16px">
      <div class="card">
        <div class="card-body" style="display:flex;gap:24px;align-items:center;flex-wrap:wrap">
          <div style="width:132px">${ring((report.score ?? 0) / 100, {
            size: 120,
            label: "加权总分",
          })}</div>
          <div class="stack" style="gap:12px;flex:1;min-width:220px">
            <div class="row">
              ${
                report.score == null
                  ? badge("本场未评分", "warn")
                  : badge(passed ? "达到目标水位" : "低于目标水位", passed ? "ok" : "warn")
              }
              ${badge(`${report.turns ?? 0} 轮`, "neutral")}
              ${badge(fmt.dur(report.durationSec), "neutral")}
              ${report.resumeTitle ? badge(`简历：${report.resumeTitle}`, "neutral") : ""}
            </div>
            <div style="font-size:13.5px;line-height:1.8;color:var(--ink-2)">${esc(
              report.summary || "本场还没有生成总体评价。",
            )}</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><div><div class="card-title">能力拆项</div></div></div>
        <div class="card-body">
          ${
            report.dimensions?.length
              ? barList(report.dimensions)
              : `<div class="hint" style="line-height:1.8">本场没有留下评分明细。回答得越具体，这里越完整。</div>`
          }
        </div>
      </div>
    </div>

    ${transcriptBlock(report.messages)}

    <div class="grid grid-2" style="margin-bottom:16px">
      ${listBlock("做得好的地方", report.strengths, "ok", "check")}
      ${listBlock("需要补的地方", report.weaknesses, "warn", "warn")}
    </div>

    <div class="stack" style="gap:12px;margin-bottom:16px">
      <div class="row">
        <h2 style="font-size:15px">复习闪卡</h2>
        <span class="hint">已写入长期记忆，到期后进入今日复习</span>
      </div>
      ${flashcardBlock(report.flashcards)}
    </div>

    <div class="card">
      <div class="card-head"><div>
        <div class="card-title">下一步</div>
        <div class="card-desc">基于本场薄弱点与复盘的训练建议</div>
      </div></div>
      <div class="card-body stack" style="gap:12px">
        ${
          report.nextPlan?.length
            ? `<div class="hint" style="line-height:1.8">${report.nextPlan
                .map((item) => `· ${esc(item)}`)
                .join("<br>")}</div>`
            : `<div class="hint" style="line-height:1.8">建议围绕「${esc(
                (report.weaknesses?.[0] || "本场薄弱点").slice(0, 18),
              )}」再面一场，让面试官连续追问三层落地细节。</div>`
        }
        <div class="row">
          <button class="btn btn-primary" data-go="#/interview">开始专项面试 ${icon("chevron", 13)}</button>
          <button class="btn" data-go="#/review">去复习训练</button>
          <button class="btn" data-go="#/memory">查看长期记忆</button>
        </div>
      </div>
    </div>`;
}

export default {
  id: "report",
  title: "面试报告",

  async render() {
    let report = null;
    let failure = null;
    try {
      report = await loadReport();
    } catch (error) {
      failure = error;
    }

    const html = report
      ? reportPageHtml(report)
      : emptyPageHtml(
          failure ? `复盘加载失败：${failure.message}` : "完成一场模拟面试后，这里会自动生成复盘。",
        );

    return {
      html,
      after(root) {
        root.querySelectorAll("[data-go]").forEach((node) => {
          node.addEventListener("click", () => {
            location.hash = node.dataset.go;
          });
        });
        root.querySelector("#export")?.addEventListener("click", () => {
          if (!report) return;
          download(`面试复盘-${report.topic || "面试"}.md`, toMarkdown(report));
          toast("已导出 Markdown", "ok");
        });
      },
    };
  },
};
