/**
 * 复习训练：按天组织的练习。
 *
 * 两种状态，靠 hash 区分：
 *   1. 计划页      #/review
 *   2. 当天练习页  #/review?date=2026-11-25
 *
 * 一天一张「练习」：当天到期的闪卡进入当天练习（逾期卡片滚动到今天）。
 * 每答一道题会写一条练习记录，所以完成时能给出当天的正确率与错误次数。
 * 每道题右上角的「关联知识点」会拉取同主题（以及知识图谱里相邻主题）的历史闪卡。
 */

import { api } from "../api.js";
import { badge, esc, fmt, icon, openModal, ring, toast } from "../ui.js";

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

const state = {
  date: null,
  detail: null,
  revealed: false,
  busy: false,
};

/* ------------------------------ 工具 ------------------------------ */

function queryParams() {
  const raw = location.hash.split("?")[1] || "";
  return new URLSearchParams(raw);
}

function toISODate(date) {
  const d = new Date(date);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function dayLabel(iso) {
  const today = toISODate(new Date());
  const tomorrow = toISODate(new Date(Date.now() + 86400000));
  if (iso === today) return "今天";
  if (iso === tomorrow) return "明天";
  const d = new Date(`${iso}T00:00:00`);
  return `${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${
    WEEKDAYS[d.getDay()]
  }`;
}

function accuracyText(value) {
  return value == null ? "—" : `${Math.round(value * 100)}%`;
}

const STATUS_BADGE = {
  DONE: ["已完成", "ok"],
  TODAY: ["进行中", "accent"],
  UPCOMING: ["待开始", "neutral"],
  EMPTY: ["无到期", "neutral"],
};

function statusBadge(status) {
  const [text, tone] = STATUS_BADGE[status] || STATUS_BADGE.EMPTY;
  return badge(text, tone);
}

/* --------------------------- 关联知识点弹窗 --------------------------- */

async function showRelated(cardId) {
  let group;
  try {
    group = await api.reviewRelated(cardId);
  } catch (error) {
    toast(`关联知识点加载失败：${error.message}`, "err");
    return;
  }

  const linkTopics = (group?.linkTopics || []).filter(Boolean);
  const cards = group?.cards || [];

  const body = `
    <div class="stack" style="gap:14px">
      <div class="row" style="gap:8px;flex-wrap:wrap">
        ${badge(group?.topic || "知识点", "accent")}
        ${linkTopics.map((topic) => badge(`图谱关联：${topic}`, "neutral")).join("")}
      </div>
      ${
        cards.length
          ? `<div class="list" style="border:1px solid var(--line);border-radius:12px;overflow:hidden">
              ${cards
                .map(
                  (card) => `<div class="list-item" style="align-items:flex-start">
                    <span class="dot ${
                      card.lastResult === "WRONG" ? "err" : card.lastResult === "CORRECT" ? "ok" : ""
                    }" style="margin-top:7px"></span>
                    <div class="li-main">
                      <div class="li-title" style="white-space:normal">${esc(card.question)}</div>
                      <div class="li-meta" style="white-space:pre-wrap;line-height:1.7;margin-top:6px">${esc(
                        card.answer,
                      )}</div>
                      <div class="row" style="gap:8px;margin-top:8px;flex-wrap:wrap">
                        ${badge(card.topic || "知识点", "neutral")}
                        ${badge(card.reason === "GRAPH" ? "图谱关联" : "同主题", "neutral")}
                        ${
                          card.lastResult
                            ? badge(
                                card.lastResult === "CORRECT" ? "上次答对" : "上次答错",
                                card.lastResult === "CORRECT" ? "ok" : "warn",
                              )
                            : ""
                        }
                        <span class="hint" style="margin-left:auto">掌握度 ${card.masteryLevel ?? 0}</span>
                        <span class="hint">${esc(fmt.date(card.createdAt))}</span>
                      </div>
                    </div>
                  </div>`,
                )
                .join("")}
            </div>`
          : `<div class="empty" style="padding:24px">
              <div class="empty-title">还没有同主题的历史题</div>
              <div class="empty-desc">后续复盘会继续为「${esc(group?.topic || "这个知识点")}」生成新题。</div>
            </div>`
      }
      <div class="hint">
        关联范围：同主题的历史闪卡${
          linkTopics.length ? " + 知识图谱里与该主题直接相连的知识点" : ""
        }。
      </div>
    </div>`;

  openModal({ title: "关联知识点", html: body });
}

/* ------------------------------ 计划页 ------------------------------ */

function planPageHead(days) {
  const today = days[0] || {};
  return `<div class="page-head">
    <div>
      <h1 class="page-title">复习训练</h1>
      <p class="page-sub">每天一次练习。今天到期 ${today.total ?? 0} 张，答错的题会更早回到队列。</p>
    </div>
    <div class="page-actions">
      <button class="btn" id="review-refresh">${icon("refresh", 15)}刷新</button>
    </div>
  </div>`;
}

function planStats(days) {
  const today = days[0] || {};
  const weekTotal = days.reduce((sum, day) => sum + (day.total || 0), 0);
  const weekDone = days.reduce((sum, day) => sum + (day.done || 0), 0);
  const cell = (label, value, unit, iconName) => `<div class="stat">
    <div class="stat-label">${icon(iconName, 14)}<span>${esc(label)}</span></div>
    <div class="stat-value">${esc(value)}${unit ? `<span class="stat-unit">${esc(unit)}</span>` : ""}</div>
  </div>`;
  return `<div class="grid grid-4" style="margin-bottom:${weekDone ? 6 : 16}px">
    ${cell("今日题量", today.total ?? 0, "张", "clock")}
    ${cell("今日已完成", today.done ?? 0, "张", "check")}
    ${cell("今日正确率", accuracyText(today.accuracy), "", "target")}
    ${cell("未来 7 天", weekTotal, "张", "layers")}
  </div>
  ${weekDone ? `<div class="hint" style="margin:0 0 14px">最近 7 天共完成 ${weekDone} 张。</div>` : ""}`;
}

function dayCardHtml(day) {
  const empty = (day.total || 0) === 0;
  return `<button class="card day-card" data-date="${esc(day.date)}" ${empty ? "disabled" : ""}
    style="text-align:left;cursor:${empty ? "default" : "pointer"};opacity:${empty ? 0.6 : 1};box-shadow:none">
    <div class="card-body stack" style="gap:8px;padding:16px 18px">
      <div class="row" style="gap:8px">
        <strong style="font-size:14px">${esc(dayLabel(day.date))}</strong>
        <span class="hint">${esc(day.date)}</span>
        <span class="spacer"></span>
        ${statusBadge(day.status)}
      </div>
      <div class="stat-value" style="font-size:24px">${day.total}<span class="stat-unit">张</span></div>
      <div class="hint">已完成 ${day.done} · 剩余 ${day.remaining}</div>
      <div class="row" style="gap:10px;flex-wrap:wrap">
        <span class="hint">正确率 ${accuracyText(day.accuracy)}</span>
        <span class="hint" style="color:var(--danger)">错误 ${day.wrong} 次</span>
        ${empty ? "" : '<span class="spacer"></span><span class="hint">进入练习 →</span>'}
      </div>
    </div>
  </button>`;
}

function studyPlanHtml(plan) {
  const weak = plan?.weakTopics || [];
  const order = plan?.reviewOrder || [];
  const gaps = plan?.roleGapSuggestions || [];
  return `<div class="card">
    <div class="card-head">
      <div class="icon-box accent">${icon("target", 15)}</div>
      <div>
        <div class="card-title">薄弱点</div>
        <div class="card-desc">来自最近几场的复盘</div>
      </div>
    </div>
    <div class="card-body tight">
      ${
        weak.length
          ? `<div class="list">${weak
              .map(
                (topic, index) => `<div class="list-item">
                  <div class="icon-box ${index === 0 ? "accent" : ""}">${index + 1}</div>
                  <div class="li-main">
                    <div class="li-title">${esc(topic)}</div>
                    <div class="li-meta">${index === 0 ? "建议优先复习" : "次优先"}</div>
                  </div>
                </div>`,
              )
              .join("")}</div>`
          : `<div class="empty" style="padding:26px">
              <div class="empty-title">暂无薄弱点</div>
              <div class="empty-desc">完成几场面试后，这里会排出优先复习的知识点。</div>
              <button class="btn btn-sm" data-go="#/interview">去面试</button>
            </div>`
      }
    </div>
    ${order.length ? `<div class="card-foot"><span>推荐顺序：${esc(order.join(" → "))}</span></div>` : ""}
    ${
      gaps.length
        ? `<div class="card-body stack" style="gap:8px;border-top:1px solid var(--line)">
            ${gaps
              .map(
                (gap) => `<div class="row" style="align-items:flex-start;gap:8px">
                  <span style="color:var(--warn);margin-top:2px">${icon("warn", 14)}</span>
                  <span class="hint" style="line-height:1.7">${esc(gap)}</span>
                </div>`,
              )
              .join("")}
          </div>`
        : ""
    }
  </div>`;
}

async function renderPlan() {
  let days = [];
  let plan = null;
  let failure = null;
  try {
    days = (await api.reviewCalendar(undefined, 7)) || [];
    plan = await api.reviewPlan().catch(() => null);
  } catch (error) {
    failure = error;
  }

  const totalCards = days.reduce((sum, day) => sum + (day.total || 0), 0);

  const html = `${planPageHead(days)}
    ${
      failure
        ? `<div class="card" style="margin-bottom:16px"><div class="card-body">
            <div class="empty"><div class="empty-title">练习计划加载失败</div>
            <div class="empty-desc">${esc(failure.message)}</div></div></div></div>`
        : ""
    }
    ${
      totalCards === 0
        ? `<div class="card"><div class="card-body"><div class="empty">
            <div class="empty-title">还没有需要复习的闪卡</div>
            <div class="empty-desc">完成一场面试并生成复盘后，薄弱知识点会自动变成闪卡排进每天练习。</div>
            <button class="btn btn-primary" data-go="#/interview">去面试</button>
          </div></div></div>`
        : `${planStats(days)}
           <div class="grid grid-3" style="margin-bottom:16px">
             ${days.map(dayCardHtml).join("")}
           </div>
           ${studyPlanHtml(plan)}`
    }`;

  return {
    html,
    after(root) {
      root.querySelector("#review-refresh")?.addEventListener("click", () => location.reload());
      root.querySelectorAll("[data-go]").forEach((node) => {
        node.addEventListener("click", () => {
          location.hash = node.dataset.go;
        });
      });
      root.querySelectorAll(".day-card[data-date]").forEach((node) => {
        node.addEventListener("click", () => {
          if (node.disabled) return;
          location.hash = `#/review?date=${node.dataset.date}`;
        });
      });
    },
  };
}

/* ------------------------------ 练习页 ------------------------------ */

function practiceHead(detail) {
  return `<div class="page-head">
    <div>
      <h1 class="page-title">${esc(dayLabel(detail.date))}的练习</h1>
      <p class="page-sub">${esc(detail.date)} · 共 ${detail.total} 张，已完成 ${detail.done} 张${
        detail.accuracy == null ? "" : `，正确率 ${accuracyText(detail.accuracy)}`
      }</p>
    </div>
    <div class="page-actions">
      <button class="btn" id="back-plan">${icon("chevron", 14)}返回计划</button>
    </div>
  </div>`;
}

function practiceStats(detail) {
  const cell = (label, value, unit, iconName, tone) => `<div class="stat">
    <div class="stat-label">${icon(iconName, 14)}<span>${esc(label)}</span></div>
    <div class="stat-value" ${tone ? `style="color:${tone}"` : ""}>${esc(value)}${
      unit ? `<span class="stat-unit">${esc(unit)}</span>` : ""
    }</div>
  </div>`;
  return `<div class="grid grid-4" style="margin-bottom:16px">
    ${cell("本日题量", detail.total, "张", "layers")}
    ${cell("已完成", detail.done, "张", "check")}
    ${cell("正确率", accuracyText(detail.accuracy), "", "target")}
    ${cell("错误", detail.wrong, "次", "warn", detail.wrong > 0 ? "var(--danger)" : "")}
  </div>`;
}

function progressBar(detail) {
  const total = detail.total || 1;
  const pct = Math.round((detail.done / total) * 100);
  return `<div class="meter" style="margin-bottom:14px">
    <div class="meter-top"><span>本日进度</span><b>${detail.done} / ${detail.total}</b></div>
    <div class="bar"><i style="width:${pct}%"></i></div>
  </div>`;
}

function currentCard(detail) {
  const pending = detail.cards.filter((card) => !card.lastResult);
  const card = pending[0];
  return card ? { card, index: detail.done + 1 } : null;
}

function cardHtml(detail) {
  const pending = currentCard(detail);
  if (!pending) return finishedHtml(detail);
  const { card, index } = pending;
  return `<div class="card" style="box-shadow:none">
    <div class="card-head">
      <div class="icon-box accent">${icon("layers", 15)}</div>
      <div>
        <div class="card-title">第 ${index} 张 / 共 ${detail.total} 张</div>
        <div class="card-desc">${esc(card.topic || "知识点")}</div>
      </div>
      <div class="card-head-actions">
        ${badge(card.topic || "知识点", "neutral")}
        <button class="btn btn-sm" id="related" data-card="${card.cardId}" type="button">
          ${icon("knowledge", 13)}关联知识点
        </button>
      </div>
    </div>
    <div class="card-body stack" style="gap:16px">
      <div style="font-size:15px;font-weight:560;line-height:1.75">${esc(card.question)}</div>
      ${
        state.revealed
          ? `<div style="border-top:1px solid var(--line);padding-top:14px">
              <div class="hint" style="margin-bottom:6px">参考答案</div>
              <div style="font-size:13px;line-height:1.85;color:var(--ink-2)">${esc(card.answer)}</div>
            </div>`
          : `<button class="btn btn-primary" id="reveal" ${state.busy ? "disabled" : ""}>显示答案</button>`
      }
    </div>
    ${
      state.revealed
        ? `<div class="card-foot">
            <span>如实打分，遗忘曲线据此调整</span>
            <span class="spacer"></span>
            <button class="btn btn-danger btn-sm" id="wrong" ${state.busy ? "disabled" : ""}>忘了</button>
            <button class="btn btn-primary btn-sm" id="correct" ${state.busy ? "disabled" : ""}>记得</button>
          </div>`
        : ""
    }
  </div>`;
}

function finishedHtml(detail) {
  return `<div class="card">
    <div class="card-body" style="display:flex;gap:24px;align-items:center;flex-wrap:wrap;padding:32px">
      <div style="width:132px">${ring(detail.accuracy ?? 0, { size: 120, label: "本日正确率" })}</div>
      <div class="stack" style="gap:10px">
        <div style="font-size:16px;font-weight:600">${esc(dayLabel(detail.date))}的练习已完成</div>
        <div class="hint" style="line-height:1.8">
          共 ${detail.done} 张，答对 ${detail.correct} 张，答错
          <b style="color:var(--danger)">${detail.wrong}</b> 次，正确率
          <b>${accuracyText(detail.accuracy)}</b>。答错的卡片会更早回到队列。
        </div>
        <div class="row">
          <button class="btn btn-primary" id="back-plan-2">返回练习计划</button>
          <button class="btn" data-go="#/memory">查看记忆与画像</button>
        </div>
      </div>
    </div>
  </div>`;
}

function bodyHtml(detail) {
  const pending = currentCard(detail);
  return `<div class="grid grid-2-1">
    <div class="card">
      <div class="card-head">
        <div>
          <div class="card-title">今日练习</div>
          <div class="card-desc">按遗忘曲线调度，答错会缩短间隔</div>
        </div>
      </div>
      <div class="card-body">
        ${progressBar(detail)}
        ${pending ? cardHtml(detail) : finishedHtml(detail)}
      </div>
    </div>
    <div class="stack">
      <div class="card">
        <div class="card-head"><div><div class="card-title">本日小结</div></div></div>
        <div class="card-body">
          <dl class="kv">
            <dt>题量</dt><dd class="num">${detail.total}</dd>
            <dt>已完成</dt><dd class="num">${detail.done}</dd>
            <dt>答对</dt><dd class="num">${detail.correct}</dd>
            <dt>答错</dt><dd class="num">${detail.wrong}</dd>
            <dt>正确率</dt><dd>${accuracyText(detail.accuracy)}</dd>
          </dl>
        </div>
      </div>
      <div class="card">
        <div class="card-head"><div><div class="card-title">怎么做更有效</div></div></div>
        <div class="card-body">
          <div class="hint" style="line-height:1.8">
            先想清楚再看答案。答错的题会缩短间隔，第二天就会再出现。
          </div>
        </div>
      </div>
    </div>
  </div>`;
}

async function submit(correct) {
  const pending = currentCard(state.detail);
  if (!pending || state.busy) return;
  state.busy = true;
  repaint();

  try {
    const result = await api.submitReview({ cardId: pending.card.cardId, correct });
    state.detail = {
      ...state.detail,
      done: result.done,
      correct: result.correctCount,
      wrong: result.wrongCount,
      accuracy: result.accuracy,
      remaining: result.remaining,
      cards: state.detail.cards.map((card) =>
        card.cardId === pending.card.cardId
          ? { ...card, lastResult: correct ? "CORRECT" : "WRONG" }
          : card,
      ),
    };
    state.revealed = false;
  } catch (error) {
    toast(`提交失败：${error.message}`, "err");
  } finally {
    state.busy = false;
    repaint();
  }
}

function bindPractice(root) {
  root.querySelector("#reveal")?.addEventListener("click", () => {
    state.revealed = true;
    repaint();
  });
  root.querySelector("#correct")?.addEventListener("click", () => submit(true));
  root.querySelector("#wrong")?.addEventListener("click", () => submit(false));
  root.querySelector("#related")?.addEventListener("click", (event) => {
    // 卡片 id 是 19 位长整型，必须保持字符串；转 Number 会丢精度，后端就查不到这张卡
    const cardId = event.currentTarget.dataset.card;
    if (cardId) showRelated(cardId);
  });
  root.querySelector("#back-plan")?.addEventListener("click", () => {
    location.hash = "#/review";
  });
  root.querySelector("#back-plan-2")?.addEventListener("click", () => {
    location.hash = "#/review";
  });
  root.querySelectorAll("[data-go]").forEach((node) => {
    node.addEventListener("click", () => {
      location.hash = node.dataset.go;
    });
  });
}

function repaint() {
  const host = document.querySelector("#practice-body");
  if (!host || !state.detail) return;
  host.innerHTML = bodyHtml(state.detail);
  bindPractice(host);
}

async function renderPractice(date) {
  let detail;
  try {
    detail = await api.reviewPractice(date);
  } catch (error) {
    return {
      html: `<div class="page-head"><div><h1 class="page-title">复习训练</h1></div></div>
        <div class="card"><div class="card-body"><div class="empty">
          <div class="empty-title">练习加载失败</div>
          <div class="empty-desc">${esc(error.message)}</div>
          <button class="btn" data-go="#/review">返回计划</button>
        </div></div></div>`,
      after(root) {
        root.querySelectorAll("[data-go]").forEach((node) => {
          node.addEventListener("click", () => {
            location.hash = node.dataset.go;
          });
        });
      },
    };
  }

  state.date = date;
  state.detail = detail;
  state.revealed = false;
  state.busy = false;

  return {
    html: `${practiceHead(detail)}${practiceStats(detail)}<div id="practice-body">${bodyHtml(
      detail,
    )}</div>`,
    after(root) {
      root.querySelector("#back-plan")?.addEventListener("click", () => {
        location.hash = "#/review";
      });
      bindPractice(root.querySelector("#practice-body") || root);
    },
  };
}

/* ------------------------------- 入口 ------------------------------- */

export default {
  id: "review",
  title: "复习训练",

  async render() {
    const date = queryParams().get("date");
    return date ? renderPractice(date) : renderPlan();
  },
};
