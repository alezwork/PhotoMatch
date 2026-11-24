# Streams a directory as an uncompressed UTF-8 tar to stdout. Usage: tar_stream.py DIR
import sys, os, tarfile
src = sys.argv[1]
with tarfile.open(fileobj=sys.stdout.buffer, mode='w|', format=tarfile.PAX_FORMAT, encoding='utf-8') as t:
    for root, dirs, files in os.walk(src):
        for f in files:
            p = os.path.join(root, f)
            t.add(p, arcname=os.path.relpath(p, src).replace(os.sep, '/'), recursive=False)
