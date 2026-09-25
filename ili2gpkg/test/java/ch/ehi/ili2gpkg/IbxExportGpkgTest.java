package ch.ehi.ili2gpkg;

import ch.ehi.ili2db.AbstractTestSetup;
import java.nio.file.Path;

public class IbxExportGpkgTest extends ch.ehi.ili2db.IbxExportTest {
  @Override
  protected AbstractTestSetup setup(Path directory) {
    String file = directory.resolve("test.gpkg").toString();
    return new GpkgTestSetup(file, "jdbc:sqlite:" + file);
  }
}
