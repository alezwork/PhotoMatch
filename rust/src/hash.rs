//! Perceptual hashing (pHash) on tiny grayscale thumbnails.
//!
//! The Android side decodes a 32x32 grayscale thumbnail through the system
//! thumbnail cache (very cheap), and hands batches of them here. We compute a
//! 64-bit DCT hash for every thumbnail in parallel.

use rayon::prelude::*;
use std::sync::OnceLock;

pub const SIDE: usize = 32;
pub const PIXELS: usize = SIDE * SIDE;
const LOW: usize = 8;

fn cos_table() -> &'static [[f32; SIDE]; LOW] {
    static TABLE: OnceLock<[[f32; SIDE]; LOW]> = OnceLock::new();
    TABLE.get_or_init(|| {
        let mut t = [[0f32; SIDE]; LOW];
        for (u, row) in t.iter_mut().enumerate() {
            for (x, v) in row.iter_mut().enumerate() {
                *v = (((2 * x + 1) as f32) * u as f32 * std::f32::consts::PI / (2.0 * SIDE as f32)).cos();
            }
        }
        t
    })
}

/// pHash of a single 32x32 grayscale image.
pub fn phash(px: &[u8]) -> u64 {
    debug_assert_eq!(px.len(), PIXELS);
    let c = cos_table();
    // Separable DCT, keeping only the 8x8 low-frequency block.
    let mut tmp = [[0f32; LOW]; SIDE]; // tmp[y][u]
    for y in 0..SIDE {
        let row = &px[y * SIDE..(y + 1) * SIDE];
        for u in 0..LOW {
            let mut s = 0f32;
            for x in 0..SIDE {
                s += row[x] as f32 * c[u][x];
            }
            tmp[y][u] = s;
        }
    }
    let mut coef = [0f32; LOW * LOW];
    for v in 0..LOW {
        for u in 0..LOW {
            let mut s = 0f32;
            for y in 0..SIDE {
                s += c[v][y] * tmp[y][u];
            }
            coef[v * LOW + u] = s;
        }
    }
    let mut sorted: Vec<f32> = coef[1..].to_vec();
    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let median = sorted[sorted.len() / 2];
    let mut h = 0u64;
    for (i, v) in coef.iter().enumerate() {
        if *v > median {
            h |= 1 << i;
        }
    }
    h
}

/// Hash `count` thumbnails packed back to back. Uses all cores.
pub fn phash_batch(data: &[u8], count: usize) -> Vec<u64> {
    let count = count.min(data.len() / PIXELS);
    data[..count * PIXELS]
        .par_chunks_exact(PIXELS)
        .map(phash)
        .collect()
}

#[inline]
pub fn hamming(a: u64, b: u64) -> u32 {
    (a ^ b).count_ones()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn img(f: impl Fn(usize, usize) -> u8) -> Vec<u8> {
        let mut v = vec![0u8; PIXELS];
        for y in 0..SIDE {
            for x in 0..SIDE {
                v[y * SIDE + x] = f(x, y);
            }
        }
        v
    }

    #[test]
    fn similar_images_have_close_hashes() {
        let a = img(|x, y| ((x * 7 + y * 3) % 256) as u8);
        let b = img(|x, y| ((x * 7 + y * 3) % 256).saturating_add(4).min(255) as u8);
        let c = img(|x, y| if (x / 4 + y / 4) % 2 == 0 { 250 } else { 5 });
        let (ha, hb, hc) = (phash(&a), phash(&b), phash(&c));
        assert!(hamming(ha, hb) <= 6, "{}", hamming(ha, hb));
        assert!(hamming(ha, hc) > 12, "{}", hamming(ha, hc));
    }
}
