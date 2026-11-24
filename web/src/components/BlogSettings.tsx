import { useState, type FormEvent } from 'react'
import { Button, FormField, Input, Message, SegmentedControl, Switch, TextArea } from '@lec-space/liquidglass'
import { useAuth } from '../auth'
import { api, errorText, type BlogMode, type MyBlog } from '../api'

const OPTIONS = [{ value: 'HOSTED', label: '工作室托管' }, { value: 'EXTERNAL', label: '绑定外部博客' }]

export function BlogSettings({ blog, saved }: { blog: MyBlog | null; saved: (blog: MyBlog) => void }) {
  const { config, session } = useAuth()
  const [mode, setMode] = useState<BlogMode>('HOSTED')
  const [slug, setSlug] = useState(blog?.hostedSlug ?? '')
  const [title, setTitle] = useState(blog?.blogTitle ?? '')
  const [bio, setBio] = useState(blog?.bio ?? '')
  const [tags, setTags] = useState(blog?.tags.join(', ') ?? '')
  const [externalUrl, setExternalUrl] = useState(blog?.blogMode === 'EXTERNAL' ? blog.blogUrl : '')
  const [rssUrl, setRssUrl] = useState(blog?.rssUrl ?? '')
  const [discover, setDiscover] = useState(!blog?.rssUrl)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    const values = tags.split(/[,，]/).map((part) => part.trim()).filter(Boolean)
    try {
      const profile = { blogTitle: title, bio, tags: values }
      const hosted = mode === 'HOSTED'
      const next = await api<MyBlog>(hosted ? '/blogs/me/enable-hosted' : '/blogs/me', {
        method: hosted ? 'POST' : 'PUT',
        headers: session?.headers,
        body: JSON.stringify(hosted ? { ...profile, hostedSlug: slug }
          : { ...profile, blogUrl: externalUrl, rssUrl, autoDiscoverRss: discover }),
      })
      saved(next)
    } catch (error) {
      setError(errorText(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="field-stack" onSubmit={(event) => void submit(event)}>
      <SegmentedControl aria-label="博客模式" items={OPTIONS} value={mode}
        onValueChange={(value) => !busy && setMode(value as BlogMode)} />
      {mode === 'HOSTED' ? (
        <FormField label="博客短链" disabled={busy} description={config.publicUrl + '/u/ · 留空使用用户名生成'}>
          {(props) => <Input {...props} variant="fill" maxLength={32} value={slug} onValueChange={setSlug} />}
        </FormField>
      ) : (
        <>
          {blog?.blogMode === 'HOSTED' && <Message>切换后托管主页会下线。切回托管模式即可恢复原生文章和草稿。</Message>}
          <FormField label="外部博客地址" required disabled={busy}>
            {(props) => <Input {...props} variant="fill" type="url" maxLength={512} value={externalUrl} onValueChange={setExternalUrl} />}
          </FormField>
          <FormField label="RSS 或 Atom 地址" disabled={busy}>
            {(props) => <Input {...props} variant="fill" type="url" maxLength={512} value={rssUrl} onValueChange={setRssUrl} />}
          </FormField>
          <FormField label="留空时自动探测订阅源" disabled={busy || !!rssUrl}>
            {(props) => <Switch {...props} checked={discover} onCheckedChange={setDiscover} />}
          </FormField>
        </>
      )}
      <FormField label="博客名称" disabled={busy}>
        {(props) => <Input {...props} variant="fill" maxLength={128} value={title} onValueChange={setTitle} />}
      </FormField>
      <FormField label="博客简介" disabled={busy}>
        {(props) => <TextArea {...props} variant="fill" rows={3} maxLength={300} showCount value={bio} onValueChange={setBio} />}
      </FormField>
      <FormField label="标签" description="用逗号分隔，最多 8 个标签" disabled={busy}>
        {(props) => <Input {...props} variant="fill" maxLength={220} value={tags} onValueChange={setTags} />}
      </FormField>
      {error && <Message tone="error">{error}</Message>}
      <div><Button variant="filled" type="submit" loading={busy}>
        {mode === 'HOSTED' && blog?.blogMode !== 'HOSTED' ? '开通托管博客' : '保存博客配置'}
      </Button></div>
    </form>
  )
}
