 vue
  <script setup>
  import { ref } from 'vue'

  const fileInput = ref(null)      // 隐藏的文件选择框
  const selectedFile = ref(null)   // 当前选中的文件
  const uploading = ref(false)     // 是否正在上传
  const result = ref(null)         // 最近一次上传结果（成功/失败提示）
  const fileList = ref([])         // 已上传成功的文件列表

  // 允许的文件类型（和后端白名单保持一致）
  const allowedTypes = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx', '.txt', '.md']

  // 点击选择区时，触发隐藏的 input
  function triggerSelect() {
    fileInput.value.click()
  }

  // 统一的"设置文件"逻辑（点击选择 和 拖拽 都走这里）
  function setFile(file) {
    const name = file.name.toLowerCase()
    const ok = allowedTypes.some(ext => name.endsWith(ext))
    if (!ok) {
      result.value = { success: false, message: '不支持的文件类型：' + file.name }
      selectedFile.value = null
      return
    }
    selectedFile.value = file
    result.value = null
  }

  // 点击选择文件后触发
  function onFileChange(e) {
    if (e.target.files[0]) setFile(e.target.files[0])
  }

  // 拖拽文件进来后触发
  function onDrop(e) {
    if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0])
  }

  // 把字节数格式化成好读的大小
  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  }

  // 上传
  async function upload() {
    if (!selectedFile.value || uploading.value) return

    uploading.value = true
    result.value = null

    try {
      // 用 FormData 装文件，字段名必须是 file（和后端 @RequestParam("file") 对应）
      const formData = new FormData()
      formData.append('file', selectedFile.value)

      const response = await fetch('/assistant/upload', {
        method: 'POST',
        body: formData,
        // ★ 千万别手动写 Content-Type！浏览器会自动带上 multipart/form-data 和 boundary
      })
       if (!response.ok) throw new Error('上传失败，状态码 ' + response.status)
        const data = await response.json()

      if (data.success) {
        result.value = data
        fileList.value.unshift(data)   // 成功的文件插到列表最前面
        selectedFile.value = null      // 清空当前选择
        fileInput.value.value = ''     // 清空 input，方便再次选同一个文件
      } else {
        result.value = data            // 后端返回的失败信息，直接显示
      }
    } catch (err) {
      result.value = { success: false, message: '网络错误：' + err.message }
    } finally {
      uploading.value = false
    }
  }
  </script>

  <template>
    <div class="upload-page">
      <div class="upload-card">
        <h2 class="title">📄 上传资料到知识库</h2>
        <p class="subtitle">支持 PDF / Word / Excel / PPT / TXT / Markdown</p>

        <!-- 隐藏的文件选择框（真正负责弹窗选文件） -->
        <input
          ref="fileInput"
          type="file"
          :accept="allowedTypes.join(',')"
          style="display: none"
          @change="onFileChange"
        />

        <!-- 选择区：点一下选文件，或把文件拖进来 -->
        <div
          class="drop-zone"
          @click="triggerSelect"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <div v-if="!selectedFile" class="empty">
            <div class="icon">📂</div>
            <p>点击选择文件，或把文件拖到这里</p>
          </div>
          <div v-else class="file-info">
            <div class="file-icon">📄</div>
            <div>
              <div class="file-name">{{ selectedFile.name }}</div>
              <div class="file-size">{{ formatSize(selectedFile.size) }}</div>
            </div>
          </div>
        </div>

        <!-- 上传按钮 -->
        <button class="upload-btn" :disabled="!selectedFile || uploading" @click="upload">
          <span v-if="uploading">⏳  正在入库…</span>
          <span v-else-if="!selectedFile">请先选择文件</span>
          <span v-else>🚀 上传入库</span>
        </button>

        <!-- 结果提示 -->
        <div v-if="result" class="result" :class="result.success ? 'ok' : 'fail'">
          <template v-if="result.success">
            ✅  上传成功：{{ result.filename }}，切成 {{ result.chunks }} 个片段存入知识库
          </template>
          <template v-else>
            ❌  {{ result.message }}
          </template>
        </div>
      </div>

      <!-- 已上传文件列表 -->
      <div class="history" v-if="fileList.length > 0">
        <h3>已入库文件</h3>
        <div v-for="(f, i) in fileList" :key="i" class="history-item">
          <span class="h-name">📄 {{ f.filename }}</span>
          <span class="h-meta">{{ f.chunks }} 片段 · {{ formatSize(f.size) }}</span>
        </div>
      </div>
    </div>
  </template>

  <style scoped>
  .upload-page {
    height: 100%;
    max-width: 860px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 16px;
    overflow-y: auto;
  }

  .upload-card {
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

  /* 选择区：虚线框 */
  .drop-zone {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 160px;
    margin-top: 18px;
    padding: 20px;
    border: 2px dashed #d5d9e6;
    border-radius: 16px;
    background: #fafbfd;
    cursor: pointer;
    text-align: center;
    transition: all 0.2s;
  }
  .drop-zone:hover {
    border-color: #8b5cf6;
    background: #f5f3ff;
  }
  .empty .icon {
    font-size: 40px;
  }
  .empty p {
    margin-top: 8px;
    font-size: 14px;
    color: #a0a6b5;
  }
  .file-info {
    display: flex;
    align-items: center;
    gap: 14px;
  }
  .file-icon {
    font-size: 40px;
  }
  .file-name {
    font-weight: 600;
    color: #333;
    word-break: break-all;
  }
  .file-size {
    margin-top: 4px;
    font-size: 13px;
    color: #a0a6b5;
  }

  /* 上传按钮 */
  .upload-btn {
    width: 100%;
    margin-top: 18px;
    padding: 12px;
    border: none;
    border-radius: 12px;
    background: linear-gradient(135deg, #5b7cfa, #8b5cf6);
    color: #fff;
    font-size: 15px;
    cursor: pointer;
    transition: all 0.2s;
  }
  .upload-btn:hover:not(:disabled) {
    box-shadow: 0 6px 16px rgba(91, 124, 250, 0.4);
  }
  .upload-btn:disabled {
    background: #d5d9e6;
    cursor: not-allowed;
  }

  /* 结果提示 */
  .result {
    margin-top: 18px;
    padding: 12px 16px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.6;
  }
  .result.ok {
    background: #f0faf4;
    color: #2f9e44;
  }
  .result.fail {
    background: #fff1f0;
    color: #e03131;
  }

  /* 已上传列表 */
  .history {
    background: #fff;
    border-radius: 24px;
    padding: 24px 28px;
    box-shadow: 0 20px 60px rgba(31, 45, 80, 0.14);
  }
  .history h3 {
    margin: 0 0 8px;
    font-size: 16px;
    color: #333;
  }
  .history-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 0;
    border-bottom: 1px solid #f2f4f9;
  }
  .history-item:last-child {
    border-bottom: none;
  }
  .h-name {
    font-size: 14px;
    color: #333;
  }
  .h-meta {
    font-size: 13px;
    color: #a0a6b5;
  }
  </style>