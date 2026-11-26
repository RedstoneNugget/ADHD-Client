// Erzeugt das Mod-Icon aus der Dino-Form im Client: node tools/gen_icon.js
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const root = path.join(__dirname, "..");
const java = fs.readFileSync(path.join(root, "src/main/java/com/nuggetclient/gui/DesignFx.java"), "utf8");
const block = java.match(/float\[\]\[\] DINO = \{([\s\S]*?\})\};/);
if (!block) throw new Error("DINO nicht gefunden");
const DINO = [...block[1].matchAll(/\{\s*(-?[\d.]+)f?\s*,\s*(-?[\d.]+)f?\s*\}/g)].map(m => [+m[1], +m[2]]);
if (DINO.length < 8) throw new Error("DINO zu kurz");

function inside(x, y) {
    let inn = false;
    for (let i = 0, j = DINO.length - 1; i < DINO.length; j = i++) {
        const a = DINO[i], b = DINO[j];
        if ((a[1] > y) !== (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) inn = !inn;
    }
    return inn;
}
function edgeDist(x, y) {
    let best = 99;
    for (let i = 0, j = DINO.length - 1; i < DINO.length; j = i++) {
        const a = DINO[j], b = DINO[i], dx = b[0] - a[0], dy = b[1] - a[1], len = dx * dx + dy * dy;
        const t = len === 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / len));
        best = Math.min(best, Math.hypot(a[0] + t * dx - x, a[1] + t * dy - y));
    }
    return best;
}
const SIZE = 256, SCALE = 3.5, OX = (SIZE - 64 * SCALE) / 2, OY = (SIZE - 48 * SCALE) / 2 + 4, R = 44;
const ACC = [0xF0, 0xA6, 0x3A], BG = [0x1C, 0x13, 0x0A], RING = [0xF5, 0xA5, 0x24];
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
function boxDist(x, y) { const qx = Math.abs(x - SIZE / 2) - (SIZE / 2 - 6 - R), qy = Math.abs(y - SIZE / 2) - (SIZE / 2 - 6 - R); return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - R; }
const raw = Buffer.alloc((SIZE * 4 + 1) * SIZE);
for (let py = 0; py < SIZE; py++) {
    for (let px = 0; px < SIZE; px++) {
        const d = boxDist(px + 0.5, py + 0.5);
        const shape = Math.max(0, Math.min(1, 0.5 - d));
        let c = mix(mix(BG, [0x3A, 0x24, 0x10], Math.max(0, 1 - (py / SIZE) * 1.4)), RING, Math.max(0, Math.min(1, 1 - Math.abs(d + 2.5) / 2.5)));
        let hits = 0;
        for (let sy = 0; sy < 3; sy++) for (let sx = 0; sx < 3; sx++) if (inside((px + (sx + 0.5) / 3 - OX) / SCALE, (py + (sy + 0.5) / 3 - OY) / SCALE)) hits++;
        if (hits) {
            const x = (px + 0.5 - OX) / SCALE, y = (py + 0.5 - OY) / SCALE, lx = (x - 26) / 40, ly = (y - 16) / 30;
            let g = 1.05 - 0.55 * Math.hypot(lx, ly);
            const e = edgeDist(x, y);
            if (e < 1.6) g *= 0.62 + 0.38 * e / 1.6;
            if ((Math.imul(Math.floor(x / 2.3), 73856093) ^ Math.imul(Math.floor(y / 2.3), 19349663)) % 11 === 0 && e > 2) g = Math.min(1.15, g + 0.25);
            if ((x - 47) ** 2 + (y - 13) ** 2 < 3.4) g = 0.12;
            c = mix(c, ACC.map(v => Math.min(255, v * g)), hits / 9);
        }
        const i = py * (SIZE * 4 + 1) + 1 + px * 4;
        raw[i] = Math.round(c[0]); raw[i + 1] = Math.round(c[1]); raw[i + 2] = Math.round(c[2]); raw[i + 3] = Math.round(shape * 255);
    }
}
const T = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c; });
const crc = b => { let c = -1; for (const x of b) c = T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const b = Buffer.concat([Buffer.from(t, "latin1"), d]), c = Buffer.alloc(4); c.writeUInt32BE(crc(b)); return Buffer.concat([l, b, c]); };
const ih = Buffer.alloc(13); ih.writeUInt32BE(SIZE, 0); ih.writeUInt32BE(SIZE, 4); ih[8] = 8; ih[9] = 6;
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("tEXt", Buffer.from("Comment\0made by Diamond_Nugget", "latin1")), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
fs.writeFileSync(path.join(root, "src/main/resources/assets/nugget_client/icon.png"), png);
console.log("icon.png", png.length, "Bytes, Punkte", DINO.length);
