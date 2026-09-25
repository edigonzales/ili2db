package ch.ehi.ili2db;

import static org.junit.Assert.*;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2db.ibx.IbxExport;
import ch.interlis.ibx.api.*;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.xtf.*;
import ch.interlis.iox.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public abstract class IbxExportTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  protected abstract AbstractTestSetup setup(Path directory);

  @Test
  public void export23() throws Exception {
    roundtrip("2.3");
  }

  @Test
  public void export24() throws Exception {
    roundtrip("2.4");
  }

  private void roundtrip(String version) throws Exception {
    Path dir = temp.getRoot().toPath();
    AbstractTestSetup db = setup(dir);
    db.resetDb();
    Path ili = dir.resolve("IbxTest.ili");
    String model =
        "INTERLIS "
            + version
            + ";\n"
            + "MODEL IbxTest (en) AT \"https://example.org\" VERSION \"2026-09-25\" =\n"
            + "DOMAIN Coord = COORD 0.000 .. 3000000.000, 0.000 .. 3000000.000;\n"
            + "TOPIC Data =\n"
            + "STRUCTURE Detail = label : TEXT*50; END Detail;\n"
            + "CLASS Item = name : TEXT*50; amount : 0.00000 .. 100.00000; point : Coord; details :"
            + " LIST {0..*} OF Detail; END Item;\n"
            + "ASSOCIATION Link = a -- {0..*} Item; b -- {0..*} Item; END Link;\n"
            + "END Data; END IbxTest.\n";
    Files.write(ili, model.getBytes("UTF-8"));
    ch.interlis.ili2c.config.Configuration compile = new ch.interlis.ili2c.config.Configuration();
    compile.addFileEntry(
        new ch.interlis.ili2c.config.FileEntry(
            ili.toString(), ch.interlis.ili2c.config.FileEntryKind.ILIMODELFILE));
    TransferDescription td = ch.interlis.ili2c.Main.runCompiler(compile);
    assertNotNull(td);
    Path input = dir.resolve("input.xtf");
    XtfWriter writer = new XtfWriter(input.toFile(), td);
    try {
      writer.write(new ch.interlis.iox_j.StartTransferEvent("ibx-test"));
      writer.write(new ch.interlis.iox_j.StartBasketEvent("IbxTest.Data", "b1"));
      for (int i = 1; i <= 2; i++) {
        IomObject object = new Iom_jObject("IbxTest.Data.Item", "o" + i);
        object.setattrvalue("name", "item" + i);
        object.setattrvalue("amount", "1.23450");
        IomObject point = object.addattrobj("point", "COORD");
        point.setattrvalue("C1", "260000" + i + ".000");
        point.setattrvalue("C2", "1200000.000");
        object.addattrobj("details", "IbxTest.Data.Detail").setattrvalue("label", "first");
        object.addattrobj("details", "IbxTest.Data.Detail").setattrvalue("label", "second");
        writer.write(new ch.interlis.iox_j.ObjectEvent(object));
      }
      IomObject link = new Iom_jObject("IbxTest.Data.Link", null);
      link.addattrobj("a", "REF").setobjectrefoid("o1");
      link.addattrobj("b", "REF").setobjectrefoid("o2");
      writer.write(new ch.interlis.iox_j.ObjectEvent(link));
      writer.write(new ch.interlis.iox_j.EndBasketEvent());
      writer.write(new ch.interlis.iox_j.EndTransferEvent());
    } finally {
      writer.close();
    }
    Config config = db.initConfig(input.toString(), dir.resolve("import.log").toString());
    config.setModeldir(dir.toString());
    config.setModels("IbxTest");
    config.setFunction(Config.FC_IMPORT);
    config.setDoImplicitSchemaImport(true);
    config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
    config.setTidHandling(Config.TID_HANDLING_PROPERTY);
    config.setDefaultSrsCode("2056");
    config.setImportTid(true);
    Ili2db.run(config, null);
    Path xtf = dir.resolve("out.xtf"), ibx = dir.resolve("out.ibx");
    export(db, dir, xtf, false);
    export(db, dir, ibx, true);
    assertTrue(Files.size(ibx) > 0);
    IoxReader expected = Xtf24Reader.createReader(xtf.toFile());
    ((ch.interlis.iox_j.IoxIliReader) expected).setModel(td);
    try (IbxContainer c = IbxContainer.open(ibx)) {
      assertEquals(version, c.metadata().version);
      IoxReader actual = c.openTransferReader();
      try {
        assertEquals(canonical(expected), canonical(actual));
      } finally {
        actual.close();
      }
      try (Fragment objects = c.getClass("IbxTest.Data.Item")) {
        assertEquals(2, objects.objects().count());
      }
      assertEquals(1, ch.interlis.ibx.spatial.SpatialIndex.manifest(c).indexes.size());
    } finally {
      expected.close();
    }
  }

  private void export(AbstractTestSetup db, Path dir, Path file, boolean ibx) throws Exception {
    Config c = db.initConfig(file.toString(), file + ".log");
    c.setModeldir(dir.toString());
    c.setModels("IbxTest");
    c.setFunction(Config.FC_EXPORT);
    c.setExportTid(true);
    Ili2db.readSettingsFromDb(c);
    if (ibx) {
      String[] args = {
        "--ibxCompression",
        "deflate",
        "--ibxGeometryEncoding",
        "wkb",
        "--ibxGeometryCrs",
        "IbxTest.Data.Item.point=EPSG:2056",
        "--ibxSpatial",
        "IbxTest.Data.Item:point",
        "--ibxSpatialOrder",
        "IbxTest.Data.Item.point",
        "--ibxCrs",
        "EPSG:2056",
        "--ibxEmbedModels"
      };
      for (int i = 0; i < args.length; ) i = IbxExport.parse(args, i, c);
    }
    Ili2db.run(c, null);
  }

  private static List<String> canonical(IoxReader reader) throws Exception {
    List<String> result = new ArrayList<String>();
    String basket = "";
    IoxEvent e;
    while ((e = reader.read()) != null) {
      if (e instanceof StartBasketEvent) {
        StartBasketEvent b = (StartBasketEvent) e;
        basket = b.getBid();
        result.add("B:" + basket + ":" + b.getType());
      }
      if (e instanceof ObjectEvent)
        result.add("O:" + basket + ":" + object(((ObjectEvent) e).getIomObject()));
      if (e instanceof EndTransferEvent) break;
    }
    Collections.sort(result);
    return result;
  }

  private static Object object(IomObject o) {
    Map<String, Object> attrs = new TreeMap<String, Object>();
    for (int i = 0; i < o.getattrcount(); i++) {
      String name = o.getattrname(i);
      List<Object> values = new ArrayList<Object>();
      for (int j = 0; j < o.getattrvaluecount(name); j++) {
        IomObject child = o.getattrobj(name, j);
        Object value = child == null ? o.getattrprim(name, j) : object(child);
        if (child == null && (o.getobjecttag().equals("COORD") || o.getobjecttag().equals("ARC")))
          value = new java.math.BigDecimal((String) value).stripTrailingZeros().toPlainString();
        values.add(value);
      }
      attrs.put(name, values);
    }
    return Arrays.asList(
        o.getobjecttag(),
        o.getobjectoid(),
        o.getobjectrefoid(),
        o.getobjectrefbid(),
        o.getobjectreforderpos(),
        attrs);
  }
}
