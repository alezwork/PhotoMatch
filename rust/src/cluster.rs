//! Grouping: near-duplicate clusters and "moments" (shots taken together).

use crate::hash::hamming;
use std::collections::HashMap;

struct Dsu {
    p: Vec<u32>,
    r: Vec<u8>,
}

impl Dsu {
    fn new(n: usize) -> Self {
        Self { p: (0..n as u32).collect(), r: vec![0; n] }
    }
    fn find(&mut self, mut x: u32) -> u32 {
        while self.p[x as usize] != x {
            let gp = self.p[self.p[x as usize] as usize];
            self.p[x as usize] = gp;
            x = gp;
        }
        x
    }
    fn union(&mut self, a: u32, b: u32) {
        let (a, b) = (self.find(a), self.find(b));
        if a == b {
            return;
        }
        let (ra, rb) = (self.r[a as usize], self.r[b as usize]);
        if ra < rb {
            self.p[a as usize] = b;
        } else if ra > rb {
            self.p[b as usize] = a;
        } else {
            self.p[b as usize] = a;
            self.r[a as usize] += 1;
        }
    }
}

/// Returns a label per input item: `-1` for items without similar siblings,
/// otherwise a group id `0..groups`. Groups are numbered newest first.
///
/// * Items close in time (within `window_ms`, up to 48 neighbours) are linked
///   when their hashes differ by at most `threshold` bits — bursts and retakes.
/// * Anywhere in the library, items differing by at most 3 bits are linked —
///   true duplicates (re-saved, re-downloaded, edited copies). Found with
///   4x16-bit bands, so it stays near O(n) on huge libraries.
pub fn similar_groups(hashes: &[Option<u64>], times: &[i64], threshold: u32, window_ms: i64) -> Vec<i32> {
    let n = hashes.len();
    let mut dsu = Dsu::new(n);

    let mut order: Vec<u32> = (0..n as u32).filter(|&i| hashes[i as usize].is_some()).collect();
    order.sort_unstable_by_key(|&i| times[i as usize]);

    for p in 0..order.len() {
        let i = order[p] as usize;
        let hi = hashes[i].unwrap();
        for &j in order.iter().skip(p + 1).take(48) {
            let j = j as usize;
            if times[j] - times[i] > window_ms {
                break;
            }
            if hamming(hi, hashes[j].unwrap()) <= threshold {
                dsu.union(i as u32, j as u32);
            }
        }
    }

    let mut buckets: HashMap<(u8, u16), Vec<u32>> = HashMap::new();
    for &i in &order {
        let h = hashes[i as usize].unwrap();
        for band in 0..4u8 {
            buckets.entry((band, (h >> (band * 16)) as u16)).or_default().push(i);
        }
    }
    for members in buckets.values() {
        if members.len() < 2 || members.len() > 256 {
            continue; // huge buckets are flat/blank images; skip to stay fast
        }
        for a in 0..members.len() {
            for b in a + 1..members.len() {
                let (x, y) = (members[a], members[b]);
                if hamming(hashes[x as usize].unwrap(), hashes[y as usize].unwrap()) <= 3 {
                    dsu.union(x, y);
                }
            }
        }
    }

    let mut size: HashMap<u32, (u32, i64)> = HashMap::new();
    for i in 0..n {
        if hashes[i].is_none() {
            continue;
        }
        let r = dsu.find(i as u32);
        let e = size.entry(r).or_insert((0, i64::MIN));
        e.0 += 1;
        e.1 = e.1.max(times[i]);
    }
    let mut roots: Vec<(u32, i64)> = size
        .iter()
        .filter(|(_, (c, _))| *c >= 2)
        .map(|(r, (_, t))| (*r, *t))
        .collect();
    roots.sort_unstable_by(|a, b| b.1.cmp(&a.1));
    let label: HashMap<u32, i32> = roots.iter().enumerate().map(|(k, (r, _))| (*r, k as i32)).collect();

    (0..n)
        .map(|i| {
            if hashes[i].is_none() {
                return -1;
            }
            *label.get(&dsu.find(i as u32)).unwrap_or(&-1)
        })
        .collect()
}

/// Splits a time-sorted (either direction) sequence into moments: a new moment
/// starts whenever the gap to the previous shot exceeds `gap_ms`.
pub fn moments(times: &[i64], gap_ms: i64) -> Vec<i32> {
    let mut out = Vec::with_capacity(times.len());
    let mut id = 0i32;
    for (k, t) in times.iter().enumerate() {
        if k > 0 && (t - times[k - 1]).abs() > gap_ms {
            id += 1;
        }
        out.push(id);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn groups_bursts_and_global_dups() {
        let hashes = vec![
            Some(0xFFFF_0000_FFFF_0000u64),
            Some(0xFFFF_0000_FFFF_0003),          // burst sibling of 0
            Some(0x1234_5678_9ABC_DEF0),          // alone
            Some(0xFFFF_0000_FFFF_0001),          // global dup of 0, far in time
            None,
        ];
        let times = vec![0, 1_000, 2_000, 999_999_999, 5];
        let g = similar_groups(&hashes, &times, 8, 60_000);
        assert_eq!(g[0], g[1]);
        assert_eq!(g[0], g[3]);
        assert_eq!(g[2], -1);
        assert_eq!(g[4], -1);
    }

    #[test]
    fn splits_moments() {
        assert_eq!(moments(&[100, 90, 10, 5], 20), vec![0, 0, 1, 1]);
    }
}
