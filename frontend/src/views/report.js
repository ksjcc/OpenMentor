/**
 * 面试报告页。
 *
 * 数据来源：store.session.report（刚结束的那场）或 mockReport（演示）。
 * 后端 finish 接口返回的是知识提炼 JSON 字符串，这里做一次宽容解析。
 */

import { store } from "../store.js";
import { mockReport } from "../mock.js";
import { badge, barList, esc, fmt, icon, ring, toast } from "../ui.js";

function resolveReport() {
  const raw = store.session.report;
  if (!raw) return mockReport;

  if (typeof raw === "string") {
    try {
      const parsed = JSON.parse(raw);
      return normalize(parsed);
    } catch {
      return { ...mockReport, summary: raw };
    }
  }
  return normalize(raw);
}

/** 把后端可能返回的各种字段名收敛成报告页需要的形状。 */
function normalize(raw) {
  if (!raw || typeof raw !== "object") {
    // finish 接口可能只返回一个纯字符串（提炼 JSON），此时退回演示报告结构
    return mockReport;
  }
  return {
    ...mockReport,
    ...raw,
    score: raw.score ?? mockReport.score,
    turns: raw.turns ?? mockReport.turns,
    dimensions:
      Array.isArray(raw.dimensions) && raw.dimensions.length ? raw.dimensions : mockReport.dimensions,
    strengths: raw.strengths ?? mockReport.strengths,
    weaknesses: raw.weaknesses ?? mockReport.weaknesses,
    flashcards: raw.flashcards ?? mockReport.flashcards,
    summary: raw.summary ?? mockReport.summary,
  };
}

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
  return `<div class="grid grid-3">${(cards || [])
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

function toMarkdown(report) {
  const lines = [
    `# 面试复盘 · ${report.topic}`,
    "",
    `- 目标岗位：${report.targetRole}`,
    `- 得分：${report.score}`,
    `- 轮次：${report.turns}`,
    `- 时长：${fmt.dur(report.durationSec)}`,
    "",
    "## 总体评价",
    "",
    report.summary,
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

export default {
  id: "report",
  title: "面试报告",

  async render() {
    const report = resolveReport();
    const passed = report.score >= 75;

    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">面试报告</h1>
          <p class="page-sub">${esc(report.topic)} · ${esc(report.targetRole)} · ${esc(
            fmt.date(report.startedAt),
          )}</p>
        </div>
        <div class="page-actions">
          <button class="btn" id="export">${icon("download", 15)}导出 Markdown</button>
          <button class="btn" data-go="#/overview">返回概览</button>
          <button class="btn btn-primary" data-go="#/interview">${icon("play", 15)}再面一场</button>
        </div>
      </div>

      <div class="grid grid-2-1" style="margin-bottom:16px">
        <div class="card">
          <div class="card-body" style="display:flex;gap:24px;align-items:center;flex-wrap:wrap">
            <div style="width:132px">${ring(report.score / 100, { size: 120, label: "加权总分" })}</div>
            <div class="stack" style="gap:12px;flex:1;min-width:220px">
              <div class="row">
                ${badge(passed ? "达到目标水位" : "低于目标水位", passed ? "ok" : "warn")}
                ${badge(`${report.turns} 轮`, "neutral")}
                ${badge(fmt.dur(report.durationSec), "neutral")}
              </div>
              <div style="font-size:13.5px;line-height:1.8;color:var(--ink-2)">${esc(report.summary)}</div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><div><div class="card-title">能力拆项</div></div></div>
          <div class="card-body">${barList(report.dimensions)}</div>
        </div>
      </div>

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
          <div class="card-desc">基于本场薄弱点推荐</div>
        </div></div>
        <div class="card-body stack" style="gap:12px">
          <div class="hint">
            本场最明显的缺口是把结论停在概念层。建议下一场直接面试
            「${esc(report.weaknesses?.[0]?.slice(0, 18) || "薄弱点")}」相关主题，
            让面试官连续追问三层落地细节。
          </div>
          <div class="row">
            <button class="btn btn-primary" data-go="#/interview">开始专项面试 ${icon("chevron", 13)}</button>
            <button class="btn" data-go="#/memory">查看长期记忆</button>
          </div>
        </div>
      </div>
    `;

    return {
      html,
      after(root) {
        root.querySelectorAll("[data-go]").forEach((node) => {
          node.addEventListener("click", () => {
            location.hash = node.dataset.go;
          });
        });
        root.querySelector("#export")?.addEventListener("click", () => {
          download(`面试复盘-${report.topic}.md`, toMarkdown(report));
          toast("已导出 Markdown", "ok");
        });
      },
    };
  },
};
