import { applyEdits } from 'jsonc-parser'
import { jsonSourceTree } from './json-source.mjs'

export function applyHttpFieldRules(payload, now = new Date()) {
  const rules = (payload.fieldRules || []).filter((rule) => rule?.enabled === true)
  if (!rules.length) return payload
  const fail = (rule, message) => { throw new Error(`欄位規則「${rule?.name || '未命名規則'}」：${message}`) }
  if (payload.bodyType !== 'json') fail(null, 'HTTP 欄位規則只能套用 JSON Body。')
  const body = payload.body || '{}'
  const root = jsonSourceTree(body)
  if (!root) fail(null, /{{[^{}]*}}/.test(body) ? 'Body 含未解析變數，請先套用目前 Environment。' : 'HTTP Body 不是有效的 JSON。')
  if (root.type === 'array') fail(null, 'HTTP 欄位規則第一版不支援 JSON array。')
  if (root.type !== 'object') fail(null, 'HTTP 欄位規則需要 JSON object Body。')
  const ids = new Set()
  const paths = new Set()
  const edits = []
  for (const rule of rules) {
    if (!rule.id?.trim() || ids.has(rule.id)) fail(rule, '規則識別碼不可空白或重複。')
    ids.add(rule.id)
    if (!rule.name?.trim()) fail(rule, '規則名稱不可空白。')
    if (!/^#[0-9a-fA-F]{6}$/.test(rule.color || '')) fail(rule, '顏色必須為 #RRGGBB 格式。')
    if (rule.targetType !== 'HTTP_JSON_VALUE') fail(rule, '欄位規則不適用於目前 Request 類型。')
    const action = (rule.action ?? 'NONE').trim().toUpperCase()
    if (!['NONE', 'INCREMENT', 'TODAY_DD'].includes(action)) fail(rule, '不支援的欄位規則動作。')
    const path = rule.path || ''
    if (!path.startsWith('/') || path === '/' || /~(?:[^01]|$)/.test(path)) fail(rule, 'HTTP JSON Pointer 格式錯誤。')
    if (paths.has(path)) fail(rule, '同一 JSON 欄位不可設定多條啟用規則。')
    paths.add(path)
    let target = root
    for (const segment of path.slice(1).split('/').map((key) => key.replaceAll('~1', '/').replaceAll('~0', '~'))) {
      if (target.type === 'array') fail(rule, 'HTTP 欄位規則第一版不支援 JSON array。')
      if (target.type !== 'object') fail(rule, `找不到 HTTP JSON Pointer 指定的欄位：${path}`)
      target = target.children.filter((property) => property.children[0].value === segment).at(-1)?.children[1]
      if (!target) fail(rule, `找不到 HTTP JSON Pointer 指定的欄位：${path}`)
    }
    if (action === 'NONE') {
      if (target.type === 'object' || target.type === 'array') fail(rule, 'HTTP 欄位規則只能指向單一值。')
      continue
    }
    if (target.type === 'string' && /{{[^{}]*}}/.test(target.value)) fail(rule, '目標欄位含未解析變數，請先套用目前 Environment。')
    let content
    if (action === 'TODAY_DD') {
      if (target.type !== 'string') fail(rule, '今日 DD 只能套用 JSON 字串欄位。')
      content = JSON.stringify(new Intl.DateTimeFormat('en', { timeZone: 'Asia/Taipei', day: '2-digit' }).format(now))
    } else if (target.type === 'string') {
      if (!/^\d+$/.test(target.value)) fail(rule, '遞增欄位必須是十進位數字。')
      const next = String(BigInt(target.value) + 1n)
      if (next.length > target.value.length) fail(rule, '遞增後超過原欄位長度。')
      content = JSON.stringify(next.padStart(target.value.length, '0'))
    } else {
      const raw = body.slice(target.offset, target.offset + target.length)
      if (target.type !== 'number' || !/^-?(?:0|[1-9]\d*)$/.test(raw)) fail(rule, '遞增只能套用 JSON 字串數字或整數欄位。')
      content = String(BigInt(raw) + 1n)
    }
    edits.push({ offset: target.offset, length: target.length, content })
  }
  return { ...payload, body: applyEdits(body, edits) }
}
