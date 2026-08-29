// Run: node generate-icons.js
// Generates the Ascend SVG icon set for the PWA.
// Mark: twin gold chevrons rising over a navy→teal gradient — "ascend".

const fs = require('fs')
const path = require('path')

const sizes = [72, 96, 128, 144, 152, 192, 384, 512]

const svgTemplate = (size) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#0B1A2B"/>
      <stop offset="100%" stop-color="#0F4C5C"/>
    </linearGradient>
    <linearGradient id="gold" x1="0" y1="1" x2="0" y2="0">
      <stop offset="0%" stop-color="#9A7B1E"/>
      <stop offset="100%" stop-color="#E8C547"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="102" fill="url(#bg)"/>
  <path d="M256 132 L396 296 L342 296 L256 196 L170 296 L116 296 Z" fill="url(#gold)"/>
  <path d="M256 244 L372 380 L318 380 L256 308 L194 380 L140 380 Z" fill="url(#gold)" opacity="0.55"/>
</svg>`

// Master icon
fs.writeFileSync(path.join(__dirname, 'icon.svg'), svgTemplate(512))
console.log('Created icon.svg')

sizes.forEach(size => {
  fs.writeFileSync(path.join(__dirname, `icon-${size}x${size}.svg`), svgTemplate(size))
  console.log(`Created icon-${size}x${size}.svg`)
})
