# IBX export preview

This branch adds direct database-to-IBX export to **ili2pg** and **ili2gpkg**.
Java 21 is required. INTERLIS 2.3 and 2.4 FULL transfers are supported; IBX import
and INITIAL/UPDATE transfer encoding are not implemented.

The existing export pipeline supplies IOX events and compiled models directly to
`ch.interlis:iox-ibx:0.1.0-SNAPSHOT`. No intermediate XTF file is created.
The normal model, dataset, basket and topic selections and export validation apply.
An `.ibx` output filename selects the writer (case insensitive). XTF/ITF/GML
export keeps its existing behavior.

```sh
java -jar ili2pg-<version>.jar --export \
  --dbhost localhost --dbdatabase data --dbusr user --dbpwd password \
  --dbschema example --models MyModel output.ibx

java -jar ili2gpkg-<version>.jar --export \
  --dbfile data.gpkg --models MyModel \
  --ibxGeometryEncoding wkb \
  --ibxGeometryCrs MyModel.Topic.Class.geom=EPSG:2056 \
  --ibxSpatial MyModel.Topic.Class:geom --ibxCrs EPSG:2056 output.ibx
```

| Option | Default / meaning |
| --- | --- |
| `--ibxChunkSize` | `262144`, uncompressed bytes; objects remain whole |
| `--ibxCompression` | `zstd`; also `deflate`, `none` |
| `--ibxCompressionLevel` | `3`, Zstandard level |
| `--ibxNumericEncoding` | `lexical`; also `decimal` |
| `--ibxGeometryEncoding` | `iom`; also `wkb` |
| `--ibxGeometryCrs Class.Attribute=CRS` | Explicit geometry CRS; repeatable for distinct attributes |
| `--ibxSpatial Class:Attribute` | Add spatial index after writing the core; repeatable |
| `--ibxSpatialOrder Class.Attribute` | Spatial order; repeatable for distinct classes |
| `--ibxSpatialPacking` | `str`; also `x` |
| `--ibxCrs` | Explicit spatial-index CRS |
| `--ibxEmbedModels` | Embed resolved ILI source files |
| `--ibxOverwrite` | Replace existing target only after successful core writing |

Use normal `--modeldir` and `--models` for model resolution. Conflicting IBX and
ITF/GML options fail. Invalid options fail before connecting to the database.
An optional index failure returns an error but leaves the completed core available.
IOX internal `_t_id` attributes are excluded, just as in XTF output.
IBX retains the model transfer version for later XTF export in ilicontainer.

## Build and test

The snapshot repository is `https://jars.interlis.guru/snapshots`; no local sibling
checkout or Maven local installation is required. Install Python docutils for the
HTML manual (`python -m pip install docutils==0.22.2`).

```sh
./gradlew test --tests '*IbxOptionsTest' \
  ili2gpkgTest --tests '*IbxExportGpkgTest' --tests '*SimpleGpkgTest' \
  ili2pgTest --tests '*IbxExportPgTest' --tests '*SimplePgTest' \
  -Ddburl=jdbc:postgresql://localhost:5432/ibx_test -Ddbusr=postgres -Ddbpwd=ibx_test
./gradlew ibxReleaseChecksums
python scripts/smoke-bindists.py
```

PostgreSQL tests need a disposable PostGIS database. They replace test schemas.
The standalone smoke test uses the `ibx_test` database with user `postgres` and
password `ibx_test`; `IBX_PG_PORT` defaults to 5432. GeoPackage tests use temporary
files. Tests compare XTF and IBX objects, structures, references and geometry for
both model versions, and exercise WKB, spatial sorting and indexing.

Every push on `codex/ibx-export` builds only pg/gpkg distributions and publishes a
GitHub prerelease after tests and standalone smoke tests pass. Pull requests only
test/build. Release assets include both bindists, a source ZIP containing the
resolved iox-ibx sources, `SHA256SUMS`, and `provenance.json` with source commits and
snapshot hashes. Source and binary JAR commits must match before packaging.
