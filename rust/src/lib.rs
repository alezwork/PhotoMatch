//! PhotoMatch native core: hashing, grouping and the decision store.
//! Exposed to Kotlin through JNI as `com.photomatch.core.NativeCore`.

pub mod cluster;
pub mod hash;
pub mod store;

use jni::objects::{JByteArray, JClass, JLongArray, JString};
use jni::sys::{jint, jintArray, jlong, jlongArray};
use jni::JNIEnv;
use std::path::PathBuf;
use std::sync::Mutex;
use store::{decode_hash, encode_hash, Store};

pub const KEEP: u64 = 1;
pub const NOPE: u64 = 2;
pub const SUPER: u64 = 3;
pub const DELETED: u64 = 4;

type Handle = Mutex<Store>;

fn with_store<R>(h: jlong, f: impl FnOnce(&mut Store) -> R) -> Option<R> {
    if h == 0 {
        return None;
    }
    // SAFETY: handle comes from storeOpen and lives until storeClose.
    let m = unsafe { &*(h as *const Handle) };
    let mut g = m.lock().unwrap_or_else(|e| e.into_inner());
    Some(f(&mut g))
}

fn read_longs(env: &mut JNIEnv, arr: &JLongArray) -> Vec<i64> {
    let n = env.get_array_length(arr).unwrap_or(0) as usize;
    let mut v = vec![0i64; n];
    if n > 0 {
        let _ = env.get_long_array_region(arr, 0, &mut v);
    }
    v
}

fn new_longs(env: &mut JNIEnv, v: &[i64]) -> jlongArray {
    match env.new_long_array(v.len() as i32) {
        Ok(a) => {
            let _ = env.set_long_array_region(&a, 0, v);
            a.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

fn new_ints(env: &mut JNIEnv, v: &[i32]) -> jintArray {
    match env.new_int_array(v.len() as i32) {
        Ok(a) => {
            let _ = env.set_int_array_region(&a, 0, v);
            a.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_storeOpen(mut env: JNIEnv, _c: JClass, dir: JString) -> jlong {
    let dir: String = match env.get_string(&dir) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    match Store::open(&PathBuf::from(dir)) {
        Ok(s) => Box::into_raw(Box::new(Mutex::new(s))) as jlong,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_storeClose(_env: JNIEnv, _c: JClass, h: jlong) {
    if h != 0 {
        // SAFETY: matches Box::into_raw in storeOpen.
        drop(unsafe { Box::from_raw(h as *mut Handle) });
    }
}

/// Sets decisions for many ids at once (decision 0 = undo).
#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_storeSet(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    ids: JLongArray,
    decision: jint,
) {
    let ids = read_longs(&mut env, &ids);
    with_store(h, |s| {
        let e: Vec<(u64, u64)> = ids.iter().map(|&i| (i as u64, decision as u64)).collect();
        let _ = s.decisions.put_many(&e);
    });
}

/// All decisions, interleaved [id, decision, id, decision, ...].
#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_storeDecisions(mut env: JNIEnv, _c: JClass, h: jlong) -> jlongArray {
    let v = with_store(h, |s| {
        let mut v = Vec::with_capacity(s.decisions.map.len() * 2);
        for (k, d) in &s.decisions.map {
            v.push(*k as i64);
            v.push(*d as i64);
        }
        v
    })
    .unwrap_or_default();
    new_longs(&mut env, &v)
}

#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_storeReset(_env: JNIEnv, _c: JClass, h: jlong) {
    with_store(h, |s| {
        let _ = s.decisions.clear();
    });
}

/// Ids from `ids` that have no cached hash yet (order preserved).
#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_missingHashes(mut env: JNIEnv, _c: JClass, h: jlong, ids: JLongArray) -> jlongArray {
    let ids = read_longs(&mut env, &ids);
    let v = with_store(h, |s| ids.iter().copied().filter(|i| !s.hashes.map.contains_key(&(*i as u64))).collect::<Vec<_>>())
        .unwrap_or_default();
    new_longs(&mut env, &v)
}

/// Hashes `count` packed 32x32 grayscale thumbnails and stores them for `ids`.
#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_hashAndStore(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    ids: JLongArray,
    pixels: JByteArray,
) -> jint {
    let ids = read_longs(&mut env, &ids);
    let px = env.convert_byte_array(&pixels).unwrap_or_default();
    let hashes = hash::phash_batch(&px, ids.len());
    with_store(h, |s| {
        let e: Vec<(u64, u64)> = ids.iter().zip(&hashes).map(|(&i, &hh)| (i as u64, encode_hash(hh))).collect();
        let _ = s.hashes.put_many(&e);
    });
    hashes.len() as jint
}

/// Similar-photo group label for each id (-1 = no siblings), using cached hashes.
#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_similarGroups(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    ids: JLongArray,
    times: JLongArray,
    threshold: jint,
    window_ms: jlong,
) -> jintArray {
    let ids = read_longs(&mut env, &ids);
    let times = read_longs(&mut env, &times);
    let hashes: Vec<Option<u64>> = with_store(h, |s| {
        ids.iter().map(|i| s.hashes.map.get(&(*i as u64)).map(|v| decode_hash(*v))).collect()
    })
    .unwrap_or_else(|| vec![None; ids.len()]);
    let labels = cluster::similar_groups(&hashes, &times, threshold.max(0) as u32, window_ms);
    new_ints(&mut env, &labels)
}

/// Moment label for each (time-sorted) timestamp.
#[no_mangle]
pub extern "system" fn Java_com_photomatch_core_NativeCore_moments(mut env: JNIEnv, _c: JClass, times: JLongArray, gap_ms: jlong) -> jintArray {
    let times = read_longs(&mut env, &times);
    let labels = cluster::moments(&times, gap_ms);
    new_ints(&mut env, &labels)
}
