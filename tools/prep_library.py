# Makes downscaled, EXIF-preserving copies of a photo library for the emulator.
# Originals are only read. Usage: prep_library.py SRC DST [LONG_SIDE] [--images-only]
import os, sys, shutil, time
from multiprocessing import Pool
from PIL import Image, ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True
SRC, DST = sys.argv[1], sys.argv[2]
LONG = int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3].isdigit() else 2560
IMAGES_ONLY = '--images-only' in sys.argv
IMG = {'.jpg', '.jpeg', '.png', '.gif'}
VID = {'.mp4', '.3gp'}
MAXV = 10 * 1024 * 1024

def job(rel):
    src = os.path.join(SRC, rel); dst = os.path.join(DST, rel)
    try:
        if os.path.exists(dst): return 'skip'
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        st = os.stat(src); ext = os.path.splitext(rel)[1].lower()
        if ext in VID or ext in ('.gif', '.png'):
            shutil.copyfile(src, dst)
        else:
            with Image.open(src) as im:
                im.draft('RGB', (LONG, LONG))
                exif = im.info.get('exif')
                im = im.convert('RGB')
                im.thumbnail((LONG, LONG), Image.LANCZOS)
                kw = {'quality': 88, 'optimize': True}
                if exif: kw['exif'] = exif
                im.save(dst, 'JPEG', **kw)
        os.utime(dst, (st.st_atime, st.st_mtime))
        return 'ok'
    except Exception as e:
        try: os.remove(dst)
        except OSError: pass
        return 'err'

if __name__ == '__main__':
    rels = []
    for root, dirs, files in os.walk(SRC):
        for f in files:
            ext = os.path.splitext(f)[1].lower(); p = os.path.join(root, f)
            if ext in IMG or (not IMAGES_ONLY and ext in VID and os.path.getsize(p) <= MAXV):
                rels.append(os.path.relpath(p, SRC))
    print('todo', len(rels), flush=True)
    t = time.time(); c = {}
    with Pool(10) as pool:
        for i, r in enumerate(pool.imap_unordered(job, rels, chunksize=8)):
            c[r] = c.get(r, 0) + 1
            if i % 1000 == 0: print(i, c, round(time.time() - t), 's', flush=True)
    print('done', c, round(time.time() - t), 's', flush=True)
