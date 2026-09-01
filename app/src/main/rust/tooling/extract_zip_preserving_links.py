import os
import shutil
import stat
import sys
import zipfile


archive_path, destination = sys.argv[1:3]
with zipfile.ZipFile(archive_path) as archive:
    for entry in archive.infolist():
        output_path = os.path.join(destination, entry.filename)
        mode = entry.external_attr >> 16
        if entry.is_dir():
            os.makedirs(output_path, exist_ok=True)
            continue
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        if stat.S_ISLNK(mode):
            target = archive.read(entry).decode("utf-8")
            if os.path.lexists(output_path):
                os.remove(output_path)
            os.symlink(target, output_path)
            continue
        with archive.open(entry) as source, open(output_path, "wb") as target:
            shutil.copyfileobj(source, target)
        if mode:
            os.chmod(output_path, stat.S_IMODE(mode))
