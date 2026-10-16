import { applyEdits } from 'jsonc-parser'
import { jsonSourceTree } from './json-source.mjs'

function pointerNode(root, pointer) {
  let node = root
  for (const key of pointer.slice(1).split('/').map((part) => part.replaceAll('~1', '/').replaceAll('~0', '~'))) {
    if (node?.type !== 'object') return undefined
    node = node.children.filter((property) => property.children[0].value === key).at(-1)?.children[1]
  }
  return node
}

export function mergePreparedValues(source, prepared, pointers) {
  const originalTree = jsonSourceTree(source)
  const preparedTree = jsonSourceTree(prepared)
  if (!originalTree && /^\s*{{[^{}]+}}\s*$/.test(source)) return prepared
  if (!originalTree || !preparedTree) throw new Error('無法更新欄位：Body 必須是有效 JSON。')
  const edits = [...new Set(pointers)].map((pointer) => {
    const original = pointerNode(originalTree, pointer)
    const result = pointerNode(preparedTree, pointer)
    if (!original || !result) throw new Error(`無法更新欄位：找不到 JSON Pointer ${pointer}`)
    return { offset: original.offset, length: original.length, content: prepared.slice(result.offset, result.offset + result.length) }
  })
  return applyEdits(source, edits)
}

export async function prepareFieldRuleDispatch({ type, payload, sourceBody, increment = 1, prepare }) {
  const rules = (type === 'GRPC_BUR' ? payload.settings?.fieldRules : payload.fieldRules) || []
  const changingRules = rules.filter((rule) => rule?.enabled === true && rule.action !== 'NONE')
  if (!changingRules.length) return { payload, executionPayload: payload, changed: false }
  if (!Number.isInteger(increment) || increment < 1 || increment > 2147483647) throw new Error('預留筆數必須為有效正整數。')
  const appliedRules = rules.map((rule) => rule?.enabled ? { ...rule, action: 'NONE' } : rule)
  if (type === 'GRPC_BUR') {
    const preview = await prepare('/api/grpc-bur/preview', { ...payload, basicLabelSequenceIncrement: increment })
    if (typeof preview.effectiveBasicLabel !== 'string') throw new Error('未取得準備完成的 Basic Label。')
    return { payload, changed: true, basicLabel: preview.effectiveBasicLabel, preview,
      executionPayload: { ...payload, basicLabel: preview.effectiveBasicLabel, basicLabelSequenceIncrement: null,
        settings: { ...payload.settings, fieldRules: appliedRules } } }
  }
  const response = await prepare(type === 'GRPC' ? '/api/grpc/prepare' : '/api/http/prepare',
    { ...payload, fieldRuleIncrement: increment })
  if (typeof response.body !== 'string') throw new Error('未取得準備完成的 Request Body。')
  const pointers = type === 'GRPC' ? ['/payload/data'] : changingRules.map((rule) => rule.path)
  return { payload, changed: true, body: mergePreparedValues(sourceBody, response.body, pointers),
    executionPayload: { ...payload, body: response.body, fieldRules: appliedRules, fieldRuleIncrement: null,
      ...(type === 'HTTP' ? { sourceFieldRules: rules } : {}) } }
}
