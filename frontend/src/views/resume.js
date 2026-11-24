/**
 * 简历库页。
 *
 * 一份简历 = 一段可注入面试上下文的文本 + 模型解析出的结构化画像。
 * 支持上传 PDF/DOCX/TXT 或直接粘贴文本；创建面试时在这里选中的简历会被
 * 面试官当作候选人背景。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { badge, barList, confirmDialog, esc, fmt, icon, toast } from "../ui.js";

const state = {
  resumes: [],
  selectedId: null,
  detail: null,
  editing: null,
  loaded: false,
};

/* ------------------------------ 数据 ------------------------------ */

async function loadList() {
  state.resumes = (await api.resumeList(store.settings.userId)) || [];
  state.loaded = true;
  if (state.selectedId == null || !state.resumes.some((item) => item.id === state.selectedId)) {
    const fallback = state.resumes.find((item) => item.isDefault === 1) || state.resumes[0];
    state.selectedId = fallback ? fallback.id : null;
  }
  if (state.detail && state.detail.id !== state.selectedId) {
    state.detail = null;
  }
  return state.resumes;
}

function selected() {
  return state.resumes.find((item) => item.id === state.selectedId) || null;
}

/** 预览优先用详情接口拿到的完整数据（列表接口不返回全文）。 */
function detailOf(resume) {
  if (state.detail && resume && state.detail.id === resume.id) return state.detail;
  return resume;
}

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function skillsOf(parsed) {
  const map = parsed?.abilityMap;
  if (!map || typeof map !== "object") return [];
  return Object.entries(map).map(([name, value]) => ({
    label: name,
    value: Math.max(0, Math.min(1, Number(value) / 100)),
    tone: Number(value) >= 75 ? "ok" : Number(value) >= 55 ? "" : "warn",
  }));
}

function snippet(text, max = 120) {
  const clean = String(text || "").replace(/\s+/g, " ").trim();
  return clean.length > max ? `${clean.slice(0, max)}…` : clean;
}

/* ------------------------------ 渲染 ------------------------------ */

function cardHtml(resume) {
  const isDefault = resume.isDefault === 1;
  const active = resume.id === state.selectedId;
  return `<div class="card" style="box-shadow:none;border-color:${
    active ? "var(--accent)" : "var(--line)"
  }" data-resume="${resume.id}">
    <div class="card-body stack" style="gap:10px;padding:16px 18px">
      <div class="row" style="gap:8px">
        <div class="li-title" style="font-size:14px">${esc(resume.title)}</div>
        ${isDefault ? badge("默认", "accent") : ""}
        ${resume.hasParsed ? badge("已解析", "ok") : badge("未解析", "warn")}
        <span class="spacer"></span>
        <span class="hint">${esc(fmt.rel(resume.updatedAt))}</span>
      </div>
      <div class="hint" style="line-height:1.7">${esc(snippet(resume.content))}</div>
      <div class="row" style="gap:8px;flex-wrap:wrap">
        ${resume.targetRole ? `<span class="hint">目标岗位：${esc(resume.targetRole)}</span>` : ""}
        ${resume.fileType ? `<span class="hint">来源：${esc(resume.fileType)}</span>` : ""}
        <span class="spacer"></span>
        <button class="btn btn-ghost btn-sm" data-act="preview" data-id="${resume.id}">查看</button>
        ${
          isDefault
            ? ""
            : `<button class="btn btn-ghost btn-sm" data-act="default" data-id="${resume.id}">设为默认</button>`
        }
        <button class="btn btn-ghost btn-sm" data-act="parse" data-id="${resume.id}">重新解析</button>
        <button class="btn btn-ghost btn-sm" data-act="edit" data-id="${resume.id}">编辑</button>
        <button class="btn btn-ghost btn-sm" data-act="delete" data-id="${resume.id}"
          style="color:var(--danger)">删除</button>
      </div>
    </div>
  </div>`;
}

function listHtml() {
  if (!state.resumes.length) {
    return `<div class="empty" style="padding:32px">
      <div class="empty-title">还没有简历</div>
      <div class="empty-desc">上传一份 PDF / DOCX / TXT，或直接粘贴简历文本。创建面试时就能选中它。</div>
    </div>`;
  }
  return `<div class="stack" style="gap:10px">${state.resumes.map(cardHtml).join("")}</div>`;
}

function previewHtml() {
  const resume = selected();
  if (!resume) {
    return `<div class="empty" style="padding:28px">
      <div class="empty-title">选择一份简历</div>
      <div class="empty-desc">左侧点击「查看」，这里会显示解析出来的技能水位、项目与薄弱点。</div>
    </div>`;
  }
  const full = detailOf(resume);
  const parsed = parseJson(full.parsedJson);
  const skills = skillsOf(parsed);
  return `<div class="stack" style="gap:14px">
    <div>
      <div class="row" style="gap:8px">
        <div class="li-title" style="font-size:14px">${esc(resume.title)}</div>
        ${resume.isDefault === 1 ? badge("默认简历", "accent") : ""}
      </div>
      <dl class="kv" style="margin-top:10px">
        <dt>目标岗位</dt><dd>${esc(resume.targetRole || parsed?.targetRole || "—")}</dd>
        <dt>经验水平</dt><dd>${esc(parsed?.experienceLevel || "—")}</dd>
        <dt>技术栈</dt><dd>${esc((parsed?.techStack || []).join(" / ") || "—")}</dd>
        <dt>更新于</dt><dd>${esc(fmt.rel(resume.updatedAt))}</dd>
      </dl>
    </div>

    ${
      skills.length
        ? `<div><div class="hint" style="margin-bottom:10px">技能水位（模型解析）</div>${barList(skills)}</div>`
        : `<div class="hint">还没有解析结果，点「重新解析」让模型读一遍简历。</div>`
    }

    ${
      (parsed?.projects || []).length
        ? `<div>
            <div class="hint" style="margin-bottom:8px">项目经历</div>
            <div class="list" style="border:1px solid var(--line);border-radius:10px;overflow:hidden">
              ${parsed.projects
                .map(
                  (project) => `<div class="list-item">
                    <div class="icon-box">${icon("layers", 14)}</div>
                    <div class="li-main">
                      <div class="li-title">${esc(project.name || "项目")}</div>
                      <div class="li-meta" style="white-space:normal">${esc(
                        snippet([project.description, ...(project.highlights || [])].filter(Boolean).join("；"), 160),
                      )}</div>
                    </div>
                  </div>`,
                )
                .join("")}
            </div>
          </div>`
        : ""
    }

    ${
      (parsed?.strengths || []).length || (parsed?.weaknesses || []).length
        ? `<div class="grid grid-2" style="gap:12px">
            <div>
              <div class="hint" style="margin-bottom:6px">优势</div>
              <div style="font-size:12.5px;line-height:1.8;color:var(--ink-2)">
                ${
                  (parsed?.strengths || [])
                    .map((item) => `- ${esc(item.name || item)}`)
                    .join("<br>") || "—"
                }
              </div>
            </div>
            <div>
              <div class="hint" style="margin-bottom:6px">薄弱点</div>
              <div style="font-size:12.5px;line-height:1.8;color:var(--ink-2)">
                ${
                  (parsed?.weaknesses || [])
                    .map((item) => `- ${esc(item.name || item)}`)
                    .join("<br>") || "—"
                }
              </div>
            </div>
          </div>`
        : ""
    }

    <div>
      <div class="hint" style="margin-bottom:6px">简历正文（节选）</div>
      <div style="font-size:12.5px;line-height:1.75;color:var(--ink-2);max-height:180px;overflow:auto">
        ${esc(snippet(full.content, 520))}
      </div>
    </div>
  </div>`;
}

/* ------------------------------ 交互 ------------------------------ */

function paint(root) {
  const list = root.querySelector("#resume-list");
  const preview = root.querySelector("#resume-preview");
  if (list) list.innerHTML = listHtml();
  if (preview) preview.innerHTML = previewHtml();
}

function fillForm(root, resume) {
  const titleInput = root.querySelector("#r-title");
  const roleInput = root.querySelector("#r-role");
  const textArea = root.querySelector("#r-content");
  if (!titleInput || !roleInput || !textArea) return;
  titleInput.value = resume?.title || "";
  roleInput.value = resume?.targetRole || "";
  textArea.value = resume?.content || "";
  const hint = root.querySelector("#r-form-hint");
  if (hint) {
    hint.textContent = resume ? `正在编辑：${resume.title}` : "上传文件时会自动抽取文本，也可以直接粘贴";
  }
  state.editing = resume ? resume.id : null;
  const submit = root.querySelector("#r-save-text");
  if (submit) submit.textContent = resume ? "保存修改" : "保存并解析";
}

async function refresh(root, { keepForm = false } = {}) {
  await loadList();
  paint(root);
  if (!keepForm) fillForm(root, selected());
}

async function handleAction(root, act, id) {
  const resume = state.resumes.find((item) => item.id === id);
  if (!resume) return;

  if (act === "preview") {
    state.selectedId = id;
    try {
      state.detail = await api.resumeGet(id, store.settings.userId);
    } catch (error) {
      state.detail = null;
      toast(`加载简历详情失败：${error.message}`, "err");
    }
    paint(root);
    return;
  }

  if (act === "default") {
    try {
      await api.resumeSetDefault({ resumeId: id, userId: store.settings.userId });
      toast("已设为默认简历", "ok");
      await refresh(root);
    } catch (error) {
      toast(`设置失败：${error.message}`, "err");
    }
    return;
  }

  if (act === "parse") {
    toast("正在解析简历，最长约 60 秒…", "neutral");
    try {
      await api.resumeParse({ resumeId: id, userId: store.settings.userId });
      toast("解析完成", "ok");
      await refresh(root, { keepForm: true });
    } catch (error) {
      toast(`解析失败：${error.message}`, "err");
    }
    return;
  }

  if (act === "edit") {
    state.selectedId = id;
    let full = resume;
    try {
      full = (await api.resumeGet(id, store.settings.userId)) || resume;
      state.detail = full;
    } catch (error) {
      toast(`加载简历详情失败：${error.message}`, "err");
    }
    fillForm(root, full);
    paint(root);
    root.querySelector("#r-title")?.focus();
    return;
  }

  if (act === "delete") {
    const ok = await confirmDialog({
      title: "删除这份简历？",
      body: `「${resume.title}」删除后不可恢复，已在用它的面试记录不受影响。`,
      confirmText: "删除",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.resumeDelete({ resumeId: id, userId: store.settings.userId });
      toast("已删除", "ok");
      state.selectedId = null;
      await refresh(root);
    } catch (error) {
      toast(`删除失败：${error.message}`, "err");
    }
  }
}

function bind(root) {
  root.querySelector("#resume-list")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-act]");
    if (!button) return;
    handleAction(root, button.dataset.act, Number(button.dataset.id));
  });

  // 上传文件
  const fileInput = root.querySelector("#r-file");
  const uploadButton = root.querySelector("#r-upload");
  uploadButton?.addEventListener("click", () => fileInput?.click());
  fileInput?.addEventListener("change", async () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    const title = root.querySelector("#r-title").value.trim() || file.name.replace(/\.[^.]+$/, "");
    const targetRole = root.querySelector("#r-role").value.trim();
    uploadButton.disabled = true;
    uploadButton.innerHTML = "上传解析中…";
    try {
      const created = await api.resumeUpload({
        userId: store.settings.userId,
        file,
        title,
        targetRole,
        parse: true,
      });
      state.selectedId = created?.id ?? null;
      toast("上传成功，已完成解析", "ok");
      await refresh(root);
    } catch (error) {
      toast(`上传失败：${error.message}`, "err");
    } finally {
      uploadButton.disabled = false;
      uploadButton.innerHTML = `${icon("upload", 14)}上传文件`;
      fileInput.value = "";
    }
  });

  // 粘贴文本保存
  root.querySelector("#r-save-text")?.addEventListener("click", async () => {
    const title = root.querySelector("#r-title").value.trim();
    const targetRole = root.querySelector("#r-role").value.trim();
    const content = root.querySelector("#r-content").value.trim();
    if (!content || content.length < 20) {
      toast("简历内容太短，至少写 20 个字", "warn");
      return;
    }
    const button = root.querySelector("#r-save-text");
    button.disabled = true;
    try {
      if (state.editing) {
        await api.resumeUpdate({
          resumeId: state.editing,
          userId: store.settings.userId,
          title,
          content,
          targetRole,
        });
        await api.resumeParse({ resumeId: state.editing, userId: store.settings.userId });
        toast("已保存并重新解析", "ok");
      } else {
        const created = await api.resumeCreate({
          userId: store.settings.userId,
          title,
          content,
          targetRole,
          parse: true,
        });
        state.selectedId = created?.id ?? null;
        toast("已保存并解析", "ok");
      }
      await refresh(root);
    } catch (error) {
      toast(`保存失败：${error.message}`, "err");
    } finally {
      button.disabled = false;
    }
  });

  root.querySelector("#r-new")?.addEventListener("click", () => {
    state.editing = null;
    state.selectedId = null;
    fillForm(root, null);
    paint(root);
  });
}

export default {
  id: "resume",
  title: "简历库",

  async render() {
    const loadFailed = await loadList()
      .then(() => null)
      .catch((error) => error);

    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">简历库</h1>
          <p class="page-sub">创建面试时选择这里的简历，面试官会围绕它追问项目细节。</p>
        </div>
        <div class="page-actions">
          <button class="btn" id="r-new">${icon("plus", 15)}新建（粘贴文本）</button>
          <button class="btn btn-primary" id="r-upload">${icon("upload", 15)}上传文件</button>
          <input type="file" id="r-file" accept=".pdf,.docx,.txt,.md" hidden />
        </div>
      </div>

      ${
        loadFailed
          ? `<div class="card"><div class="card-body"><div class="empty">
              <div class="empty-title">简历列表加载失败</div>
              <div class="empty-desc">${esc(loadFailed.message)}</div>
            </div></div></div>`
          : ""
      }

      <div class="grid grid-2-1">
        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">我的简历</div>
              <div class="card-desc">共 ${state.resumes.length} 份 · 标为默认的那份会在创建面试时自动选中</div>
            </div>
          </div>
          <div class="card-body tight" id="resume-list">${listHtml()}</div>
        </div>

        <div class="stack">
          <div class="card">
            <div class="card-head">
              <div class="icon-box accent">${icon("resume", 15)}</div>
              <div>
                <div class="card-title">简历详情</div>
                <div class="card-desc">解析结果会同步进记忆与画像</div>
              </div>
            </div>
            <div class="card-body" id="resume-preview">${previewHtml()}</div>
          </div>

          <div class="card">
            <div class="card-head">
              <div class="icon-box">${icon("plus", 15)}</div>
              <div>
                <div class="card-title">新增 / 编辑</div>
                <div class="card-desc" id="r-form-hint">上传文件时会自动抽取文本，也可以直接粘贴</div>
              </div>
            </div>
            <div class="card-body stack" style="gap:12px">
              <div class="field">
                <label class="field-label" for="r-title">简历名称</label>
                <input class="input" id="r-title" placeholder="例如 Java 后端 · 2026 春招" />
              </div>
              <div class="field">
                <label class="field-label" for="r-role">目标岗位</label>
                <input class="input" id="r-role" placeholder="例如 Java 后端开发" />
              </div>
              <div class="field">
                <label class="field-label" for="r-content">简历正文</label>
                <textarea class="textarea" id="r-content" style="min-height:140px"
                  placeholder="粘贴简历文本；上传 PDF/DOCX 时这一栏可留空"></textarea>
                <div class="field-hint">支持 PDF / DOCX / TXT，扫描件（图片 PDF）读不出文字。</div>
              </div>
              <button class="btn btn-primary btn-block" id="r-save-text">保存并解析</button>
            </div>
          </div>
        </div>
      </div>
    `;

    return {
      html,
      after(root) {
        bind(root);
        fillForm(root, selected());
      },
    };
  },
};
