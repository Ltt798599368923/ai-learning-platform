<template>
  <div class="agent-chat-page">
    <!-- 历史记录切换按钮 -->
    <button class="history-toggle" @click="showHistory = !showHistory" :class="{ active: showHistory }">
      <el-icon><Fold v-if="showHistory" /><Expand v-else /></el-icon>
    </button>

    <!-- Sidebar - 历史记录面板 -->
    <transition name="slide">
      <aside class="chat-sidebar" v-show="showHistory">
        <div class="sidebar-header">
          <h3>历史对话</h3>
          <button class="apple-btn apple-btn-icon" @click="handleNewChat" :disabled="store.isStreaming" title="新对话">
            <el-icon :size="18"><Plus /></el-icon>
          </button>
        </div>
        <div class="session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            :class="['session-item', { active: store.sessionId === session.id }]"
            @click="loadSession(session)"
          >
            <div class="session-info">
              <p class="session-title">{{ session.title || '新对话' }}</p>
              <p class="session-time">{{ formatTime(session.createdAt) }}</p>
            </div>
            <button class="session-delete" @click.stop="handleDeleteSession(session)" title="删除">
              <el-icon :size="14"><Delete /></el-icon>
            </button>
          </div>
          <div v-if="sessions.length === 0" class="session-empty">
            <p>暂无对话记录</p>
          </div>
        </div>
      </aside>
    </transition>

    <!-- Main Chat Area -->
    <main class="chat-main" :class="{ 'sidebar-collapsed': !showHistory }">
      <!-- Chat Header -->
      <header class="chat-header">
        <div class="header-left">
          <h2>AI 学习助手</h2>
          <span v-if="store.currentPhase && store.currentPhase !== 'idle'" class="phase-badge">
            {{ store.phaseLabel }}
          </span>
        </div>
        <div class="header-right">
          <button v-if="store.selectedNodeId" class="apple-btn apple-btn-ghost apple-btn-sm" @click="store.selectNode(null)">
            关闭资源面板
          </button>
        </div>
      </header>

      <!-- Messages Area -->
      <div class="messages-container" ref="messagesRef">
        <!-- Welcome Message -->
        <div v-if="store.messages.length === 0" class="welcome-section">
          <div class="welcome-icon">
            <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
              <rect width="56" height="56" rx="16" fill="url(#welcome-grad)"/>
              <path d="M20 28L26 34L36 22" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="welcome-grad" x1="0" y1="0" x2="56" y2="56">
                  <stop stop-color="#007AFF"/>
                  <stop offset="1" stop-color="#5856D6"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h2>你好，我是 AI 学习助手</h2>
          <p>告诉我你想学什么，我会通过对话了解你的水平，为你规划个性化学习路径，并生成专属学习资料。</p>
          <div class="quick-prompts">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              class="prompt-chip"
              @click="handleQuickPrompt(prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </div>

        <!-- Messages -->
        <div v-for="msg in store.messages" :key="msg.id" :class="['message', msg.role]">
          <div v-if="msg.role === 'assistant' || msg.role === 'system'" class="message-avatar">
            <div class="avatar-icon">
              <el-icon :size="16"><Cpu /></el-icon>
            </div>
          </div>
          <div class="message-bubble">
            <p class="message-text">{{ getMessageText(msg.content) }}</p>
            <span v-if="msg.options" class="message-options">
              <button
                v-for="opt in msg.options"
                :key="opt"
                class="option-chip"
                @click="handleSelectOption(opt)"
              >
                {{ opt }}
              </button>
            </span>
          </div>
        </div>

        <!-- Streaming Indicator -->
        <div v-if="store.isStreaming" class="message assistant">
          <div class="message-avatar">
            <div class="avatar-icon">
              <el-icon :size="16"><Cpu /></el-icon>
            </div>
          </div>
          <div class="message-bubble typing">
            <div class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="chat-input-area">
        <div class="input-wrapper">
          <input
            v-model="inputText"
            type="text"
            placeholder="输入你的学习需求..."
            @keydown.enter="handleSendInput"
            :disabled="store.isStreaming"
          />
          <button class="send-btn" @click="handleSendInput" :disabled="!inputText.trim() || store.isStreaming">
            <el-icon :size="18"><Promotion /></el-icon>
          </button>
        </div>
      </div>
    </main>

    <!-- Resource Panel (Right Side) -->
    <aside v-if="store.selectedNodeId" class="resource-panel">
      <div class="panel-header">
        <h3>学习资源</h3>
        <button class="apple-btn apple-btn-icon" @click="store.selectNode(null)">
          <el-icon :size="16"><Close /></el-icon>
        </button>
      </div>
      <div class="panel-tabs">
        <button
          v-for="tab in resourceTabs"
          :key="tab.key"
          :class="['tab-item', { active: activeResourceTab === tab.key }]"
          @click="activeResourceTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="panel-content">
        <PathTimeline
          v-if="activeResourceTab === 'path'"
          :path="store.path"
          :selected-node-id="store.selectedNodeId"
          @select-node="handleSelectPathNode"
        />
        <ResourceDoc v-if="activeResourceTab === 'doc'" :content="getResourceContent('document')" />
        <ResourceQuiz v-if="activeResourceTab === 'quiz'" :content="getResourceContent('quiz')" />
        <ResourceMindmap v-if="activeResourceTab === 'mindmap'" :content="getResourceContent('mindmap')" />
      </div>
    </aside>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { Plus, Cpu, Close, Delete, Promotion, Fold, Expand } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAgentStore } from '@/stores/agent'
import { sendMessage, fetchProfile, fetchLearningPathsByUser, fetchNodeResources, fetchSessions, fetchSessionMessages } from '@/api/agent'
import { formatTime } from '@/utils/format'
import ChatMessage from '@/components/agent/ChatMessage.vue'
import ChatInput from '@/components/agent/ChatInput.vue'
import PhaseIndicator from '@/components/agent/PhaseIndicator.vue'
import ProfilePanel from '@/components/agent/ProfilePanel.vue'
import PathTimeline from '@/components/agent/PathTimeline.vue'
import ResourceDoc from '@/components/agent/ResourceDoc.vue'
import ResourceQuiz from '@/components/agent/ResourceQuiz.vue'
import ResourceMindmap from '@/components/agent/ResourceMindmap.vue'

const store = useAgentStore()
const messagesRef = ref(null)
const activeResourceTab = ref('path')
const sessions = ref([])
const inputText = ref('')
const showHistory = ref(false)

const quickPrompts = [
  '我想学Java',
  '帮我复习面向对象',
  '我要准备Java面试',
  '帮我规划数据结构学习路径'
]

const resourceTabs = [
  { key: 'path', label: '路径' },
  { key: 'doc', label: '文档' },
  { key: 'quiz', label: '练习题' },
  { key: 'mindmap', label: '思维导图' }
]

// 解析消息内容，提取显示文本
function getMessageText(content) {
  if (!content) return ''
  
  // 尝试解析JSON
  try {
    const parsed = JSON.parse(content)
    // 如果是JSON对象，提取text字段
    if (parsed && typeof parsed === 'object') {
      return parsed.text || parsed.content || content
    }
  } catch (e) {
    // 不是JSON，直接返回原内容
  }
  
  return content
}

async function loadSessions() {
  try {
    const data = await fetchSessions(store.userId)
    sessions.value = data || []
  } catch (e) {
    console.error('加载会话列表失败:', e)
  }
}

async function loadSession(session) {
  if (store.isStreaming) return
  if (store.sessionId === session.id) return
  try {
    store.reset()
    store.sessionId = session.id
    const messages = await fetchSessionMessages(session.id)
    if (messages && messages.length > 0) {
      messages.forEach(msg => {
        store.addMessage({ role: msg.role, content: msg.content, phase: msg.phase })
      })
      const hasDone = messages.some(m => m.phase === 'done')
      if (hasDone) {
        store.currentPhase = 'done'
        await loadDoneData()
      }
    }
  } catch (e) {
    console.error('加载会话失败:', e)
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(() => store.messages.length, scrollToBottom)
watch(() => store.isStreaming, scrollToBottom)

async function handleSend(text) {
  await sendMessage(text)
  scrollToBottom()
  await loadSessions()
  if (store.currentPhase === 'done') {
    await loadDoneData()
  }
}

function handleSendInput() {
  if (!inputText.value.trim() || store.isStreaming) return
  handleSend(inputText.value)
  inputText.value = ''
}

function handleSelectOption(option) {
  handleSend(option)
}

function handleQuickPrompt(prompt) {
  handleSend(prompt)
}

function handleSelectPathNode(node) {
  store.selectNode(node.id)
}

async function loadDoneData() {
  try {
    const profile = await fetchProfile(store.userId)
    if (profile) store.setProfile(profile)
    const paths = await fetchLearningPathsByUser(store.userId)
    if (paths && paths.length > 0) {
      const latestPath = paths[0]
      store.setPath(latestPath)
      if (latestPath.nodes && latestPath.nodes.length > 0) {
        store.selectNode(latestPath.nodes[0].id)
        await loadNodeResources(latestPath.nodes[0].id)
      }
    }
  } catch (e) {
    console.error('加载完成数据失败:', e)
  }
}

async function loadNodeResources(nodeId) {
  try {
    const resources = await fetchNodeResources(nodeId)
    store.setNodeResources(resources || [])
  } catch (e) {
    console.error('加载节点资源失败:', e)
  }
}

function hasResourceType(type) {
  return store.nodeResources.some(r => r.resourceType === type || r.type === type)
}

function getResourceContent(type) {
  const resource = store.nodeResources.find(r => r.resourceType === type || r.type === type)
  return resource?.contentJson || ''
}

watch(() => store.selectedNodeId, async (nodeId) => {
  if (nodeId) await loadNodeResources(nodeId)
})

watch(() => store.currentPhase, async (newPhase, oldPhase) => {
  if (oldPhase === 'profiling' && newPhase !== 'profiling' && !store.profile) {
    const profile = await fetchProfile(store.userId)
    if (profile) store.setProfile(profile)
  }
})

async function loadRecentSession() {
  try {
    await loadSessions()
    if (sessions.value.length > 0) {
      const recentSession = sessions.value.find(s => s.status === 'active') || sessions.value[0]
      store.sessionId = recentSession.id
      const messages = await fetchSessionMessages(recentSession.id)
      if (messages && messages.length > 0) {
        store.messages = []
        messages.forEach(msg => {
          store.addMessage({ role: msg.role, content: msg.content, phase: msg.phase })
        })
        const hasDone = messages.some(m => m.phase === 'done')
        if (hasDone) {
          store.currentPhase = 'done'
          await loadDoneData()
        }
      }
    }
  } catch (e) {
    console.error('加载历史会话失败:', e)
  }
}

async function handleDeleteSession(session) {
  try {
    await ElMessageBox.confirm(
      `确定要删除对话 "${session.title || '对话 #' + session.id}" 吗？`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await fetch(`/api/agent/sessions/${session.id}`, { method: 'DELETE' })
    const json = await res.json()
    if (json.code === 200) {
      ElMessage.success('删除成功')
      if (store.sessionId === session.id) store.reset()
      await loadSessions()
    } else {
      ElMessage.error(json.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除会话失败:', e)
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

function handleNewChat() {
  store.reset()
  loadSessions()
}

onMounted(async () => {
  await loadRecentSession()
})
</script>

<style scoped>
/* ===== Layout ===== */
.agent-chat-page {
  height: 100vh;
  display: flex;
  background: var(--color-bg-grouped);
  position: relative;
}

/* ===== History Toggle Button ===== */
.history-toggle {
  position: fixed;
  left: 276px;
  top: 80px;
  z-index: 100;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--apple-text-secondary);
  transition: all 0.2s ease;
}

.history-toggle:hover {
  background: rgba(255, 255, 255, 0.95);
  color: var(--apple-text-primary);
  transform: scale(1.05);
}

.history-toggle.active {
  background: var(--apple-blue);
  color: white;
}

/* ===== Slide Transition ===== */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}

/* ===== Sidebar ===== */
.chat-sidebar {
  width: 280px;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 0.5px solid var(--apple-border);
  position: fixed;
  top: 0;
  left: 260px;
  bottom: 0;
  z-index: 90;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.05);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 0.5px solid var(--color-separator);
}

.sidebar-header h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.session-item:hover {
  background: var(--color-surface-hover);
}

.session-item.active {
  background: var(--color-accent-light);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.session-delete {
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
  transition: all 0.15s;
}

.session-item:hover .session-delete {
  opacity: 1;
}

.session-delete:hover {
  background: rgba(255, 59, 48, 0.12);
  color: var(--color-red);
}

.session-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 15px;
}

/* ===== Chat Main ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: var(--color-surface);
  border-bottom: 0.5px solid var(--color-separator);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.phase-badge {
  padding: 2px 10px;
  background: var(--color-accent-light);
  color: var(--color-accent);
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
}

/* ===== Messages ===== */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}

.welcome-icon {
  margin-bottom: 24px;
}

.welcome-section h2 {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 12px;
}

.welcome-section p {
  font-size: 17px;
  color: var(--color-text-secondary);
  max-width: 480px;
  line-height: 1.6;
  margin-bottom: 32px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.prompt-chip {
  padding: 10px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-separator);
  border-radius: 20px;
  font-size: 15px;
  color: var(--color-text-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.prompt-chip:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-light);
  color: var(--color-accent);
}

/* Message Bubbles */
.message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  max-width: 80%;
}

.message.user {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #007AFF, #5856D6);
  border-radius: 50%;
  color: white;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.5;
}

.message.user .message-bubble {
  background: var(--color-accent);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-bubble,
.message.system .message-bubble {
  background: var(--color-surface);
  color: var(--color-text-primary);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.message-text {
  white-space: pre-wrap;
}

.message-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.option-chip {
  padding: 6px 14px;
  background: var(--color-accent-light);
  border: 1px solid var(--color-accent);
  border-radius: 16px;
  font-size: 13px;
  color: var(--color-accent);
  cursor: pointer;
  transition: all 0.15s;
}

.option-chip:hover {
  background: var(--color-accent);
  color: white;
}

/* Typing Indicator */
.typing-dots {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  background: var(--color-text-tertiary);
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:last-child { animation-delay: 0.4s; }

@keyframes typing {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

/* ===== Input Area ===== */
.chat-input-area {
  padding: 16px 24px;
  background: var(--color-surface);
  border-top: 0.5px solid var(--color-separator);
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 8px 18px;
  background: var(--color-bg-secondary);
  border-radius: 24px;
  transition: all 0.2s;
}

.input-wrapper:focus-within {
  background: var(--color-surface);
  box-shadow: 0 0 0 2px var(--color-accent);
}

.input-wrapper input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 17px;
  color: var(--color-text-primary);
  outline: none;
}

.input-wrapper input::placeholder {
  color: var(--color-text-quaternary);
}

.send-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent);
  border: none;
  border-radius: 50%;
  color: white;
  cursor: pointer;
  transition: all 0.15s;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  background: var(--color-accent-hover);
}

/* ===== Resource Panel ===== */
.resource-panel {
  width: 360px;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-left: 0.5px solid var(--color-separator);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 0.5px solid var(--color-separator);
}

.panel-header h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.panel-tabs {
  display: flex;
  padding: 8px 12px;
  gap: 4px;
  border-bottom: 0.5px solid var(--color-separator);
}

.tab-item {
  flex: 1;
  padding: 8px 12px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.tab-item.active {
  background: var(--color-accent-light);
  color: var(--color-accent);
}

.tab-item:hover:not(.active) {
  background: var(--color-surface-hover);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* ===== Responsive ===== */
@media (max-width: 1024px) {
  .resource-panel {
    display: none;
  }
}

@media (max-width: 768px) {
  .chat-sidebar {
    display: none;
  }
  
  .chat-input-area {
    padding: 12px 16px;
  }
}
</style>
