export function buildCurlCommand(payload, shell) {
  const parts = [
    `--request ${quoteCurlArgument(payload.method || 'GET', shell)}`,
    `--url ${quoteCurlArgument(buildCurlUrl(payload.url, payload.params), shell)}`,
  ]

  for (const header of payload.headers || []) {
    if (header?.enabled === false || !header?.name) {
      continue
    }
    parts.push(`--header ${quoteCurlArgument(`${header.name}: ${header.value || ''}`, shell)}`)
  }

  if (payload.followRedirects) {
    parts.push('--location')
  }
  if (payload.ignoreSslVerification) {
    parts.push('--insecure')
  }
  parts.push(`--max-time ${formatCurlTimeout(payload.timeoutMillis)}`)

  if (payload.bodyType === 'form-data') {
    for (const part of payload.formData || []) {
      if (!part?.name) {
        continue
      }
      const value = part.type === 'file'
        ? `${part.name}=@/path/to/file`
        : `${part.name}=${part.value || ''}`
      parts.push(`-F ${quoteCurlArgument(value, shell)}`)
    }
  } else if (payload.bodyType && payload.bodyType !== 'none' && payload.body) {
    parts.push(`--data ${quoteCurlArgument(payload.body, shell)}`)
  }

  const command = shell === 'powershell' ? 'curl.exe' : 'curl'
  const lineBreak = shell === 'powershell'
    ? ` ${String.fromCharCode(96)}\n  `
    : ` ${String.fromCharCode(92)}\n  `
  return [command, ...parts].join(lineBreak)
}

export function buildCurlUrl(baseUrl, params) {
  const enabledParams = (params || []).filter((param) => param?.enabled !== false && param?.name)
  if (!enabledParams.length) {
    return baseUrl || ''
  }
  const query = enabledParams
    .map((param) => `${encodeURIComponent(param.name)}=${encodeURIComponent(param.value || '')}`)
    .join('&')
  const hashIndex = String(baseUrl || '').indexOf('#')
  const path = hashIndex >= 0 ? String(baseUrl).slice(0, hashIndex) : String(baseUrl || '')
  const hash = hashIndex >= 0 ? String(baseUrl).slice(hashIndex) : ''
  const separator = path.includes('?') ? (path.endsWith('?') || path.endsWith('&') ? '' : '&') : '?'
  return `${path}${separator}${query}${hash}`
}

export function formatCurlTimeout(timeoutMillis) {
  const milliseconds = Number(timeoutMillis)
  const seconds = Number.isFinite(milliseconds) && milliseconds > 0 ? milliseconds / 1000 : 30
  return String(Number(seconds.toFixed(3)))
}

export function hasCurlTemplateVariables(value) {
  return /{{\s*[^{}]+?\s*}}/.test(String(value || ''))
}

export function quoteCurlArgument(value, shell) {
  const text = String(value ?? '')
  if (shell === 'powershell') {
    return `'${text.replaceAll("'", "''")}'`
  }
  return `'${text.replaceAll("'", "'\"'\"'")}'`
}
