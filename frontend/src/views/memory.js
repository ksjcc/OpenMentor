/**
 * 记忆与画像页。
 *
 * 三层数据都来自后端，缺哪层就展示空状态，不拿样例数据凑：
 *   1. 结构化画像：GET /api/profile（能力水位、目标岗位、简历文本）
 *   2. 长期摘要：GET /api/memory/summary-md
 *   3. 片段：GET /api/memory/recent（最近沉淀）+ /api/memory/search（按关键词检索）
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { badge, barList, esc, fmt, icon, toast } from "../ui.js";

/* ------------------------------ 数据 ------------------------------ */

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function loadAll() {
  const userId = store.settings.userId;
  const [profile, profileJson, summary, chunks] = await Promise.all([
    api.profile(userId).catch(() => null),
    api.memoryProfileJson(userId).catch(() => null),
    api.memorySummary(userId, "overall").catch(() => null),
    api.memoryRecent(userId, 5).catch(() => []),
  ]);
  return {
    profile,
    parsed: parseJson(profileJson) || {},
    summary: typeof summary === "string" ? summary : "",
    chunks: Array.isArray(chunks) ? chunks : [],
  };
}

function abilityMapOf(profile, parsed) {
  const fromProfile = parseJson(profile?.abilityMap);
  const map = fromProfile && typeof fromProfile === "object" ? fromProfile : parsed?.abilityMap;
  if (!map || typeof map !== "object") return [];
  return Object.entries(map).map(([name, value]) => ({
    label: name,
    value: Math.max(0, Math.min(1, Number(value) / 100)),
    tone: Number(value) >= 75 ? "ok" : Number(value) >= 55 ? "" : "warn",
  }));
}

/* -------------------------------- 渲染 -------------------------------- */

export default {
  id: "memory",
  title: "记忆与画像",

  async render() {
    let data = null;
    let failure = null;
    try {
      data = await loadAll();
    } catch (error) {
      failure = error;
    }

    const profile = data?.profile || {};
    const parsed = data?.parsed || {};
    const skills = abilityMapOf(profile, parsed);
    const projects = parsed.projects || [];
    const strengths = parsed.strengths || [];
    const weaknesses = parsed.weaknesses || [];
    const resumeText = profile.resumeText || "";

    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">记忆与画像</h1>
          <p class="page-sub">结构化画像 + 长期摘要 + 片段检索。面试官的工具就查这三层。</p>
        </div>
        <div class="page-actions">
          <button class="btn" id="m-refresh">${icon("refresh", 15)}刷新</button>
          <button class="btn btn-primary" data-go="#/resume">${icon("resume", 15)}简历库</button>
        </div>
      </div>

      ${
        failure
          ? `<div class="card"><div class="card-body"><div class="empty">
              <div class="empty-title">画像加载失败</div>
              <div class="empty-desc">${esc(failure.message)}</div>
            </div></div></div>`
          : ""
      }

      <div class="grid grid-2-1" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head">
            <div class="icon-box accent">${icon("user", 15)}</div>
            <div>
              <div class="card-title">结构化画像</div>
              <div class="card-desc">来自简历解析与历次复盘合并</div>
            </div>
            <div class="card-head-actions">${badge(parsed.experienceLevel || profile.experienceLevel || "未评估", "accent")}</div>
          </div>
          <div class="card-body stack" style="gap:18px">
            <dl class="kv">
              <dt>目标岗位</dt><dd>${esc(profile.targetRole || parsed.targetRole || "—")}</dd>
              <dt>经验水平</dt><dd>${esc(profile.experienceLevel || parsed.experienceLevel || "—")}</dd>
              <dt>技术栈</dt><dd>${esc((parsed.techStack || []).join(" / ") || "—")}</dd>
            </dl>
            ${
              skills.length
                ? `<div>
                    <div class="hint" style="margin-bottom:10px">技能水位</div>
                    ${barList(skills)}
                  </div>`
                : `<div class="empty" style="padding:24px">
                    <div class="empty-title">还没有能力画像</div>
                    <div class="empty-desc">上传简历并解析后，这里会出现技能水位。</div>
                    <button class="btn btn-sm" data-go="#/resume">去简历库</button>
                  </div>`
            }
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="icon-box accent">${icon("memory", 15)}</div>
            <div><div class="card-title">长期摘要</div></div>
          </div>
          <div class="card-body" style="font-size:13px;line-height:1.85;color:var(--ink-2);white-space:pre-wrap">
            ${esc(data?.summary || "还没有长期摘要。完成一场面试后会自动生成。")}
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
              projects.length
                ? `<div class="list">${projects
                    .map(
                      (project) => `<div class="list-item">
                        <div class="icon-box">${icon("layers", 14)}</div>
                        <div class="li-main">
                          <div class="li-title">${esc(project.name || "项目")}${
                            project.role ? ` · ${esc(project.role)}` : ""
                          }</div>
                          <div class="li-meta" style="white-space:normal">${esc(
                            project.description ||
                              (project.highlights || []).join("；") ||
                              "暂无描述",
                          )}</div>
                        </div>
                      </div>`,
                    )
                    .join("")}</div>`
                : `<div class="empty" style="padding:28px"><div class="empty-title">暂无项目</div>
                     <div class="empty-desc">简历解析后会在这里列出项目经历。</div></div>`
            }
          </div>
        </div>

        <div class="card">
          <div class="card-head"><div>
            <div class="card-title">最近沉淀</div>
            <div class="card-desc">最近几场面试写入的记忆片段</div>
          </div></div>
          <div class="card-body tight">
            ${
              (data?.chunks || []).length
                ? `<div class="list">${data.chunks
                    .map(
                      (chunk) => `<div class="list-item">
                        <span class="dot ok" style="margin-top:6px"></span>
                        <div class="li-main"><div class="li-meta" style="white-space:normal;font-size:12.5px;line-height:1.7">${esc(
                          chunk.content,
                        )}</div></div>
                      </div>`,
                    )
                    .join("")}</div>`
                : `<div class="empty" style="padding:28px"><div class="empty-title">还没有记忆片段</div>
                     <div class="empty-desc">面试复盘与简历解析都会沉淀片段。</div></div>`
            }
          </div>
        </div>
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        <div class="card">
          <div class="card-head"><div><div class="card-title">优势</div></div></div>
          <div class="card-body" style="font-size:13px;line-height:1.85;color:var(--ink-2)">
            ${strengths.map((item) => `· ${esc(item.name || item)}`).join("<br>") || "—"}
          </div>
        </div>
        <div class="card">
          <div class="card-head"><div><div class="card-title">薄弱点</div></div></div>
          <div class="card-body" style="font-size:13px;line-height:1.85;color:var(--ink-2)">
            ${weaknesses.map((item) => `· ${esc(item.name || item)}`).join("<br>") || "—"}
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div class="icon-box accent">${icon("search", 15)}</div>
          <div>
            <div class="card-title">记忆检索</div>
            <div class="card-desc">面试官的 retrieve_material 工具背后就是这个查询</div>
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

      <details class="card" style="margin-top:16px">
        <summary style="cursor:pointer;list-style:none;padding:16px 18px">
          <div class="row">
            <div class="icon-box accent">${icon("user", 15)}</div>
            <div>
              <div class="card-title">用一段文本重建画像</div>
              <div class="card-desc">不想上传文件时，可直接粘贴简历文本解析</div>
            </div>
            <span class="spacer"></span>
            <span class="hint">展开</span>
          </div>
        </summary>
        <div class="card-body stack" style="gap:14px;border-top:1px solid var(--line)">
          <div class="field">
            <label class="field-label" for="m-role">目标岗位</label>
            <input class="input" id="m-role" value="${esc(profile.targetRole || "")}" />
          </div>
          <div class="field">
            <label class="field-label" for="m-resume">简历 / 项目经历</label>
            <textarea class="textarea" id="m-resume" placeholder="贴一段简历或项目描述，解析后会更新技能水位与薄弱点">${esc(
              resumeText,
            )}</textarea>
          </div>
          <div class="row">
            <button class="btn btn-primary" id="m-init">${icon("refresh", 14)}解析并更新</button>
            <span class="hint" id="m-init-hint">解析会调用一次模型，约 5–15 秒。</span>
          </div>
        </div>
      </details>
    `;

    return {
      html,
      after(root) {
        root.querySelectorAll("[data-go]").forEach((node) => {
          node.addEventListener("click", () => {
            location.hash = node.dataset.go;
          });
        });

        const input = root.querySelector("#m-query");
        const results = root.querySelector("#m-results");

        const paint = (items) => {
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
                    <span class="hint" style="margin-left:auto">${esc(fmt.rel(item.at))}</span>
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
          try {
            const raw = await api.memorySearch({ userId: store.settings.userId, keyword });
            const items = Array.isArray(raw) ? raw : raw?.items || [];
            paint(
              items.map((item) => ({
                id: item.id,
                source: item.sourceType || "记忆",
                text: item.content || item.text || String(item),
                at: item.createdAt || null,
              })),
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

        // ---- 用文本重建画像 ----
        const initButton = root.querySelector("#m-init");
        const initHint = root.querySelector("#m-init-hint");
        initButton?.addEventListener("click", async () => {
          const targetRole = root.querySelector("#m-role").value.trim();
          const resumeTextValue = root.querySelector("#m-resume").value.trim();
          if (!resumeTextValue) {
            toast("简历文本不能为空", "warn");
            return;
          }
          if (targetRole) {
            store.set({ targetRole });
          }
          initButton.disabled = true;
          initHint.textContent = "解析中，请稍候…";
          try {
            await api.profileInit({
              userId: store.settings.userId,
              targetRole,
              resumeText: resumeTextValue,
            });
            initHint.textContent = "已更新，刷新页面查看新画像。";
            toast("画像已更新", "ok");
            setTimeout(() => location.reload(), 800);
          } catch (error) {
            initHint.textContent = `解析失败：${error.message}`;
            toast(`解析失败：${error.message}`, "err");
          } finally {
            initButton.disabled = false;
          }
        });
      },
    };
  },
};
