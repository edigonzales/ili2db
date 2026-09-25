"""Exercise only the unpacked release JARs, using a disposable output directory."""
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'

with tempfile.TemporaryDirectory(prefix='ibx-bindist-') as directory:
    work = Path(directory)
    for variant in ('ili2gpkg', 'ili2pg'):
        archives = sorted((ROOT / 'dist').glob(variant + '-*.zip'), key=lambda p: p.stat().st_mtime)
        if not archives:
            raise RuntimeError('Missing bindist: ' + variant)
        install = work / variant
        with zipfile.ZipFile(archives[-1]) as archive:
            archive.extractall(install)
        jar, = install.glob(variant + '-*.jar')
        command = [JAVA, '-jar', str(jar)]
        subprocess.run(command + ['--help'], cwd=work, check=True)
        if variant == 'ili2gpkg':
            connection = ['--dbfile', str(work / 'smoke.gpkg')]
        else:
            connection = ['--dbhost', 'localhost', '--dbport', os.environ.get('IBX_PG_PORT', '5432'),
                          '--dbdatabase', 'ibx_test', '--dbusr', 'postgres', '--dbpwd', 'ibx_test',
                          '--dbschema', 'ibx_smoke_' + str(os.getpid())]
        model = ['--models', 'SimpleCoord23', '--modeldir', str(ROOT / 'test/data/Simple')]
        subprocess.run(command + ['--import', '--doSchemaImport', '--createTidCol', '--importTid'] + connection + model +
                       [str(ROOT / 'test/data/Simple/SimpleCoord23a.xtf')], cwd=work, check=True)
        target = work / (variant + '.ibx')
        subprocess.run(command + ['--export', '--exportTid'] + connection + model + [str(target)],
                       cwd=work, check=True)
        data = target.read_bytes()
        assert data[:8] == b'IBXCONT1' and struct.unpack('>I', data[8:12])[0] == 4
        assert data[-64:-56] == b'IBXFOOT1'
        result = subprocess.run(command + ['--export', '--exportTid'] + connection + model + [str(target)],
                                cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        assert result.returncode != 0, 'An existing IBX file must require --ibxOverwrite'
        assert target.read_bytes() == data
        print(variant + ': standalone IBX export and overwrite protection passed', flush=True)
