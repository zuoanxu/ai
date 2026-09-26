  vue
   <script setup>
   import { ref } from 'vue'
   import ChatPage from './components/ChatPage.vue'
   import UploadPage from './components/UploadPage.vue'
   import ToolPage from './components/ToolPage.vue'

   const currentTab = ref('chat')   // 当前页面：'chat' 对话 / 'upload' 上传 / 'tool' 工具
   </script>

   <template>
     <div class="app-shell">
       <nav class="nav">
         <button :class="{ active: currentTab === 'chat' }" @click="currentTab = 'chat'">
           💬 智能对话
         </button>
         <button :class="{ active: currentTab === 'upload' }" @click="currentTab = 'upload'">
           📄 文档上传
         </button>
         <button :class="{ active: currentTab === 'tool' }" @click="currentTab = 'tool'">
           🔧 工具调用
         </button>
       </nav>

       <div class="page">
         <KeepAlive>
           <ChatPage v-if="currentTab === 'chat'" />
           <UploadPage v-else-if="currentTab === 'upload'" />
           <ToolPage v-else />
         </KeepAlive>
       </div>
     </div>
   </template>

   <style scoped>
   .app-shell {
     height: 100vh;
     display: flex;
     flex-direction: column;
   }
   .nav {
     display: flex;
     justify-content: center;
     gap: 8px;
     padding: 12px 20px;
     background: #fff;
     border-bottom: 1px solid #eef0f5;
   }
   .nav button {
     padding: 8px 22px;
     border: none;
     border-radius: 20px;
     background: #f2f4f9;
     color: #666;
     font-size: 14px;
     cursor: pointer;
     transition: all 0.2s;
   }
   .nav button.active {
     background: linear-gradient(135deg, #5b7cfa, #8b5cf6);
     color: #fff;
   }
   .page {
     flex: 1;
     padding: 20px;
     overflow: hidden;
   }
   </style>