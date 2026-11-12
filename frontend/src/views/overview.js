/**
 * 概览页。所有数据都尽量取后端，取不到就落到演示数据，
 * 页面顶部会通过"演示数据"标记告诉使用者当前是哪种来源。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { mockDashboard } from "../mock.js";
import {
  areaChart,
  badge,
  barList,
  esc,
  fmt,
  icon,
  ring,
  toast,
} from "../ui.js";

function statCard(label, value, unit, delta, iconName) {
  const dir = delta > 0.001 ? "up" : delta < -0.001 ? "down" : "flat";
  const arrow = dir === "up" ? "arrowUp" : dir === "down" ? "arrowDown" : "minus";
  const deltaText =
    dir === "flat" ? "持平" : `${dir === "up" ? "+" : ""}${(delta * 100).toFixed(0)}%`;
  return `<div class="stat">
    <div class="stat-label">${icon(iconName, 14)}<span>${esc(label)}</span></div>
    <div class="stat-value">${esc(value)}${unit ? `<span class="stat-unit">${esc(unit)}</span>` : ""}</div>
    <div class="stat-delta ${dir}">${icon(arrow, 13)}<span>${deltaText}</span><span style="color:var(--ink-4)">较上周</span></div>
  </div>`;
}

function recentTable(rows) {
  if (!rows.length) {
    return `<div class="card-body"><div class="empty"><div class="empty-title">还没有面试记录</div>
      <div class="empty-desc">开始第一场模拟面试后，这里会出现每场的得分与薄弱点。</div></div></div>`;
  }
  return `<div class="table-wrap"><table class="data">
    <thead><tr>
      <th>主题</th><th>目标岗位</th><th>轮次</th><th>得分</th><th>薄弱点</th><th>时间</th><th></th>
    </tr></thead>
    <tbody>
      ${rows
        .map((row) => {
          const tone = row.score >= 80 ? "ok" : row.score >= 70 ? "warn" : "danger";
          return `<tr>
            <td class="strong">${esc(row.topic)}</td>
            <td>${esc(row.targetRole)}</td>
            <td class="num">${row.turns}</td>
            <td>${badge(`${row.score} 分`, tone)}</td>
            <td>${row.weak?.length ? esc(row.weak[0]) : `<span style="color:var(--ink-4)">—</span>`}</td>
            <td style="white-space:nowrap">${esc(fmt.rel(row.at))}</td>
            <td><button class="btn btn-ghost btn-sm" data-report="${row.id}">查看报告</button></td>
          </tr>`;
        })
        .join("")}
    </tbody>
  </table></div>`;
}

function weakList(items) {
  if (!items.length) {
    return `<div class="empty"><div class="empty-title">暂无薄弱点</div>
      <div class="empty-desc">继续面试，系统会从追问与评分里沉淀薄弱知识点。</div></div>`;
  }
  return `<div class="list">${items
    .map((item) => {
      const tone = item.score >= 75 ? "ok" : item.score >= 60 ? "warn" : "danger";
      const arrow = item.trend === "up" ? "arrowUp" : item.trend === "down" ? "arrowDown" : "minus";
      return `<div class="list-item">
        <div class="icon-box ${tone === "danger" ? "" : "accent"}">${icon("target", 15)}</div>
        <div class="li-main">
          <div class="li-title">${esc(item.topic)}</div>
          <div class="li-meta">被追问 ${item.count} 次 · 平均 ${item.score} 分</div>
        </div>
        <div style="width:96px"><div class="bar ${tone}"><i style="width:${item.score}%"></i></div></div>
        <span style="color:var(--ink-3);display:flex">${icon(arrow, 14)}</span>
      </div>`;
    })
    .join("")}</div>`;
}

function suggestionCards(items) {
  return `<div class="grid grid-3">${items
    .map(
      (item) => `<div class="card"><div class="card-body stack" style="gap:10px">
        <div class="row">${badge(item.tag, item.tone === "accent" ? "accent" : item.tone)}</div>
        <div style="font-size:14px;font-weight:600;letter-spacing:-0.01em">${esc(item.title)}</div>
        <div class="hint">${esc(item.desc)}</div>
        <button class="btn btn-sm" data-start-topic="${esc(item.topic)}">${esc(item.action)}
          ${icon("chevron", 13)}</button>
      </div></div>`,
    )
    .join("")}</div>`;
}

async function loadDashboard() {
  if (store.settings.demo) return mockDashboard;
  try {
    const raw = await api.dashboard();
    // 后端 DTO 字段可能和演示数据结构不同：缺关键字段就退回演示数据，避免半张空页面
    if (!raw || !raw.kpis || !raw.trend) {
      return { ...mockDashboard, __partial: true };
    }
    return raw;
  } catch (error) {
    toast(`概览数据加载失败：${error.message}`, "warn");
    return mockDashboard;
  }
}

export default {
  id: "overview",
  title: "概览",

  async render() {
    const data = await loadDashboard();
    const k = data.kpis;
    const trend = data.trend;
    const review = data.review || mockDashboard.review;

    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">概览</h1>
          <p class="page-sub">
            最近 14 天的面试表现、能力维度与待补强的知识点。
            ${data.__partial ? "部分数据来自演示样本。" : ""}
          </p>
        </div>
        <div class="page-actions">
          <button class="btn" id="refresh">${icon("refresh", 15)}刷新</button>
          <button class="btn btn-primary" data-go="#/interview">${icon("play", 15)}开始面试</button>
        </div>
      </div>

      <div class="grid grid-4" style="margin-bottom:16px">
        ${statCard(k.sessions.label, fmt.num(k.sessions.value), "场", k.sessions.delta, "layers")}
        ${statCard(k.avgScore.label, fmt.num(k.avgScore.value, 1), k.avgScore.unit, k.avgScore.delta, "target")}
        ${statCard(k.weakTopics.label, fmt.num(k.weakTopics.value), k.weakTopics.unit, k.weakTopics.delta, "warn")}
        ${statCard(k.practice.label, fmt.num(k.practice.value), k.practice.unit, k.practice.delta, "clock")}
      </div>

      <div class="grid grid-2-1" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">得分趋势</div>
              <div class="card-desc">每场面试的加权总分，14 天窗口</div>
            </div>
            <div class="card-head-actions">
              ${badge(`均值 ${fmt.num(
                trend.points.reduce((a, b) => a + b, 0) / trend.points.length,
                1,
              )}`, "neutral")}
            </div>
          </div>
          <div class="card-body">${areaChart(trend.points, { labels: trend.labels })}</div>
          <div class="card-foot">
            <span>共 ${trend.points.length} 个采样点</span>
            <span class="spacer"></span>
            <span>波动区间 ${Math.min(...trend.points)} – ${Math.max(...trend.points)}</span>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">今日复习</div>
              <div class="card-desc">按遗忘曲线到期</div>
            </div>
          </div>
          <div class="card-body stack" style="justify-items:center;gap:14px">
            ${ring(review.dueToday / 12, { label: `今日到期 ${review.dueToday} 张` })}
            <div class="row" style="gap:18px;font-size:12px;color:var(--ink-3)">
              <span>${icon("bolt", 13)} 连续 ${review.streakDays} 天</span>
              <span>${icon("plus", 13)} 新增 ${review.newCards} 张</span>
            </div>
          </div>
          <div class="card-foot">
            <span>复习会更新长期记忆</span>
            <span class="spacer"></span>
            <button class="btn btn-sm btn-ghost" data-go="#/memory">查看记忆 ${icon("chevron", 12)}</button>
          </div>
        </div>
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">能力维度</div>
              <div class="card-desc">最近 8 场面试的拆项均分</div>
            </div>
          </div>
          <div class="card-body">${barList(data.dimensions)}</div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">待攻克知识点</div>
              <div class="card-desc">按被追问次数与得分排序</div>
            </div>
            <div class="card-head-actions">
              <button class="btn btn-sm btn-ghost" data-go="#/knowledge">知识图谱 ${icon("chevron", 12)}</button>
            </div>
          </div>
          <div class="card-body tight">${weakList(data.weakTopics)}</div>
        </div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-head">
          <div>
            <div class="card-title">最近面试</div>
            <div class="card-desc">点击可回看该场的复盘报告</div>
          </div>
          <div class="card-head-actions">
            <button class="btn btn-sm btn-ghost" data-go="#/report">全部报告 ${icon("chevron", 12)}</button>
          </div>
        </div>
        <div class="card-body tight">${recentTable(data.recent)}</div>
      </div>

      <div class="stack" style="gap:12px">
        <div class="row">
          <h2 style="font-size:15px">下一步建议</h2>
          <span class="hint">由最近的评分与薄弱点生成</span>
        </div>
        ${suggestionCards(data.suggestions)}
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

        root.querySelectorAll("[data-report]").forEach((node) => {
          node.addEventListener("click", () => {
            store.session.report = node.dataset.report;
            location.hash = "#/report";
          });
        });

        root.querySelectorAll("[data-start-topic]").forEach((node) => {
          node.addEventListener("click", () => {
            store.set({ topic: node.dataset.startTopic });
            location.hash = "#/interview";
          });
        });

        root.querySelector("#refresh")?.addEventListener("click", () => {
          location.reload();
        });
      },
    };
  },
};
