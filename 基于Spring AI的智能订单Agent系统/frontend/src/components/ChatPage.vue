 vue
  <script setup>
  import { ref } from 'vue'

  const messages = ref([
    { role: 'ai', text: '你好，我是订单助手 👋 有什么可以帮你？', time: '14:20' },
    { role: 'user', text: '帮我查一下订单状态', time: '14:21' },
    { role: 'ai', text: '好的，请告诉我你的订单号～', time: '14:21' },
  ])

  const inputText = ref('')
  const isStreaming = ref(false)
  let controller = null

  function nowTime() {
    return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  async function sendMessage() {
    const text = inputText.value.trim()
    if (text === '' || isStreaming.value) return

    messages.value.push({ role: 'user', text, time: nowTime() })
    inputText.value = ''

    messages.value.push({ role: 'ai', text: '', time: nowTime() })
    const aiIndex = messages.value.length - 1

    isStreaming.value = true
    controller = new AbortController()

    try {
      const response = await fetch(
        '/assistant/ask/stream?question=' + encodeURIComponent(text),
        { signal: controller.signal }
      )

      if (!response.ok || !response.body) {
        throw new Error('请求失败，状态码 ' + response.status)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop()

        for (const line of lines) {
          const s = line.replace(/\r$/, '')
          if (s.startsWith('data:')) {
            const token = s.slice(5).replace(/^ /, '')
            if (token) {
              messages.value[aiIndex].text += token
            }
          }
        }
      }
    } catch (err) {
      if (err.name === 'AbortError') {
        if (messages.value[aiIndex].text === '') {
          messages.value[aiIndex].text = '（已停止）'
        }
      } else {
        messages.value[aiIndex].text = '出错了：' + err.message
      }
    } finally {
      isStreaming.value = false
      controller = null
    }
  }

  function stopStreaming() {
    if (controller) controller.abort()
  }
  </script>

  <template>
    <div class="chat-page">
      <header class="chat-header">
        <div class="header-avatar">🤖</div>
        <div class="header-info">
          <h1>智能订单助手</h1>
          <p class="status"><span class="dot"></span>在线 · 秒回</p>
        </div>
      </header>

      <main class="message-list">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message"
          :class="msg.role === 'user' ? 'message--user' : 'message--ai'"
        >
          <div class="avatar">{{ msg.role === 'user' ? '😊' : '🤖' }}</div>
          <div class="msg-body">
            <div class="bubble">
              <span v-if="msg.role === 'ai' && msg.text === ''" class="typing">正在输入…</span>
              <template v-else>
                {{ msg.text }}
                <span
                  v-if="msg.role === 'ai' && isStreaming && index === messages.length - 1"
                  class="cursor"
                >▍</span>
              </template>
            </div>
            <div class="time">{{ msg.time }}</div>
          </div>
        </div>
      </main>

      <footer class="input-bar">
        <input
          v-model="inputText"
          type="text"
          placeholder="输入消息，回车发送…"
          @keyup.enter="sendMessage"
        />
        <button v-if="!isStreaming" @click="sendMessage" title="发送">➤</button>
        <button v-else @click="stopStreaming" class="stop" title="停止">■</button>
      </footer>
    </div>
  </template>

  <style scoped>
  .chat-page {
    display: flex;
    flex-direction: column;
    height: 100%;       /* ★ 原来是 92vh，现在改成 100% 填满外壳 */
    max-width: 860px;
    margin: 0 auto;     /* ★ 原来是 4vh auto，现在改成 0 auto */
    background: #fff;
    border-radius: 24px;
    overflow: hidden;
    box-shadow: 0 20px 60px rgba(31, 45, 80, 0.14);
  }

  .chat-header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 18px 24px;
    background: linear-gradient(135deg, #5b7cfa, #8b5cf6);
    color: #fff;
  }
  .header-avatar {
    width: 42px;
    height: 42px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 12px;
  }
  .header-info h1 {
    font-size: 18px;
    font-weight: 600;
    margin: 0;
  }
  .status {
    font-size: 12px;
    opacity: 0.9;
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 2px;
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #5dff9d;
    display: inline-block;
    box-shadow: 0 0 0 3px rgba(93, 255, 157, 0.2);
  }

  .message-list {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: #f7f8fc;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
  .message-list::-webkit-scrollbar {
    width: 6px;
  }
  .message-list::-webkit-scrollbar-thumb {
    background: #d5d9e6;
    border-radius: 3px;
  }

  .message {
    display: flex;
    gap: 10px;
    align-items: flex-end;
  }
  .message--user {
    flex-direction: row-reverse;
  }

  .avatar {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    background: #fff;
    box-shadow: 0 2px 6px rgba(31, 45, 80, 0.08);
  }

  .msg-body {
    max-width: 70%;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .message--user .msg-body {
    align-items: flex-end;
  }

  .bubble {
    padding: 11px 15px;
    border-radius: 16px;
    line-height: 1.55;
    font-size: 15px;
    word-break: break-word;
  }
  .message--ai .bubble {
    background: #fff;
    color: #333;
    border-bottom-left-radius: 6px;
    box-shadow: 0 2px 8px rgba(31, 45, 80, 0.06);
  }
  .message--user .bubble {
    background: linear-gradient(135deg, #5b7cfa, #8b5cf6);
    color: #fff;
    border-bottom-right-radius: 6px;
    box-shadow: 0 4px 12px rgba(91, 124, 250, 0.35);
  }

  .typing {
    color: #a0a6b5;
  }
  .cursor {
    color: #8b5cf6;
    animation: blink 1s step-end infinite;
  }
  @keyframes blink {
    50% { opacity: 0; }
  }

  .time {
    font-size: 11px;
    color: #a0a6b5;
    padding: 0 4px;
  }

  .input-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 16px 20px;
    background: #fff;
    border-top: 1px solid #eef0f5;
  }
  .input-bar input {
    flex: 1;
    padding: 12px 18px;
    border: 1px solid #e5e8f0;
    border-radius: 24px;
    font-size: 15px;
    outline: none;
    background: #f7f8fc;
    transition: all 0.2s;
  }
  .input-bar input:focus {
    border-color: #8b5cf6;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.12);
  }
  .input-bar button {
    width: 44px;
    height: 44px;
    border: none;
    border-radius: 50%;
    background: linear-gradient(135deg, #5b7cfa, #8b5cf6);
    color: #fff;
    font-size: 16px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s;
  }
  .input-bar button:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(91, 124, 250, 0.4);
  }
  .input-bar button:active {
    transform: translateY(0);
  }
  .input-bar .stop {
    background: #f56c6c;
  }
  .input-bar .stop:hover {
    box-shadow: 0 6px 16px rgba(245, 108, 108, 0.4);
  }
  </style>