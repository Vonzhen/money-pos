<template>
    <div class="money-uploader">
        <el-upload
            :action="uploadUrl"
            :headers="headers"
            :list-type="listType"
            :limit="limit"
            :accept="accept"
            :file-list="fileList"
            :before-upload="handleBeforeUpload"
            :http-request="customUpload"
            :on-remove="handleRemove"
            :on-preview="handlePreview"
            :on-exceed="handleExceed"
            class="money-upload-wrapper"
            :class="{ 'hide-upload-btn': fileList.length >= limit }"
        >
            <el-icon v-if="listType === 'picture-card'"><Plus /></el-icon>
            <el-button v-else type="primary"><el-icon class="mr-1"><Upload /></el-icon>点击上传</el-button>

            <template #tip v-if="showTip">
                <div class="el-upload__tip text-xs text-gray-400 mt-1 leading-tight">
                    请上传 <span class="text-blue-500 font-bold">{{ accept }}</span> 格式文件，
                    大小不能超过 <span class="text-red-500 font-bold">{{ maxSize }}MB</span>
                </div>
            </template>
        </el-upload>

        <el-dialog v-model="dialogVisible" title="图片预览" width="500px" append-to-body destroy-on-close>
            <img class="w-full rounded shadow-sm" :src="dialogImageUrl" alt="Preview" />
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/index.js'

const props = defineProps({
    modelValue: { type: [String, Array], default: '' },
    limit: { type: Number, default: 1 },
    maxSize: { type: Number, default: 5 },
    accept: { type: String, default: '.jpg,.jpeg,.png,.gif,.webp' },
    listType: { type: String, default: 'picture-card' },
    showTip: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue'])
const userStore = useUserStore()

// 🌟 核心修复 1：把地址设为 '#'，彻底阻止组件在 Electron(file://) 环境下乱发网络请求！
const uploadUrl = ref('#')
const headers = computed(() => ({ Authorization: 'Bearer ' + userStore.token }))

const fileList = ref([])
const dialogVisible = ref(false)
const dialogImageUrl = ref('')

watch(() => props.modelValue, (newVal) => {
    if (!newVal) {
        fileList.value = [];
        return;
    }
    if (Array.isArray(newVal)) {
        fileList.value = newVal.map((url, index) => ({ name: `file_${index}`, url }));
    } else {
        fileList.value = newVal.split(',').filter(Boolean).map((url, index) => ({ name: `file_${index}`, url }));
    }
}, { immediate: true })

const syncModelValue = () => {
    const urls = fileList.value.map(item => item.url).filter(Boolean);
    if (props.limit === 1) {
        emit('update:modelValue', urls.length > 0 ? urls[0] : '');
    } else {
        emit('update:modelValue', urls.join(','));
    }
}

const handleBeforeUpload = (file) => {
    const isSizeOk = file.size / 1024 / 1024 < props.maxSize;
    if (!isSizeOk) {
        ElMessage.error(`上传文件大小不能超过 ${props.maxSize}MB!`);
        return false;
    }
    return true;
}

// 🌟 恢复您最初的完美逻辑：纯前端模拟上传 (生成本地 Blob URL预览)
const customUpload = (options) => {
    const { file, onSuccess, onError } = options;

    setTimeout(() => {
        try {
            const localBlobUrl = URL.createObjectURL(file);

            // 欺骗组件，让它以为上传成功了，以显示缩略图
            onSuccess({ code: 200, data: localBlobUrl }, file);

            const targetFile = fileList.value.find(f => f.uid === file.uid);
            if (targetFile) {
                targetFile.url = localBlobUrl;
                targetFile.raw = file; // 🌟 核心修复 2：必须保留真实的二进制文件(raw)，供父组件(商品页面)点击保存时打包发送给后端！
            } else {
                fileList.value.push({ name: file.name, url: localBlobUrl, uid: file.uid, raw: file });
            }

            syncModelValue();
            ElMessage.success('图片加载成功！');

        } catch (e) {
            onError(e);
            ElMessage.error('图片加载失败');
        }
    }, 100);
}

const handleRemove = (uploadFile, uploadFiles) => {
    if (uploadFile.url && uploadFile.url.startsWith('blob:')) {
        URL.revokeObjectURL(uploadFile.url);
    }
    fileList.value = uploadFiles;
    syncModelValue();
}

const handlePreview = (uploadFile) => {
    if (props.listType === 'picture-card') {
        dialogImageUrl.value = uploadFile.url;
        dialogVisible.value = true;
    } else {
        window.open(uploadFile.url);
    }
}

const handleExceed = () => {
    ElMessage.warning(`最多只能上传 ${props.limit} 个文件，请先删除已有文件。`);
}
</script>

<style scoped>
.hide-upload-btn :deep(.el-upload--picture-card) {
    display: none !important;
}
.money-uploader :deep(.el-upload-list__item) {
    transition: all 0.3s;
}
</style>