import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Avatar, Button, Empty, Glass, Pagination, Tag, useToast } from '@lec-space/liquidglass'
import { BookOpen, Copy, ShieldCheck } from '@lec-space/icons'
import { Rss } from 'lucide-react'
import { type Homepage, useQuery } from '../api'
import { authorName, httpUrl, LoadingCards, PostCard, QueryError } from '../components/content'

export function AuthorPage() {
  const slug = useParams().hostedSlug!
  const [params, setParams] = useSearchParams()
  const page = Math.max(1, Number(params.get('page')) || 1)
  const [revision, setRevision] = useState(0)
  const result = useQuery<Homepage>('/public/u/' + encodeURIComponent(slug) + '?page=' + page, undefined, revision)
  const { toast } = useToast()
  if (!result) return <LoadingCards />
  if (result.error) return <QueryError error={result.error} retry={() => setRevision(revision + 1)} />
  const { author, posts, rssUrl } = result.data!
  const name = authorName(author)
  return (
    <div className="page-stack author-page">
      <Link className="back-link" to="/explore">返回博客广场</Link>
      <Glass className="author-hero">
        <Avatar src={httpUrl(author.avatarUrl)} name={name} shape="circle" size={92} />
        <div className="author-copy">
          <div className="identity-line"><span>{name}</span>
            {author.isCaptain && <Tag tone="info" icon={<ShieldCheck size={14} />}>队长</Tag>}
          </div>
          <h1>{author.blogTitle || name + '的博客'}</h1>
          {author.bio && <p>{author.bio}</p>}
          {author.tags.length > 0 && <div className="tags">{author.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}</div>}
        </div>
        <div className="author-actions">
          <a href={httpUrl(rssUrl)} className="link-with-icon"><Rss size={16} /> RSS 订阅</a>
          <Button variant="glass" size="small" icon={<Copy size={15} />} onClick={() => {
            void navigator.clipboard.writeText(rssUrl).then(() => toast({ title: '订阅链接已复制', tone: 'success' }))
              .catch(() => toast({ title: '无法访问剪贴板，请手动复制订阅链接', tone: 'error' }))
          }}>复制地址</Button>
        </div>
      </Glass>
      <div className="section-head"><h2>文章</h2><span className="muted">{posts.total} 篇</span></div>
      {posts.items.length > 0 ? <div className="article-list">{posts.items.map((post) => <PostCard key={post.id} post={post} showAuthor={false} />)}</div>
        : <Empty icon={<BookOpen size={28} />} title="这位成员还没有发布文章" />}
      {posts.total > posts.size && <div className="pagination"><Pagination total={posts.total} page={page} pageSize={posts.size}
        onPageChange={(next) => { setParams({ page: String(next) }); window.scrollTo({ top: 0, behavior: 'smooth' }) }} /></div>}
    </div>
  )
}
