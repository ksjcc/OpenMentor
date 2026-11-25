/**
 * 概览页。
 *
 * 全部数据来自 GET /api/dashboard（真实会话、画像、闪卡）。
 * 后端没数据时展示空状态，不再回落样例数据。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { areaChart, badge, barList, esc, fmt, icon } from "../ui.js";

function statCard(label, value, unit, iconName, hint) {
  return `<div class="stat">
    <div class="stat-label">${icon(iconName, 14)}<span>${esc(label)}</span></div>
    <div class="stat-value">${esc(value)}${unit ? `<span class="stat-unit">${esc(unit)}</span>` : ""}</div>
    <div class="stat-delta flat"><span style="color:var(--ink-4)">${esc(hint)}</span></div>
  </div>`;
}

function recentTable(rows) {
  if (!rows.length) {
    return `<div class="card-body"><div class="empty"><div class="empty-title">还没有面试记录</div>
      <div class="empty-desc">开始第一场模拟面试后，这里会出现每场的得分与薄弱点。</div>
      <button class="btn btn-primary" data-go="#/interview">开始面试</button></div></div>`;
  }
  return `<div class="table-wrap"><table class="data">
    <thead><tr>
      <th>主题</th><th>目标岗位</th><th>轮次</th><th>得分</th><th>薄弱点</th><th>时间</th><th></th>
    </tr></thead>
    <tbody>
      ${rows
        .map((row) => {
          const score = row.score;
          const tone = score == null ? "neutral" : score >= 80 ? "ok" : score >= 70 ? "warn" : "danger";
          return `<tr>
            <td class="strong">${esc(row.topic || "通用面试")}</td>
            <td>${esc(row.targetRole || "—")}</td>
            <td class="num">${row.turns ?? 0}</td>
            <td>${score == null ? badge("未评分", "neutral") : badge(`${score} 分`, tone)}</td>
            <td>${
              row.topWeakness
                ? esc(row.topWeakness)
                : `<span style="color:var(--ink-4)">—</span>`
            }</td>
            <td style="white-space:nowrap">${esc(fmt.rel(row.endedAt || row.createdAt))}</td>
            <td><button class="btn btn-ghost btn-sm" data-report="${row.sessionId}">查看报告</button></td>
          </tr>`;
        })
        .join("")}
    </tbody>
  </table></div>`;
}

function weaknessList(items) {
  if (!items.length) {
    return `<div class="empty"><div class="empty-title">暂无薄弱点</div>
      <div class="empty-desc">完成几场面试后，系统会从追问与评分里沉淀薄弱知识点。</div></div>`;
  }
  const max = Math.max(...items.map((item) => item.count || 1), 1);
  return `<div class="list">${items
    .map(
      (item) => `<div class="list-item">
        <div class="icon-box ${item.count >= 3 ? "" : "accent"}">${icon("target", 15)}</div>
        <div class="li-main">
          <div class="li-title">${esc(item.name)}</div>
          <div class="li-meta">被追问 ${item.count} 次</div>
        </div>
        <div style="width:96px"><div class="bar ${item.count >= 3 ? "danger" : "warn"}"><i
          style="width:${Math.round((item.count / max) * 100)}%"></i></div></div>
      </div>`,
    )
    .join("")}</div>`;
}

function suggestionCards(items, weakTopics) {
  if (!items.length) {
    return `<div class="card"><div class="card-body"><div class="empty" style="padding:24px">
      <div class="empty-title">还没有建议</div>
      <div class="empty-desc">完成一场面试并生成复盘后，这里会给出下一步的训练方向。</div>
      <button class="btn btn-primary" data-go="#/interview">开始面试</button>
    </div></div></div>`;
  }
  const topic = weakTopics[0] || "";
  return `<div class="grid grid-3">${items
    .map(
      (text, index) => `<div class="card"><div class="card-body stack" style="gap:10px">
        <div class="row">${badge(index === 0 ? "优先" : "建议", index === 0 ? "warn" : "accent")}</div>
        <div class="hint" style="line-height:1.75">${esc(text)}</div>
        <button class="btn btn-sm" data-start-topic="${esc(topic || "")}">
          去练习 ${icon("chevron", 13)}
        </button>
      </div></div>`,
    )
    .join("")}</div>`;
}

export default {
  id: "overview",
  title: "概览",

  async render() {
    let data = null;
    let failure = null;
    try {
      data = await api.dashboard(store.settings.userId);
    } catch (error) {
      failure = error;
    }

    if (!data) {
      return {
        html: `<div class="page-head">
            <div>
              <h1 class="page-title">概览</h1>
              <p class="page-sub">你的面试表现与待补强的知识点。</p>
            </div>
          </div>
          <div class="card"><div class="card-body"><div class="empty">
            <div class="empty-title">数据加载失败</div>
            <div class="empty-desc">${esc(failure?.message || "请稍后重试")}</div>
            <button class="btn" id="refresh">${icon("refresh", 15)}重新加载</button>
          </div></div></div>`,
        after(root) {
          root.querySelector("#refresh")?.addEventListener("click", () => location.reload());
        },
      };
    }

    const skills = (data.skills || []).map((item) => ({
      label: item.skill,
      value: Math.max(0, Math.min(1, (item.score ?? 0) / 100)),
      tone: (item.score ?? 0) >= 75 ? "ok" : (item.score ?? 0) >= 55 ? "" : "warn",
    }));
    const weaknesses = data.weaknesses || [];
    const recent = data.recentInterviews || [];
    const trendPoints = data.scoreTrend || [];
    const trendLabels = data.trendLabels || [];
    const suggestions = data.aiSuggestions || [];
    const weakTopicNames = data.learningRecommendation?.weakTopics || weaknesses.map((item) => item.name);
    const dueToday = data.todayReviewCount ?? 0;

    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">概览</h1>
          <p class="page-sub">面试表现、能力维度与待补强的知识点。</p>
        </div>
        <div class="page-actions">
          <button class="btn" id="refresh">${icon("refresh", 15)}刷新</button>
          <button class="btn btn-primary" data-go="#/interview">${icon("play", 15)}开始面试</button>
        </div>
      </div>

      <div class="grid grid-4" style="margin-bottom:16px">
        ${statCard("累计面试", fmt.num(data.totalSessions ?? 0), "场", "layers", "含进行中的场次")}
        ${statCard(
          "平均得分",
          data.averageScore == null ? "—" : fmt.num(data.averageScore),
          data.averageScore == null ? "" : "分",
          "target",
          "只统计已评分的场次",
        )}
        ${statCard("今日待复习", fmt.num(dueToday), "张", "clock", dueToday ? "按遗忘曲线到期" : "今天没有到期卡片")}
        ${statCard("待攻克薄弱点", fmt.num(weaknesses.length), "个", "warn", "来自历次复盘")}
      </div>

      <div class="grid grid-2-1" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">得分趋势</div>
              <div class="card-desc">每场面试的加权总分</div>
            </div>
            <div class="card-head-actions">
              ${
                trendPoints.length
                  ? badge(
                      `均值 ${fmt.num(trendPoints.reduce((a, b) => a + b, 0) / trendPoints.length, 1)}`,
                      "neutral",
                    )
                  : badge("暂无评分", "neutral")
              }
            </div>
          </div>
          <div class="card-body">
            ${
              trendPoints.length >= 2
                ? areaChart(trendPoints, { labels: trendLabels })
                : `<div class="empty" style="padding:28px">
                    <div class="empty-title">${trendPoints.length ? "再面一场就有趋势了" : "还没有评分数据"}</div>
                    <div class="empty-desc">每场面试回答后的评分会记录在这里。</div>
                  </div>`
            }
          </div>
          ${
            trendPoints.length
              ? `<div class="card-foot">
                  <span>共 ${trendPoints.length} 个采样点</span>
                  <span class="spacer"></span>
                  <span>区间 ${Math.min(...trendPoints)} – ${Math.max(...trendPoints)}</span>
                </div>`
              : ""
          }
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">今日复习</div>
              <div class="card-desc">按遗忘曲线到期</div>
            </div>
          </div>
          <div class="card-body stack" style="justify-items:center;gap:12px;text-align:center">
            <div class="stat-value" style="font-size:38px">${fmt.num(dueToday)}</div>
            <div class="hint">${dueToday ? "张闪卡等待复习" : "今天没有到期卡片"}</div>
            <button class="btn btn-primary btn-sm" data-go="#/review">
              ${icon("bolt", 13)}${dueToday ? "开始复习" : "看看复习计划"}
            </button>
          </div>
          <div class="card-foot">
            <span>复习会更新长期记忆</span>
          </div>
        </div>
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">能力维度</div>
              <div class="card-desc">来自简历解析与历次复盘合并</div>
            </div>
          </div>
          <div class="card-body">
            ${
              skills.length
                ? barList(skills)
                : `<div class="empty" style="padding:26px">
                    <div class="empty-title">还没有能力画像</div>
                    <div class="empty-desc">在简历库上传一份简历，解析后这里会出现技能水位。</div>
                    <button class="btn btn-sm" data-go="#/resume">去简历库</button>
                  </div>`
            }
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">待攻克知识点</div>
              <div class="card-desc">按被追问次数排序</div>
            </div>
            <div class="card-head-actions">
              <button class="btn btn-sm btn-ghost" data-go="#/knowledge">知识图谱 ${icon("chevron", 12)}</button>
            </div>
          </div>
          <div class="card-body tight">${weaknessList(weaknesses)}</div>
        </div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-head">
          <div>
            <div class="card-title">最近面试</div>
            <div class="card-desc">点击可回看该场的复盘报告</div>
          </div>
          <div class="card-head-actions">
            <button class="btn btn-sm btn-ghost" data-go="#/interview">再面一场 ${icon("chevron", 12)}</button>
          </div>
        </div>
        <div class="card-body tight">${recentTable(recent)}</div>
      </div>

      <div class="stack" style="gap:12px">
        <div class="row">
          <h2 style="font-size:15px">下一步建议</h2>
          <span class="hint">由最近的评分与薄弱点生成</span>
        </div>
        ${suggestionCards(suggestions, weakTopicNames)}
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
            store.session.sessionId = node.dataset.report;
            location.hash = "#/report";
          });
        });

        root.querySelectorAll("[data-start-topic]").forEach((node) => {
          node.addEventListener("click", () => {
            const topic = node.dataset.startTopic;
            if (topic) store.set({ topic });
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
