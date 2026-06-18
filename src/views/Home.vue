<template>
  <div class="dashboard">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-blob blob-1"></div>
      <div class="bg-blob blob-2"></div>
      <div class="bg-blob blob-3"></div>
    </div>

    <!-- 欢迎区域 -->
    <section class="welcome-section animate-fade-in">
      <div class="welcome-content">
        <h1 class="welcome-title">
          欢迎回来，<span class="text-gradient">学习者</span>
        </h1>
        <p class="welcome-subtitle">继续你的学习旅程，探索AI驱动的个性化学习体验</p>
        <div class="welcome-stats">
          <div class="welcome-stat">
            <span class="stat-num">{{ stats.sessions }}</span>
            <span class="stat-text">次对话</span>
          </div>
          <div class="welcome-stat">
            <span class="stat-num">{{ stats.paths }}</span>
            <span class="stat-text">条路径</span>
          </div>
          <div class="welcome-stat">
            <span class="stat-num">{{ stats.resources }}</span>
            <span class="stat-text">份资源</span>
          </div>
        </div>
      </div>
      <div class="welcome-art">
        <div class="art-circle"></div>
        <div class="art-circle"></div>
        <div class="art-circle"></div>
      </div>
    </section>

    <!-- 统计卡片 -->
    <section class="stats-section">
      <div class="stat-card glass-card animate-fade-in" style="animation-delay: 0.1s">
        <div class="stat-icon" style="background: linear-gradient(135deg, #0071e3, #00c6fb)">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.sessions }}</span>
          <span class="stat-label">对话次数</span>
        </div>
        <div class="stat-trend up">
          <el-icon><Top /></el-icon> 活跃
        </div>
      </div>

      <div class="stat-card glass-card animate-fade-in" style="animation-delay: 0.2s">
        <div class="stat-icon" style="background: linear-gradient(135deg, #34c759, #30d15c)">
          <el-icon><Guide /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.paths }}</span>
          <span class="stat-label">学习路径</span>
        </div>
        <div class="stat-trend up">
          <el-icon><Top /></el-icon> 进行中
        </div>
      </div>

      <div class="stat-card glass-card animate-fade-in" style="animation-delay: 0.3s">
        <div class="stat-icon" style="background: linear-gradient(135deg, #ff9500, #ff3b30)">
          <el-icon><VideoPlay /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.videos }}</span>
          <span class="stat-label">视频课程</span>
        </div>
        <div class="stat-trend">
          <el-icon><VideoCamera /></el-icon> 可用
        </div>
      </div>

      <div class="stat-card glass-card animate-fade-in" style="animation-delay: 0.4s">
        <div class="stat-icon" style="background: linear-gradient(135deg, #af52de, #5856d6)">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.resources }}</span>
          <span class="stat-label">学习资源</span>
        </div>
        <div class="stat-trend">
          <el-icon><Document /></el-icon> 已生成
        </div>
      </div>
    </section>

    <!-- 快速入口 -->
    <section class="quick-actions">
      <h2 class="section-title">快速开始</h2>
      <div class="actions-grid">
        <div
          v-for="(action, idx) in quickActions"
          :key="idx"
          class="action-card glass-card animate-fade-in"
          :style="{ animationDelay: `${0.1 * (idx + 1)}s` }"
          @click="$router.push(action.path)"
        >
          <div class="action-icon" :style="{ background: action.gradient }">
            <el-icon :size="28"><component :is="action.icon" /></el-icon>
          </div>
          <h3 class="action-title">{{ action.title }}</h3>
          <p class="action-desc">{{ action.description }}</p>
          <div class="action-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </section>

    <!-- 最近学习 -->
    <section class="recent-section">
      <h2 class="section-title">最近学习</h2>
      <div class="recent-list">
        <div
          v-for="(item, idx) in recentItems"
          :key="idx"
          class="recent-item glass-card animate-fade-in"
          :style="{ animationDelay: `${0.1 * (idx + 1)}s` }"
          @click="$router.push(item.path)"
        >
          <div class="recent-icon" :style="{ background: item.gradient }">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="recent-info">
            <h4 class="recent-title">{{ item.title }}</h4>
            <p class="recent-meta">{{ item.meta }}</p>
          </div>
          <el-tag :type="item.tagType" size="small" effect="plain">{{ item.tag }}</el-tag>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  ChatDotRound,
  Guide,
  VideoPlay,
  Document,
  ArrowRight,
  Cpu,
  Collection,
  VideoCamera,
  Reading,
  Top
} from '@element-plus/icons-vue'
import { useAgentStore } from '@/stores/agent'
import { fetchSessions, fetchLearningPathsByUser } from '@/api/agent'
import request from '@/utils/request'

const store = useAgentStore()

const stats = ref({
  sessions: 0,
  paths: 0,
  videos: 0,
  resources: 0
})

const quickActions = [
  {
    title: 'AI学习助手',
    description: '通过对话了解你的学习水平，生成个性化学习路径',
    icon: Cpu,
    gradient: 'linear-gradient(135deg, #0071e3, #00c6fb)',
    path: '/agent-chat'
  },
  {
    title: '学习路径',
    description: '查看AI为你规划的个性化学习路径',
    icon: Collection,
    gradient: 'linear-gradient(135deg, #34c759, #30d15c)',
    path: '/learning-paths'
  },
  {
    title: '视频学习',
    description: '浏览精选视频课程，高效学习',
    icon: VideoCamera,
    gradient: 'linear-gradient(135deg, #ff9500, #ff6b00)',
    path: '/videos'
  },
  {
    title: '学习资源',
    description: '查看生成的文档、练习题和思维导图',
    icon: Reading,
    gradient: 'linear-gradient(135deg, #af52de, #5856d6)',
    path: '/resources'
  }
]

const recentItems = ref([])

async function loadStats() {
  try {
    // 加载会话数
    const sessions = await fetchSessions(store.userId)
    stats.value.sessions = sessions?.length || 0

    // 加载路径数
    const paths = await fetchLearningPathsByUser(store.userId)
    stats.value.paths = paths?.length || 0

    // 加载视频数
    try {
      const videoRes = await request.get('/api/videos', { params: { page: 1, size: 1 } })
      stats.value.videos = videoRes.data?.total || 5
    } catch {
      stats.value.videos = 5
    }

    // 加载资源数
    stats.value.resources = 21 // 预存资源数

    // 更新最近学习
    updateRecentItems(sessions, paths)
  } catch (e) {
    console.error('加载统计数据失败:', e)
  }
}

function updateRecentItems(sessions, paths) {
  const items = []

  // 添加最近的路径
  if (paths && paths.length > 0) {
    const latestPath = paths[0]
    items.push({
      title: latestPath.title || '学习路径 #' + latestPath.id,
      meta: `学习路径 · ${latestPath.nodeCount || 0}个节点`,
      icon: Guide,
      gradient: 'linear-gradient(135deg, #0071e3, #00c6fb)',
      path: '/learning-paths',
      tag: latestPath.status === 'completed' ? '已完成' : '进行中',
      tagType: latestPath.status === 'completed' ? 'success' : 'primary'
    })
  }

  // 添加最近的对话
  if (sessions && sessions.length > 0) {
    const latestSession = sessions[0]
    items.push({
      title: latestSession.title || 'AI对话 #' + latestSession.id,
      meta: '最近对话',
      icon: ChatDotRound,
      gradient: 'linear-gradient(135deg, #34c759, #30d15c)',
      path: '/agent-chat',
      tag: latestSession.status === 'completed' ? '已完成' : '继续',
      tagType: latestSession.status === 'completed' ? 'success' : 'primary'
    })
  }

  // 默认项
  if (items.length === 0) {
    items.push({
      title: '开始学习',
      meta: '点击开始你的学习之旅',
      icon: Cpu,
      gradient: 'linear-gradient(135deg, #0071e3, #00c6fb)',
      path: '/agent-chat',
      tag: '开始',
      tagType: 'primary'
    })
  }

  recentItems.value = items
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 40px;
  position: relative;
}

/* ========== 背景装饰 ========== */
.bg-decoration {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.15;
}

.blob-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(135deg, #0071e3, #5856d6);
  top: -200px;
  right: -200px;
  animation: blob-float 20s ease-in-out infinite;
}

.blob-2 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #34c759, #00c6fb);
  bottom: -100px;
  left: -100px;
  animation: blob-float 25s ease-in-out infinite reverse;
}

.blob-3 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #af52de, #ff3b30);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: blob-float 30s ease-in-out infinite;
}

@keyframes blob-float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -30px) scale(1.05); }
  50% { transform: translate(-20px, 20px) scale(0.95); }
  75% { transform: translate(20px, 10px) scale(1.02); }
}

/* ========== 欢迎区域 ========== */
.welcome-section {
  background: linear-gradient(135deg, rgba(26, 26, 46, 0.9) 0%, rgba(22, 33, 62, 0.9) 50%, rgba(15, 52, 96, 0.9) 100%);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 48px;
  position: relative;
  overflow: hidden;
  z-index: 1;
}

.welcome-content {
  position: relative;
  z-index: 2;
}

.welcome-title {
  font-size: 36px;
  font-weight: 700;
  color: white;
  margin-bottom: 12px;
  line-height: 1.2;
}

.welcome-subtitle {
  font-size: 17px;
  color: rgba(255, 255, 255, 0.7);
  max-width: 500px;
  margin-bottom: 24px;
}

.welcome-stats {
  display: flex;
  gap: 32px;
}

.welcome-stat {
  display: flex;
  flex-direction: column;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: white;
}

.stat-text {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.welcome-art {
  position: absolute;
  right: -50px;
  top: -50px;
  width: 400px;
  height: 400px;
}

.art-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.art-circle:nth-child(1) {
  width: 300px;
  height: 300px;
  right: 0;
  top: 0;
  animation: float 6s ease-in-out infinite;
}

.art-circle:nth-child(2) {
  width: 200px;
  height: 200px;
  right: 100px;
  top: 100px;
  animation: float 8s ease-in-out infinite reverse;
}

.art-circle:nth-child(3) {
  width: 150px;
  height: 150px;
  right: 50px;
  top: 200px;
  animation: float 10s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-20px) scale(1.05); }
}

/* ========== 统计卡片 ========== */
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  position: relative;
  z-index: 1;
}

.stat-card {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--apple-text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 13px;
  color: var(--apple-text-secondary);
  margin-top: 4px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--apple-text-tertiary);
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 8px;
}

.stat-trend.up {
  color: var(--apple-green);
  background: rgba(52, 199, 89, 0.1);
}

/* ========== 快速入口 ========== */
.section-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--apple-text-primary);
  margin-bottom: 20px;
  position: relative;
  z-index: 1;
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  position: relative;
  z-index: 1;
}

.action-card {
  padding: 28px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.action-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 20px;
}

.action-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--apple-text-primary);
  margin-bottom: 8px;
}

.action-desc {
  font-size: 13px;
  color: var(--apple-text-secondary);
  line-height: 1.5;
}

.action-arrow {
  position: absolute;
  right: 20px;
  bottom: 20px;
  width: 32px;
  height: 32px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--apple-text-tertiary);
  transition: all 0.3s ease;
}

.action-card:hover .action-arrow {
  background: var(--apple-blue);
  color: white;
  transform: translateX(4px);
}

/* ========== 最近学习 ========== */
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.recent-item {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.recent-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  flex-shrink: 0;
}

.recent-info {
  flex: 1;
}

.recent-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--apple-text-primary);
  margin-bottom: 4px;
}

.recent-meta {
  font-size: 13px;
  color: var(--apple-text-secondary);
}

/* ========== 响应式 ========== */
@media (max-width: 1200px) {
  .stats-section,
  .actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .welcome-section {
    padding: 32px;
  }
  
  .welcome-title {
    font-size: 28px;
  }
  
  .stats-section,
  .actions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
