import { useDeferredValue, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, Card, Dialog, Drawer, Empty, Glass, Pagination, SearchField, SegmentedControl, Tag } from '@lec-space/liquidglass'
import { BookOpen, ListFilter, Plus, Settings, ShieldCheck } from '@lec-space/icons'
import { useQuery, type MyBlog, type NativePost, type NativePostItem, type Page } from '../api'
import { useAuth, WriterLogin } from '../auth'
import { authorRoute, dateLabel, LoadingCards, NotFound, QueryError } from '../components/content'
import { BlogSettings } from '../components/BlogSettings'
import { PostEditor } from '../components/PostEditor'

const FILTERS = [{ value: 'ALL', label: '全部' }, { value: 'DRAFT', label: '草稿' }, { value: 'PUBLISHED', label: '已发布' }]

export default function WriterPage() {
  const { session } = useAuth()
  return session ? <Writer key={session.userId} /> : <WriterLogin />
}

function Writer() {
  const { session } = useAuth()
  const result = useQuery<MyBlog | null>('/blogs/me', session!.headers)
  const [override, setOverride] = useState<MyBlog | null>(null)
  if (!result) return <LoadingCards />
  if (result.error) return <QueryError error={result.error} />
  const blog = override || result.data || null
  if (blog?.status === 'HIDDEN') {
    return <Card className="empty-card"><Empty icon={<ShieldCheck size={28} />} title="博客已下架"
      description="请联系工作室队长恢复后再管理文章。" /></Card>
  }
  if (blog?.blogMode !== 'HOSTED') {
    return (
      <Card className="setup-card" padding="large">
        <h1>开通工作室博客</h1>
        <p className="muted">选一个短链即可开始写作。发布前，你的草稿只会出现在自己的写作台。</p>
        <BlogSettings blog={blog} saved={setOverride} />
      </Card>
    )
  }
  return <Workspace blog={blog} updateBlog={setOverride} />
}

function Workspace({ blog, updateBlog }: { blog: MyBlog; updateBlog: (value: MyBlog) => void }) {
  const { session } = useAuth()
  const navigate = useNavigate()
  const id = useParams().id
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [page, setPage] = useState(1)
  const [revision, setRevision] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const q = useDeferredValue(search.trim())
  const list = useQuery<Page<NativePostItem>>('/posts/me?' + new URLSearchParams({
    q, status: filter, page: String(page), size: '10',
  }), session!.headers, revision)
  const refresh = () => setRevision((value) => value + 1)

  function open(postId?: number) {
    setDrawerOpen(false)
    navigate(postId ? '/write/' + postId : '/write')
  }

  const articles = (
    <div className="library">
      <div className="section-head">
        <h2>我的文章</h2>
        <Button size="small" variant="filled" icon={<Plus size={15} />} onClick={() => open()}>新建</Button>
      </div>
      <SearchField aria-label="搜索我的文章" placeholder="搜索文章" variant="fill" value={search}
        maxLength={128} onValueChange={(value) => { setSearch(value); setPage(1) }} />
      <SegmentedControl aria-label="文章状态" items={FILTERS} value={filter}
        onValueChange={(value) => { setFilter(value); setPage(1) }} />
      {!list ? <LoadingCards /> : list.error ? <QueryError error={list.error} retry={refresh} />
        : list.data?.items.length ? (
          <>
            <div className="library-list">
              {list.data.items.map((post) => (
                <Button key={post.id} variant="glass" className="library-item"
                  aria-pressed={String(post.id) === id} onClick={() => open(post.id)}>
                  <span className="library-title">{post.title}</span>
                  <span className="library-meta">
                    <Tag tone={post.postStatus === 'PUBLISHED' ? 'success' : 'neutral'}>{post.postStatus === 'PUBLISHED' ? '已发布' : '草稿'}</Tag>
                    <span>{dateLabel(post.updatedAt)}</span>
                  </span>
                </Button>
              ))}
            </div>
            {list.data.total > list.data.size && <Pagination page={page} pageSize={list.data.size}
              total={list.data.total} onPageChange={setPage} />}
          </>
        ) : <Empty icon={<BookOpen size={22} />} title="暂无文章" />}
    </div>
  )

  const editorActions = {
    blog,
    saveComplete: (post: NativePost) => {
      if (String(post.id) !== id) navigate('/write/' + post.id, { replace: true })
      refresh()
    },
    deleteComplete: () => {
      setPage(1)
      navigate('/write', { replace: true })
      refresh()
    },
  }

  return (
    <div className="writer-page page-stack">
      <div className="section-head writer-heading">
        <div><h1>写作台</h1><Link to={authorRoute(blog.hostedSlug!)} className="muted">{blog.blogTitle || '我的博客'}</Link></div>
        <div className="row">
          <Button variant="glass" className="library-trigger" icon={<ListFilter size={16} />}
            onClick={() => setDrawerOpen(true)}>我的文章</Button>
          <Button variant="glass" icon={<Settings size={16} />} onClick={() => setSettingsOpen(true)}>博客设置</Button>
        </div>
      </div>
      <div className="writer-layout">
        <Glass className="writer-sidebar">{articles}</Glass>
        {id ? (/^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id))
          ? <LoadedEditor key={id} id={id} {...editorActions} /> : <NotFound title="文章不存在" />)
          : <PostEditor key="new" {...editorActions} />}
      </div>
      <Drawer side="start" open={drawerOpen} onOpenChange={setDrawerOpen} title="文章管理">
        {drawerOpen && articles}
      </Drawer>
      <Dialog open={settingsOpen} onOpenChange={setSettingsOpen} title="博客设置">
        {settingsOpen && <BlogSettings blog={blog} saved={(next) => { updateBlog(next); setSettingsOpen(false) }} />}
      </Dialog>
    </div>
  )
}

function LoadedEditor({ id, ...options }: Omit<Parameters<typeof PostEditor>[0], 'post'> & { id: string }) {
  const { session } = useAuth()
  const result = useQuery<NativePost>('/posts/me/' + encodeURIComponent(id), session!.headers)
  if (!result) return <LoadingCards />
  if (result.error) return <QueryError error={result.error} />
  return <PostEditor post={result.data!} {...options} />
}
