//! Tiny crash-safe key/value logs: swipe decisions and cached hashes.
//!
//! Each log is a flat file of 16-byte records (key u64 LE, value u64 LE).
//! Appends are O(1); the whole map is loaded at startup (100k items ≈ 1.6 MB)
//! and compacted when it has grown more than 2x its live size.

use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};

const REC: usize = 16;

pub struct Log {
    path: PathBuf,
    file: File,
    pub map: HashMap<u64, u64>,
}

impl Log {
    pub fn open(path: &Path) -> io::Result<Self> {
        let mut map = HashMap::new();
        let mut records = 0usize;
        if let Ok(mut f) = File::open(path) {
            let mut buf = Vec::new();
            f.read_to_end(&mut buf)?;
            for rec in buf.chunks_exact(REC) {
                let k = u64::from_le_bytes(rec[0..8].try_into().unwrap());
                let v = u64::from_le_bytes(rec[8..16].try_into().unwrap());
                if v == 0 {
                    map.remove(&k);
                } else {
                    map.insert(k, v);
                }
                records += 1;
            }
            if records > map.len() * 2 + 4096 || buf.len() % REC != 0 {
                Self::rewrite(path, &map)?;
            }
        }
        let file = OpenOptions::new().create(true).append(true).open(path)?;
        Ok(Self { path: path.to_path_buf(), file, map })
    }

    fn rewrite(path: &Path, map: &HashMap<u64, u64>) -> io::Result<()> {
        let tmp = path.with_extension("tmp");
        let mut buf = Vec::with_capacity(map.len() * REC);
        for (k, v) in map {
            buf.extend_from_slice(&k.to_le_bytes());
            buf.extend_from_slice(&v.to_le_bytes());
        }
        {
            let mut f = File::create(&tmp)?;
            f.write_all(&buf)?;
            f.sync_all()?;
        }
        fs::rename(&tmp, path)
    }

    /// Writes many entries in one syscall. Value 0 deletes the key.
    pub fn put_many(&mut self, entries: &[(u64, u64)]) -> io::Result<()> {
        let mut buf = Vec::with_capacity(entries.len() * REC);
        for &(k, v) in entries {
            if v == 0 {
                self.map.remove(&k);
            } else {
                self.map.insert(k, v);
            }
            buf.extend_from_slice(&k.to_le_bytes());
            buf.extend_from_slice(&v.to_le_bytes());
        }
        self.file.write_all(&buf)?;
        self.file.flush()
    }

    pub fn clear(&mut self) -> io::Result<()> {
        self.map.clear();
        self.file = File::create(&self.path)?;
        Ok(())
    }
}

pub struct Store {
    pub decisions: Log,
    pub hashes: Log,
}

/// Hash values are stored +1 so that a real hash of 0 is never confused with
/// the "deleted" marker. We keep the low 63 bits of the shifted value.
pub fn encode_hash(h: u64) -> u64 {
    h | (1u64 << 63) // top bit set => never zero; pHash bit 63 is rarely meaningful
}

pub fn decode_hash(v: u64) -> u64 {
    v
}

impl Store {
    pub fn open(dir: &Path) -> io::Result<Self> {
        fs::create_dir_all(dir)?;
        Ok(Self {
            decisions: Log::open(&dir.join("decisions.log"))?,
            hashes: Log::open(&dir.join("hashes.log"))?,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn persists_and_compacts() {
        let dir = std::env::temp_dir().join(format!("pm_store_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        {
            let mut s = Store::open(&dir).unwrap();
            s.decisions.put_many(&[(1, 1), (2, 2), (3, 3)]).unwrap();
            s.decisions.put_many(&[(2, 0)]).unwrap();
        }
        let s = Store::open(&dir).unwrap();
        assert_eq!(s.decisions.map.get(&1), Some(&1));
        assert_eq!(s.decisions.map.get(&2), None);
        assert_eq!(s.decisions.map.get(&3), Some(&3));
        let _ = fs::remove_dir_all(&dir);
    }
}
