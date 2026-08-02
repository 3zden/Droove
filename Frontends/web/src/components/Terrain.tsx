import styles from './Terrain.module.css';

/**
 * Decorative mossy-terrain band in awesomic's organic-render style.
 *
 * Drawn with SVG filters rather than a bitmap: a coarse feTurbulence erodes the
 * bezier ridgelines so they read as landscape instead of smooth curves, a fine
 * one feeds feDiffuseLighting for the velvet moss micro-texture, and every
 * colour comes from CSS variables so it re-tints with the theme. No image bytes,
 * no second asset for dark mode, sharp at any size.
 *
 * One wide landscape - gentle hills left, a ridge peak right. Call sites crop it
 * with CSS to get either the full band or a close-up of the peak.
 *
 * Paths overshoot the viewBox on all sides; the displacement pass shifts edges by
 * up to ~half its scale, and the overshoot keeps that from exposing a gap.
 */
export function Terrain({ className }: { className?: string }) {
  return (
    <svg
      className={className ? `${styles.svg} ${className}` : styles.svg}
      viewBox="0 0 1200 400"
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id="droove-t1" className={styles.g1} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" className={styles.stopTop} />
          <stop offset="1" className={styles.stopBot} />
        </linearGradient>
        <linearGradient id="droove-t2" className={styles.g2} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" className={styles.stopTop} />
          <stop offset="1" className={styles.stopBot} />
        </linearGradient>
        <linearGradient id="droove-t3" className={styles.g3} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" className={styles.stopTop} />
          <stop offset="1" className={styles.stopBot} />
        </linearGradient>

        <filter id="droove-moss" x="-4%" y="-4%" width="108%" height="108%">
          {/* coarse noise erodes the ridgelines - smooth beziers read as clip-art */}
          <feTurbulence type="fractalNoise" baseFrequency="0.006 0.017" numOctaves="4" seed="4" result="warp" />
          <feDisplacementMap
            in="SourceGraphic"
            in2="warp"
            scale="42"
            xChannelSelector="R"
            yChannelSelector="G"
            result="hills"
          />

          {/* Fine noise, deliberately anisotropic - narrow in x, stretched in y - so it
              resolves into vertical erosion gullies rather than generic mottling. That
              directional nap is what makes the slopes read as moss instead of marble. */}
          <feTurbulence type="fractalNoise" baseFrequency="0.32 0.04" numOctaves="4" seed="11" result="grain" />
          <feDiffuseLighting in="grain" surfaceScale="5" diffuseConstant="1" result="lit">
            <feDistantLight azimuth="228" elevation="68" />
          </feDiffuseLighting>
          {/* compress the lighting range so multiply textures the fill instead of blackening it */}
          <feComponentTransfer in="lit" result="litSoft">
            <feFuncR type="linear" slope="0.72" intercept="0.28" />
            <feFuncG type="linear" slope="0.72" intercept="0.28" />
            <feFuncB type="linear" slope="0.72" intercept="0.28" />
          </feComponentTransfer>

          <feComposite in="litSoft" in2="hills" operator="in" result="moss" />
          <feBlend in="moss" in2="hills" mode="multiply" />
        </filter>
      </defs>

      <g filter="url(#droove-moss)">
        {/* back ridge - hazier, reads as distance */}
        <path
          fill="url(#droove-t1)"
          d="M-40 470 L-40 300 C120 285 200 302 300 292 C420 280 520 302 600 284 C700 262 762 208 830 168 C900 126 942 58 1012 44 C1082 30 1132 92 1240 68 L1240 470 Z"
        />
        <path
          fill="url(#droove-t2)"
          d="M-40 470 L-40 332 C100 320 182 342 282 333 C400 322 480 350 570 331 C680 308 742 270 822 234 C902 198 952 144 1032 134 C1112 124 1152 166 1240 150 L1240 470 Z"
        />
        {/* front ridge - most saturated, closest to the viewer */}
        <path
          fill="url(#droove-t3)"
          d="M-40 470 L-40 362 C90 353 172 370 262 363 C382 354 470 376 560 363 C670 347 752 320 842 294 C932 268 992 224 1072 219 C1142 215 1162 241 1240 232 L1240 470 Z"
        />
      </g>
    </svg>
  );
}
