/**
 * 复习训练页。
 *
 * 交互：一次只出现一张卡（题干 → 翻看答案 → 记得/忘了），提交走
 * /api/review/submit，完成后给本轮总结。这比列表式勾选更像真实的间隔重复复习。
 */

import { api } from "../api.js";
import { mockReview } from "../mock.js";
import { badge, esc, fmt, icon, ring, toast } from "../ui.js";

/** 本轮复习状态（视图重建时保留，避免切页丢进度）。 */
const state = {
  queue: [],
  cursor: 0,
  revealed: false,
  correct: 0,
  wrong: 0,
  loaded: false,
  plan: null,
};

function resetState() {
  state.queue = [];
  state.cursor = 0;
  state.revealed = false;
  state.correct = 0;
  state.wrong = 0;
  state.loaded = false;
  state.plan = null;
}

/* ------------------------------ 数据加载 ------------------------------ */

function pickCards(raw) {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  const list = raw.cards || raw.flashcards || raw.items || [];
  return Array.isArray(list) ? list : [];
}

function normalizeCard(card, index) {
  return {
    id: card.id ?? card.cardId ?? `c${index}`,
    tag: card.tag || card.topic || card.sourceType || "知识点",
    question: card.question || card.q || card.front || card.title || "（无题干）",
    answer: card.answer || card.a || card.back || card.content || "（暂无答案）",
    result: null,
  };
}

async function loadData() {
  if (state.loaded) return;
  try {
    const [today, plan] = await Promise.all([
      api.reviewToday(),
      api.reviewPlan().catch(() => null),
    ]);
    state.queue = pickCards(today).map(normalizeCard);
    state.plan = plan;
  } catch (error) {
    toast(`复习数据加载失败：${error.message}`, "warn");
    state.queue = [];
  } finally {
    state.loaded = true;
  }
}

/* -------------------------------- 渲染 -------------------------------- */

function statsRow() {
  const total = state.queue.length;
  const done = state.correct + state.wrong;
  const accuracy = done ? state.correct / done : 0;
  const remaining = Math.max(0, total - done);

  const cell = (label, value, unit, iconName) => `<div class="stat">
    <div class="stat-label">${icon(iconName, 14)}<span>${esc(label)}</span></div>
    <div class="stat-value">${esc(value)}${unit ? `<span class="stat-unit">${esc(unit)}</span>` : ""}</div>
  </div>`;

  return `<div class="grid grid-4" style="margin-bottom:16px">
    ${cell("今日到期", total, "张", "clock")}
    ${cell("已完成", done, "张", "check")}
    ${cell("本轮正确率", done ? `${Math.round(accuracy * 100)}` : "—", done ? "%" : "", "target")}
    ${cell("剩余", remaining, "张", "layers")}
  </div>`;
}

function progressBar() {
  const total = state.queue.length || 1;
  const done = state.correct + state.wrong;
  const pct = Math.round((done / total) * 100);
  return `<div class="meter" style="margin-bottom:14px">
    <div class="meter-top">
      <span>本轮进度</span>
      <b>${done} / ${state.queue.length}</b>
    </div>
    <div class="bar"><i style="width:${pct}%"></i></div>
  </div>`;
}

function cardHtml(card) {
  return `<div class="card" style="box-shadow:none">
    <div class="card-head">
      <div class="icon-box accent">${icon("layers", 15)}</div>
      <div>
        <div class="card-title">第 ${state.cursor + 1} 张 / 共 ${state.queue.length} 张</div>
        <div class="card-desc">${esc(card.tag)}</div>
      </div>
      <div class="card-head-actions">${badge(card.tag, "neutral")}</div>
    </div>
    <div class="card-body stack" style="gap:16px">
      <div style="font-size:15px;font-weight:560;line-height:1.75">${esc(card.question)}</div>
      ${
        state.revealed
          ? `<div style="border-top:1px solid var(--line);padding-top:14px">
              <div class="hint" style="margin-bottom:6px">参考答案</div>
              <div style="font-size:13px;line-height:1.85;color:var(--ink-2)">${esc(card.answer)}</div>
            </div>`
          : `<button class="btn btn-primary" id="reveal">显示答案</button>`
      }
    </div>
    ${
      state.revealed
        ? `<div class="card-foot">
            <span>如实打分，遗忘曲线据此调整</span>
            <span class="spacer"></span>
            <button class="btn btn-danger btn-sm" id="wrong">忘了</button>
            <button class="btn btn-primary btn-sm" id="correct">记得</button>
          </div>`
        : ""
    }
  </div>`;
}

function finishedHtml() {
  const done = state.correct + state.wrong;
  const accuracy = done ? state.correct / done : 0;
  return `<div class="card">
    <div class="card-body" style="display:flex;gap:24px;align-items:center;flex-wrap:wrap;justify-content:center;padding:36px">
      <div style="width:132px">${ring(accuracy, { size: 120, label: "本轮正确率" })}</div>
      <div class="stack" style="gap:10px">
        <div style="font-size:16px;font-weight:600">本轮复习完成</div>
        <div class="hint">共 ${done} 张，记得 ${state.correct} 张，忘了 ${state.wrong} 张。
        忘记的卡片会在短时间内重新进入队列。</div>
        <div class="row">
          <button class="btn btn-primary" id="again">${icon("refresh", 14)}再来一轮</button>
          <button class="btn" data-go="#/overview">返回概览</button>
        </div>
      </div>
    </div>
  </div>`;
}

function planHtml() {
  const plan = state.plan || mockReview.plan;
  const weak = plan.weakTopics || [];
  const order = plan.reviewOrder || [];
  const gaps = plan.roleGapSuggestions || [];

  return `<div class="card">
    <div class="card-head"><div>
      <div class="card-title">学习计划</div>
      <div class="card-desc">按薄弱点与岗位缺口生成</div>
    </div></div>
    <div class="card-body tight">
      <div class="list">
        ${weak.length
          ? weak
              .map(
                (topic, index) => `<div class="list-item">
                  <div class="icon-box ${index === 0 ? "accent" : ""}">${index + 1}</div>
                  <div class="li-main">
                    <div class="li-title">${esc(topic)}</div>
                    <div class="li-meta">${index === 0 ? "建议优先复习" : "次优先"}</div>
                  </div>
                </div>`,
              )
              .join("")
          : `<div class="empty" style="padding:24px"><div class="empty-title">暂无薄弱点</div></div>`}
      </div>
    </div>
    ${
      order.length
        ? `<div class="card-foot"><span>推荐顺序：${esc(order.join(" → "))}</span></div>`
        : ""
    }
  </div>

  ${
    gaps.length
      ? `<div class="card">
          <div class="card-head"><div>
            <div class="card-title">岗位缺口建议</div>
            <div class="card-desc">对照目标岗位能力项</div>
          </div></div>
          <div class="card-body stack" style="gap:10px">
            ${gaps
              .map(
                (gap) => `<div class="row" style="align-items:flex-start;gap:8px">
                  <span style="color:var(--warn);margin-top:2px">${icon("warn", 14)}</span>
                  <span class="hint" style="line-height:1.75">${esc(gap)}</span>
                </div>`,
              )
              .join("")}
          </div>
        </div>`
      : ""
  }`;
}

function bodyHtml() {
  if (!state.queue.length) {
    return `<div class="card"><div class="card-body">
      <div class="empty">
        <div class="empty-title">今日没有到期的复习卡</div>
        <div class="empty-desc">完成一场面试并生成复盘后，薄弱点会自动进入复习队列。</div>
        <button class="btn btn-primary" data-go="#/interview">去面试</button>
      </div>
    </div></div>`;
  }

  const finished = state.cursor >= state.queue.length;
  const current = finished ? null : state.queue[state.cursor];

  return `<div class="grid grid-2-1">
    <div class="card">
      <div class="card-head"><div>
        <div class="card-title">今日复习</div>
        <div class="card-desc">按遗忘曲线调度，答错会缩短间隔</div>
      </div></div>
      <div class="card-body">
        ${progressBar()}
        ${finished ? finishedHtml() : cardHtml(current)}
      </div>
    </div>
    <div class="stack">${planHtml()}</div>
  </div>`;
}

/* -------------------------------- 行为 -------------------------------- */

async function submit(correct) {
  const card = state.queue[state.cursor];
  if (!card) return;

  card.result = correct ? "correct" : "wrong";
  if (correct) state.correct += 1;
  else state.wrong += 1;

  state.cursor += 1;
  state.revealed = false;
  repaint();

  try {
    await api.submitReview({ cardId: card.id, correct });
  } catch (error) {
    toast(`提交失败：${error.message}`, "err");
  }}

function repaint() {
  const host = document.querySelector("#review-body");
  if (!host) return;
  host.innerHTML = bodyHtml();
  bind(host);
}

function bind(root) {
  root.querySelector("#reveal")?.addEventListener("click", () => {
    state.revealed = true;
    repaint();
  });
  root.querySelector("#correct")?.addEventListener("click", () => submit(true));
  root.querySelector("#wrong")?.addEventListener("click", () => submit(false));
  root.querySelector("#again")?.addEventListener("click", () => {
    state.queue = state.queue.map((card) => ({ ...card, result: null }));
    state.cursor = 0;
    state.correct = 0;
    state.wrong = 0;
    state.revealed = false;
    repaint();
  });
  root.querySelectorAll("[data-go]").forEach((node) => {
    node.addEventListener("click", () => {
      location.hash = node.dataset.go;
    });
  });
}

export default {
  id: "review",
  title: "复习训练",

  async render() {
    await loadData();
    return {
      html: `
        <div class="page-head">
          <div>
            <h1 class="page-title">复习训练</h1>
            <p class="page-sub">按遗忘曲线调度的间隔重复，答错的卡片会更快回到队列。</p>
          </div>
          <div class="page-actions">
            <button class="btn" id="review-reset">${icon("refresh", 15)}重新载入</button>
          </div>
        </div>
        ${statsRow()}
        <div id="review-body">${bodyHtml()}</div>
      `,
      after(root) {
        bind(document.querySelector("#review-body") || root);
        root.querySelector("#review-reset")?.addEventListener("click", () => {
          resetState();
          location.reload();
        });
      },
    };
  },

  unmount() {
    // 进度留在内存里，切页回来还能继续；真正的清空只在"再来一轮"时发生
  },
};
