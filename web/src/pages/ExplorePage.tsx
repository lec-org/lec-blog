import { useDeferredValue, useState } from 'react'
import { Button, Empty, Glass, Pagination, SegmentedControl, Tag } from '@lec-space/liquidglass'
import { BookOpen, Pencil, Users } from '@lec-space/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, type Member, type Page, type PublicPost } from '../api'
import { LoadingCards, MemberTile, PostCard, QueryError } from '../components/content'

const MODES = [{ value: 'ALL', label: '全部' }, { value: 'HOSTED', label: '工作室托管' }, { value: 'EXTERNAL', label: '外部博客' }]

export function ExplorePage() {
  const [params] = useSearchParams()
  const [mode, setMode] = useState('ALL')
  const [tag, setTag] = useState('')
  const [page, setPage] = useState(1)
  const [revision, setRevision] = useState(0)
  const query = useDeferredValue((params.get('q') || '').trim().slice(0, 128))
  const members = useQuery<Member[]>('/public/blogs?' + new URLSearchParams({ q: query, mode, tag }), undefined, revision)
  const recent = useQuery<Page<PublicPost>>('/public/posts/recent?page=' + page, undefined, revision)
  const navigate = useNavigate()
  const tags = Array.from(new Set([tag, ...((members?.data || []).flatMap((member) => member.tags))])).filter(Boolean).slice(0, 12)
  const retry = () => setRevision((value) => value + 1)
  return (
    <div className="explore page-stack">
      <Glass className="explore-hero">
        <div className="hero-copy">
          <h1>成员博客广场</h1>
          <p>读工作室成员的技术分享，或开通自己的托管博客。</p>
          <Button variant="filled" icon={<Pencil size={17} />} onClick={() => navigate('/write')}>打开写作台</Button>
        </div>
        <div className="hero-art" aria-hidden="true"><span /><span /><span /></div>
      </Glass>
      <section className="page-stack compact" aria-labelledby="member-heading">
        <div className="section-head">
          <h2 id="member-heading">成员博客</h2>
          <div className="directory-controls">
            <SegmentedControl items={MODES} value={mode} onValueChange={setMode} aria-label="博客模式" />
          </div>
        </div>
        {tags.length > 0 && <div className="tags">
          <Button size="small" variant={tag ? 'glass' : 'filled'} onClick={() => setTag('')}>全部标签</Button>
          {tags.map((value) => <Button key={value} size="small" variant={tag === value ? 'filled' : 'glass'}
            onClick={() => setTag((active) => active === value ? '' : value)}>{value}</Button>)}
        </div>}
        {!members ? <LoadingCards /> : members.error ? <QueryError error={members.error} retry={retry} />
          : members.data?.length
            ? <Glass className="member-strip">{members.data.map((member) => <MemberTile key={member.userId} member={member} />)}</Glass>
            : <Empty icon={<Users size={28} />} title={query || tag || mode !== 'ALL' ? '没有匹配的成员博客' : '暂无成员博客'} />}
      </section>
      <section className="page-stack compact" aria-labelledby="latest-heading">
        <div className="section-head">
          <h2 id="latest-heading">最新动态</h2>
          {recent?.data?.total ? <Tag tone="neutral">{recent.data.total} 篇文章</Tag> : null}
        </div>
        {!recent ? <LoadingCards /> : recent.error ? <QueryError error={recent.error} retry={retry} />
          : recent.data?.items.length
            ? <>
              <div className="article-list">{recent.data.items.map((post) => <PostCard key={post.id} post={post} />)}</div>
              {recent.data.total > recent.data.size && <div className="pagination"><Pagination total={recent.data.total}
                page={page} pageSize={recent.data.size} onPageChange={setPage} /></div>}
            </>
            : <Empty icon={<BookOpen size={27} />} title="暂无已发布文章" />}
      </section>
    </div>
  )
}
