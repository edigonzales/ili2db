package ch.ehi.ili2db;

import static org.junit.Assert.*;

import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2db.ibx.IbxExport;
import org.junit.Test;

public class IbxOptionsTest {
  private Config config(String... args) throws Exception {
    Config c = new Config();
    c.setFunction(Config.FC_EXPORT);
    c.setXtffile("out.ibx");
    for (int i = 0; i < args.length; ) i = IbxExport.parse(args, i, c);
    return c;
  }

  @Test
  public void defaultsAndRepeatedOptions() throws Exception {
    Config c =
        config(
            "--ibxGeometryCrs", "M.T.A.geom=EPSG:2056", "--ibxGeometryCrs", "M.T.B.geom=EPSG:2056");
    IbxExport.validate(c);
    assertEquals(2, IbxExport.options(c).geometryCrs.size());
    assertEquals("zstd", IbxExport.options(c).compression);
    assertEquals(262144, IbxExport.options(c).chunkSize);
    assertFalse(IbxExport.options(c).overwrite);
  }

  @Test
  public void rejectsWrongActionFormatAndMalformedOptions() throws Exception {
    Config importConfig = config();
    importConfig.setFunction(Config.FC_IMPORT);
    Config gml = config();
    gml.setTransferFileFormat(Config.ILIGML20);
    Config xtf = config("--ibxOverwrite");
    xtf.setXtffile("out.xtf");
    for (Config c :
        new Config[] {
          importConfig,
          gml,
          xtf,
          config("--ibxChunkSize", "0"),
          config("--ibxSpatial", "bad"),
          config("--ibxSpatialPacking", "bad"),
          config("--ibxCompression", "bad"),
          config("--ibxGeometryCrs", "bad")
        }) {
      try {
        IbxExport.validate(c);
        fail("accepted invalid config");
      } catch (ch.ehi.ili2db.base.Ili2dbException expected) {
      }
    }
    try {
      config("--ibxCompression");
      fail();
    } catch (java.text.ParseException expected) {
    }
    try {
      config("--ibxUnknown");
      fail();
    } catch (java.text.ParseException expected) {
    }
    Config regular = new Config();
    regular.setFunction(Config.FC_EXPORT);
    regular.setXtffile("out.xtf");
    IbxExport.validate(regular);
  }
}
