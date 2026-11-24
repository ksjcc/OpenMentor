/**
 * 知识图谱页。
 *
 * 数据来自 GET /api/graph（GraphRAG 实体与关系，由历次面试复盘抽取）。
 * 后端返回 { nodes:[{id,name,type,importanceScore}], links:[{source,target,...}] }，
 * 这里归一化成绘图用的 { nodes:[{id,label,weight,type}], edges:[[a,b]] }。
 *
 * 布局用一个小型力导向模拟（几十个节点，140 次迭代，浏览器里毫秒级完成），
 * 不引入 d3 —— 为了三四十行代码加一个 300KB 依赖不划算。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { badge, esc, fmt, icon, toast } from "../ui.js";

const WIDTH = 920;
const HEIGHT = 430;

/** 简易力导向：斥力 + 边引力 + 向心力。确定性初始位置，保证每次刷新图一致。 */
function layout(nodes, edges) {
  const cx = WIDTH / 2;
  const cy = HEIGHT / 2;
  const radius = Math.min(WIDTH, HEIGHT) * 0.34;

  const points = nodes.map((node, index) => {
    const angle = (index / Math.max(nodes.length, 1)) * Math.PI * 2;
    const r = radius * (0.65 + ((index * 37) % 30) / 100);
    return { id: node.id, x: cx + Math.cos(angle) * r, y: cy + Math.sin(angle) * r };
  });
  const byId = new Map(points.map((p) => [p.id, p]));

  const links = edges
    .map(([a, b]) => [byId.get(a), byId.get(b)])
    .filter(([a, b]) => a && b);

  const iterations = 140;
  for (let step = 0; step < iterations; step += 1) {
    const cooling = 1 - step / iterations;

    // 斥力
    for (let i = 0; i < points.length; i += 1) {
      for (let j = i + 1; j < points.length; j += 1) {
        const a = points[i];
        const b = points[j];
        let dx = a.x - b.x;
        let dy = a.y - b.y;
        let dist = Math.hypot(dx, dy);
        if (dist < 0.01) {
          dx = (i - j) * 0.1 + 0.1;
          dy = (j - i) * 0.1 + 0.1;
          dist = 0.2;
        }
        const force = (2600 / (dist * dist)) * cooling;
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        a.x += fx;
        a.y += fy;
        b.x -= fx;
        b.y -= fy;
      }
    }

    // 边引力
    for (const [a, b] of links) {
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const dist = Math.hypot(dx, dy) || 1;
      const target = 108;
      const force = ((dist - target) / dist) * 0.06 * cooling;
      const fx = dx * force;
      const fy = dy * force;
      a.x += fx;
      a.y += fy;
      b.x -= fx;
      b.y -= fy;
    }

    // 向心力 + 边界
    for (const point of points) {
      point.x += (cx - point.x) * 0.012 * cooling;
      point.y += (cy - point.y) * 0.012 * cooling;
      point.x = Math.max(46, Math.min(WIDTH - 46, point.x));
      point.y = Math.max(34, Math.min(HEIGHT - 34, point.y));
    }
  }

  return byId;
}

function renderSvg(graph, selectedId) {
  const positions = layout(graph.nodes, graph.edges);
  const neighborIds = new Set([selectedId]);
  for (const [a, b] of graph.edges) {
    if (a === selectedId) neighborIds.add(b);
    if (b === selectedId) neighborIds.add(a);
  }

  const edgePath = graph.edges
    .map(([a, b]) => {
      const from = positions.get(a);
      const to = positions.get(b);
      if (!from || !to) return "";
      const active = selectedId && (a === selectedId || b === selectedId);
      return `<line class="gedge ${active ? "accent" : ""}" x1="${from.x.toFixed(1)}" y1="${from.y.toFixed(
        1,
      )}" x2="${to.x.toFixed(1)}" y2="${to.y.toFixed(1)}" />`;
    })
    .join("");

  const nodeEls = graph.nodes
    .map((node) => {
      const point = positions.get(node.id);
      if (!point) return "";
      const hub = node.weight >= 4;
      const dim = selectedId && !neighborIds.has(node.id);
      const r = hub ? 20 : 13 + Math.min(4, node.weight || 0);
      return `<g class="gnode ${hub ? "hub" : ""}" data-node="${esc(node.id)}"
          transform="translate(${point.x.toFixed(1)},${point.y.toFixed(1)})"
          style="cursor:pointer;opacity:${dim ? 0.35 : 1}">
        <circle r="${r}" />
        <text y="${r + 14}">${esc(node.label)}</text>
      </g>`;
    })
    .join("");

  return `<svg viewBox="0 0 ${WIDTH} ${HEIGHT}" role="img" aria-label="知识点关系图">
    ${edgePath}${nodeEls}
  </svg>`;
}

function entityList(graph, keyword) {
  const kw = (keyword || "").trim().toLowerCase();
  const rows = graph.nodes.filter((node) => !kw || node.label.toLowerCase().includes(kw));
  if (!rows.length) {
    return `<div class="empty" style="padding:26px"><div class="empty-title">没有匹配的实体</div></div>`;
  }
  return `<div class="list">${rows
    .map(
      (node) => `<div class="list-item" data-node-row="${esc(node.id)}" style="cursor:pointer">
        <div class="icon-box ${node.weight >= 4 ? "accent" : ""}">${icon("knowledge", 14)}</div>
        <div class="li-main">
          <div class="li-title">${esc(node.label)}</div>
          <div class="li-meta">${esc(node.type || "实体")} · 权重 ${node.weight}</div>
        </div>
      </div>`,
    )
    .join("")}</div>`;
}

/** 后端 GraphDTO → 绘图模型。id 统一转字符串，避免数字/字符串比较踩坑。 */
function normalizeGraph(raw) {
  const nodes = (raw?.nodes || [])
    .map((node) => ({
      id: String(node.id ?? node.name),
      label: node.name || node.label || "未命名",
      type: node.type || "实体",
      weight: Math.max(1, Math.min(5, Math.round((node.importanceScore ?? node.masteryLevel ?? 60) / 20))),
    }))
    .filter((node) => node.id);

  const known = new Set(nodes.map((node) => node.id));
  const edges = [];
  for (const link of raw?.links || []) {
    const from = String(link.source ?? "");
    const to = String(link.target ?? "");
    if (!from || !to || !known.has(from) || !known.has(to) || from === to) continue;
    edges.push([from, to]);
  }
  return {
    nodes,
    edges,
    stats: { entities: nodes.length, relations: edges.length, indexedAt: raw?.indexedAt || null },
  };
}

async function loadGraph() {
  const raw = await api.graph(store.settings.userId);
  return normalizeGraph(raw);
}

export default {
  id: "knowledge",
  title: "知识图谱",

  async render() {
    let graph = null;
    let failure = null;
    try {
      graph = await loadGraph();
    } catch (error) {
      failure = error;
    }

    const head = `<div class="page-head">
        <div>
          <h1 class="page-title">知识图谱</h1>
          <p class="page-sub">从历次面试的追问与评分里抽取的实体与关系，用于决定下一题考什么。</p>
        </div>
        <div class="page-actions">
          <div class="row" style="gap:8px">
            ${icon("search", 15)}
            <input class="input" id="g-search" placeholder="搜索实体" style="width:200px" />
          </div>
          <button class="btn" id="g-refresh">${icon("refresh", 15)}刷新</button>
        </div>
      </div>`;

    if (!graph || !graph.nodes.length) {
      return {
        html: `${head}
          <div class="card"><div class="card-body"><div class="empty">
            <div class="empty-title">${failure ? "图谱加载失败" : "图谱还没有内容"}</div>
            <div class="empty-desc">${esc(
              failure ? failure.message : "完成一场面试并生成复盘后，知识点与关系会在这里逐步长出来。",
            )}</div>
            <button class="btn btn-primary" data-go="#/interview">去面试</button>
          </div></div></div>`,
        after(root) {
          root.querySelector("#g-refresh")?.addEventListener("click", () => location.reload());
          root.querySelectorAll("[data-go]").forEach((node) => {
            node.addEventListener("click", () => {
              location.hash = node.dataset.go;
            });
          });
        },
      };
    }

    let selected = null;

    const html = `${head}

      <div class="grid grid-2-1">
        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">关系视图</div>
              <div class="card-desc">点击节点查看邻居；节点越大越是高频考点</div>
            </div>
            <div class="card-head-actions">
              ${badge(`${graph.nodes.length} 节点`, "neutral")}
              ${badge(`${graph.edges.length} 条边`, "neutral")}
            </div>
          </div>
          <div class="card-body">
            <div class="graph-stage" id="g-stage">${renderSvg(graph, null)}</div>
          </div>
          <div class="card-foot">
            <span>${graph.stats.indexedAt ? `索引时间 ${esc(fmt.rel(graph.stats.indexedAt))}` : "由历次面试复盘逐步累积"}</span>
            <span class="spacer"></span>
            <span>实体 ${fmt.num(graph.stats.entities)} · 关系 ${fmt.num(graph.stats.relations)}</span>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <div class="card-title">实体</div>
              <div class="card-desc" id="g-hint">按权重排序</div>
            </div>
          </div>
          <div class="card-body tight scroll-y" id="g-list" style="max-height:520px">
            ${entityList(graph, "")}
          </div>
        </div>
      </div>

      <div class="card" style="margin-top:16px">
        <div class="card-head">
          <div class="icon-box accent">${icon("search", 15)}</div>
          <div>
            <div class="card-title">知识问答</div>
            <div class="card-desc">基于图谱与记忆的检索问答，语气为学习辅导而非面试</div>
          </div>
        </div>
        <div class="card-body stack" style="gap:14px">
          <div class="row">
            <input class="input" id="k-question" placeholder="例如：G1 的 Mixed GC 和老年代占比是什么关系？" />
            <button class="btn btn-primary" id="k-ask" style="flex:none">${icon("send", 14)}提问</button>
          </div>
          <div id="k-answer">
            <div class="hint">提问后会走检索增强链路，答案只基于召回证据。</div>
          </div>
        </div>
      </div>
    `;

    return {
      html,
      after(root) {
        const stage = root.querySelector("#g-stage");
        const list = root.querySelector("#g-list");
        const hint = root.querySelector("#g-hint");

        const paint = () => {
          stage.innerHTML = renderSvg(graph, selected);
          const node = graph.nodes.find((item) => item.id === selected);
          hint.textContent = node ? `已选中：${node.label}` : "按权重排序";
          list.innerHTML = entityList(graph, root.querySelector("#g-search")?.value || "");
        };

        stage.addEventListener("click", (event) => {
          const id = event.target.closest("[data-node]")?.dataset.node;
          if (!id) return;
          selected = selected === id ? null : id;
          paint();
        });

        list.addEventListener("click", (event) => {
          const id = event.target.closest("[data-node-row]")?.dataset.nodeRow;
          if (!id) return;
          selected = selected === id ? null : id;
          paint();
        });

        root.querySelector("#g-refresh").addEventListener("click", () => location.reload());
        root.querySelector("#g-search")?.addEventListener("input", () => paint());

        // ---- 知识问答 ----
        const input = root.querySelector("#k-question");
        const output = root.querySelector("#k-answer");
        const askButton = root.querySelector("#k-ask");

        const ask = async () => {
          const question = input.value.trim();
          if (!question) {
            toast("先输入一个问题", "warn");
            return;
          }
          askButton.disabled = true;
          output.innerHTML = `<div class="hint">检索中…</div>`;
          try {
            const raw = await api.ask({ userId: store.settings.userId, question });
            const answer = typeof raw === "string" ? raw : raw?.answer || JSON.stringify(raw);
            output.innerHTML = `<div class="card" style="box-shadow:none">
              <div class="card-body" style="padding:14px 16px">
                <div class="hint" style="margin-bottom:8px">回答</div>
                <div style="font-size:13px;line-height:1.85;color:var(--ink-2);white-space:pre-wrap">${esc(
                  answer,
                )}</div>
              </div>
            </div>`;
          } catch (error) {
            output.innerHTML = `<div class="hint" style="color:var(--danger)">问答失败：${esc(
              error.message,
            )}</div>`;
          } finally {
            askButton.disabled = false;
          }
        };

        askButton.addEventListener("click", ask);
        input.addEventListener("keydown", (event) => {
          if (event.key === "Enter") ask();
        });
      },
    };
  },
};
