#!/usr/bin/env python3
"""Re-deflate the big compressed entries of an (unsigned) APK with Zopfli: same bytes once inflated,
about 3-4% smaller. Stored entries (Liora clips, model.zip, resources.arsc) stay stored.
Usage: zopfli_apk.py in.apk out.apk     (needs `pip install zopfli`)"""
import sys, zipfile, zlib
from concurrent.futures import ProcessPoolExecutor
import zopfli.zlib as zz

MIN = 64 * 1024

def raw(data):
    return zz.compress(data, numiterations=1)[2:-4]   # strip the zlib header / adler32 -> raw deflate

class Pre:
    """Stands in for zipfile's compressor: hands back the Zopfli stream we already made."""
    def __init__(self, blob): self.blob = blob
    def compress(self, data): return b""
    def flush(self): return self.blob

def main(src, dst):
    zin = zipfile.ZipFile(src)
    infos = zin.infolist()
    big = [i for i in infos if i.compress_type == zipfile.ZIP_DEFLATED and i.file_size >= MIN]
    with ProcessPoolExecutor() as ex:
        blobs = dict(zip([i.filename for i in big], ex.map(raw, [zin.read(i) for i in big])))
    orig = zipfile._get_compressor
    cur = {}
    zipfile._get_compressor = lambda t, l=None: Pre(cur["blob"]) if "blob" in cur else orig(t, l)
    saved = 0
    with zipfile.ZipFile(dst, "w") as zout:
        for i in infos:
            data = zin.read(i)
            zi = zipfile.ZipInfo(i.filename, i.date_time); zi.compress_type = i.compress_type
            zi.external_attr = i.external_attr; zi.create_system = i.create_system
            if i.filename in blobs and len(blobs[i.filename]) < i.compress_size:
                cur["blob"] = blobs[i.filename]; saved += i.compress_size - len(cur["blob"])
            else:
                cur.pop("blob", None)
            zout.writestr(zi, data)
            cur.pop("blob", None)
    zipfile._get_compressor = orig
    with zipfile.ZipFile(dst) as z:                      # inflate everything back and compare
        for i in infos:
            assert z.read(i.filename) == zin.read(i), i.filename
    print(f"zopfli: {len(big)} entries re-deflated, {saved} bytes saved, contents verified")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
