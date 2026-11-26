// Erzeugt die 6 Start-Packs von Nugget Style komplett selbst (keine fremden Texturen) und eine Vorschau-Seite.
// Aufruf: node tools/gen_styles.js <Ausgabe-Ordner>
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const OUT = process.argv[2] || "build/styles";
fs.mkdirSync(OUT, { recursive: true });

// ------------------------------------------------------------ Technik
const CRC = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c; });
const crc32 = b => { let c = -1; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
function chunk(t, d) { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const b = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(b)); return Buffer.concat([l, b, c]); }
/** Bild: {w,h,p:Uint8Array RGBA} -> PNG (Signatur als tEXt, keine Zeitstempel) */
function png(img, rgbOnly) {
    const { w, h, p } = img, bpp = rgbOnly ? 3 : 4, raw = Buffer.alloc((w * bpp + 1) * h);
    for (let y = 0, o = 0; y < h; y++) { raw[o++] = 0; for (let x = 0; x < w; x++) { const i = (y * w + x) * 4; raw[o++] = p[i]; raw[o++] = p[i + 1]; raw[o++] = p[i + 2]; if (!rgbOnly) raw[o++] = p[i + 3]; } }
    const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = rgbOnly ? 2 : 6;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("tEXt", Buffer.from("Comment\0made by Diamond_Nugget", "latin1")), chunk("IEND", Buffer.alloc(0))]);
}
/** Zip ohne Zeitstempel (1980) */
function zip(files) {
    const parts = [], central = []; let off = 0;
    for (const [name, data] of files) {
        const n = Buffer.from(name), d = zlib.deflateRawSync(data, { level: 9 }), c = crc32(data), h = Buffer.alloc(30);
        h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(8, 8); h.writeUInt16LE(0x21, 12); h.writeUInt32LE(c, 14); h.writeUInt32LE(d.length, 18); h.writeUInt32LE(data.length, 22); h.writeUInt16LE(n.length, 26);
        parts.push(h, n, d);
        const e = Buffer.alloc(46); e.writeUInt32LE(0x02014b50, 0); e.writeUInt16LE(20, 4); e.writeUInt16LE(20, 6); e.writeUInt16LE(8, 10); e.writeUInt16LE(0x21, 14); e.writeUInt32LE(c, 16); e.writeUInt32LE(d.length, 20); e.writeUInt32LE(data.length, 24); e.writeUInt16LE(n.length, 28); e.writeUInt32LE(off, 42);
        central.push(e, n); off += 30 + n.length + d.length;
    }
    const cd = Buffer.concat(central), end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10); end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(off, 16);
    return Buffer.concat([...parts, cd, end]);
}
const hex = c => [(c >> 16) & 255, (c >> 8) & 255, c & 255];
const mix = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; };
const clamp = v => Math.max(0, Math.min(1, v));
const smooth = t => t * t * (3 - 2 * t);
function hash(x, y, z, s) { let h = (x * 374761393 + y * 668265263 + z * 2147483647 + s * 1274126177) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function noise3(x, y, z, s) { const X = Math.floor(x), Y = Math.floor(y), Z = Math.floor(z), u = smooth(x - X), v = smooth(y - Y), w = smooth(z - Z); const L = (a, b, t) => a + (b - a) * t; return L(L(L(hash(X, Y, Z, s), hash(X + 1, Y, Z, s), u), L(hash(X, Y + 1, Z, s), hash(X + 1, Y + 1, Z, s), u), v), L(L(hash(X, Y, Z + 1, s), hash(X + 1, Y, Z + 1, s), u), L(hash(X, Y + 1, Z + 1, s), hash(X + 1, Y + 1, Z + 1, s), u), v), w); }
const fbm = (x, y, z, s) => { let v = 0, a = 0.5, f = 1; for (let i = 0; i < 4; i++) { v += a * noise3(x * f, y * f, z * f, s + i); a *= 0.5; f *= 2; } return v / 0.9375; };
function tile(x, y, per, s) { const X = Math.floor(x), Y = Math.floor(y), u = smooth(x - X), v = smooth(y - Y), H = (a, b) => hash(((a % per) + per) % per, ((b % per) + per) % per, 7, s), L = (a, b, t) => a + (b - a) * t; return L(L(H(X, Y), H(X + 1, Y), u), L(H(X, Y + 1), H(X + 1, Y + 1), u), v); }
function image(w, h, f) { const p = new Uint8Array(w * h * 4); for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = f(x, y); if (!c) continue; const i = (y * w + x) * 4; p[i] = Math.round(Math.max(0, Math.min(255, c[0]))); p[i + 1] = Math.round(Math.max(0, Math.min(255, c[1]))); p[i + 2] = Math.round(Math.max(0, Math.min(255, c[2]))); p[i + 3] = c[3] === undefined ? 255 : c[3]; } return { w, h, p }; }

// ------------------------------------------------------------ Die 6 Packs
const stars = (d, s, dens, bright) => { const q = 90, H = hash(Math.floor(d[0] * q), Math.floor(d[1] * q), Math.floor(d[2] * q), s); return H > 1 - dens ? bright * (H - (1 - dens)) / dens : 0; };
const norm = v => { const l = Math.hypot(...v); return v.map(a => a / l); };
const glowAt = (d, sun, pow) => Math.pow(clamp(d[0] * sun[0] + d[1] * sun[1] + d[2] * sun[2]), pow);
const PACKS = [
    {
        id: "dino_nugget", name: "Dino-Nugget", acc: 0xF5A524, dark: 0x3A2208, mid: 0x9A5F1C, light: 0xFFD58A, panel: 0xC9A06A, fire: [0x8A3A08, 0xE07A12, 0xFFC23D, 0xFFF0B0],
        sky(d) {
            const sun = norm([0.6, 0.18, -0.75]), h = clamp(d[1] * 1.3 + 0.1);
            let c = mix(mix(hex(0xFFB347), hex(0xB5552A), smooth(clamp(h * 2.4))), hex(0x2E1A3D), smooth(clamp((h - 0.3) * 1.5)));
            c = mix(c, hex(0xFFE9A8), Math.pow(glowAt(d, sun, 18), 1) * 0.9);
            const n = fbm(d[0] * 3 / (Math.abs(d[1]) + 0.35), d[2] * 3 / (Math.abs(d[1]) + 0.35), 2, 3);
            if (d[1] > -0.05) c = mix(c, hex(0xFFCF8A), clamp((n - 0.56) * 4) * 0.55 * clamp(1 - h * 1.1));
            return c;
        }
    },
    {
        id: "fritteuse", name: "Fritteuse", acc: 0xFF6A2B, dark: 0x1E0D07, mid: 0x7A2F12, light: 0xFFB27A, panel: 0xA86A4A, fire: [0x6A1F04, 0xD8440C, 0xFF8A1E, 0xFFD27A],
        sky(d) {
            const h = clamp(d[1] * 1.4 + 0.08);
            let c = mix(hex(0xE8571A), hex(0x2A120C), smooth(clamp(h * 2.1)));
            c = mix(c, hex(0x0C0605), smooth(clamp((h - 0.45) * 1.6)));
            const n = fbm(d[0] * 2.6 + 4, d[1] * 5, d[2] * 2.6, 11);
            c = mix(c, hex(0x151010), clamp((n - 0.48) * 3.2) * 0.75);
            const e = fbm(d[0] * 9, d[1] * 9, d[2] * 9, 17);
            if (e > 0.74 && h < 0.6) c = mix(c, hex(0xFFB347), (e - 0.74) * 5 * (1 - h));
            return c;
        }
    },
    {
        id: "jurassic_nacht", name: "Jurassic Nacht", acc: 0xE0A53A, dark: 0x0D1F12, mid: 0x2F5E34, light: 0xBFE08A, panel: 0x7E9A6A, fire: [0x1F5E1A, 0x4FB02A, 0xB6E03C, 0xF0FFB0],
        sky(d) {
            const h = clamp(d[1] * 1.3 + 0.1);
            let c = mix(hex(0x2E6B3A), hex(0x07150E), smooth(clamp(h * 2.2)));
            const moon = norm([-0.55, 0.55, 0.62]), g = glowAt(d, moon, 900);
            c = mix(c, hex(0xE8E2B0), glowAt(d, moon, 30) * 0.28);
            c = mix(c, hex(0xFFF7D0), smooth(clamp(g * 3)));
            const s = stars(d, 5, 0.012, 1) * clamp(h * 3);
            c = mix(c, hex(0xF2FFD8), s);
            const n = fbm(d[0] * 4 + 9, d[1] * 8, d[2] * 4, 21);
            if (h < 0.35) c = mix(c, hex(0x8FD06A), clamp((n - 0.55) * 3) * 0.35 * (1 - h * 2.8));
            return c;
        }
    },
    {
        id: "ketchup_mayo", name: "Ketchup & Mayo", acc: 0xE3262B, dark: 0x240808, mid: 0x8E1518, light: 0xF4E7C5, panel: 0xC08A6A, fire: [0x7A0A0E, 0xD91F26, 0xFF6A4A, 0xF4E7C5],
        sky(d) {
            const h = clamp(d[1] * 1.3 + 0.1);
            let c = mix(hex(0xB3141B), hex(0x220407), smooth(clamp(h * 1.9)));
            const k = Math.abs(d[1]) + 0.3, n = fbm(d[0] * 2.5 / k + 3, d[2] * 2.5 / k, 5, 31);
            if (d[1] > -0.1) c = mix(c, hex(0xF4E7C5), clamp((n - 0.53) * 3.6) * 0.85 * clamp(1.2 - h));
            return c;
        }
    },
    {
        id: "meteor", name: "Meteor", acc: 0xFF9A3C, dark: 0x0F1230, mid: 0x3A3E7A, light: 0xCFC8FF, panel: 0x8A86B8, fire: [0x12309A, 0x2A7CE0, 0x6FD0FF, 0xE6FBFF],
        sky(d) {
            const h = clamp(d[1] * 1.2 + 0.2);
            let c = mix(hex(0x1A1848), hex(0x05060F), smooth(clamp(h * 1.6)));
            const n = fbm(d[0] * 2.2 + 2, d[1] * 2.2, d[2] * 2.2, 41), m = fbm(d[0] * 3 + 8, d[1] * 3, d[2] * 3, 47);
            c = mix(c, hex(0x6A2FC0), clamp((n - 0.5) * 3.2) * 0.7);
            c = mix(c, hex(0xE0568A), clamp((m - 0.6) * 4) * 0.5);
            c = mix(c, hex(0xFFFFFF), stars(d, 9, 0.02, 1));
            const dir = norm([0.5, -0.32, 0.8]), ax = norm([0.45, 0.42, -0.79]), t = d[0] * ax[0] + d[1] * ax[1] + d[2] * ax[2];
            const off = Math.hypot(d[0] - ax[0] * t, d[1] - ax[1] * t, d[2] - ax[2] * t) ;
            if (t > 0.72 && t < 1) { const dist = (d[0] - ax[0]) * dir[0] + (d[1] - ax[1]) * dir[1] + (d[2] - ax[2]) * dir[2]; const perp = Math.hypot(d[0] - ax[0] - dir[0] * dist, d[1] - ax[1] - dir[1] * dist, d[2] - ax[2] - dir[2] * dist); if (dist > 0 && dist < 0.5) c = mix(c, hex(0xFFB35A), clamp(1 - perp / (0.018 * (1 - dist * 1.6) + 0.004)) * (1 - dist * 1.9)); }
            return c;
        }
    },
    {
        id: "eiszeit", name: "Eiszeit", acc: 0x7FDCFF, dark: 0x0A2230, mid: 0x356E86, light: 0xE4F8FF, panel: 0x9CC4D4, fire: [0x0E5C90, 0x2FB0E8, 0x9CE8FF, 0xFFFFFF],
        sky(d) {
            const h = clamp(d[1] * 1.3 + 0.1);
            let c = mix(hex(0x3F7E96), hex(0x051520), smooth(clamp(h * 2)));
            c = mix(c, hex(0xFFFFFF), stars(d, 13, 0.012, 0.9) * clamp(h * 3));
            const a = Math.atan2(d[0], d[2]), wave = Math.sin(a * 3 + fbm(d[0] * 2, 1, d[2] * 2, 51) * 6) * 0.08;
            const band = Math.exp(-Math.pow((d[1] - 0.42 - wave) / 0.16, 2)), curtain = fbm(a * 7 + 40, d[1] * 2, 3, 57);
            c = mix(c, hex(0x4CFFB0), band * clamp((curtain - 0.32) * 2.4) * 0.78);
            c = mix(c, hex(0x9A6CFF), Math.exp(-Math.pow((d[1] - 0.66 - wave) / 0.11, 2)) * clamp((curtain - 0.4) * 2.4) * 0.5);
            return c;
        }
    }
];

// ------------------------------------------------------------ Himmel neu gemalt: Wolken auf einer Ebene, weiche Verläufe
const sstep = (a, b, x) => smooth(clamp((x - a) / (b - a)));
const f6 = (x, y, z, s) => { let v = 0, a = 0.5, f = 1, t = 0; for (let i = 0; i < 6; i++) { v += a * noise3(x * f, y * f, z * f, s + i); t += a; a *= 0.5; f *= 2.03; } return v / t; };
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const add = (c, col, k) => [c[0] + col[0] * k, c[1] + col[1] * k, c[2] + col[2] * k];
function grad(h, stops) { let i = 0; while (i < stops.length - 2 && h > stops[i + 1][0]) i++; const [a, ca] = stops[i], [b, cb] = stops[i + 1]; return mix(hex(ca), hex(cb), smooth(clamp((h - a) / (b - a)))); }
function cloudAt(d, s, scale, cover, soft) { if (d[1] <= 0.015) return [0, 0]; const k = 1 / (d[1] + 0.16), x = d[0] * k * scale, z = d[2] * k * scale, w = f6(x * 0.5, z * 0.5, 3, s + 50) * 1.4, n = f6(x + w, z - w, 1, s), n2 = f6(x + w + 0.35, z - w + 0.2, 1, s); return [sstep(cover, cover + soft, n) * sstep(0.015, 0.16, d[1]), clamp((n - n2) * 5 + 0.55)]; }
function roundStars(d, s, dens) { const q = 220, X = Math.floor(d[0] * q), Y = Math.floor(d[1] * q), Z = Math.floor(d[2] * q), H = hash(X, Y, Z, s); if (H < 1 - dens) return 0; const dist = Math.hypot(d[0] * q - X - 0.5, d[1] * q - Y - 0.5, d[2] * q - Z - 0.5); return Math.pow((H - (1 - dens)) / dens, 2) * clamp(1.3 - dist * 2.4); }
const disk = (v, cosR, edge) => sstep(cosR - edge, cosR + edge, v);
const SKIES = {
    dino_nugget(d) {
        const h = d[1], sun = norm([0.62, 0.2, -0.75]), v = clamp(dot(d, sun));
        let c = h < 0 ? mix(hex(0xE89A5C), hex(0x4A3428), sstep(0, -0.35, h)) : grad(h, [[0, 0xFFC27A], [0.08, 0xF79A6A], [0.25, 0xB98AA8], [0.5, 0x5C75B8], [1, 0x23386F]]);
        c = mix(c, hex(0xFFD9A0), Math.pow(v, 8) * 0.55);
        c = mix(c, hex(0xFFF1C8), Math.pow(v, 90) * 0.9);
        const [den, lit] = cloudAt(d, 61, 1.6, 0.5, 0.16);
        c = mix(c, mix(hex(0xC9788A), hex(0xFFE7C4), lit), den * 0.92);
        return mix(c, hex(0xFFFDF0), disk(v, 0.9985, 0.0004));
    },
    fritteuse(d) {
        const h = d[1];
        let c = h < 0 ? mix(hex(0x7A2410), hex(0x1E0B06), sstep(0, -0.3, h)) : grad(h, [[0, 0xFF8A2A], [0.1, 0xE0461A], [0.3, 0x6E1E14], [0.6, 0x26100E], [1, 0x110909]]);
        const [den, lit] = cloudAt(d, 71, 1.3, 0.46, 0.2);
        c = mix(c, mix(hex(0x140C0C), hex(0xFF7A2E), lit * lit * clamp(1 - h * 1.4)), den * 0.95);
        return add(c, hex(0xFFB060), roundStars(d, 73, 0.012) * clamp(1 - h * 1.6) * (h > 0 ? 1 : 0));
    },
    jurassic_nacht(d) {
        const h = d[1], moon = norm([-0.55, 0.55, 0.62]), v = clamp(dot(d, moon));
        let c = h < 0 ? mix(hex(0x1F5A48), hex(0x04100E), sstep(0, -0.3, h)) : grad(h, [[0, 0x2F7A5A], [0.12, 0x17493F], [0.4, 0x0B2430], [1, 0x050C18]]);
        c = add(c, hex(0xFFFFFF), roundStars(d, 81, 0.05) * sstep(0.05, 0.3, h) * 0.9);
        c = mix(c, hex(0xCFE8C0), Math.pow(v, 60) * 0.3);
        const crater = f6(d[0] * 26, d[1] * 26, d[2] * 26, 83);
        c = mix(c, mix(hex(0xB9C49A), hex(0xFFFBE0), sstep(0.38, 0.62, crater)), disk(v, 0.9935, 0.0006));
        const [den, lit] = cloudAt(d, 85, 1.8, 0.56, 0.2);
        return mix(c, mix(hex(0x0F2F33), hex(0x9FE0B8), lit * clamp(Math.pow(v, 2) + 0.15)), den * 0.5);
    },
    ketchup_mayo(d) {
        const h = d[1];
        let c = h < 0 ? mix(hex(0xB02A28), hex(0x2A0507), sstep(0, -0.3, h)) : grad(h, [[0, 0xFF8A5A], [0.08, 0xEA3F33], [0.35, 0xA81424], [1, 0x3E0612]]);
        const [den, lit] = cloudAt(d, 91, 1.4, 0.5, 0.12);
        return mix(c, mix(hex(0xC8534A), hex(0xFFF4DC), lit), den * 0.97);
    },
    meteor(d) {
        const a = f6(d[0] * 2.4 + 2, d[1] * 2.4, d[2] * 2.4, 101), b = f6(d[0] * 3.1 + 8, d[1] * 3.1, d[2] * 3.1, 107), e = f6(d[0] * 1.7 - 5, d[1] * 1.7, d[2] * 1.7, 113);
        let c = hex(0x05060F);
        c = mix(c, hex(0x24328F), sstep(0.42, 0.7, e) * 0.8);
        c = mix(c, hex(0x7A2FD0), sstep(0.46, 0.72, a) * 0.85);
        c = mix(c, hex(0xF0609A), sstep(0.58, 0.76, b) * sstep(0.45, 0.65, a) * 0.8);
        c = add(c, hex(0xFFFFFF), roundStars(d, 117, 0.07) * (1 - sstep(0.55, 0.8, a) * 0.6));
        const head = norm([0.4, 0.5, -0.77]), t0 = norm([0.6, -0.5, 0.1]), n = norm([head[1] * t0[2] - head[2] * t0[1], head[2] * t0[0] - head[0] * t0[2], head[0] * t0[1] - head[1] * t0[0]]);
        const along = dot(d, t0) - dot(head, t0), perp = Math.abs(dot(d, n));
        if (dot(d, head) > 0.7 && along > 0 && along < 0.5) { const k = 1 - along / 0.5; c = mix(c, hex(0xFFD6A8), clamp(1 - perp / (0.009 * k + 0.0015)) * k); }
        return c;
    },
    eiszeit(d) {
        const h = d[1];
        let c = h < 0 ? mix(hex(0x1E5668), hex(0x03101A), sstep(0, -0.3, h)) : grad(h, [[0, 0x1E5668], [0.15, 0x0E3446], [0.5, 0x071C2C], [1, 0x030B16]]);
        c = add(c, hex(0xFFFFFF), roundStars(d, 121, 0.05) * sstep(0.03, 0.25, h));
        const a = Math.atan2(d[0], d[2]), ca = Math.cos(a), sa = Math.sin(a);
        const base = 0.14 + 0.12 * f6(ca * 1.6, sa * 1.6, 2, 123), rays = f6(ca * 9, sa * 9, 1, 127), wide = f6(ca * 2.2, sa * 2.2, 5, 131);
        const up = (h - base) / 0.55;
        if (up > 0 && up < 1) { const env = Math.pow(1 - up, 1.7) * sstep(0, 0.05, up) * sstep(0.32, 0.62, wide); c = add(c, mix(hex(0x4CFFB0), hex(0x8A6CFF), sstep(0.15, 0.8, up)), env * sstep(0.34, 0.72, rays) * 0.95); }
        return c;
    }
};
for (const P of PACKS) P.sky = SKIES[P.id];
// leichtes Rauschen gegen Farbstreifen im Dunkeln
const dither = (c, x, y) => { const n = (hash(x, y, 3, 9) - 0.5) * 1.1; return [c[0] + n, c[1] + n, c[2] + n]; };

// ------------------------------------------------------------ Himmel (OptiFine-Blatt 3×2, kein Rand)
const FACE = [ // Aufbau wie OptiFine: unten, oben, Osten / Süden, Westen, Norden
    (u, v) => [v, -1, -u],
    (u, v) => [-v, 1, -u],
    (u, v) => [1, -v, u],
    (u, v) => [-u, -v, 1],
    (u, v) => [-1, -v, -u],
    (u, v) => [u, -v, -1],
];
function skySheet(pack, S) {
    return image(S * 3, S * 2, (x, y) => {
        const f = Math.floor(x / S) + Math.floor(y / S) * 3, u = (x % S + 0.5) / S * 2 - 1, v = (y % S + 0.5) / S * 2 - 1;
        return dither(pack.sky(norm(FACE[f](u, v))), x, y);
    });
}
function panorama(pack, w, h) {
    return image(w, h, (x, y) => {
        const yaw = (x / w) * Math.PI * 2, el = (1 - y / h) * 1.25 - 0.2;
        return pack.sky(norm([Math.sin(yaw) * Math.cos(el), Math.sin(el), Math.cos(yaw) * Math.cos(el)]));
    });
}

// ------------------------------------------------------------ Bausteine: 16×-Pixelkunst von Hand, ein Farbton in Abstufungen
const palette = P => ({ d: hex(P.dark), m: hex(P.mid), l: hex(P.light), a: hex(P.acc) });
const white = [255, 255, 255], black = [0, 0, 0];
function outline(img, col) { const { w, h, p } = img, a = (x, y) => x >= 0 && y >= 0 && x < w && y < h && p[(y * w + x) * 4 + 3] > 0, add = []; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (!a(x, y) && (a(x - 1, y) || a(x + 1, y) || a(x, y - 1) || a(x, y + 1))) add.push([x, y]); for (const [x, y] of add) { const i = (y * w + x) * 4; p[i] = col[0]; p[i + 1] = col[1]; p[i + 2] = col[2]; p[i + 3] = 255; } return img; }
function inPoly(poly, x, y) { let c = false; for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) { const a = poly[i], b = poly[j]; if ((a[1] > y) !== (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) c = !c; } return c; }
function seg(px, py, a, b) { const dx = b[0] - a[0], dy = b[1] - a[1], l = dx * dx + dy * dy, t = l ? clamp(((px - a[0]) * dx + (py - a[1]) * dy) / l) : 0; return { d: Math.hypot(px - a[0] - t * dx, py - a[1] - t * dy), t }; }
function edgeDist(poly, x, y) { let m = 99; for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) m = Math.min(m, seg(x, y, poly[j], poly[i]).d); return m; }
/** Farb-Rampe: o Umriss, d dunkel, m Mitte, l hell, h Glanz, a Detail, b Griff */
function ramp(P, tier) {
    const c = palette(P);
    return tier === "netherite"
        ? { o: mix(c.d, black, 0.6), d: mix(c.d, black, 0.2), m: mix(c.d, c.a, 0.2), l: mix(c.d, c.a, 0.48), h: mix(c.a, c.l, 0.25), a: mix(c.a, white, 0.15), b: mix(c.d, c.m, 0.45) }
        : { o: mix(c.a, c.d, 0.78), d: mix(c.a, c.d, 0.45), m: c.a, l: mix(c.a, c.l, 0.6), h: mix(c.l, white, 0.55), a: mix(c.a, c.d, 0.6), b: mix(c.d, c.m, 0.6) };
}
const draw = (rows, pal) => image(16, 16, (x, y) => { const ch = (rows[y] || "")[x]; return !ch || ch === "." ? null : pal[ch]; });
const SWORD = ["............oooo", "...........olhlo", "..........olhlmo", ".........olhlmo.", "........olhlmo..", ".......olhlmo...", "......olhlmo....", ".....olhlmo.....", "oo..olhlmo......", "oao.ohlmo.......", ".oaoolmo........", "..oaaoo.........", "..oobaao........", ".obbooaao.......", "oabo..oao.......", "ooo....o........"];
const PICK = ["....ooooooo.....", "...ohlllmmmoo...", "...oooooollmmo..", ".........oolmmo.", "........ob.oomo.", ".......obbo.olmo", "......obbo..olmo", ".....obbo...olmo", "....obbo.....oo.", "...obbo.........", "..obbo..........", ".obbo...........", "oabo............", "oo.............."];
const AXE = ["........oooo....", ".......ohllmoo..", "......ohlllmmmo.", "......olllmmmmmo", ".......ooomommmo", ".........obbo.mo", "........obbo..oo", ".......obbo.....", "......obbo......", ".....obbo.......", "....obbo........", "...obbo.........", "..obbo..........", ".obbo...........", "oabo............", "oo.............."];
const MACE = ["", ".........ooooo..", "........ohllmao.", ".......ohlllmmao", ".......olllmmmao", ".......oalmmmmao", "........oaammao.", ".......obooooo..", "......obbo......", ".....obbo.......", "....obbo........", "...obbo.........", "..obbo..........", ".oabo...........", "oaao............", "ooo............."];
const HELM = ["", "", "...oooooooooo...", "..ohhlllmmmmdo..", ".ohllmmmmmmmmdo.", ".ohlmmmmmmmmmdo.", ".olmaaaaaaaamdo.", ".olmmmmmmmmmmdo.", ".olmo......omdo.", ".olmo......omdo.", ".olmo......omdo.", ".oooo......oooo."];
const CHEST = ["", ".ooooo....ooooo.", "ohlllmo..omlmmdo", "ohllmmoooommmmdo", "ohlmmmllllmmmmdo", "oolmmlaaaalmmdoo", ".oolmahhhamlmoo.", "..olmlahalmmdo..", "..olmmlalmmmdo..", "..olmmmlmmmmdo..", "..olmmmmmmmmdo..", "..olmmmmmmmmdo..", "..olaaaaaaaado..", "..olmmmmmmmmdo..", "..oooooooooooo.."];
const LEGS = ["", "..oooooooooooo..", "..oaaaaaaaaaao..", "..ohlmmmmmmmdo..", "..olmmmmmmmmdo..", "..olmmmoommmdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..olmmo..ommdo..", "..ooooo..ooooo.."];
const BOOTS = ["", "", "", "", "", "", "..ooooo..ooooo..", "..ohlmo..omldo..", "..olmmo..ommdo..", "..olmmo..ommdo..", ".oolmmo..ommdoo.", "ohlllmo..omllldo", "oaaaaao..oaaaaao", "ooooooo..ooooooo"];
const GEM = ["", ".......oo.......", "......ohlo......", ".....ohllao.....", "....ohhlaaao....", "...ohhllaaamo...", "..ohllllaaammo..", ".oooooooooooooo.", ".ollllaaaammddo.", "..olllaaaammdo..", "...ollaaammdo...", "....olaaamdo....", ".....olamdo.....", "......oado......", ".......oo......."];
const PEARL = ["", "", ".....oooooo.....", "...oohhllaaoo...", "..ohhlaaaaaado..", ".ohlaaaddaaaado.", ".olaaddooddaado.", ".oaadooooodaado.", ".oaadooooodaado.", ".oaaddooddaaddo.", ".oaaaaddaaaaddo.", "..oaaaaaaaaddo..", "...oodaaaddoo...", ".....oooooo....."];
const APPLE = ["", ".........oo.....", "........omo.....", "....ooo.omooo...", "...ohllooaaado..", "..ohllaaaaaaado.", "..ohlaaaaaaaado.", "..olaaaaaaaaado.", "..olaaaaaaaaado.", "..oaaaaaaaaaddo.", "...oaaaaaaaddo..", "....oaaaaaddo...", ".....ooooooo...."];
const BOTTLE = ["", "", "......oooo......", "......olmo......", "......oooo......", ".....ohmmo......", "....ohmmmmo.....", "...ohmmmmmmo....", "...ohaaaaaao....", "...olaaaaaao....", "...olaaaaaao....", "...oaaaaaado....", "....oaaaado.....", ".....oooooo....."];
const gemRamp = P => { const c = palette(P); return { o: mix(c.a, c.d, 0.75), d: mix(c.a, c.d, 0.4), m: mix(c.a, c.d, 0.15), l: mix(c.a, c.l, 0.55), h: mix(c.l, white, 0.6), a: c.a }; };
const sword = (P, t) => draw(SWORD, ramp(P, t)), axe = (P, t) => draw(AXE, ramp(P, t)), pickaxe = (P, t) => draw(PICK, ramp(P, t));
const armorIcon = (P, t, k) => draw({ helmet: HELM, chest: CHEST, legs: LEGS, boots: BOOTS }[k], ramp(P, t));
const mace = P => draw(MACE, ramp(P, "diamond")), crystalItem = P => draw(GEM, gemRamp(P)), pearl = P => draw(PEARL, gemRamp(P)), apple = P => draw(APPLE, gemRamp(P)), bottle = P => draw(BOTTLE, gemRamp(P));
const DINO = [[8, 30], [5, 28.5], [3.6, 25.5], [6, 22], [12, 21], [16, 21], [20, 20], [22.5, 15], [26, 11.5], [32, 10], [44, 8], [50, 8.5], [54, 10.5], [56, 14], [57.5, 18], [55, 21.5], [52, 22], [46, 22], [46.8, 24.5], [46, 27], [42, 26], [40.5, 30], [40.5, 34], [42, 38], [44, 42], [38, 42], [36, 36], [30, 36], [30, 42], [24, 42], [24, 34], [18, 34], [12, 32.5], [8, 30]];
const icon = fn => image(32, 32, (x, y) => fn(x + 0.5, y + 0.5));
function totem(P) {
    const c = palette(P), poly = DINO.map(([a, b]) => [a / 2, b / 2 + 3.5]);
    return outline(icon((x, y) => {
        if (!inPoly(poly, x, y)) return null;
        if (Math.hypot(x - 23.5, y - 10) < 1.3) return mix(c.d, black, 0.5);
        const e = edgeDist(poly, x, y);
        if (hash(Math.floor(x), Math.floor(y), 3, 77) > 0.86 && e > 1.5) return c.l;
        return e < 1.4 ? mix(c.a, c.d, 0.25) : mix(c.a, c.l, clamp(0.55 - (x + y) / 80));
    }), mix(hex(P.dark), black, 0.55));
}
/** Dunkler Stein in 4 Tönen des Pack-Farbtons (für Obsidian und Anchor) */
function darkStone(P, x, y, s) { const c = palette(P), B = mix(c.d, black, 0.55), n = tile(x / 3, y / 3, 6, s) * 0.65 + tile(x, y, 16, s + 1) * 0.35, q = Math.floor(n * 4.9); return [B, mix(B, c.d, 0.6), mix(c.d, c.m, 0.25), mix(c.d, c.a, 0.3)][Math.max(0, Math.min(3, q - 1))]; }
const obsidian = P => image(16, 16, (x, y) => darkStone(P, x, y, 61));
function glowstone(P) { const r = gemRamp(P); return image(16, 16, (x, y) => { const n = tile(x / 2.5, y / 2.5, 6, 71), q = Math.floor(n * 5); return [r.o, r.d, r.m, r.l, r.h][Math.max(0, Math.min(4, q))]; }); }
function anchorSide(P, lit) { const r = gemRamp(P); return image(16, 16, (x, y) => { if (x >= 5 && x <= 10 && y >= 3 && y <= 12) { if (x === 5 || x === 10 || y === 3 || y === 12) return r.o; const seg4 = Math.floor((11 - y) / 2), on = seg4 < lit; if ((11 - y) % 2 === 1 && y > 4) return r.o; return on ? (x === 7 || x === 8 ? r.h : r.a) : mix(hex(P.dark), black, 0.6); } const b = darkStone(P, x, y, 7); return (x === 0 || y === 0 || x === 15 || y === 15) ? r.d : b; }); }
function anchorTop(P, on) { const r = gemRamp(P); return image(16, 16, (x, y) => { const d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5)); if (d < 3) return on ? (d < 2 ? r.h : r.a) : mix(hex(P.dark), black, 0.6); if (d < 4) return r.o; if (d >= 7) return r.d; return darkStone(P, x, y, 9); }); }
function fire(P, seed) {
    const cols = P.fire.map(hex), F = 32;
    return image(16, 16 * F, (x, Y) => {
        const fr = Math.floor(Y / 16), y = Y % 16, t = fr / F * 8;
        const h = 3.5 + tile(x / 4 + seed, t, 8, seed + 3) * 3.5 + tile(x / 2 + 3, t * 2, 16, seed + 5) * 1.6;   // niedrig: höchstens etwa die halbe Höhe
        const up = 16 - y;
        if (up > h) return null;
        const k = up / h, i = k < 0.25 ? 3 : k < 0.55 ? 2 : k < 0.82 ? 1 : 0;
        return cols[i];
    });
}
function crystal(P) {
    const r = gemRamp(P), c = palette(P);
    return image(128, 64, (x, y) => {
        if (y < 32 && x < 64) { const e = x % 16 < 2 || x % 16 > 13 || y % 16 < 2 || y % 16 > 13; return e ? [...r.l, 240] : [...mix(c.a, c.l, 0.25), 60]; }
        if (y < 32) { const fx = x % 16, fy = y % 16, d = Math.abs(fx - 7.5) + Math.abs(fy - 7.5); return d < 3.5 ? r.h : d < 6.5 ? r.a : r.m; }
        if (y < 64 && x < 96) return darkStone(P, x >> 1, y >> 1, 15);
        return null;
    });
}
// Rüstung am Körper: Layout der Minecraft-Figur (64×32), klare Farbflächen
const K = 1;
function faces(u, v, w, h, d) { return [["top", u + d, v, w, d], ["bottom", u + d + w, v, w, d], ["right", u, v + d, d, h], ["front", u + d, v + d, w, h], ["left", u + d + w, v + d, d, h], ["back", u + d + w + d, v + d, w, h]]; }
function paintBox(q, r, u, v, w, h, d, kind) {
    for (const [name, fx, fy, fw, fh] of faces(u, v, w, h, d)) for (let py = 0; py < fh; py++) for (let px = 0; px < fw; px++) {
        const edge = px === 0 || py === 0 || px === fw - 1 || py === fh - 1, side = name !== "front";
        let col = py < fh * 0.2 ? r.l : py > fh * 0.75 ? r.d : r.m;
        if (side) col = py > fh * 0.5 ? r.d : r.m;
        if (edge && (py === fh - 1 || name === "top" || name === "bottom")) col = name === "top" ? r.l : r.o;
        else if (edge && (px === 0 || px === fw - 1)) col = r.d;
        if (kind === "head" && name === "front") { if (py >= 3 && py <= 4 && px !== 3 && px !== 4) col = r.o; if (py === 1) col = r.a; }
        if (kind === "body" && name === "front") { const g = Math.abs(px - 3.5) + Math.abs(py - 4); if (g < 3) col = g < 1.2 ? r.h : r.a; if (py === fh - 2) col = r.a; }
        if (kind === "arm" && py < 3) col = name === "top" ? r.l : r.a;
        if (kind === "boot") { if (py < 8) { col = null; } else if (py === 8) col = r.a; }
        if (kind === "waist" && py >= 3) col = null;
        if (kind === "leg" && py === 0) col = r.a;
        if (col) put(q, fx + px, fy + py, col);
    }
}
function armorSheet(P, tier, legs) {
    const q = image(64, 32, () => null), r = ramp(P, tier);
    if (legs) { paintBox(q, r, 16, 16, 8, 12, 4, "waist"); paintBox(q, r, 0, 16, 4, 12, 4, "leg"); }
    else { paintBox(q, r, 0, 0, 8, 8, 8, "head"); paintBox(q, r, 16, 16, 8, 12, 4, "body"); paintBox(q, r, 40, 16, 4, 12, 4, "arm"); paintBox(q, r, 0, 16, 4, 12, 4, "boot"); }
    return q;
}
function elytra(P) { const r = gemRamp(P); return image(64, 32, (x, y) => { if (!(x >= 22 && x < 46 && y < 22)) return null; if (y % 3 === 2) return r.o; return y < 6 ? r.l : y < 14 ? r.m : r.d; }); }
function shield(P) { const r = gemRamp(P); return image(64, 64, (x, y) => { if (x >= 1 && x < 13 && y >= 1 && y < 23) { const nx = x - 1, ny = y - 1, e = nx === 0 || nx === 11 || ny === 0 || ny === 21, g = Math.abs(nx - 5.5) + Math.abs(ny - 10.5); if (e) return r.a; if (g < 2) return r.h; if (g < 4) return r.a; return ny < 8 ? r.m : r.d; } if (x >= 26 && x < 44 && y < 12) return r.o; if (y < 24 && x < 26) return r.d; return null; }); }

// Inventar: gleiche Maße und Felder wie Minecraft, eigenes Aussehen
function panel(P) {
    const pan = mix(hex(P.dark), hex(P.mid), 0.12), hi = mix(pan, hex(P.acc), 0.4), sh = mix(pan, [0, 0, 0], 0.45);
    return { pan, hi, sh, slot: mix(hex(P.dark), [0, 0, 0], 0.4) };
}
function drawPanel(q, W, H, x0, y0, c) { for (let y = y0; y < y0 + H; y++) for (let x = x0; x < x0 + W; x++) { const e = x === x0 || y === y0 || x === x0 + W - 1 || y === y0 + H - 1; const ee = x < x0 + 3 || y < y0 + 3; const f = x > x0 + W - 4 || y > y0 + H - 4; put(q, x, y, e ? [10, 10, 12] : ee ? c.hi : f ? c.sh : c.pan); } }
function put(q, x, y, c) { const i = (y * q.w + x) * 4; q.p[i] = Math.round(c[0]); q.p[i + 1] = Math.round(c[1]); q.p[i + 2] = Math.round(c[2]); q.p[i + 3] = 255; }
function slot(q, X, Y, c, acc) { for (let y = Y - 1; y < Y + 17; y++) for (let x = X - 1; x < X + 17; x++) { const b = x === X - 1 || y === Y - 1, g = x === X + 16 || y === Y + 16; put(q, x, y, b ? mix(c.slot, [0, 0, 0], 0.5) : g ? acc : c.slot); } }
function invScreen(P) {
    const q = image(256, 256, () => null), c = panel(P), acc = mix(hex(P.acc), c.pan, 0.35);
    drawPanel(q, 176, 166, 0, 0, c);
    for (let i = 0; i < 4; i++) slot(q, 8, 8 + i * 18, c, acc);
    slot(q, 77, 62, c, acc);
    for (let r = 0; r < 2; r++) for (let k = 0; k < 2; k++) slot(q, 98 + k * 18, 18 + r * 18, c, acc);
    slot(q, 154, 28, c, acc);
    for (let r = 0; r < 3; r++) for (let k = 0; k < 9; k++) slot(q, 8 + k * 18, 84 + r * 18, c, acc);
    for (let k = 0; k < 9; k++) slot(q, 8 + k * 18, 142, c, acc);
    for (let y = 8; y < 78; y++) for (let x = 26; x < 75; x++) put(q, x, y, mix(hex(P.dark), [0, 0, 0], 0.3));
    for (let i = 0; i < 12; i++) for (let y = 34 - Math.max(0, i - 5); y <= 34 + Math.max(0, i - 5); y++) put(q, 134 + i, y, hex(P.acc));
    return q;
}
function chestScreen(P) {
    const q = image(256, 256, () => null), c = panel(P), acc = mix(hex(P.acc), c.pan, 0.35);
    drawPanel(q, 176, 125, 0, 0, c);
    drawPanel(q, 176, 96, 0, 126, c);
    for (let r = 0; r < 6; r++) for (let k = 0; k < 9; k++) slot(q, 8 + k * 18, 18 + r * 18, c, acc);
    for (let r = 0; r < 3; r++) for (let k = 0; k < 9; k++) slot(q, 8 + k * 18, 140 + r * 18, c, acc);
    for (let k = 0; k < 9; k++) slot(q, 8 + k * 18, 198, c, acc);
    return q;
}
function hotbar(P) { const c = panel(P), acc = hex(P.acc); return image(182, 22, (x, y) => { if (x === 0 || y === 0 || x === 181 || y === 21) return [10, 10, 12, 230]; if ((x - 1) % 20 === 0 || (y === 1 || y === 20)) return [...mix(c.pan, acc, 0.35), 230]; return [...mix(hex(P.dark), [0, 0, 0], 0.2), 170]; }); }
function selection(P) { const a = hex(P.acc), l = hex(P.light); return image(24, 23, (x, y) => { if (x < 1 || y < 1 || x > 22 || y > 21) return [...a, 255]; if (x < 2 || y < 2 || x > 21 || y > 20) return [...l, 255]; return null; }); }
function offhand(P) { const c = panel(P); return image(29, 24, (x, y) => { if (x > 21 || y > 21 || y < 1) return null; const e = x === 0 || x === 21 || y === 1 || y === 21; return e ? [...mix(c.pan, hex(P.acc), 0.35), 230] : [...hex(P.dark), 170]; }); }
function packIcon(P) {
    const POLY = DINO;
    const inside = (x, y) => { let c = false; for (let i = 0, j = POLY.length - 1; i < POLY.length; j = i++) { const a = POLY[i], b = POLY[j]; if ((a[1] > y) !== (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) c = !c; } return c; };
    return image(128, 128, (x, y) => {
        const u = (x / 128) * 64, v = (y / 128) * 64 - 8;
        if (inside(u, v)) { const e = Math.hypot(u - 47, v - 13); if (e < 1.8) return hex(P.dark); return mix(hex(P.acc), hex(P.light), clamp(1 - (u + v * 2) / 90)); }
        return P.sky(norm([(x - 64) / 90, 0.25 + (64 - y) / 110, -1]));
    });
}

// ------------------------------------------------------------ Pack bauen
const T = "assets/minecraft/textures/";
const previews = [], sheetRows = [];
/** Figur von vorn aus den Rüstungs-Blättern (zum Anschauen) */
function dummy(top, low) {
    const q = image(16 * K, 32 * K, () => null);
    const cp = (src, sx, sy, sw, sh, dx, dy, skipEmpty) => { for (let y = 0; y < sh * K; y++) for (let x = 0; x < sw * K; x++) { const i = ((sy * K + y) * src.w + sx * K + x) * 4; if (src.p[i + 3]) put(q, dx * K + x, dy * K + y, [src.p[i], src.p[i + 1], src.p[i + 2]]); } };
    cp(top, 8, 8, 8, 8, 4, 0); cp(low, 20, 20, 8, 12, 4, 8); cp(top, 20, 20, 8, 12, 4, 8); cp(top, 44, 20, 4, 12, 0, 8); cp(top, 44, 20, 4, 12, 12, 8);
    cp(low, 4, 20, 4, 12, 4, 20); cp(low, 4, 20, 4, 12, 8, 20); cp(top, 4, 20, 4, 12, 4, 20); cp(top, 4, 20, 4, 12, 8, 20);
    return q;
}
for (const P of PACKS) {
    const files = [], add = (n, img, rgb) => { files.push([n, png(img, rgb)]); return img; };
    files.push(["pack.mcmeta", Buffer.from(JSON.stringify({ pack: { description: "Nugget Style: " + P.name, min_format: 75, max_format: 75 } }))]);
    const ic = add("pack.png", packIcon(P));
    add("assets/minecraft/optifine/sky/world0/sky1.png", skySheet(P, 512), true);
    files.push(["assets/minecraft/optifine/sky/world0/sky1.properties", Buffer.from("source=./sky1.png\nblend=replace\nrotate=false\n")]);
    const anim = Buffer.from('{"animation":{"frametime":1}}');
    const f0 = add(T + "block/fire_0.png", fire(P, 1)); files.push([T + "block/fire_0.png.mcmeta", anim]);
    add(T + "block/fire_1.png", fire(P, 6)); files.push([T + "block/fire_1.png.mcmeta", anim]);
    const it = {}, gear = {};
    for (const tier of ["diamond", "netherite"]) {
        const g = gear[tier] = { sword: sword(P, tier), axe: axe(P, tier), pickaxe: pickaxe(P, tier), helmet: armorIcon(P, tier, "helmet"), chestplate: armorIcon(P, tier, "chest"), leggings: armorIcon(P, tier, "legs"), boots: armorIcon(P, tier, "boots") };
        for (const k of Object.keys(g)) add(T + `item/${tier}_${k}.png`, g[k]);
        g.top = add(T + `entity/equipment/humanoid/${tier}.png`, armorSheet(P, tier, false));
        g.low = add(T + `entity/equipment/humanoid_leggings/${tier}.png`, armorSheet(P, tier, true));
    }
    Object.assign(it, { totem: add(T + "item/totem_of_undying.png", totem(P)), crystal: add(T + "item/end_crystal.png", crystalItem(P)), pearl: add(T + "item/ender_pearl.png", pearl(P)), apple: add(T + "item/golden_apple.png", apple(P)), bottle: add(T + "item/experience_bottle.png", bottle(P)), mace: add(T + "item/mace.png", mace(P)) });
    add(T + "entity/equipment/wings/elytra.png", elytra(P));
    const sh = add(T + "entity/shield_base_nopattern.png", shield(P));
    add(T + "entity/shield_base.png", sh);
    const cr = add(T + "entity/end_crystal/end_crystal.png", crystal(P));
    it.obsidian = add(T + "block/obsidian.png", obsidian(P));
    it.glowstone = add(T + "block/glowstone.png", glowstone(P));
    for (let n = 0; n <= 4; n++) { const a = add(T + `block/respawn_anchor_side${n}.png`, anchorSide(P, n)); if (n === 3) it.anchor = a; }
    add(T + "block/respawn_anchor_top.png", anchorTop(P, true));
    add(T + "block/respawn_anchor_top_off.png", anchorTop(P, false));
    add(T + "block/respawn_anchor_bottom.png", anchorTop(P, false));
    const inv = add(T + "gui/container/inventory.png", invScreen(P));
    add(T + "gui/container/generic_54.png", chestScreen(P));
    const hb = add(T + "gui/sprites/hud/hotbar.png", hotbar(P));
    add(T + "gui/sprites/hud/hotbar_selection.png", selection(P));
    add(T + "gui/sprites/hud/hotbar_offhand_left.png", offhand(P));
    add(T + "gui/sprites/hud/hotbar_offhand_right.png", offhand(P));
    // Nur das, was der Nutzer ausgewählt hat: seine zwei Schwerter, Himmel und Dino-Totem
    const signed = f => { const b = fs.readFileSync(f), end = b.lastIndexOf(Buffer.from("IEND")) - 4; return Buffer.concat([b.subarray(0, end), chunk("tEXt", Buffer.from("Comment made by Diamond_Nugget", "latin1")), b.subarray(end)]); };
    const artDir = path.join(__dirname, "art"), mySword = t => signed(path.join(artDir, t + "_sword.png"));
    const keep = n => n === "pack.mcmeta" || n === "pack.png" || n.includes("optifine/sky/") || n.endsWith("item/totem_of_undying.png");
    const finalFiles = files.filter(([n]) => keep(n));
    for (const t of ["diamond", "netherite"]) finalFiles.push([T + "item/" + t + "_sword.png", mySword(t)]);
    files.length = 0; files.push(...finalFiles);
    fs.writeFileSync(path.join(OUT, P.id + ".zip"), zip(files));
    const fireFrame = image(16, 16, (x, y) => { const i = (y * 16 + x) * 4 + 16 * 16 * 4 * 3; return f0.p[i + 3] ? [f0.p[i], f0.p[i + 1], f0.p[i + 2]] : null; });
    const uri = img => "data:image/png;base64," + png(img).toString("base64");
    const set = t => [["Schwert", gear[t].sword], ["Axt", gear[t].axe], ["Spitzhacke", gear[t].pickaxe], ["Helm", gear[t].helmet], ["Brust", gear[t].chestplate], ["Hose", gear[t].leggings], ["Stiefel", gear[t].boots]];
    const rest = [["Totem", it.totem], ["Kristall", it.crystal], ["Perle", it.pearl], ["Goldapfel", it.apple], ["XP-Flasche", it.bottle], ["Mace", it.mace], ["Obsidian", it.obsidian], ["Glowstone", it.glowstone], ["Anchor", it.anchor], ["Feuer", fireFrame]];
    const dia = dummy(gear.diamond.top, gear.diamond.low), net = dummy(gear.netherite.top, gear.netherite.low);
    sheetRows.push({ pano: panorama(P, 900, 90), dia: set("diamond").map(a => a[1]), net: set("netherite").map(a => a[1]), rest: rest.map(a => a[1]), dd: dia, nd: net, sh });
    previews.push({ keep: [["Diamant-Schwert", "data:image/png;base64," + mySword("diamond").toString("base64")], ["Netherite-Schwert", "data:image/png;base64," + mySword("netherite").toString("base64")], ["Totem", uri(it.totem)]], P, size: fs.statSync(path.join(OUT, P.id + ".zip")).size, pano: uri(panorama(P, 900, 260)), icon: uri(ic), inv: uri(image(176, 166, (x, y) => { const i = (y * 256 + x) * 4; return inv.p[i + 3] ? [inv.p[i], inv.p[i + 1], inv.p[i + 2]] : null; })), hb: uri(hb), crystal: uri(cr), dia: set("diamond").map(([n, i]) => [n, uri(i)]), net: set("netherite").map(([n, i]) => [n, uri(i)]), rest: rest.map(([n, i]) => [n, uri(i)]), dd: uri(dia), nd: uri(net), shield: uri(image(13 * K, 24 * K, (x, y) => { const i = (y * sh.w + x) * 4; return sh.p[i + 3] ? [sh.p[i], sh.p[i + 1], sh.p[i + 2]] : null; })) });
    console.log(P.id, Math.round(fs.statSync(path.join(OUT, P.id + ".zip")).size / 1024) + " KB,", files.length, "Dateien");
}
// ------------------------------------------------------------ Vorschau
const row = (t, a) => `<div class="rowt">${t}</div><div class="items">${a.map(([n, u]) => `<div class="i"><img src="${u}">${n}</div>`).join("")}</div>`;
const html = `<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Nugget Style – Start-Packs</title>
<style>*{box-sizing:border-box;margin:0}body{background:#0b0a0c;color:#f4eee8;font-family:Inter,"Segoe UI",system-ui,sans-serif;padding:26px 18px}h1{font-size:22px;text-align:center}.lead{color:#9a9299;text-align:center;font-size:13px;margin:4px 0 22px}.p{max-width:1000px;margin:0 auto 26px;border-radius:18px;overflow:hidden;background:#151216;box-shadow:0 0 0 1px #ffffff14}.sky{display:block;width:100%;height:210px;object-fit:cover}.h{display:flex;align-items:center;gap:12px;padding:12px 16px 2px}.h img{width:44px;height:44px;border-radius:9px;image-rendering:pixelated}.h b{font-size:17px}.h span{color:#9a9299;font-size:12px;margin-left:auto}.rowt{padding:10px 16px 0;font-size:12px;color:#c9c1c8;font-weight:600}.items{display:flex;flex-wrap:wrap;gap:8px;padding:6px 16px}.i{width:78px;text-align:center;font-size:10.5px;color:#a79fa6}.i img{width:70px;height:70px;image-rendering:pixelated;background:#ffffff0d;border-radius:8px;padding:4px;display:block;margin:0 auto 3px}.gui{display:flex;flex-wrap:wrap;gap:18px;align-items:flex-end;padding:10px 16px 16px}.gui img{image-rendering:pixelated;display:block}.gui small{color:#a79fa6;font-size:10.5px;display:block;margin-top:4px;text-align:center}</style>
<h1>Nugget Style – die 6 Start-Packs</h1><p class="lead">Deine zwei Schwerter, dazu Himmel und Dino-Totem. Himmel als Rundum-Ansicht, Items stark vergrößert.</p>
${previews.map(v => `<div class="p"><img class="sky" src="${v.pano}"><div class="h"><img src="${v.icon}"><b>${v.P.name}</b><span>${Math.round(v.size / 1024)} KB</span></div>${row("Schwerter und Totem", v.keep)}</div>`).join("")}
</html>`;
fs.writeFileSync(path.join(OUT, "vorschau.html"), html);
// Kontrollbild (für die eigene Prüfung)
const ROW = 410, sheet = image(900, ROW * 6, () => [24, 22, 26]);
const blit = (src, X, Y, k) => { for (let y = 0; y < src.h * k; y++) for (let x = 0; x < src.w * k; x++) { const i = (Math.floor(y / k) * src.w + Math.floor(x / k)) * 4; if (src.p[i + 3] && X + x < sheet.w && Y + y < sheet.h) put(sheet, X + x, Y + y, [src.p[i], src.p[i + 1], src.p[i + 2]]); } };
sheetRows.forEach((r, n) => { const y = n * ROW; blit(r.pano, 0, y, 1); r.dia.forEach((im, i) => blit(im, 4 + i * 100, y + 94, 6)); r.net.forEach((im, i) => blit(im, 4 + i * 100, y + 194, 6)); r.rest.forEach((im, i) => blit(im, 4 + i * 68, y + 296, im.w > 16 ? 2 : 4)); blit(r.dd, 716, y + 94, 6); blit(r.nd, 816, y + 94, 6); });
fs.writeFileSync(path.join(OUT, "kontrolle.png"), png(sheet, true));
console.log("fertig");
