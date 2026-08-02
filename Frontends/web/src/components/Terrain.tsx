import styles from './Terrain.module.css';

export type TerrainVariant = 'band' | 'moss' | 'mist';

/**
 * Decorative terrain artwork, in awesomic's organic-render language.
 *
 * The three variants are different framings of the same motif, each with the
 * fade baked in so call sites only have to position a box:
 *
 *  - band  hills rising off the bottom against nothing. The source render had a
 *          flat white sky; it was keyed to alpha at build time, so the ridgeline
 *          feathers into whatever sits behind it instead of only working on
 *          white. That alpha IS the fade for this variant.
 *  - moss  dense close-up texture, dissolving leftward into its container. For
 *          cards where the artwork bleeds off one edge.
 *  - mist  misty hills anchored to the bottom, dissolving upward. For giving a
 *          short empty state a floor.
 *
 * Dark mode dims through one filter token rather than a second set of files.
 */
const SOURCES: Record<TerrainVariant, string> = {
  band: '/terrain/hills.webp',
  moss: '/terrain/moss.webp',
  mist: '/terrain/mist.webp',
};

export function Terrain({ variant = 'band', className }: { variant?: TerrainVariant; className?: string }) {
  return (
    <img
      src={SOURCES[variant]}
      alt=""
      aria-hidden="true"
      /* the band is hero artwork and sits above the fold; the rest can wait */
      loading={variant === 'band' ? 'eager' : 'lazy'}
      decoding="async"
      className={[styles.img, styles[variant], className].filter(Boolean).join(' ')}
    />
  );
}
