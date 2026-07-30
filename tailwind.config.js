/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,jsx,ts,tsx}',
    './components/**/*.{js,jsx,ts,tsx}',
    './lib/**/*.{js,jsx,ts,tsx}',
  ],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        // NAFS brand palette
        navy:     '#0B1A2B',
        teal:     '#0F4C5C',
        gold:     '#C9A227',
        'gold-light': '#E5B83A',
        'teal-light': '#1A6B7E',
        // Semantic tokens
        background: '#0B1A2B',
        card:       '#0E2236',
        border:     '#1E3448',
        primary:    '#0F4C5C',
        muted:      '#1C3045',
        'muted-fg': '#6B8CA8',
        surface:    'rgba(255,255,255,0.05)',
      },
      fontFamily: {
        sans: ['Inter', 'System'],
        arabic: ['Arial Unicode MS', 'serif'],
      },
    },
  },
  plugins: [],
}
