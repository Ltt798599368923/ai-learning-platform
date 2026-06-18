<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar glass-bg" :class="{ collapsed: isCollapsed }">
      <!-- Logo -->
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <span class="logo-text" v-show="!isCollapsed">AI智训平台</span>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-text" v-show="!isCollapsed">{{ item.label }}</span>
          <span class="nav-badge" v-if="item.badge">{{ item.badge }}</span>
        </router-link>
      </nav>

      <!-- 底部 -->
      <div class="sidebar-footer">
        <button class="collapse-btn" @click="isCollapsed = !isCollapsed">
          <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
        </button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-wrapper">
      <!-- 顶部导航 -->
      <header class="header glass-bg">
        <div class="header-left">
          <!-- 面包屑 -->
          <div class="breadcrumb">
            <span class="breadcrumb-item" @click="$router.push('/home')">首页</span>
            <span class="breadcrumb-separator" v-if="currentPage">/</span>
            <span class="breadcrumb-item active" v-if="currentPage">{{ currentPage }}</span>
          </div>
        </div>

        <div class="header-right">
          <!-- 搜索 -->
          <div class="search-box">
            <el-icon><Search /></el-icon>
            <input type="text" placeholder="搜索..." class="search-input" />
          </div>

          <!-- 通知 -->
          <button class="header-btn">
            <el-icon><Bell /></el-icon>
          </button>

          <!-- 用户 -->
          <div class="user-info">
            <div class="user-avatar">Y</div>
            <span class="user-name">用户</span>
          </div>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeFilled,
  ChatDotRound,
  Guide,
  VideoPlay,
  Setting,
  Fold,
  Expand,
  Search,
  Bell
} from '@element-plus/icons-vue'

const route = useRoute()
const isCollapsed = ref(false)

const menuItems = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/agent-chat', label: 'AI助手', icon: ChatDotRound },
  { path: '/learning-paths', label: '学习路径', icon: Guide },
  { path: '/videos', label: '视频学习', icon: VideoPlay },
  { path: '/admin', label: '管理后台', icon: Setting }
]

const currentPage = computed(() => {
  const item = menuItems.find(m => route.path.startsWith(m.path))
  return item?.label || ''
})

function isActive(path) {
  return route.path.startsWith(path)
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--apple-bg);
}

/* ========== 侧边栏 ========== */
.sidebar {
  width: 260px;
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-right: 1px solid var(--apple-border);
}

.sidebar.collapsed {
  width: 80px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px;
  border-bottom: 1px solid var(--apple-border);
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #0071e3, #af52de);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-icon svg {
  width: 20px;
  height: 20px;
  color: white;
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--apple-radius);
  color: var(--apple-text-secondary);
  text-decoration: none;
  transition: all 0.2s ease;
  position: relative;
}

.nav-item:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--apple-text-primary);
}

.nav-item.active {
  background: var(--apple-blue);
  color: white;
}

.nav-item.active .nav-icon {
  color: white;
}

.nav-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.nav-text {
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
}

.nav-badge {
  margin-left: auto;
  background: var(--apple-red);
  color: white;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 980px;
  font-weight: 600;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--apple-border);
}

.collapse-btn {
  width: 100%;
  padding: 10px;
  border: none;
  background: rgba(0, 0, 0, 0.04);
  border-radius: var(--apple-radius);
  cursor: pointer;
  color: var(--apple-text-secondary);
  transition: all 0.2s ease;
}

.collapse-btn:hover {
  background: rgba(0, 0, 0, 0.08);
  color: var(--apple-text-primary);
}

/* ========== 主内容区 ========== */
.main-wrapper {
  flex: 1;
  margin-left: 260px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar.collapsed + .main-wrapper {
  margin-left: 80px;
}

/* ========== 顶部导航 ========== */
.header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.breadcrumb-item {
  color: var(--apple-text-secondary);
  cursor: pointer;
  transition: color 0.2s ease;
}

.breadcrumb-item:hover {
  color: var(--apple-text-primary);
}

.breadcrumb-item.active {
  color: var(--apple-text-primary);
  font-weight: 500;
}

.breadcrumb-separator {
  color: var(--apple-text-tertiary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 980px;
  transition: all 0.2s ease;
}

.search-box:focus-within {
  background: white;
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.1);
}

.search-box .el-icon {
  color: var(--apple-text-tertiary);
  font-size: 16px;
}

.search-input {
  border: none;
  background: transparent;
  font-size: 14px;
  outline: none;
  width: 200px;
  color: var(--apple-text-primary);
}

.search-input::placeholder {
  color: var(--apple-text-tertiary);
}

.header-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--apple-text-secondary);
  transition: all 0.2s ease;
}

.header-btn:hover {
  background: rgba(0, 0, 0, 0.08);
  color: var(--apple-text-primary);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 980px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-info:hover {
  background: rgba(0, 0, 0, 0.08);
}

.user-avatar {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #0071e3, #af52de);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--apple-text-primary);
}

/* ========== 主内容 ========== */
.main-content {
  flex: 1;
  padding: 32px;
}

/* ========== 过渡动画 ========== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .sidebar {
    width: 80px;
  }
  
  .sidebar .logo-text,
  .sidebar .nav-text {
    display: none;
  }
  
  .main-wrapper {
    margin-left: 80px;
  }
  
  .search-input {
    width: 150px;
  }
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
  }
  
  .main-wrapper {
    margin-left: 0;
  }
  
  .header {
    padding: 0 16px;
  }
  
  .main-content {
    padding: 16px;
  }
}
</style>
