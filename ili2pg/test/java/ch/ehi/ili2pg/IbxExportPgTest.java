package ch.ehi.ili2pg;

import ch.ehi.ili2db.AbstractTestSetup;
import java.nio.file.Path;

public class IbxExportPgTest extends ch.ehi.ili2db.IbxExportTest {
  @Override
  protected AbstractTestSetup setup(Path directory) {
    return new PgTestSetup(
        System.getProperty("dburl"),
        System.getProperty("dbusr"),
        System.getProperty("dbpwd"),
        "ibx_export");
  }
}
