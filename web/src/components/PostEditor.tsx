import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button, Dialog, FormField, Glass, Input, Message, SegmentedControl, Tag, TextArea, Toolbar, ToolbarGroup, useToast,
} from '@lec-space/liquidglass'
import { CodeXml, FileUp, Link as LinkIcon, Save, Trash2 } from '@lec-space/icons'
import { api, errorText, type MyBlog, type NativePost, type PostForm, type PostStatus } from '../api'
import { useAuth } from '../auth'
import { articleRoute, httpUrl } from './content'
import { Markdown } from './Markdown'

const MODES = [{ value: 'split', label: '分屏' }, { value: 'editor', label: '编辑' }, { value: 'preview', label: '预览' }]
const BLANK: PostForm = { title: '', slug: '', summary: '', coverUrl: '', contentMd: '' }
const UPLOAD_TYPES = 'image/png,image/jpeg,image/gif,image/webp'

function fields(post?: NativePost): PostForm {
  return post ? {
    title: post.title, slug: post.slug, summary: post.summary, coverUrl: post.coverUrl || '', contentMd: post.contentMd,
  } : BLANK
}

function readBackup(key: string, initial: PostForm): PostForm | null {
  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null')
    return saved && Object.keys(BLANK).every((field) => typeof saved[field] === 'string')
      && JSON.stringify(saved) !== JSON.stringify(initial) ? saved : null
  } catch {
    return null
  }
}

export function PostEditor({ post, blog, saveComplete, deleteComplete }: {
  post?: NativePost
  blog: MyBlog
  saveComplete: (post: NativePost) => void
  deleteComplete: () => void
}) {
  const { config, session } = useAuth()
  const { toast } = useToast()
  const [article, setArticle] = useState(post)
  const localKey = 'lec-blog:draft:' + encodeURIComponent(session!.userId) + ':' + (article?.id || 'new')
  const [clean, setClean] = useState(() => fields(post))
  const [form, setForm] = useState(clean)
  const [backup, setBackup] = useState(() => readBackup(localKey, clean))
  const [mode, setMode] = useState('split')
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState('')
  const editor = useRef<HTMLTextAreaElement>(null)
  const bodyPicker = useRef<HTMLInputElement>(null)
  const coverPicker = useRef<HTMLInputElement>(null)
  const disabled = busy || uploading
  const dirty = JSON.stringify(clean) !== JSON.stringify(form)
  const published = article?.postStatus === 'PUBLISHED'

  useEffect(() => {
    if (backup || !dirty) return
    try { localStorage.setItem(localKey, JSON.stringify(form)) }
    catch { /* The backend Save action remains available if browser storage is full. */ }
  }, [backup, dirty, form, localKey])

  function edit<K extends keyof PostForm>(key: K, value: PostForm[K]) {
    setForm((previous) => ({ ...previous, [key]: value }))
  }

  async function save(status: PostStatus) {
    if (disabled) return
    if (!form.title.trim()) { setError('请填写文章标题'); return }
    setBusy(true)
    setError('')
    try {
      const saved = await api<NativePost>(article ? '/posts/me/' + article.id : '/posts/me', {
        method: article ? 'PUT' : 'POST',
        headers: session!.headers,
        body: JSON.stringify({ ...form, postStatus: status }),
      })
      localStorage.removeItem(localKey)
      const next = fields(saved)
      setArticle(saved)
      setForm(next)
      setClean(next)
      setBackup(null)
      toast({ title: status === 'PUBLISHED' ? (published ? '修改已保存' : '文章已发布')
        : (published ? '已撤回为草稿' : '草稿已保存'), tone: 'success' })
      saveComplete(saved)
    } catch (error) {
      setError(errorText(error))
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
        event.preventDefault()
        void save(published ? 'PUBLISHED' : 'DRAFT')
      }
    }
    addEventListener('keydown', shortcut)
    return () => removeEventListener('keydown', shortcut)
  })

  function insert(before: string, after = '', placeholder = '') {
    const start = editor.current?.selectionStart ?? form.contentMd.length
    const end = editor.current?.selectionEnd ?? start
    const selected = form.contentMd.slice(start, end)
    const text = before + (selected || placeholder) + after
    const next = form.contentMd.slice(0, start) + text + form.contentMd.slice(end)
    if (next.length > 200_000) { setError('正文不能超过 200,000 字符'); return }
    edit('contentMd', next)
    requestAnimationFrame(() => {
      editor.current?.focus()
      editor.current?.setSelectionRange(start + text.length, start + text.length)
    })
  }

  async function upload(file: File, target: 'body' | 'cover') {
    if (disabled) return
    if (!config.imageUploadEnabled) { setError('图床未启用，可使用图片外链。'); return }
    if (file.size > 10 * 1024 * 1024) { setError('图片大小不能超过 10 MB'); return }
    setUploading(true)
    setError('')
    const body = new FormData()
    body.append('file', file)
    try {
      const image = await api<{ url: string }>('/uploads/image', { method: 'POST', headers: session!.headers, body })
      if (target === 'cover') edit('coverUrl', image.url)
      else insert('\n![图片](' + encodeURI(image.url) + ')\n')
      toast({ title: '图片已上传', tone: 'success' })
    } catch (error) {
      setError(errorText(error))
    } finally {
      setUploading(false)
    }
  }

  async function remove() {
    if (!article) return
    setBusy(true)
    setError('')
    try {
      await api('/posts/me/' + article.id, { method: 'DELETE', headers: session!.headers })
      localStorage.removeItem(localKey)
      setConfirm(false)
      toast({ title: '文章已删除', tone: 'success' })
      deleteComplete()
    } catch (error) {
      setError(errorText(error))
      setConfirm(false)
    } finally {
      setBusy(false)
    }
  }

  const cover = httpUrl(form.coverUrl)
  return (
    <Glass className="editor-panel">
      <div className="section-head editor-heading">
        <div className="row"><h2>{article ? '编辑文章' : '新建文章'}</h2>
          <Tag tone={published ? 'success' : 'neutral'}>{published ? '已发布' : '草稿'}</Tag>
          {dirty && <span className="muted">有未保存修改</span>}
        </div>
        <div className="row">
          {published && <Link className="muted" to={articleRoute(blog.hostedSlug!, article!.slug)}>阅读文章</Link>}
          {article && <Button variant="glass" size="small" icon={<Trash2 size={16} />} destructive
            aria-label="删除文章" disabled={disabled} onClick={() => setConfirm(true)} />}
        </div>
      </div>
      {backup && <div className="recovery">
        <Message>此浏览器有一份未提交的草稿。</Message>
        <Button size="small" variant="filled" onClick={() => { setForm(backup); setBackup(null) }}>恢复草稿</Button>
        <Button size="small" variant="glass" onClick={() => { localStorage.removeItem(localKey); setBackup(null) }}>忽略</Button>
      </div>}
      <div className="metadata">
        <FormField label="文章标题" required disabled={disabled}>
          {(props) => <Input {...props} value={form.title} onValueChange={(value) => edit('title', value)}
            variant="fill" maxLength={255} placeholder="给文章起个标题" />}
        </FormField>
        <FormField label="文章短链" disabled={disabled} description="留空时自动生成">
          {(props) => <Input {...props} value={form.slug} onValueChange={(value) => edit('slug', value)}
            variant="fill" maxLength={128} placeholder="my-first-post" />}
        </FormField>
        <FormField label="文章摘要" disabled={disabled} description="留空时从正文提取">
          {(props) => <Input {...props} value={form.summary} onValueChange={(value) => edit('summary', value)}
            variant="fill" maxLength={300} />}
        </FormField>
        <FormField label="封面图片地址" disabled={disabled}>
          {(props) => <div className="row cover-field">
            <Input {...props} value={form.coverUrl} onValueChange={(value) => edit('coverUrl', value)} variant="fill"
              type="url" maxLength={512} placeholder="https://…" />
            {config.imageUploadEnabled && (
              <Button variant="glass" size="small" icon={<FileUp size={16} />} aria-label="上传封面"
                disabled={disabled} onClick={() => coverPicker.current?.click()} />
            )}
          </div>}
        </FormField>
        {cover && <img className="cover-preview" src={cover} alt="封面预览" referrerPolicy="no-referrer" />}
      </div>
      <Toolbar className="editor-toolbar" aria-label="Markdown 工具栏">
        <ToolbarGroup>
          <Button size="small" variant="glass" disabled={disabled} onClick={() => insert('**', '**', '文本')}>加粗</Button>
          <Button size="small" variant="glass" disabled={disabled} onClick={() => insert('*', '*', '文本')}>斜体</Button>
          <Button size="small" variant="glass" disabled={disabled} onClick={() => insert('\n## ', '\n', '标题')}>标题</Button>
          <Button size="small" variant="glass" icon={<LinkIcon size={15} />} aria-label="插入链接" disabled={disabled}
            onClick={() => insert('[', '](https://example.org)', '链接')} />
          <Button size="small" variant="glass" icon={<CodeXml size={17} />} aria-label="插入代码块" disabled={disabled}
            onClick={() => insert('\n```text\n', '\n```\n', 'code')} />
          {config.imageUploadEnabled ? <Button size="small" variant="glass" icon={<FileUp size={16} />}
            aria-label="上传正文图片" loading={uploading} disabled={disabled} onClick={() => bodyPicker.current?.click()} />
            : <Button size="small" variant="glass" disabled={disabled} onClick={() => insert('\n![', '](https://example.org/image.png)\n', '图片')}>插入图片外链</Button>}
        </ToolbarGroup>
        <SegmentedControl aria-label="编辑视图" items={MODES} value={mode} onValueChange={setMode} />
      </Toolbar>
      <input type="file" accept={UPLOAD_TYPES} ref={bodyPicker} hidden onChange={(event) => {
        const file = event.target.files?.[0]
        if (file) void upload(file, 'body')
        event.target.value = ''
      }} />
      <input type="file" accept={UPLOAD_TYPES} ref={coverPicker} hidden onChange={(event) => {
        const file = event.target.files?.[0]
        if (file) void upload(file, 'cover')
        event.target.value = ''
      }} />
      <div className={'editing-space ' + mode}>
        {mode !== 'preview' && <div className="source-pane">
          <TextArea ref={editor} aria-label="Markdown 正文" variant="fill" rows={18} maxLength={200000}
            disabled={disabled} value={form.contentMd} onValueChange={(value) => edit('contentMd', value)}
            placeholder="使用 Markdown 撰写正文…" onPaste={(event) => {
              const file = event.clipboardData.files[0]
              if (file) { event.preventDefault(); void upload(file, 'body') }
            }} onDragOver={(event) => {
              if (event.dataTransfer.types.includes('Files')) event.preventDefault()
            }} onDrop={(event) => {
              const file = event.dataTransfer.files[0]
              if (file) { event.preventDefault(); void upload(file, 'body') }
            }} />
        </div>}
        {mode !== 'editor' && <div className="preview-pane" aria-label="Markdown 预览">
          {form.contentMd ? <Markdown content={form.contentMd} /> : <p className="muted">正文预览</p>}
        </div>}
      </div>
      {error && <Message tone="error">{error}</Message>}
      <div className="editor-footer">
        {config.imageUploadEnabled && <span className="muted">可在正文中粘贴或拖入图片</span>}
        <div className="row">
          <Button variant="glass" icon={<Save size={16} />} disabled={disabled} onClick={() => void save('DRAFT')}>
            {published ? '撤回草稿' : '保存草稿'}
          </Button>
          <Button variant="filled" loading={busy} disabled={disabled} onClick={() => void save('PUBLISHED')}>
            {published ? '保存修改' : '发布文章'}
          </Button>
        </div>
      </div>
      <Dialog open={confirm} onOpenChange={setConfirm} dismissible={!busy} title="删除这篇文章"
        description="删除后将无法恢复，已发布文章也会从主页和 RSS 中移除。"
        footer={<div className="row">
          <Button variant="glass" disabled={busy} onClick={() => setConfirm(false)}>取消</Button>
          <Button variant="filled" destructive loading={busy} onClick={() => void remove()}>确认删除</Button>
        </div>} />
    </Glass>
  )
}
