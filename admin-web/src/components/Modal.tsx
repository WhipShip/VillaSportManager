import { useEffect, useRef, type ReactNode } from 'react'

/** Accessible modal: focus moves in, Escape or backdrop click closes. */
export default function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const first = ref.current?.querySelector<HTMLElement>('select, input, button')
    first?.focus()
    return () => previous?.focus()
  }, [])

  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={ref}
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        onKeyDown={(e) => e.key === 'Escape' && onClose()}
      >
        <h2 id="dialog-title">{title}</h2>
        {children}
      </div>
    </div>
  )
}
