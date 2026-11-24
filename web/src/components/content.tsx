import { Link, useNavigate } from 'react-router-dom'
import { Avatar, Button, Card, Empty, Message, Skeleton, Tag } from '@lec-space/liquidglass'
import { BookOpen, ExternalLink, Flame } from '@lec-space/icons'
import { ApiError, errorText, type Member, type PublicPost } from '../api'

export function httpUrl(value?: string | null): string | undefined {
  if (!value) return undefined
  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && !url.username && !url.password
      ? url.href : undefined
  } catch {
    return undefined
  }
}

export function authorName(author: { displayName: string; username: string }): string {
  return author.displayName || author.username || 'LEC 成员'
}

export const authorRoute = (slug: string) => '/u/' + encodeURIComponent(slug)
export const articleRoute = (slug: string, post: string) => authorRoute(slug) + '/p/' + encodeURIComponent(post)

export function dateLabel(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(value))
}

export function NotFound({ title = '找不到这个页面' }: { title?: string }) {
  const navigate = useNavigate()
  return (
    <Card className="empty-card">
      <Empty title={title} icon={<BookOpen size={28} />}>
        <Button variant="glass" onClick={() => navigate('/explore')}>回到广场</Button>
      </Empty>
    </Card>
  )
}

export function QueryError({ error, retry }: { error: Error; retry?: () => void }) {
  if (error instanceof ApiError && error.status === 404) return <NotFound title="博客或文章不存在" />
  return <Message tone="error" action={retry ? { label: '重试', onClick: retry } : undefined}>{errorText(error)}</Message>
}

export function LoadingCards() {
  return (
    <div className="loading-cards" role="status" aria-label="正在加载">
      {[1, 2, 3].map((item) => <Skeleton key={item} shape="rounded" height={106} />)}
    </div>
  )
}

export function MemberTile({ member }: { member: Member }) {
  const name = authorName(member)
  const hosted = member.blogMode === 'HOSTED' && member.hostedSlug
  const body = (
    <>
      <span className={member.hasRecentPost ? 'avatar-ring is-recent' : 'avatar-ring'}>
        <Avatar src={httpUrl(member.avatarUrl)} name={name} size={49} shape="circle" />
      </span>
      <strong>{name}</strong>
      <span className="member-title">{member.blogTitle || name + '的博客'}</span>
      {member.hasRecentPost && <span title="近 7 天有新文章" aria-label="近 7 天有新文章" className="recent-icon"><Flame size={14} /></span>}
    </>
  )
  return hosted
    ? <Link className="member-tile" to={authorRoute(hosted)}>{body}</Link>
    : <a className="member-tile" href={httpUrl(member.blogUrl)} target="_blank" rel="noopener noreferrer">{body}</a>
}

export function PostCard({ post, showAuthor = true }: { post: PublicPost; showAuthor?: boolean }) {
  const route = post.sourceType === 'NATIVE' && post.hostedSlug && post.slug ? articleRoute(post.hostedSlug, post.slug) : null
  const cover = httpUrl(post.coverUrl)
  return (
    <Card className={cover ? 'post-card has-cover' : 'post-card'}>
      {cover && <img className="post-thumbnail" src={cover} referrerPolicy="no-referrer" alt="" loading="lazy" />}
      <div className="post-card-copy">
        <div className="post-meta">
          {showAuthor && <><Avatar src={httpUrl(post.avatarUrl)} name={authorName(post)} size="small" />
            <span>{authorName(post)}</span></>}
          <time dateTime={post.publishedAt}>{dateLabel(post.publishedAt)}</time>
          {post.readingMinutes && <span>约 {post.readingMinutes} 分钟</span>}
        </div>
        <h3>
          {route ? <Link to={route}>{post.title}</Link>
            : <a href={httpUrl(post.postUrl)} target="_blank" rel="noopener noreferrer">{post.title} <ExternalLink size={15} /></a>}
        </h3>
        {post.summary && <p>{post.summary}</p>}
        {showAuthor && <Tag tone={route ? 'info' : 'neutral'}>{route ? '托管文章' : '外部博客'}</Tag>}
      </div>
    </Card>
  )
}
