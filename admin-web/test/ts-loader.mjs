// Node 24 strips TypeScript types natively but does not guess file extensions.
// This loader resolves the extensionless relative imports used by the Vite sources
// (e.g. `import { clubIso } from './time'`) to the `.ts` file so `node --test` can run them.
export async function resolve(specifier, context, next) {
  const relative = specifier.startsWith('./') || specifier.startsWith('../')
  if (relative && !/\.[cm]?[jt]sx?$/.test(specifier)) {
    try {
      return await next(specifier + '.ts', context)
    } catch {
      /* fall through to the default resolution */
    }
  }
  return next(specifier, context)
}
