import { isValidElement, useRef, type ReactNode } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import { Button, useToast } from '@lec-space/liquidglass'
import { Copy } from '@lec-space/icons'

const schema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    code: [['className', 'hljs', /^language-[a-z0-9_+-]+$/i]],
    span: [['className', /^hljs-[a-z0-9_-]+$/i]],
  },
  protocols: {
    ...defaultSchema.protocols,
    href: ['http', 'https', 'mailto'],
    src: ['http', 'https'],
  },
}

function CodeBlock({ children }: { children?: ReactNode }) {
  const code = useRef<HTMLPreElement>(null)
  const { toast } = useToast()
  const language = isValidElement<{ className?: string }>(children)
    ? children.props.className?.match(/\blanguage-([a-z0-9_+-]+)/i)?.[1] : undefined
  return (
    <div className="md-code-block">
      <div className="md-code-head">
        <span>{language || '代码'}</span>
        <Button size="small" variant="glass" icon={<Copy size={14} />} aria-label="复制代码" onClick={() => {
          if (!code.current) return
          void navigator.clipboard.writeText(code.current.textContent || '').then(() => toast({ title: '代码已复制', tone: 'success' }))
            .catch(() => toast({ title: '无法访问剪贴板', tone: 'error' }))
        }} />
      </div>
      <pre ref={code}>{children}</pre>
    </div>
  )
}

const components: Components = {
  pre({ children }) {
    return <CodeBlock>{children}</CodeBlock>
  },
  a({ children, href }) {
    const external = href?.startsWith('https://') || href?.startsWith('http://') || href?.startsWith('//')
    return href
      ? <a href={href} target={external ? '_blank' : undefined} rel={external ? 'noopener noreferrer' : undefined}>{children}</a>
      : <span>{children}</span>
  },
  img({ src, alt }) {
    return src ? <img src={src} alt={alt || ''} loading="lazy" referrerPolicy="no-referrer" /> : null
  },
  table({ children }) {
    return <div className="md-table"><table>{children}</table></div>
  },
}

export function Markdown({ content }: { content: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown skipHtml remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeHighlight, { ignoreMissing: true }], [rehypeSanitize, schema]]} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  )
}
