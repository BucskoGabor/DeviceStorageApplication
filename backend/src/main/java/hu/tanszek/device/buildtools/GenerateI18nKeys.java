package hu.tanszek.device.buildtools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * GenerateI18nKeys — Maven plugin goal, amely a backend message bundle-ből TypeScript union type-ot
 * + JSON dict-ot generál a frontend számára.
 *
 * <p>Használat:
 *
 * <pre>
 *   mvn generate-i18n-keys
 * </pre>
 *
 * <p>Bemenet:
 *
 * <ul>
 *   <li>{@code src/main/resources/messages_hu.properties}
 *   <li>{@code src/main/resources/messages_en.properties}
 * </ul>
 *
 * <p>Kimenet:
 *
 * <ul>
 *   <li>{@code ../../frontend/src/lib/i18n/i18n-keys.ts} — TypeScript MessageKey union type +
 *       defaultMessages
 * </ul>
 *
 * <p>A generált TypeScript fájl formátuma:
 *
 * <pre>
 *   export const i18nKeys = ['key1', 'key2', ...] as const;
 *   export type MessageKey = (typeof i18nKeys)[number];
 *   export const defaultMessages: Record<MessageKey, string> = { ... };
 * </pre>
 *
 * <p>A script a Maven build előtt fut (process-resources phase), így a frontend build mindig friss
 * i18n-keys.ts-t használ.
 */
public class GenerateI18nKeys {

  /**
   * Fő metódus — argumentumok: {@code <hu-properties-path> <en-properties-path> <output-ts-path>}.
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("Usage: GenerateI18nKeys <hu.properties> <en.properties> <output.ts>");
      System.exit(1);
    }

    Path huPropsPath = Paths.get(args[0]);
    Path enPropsPath = Paths.get(args[1]);
    Path outputPath = Paths.get(args[2]);

    // 1. Properties betöltése
    Properties huProps = loadProperties(huPropsPath);
    Properties enProps = loadProperties(enPropsPath);

    // 2. Keys union — hu és en azonos keyset (a code generator feltételezi, hogy igen)
    List<String> keys = new ArrayList<>(huProps.stringPropertyNames());
    keys.sort(String::compareTo);

    // 3. TypeScript fájl generálása
    StringBuilder ts = new StringBuilder();
    ts.append("/**\n");
    ts.append(" * i18n keys — generated from backend messages bundles\n");
    ts.append(" * Run `mvn generate-i18n-keys` to regenerate.\n");
    ts.append(" * Source: messages_hu.properties + messages_en.properties\n");
    ts.append(" */\n\n");
    ts.append("export const i18nKeys = [\n");
    for (int i = 0; i < keys.size(); i++) {
      String key = keys.get(i);
      String escaped = key.replace("'", "\\'");
      ts.append("    '").append(escaped).append("'");
      if (i < keys.size() - 1) {
        ts.append(",");
      }
      ts.append("\n");
    }
    ts.append("] as const;\n\n");
    ts.append("export type MessageKey = (typeof i18nKeys)[number];\n\n");
    ts.append("/**\n");
    ts.append(" * Default messages (Hungarian fallback).\n");
    ts.append(" */\n");
    ts.append("export const defaultMessages: Record<MessageKey, string> = {\n");
    for (String key : keys) {
      String huValue = huProps.getProperty(key, "");
      String escaped =
          huValue
              .replace("\\", "\\\\")
              .replace("\"", "\\\"")
              .replace("\n", "\\n")
              .replace("\r", "");
      ts.append("    \"").append(key).append("\": \"").append(escaped).append("\",\n");
    }
    ts.append("};\n");

    // 4. Könyvtár létrehozása + fájl írása
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, ts.toString());

    System.out.println("Generated " + keys.size() + " i18n keys -> " + outputPath);
  }

  /** Properties fájl betöltése UTF-8 kódolással. */
  private static Properties loadProperties(Path path) throws IOException {
    Properties props = new Properties();
    try (InputStream is = Files.newInputStream(path);
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
      props.load(reader);
    }
    return props;
  }
}
