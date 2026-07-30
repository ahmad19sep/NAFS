// Run: node generate-icons.js
// Generates simple SVG-based PNG icons for the PWA
// In production, replace with proper designed icons

const fs = require('fs')
const path = require('path')

const sizes = [72, 96, 128, 144, 152, 192, 384, 512]

const svgTemplate = (size) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect width="${size}" height="${size}" rx="${size * 0.2}" fill="#0F4C5C"/>
  <text x="50%" y="52%" dominant-baseline="middle" text-anchor="middle"
        font-family="Arial, sans-serif" font-size="${size * 0.45}" fill="#C9A227">ن</text>
</svg>`

sizes.forEach(size => {
  const svgContent = svgTemplate(size)
  fs.writeFileSync(path.join(__dirname, `icon-${size}x${size}.svg`), svgContent)
  console.log(`Created icon-${size}x${size}.svg`)
})

console.log('\nSVG icons created. To convert to PNG, use ImageMagick:')
console.log('  magick icon-192x192.svg icon-192x192.png')
console.log('\nOr use: https://cloudconvert.com/svg-to-png')
