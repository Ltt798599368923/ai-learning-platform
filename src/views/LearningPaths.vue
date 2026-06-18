<template>
  <div class="learning-paths-page">
    <!-- Navigation -->
    <nav class="apple-navbar">
      <div class="nav-brand" @click="$router.push('/home')">
        <div class="brand-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="6" fill="url(#grad)"/>
            <path d="M8 14L12 18L20 10" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            <defs>
              <linearGradient id="grad" x1="0" y1="0" x2="28" y2="28">
                <stop stop-color="#007AFF"/>
                <stop offset="1" stop-color="#5856D6"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <span class="brand-name">AI 云学智训</span>
      </div>
      <div class="nav-links">
        <router-link to="/home" class="nav-link">首页</router-link>
        <router-link to="/agent-chat" class="nav-link">AI 助手</router-link>
      </div>
    </nav>

    <!-- Main Content -->
    <main class="main-content">
      <div class="apple-container">
        <!-- Page Header -->
        <div class="page-header animate-slide-up">
          <h1 class="apple-large-title">学习路径</h1>
          <p class="page-subtitle">多智能体协同规划的个性化学习路径</p>
        </div>

        <!-- Empty State -->
        <div v-if="paths.length === 0" class="apple-empty-state">
          <div class="apple-empty-state-icon">
            <el-icon :size="32"><Guide /></el-icon>
          </div>
          <h3 class="apple-empty-state-title">暂无学习路径</h3>
          <p class="apple-empty-state-desc">与 AI 助手对话，生成你的个性化学习路径</p>
          <button class="apple-btn apple-btn-primary apple-btn-pill" @click="$router.push('/agent-chat')">
            开始对话
          </button>
        </div>

        <!-- Path List -->
        <div v-else class="path-grid">
          <div
            v-for="path in paths"
            :key="path.id"
            class="path-card apple-card apple-card-interactive"
            @click="goToPath(path)"
          >
            <div class="path-card-top">
              <span :class="['apple-tag', statusTagClass(path.status)]">
                {{ statusLabel(path.status) }}
              </span>
              <div class="path-actions">
                <span class="path-date">{{ formatDate(path.createdAt) }}</span>
                <button class="delete-btn" @click.stop="handleDeletePath(path)" title="删除">
                  <el-icon :size="16"><Delete /></el-icon>
                </button>
              </div>
            </div>
            <h3 class="path-title">{{ path.title || '学习路径 #' + path.id }}</h3>
            <p class="path-desc" v-if="path.courseName">{{ path.courseName }}</p>
            <div class="path-meta">
              <span class="meta-item">
                <el-icon><List /></el-icon>
                {{ path.nodeCount || path.nodes?.length || 0 }} 个节点
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ calcTotalMinutes(path) }} 分钟
              </span>
            </div>
            <div class="path-generating" v-if="path.status === 'active' && !path.nodes?.length">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>资源生成中...</span>
            </div>
          </div>
        </div>

        <!-- Path Detail -->
        <div v-if="selectedPath" class="detail-section animate-slide-up">
          <div class="detail-header">
            <h2 class="apple-title2">{{ selectedPath.title || '学习路径详情' }}</h2>
            <button class="apple-btn apple-btn-ghost" @click="selectedPath = null">
              ← 返回列表
            </button>
          </div>

          <div class="detail-layout">
            <!-- Left: Timeline -->
            <div class="detail-sidebar">
              <div class="sidebar-card apple-grouped-list">
                <PathTimeline
                  :path="selectedPath"
                  :selected-node-id="selectedNodeId"
                  @select-node="handleSelectNode"
                />
              </div>
              <div class="generating-tip" v-if="selectedPath && !selectedPath.nodes?.length">
                <el-icon class="is-loading"><Loading /></el-icon>
                <div>
                  <p class="tip-title">学习路径生成中...</p>
                  <p class="tip-desc">AI 正在为您规划个性化学习路径</p>
                </div>
              </div>
            </div>

            <!-- Right: Resources -->
            <div class="detail-main" v-if="selectedNodeId">
              <div class="resource-card apple-card">
                <div class="resource-header">
                  <h3 class="apple-headline">学习资源</h3>
                  <span v-if="currentNode" class="apple-tag apple-tag-blue">{{ currentNode.title }}</span>
                </div>

                <!-- Loading -->
                <div v-if="resourceLoading" class="resource-loading">
                  <div class="loading-spinner"></div>
                  <p class="loading-title">AI 正在生成学习资源...</p>
                  <p class="loading-subtitle">首次加载需要几秒，请稍候</p>
                </div>

                <!-- Resource Tabs -->
                <div v-else-if="hasResource('document') || hasResource('quiz') || hasResource('mindmap')" class="resource-tabs">
                  <div class="tab-bar">
                    <button
                      v-for="tab in availableTabs"
                      :key="tab.key"
                      :class="['tab-item', { active: activeResourceTab === tab.key }]"
                      @click="activeResourceTab = tab.key"
                    >
                      {{ tab.label }}
                    </button>
                  </div>

                  <!-- Document -->
                  <div v-if="activeResourceTab === 'document' && hasResource('document')" class="tab-content">
                    <div class="markdown-body" v-html="renderMarkdown(getResourceContent('document'))"></div>
                  </div>

                  <!-- Quiz -->
                  <div v-if="activeResourceTab === 'quiz' && hasResource('quiz')" class="tab-content">
                    <div class="quiz-list">
                      <div v-for="(q, idx) in getQuizQuestions()" :key="idx" class="quiz-item">
                        <p class="quiz-question">
                          <span class="quiz-num">{{ idx + 1 }}</span>
                          {{ q.content }}
                        </p>
                        <div class="quiz-options">
                          <div
                            v-for="(opt, oidx) in q.options"
                            :key="oidx"
                            :class="['quiz-option', {
                              selected: selectedAnswers[idx] === oidx,
                              correct: showAnswers && opt.isCorrect,
                              wrong: showAnswers && selectedAnswers[idx] === oidx && !opt.isCorrect
                            }]"
                            @click="selectAnswer(idx, oidx)"
                          >
                            <span class="option-marker">{{ String.fromCharCode(65 + oidx) }}</span>
                            <span>{{ opt.content }}</span>
                          </div>
                        </div>
                        <div v-if="showAnswers && q.analysis" class="quiz-analysis">
                          <strong>解析：</strong>{{ q.analysis }}
                        </div>
                      </div>
                    </div>
                    <div class="quiz-actions">
                      <button v-if="!showAnswers" class="apple-btn apple-btn-primary" @click="showAnswers = true">
                        查看答案
                      </button>
                      <button v-if="showAnswers" class="apple-btn apple-btn-secondary" @click="resetQuiz">
                        重新作答
                      </button>
                    </div>
                  </div>

                  <!-- Mindmap -->
                  <div v-if="activeResourceTab === 'mindmap' && hasResource('mindmap')" class="tab-content">
                    <MindmapNode v-if="getMindmapData()" :node="getMindmapData()" :depth="0" />
                  </div>
                </div>

                <!-- Empty State -->
                <div v-else class="resource-empty">
                  <div class="empty-icon">
                    <el-icon :size="32"><Document /></el-icon>
                  </div>
                  <p class="empty-title">暂无学习资源</p>
                  <button class="apple-btn apple-btn-primary" @click="generateNodeResources" :disabled="generating">
                    <el-icon v-if="!generating"><MagicStick /></el-icon>
                    {{ generating ? 'AI 生成中...' : 'AI 生成学习资源' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { List, Clock, Loading, MagicStick, Delete, Guide, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchLearningPathsByUser, fetchLearningPath, fetchNodeResources } from '@/api/agent'
import { useAgentStore } from '@/stores/agent'
import { renderMarkdown } from '@/utils/markdown'
import PathTimeline from '@/components/agent/PathTimeline.vue'
import MindmapNode from '@/components/agent/MindmapNode.vue'

const store = useAgentStore()
const paths = ref([])
const selectedPath = ref(null)
const selectedNodeId = ref(null)
const nodeResources = ref([])
const activeResourceTab = ref('document')
const selectedAnswers = ref({})
const showAnswers = ref(false)
const resourceLoading = ref(false)
const generating = ref(false)

const currentNode = computed(() => {
  if (!selectedPath.value?.nodes || !selectedNodeId.value) return null
  return selectedPath.value.nodes.find(n => n.id === selectedNodeId.value)
})

const availableTabs = computed(() => {
  const tabs = []
  if (hasResource('document')) tabs.push({ key: 'document', label: '文档' })
  if (hasResource('quiz')) tabs.push({ key: 'quiz', label: '练习题' })
  if (hasResource('mindmap')) tabs.push({ key: 'mindmap', label: '思维导图' })
  return tabs
})

function hasResource(type) {
  return nodeResources.value.some(r => (r.resourceType || r.type) === type)
}

function getResourceContent(type) {
  const resource = nodeResources.value.find(r => (r.resourceType || r.type) === type)
  if (!resource) return ''
  const content = resource.contentJson
  if (!content) return ''
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content)
      return parsed.content || parsed.title || content
    } catch { return content }
  }
  if (typeof content === 'object') {
    if (content.content) return content.content
    if (content.title) return content.title
    return JSON.stringify(content, null, 2)
  }
  return String(content)
}

function getQuizQuestions() {
  const resource = nodeResources.value.find(r => (r.resourceType || r.type) === 'quiz')
  if (!resource) return []
  const content = resource.contentJson
  if (!content) return []
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content)
      return parsed.questions || parsed.content?.questions || []
    } catch { return [] }
  }
  if (typeof content === 'object') {
    return content.questions || content.content?.questions || []
  }
  return []
}

function getMindmapData() {
  const resource = nodeResources.value.find(r => (r.resourceType || r.type) === 'mindmap')
  if (!resource) return null
  const content = resource.contentJson
  if (!content) return null
  if (typeof content === 'string') {
    try { return JSON.parse(content) } catch { return null }
  }
  if (typeof content === 'object') {
    if (content.topic) return content
    if (content.content?.topic) return content.content
    return content
  }
  return null
}

function selectAnswer(questionIdx, optionIdx) {
  if (showAnswers.value) return
  selectedAnswers.value[questionIdx] = optionIdx
}

function resetQuiz() {
  selectedAnswers.value = {}
  showAnswers.value = false
}

async function generateNodeResources() {
  if (!selectedNodeId.value) return
  generating.value = true
  resourceLoading.value = true
  try {
    await fetch(`/api/learning-path/generate-resources?pathNodeId=${selectedNodeId.value}`, {
      method: 'POST'
    }).catch(() => {})
    let resources = []
    let retries = 0
    const maxRetries = 30
    while (retries < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, 2000))
      const res = await fetch(`/api/generated-resources?pathNodeId=${selectedNodeId.value}`)
      const json = await res.json()
      if (json.code === 200 && json.data && json.data.length > 0) {
        resources = json.data
        break
      }
      retries++
    }
    nodeResources.value = resources
    store.setNodeResources(resources)
    if (resources.length > 0) {
      ElMessage.success('学习资源生成成功')
    } else {
      ElMessage.warning('资源生成超时，请稍后刷新页面查看')
    }
  } catch (e) {
    console.error('生成资源失败:', e)
    ElMessage.error('资源生成失败，请稍后重试')
  } finally {
    generating.value = false
    resourceLoading.value = false
  }
}

async function loadPaths() {
  try {
    const data = await fetchLearningPathsByUser(store.userId)
    paths.value = data || []
  } catch (e) {
    console.error('加载学习路径失败:', e)
  }
}

async function handleDeletePath(path) {
  try {
    await ElMessageBox.confirm(
      `确定要删除学习路径 "${path.title || '学习路径 #' + path.id}" 吗？`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await fetch(`/api/learning-path/${path.id}`, { method: 'DELETE' })
    const json = await res.json()
    if (json.code === 200) {
      ElMessage.success('删除成功')
      if (selectedPath.value?.id === path.id) {
        selectedPath.value = null
        selectedNodeId.value = null
      }
      await loadPaths()
    } else {
      ElMessage.error(json.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除路径失败:', e)
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

async function goToPath(path) {
  try {
    const detail = await fetchLearningPath(path.id)
    selectedPath.value = detail
    if (detail?.nodes?.length) {
      selectedNodeId.value = detail.nodes[0].id
      await handleSelectNode(detail.nodes[0])
    } else {
      nodeResources.value = []
      selectedNodeId.value = null
    }
  } catch (e) {
    console.error('加载路径详情失败:', e)
  }
}

async function handleSelectNode(node) {
  selectedNodeId.value = node.id
  selectedAnswers.value = {}
  showAnswers.value = false
  activeResourceTab.value = 'document'
  resourceLoading.value = true
  try {
    let resources = await fetchNodeResources(node.id)
    if (!resources || resources.length === 0) {
      let retries = 0
      const maxRetries = 30
      while (retries < maxRetries) {
        await new Promise(resolve => setTimeout(resolve, 2000))
        resources = await fetchNodeResources(node.id)
        if (resources && resources.length > 0) break
        retries++
      }
    }
    nodeResources.value = resources || []
    store.setNodeResources(resources || [])
    store.selectNode(node.id)
    store.setPath(selectedPath.value)
  } catch (e) {
    console.error('加载节点资源失败:', e)
    nodeResources.value = []
  } finally {
    resourceLoading.value = false
  }
}

function statusLabel(status) {
  const map = { active: '进行中', completed: '已完成', abandoned: '已放弃' }
  return map[status] || status || '未知'
}

function statusTagClass(status) {
  const map = { active: 'apple-tag-blue', completed: 'apple-tag-green', abandoned: '' }
  return map[status] || ''
}

function formatDate(date) {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

function calcTotalMinutes(path) {
  if (!path.nodes?.length) return 0
  return path.nodes.reduce((sum, n) => sum + (n.estimatedMinutes || n.estimated_minutes || 0), 0)
}

onMounted(loadPaths)
</script>

<style scoped>
/* ===== Page Layout ===== */
.learning-paths-page {
  min-height: 100vh;
  background: var(--color-bg-grouped);
}

.apple-navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 48px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(180%) blur(40px);
  -webkit-backdrop-filter: saturate(180%) blur(40px);
  border-bottom: 0.5px solid var(--color-separator);
}

[data-theme="dark"] .apple-navbar {
  background: rgba(28, 28, 30, 0.72);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 24px;
}

.nav-link {
  font-size: 15px;
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color 0.2s;
}

.nav-link:hover {
  color: var(--color-text-primary);
}

/* ===== Main Content ===== */
.main-content {
  padding: 40px 0 80px;
}

.page-header {
  margin-bottom: 40px;
}

.page-header h1 {
  margin-bottom: 8px;
}

.page-subtitle {
  font-size: 19px;
  color: var(--color-text-secondary);
}

/* ===== Path Grid ===== */
.path-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.path-card {
  padding: 24px;
  border-radius: 16px;
  cursor: pointer;
}

.path-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.path-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.path-date {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.delete-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 50%;
  color: var(--color-text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
}

.path-card:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: rgba(255, 59, 48, 0.12);
  color: var(--color-red);
}

.path-title {
  font-size: 19px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.path-desc {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

.path-meta {
  display: flex;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.path-generating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 14px;
  background: var(--color-accent-light);
  border-radius: 10px;
  color: var(--color-accent);
  font-size: 13px;
}

/* ===== Detail Section ===== */
.detail-section {
  margin-top: 48px;
  padding-top: 32px;
  border-top: 1px solid var(--color-separator);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
}

.detail-sidebar {
  position: sticky;
  top: 80px;
  align-self: start;
}

.sidebar-card {
  border-radius: 16px;
  overflow: hidden;
}

.generating-tip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 16px;
  background: var(--color-accent-light);
  border-radius: 12px;
}

.tip-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-accent);
}

.tip-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* ===== Resource Card ===== */
.resource-card {
  padding: 24px;
  border-radius: 16px;
}

.resource-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-separator);
}

/* Loading */
.resource-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-fill);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.loading-subtitle {
  font-size: 15px;
  color: var(--color-text-tertiary);
}

/* Tabs */
.tab-bar {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--color-fill);
  border-radius: 12px;
  margin-bottom: 24px;
}

.tab-item {
  flex: 1;
  padding: 8px 16px;
  border: none;
  background: transparent;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.tab-item.active {
  background: var(--color-surface);
  color: var(--color-text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.tab-content {
  min-height: 300px;
}

/* Markdown */
.markdown-body {
  line-height: 1.7;
  color: var(--color-text-primary);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 24px;
  margin-bottom: 12px;
  font-weight: 600;
}

.markdown-body :deep(pre) {
  background: var(--color-bg-secondary);
  padding: 16px;
  border-radius: 12px;
  overflow-x: auto;
}

.markdown-body :deep(code) {
  background: var(--color-fill);
  padding: 2px 6px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 14px;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
  border-radius: 12px;
  overflow: hidden;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid var(--color-separator);
}

.markdown-body :deep(th) {
  background: var(--color-bg-secondary);
  font-weight: 600;
}

/* Quiz */
.quiz-list {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.quiz-item {
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-separator);
}

.quiz-item:last-child {
  border-bottom: none;
}

.quiz-question {
  font-size: 17px;
  line-height: 1.6;
  color: var(--color-text-primary);
  margin-bottom: 16px;
}

.quiz-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: var(--color-accent);
  color: white;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  margin-right: 8px;
}

.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quiz-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-separator);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 15px;
}

.quiz-option:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-light);
}

.quiz-option.selected {
  border-color: var(--color-accent);
  background: var(--color-accent-light);
}

.quiz-option.correct {
  border-color: var(--color-green);
  background: rgba(52, 199, 89, 0.12);
}

.quiz-option.wrong {
  border-color: var(--color-red);
  background: rgba(255, 59, 48, 0.12);
}

.option-marker {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-fill);
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.quiz-option.selected .option-marker {
  background: var(--color-accent);
  color: white;
}

.quiz-option.correct .option-marker {
  background: var(--color-green);
  color: white;
}

.quiz-option.wrong .option-marker {
  background: var(--color-red);
  color: white;
}

.quiz-analysis {
  margin-top: 16px;
  padding: 16px;
  background: var(--color-bg-secondary);
  border-radius: 12px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.quiz-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

/* Empty State */
.resource-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.empty-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-fill);
  border-radius: 16px;
  color: var(--color-text-tertiary);
  margin-bottom: 16px;
}

.empty-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

/* ===== Responsive ===== */
@media (max-width: 1024px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  
  .detail-sidebar {
    position: static;
  }
}

@media (max-width: 768px) {
  .apple-navbar {
    padding: 0 20px;
  }
  
  .nav-links {
    display: none;
  }
  
  .path-grid {
    grid-template-columns: 1fr;
  }
  
  .main-content {
    padding: 24px 0 60px;
  }
}
</style>
