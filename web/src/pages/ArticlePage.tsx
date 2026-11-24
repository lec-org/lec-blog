import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Avatar, Card, Glass } from '@lec-space/liquidglass'
import { useQuery, type Article } from '../api'
import { authorName, authorRoute, articleRoute, dateLabel, httpUrl, LoadingCards, QueryError } from '../components/content'
import { Markdown } from '../components/Markdown'

interface Heading { id: string; text: string; level: number }

export default function ArticlePage() {
  const { hostedSlug, postSlug } = useParams()
  const slug = hostedSlug!
  const [revision, setRevision] = useState(0)
  const result = useQuery<Article>('/public/u/' + encodeURIComponent(slug) + '/posts/' + encodeURIComponent(postSlug!),
    undefined, revision)
  const article = result?.data
  const body = useRef<HTMLDivElement>(null)
  const [headings, setHeadings] = useState<Heading[]>([])
  const [active, setActive] = useState('')

  useEffect(() => {
    const elements = Array.from(body.current?.querySelectorAll<HTMLElement>('h1,h2,h3,h4,h5,h6') || [])
    setHeadings(elements.map((heading, index) => {
      heading.id = 'heading-' + (index + 1)
      return { id: heading.id, text: heading.textContent || '', level: Number(heading.tagName[1]) }
    }))
    const track = () => {
      const heading = elements.filter((element) => element.getBoundingClientRect().top <= 112).at(-1) || elements[0]
      setActive(heading?.id || '')
    }
    const target = elements.find((element) => '#' + element.id === location.hash)
    target?.scrollIntoView()
    track()
    addEventListener('scroll', track, { passive: true })
    return () => removeEventListener('scroll', track)
  }, [article])

  if (!result) return <LoadingCards />
  if (result.error) return <QueryError error={result.error} retry={() => setRevision(revision + 1)} />
  if (!article) return null

  const name = authorName(article.author)
  const cover = httpUrl(article.coverUrl)
  return (
    <div className="article-page page-stack">
      <Link className="back-link" to={authorRoute(slug)}>返回 {article.author.blogTitle || name + '的博客'}</Link>
      <div className={headings.length ? 'reading-layout with-toc' : 'reading-layout'}>
        <article className="reading">
          <header className="article-header">
            <h1>{article.title}</h1>
            <div className="byline">
              <Avatar name={name} src={httpUrl(article.author.avatarUrl)} size="small" />
              <Link to={authorRoute(slug)}>{name}</Link>
              <time dateTime={article.publishedAt}>{dateLabel(article.publishedAt)}</time>
              {dateLabel(article.updatedAt) !== dateLabel(article.publishedAt) && <span>更新于 {dateLabel(article.updatedAt)}</span>}
            </div>
          </header>
          {cover && <img className="article-cover" src={cover} alt="" referrerPolicy="no-referrer" />}
          <div ref={body}><Markdown content={article.contentMd} /></div>
          {(article.previous || article.next) && (
            <nav className="neighbor-grid" aria-label="相邻文章">
              {article.previous && <Card><span className="muted">上一篇</span>
                <Link to={articleRoute(slug, article.previous.slug)}>{article.previous.title}</Link></Card>}
              {article.next && <Card><span className="muted">下一篇</span>
                <Link to={articleRoute(slug, article.next.slug)}>{article.next.title}</Link></Card>}
            </nav>
          )}
        </article>
        {headings.length > 0 && (
          <aside className="toc">
            <Glass className="toc-panel">
              <nav aria-label="文章目录">
                <h2>目录</h2>
                {headings.map((heading) => (
                  <a href={'#' + heading.id} key={heading.id} aria-current={active === heading.id ? 'location' : undefined}
                    style={{ paddingInlineStart: Math.max(0, heading.level - 2) * 10 + 8 }}>{heading.text}</a>
                ))}
              </nav>
            </Glass>
          </aside>
        )}
      </div>
    </div>
  )
}
