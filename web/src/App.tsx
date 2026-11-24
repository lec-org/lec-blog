import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Link, Route, Routes, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Button, ConfigProvider, Dialog, Glass, Message, SearchField, ToastProvider, zhCN } from '@lec-space/liquidglass'
import { BookOpen, LogOut, Moon, Pencil, Sun } from '@lec-space/icons'
import { AuthProvider, DevLoginForm, useAuth } from './auth'
import { LoadingCards, NotFound } from './components/content'
import { ExplorePage } from './pages/ExplorePage'
import { AuthorPage } from './pages/AuthorPage'
import { errorText } from './api'

const ArticlePage = lazy(() => import('./pages/ArticlePage'))
const WriterPage = lazy(() => import('./pages/WriterPage'))
type Theme = 'light' | 'dark'

export function App() {
  const [theme, setTheme] = useState<Theme>(() => {
    const stored = localStorage.getItem('lec-blog:theme')
    return stored === 'light' || stored === 'dark'
      ? stored : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  })
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('lec-blog:theme', theme)
  }, [theme])
  return (
    <ConfigProvider locale={zhCN} theme={theme}>
      <ToastProvider>
        <AuthProvider>
          <Layout theme={theme} onTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')} />
        </AuthProvider>
      </ToastProvider>
    </ConfigProvider>
  )
}

function Layout({ theme, onTheme }: { theme: Theme; onTheme: () => void }) {
  const { config, session, logout } = useAuth()
  const [devOpen, setDevOpen] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const exploring = location.pathname === '/' || location.pathname === '/explore'
  useEffect(() => {
    if (!location.hash) window.scrollTo({ top: 0, behavior: 'instant' })
  }, [location.pathname, location.hash])
  return (
    <>
      <header className="site-header">
        <Glass className="nav-shell">
          <Link className="site-brand" to="/" aria-label="LEC 成员博客首页">
            <span className="brand-mark"><BookOpen size={21} /></span>
            <span>LEC<span className="brand-name"> 成员博客</span></span>
          </Link>
          {exploring && <SearchField className="global-search" variant="glass" aria-label="搜索成员博客"
            placeholder="搜索成员或博客" value={params.get('q') || ''} maxLength={128}
            onValueChange={(query) => setParams(query ? { q: query } : {}, { replace: true })} />}
          <nav className="main-nav" aria-label="网站导航">
            <Link to="/explore" aria-current={exploring ? 'page' : undefined}>广场</Link>
            {config.mockEnabled && !session && (
              <Button size="small" variant="glass" className="dev-trigger" onClick={() => setDevOpen(true)}>开发登录</Button>
            )}
            {session && (
              <Button size="small" variant="glass" icon={<LogOut size={16} />} aria-label="退出写作台"
                title={session.displayName + ' · 退出写作台'} onClick={() => void logout()} />
            )}
            <Button size="small" variant="glass" icon={theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
              aria-label={theme === 'dark' ? '切换浅色主题' : '切换深色主题'} onClick={onTheme} />
            <Button size="small" variant="filled" icon={<Pencil size={15} />} onClick={() => navigate('/write')}>写文章</Button>
          </nav>
        </Glass>
      </header>
      <main className="site-main">
        <Suspense fallback={<LoadingCards />}>
          <Routes>
            <Route path="/" element={<ExplorePage />} />
            <Route path="/explore" element={<ExplorePage />} />
            <Route path="/u/:hostedSlug" element={<AuthorPage />} />
            <Route path="/u/:hostedSlug/p/:postSlug" element={<ArticlePage />} />
            <Route path="/write" element={<WriterPage />} />
            <Route path="/write/:id" element={<WriterPage />} />
            <Route path="/callback" element={<CallbackPage />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </main>
      {devOpen && config.mockEnabled && (
        <Dialog open={devOpen} onOpenChange={setDevOpen} title="开发模式快捷登录" size="regular">
          <DevLoginForm done={() => { setDevOpen(false); navigate('/write') }} />
        </Dialog>
      )}
    </>
  )
}

function CallbackPage() {
  const { complete } = useAuth()
  const navigate = useNavigate()
  const flight = useRef<Promise<void> | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    flight.current ??= complete()
    void flight.current.then(() => {
      if (active) navigate('/write', { replace: true })
    }).catch((error) => {
      if (active) setError(errorText(error))
    })
    return () => { active = false }
  }, [complete, navigate])
  return error
    ? <Message tone="error" action={{ label: '返回写作台', onClick: () => navigate('/write', { replace: true }) }}>{error}</Message>
    : <LoadingCards />
}
