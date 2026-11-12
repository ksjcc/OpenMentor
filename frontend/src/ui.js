/**
 * UI 基础件：图标、格式化、提示、对话框、轻量图表。
 * 不引入任何第三方库，保证这套前端零构建即可运行。
 */

/* ------------------------------- dom ---------------------------------- */

export function esc(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value == null || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "text") node.textContent = value;
    else if (key === "html") node.innerHTML = value;
    else if (key.startsWith("on") && typeof value === "function") {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else node.setAttribute(key, value);
  }
  for (const child of [].concat(children)) {
    if (child == null) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

export const $ = (selector, scope = document) => scope.querySelector(selector);
export const $$ = (selector, scope = document) => Array.from(scope.querySelectorAll(selector));

/* ------------------------------ format -------------------------------- */

const nf = new Intl.NumberFormat("zh-CN");

export const fmt = {
  num: (value, digits = 0) =>
    value == null || Number.isNaN(Number(value))
      ? "—"
      : Number(value).toLocaleString("zh-CN", {
          minimumFractionDigits: digits,
          maximumFractionDigits: digits,
        }),
  compact: (value) => {
    const n = Number(value ?? 0);
    if (n >= 10000) return `${(n / 10000).toFixed(1)}万`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
    return nf.format(n);
  },
  pct: (value, digits = 0) =>
    value == null ? "—" : `${(Number(value) * 100).toFixed(digits)}%`,
  date: (value) => {
    if (!value) return "—";
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
      d.getDate(),
    ).padStart(2, "0")}`;
  },
  time: (value) => {
    if (!value) return "—";
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  },
  rel: (value) => {
    if (!value) return "—";
    const diff = Date.now() - new Date(value).getTime();
    if (Number.isNaN(diff)) return String(value);
    const min = Math.round(diff / 60000);
    if (min < 1) return "刚刚";
    if (min < 60) return `${min} 分钟前`;
    const hour = Math.round(min / 60);
    if (hour < 24) return `${hour} 小时前`;
    const day = Math.round(hour / 24);
    if (day < 30) return `${day} 天前`;
    return fmt.date(value);
  },
  dur: (seconds) => {
    const s = Math.max(0, Math.round(Number(seconds ?? 0)));
    if (s < 60) return `${s}s`;
    const m = Math.floor(s / 60);
    const rest = s % 60;
    return rest ? `${m}m ${rest}s` : `${m}m`;
  },
};

/* ------------------------------- icons -------------------------------- */

const ICONS = {
  overview: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
  interview:
    '<path d="M21 12a8 8 0 0 1-8 8H7l-4 3V12a8 8 0 0 1 8-8h2a8 8 0 0 1 8 8Z"/><path d="M9 11h6M9 15h4"/>',
  report:
    '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/>',
  knowledge:
    '<circle cx="12" cy="5" r="2.4"/><circle cx="5" cy="18" r="2.4"/><circle cx="19" cy="18" r="2.4"/><path d="M12 7.4 6.2 15.8M12 7.4l5.8 8.4M7.4 18h9.2"/>',
  memory:
    '<ellipse cx="12" cy="5.5" rx="7.5" ry="3"/><path d="M4.5 5.5v6c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3v-6"/><path d="M4.5 11.5v6c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3v-6"/>',
  settings:
    '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2.2"/><circle cx="9" cy="17" r="2.2"/>',
  play: '<path d="M7 4.5 19 12 7 19.5Z"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="2"/>',
  send: '<path d="M4 12l16-8-6 16-3-6-7-2Z"/>',
  check: '<path d="M4.5 12.5 9 17l10.5-10.5"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  download: '<path d="M12 3v12"/><path d="m7 11 5 5 5-5"/><path d="M4 20h16"/>',
  refresh:
    '<path d="M20 11a8 8 0 1 0-2.3 5.6"/><path d="M20 4.5V11h-6.5"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19"/>',
  moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"/>',
  spark: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4"/><circle cx="12" cy="12" r="3"/>',
  target:
    '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  warn: '<path d="M12 4 2.5 20h19Z"/><path d="M12 10v4M12 17h.01"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  chevron: '<path d="m9 6 6 6-6 6"/>',
  arrowUp: '<path d="M12 19V5"/><path d="m6 11 6-6 6 6"/>',
  arrowDown: '<path d="M12 5v14"/><path d="m18 13-6 6-6-6"/>',
  minus: '<path d="M5 12h14"/>',
  layers:
    '<path d="m12 3 8 4.5-8 4.5-8-4.5Z"/><path d="m4 12 8 4.5 8-4.5"/><path d="m4 16.5 8 4.5 8-4.5"/>',
  bolt: '<path d="M13 3 5 14h5l-1 7 8-11h-5Z"/>',
  user: '<circle cx="12" cy="8" r="3.6"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
};

export function icon(name, size = 17) {
  const path = ICONS[name] || ICONS.spark;
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
}

/* ------------------------------ feedback ------------------------------ */

const TONES = { ok: "ok", warn: "warn", err: "err", neutral: "" };

export function toast(message, tone = "neutral", ttl = 3200) {
  const host = document.getElementById("toasts");
  if (!host) return;
  const node = el("div", { class: "toast", role: "status" });
  node.innerHTML = `<span class="dot ${TONES[tone] ?? ""}"></span><span>${esc(message)}</span>`;
  host.append(node);
  setTimeout(() => {
    node.classList.add("out");
    node.addEventListener("animationend", () => node.remove(), { once: true });
  }, ttl);
}

export function confirmDialog({
  title = "确认操作",
  body = "",
  confirmText = "确认",
  cancelText = "取消",
  danger = false,
} = {}) {
  return new Promise((resolve) => {
    const backdrop = el("div", { class: "modal-backdrop", role: "dialog", "aria-modal": "true" });
    const modal = el("div", { class: "modal" });
    modal.innerHTML = `
      <div class="modal-head"><h3 class="modal-title">${esc(title)}</h3></div>
      <div class="modal-body">${esc(body)}</div>
      <div class="modal-foot">
        <button class="btn" data-act="cancel">${esc(cancelText)}</button>
        <button class="btn ${danger ? "btn-danger" : "btn-primary"}" data-act="ok">${esc(confirmText)}</button>
      </div>`;
    backdrop.append(modal);

    const close = (result) => {
      backdrop.remove();
      document.removeEventListener("keydown", onKey);
      resolve(result);
    };
    const onKey = (event) => {
      if (event.key === "Escape") close(false);
    };

    modal.addEventListener("click", (event) => {
      const act = event.target.closest("[data-act]")?.dataset.act;
      if (act === "ok") close(true);
      if (act === "cancel") close(false);
    });
    backdrop.addEventListener("click", (event) => {
      if (event.target === backdrop) close(false);
    });
    document.addEventListener("keydown", onKey);
    document.body.append(backdrop);
    modal.querySelector('[data-act="ok"]')?.focus();
  });
}

/* ------------------------------- charts ------------------------------- */

/**
 * 面积折线图。points: number[]。刻意不画坐标轴数字，只保留基线与末点高亮，
 * 让卡片里的趋势图保持安静。
 */
export function areaChart(points, { width = 640, height = 140, labels = [] } = {}) {
  const data = points.length ? points : [0, 0];
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const span = max - min || 1;
  const padX = 6;
  const padY = 14;
  const w = width - padX * 2;
  const h = height - padY * 2;

  const stepX = data.length > 1 ? w / (data.length - 1) : w;
  const coords = data.map((value, index) => {
    const x = padX + index * stepX;
    const y = padY + h - ((value - min) / span) * h;
    return [x, y];
  });

  const line = coords
    .map(([x, y], index) => `${index === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`)
    .join(" ");
  const area = `${line} L${(padX + w).toFixed(1)},${(padY + h).toFixed(1)} L${padX},${(
    padY + h
  ).toFixed(1)} Z`;
  const [lastX, lastY] = coords[coords.length - 1];

  const gridLines = [0, 0.5, 1]
    .map((t) => `<line x1="${padX}" x2="${padX + w}" y1="${padY + h * t}" y2="${padY + h * t}" />`)
    .join("");

  const xLabels = labels
    .map((text, index) => {
      if (data.length < 2) return "";
      const x = padX + (index * w) / (labels.length - 1 || 1);
      return `<text class="chart-label" x="${x.toFixed(1)}" y="${height - 1}" text-anchor="middle">${esc(
        text,
      )}</text>`;
    })
    .join("");

  return `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="趋势图">
    <g class="chart-grid">${gridLines}</g>
    <path class="chart-area" d="${area}" />
    <path class="chart-line" d="${line}" />
    <circle class="chart-dot" cx="${lastX.toFixed(1)}" cy="${lastY.toFixed(1)}" r="3.6" />
    ${xLabels}
  </svg>`;
}

/** 环形进度。value 取 0~1。 */
export function ring(value, { size = 108, stroke = 9, label = "" } = {}) {
  const v = Math.max(0, Math.min(1, Number(value) || 0));
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const dash = c * v;
  return `<div class="ring-wrap">
    <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" role="img" aria-label="${esc(
      label,
    )} ${(v * 100).toFixed(0)}%">
      <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none"
        stroke="var(--surface-3)" stroke-width="${stroke}" />
      <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none"
        stroke="var(--accent)" stroke-width="${stroke}" stroke-linecap="round"
        stroke-dasharray="${dash.toFixed(1)} ${c.toFixed(1)}"
        transform="rotate(-90 ${size / 2} ${size / 2})" />
    </svg>
    <div class="ring-value">${(v * 100).toFixed(0)}<span class="stat-unit">%</span></div>
    ${label ? `<div class="ring-label">${esc(label)}</div>` : ""}
  </div>`;
}

export function barList(items) {
  return `<div class="bars">${items
    .map(({ label, value, tone = "" }) => {
      const pct = Math.round(Math.max(0, Math.min(1, value)) * 100);
      return `<div class="meter">
        <div class="meter-top"><span>${esc(label)}</span><b>${pct}</b></div>
        <div class="bar ${tone}"><i style="width:${pct}%"></i></div>
      </div>`;
    })
    .join("")}</div>`;
}

/* ------------------------------ fragments ----------------------------- */

export function emptyState(title, desc, actionHtml = "") {
  return `<div class="empty">
    ${icon("layers", 30)}
    <div class="empty-title">${esc(title)}</div>
    ${desc ? `<div class="empty-desc">${esc(desc)}</div>` : ""}
    ${actionHtml}
  </div>`;
}

export function badge(text, tone = "neutral") {
  return `<span class="badge badge-${tone}">${esc(text)}</span>`;
}

export function skeletonRows(rows = 3, height = 14) {
  return Array.from({ length: rows })
    .map(
      (_, index) =>
        `<div class="skeleton" style="height:${height}px;margin:10px 0;width:${
          92 - index * 12
        }%"></div>`,
    )
    .join("");
}
