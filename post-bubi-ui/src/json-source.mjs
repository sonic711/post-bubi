import { applyEdits, format, parseTree } from 'jsonc-parser'

export function jsonSourceTree(text) {
  const errors = []
  const root = parseTree(text, errors, { disallowComments: true, allowTrailingComma: false })
  return errors.length ? undefined : root
}

export function formatJsonSource(text) {
  const source = text.trim()
  if (!source) return ''
  if (!jsonSourceTree(source)) throw new Error('JSON 格式錯誤，無法自動排版')
  return `${applyEdits(source, format(source, undefined, { insertSpaces: true, tabSize: 2, eol: '\n' }))}\n`
}

// Render source tokens rather than serialized values: textarea and overlay must share exact offsets.
export function renderJsonSource(text, { escape, literal, key }) {
  const root = jsonSourceTree(text)
  if (!root) return null
  const tokens = []
  const visit = (node, pointer) => {
    if (node.type === 'object') {
      for (const property of node.children || []) {
        const [name, value] = property.children
        tokens.push({ ...name, html: key(text.slice(name.offset, name.offset + name.length)) })
        visit(value, `${pointer}/${name.value.replaceAll('~', '~0').replaceAll('/', '~1')}`)
      }
    } else if (node.type === 'array') {
      node.children.forEach((child, index) => visit(child, `${pointer}/${index}`))
    } else {
      tokens.push({ ...node, html: literal(text.slice(node.offset, node.offset + node.length), node, pointer) })
    }
  }
  visit(root, '')
  let cursor = 0
  let result = ''
  for (const token of tokens) {
    result += escape(text.slice(cursor, token.offset)) + token.html
    cursor = token.offset + token.length
  }
  return result + escape(text.slice(cursor))
}

export function utf8SourceRanges(raw, rules) {
  const value = JSON.parse(raw)
  if (typeof value !== 'string') return []
  const rawOffsets = new Map([[0, 1]])
  let decodedOffset = 0
  for (let offset = 1; offset < raw.length - 1;) {
    offset += raw[offset] === '\\' ? (raw[offset + 1] === 'u' ? 6 : 2) : 1
    rawOffsets.set(++decodedOffset, offset)
  }
  const boundaries = new Map([[0, 0]])
  let bytes = 0
  let characters = 0
  const encoder = new TextEncoder()
  for (const character of value) {
    bytes += encoder.encode(character).length
    characters += character.length
    boundaries.set(bytes, characters)
  }
  const result = []
  let endOfPrevious = 1
  for (const rule of [...rules].sort((left, right) => left.start - right.start)) {
    const start = rawOffsets.get(boundaries.get(Number(rule.start) - 1))
    const end = rawOffsets.get(boundaries.get(Number(rule.end)))
    if (start === undefined || end === undefined || start < endOfPrevious || end <= start) continue
    result.push({ rule, start, end })
    endOfPrevious = end
  }
  return result
}
