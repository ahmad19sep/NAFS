/**
 * Generates placeholder NAFS brand icons as valid PNGs — pure Node.js, zero deps.
 * Run: node scripts/create-assets.js
 */
const zlib = require('zlib')
const fs   = require('fs')
const path = require('path')

// ─── CRC32 ────────────────────────────────────────────────────────────────────
const CRC_TABLE = (() => {
  const t = new Uint32Array(256)
  for (let i = 0; i < 256; i++) {
    let c = i
    for (let j = 0; j < 8; j++) c = (c & 1) ? 0xEDB88320 ^ (c >>> 1) : c >>> 1
    t[i] = c
  }
  return t
})()
function crc32(buf) {
  let c = 0xFFFFFFFF
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8)
  return (~c) >>> 0
}

// ─── PNG chunk builder ────────────────────────────────────────────────────────
function pngChunk(type, data) {
  const len  = Buffer.alloc(4); len.writeUInt32BE(data.length)
  const tb   = Buffer.from(type, 'ascii')
  const crcb = Buffer.alloc(4); crcb.writeUInt32BE(crc32(Buffer.concat([tb, data])))
  return Buffer.concat([len, tb, data, crcb])
}

// ─── Render a 512×512 NAFS icon ───────────────────────────────────────────────
function renderIcon(size) {
  const px    = new Uint8Array(size * size * 3)  // RGB flat
  const cx    = size / 2, cy = size / 2

  // colours
  const NAVY   = [0x0B, 0x1A, 0x2B]
  const TEAL   = [0x0F, 0x4C, 0x5C]
  const TEAL_L = [0x1A, 0x6B, 0x7E]
  const GOLD   = [0xC9, 0xA2, 0x27]
  const WHITE  = [0xF0, 0xF4, 0xF8]

  // helpers
  function set(x, y, c) {
    if (x < 0 || x >= size || y < 0 || y >= size) return
    const i = (y * size + x) * 3
    px[i] = c[0]; px[i+1] = c[1]; px[i+2] = c[2]
  }
  function fill(x0, y0, x1, y1, c) {
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) set(x, y, c)
  }
  function circle(ox, oy, r, c) {
    for (let y = -r; y <= r; y++)
      for (let x = -r; x <= r; x++)
        if (x*x + y*y <= r*r) set(ox+x, oy+y, c)
  }
  function roundRect(x0, y0, x1, y1, r, c) {
    fill(x0+r, y0,   x1-r, y1,   c)
    fill(x0,   y0+r, x1,   y1-r, c)
    circle(x0+r, y0+r, r, c); circle(x1-r-1, y0+r, r, c)
    circle(x0+r, y1-r-1, r, c); circle(x1-r-1, y1-r-1, r, c)
  }

  const s = size / 512  // scale factor (1 for 512px)

  // 1. Navy background
  fill(0, 0, size, size, NAVY)

  // 2. Teal rounded-rect card (the "app frame")
  const pad = Math.round(40*s), rad = Math.round(80*s)
  roundRect(pad, pad, size-pad, size-pad, rad, TEAL)

  // 3. Lighter teal inner highlight (top half, subtle)
  roundRect(pad+4, pad+4, size-pad-4, Math.floor(size/2), rad-4, TEAL_L)

  // 4. Draw "ن" (Nun) as a simplified pixel-art glyph in gold
  //    — we approximate the Arabic nun shape with arcs and a dot
  const gCx  = Math.round(cx)
  const gCy  = Math.round(cy + 20*s)
  const gR   = Math.round(100*s)
  const thick = Math.round(22*s)

  // outer arc (top half of circle) — the main bowl of ن
  for (let deg = 10; deg <= 170; deg++) {
    const rad2 = deg * Math.PI / 180
    for (let r2 = gR - thick; r2 <= gR; r2++) {
      const x = Math.round(gCx + r2 * Math.cos(rad2))
      const y = Math.round(gCy - r2 * Math.sin(rad2))
      set(x, y, GOLD)
    }
  }

  // horizontal tail stroke (bottom-left of ن)
  const tailY = gCy + Math.round(10*s)
  fill(gCx - gR, tailY, gCx - Math.round(20*s), tailY + thick, GOLD)

  // small upward tick at tail end
  fill(gCx - gR - Math.round(4*s), tailY - Math.round(30*s),
       gCx - gR + thick,            tailY + thick, GOLD)

  // dot below (the nuqta of ن)
  const dotR = Math.round(20*s)
  circle(gCx, gCy + gR - Math.round(10*s), dotR, GOLD)

  // 5. Subtle gold ring border around the whole icon
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const edge = Math.round(6*s)
    if (x < edge || x >= size-edge || y < edge || y >= size-edge) set(x, y, [0x1E, 0x34, 0x48])
  }

  return px
}

// ─── Encode RGBA pixels → PNG buffer ─────────────────────────────────────────
function encodePng(pixels, size) {
  // Build filtered scanlines (filter type 0 = None)
  const rows = []
  for (let y = 0; y < size; y++) {
    const row = Buffer.allocUnsafe(1 + size * 3)
    row[0] = 0
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 3
      row[1 + x*3]   = pixels[i]
      row[1 + x*3+1] = pixels[i+1]
      row[1 + x*3+2] = pixels[i+2]
    }
    rows.push(row)
  }

  const raw        = Buffer.concat(rows)
  const compressed = zlib.deflateSync(raw, { level: 6 })

  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8; ihdr[9] = 2  // 8-bit RGB

  return Buffer.concat([
    Buffer.from([0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', compressed),
    pngChunk('IEND', Buffer.alloc(0)),
  ])
}

// ─── Generate files ───────────────────────────────────────────────────────────
const assetsDir = path.join(__dirname, '..', 'assets')
if (!fs.existsSync(assetsDir)) fs.mkdirSync(assetsDir)

console.log('Generating NAFS brand icons…')

const px512 = renderIcon(512)
const png512 = encodePng(px512, 512)
fs.writeFileSync(path.join(assetsDir, 'icon.png'), png512)
console.log('  ✔  assets/icon.png          (512×512)')

// splash — same art, larger canvas with more padding
const px512s = renderIcon(512)
const splash = encodePng(px512s, 512)
fs.writeFileSync(path.join(assetsDir, 'splash.png'), splash)
console.log('  ✔  assets/splash.png        (512×512, extend via app.json resizeMode)')

fs.writeFileSync(path.join(assetsDir, 'adaptive-icon.png'), png512)
console.log('  ✔  assets/adaptive-icon.png (512×512)')

console.log('\nDone.  Replace with real 1024×1024 brand assets before submitting to stores.')
