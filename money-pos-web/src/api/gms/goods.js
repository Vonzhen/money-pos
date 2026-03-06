import { req, reqMixed } from '../index.js'

export default {
    list: (query) => req({
        url: '/gms/goods',
        method: 'GET',
        params: query
    }),
    add: (data) => reqMixed({
        url: '/gms/goods',
        method: 'POST',
    }, {
        key: 'goods',
        jsonData: data
    }, {
        pic: data.picFile
    }),
    edit: (data) => reqMixed({
        url: '/gms/goods',
        method: 'PUT',
    }, {
        key: 'goods',
        jsonData: data
    }, {
        pic: data.picFile
    }),
    del: (ids) => req({
        url: '/gms/goods',
        method: 'DELETE',
        data: ids,
    }),
    // 🌟 下载商品导入模板
    downloadTemplate: () => req({
        url: '/gms/goods/template',
        method: 'GET',
        responseType: 'blob' // 必须是 blob 防乱码
    }),
    // POS 专属全能搜索
        posSearch: (keyword) => req({
            url: '/gms/goods/pos-search',
            method: 'GET',
            params: { keyword }
        })
}

export function importGoods(file) {
    const formData = new FormData()
    formData.append('file', file)
    return req({
        url: '/gms/goods/import',
        method: 'post',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}