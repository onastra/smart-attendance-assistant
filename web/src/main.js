import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// Element Plus 统一中文语言包，组件库版本由 package.json 锁定，不再混用 ElementUI
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
