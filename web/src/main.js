import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIcons from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

/*
 * 全局注册 Element Plus 图标。
 * 必须显式注册：ElementPlus 插件只包含 UI 组件，不含图标集合，
 * 而菜单要依据路由 meta.icon 动态渲染 <component :is>，无法逐个静态引入。
 */
for (const [name, component] of Object.entries(ElementPlusIcons)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
// Element Plus 统一中文语言包，组件库版本由 package.json 锁定，不再混用 ElementUI
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
