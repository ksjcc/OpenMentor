/**
 * 演示数据。
 *
 * 后端没启动时，整套界面也能完整走通（页面顶部会显示"演示数据"标记）。
 * 数字刻意做成不像随机数的形状：有起伏、有平台期，看起来像真实使用记录。
 */

const DAY = 86400000;
const now = Date.now();

export const mockDashboard = {
  user: { id: 1, name: "候选人", targetRole: "Java 后端开发" },
  kpis: {
    sessions: { value: 24, delta: 0.14, label: "累计面试场次" },
    avgScore: { value: 78.4, delta: 0.06, label: "平均得分", unit: "分" },
    weakTopics: { value: 7, delta: -0.22, label: "待攻克薄弱点", unit: "个" },
    practice: { value: 412, delta: 0.31, label: "累计练习时长", unit: "分钟" },
  },
  trend: {
    labels: ["D-13", "D-12", "D-11", "D-10", "D-9", "D-8", "D-7", "D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "今天"],
    points: [58, 61, 59, 66, 64, 70, 69, 73, 71, 76, 74, 79, 81, 78],
  },
  dimensions: [
    { label: "技术正确性", value: 0.82, tone: "ok" },
    { label: "回答完整性", value: 0.68 },
    { label: "表达能力", value: 0.74 },
    { label: "工程深度", value: 0.57, tone: "warn" },
    { label: "concurrency 场景", value: 0.43, tone: "danger" },
  ],
  recent: [
    {
      id: 1024,
      topic: "Redis 持久化",
      targetRole: "Java 后端开发",
      score: 82,
      turns: 9,
      action: "FINISH",
      at: new Date(now - 2 * 3600 * 1000).toISOString(),
      weak: ["RDB 与 AOF 取舍", "主从切换时的数据丢失窗口"],
    },
    {
      id: 1023,
      topic: "MySQL 索引与执行计划",
      targetRole: "Java 后端开发",
      score: 76,
      turns: 7,
      action: "FINISH",
      at: new Date(now - DAY).toISOString(),
      weak: ["回表代价估算", "最左前缀在范围查询后的失效"],
    },
    {
      id: 1022,
      topic: "JVM 内存与 GC",
      targetRole: "Java 后端开发",
      score: 71,
      turns: 8,
      action: "FINISH",
      at: new Date(now - 2 * DAY).toISOString(),
      weak: ["G1 Mixed GC 触发条件"],
    },
    {
      id: 1021,
      topic: "消息队列可靠投递",
      targetRole: "Java 后端开发",
      score: 88,
      turns: 6,
      action: "FINISH",
      at: new Date(now - 4 * DAY).toISOString(),
      weak: [],
    },
    {
      id: 1020,
      topic: "分布式锁与幂等",
      targetRole: "架构师",
      score: 64,
      turns: 10,
      action: "FINISH",
      at: new Date(now - 5 * DAY).toISOString(),
      weak: ["锁续期与业务超时的关系", "幂等键设计", "脑裂场景"],
    },
  ],
  weakTopics: [
    { topic: "并发与锁", count: 5, score: 48, trend: "down" },
    { topic: "G1 / Mixed GC", count: 3, score: 61, trend: "flat" },
    { topic: "索引代价估算", count: 4, score: 66, trend: "up" },
    { topic: "AOF 重写机制", count: 2, score: 69, trend: "up" },
    { topic: "幂等键设计", count: 3, score: 71, trend: "flat" },
  ],
  suggestions: [
    {
      title: "补一轮并发专项",
      desc: "最近 5 次面试里并发相关追问的平均得分只有 48，建议先做一次「分布式锁 + 幂等」专项面试。",
      tag: "高优先",
      tone: "warn",
      action: "去练习",
      topic: "分布式锁与幂等",
    },
    {
      title: "把 Redis 这轮复盘成闪卡",
      desc: "上一场「Redis 持久化」有 2 个薄弱点没有对应的复习卡，复盘后会进入今日复习队列。",
      tag: "建议",
      tone: "accent",
      action: "生成闪卡",
      topic: "Redis 持久化",
    },
    {
      title: "换一个岗位视角自测",
      desc: "当前所有面试都按「Java 后端开发」评分。同一套题换成「架构师」标准，会暴露设计层面的缺口。",
      tag: "探索",
      tone: "neutral",
      action: "新建面试",
      topic: "支付系统设计",
    },
  ],
  review: { dueToday: 9, streakDays: 6, newCards: 3 },
};

export const mockGraph = {
  nodes: [
    { id: "redis", label: "Redis", type: "技术", weight: 5 },
    { id: "persistence", label: "持久化", type: "概念", weight: 4 },
    { id: "rdb", label: "RDB", type: "组件", weight: 3 },
    { id: "aof", label: "AOF", type: "组件", weight: 3 },
    { id: "replication", label: "主从复制", type: "概念", weight: 3 },
    { id: "sentinel", label: "哨兵", type: "组件", weight: 2 },
    { id: "cluster", label: "Cluster", type: "组件", weight: 2 },
    { id: "lock", label: "分布式锁", type: "概念", weight: 4 },
    { id: "idempotent", label: "幂等", type: "概念", weight: 3 },
    { id: "mysql", label: "MySQL", type: "技术", weight: 4 },
    { id: "index", label: "索引", type: "概念", weight: 3 },
    { id: "jvm", label: "JVM", type: "技术", weight: 3 },
    { id: "gc", label: "GC", type: "概念", weight: 2 },
  ],
  edges: [
    ["redis", "persistence"],
    ["persistence", "rdb"],
    ["persistence", "aof"],
    ["redis", "replication"],
    ["replication", "sentinel"],
    ["replication", "cluster"],
    ["redis", "lock"],
    ["lock", "idempotent"],
    ["mysql", "index"],
    ["jvm", "gc"],
    ["redis", "mysql"],
    ["redis", "jvm"],
  ],
  stats: { entities: 186, relations: 412, indexedAt: new Date(now - 3600 * 1000).toISOString() },
};

export const mockProfile = {
  userId: 1,
  targetRole: "Java 后端开发",
  resumeText:
    "5 年 Java 后端经验，负责过订单中台与支付网关。主导过 Redis 集群迁移，日均 2 亿请求。熟悉 Spring Boot、MyBatis、RocketMQ，做过一次全链路压测与容量规划。",
  parsed: {
    years: 5,
    level: "中级偏上",
    skills: [
      { name: "Java / JVM", level: 0.8 },
      { name: "Spring 生态", level: 0.85 },
      { name: "Redis", level: 0.72 },
      { name: "MySQL", level: 0.68 },
      { name: "消息队列", level: 0.66 },
      { name: "分布式一致性", level: 0.45 },
    ],
    projects: [
      {
        name: "订单中台",
        role: "核心开发",
        highlight: "拆分单体订单模块，QPS 从 1.2k 提升到 8k",
      },
      {
        name: "支付网关",
        role: "负责人",
        highlight: "接入 6 家渠道，做统一幂等与对账补偿",
      },
    ],
  },
  memory: {
    summary:
      "候选人在缓存与消息中间件上的实战经验扎实，能讲清机制与取舍。明显的短板集中在「高并发下的正确性」：分布式锁续期、幂等键设计、主从切换窗口的数据一致性，多次追问都停在概念层，缺少落地细节。",
    chunks: [
      "第 1 场 · Redis 持久化：能说清 RDB fork 与 COW，但对 AOF 重写的触发条件和阻塞点回答含糊。",
      "第 2 场 · MySQL 索引：能写对联合索引，回表代价的估算需要提示。",
      "第 3 场 · 分布式锁：能说出 SETNX + 过期时间，锁续期与业务超时的关系没有想清楚。",
      "第 4 场 · 消息队列：可靠投递答得好，能主动提到本地消息表与对账。",
    ],
  },
};

export const mockReport = {
  sessionId: 1024,
  topic: "Redis 持久化",
  targetRole: "Java 后端开发",
  score: 82,
  turns: 9,
  durationSec: 1080,
  startedAt: new Date(now - 3 * 3600 * 1000).toISOString(),
  dimensions: [
    { label: "技术正确性", value: 0.86, tone: "ok" },
    { label: "回答完整性", value: 0.78 },
    { label: "表达能力", value: 0.81 },
    { label: "工程深度", value: 0.7 },
  ],
  summary:
    "整体表现稳定：能准确说出 RDB 的 fork + COW 机制，也能把 AOF 的三种 fsync 策略讲清楚。追问到「主从切换时如何避免数据丢失」时开始变虚，两次都停在「用哨兵能自动切换」，没有提到 min-replicas-to-write 这类参数，也没有讨论异步复制窗口。",
  strengths: [
    "RDB 触发时机与 fork 代价说得清楚，能联系到实际内存占用",
    "主动对比 AOF 三种 fsync 策略的取舍，提到了性能与丢失窗口的权衡",
    "能结合订单中台的迁移经验举例，不空谈概念",
  ],
  weaknesses: [
    "主从切换的数据丢失窗口只答到「哨兵自动切换」层面",
    "AOF 重写的触发条件与重写期间的写阻塞没答出来",
    "没有提到 min-replicas-to-write / min-replicas-max-lag 的作用",
  ],
  flashcards: [
    {
      q: "主从切换时，如何降低异步复制造成的数据丢失？",
      a:
        "写入侧设置 min-replicas-to-write 与 min-replicas-max-lag，保证至少有 N 个从节点在 M 秒内确认；同时接受「极端情况下仍可能丢」这个前提，用业务幂等或对账兜底。",
      tag: "Redis",
    },
    {
      q: "AOF 重写为什么不会阻塞主线程？",
      a: "重写由子进程完成，基于当时的数据快照；重写期间的新写命令同时追加到 AOF 缓冲区和重写缓冲区，重写结束后把重写缓冲区的内容刷进新文件并替换。阻塞点在于替换时的短暂停顿与 fsync 策略。",
      tag: "Redis",
    },
    {
      q: "RDB 的 COW 在什么情况会退化成高内存占用？",
      a: "写入量大的场景下，被修改的页会复制，fork 后的内存增长可能接近原数据量；这也是为什么大实例要用更小的写压力窗口或改用从库做持久化。",
      tag: "Redis",
    },
  ],
};

export const mockMemorySearch = (keyword) => [
  {
    id: "mem-1",
    score: 0.93,
    source: "第 3 场 · 分布式锁",
    text: `与「${keyword}」相关：候选人能说出 SETNX + EX 的基本用法，但对锁续期与业务超时的关系没有形成判断标准。`,
  },
  {
    id: "mem-2",
    score: 0.87,
    source: "第 1 场 · Redis 持久化",
    text: `与「${keyword}」相关：能讲清 RDB 的 fork + COW，但对 AOF 重写的阻塞点回答含糊。`,
  },
  {
    id: "mem-3",
    score: 0.71,
    source: "长期画像 · 2026-10",
    text: `与「${keyword}」相关：高并发正确性是稳定短板，涉及锁、幂等、切换窗口的追问平均得分偏低。`,
  },
];

/** 演示模式下的模拟 SSE：按事件节奏回放，让聊天界面看起来是真流式。 */
export function mockInterviewScript(topic) {
  return [
    { delay: 320, event: "action", data: "NEXT" },
    {
      delay: 900,
      event: "chunk",
      data:
        `我们聊一下${topic}。假设线上一个 Redis 实例内存已经到 24G，RDB 落盘期间出现明显卡顿。` +
        `你会先看哪些指标，然后怎么处理？`,
    },
    { delay: 200, event: "score", data: "总分 82；薄弱点：RDB fork 代价；评价：机制清楚，取舍略虚" },
    { delay: 120, event: "complete", data: "done" },
  ];
}
