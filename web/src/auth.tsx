import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'
import { Button, Card, FormField, Input, Message, Skeleton, Switch } from '@lec-space/liquidglass'
import { api, errorText, type WebConfig } from './api'

interface DevIdentity {
  userId: string
  username: string
  displayName: string
  isCaptain: boolean
}

interface Session {
  userId: string
  displayName: string
  headers: Record<string, string>
}

interface Auth {
  config: WebConfig
  session: Session | null
  login: () => Promise<void>
  devLogin: (identity: DevIdentity) => void
  complete: () => Promise<void>
  logout: () => Promise<void>
}

const DEV_SESSION = 'lec-blog:dev-session'
const AuthContext = createContext<Auth | null>(null)

function oidcSession(user: User): Session {
  return {
    userId: user.profile.sub,
    displayName: user.profile.name || user.profile.preferred_username || user.profile.sub,
    headers: { Authorization: 'Bearer ' + user.access_token },
  }
}

function devSession(identity: DevIdentity): Session {
  return {
    userId: identity.userId,
    displayName: identity.displayName || identity.username || identity.userId,
    headers: {
      'X-Dev-User-Id': identity.userId,
      'X-Dev-Username': identity.username,
      'X-Dev-Display-Name': identity.displayName,
      'X-Dev-Captain': String(identity.isCaptain),
    },
  }
}

function restoredDevSession(): Session | null {
  try {
    const identity: DevIdentity = JSON.parse(sessionStorage.getItem(DEV_SESSION) || 'null')
    return identity?.userId ? devSession(identity) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<WebConfig | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    void api<WebConfig>('/public/config', { signal: controller.signal }).then(setConfig).catch((error) => {
      if (!controller.signal.aborted) setError(errorText(error))
    })
    return () => controller.abort()
  }, [])
  if (error) return <div className="startup"><Message tone="error" action={{ label: '重试', onClick: () => location.reload() }}>{error}</Message></div>
  if (!config) return <div className="startup" aria-busy><Skeleton width={260} height={30} /></div>
  return <SessionProvider config={config}>{children}</SessionProvider>
}

function SessionProvider({ config, children }: { config: WebConfig; children: ReactNode }) {
  const manager = useMemo(() => config.webClientId ? new UserManager({
    authority: config.issuer,
    client_id: config.webClientId,
    redirect_uri: location.origin + '/callback',
    response_type: 'code',
    scope: 'openid profile email urn:logto:scope:organizations urn:logto:scope:organization_roles',
    extraQueryParams: { organization_id: config.orgId },
    userStore: new WebStorageStateStore({ store: sessionStorage }),
    stateStore: new WebStorageStateStore({ store: sessionStorage }),
    automaticSilentRenew: false,
    monitorSession: false,
  }) : null, [config])

  const [session, setSession] = useState<Session | null>(() => config.mockEnabled ? restoredDevSession() : null)
  const [ready, setReady] = useState(!manager)

  useEffect(() => {
    if (!manager) return
    let active = true
    const expired = () => setSession(null)
    const unsubscribe = manager.events.addAccessTokenExpired(expired)
    void manager.getUser().then((user) => {
      if (active && user && !user.expired) {
        setSession(oidcSession(user))
      }
    }).catch(() => {
      if (active) void manager.removeUser()
    }).finally(() => {
      if (active) setReady(true)
    })
    return () => {
      active = false
      unsubscribe()
    }
  }, [manager])

  if (!ready) return <div className="startup" aria-busy><Skeleton width={260} height={30} /></div>

  const value: Auth = {
    config,
    session,
    async login() {
      if (!manager) throw new Error('写作台尚未配置 LecSSO Web 客户端')
      await manager.signinRedirect()
    },
    devLogin(identity) {
      if (!config.mockEnabled) return
      sessionStorage.setItem(DEV_SESSION, JSON.stringify(identity))
      setSession(devSession(identity))
    },
    async complete() {
      if (!manager) throw new Error('写作台尚未配置 LecSSO Web 客户端')
      const user = await manager.signinRedirectCallback()
      setSession(oidcSession(user))
    },
    async logout() {
      sessionStorage.removeItem(DEV_SESSION)
      await manager?.removeUser()
      setSession(null)
    },
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): Auth {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider is missing')
  return context
}

export function DevLoginForm({ done }: { done?: () => void }) {
  const { devLogin } = useAuth()
  const [identity, setIdentity] = useState<DevIdentity>({ userId: '', username: '', displayName: '', isCaptain: false })
  const [error, setError] = useState('')
  return (
    <form className="field-stack" onSubmit={(event) => {
      event.preventDefault()
      const values = [identity.userId.trim(), identity.username.trim(), identity.displayName.trim()]
      if (!values[0] || values.some((value) => /[^\x20-\x7E]/.test(value))) {
        setError('请填写成员 ID，模拟身份字段仅使用 ASCII 字符。')
        return
      }
      devLogin({ ...identity, userId: values[0], username: values[1], displayName: values[2] })
      done?.()
    }}>
      <FormField label="成员 ID" required>
        {(props) => <Input {...props} value={identity.userId} maxLength={128} variant="fill"
          onValueChange={(userId) => setIdentity({ ...identity, userId })} />}
      </FormField>
      <FormField label="用户名">
        {(props) => <Input {...props} value={identity.username} maxLength={128} variant="fill"
          onValueChange={(username) => setIdentity({ ...identity, username })} />}
      </FormField>
      <FormField label="显示名">
        {(props) => <Input {...props} value={identity.displayName} maxLength={128} variant="fill"
          onValueChange={(displayName) => setIdentity({ ...identity, displayName })} />}
      </FormField>
      <FormField label="模拟队长">
        {(props) => <Switch {...props} checked={identity.isCaptain}
          onCheckedChange={(isCaptain) => setIdentity({ ...identity, isCaptain })} />}
      </FormField>
      {error && <Message tone="error">{error}</Message>}
      <div><Button type="submit" variant="filled">进入写作台</Button></div>
    </form>
  )
}

export function WriterLogin() {
  const { config, login } = useAuth()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function signIn() {
    setBusy(true)
    try { await login() }
    catch (error) { setError(errorText(error)); setBusy(false) }
  }
  return (
    <Card className="login-card" padding="large">
      <h1>登录写作台</h1>
      <p className="muted">使用工作室账户管理你的博客与草稿。</p>
      {config.mockEnabled ? <DevLoginForm /> : (
        <Button variant="filled" loading={busy} onClick={() => void signIn()}>使用 LecSSO 登录</Button>
      )}
      {error && <Message tone="error">{error}</Message>}
    </Card>
  )
}
