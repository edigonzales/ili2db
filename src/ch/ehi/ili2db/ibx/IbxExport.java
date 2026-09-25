package ch.ehi.ili2db.ibx;

import ch.ehi.ili2db.base.Ili2dbException;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.ibx.api.*;
import ch.interlis.ibx.iox.IbxWriter;
import ch.interlis.ibx.spatial.SpatialIndex;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.xtf.XtfModel;
import java.io.File;
import java.text.ParseException;
import java.util.*;

/** Configuration adapter; all IBX encoding remains in iox-ibx. */
public final class IbxExport {
  private static final String PREFIX = "ch.ehi.ili2db.ibx.";
  private static final Set<String> FLAGS =
      new HashSet<String>(Arrays.asList("EmbedModels", "Overwrite"));
  private static final Set<String> VALUES =
      new HashSet<String>(
          Arrays.asList(
              "ChunkSize",
              "Compression",
              "CompressionLevel",
              "NumericEncoding",
              "Spatial",
              "SpatialOrder",
              "SpatialPacking",
              "GeometryEncoding",
              "GeometryCrs",
              "Crs"));
  private static final Set<String> REPEAT =
      new HashSet<String>(Arrays.asList("Spatial", "SpatialOrder", "GeometryCrs"));

  private IbxExport() {}

  public static boolean isIbx(Config config) {
    return config.getXtffile() != null
        && config.getXtffile().toLowerCase(Locale.ROOT).endsWith(".ibx");
  }

  public static int parse(String[] args, int i, Config config) throws ParseException {
    String name = args[i].substring("--ibx".length());
    if (FLAGS.contains(name)) {
      config.setValue(PREFIX + name, "true");
      return i + 1;
    }
    if (!VALUES.contains(name)) throw new ParseException("Unknown IBX option: " + args[i], i);
    if (i + 1 >= args.length || args[i + 1].startsWith("--"))
      throw new ParseException("Missing value for " + args[i], i);
    String old = config.getValue(PREFIX + name);
    config.setValue(
        PREFIX + name,
        REPEAT.contains(name) && old != null ? old + "\n" + args[i + 1] : args[i + 1]);
    return i + 2;
  }

  private static String value(Config c, String key, String fallback) {
    String v = c.getValue(PREFIX + key);
    return v == null ? fallback : v;
  }

  private static List<String> values(Config c, String key) {
    String v = c.getValue(PREFIX + key);
    return v == null ? Collections.<String>emptyList() : Arrays.asList(v.split("\n"));
  }

  public static WriterOptions options(Config c) {
    WriterOptions o = new WriterOptions();
    o.chunkSize = Integer.parseInt(value(c, "ChunkSize", "262144"));
    o.compression = value(c, "Compression", "zstd");
    o.compressionLevel = Integer.parseInt(value(c, "CompressionLevel", "3"));
    o.numericEncoding = value(c, "NumericEncoding", "lexical");
    o.geometryEncoding = value(c, "GeometryEncoding", "iom");
    o.embedModels = Boolean.parseBoolean(value(c, "EmbedModels", "false"));
    o.overwrite = Boolean.parseBoolean(value(c, "Overwrite", "false"));
    for (String order : values(c, "SpatialOrder")) {
      int split = order.lastIndexOf('.');
      if (split <= 0 || split == order.length() - 1)
        throw new IllegalArgumentException("Expected Class.Attribute: " + order);
      if (o.spatialOrder.put(order.substring(0, split), order.substring(split + 1)) != null)
        throw new IllegalArgumentException("Duplicate spatial-order class: " + order);
    }
    for (String entry : values(c, "GeometryCrs")) {
      int split = entry.indexOf('=');
      if (split <= 0 || split == entry.length() - 1)
        throw new IllegalArgumentException("Expected Class.Attribute=CRS: " + entry);
      if (o.geometryCrs.put(entry.substring(0, split), entry.substring(split + 1)) != null)
        throw new IllegalArgumentException("Duplicate geometry CRS: " + entry);
    }
    o.validate();
    SpatialIndexOptions spatial = new SpatialIndexOptions();
    spatial.packing = value(c, "SpatialPacking", "str");
    spatial.validate();
    for (String entry : values(c, "Spatial")) indexParts(entry);
    return o;
  }

  public static void validate(Config c) throws Ili2dbException {
    boolean hasOptions = false;
    for (String name : FLAGS) hasOptions |= c.getValue(PREFIX + name) != null;
    for (String name : VALUES) hasOptions |= c.getValue(PREFIX + name) != null;
    if (!isIbx(c) && !hasOptions) return;
    if (!isIbx(c) || c.getFunction() != Config.FC_EXPORT)
      throw new Ili2dbException(
          "IBX options require --export and an .ibx output file; IBX import is not supported");
    if (c.isItfTransferfile() || c.getTransferFileFormat() != null)
      throw new Ili2dbException("IBX cannot be combined with ITF/GML transfer format options");
    try {
      options(c);
    } catch (IllegalArgumentException e) {
      throw new Ili2dbException("Invalid IBX options: " + e.getMessage(), e);
    }
  }

  public static IbxWriter writer(File file, TransferDescription model, Config c, XtfModel[] models)
      throws ch.interlis.iox.IoxException {
    IbxWriter writer = new IbxWriter(file, model, options(c));
    if (models != null && models.length > 0) {
      String[] names = new String[models.length];
      for (int i = 0; i < models.length; i++) names[i] = models[i].getName();
      writer.setModels(names);
    }
    return writer;
  }

  private static String[] indexParts(String entry) {
    String[] p = entry.split(":", -1);
    if (p.length != 2 || p[0].isEmpty() || p[1].isEmpty())
      throw new IllegalArgumentException("Expected Class:Attribute: " + entry);
    return p;
  }

  public static void addIndexes(File file, Config c) throws Ili2dbException {
    SpatialIndexOptions o = new SpatialIndexOptions();
    o.packing = value(c, "SpatialPacking", "str");
    try {
      for (String entry : values(c, "Spatial")) {
        String[] p = indexParts(entry);
        SpatialIndex.add(file.toPath(), p[0], p[1], value(c, "Crs", null), o);
      }
    } catch (Exception e) {
      throw new Ili2dbException(
          "IBX core exported, but spatial index failed: " + e.getMessage(), e);
    }
  }

  public static void printHelp() {
    System.err.println("IBX export: use an .ibx output file (INTERLIS 2.3/2.4 FULL).");
    System.err.println(
        "--ibxChunkSize bytes --ibxCompression zstd|deflate|none --ibxCompressionLevel n");
    System.err.println("--ibxNumericEncoding lexical|decimal --ibxGeometryEncoding iom|wkb");
    System.err.println("--ibxEmbedModels --ibxOverwrite --ibxCrs CRS --ibxSpatialPacking str|x");
    System.err.println(
        "Repeatable: --ibxSpatial Class:Attribute --ibxSpatialOrder Class.Attribute");
    System.err.println("Repeatable: --ibxGeometryCrs Class.Attribute=CRS");
  }
}
