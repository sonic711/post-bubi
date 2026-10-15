export function filterCollections(collections, query) {
  const term = query.trim().toLocaleLowerCase()
  if (!term) return collections
  const matches = (value) => String(value || '').toLocaleLowerCase().includes(term)
  return collections.flatMap((collection) => {
    if (matches(collection.name)) return [collection]
    const folders = collection.folders || []
    const included = new Set(folders.filter((folder) => matches(folder.name)).map((folder) => folder.id))
    // A folder match includes its descendants; a request match keeps its ancestor path.
    for (let changed = true; changed;) {
      changed = false
      for (const folder of folders) {
        if (included.has(folder.parentFolderId) && !included.has(folder.id)) {
          included.add(folder.id)
          changed = true
        }
      }
    }
    const requests = (collection.requests || []).filter((request) =>
      matches(request.name) || matches(request.type) || included.has(request.folderId))
    const ancestors = new Set([...included, ...requests.map((request) => request.folderId)])
    const byId = new Map(folders.map((folder) => [folder.id, folder]))
    for (const id of [...ancestors]) {
      let parent = byId.get(id)?.parentFolderId
      while (parent && !ancestors.has(parent)) {
        ancestors.add(parent)
        parent = byId.get(parent)?.parentFolderId
      }
    }
    return requests.length || included.size
      ? [{ ...collection, folders: folders.filter((folder) => ancestors.has(folder.id)), requests }]
      : []
  })
}

export function handleTabKeydown(event) {
  const tabs = [...event.currentTarget.querySelectorAll('[role="tab"]:not(:disabled)')]
  const index = tabs.indexOf(event.target)
  if (index < 0) return
  const nextIndex = {
    ArrowRight: (index + 1) % tabs.length,
    ArrowLeft: (index - 1 + tabs.length) % tabs.length,
    Home: 0,
    End: tabs.length - 1,
  }[event.key]
  if (nextIndex === undefined) return
  event.preventDefault()
  tabs[nextIndex].focus()
  tabs[nextIndex].click()
  tabs[nextIndex].scrollIntoView({ block: 'nearest', inline: 'nearest' })
}

export async function copyText(text) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // Remote HTTP deployments may not allow the async clipboard API.
    }
  }
  const previous = document.activeElement
  const target = document.createElement('textarea')
  target.value = text
  target.setAttribute('readonly', '')
  target.style.position = 'fixed'
  target.style.opacity = '0'
  document.body.appendChild(target)
  try {
    target.select()
    if (!document.execCommand('copy')) throw new Error('瀏覽器拒絕存取剪貼簿')
  } finally {
    target.remove()
    previous?.focus()
  }
}

export const workspaceDialog = {
  mounted(element, binding) {
    const trigger = document.activeElement
    const overlay = element.closest('.modal-backdrop')
    const siblings = [...overlay.parentElement.children].filter((child) => child !== overlay)
    const previousInert = siblings.map((child) => child.inert)
    siblings.forEach((child) => { child.inert = true })
    const focusable = () => [...element.querySelectorAll('button, input, select, textarea, [tabindex="0"]')]
      .filter((node) => !node.disabled && node.getClientRects().length)
    element.tabIndex = -1
    const frame = requestAnimationFrame(() => (focusable()[0] || element).focus())
    const onKeydown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        binding.value()
      } else if (event.key === 'Tab') {
        const nodes = focusable()
        const first = nodes[0] || element
        const last = nodes.at(-1) || element
        if (event.shiftKey && (document.activeElement === first || document.activeElement === element)) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === element)) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    element.addEventListener('keydown', onKeydown)
    element.__releaseDialog = () => {
      cancelAnimationFrame(frame)
      element.removeEventListener('keydown', onKeydown)
      siblings.forEach((child, index) => { child.inert = previousInert[index] })
      if (trigger?.isConnected) trigger.focus()
    }
  },
  beforeUnmount(element) {
    element.__releaseDialog?.()
  },
}
