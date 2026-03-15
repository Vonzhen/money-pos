import { req, reqMixed } from '../index.js'

export default {
    list: (query) => req({
        url: '/gms/goods',
        method: 'GET',
        params: query
    }),
    add: (data) => {
        // 🌟 修复：对象结构拆解，文件流绝不进入 JSON
        const { picFile, ...payload } = data;
        return reqMixed({
            url: '/gms/goods',
            method: 'POST',
        }, {
            key: 'goods',
            jsonData: payload
        }, {
            pic: picFile
        });
    },
    edit: (data) => {
        // 🌟 修复：对象结构拆解，文件流绝不进入 JSON
        const { picFile, ...payload } = data;
        return reqMixed({
            url: '/gms/goods',
            method: 'PUT',
        }, {
            key: 'goods',
            jsonData: payload
        }, {
            pic: picFile
        });
    },
    del: (ids) => req({
        url: '/gms/goods',
        method: 'DELETE',
        data: ids,
    }),
    downloadTemplate: () => req({
        url: '/gms/goods/template',
        method: 'GET',
        responseType: 'blob'
    }),
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