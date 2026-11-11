/** Small stroke icons (24×24). Decorative unless a label is given by the caller. */
const base = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true }

export const ChevronLeft = () => <svg {...base}><path d="m15 6-6 6 6 6" /></svg>
export const ChevronRight = () => <svg {...base}><path d="m9 6 6 6-6 6" /></svg>
export const Close = () => <svg {...base}><path d="M6 6l12 12M18 6 6 18" /></svg>
export const Plus = () => <svg {...base}><path d="M12 5v14M5 12h14" /></svg>
export const Block = () => <svg {...base}><circle cx="12" cy="12" r="9" /><path d="M5.6 5.6l12.8 12.8" /></svg>
