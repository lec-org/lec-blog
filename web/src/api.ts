import { useEffect, useState } from 'react'

export type BlogMode = 'HOSTED' | 'EXTERNAL'
export type PostStatus = 'DRAFT' | 'PUBLISHED'

export interface WebConfig {
  issuer: string
  orgId: string
  webClientId: string
  publicUrl: string
  mockEnabled: boolean
  imageUploadEnabled: boolean
}

export interface Member {
  userId: string
  username: string
  displayName: string
  avatarUrl: string
  isCaptain: boolean
  blogUrl: string
  blogTitle: string
  bio: string
  tags: string[]
  rssUrl: string | null
  hasRecentPost: boolean
  latestPostTitle: string | null
  latestPostUrl: string | null
  latestPostAt: string | null
  blogMode: BlogMode
  hostedSlug: string | null
}

export interface MyBlog extends Member {
  status: 'ACTIVE' | 'HIDDEN'
}

export interface Page<T> {
  page: number
  size: number
  total: number
  items: T[]
}

export interface PublicPost {
  id: number
  userId: string
  username: string
  displayName: string
  avatarUrl: string
  blogTitle: string
  title: string
  postUrl: string
  summary: string
  sourceType: 'RSS' | 'NATIVE'
  publishedAt: string
  hostedSlug: string | null
  slug: string | null
  coverUrl: string | null
  readingMinutes: number | null
}

export interface Homepage {
  author: Member
  rssUrl: string
  posts: Page<PublicPost>
}

export interface Neighbor {
  slug: string
  title: string
  postUrl: string
}

export interface Article {
  slug: string
  title: string
  summary: string
  coverUrl: string | null
  contentMd: string
  postUrl: string
  publishedAt: string
  updatedAt: string
  author: Member
  previous: Neighbor | null
  next: Neighbor | null
}

export interface PostForm {
  title: string
  slug: string
  summary: string
  coverUrl: string
  contentMd: string
}

export interface NativePostItem {
  id: number
  title: string
  slug: string
  summary: string
  coverUrl: string | null
  postUrl: string
  postStatus: PostStatus
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface NativePost extends NativePostItem {
  contentMd: string
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (typeof options.body === 'string') headers.set('Content-Type', 'application/json')
  const response = await fetch('/api/v1' + path, { ...options, headers })
  const envelope = await response.json().catch(() => null)
  if (!response.ok || !envelope || envelope.code !== 0) {
    throw new ApiError(response.status, envelope?.message || '博客服务暂时无法处理请求')
  }
  return envelope.data as T
}

type QueryState<T> = { path: string; data?: T; error?: Error }

export function useQuery<T>(path: string, headers?: Record<string, string>, revision = 0) {
  const [state, setState] = useState<QueryState<T> | null>(null)
  useEffect(() => {
    const controller = new AbortController()
    setState(null)
    void api<T>(path, { signal: controller.signal, headers })
      .then((data) => {
        if (!controller.signal.aborted) setState({ path, data })
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setState({ path, error })
      })
    return () => controller.abort()
  }, [path, headers, revision])
  return state?.path === path ? state : null
}

export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : '操作未完成，请稍后重试'
}
