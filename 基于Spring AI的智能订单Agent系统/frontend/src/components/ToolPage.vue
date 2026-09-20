 vue
  <script setup>
  import { ref } from 'vue'

  const question = ref('')
  const asking = ref(false)
  const answer = ref('')
  const toolCalls = ref([])   // [{ name, arguments, result, time }]

  // 把字符串尝试格式化成好看的 JSON（格式化失败就原样返回）
  function prettyJson(str) {
    try {
      return JSON.stringify(JSON.parse(str), null, 2)
    } catch {
      return str
    }
  }

  async function ask() {
    const q = question.value.trim()
    if (q === '' || asking.value) return

    asking.value = true
    answer.value = ''
    toolCalls.value = []

    try {
       const response = await fetch('/assistant/ask-tools?question=' + encodeURIComponent(q))
        if (!response.ok) throw new Error('请求失败，状态码 ' + response.status)
        const data = await response.json()

      answer.value = data.answer || ''
      toolCalls.value = data.toolCalls || []
    } catch (err) {
      answer.value = '出错了：' + err.message
    } finally {
      asking.value = false
    }
  }
  </script>

  <template>
    <div class="tool-page">
      <div class="tool-card">
        <h2 class="title">🔧 工具调用可视化</h2>
        <p class="subtitle">问一个需要查订单/库存的问题，看 AI 如何一步步调用工具</p>

        <!-- 输入区 -->
        <div class="ask-bar">
          <input
            v-model="question"
            type="text"
            placeholder="例如：帮我查一下 1 号订单的状态"
            @keyup.enter="ask"
          />
          <button :disabled="asking" @click="ask">{{ asking ? '思考中…' : '提问' }}</button>
        </div>

        <!-- 示例问题：点一下自动填入 -->
        <div class="samples">
          <span class="sample" @click="question = '查一下 1 号订单的状态'">查 1 号订单状态</span>
          <span class="sample" @click="question = '商品 1001 的库存还有多少'">查商品库存</span>
          <span class="sample" @click="question = '取消 1 号订单'">取消订单</span>
        </div>

        <!-- AI 回答 -->
        <div v-if="answer" class="answer">
          <div class="answer-label">🤖 AI 回答</div>
          <div class="answer-text">{{ answer }}</div>
        </div>

        <!-- 工具调用过程 -->
        <div v-if="toolCalls.length > 0" class="calls">
          <div class="calls-label">🛠️工具调用过程（{{ toolCalls.length }} 次）</div>
          <div v-for="(call, i) in toolCalls" :key="i" class="call-item">
            <div class="call-header">
              <span class="call-icon">🔧</span>
              <span class="call-name">{{ call.name }}</span>
              <span class="call-time">{{ call.time }}</span>
            </div>
            <div class="call-block">
              <div class="call-block-label">参数</div>
              <pre>{{ prettyJson(call.arguments) }}</pre>
            </div>
            <div class="call-block">
              <div class="call-block-label">返回结果</div>
              <pre>{{ prettyJson(call.result) }}</pre>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="!answer && !asking" class="empty">
          <div class="empty-icon">🛠</div>
          <p>输入问题后，AI 会在这里展示它调用了哪些工具</p>
        </div>
      </div>
    </div>
  </template>

  <style scoped>
  .tool-page {
    height: 100%;
    max-width: 860px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 16px;
    overflow-y: auto;
  }
  .tool-card {
    background: #fff;
    border-radius: 24px;
    padding: 28px;
    box-shadow: 0 20px 60px rgba(31, 45, 80, 0.14);
  }
  .title {
    margin: 0;
    font-size: 20px;
    color: #333;
  }
  .subtitle {
    margin: 6px 0 0;
    font-size: 13px;
    color: #a0a6b5;
  }

  .ask-bar {
    display: flex;
    gap: 10px;
    margin-top: 18px;
  }
  .ask-bar input {
    flex: 1;
    padding: 12px 18px;
    border: 1px solid #e5e8f0;
    border-radius: 24px;
    font-size: 15px;
    outline: none;
    background: #f7f8fc;
    transition: all 0.2s;
  }
  .ask-bar input:focus {
    border-color: #8b5cf6;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.12);
  }
  .ask-bar button {
    padding: 0 24px;
    border: none;
    border-radius: 24px;
    background: linear-gradient(135deg, #5b7cfa, #8b5cf6);
    color: #fff;
    font-size: 15px;
    cursor: pointer;
  }
  .ask-bar button:disabled {
    background: #d5d9e6;
    cursor: not-allowed;
  }

  .samples {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    margin-top: 12px;
  }
  .sample {
    padding: 6px 12px;
    border-radius: 16px;
    background: #f2f4f9;
    color: #666;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;
  }
  .sample:hover {
    background: #f5f3ff;
    color: #8b5cf6;
  }

  .answer {
    margin-top: 20px;
    padding: 16px 18px;
    border-radius: 14px;
    background: #f7f8fc;
  }
  .answer-label {
    font-size: 13px;
    color: #a0a6b5;
    margin-bottom: 6px;
  }
  .answer-text {
    font-size: 15px;
    color: #333;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .calls {
    margin-top: 20px;
  }
  .calls-label {
    font-size: 14px;
    font-weight: 600;
    color: #333;
    margin-bottom: 12px;
  }
  .call-item {
    border: 1px solid #eef0f5;
    border-left: 3px solid #8b5cf6;
    border-radius: 12px;
    padding: 14px 16px;
    margin-bottom: 12px;
  }
  .call-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }
  .call-name {
    font-weight: 600;
    color: #333;
    font-size: 14px;
    font-family: ui-monospace, Consolas, monospace;
  }
  .call-time {
    margin-left: auto;
    font-size: 12px;
    color: #a0a6b5;
  }
  .call-block {
    margin-bottom: 8px;
  }
  .call-block:last-child {
    margin-bottom: 0;
  }
  .call-block-label {
    font-size: 12px;
    color: #a0a6b5;
    margin-bottom: 4px;
  }
  .call-block pre {
    margin: 0;
    padding: 10px 12px;
    background: #f7f8fc;
    border-radius: 8px;
    font-family: ui-monospace, Consolas, monospace;
    font-size: 13px;
    color: #333;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 200px;
    overflow-y: auto;
  }

  .empty {
    margin-top: 30px;
    text-align: center;
    color: #a0a6b5;
  }
  .empty-icon {
    font-size: 40px;
  }
  .empty p {
    margin-top: 8px;
    font-size: 14px;
  }
  </style>