/**
 * 记忆与画像页。
 *
 * 展示三层记忆：结构化画像（简历解析）/ 长期摘要 / 片段检索。
 * 检索是真正走接口的（/api/memory/search），输入关键词即可。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { mockProfile, mockMemorySearch } from "../mock.js";
import { badge, barList, esc, fmt, icon, toast } from "../ui.js";

async function loadProfile() {
  if (store.settings.demo) return mockProfile;
  try {
    const raw = await api.profile();
    if (!raw) return mockProfile;
    return {
      ...mockProfile,
      ...raw,
      parsed: raw.parsed || mockProfile.parsed,
      memory: raw.memory || mockProfile.memory,
    };
  } catch (error) {
    toast(`画像加载失败：${error.message}`, "warn");
    return mockProfile;
  }
}

function skillBars(skills) {
  return barList(
    (skills || []).map((skill) => ({
      label: skill.name,
      value: skill.level,
      tone: skill.level >= 0.75 ? "ok" : skill.level >= 0.55 ? "" : "warn",
    })),
  );
}

export default {
  id: "memory",
  title: "记忆与画像",

  async render() {
    const profile = await loadProfile();
    const parsed = profile.parsed || {};
    const memory = profile.memory || {};

    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">记忆与画像</h1>
          <p class="page-sub">结构化画像 + 长期摘要 + 片段检索。面试官的工具就查这三层。</p>
        </div>
        <div class="page-actions">
          <button class="btn" id="m-refresh">${icon("refresh", 15)}刷新</button>
        </div>
      </div>

      <div class="grid grid-2-1" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head">
            <div class="icon-box accent">${icon("user", 15)}</div>
            <div>
              <div class="card-title">结构化画像</div>
              <div class="card-desc">来自简历解析与历次复盘合并</div>
            </div>
            <div class="card-head-actions">${badge(parsed.level || "未评估", "accent")}</div>
          </div>
          <div class="card-body stack" style="gap:18px">
            <dl class="kv">
              <dt>目标岗位</dt><dd>${esc(profile.targetRole || "—")}</dd>
              <dt>经验年限</dt><dd class="num">${esc(parsed.years ?? "—")} 年</dd>
            </dl>
            <div style="font-size:12.5px;line-height:1.8;color:var(--ink-2)">${esc(
              profile.resumeText || "还没有简历文本。",
            )}</div>
            <div>
              <div class="hint" style="margin-bottom:10px">技能水位</div>
              ${skillBars(parsed.skills)}
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="icon-box accent">${icon("memory", 15)}</div>
            <div><div class="card-title">长期摘要</div></div>
          </div>
          <div class="card-body" style="font-size:13px;line-height:1.85;color:var(--ink-2)">
            ${esc(memory.summary || "暂无长期摘要。")}
          </div>
          <div class="card-foot">
            <span>每场面试结束后自动合并</span>
          </div>
        </div>
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head"><div><div class="card-title">项目经历</div></div></div>
          <div class="card-body tight">
            ${
              (parsed.projects || []).length
                ? `<div class="list">${parsed.projects
                    .map(
                      (project) => `<div class="list-item">
                        <div class="icon-box">${icon("layers", 14)}</div>
                        <div class="li-main">
                          <div class="li-title">${esc(project.name)} · ${esc(project.role)}</div>
                          <div class="li-meta" style="white-space:normal">${esc(project.highlight)}</div>
                        </div>
                      </div>`,
                    )
                    .join("")}</div>`
                : `<div class="empty" style="padding:28px"><div class="empty-title">暂无项目</div></div>`
            }
          </div>
        </div>

        <div class="card">
          <div class="card-head"><div>
            <div class="card-title">最近沉淀</div>
            <div class="card-desc">最近几场面试写入的记忆片段</div>
          </div></div>
          <div class="card-body tight">
            <div class="list">${(memory.chunks || [])
              .map(
                (chunk) => `<div class="list-item">
                  <span class="dot ok" style="margin-top:6px"></span>
                  <div class="li-main"><div class="li-meta" style="white-space:normal;font-size:12.5px;line-height:1.7">${esc(
                    chunk,
                  )}</div></div>
                </div>`,
              )
              .join("")}</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div class="icon-box accent">${icon("search", 15)}</div>
          <div>
            <div class="card-title">记忆检索</div>
            <div class="card-desc">面试官的 retrieve_memory 工具背后就是这个查询</div>
          </div>
        </div>
        <div class="card-body stack" style="gap:14px">
          <div class="row">
            <input class="input" id="m-query" placeholder="输入知识点，例如 分布式锁 / AOF 重写" />
            <button class="btn btn-primary" id="m-search" style="flex:none">${icon("search", 14)}检索</button>
          </div>
          <div id="m-results" class="stack" style="gap:10px">
            <div class="hint">输入关键词后回车，或点击检索。</div>
          </div>
        </div>
      </div>
    `;

    return {
      html,
      after(root) {
        const input = root.querySelector("#m-query");
        const results = root.querySelector("#m-results");

        const paint = (items, keyword) => {
          if (!items.length) {
            results.innerHTML = `<div class="empty" style="padding:24px">
              <div class="empty-title">没有命中</div>
              <div class="empty-desc">换一个更具体的知识点再试。</div></div>`;
            return;
          }
          results.innerHTML = items
            .map(
              (item) => `<div class="card" style="box-shadow:none">
                <div class="card-body" style="padding:14px 16px">
                  <div class="row" style="margin-bottom:6px">
                    ${badge(item.source || "记忆片段", "neutral")}
                    <span class="hint" style="margin-left:auto">相关度 ${(item.score ?? 0).toFixed(2)}</span>
                  </div>
                  <div style="font-size:12.8px;line-height:1.75;color:var(--ink-2)">${esc(item.text)}</div>
                </div>
              </div>`,
            )
            .join("");
        };

        const run = async () => {
          const keyword = input.value.trim();
          if (!keyword) {
            toast("先输入关键词", "warn");
            return;
          }
          if (store.settings.demo) {
            paint(mockMemorySearch(keyword), keyword);
            return;
          }
          try {
            const raw = await api.memorySearch({ userId: store.settings.userId, keyword });
            const items = Array.isArray(raw) ? raw : raw?.items || [];
            paint(
              items.map((item, index) => ({
                id: item.id ?? index,
                score: item.score ?? 0,
                source: item.sourceType || "记忆",
                text: item.content || item.text || String(item),
              })),
              keyword,
            );
          } catch (error) {
            toast(`检索失败：${error.message}`, "err");
          }
        };

        root.querySelector("#m-search").addEventListener("click", run);
        input.addEventListener("keydown", (event) => {
          if (event.key === "Enter") run();
        });
        root.querySelector("#m-refresh").addEventListener("click", () => location.reload());
      },
    };
  },
};
