package hu.mpcalc.backend;

import hu.mpcalc.engine.MsgCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** No text can be missing: every message key the engine and the backend use exists in the Hungarian and the English file. */
class MessageKeysTest {
    private static Properties load(String name) throws IOException {
        Properties p = new Properties();
        try (InputStream in = MessageKeysTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static Set<String> keysUsedInBackendCode() throws IOException {
        Set<String> keys = new TreeSet<>();
        Pattern api = Pattern.compile("\"(api\\.[A-Za-z0-9_.]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(f);
                Matcher m = api.matcher(src);
                while (m.find()) keys.add(m.group(1));
            }
        }
        // the validate differences: "api.validate." + the code of the difference
        String validation = Files.readString(Path.of("src/main/java/hu/mpcalc/backend/service/ValidationService.java"));
        Matcher codes = Pattern.compile("\"([A-Z][A-Z_]{4,})\"").matcher(validation);
        while (codes.find()) keys.add("api.validate." + codes.group(1));
        keys.remove("api.validate.");
        return keys;
    }

    @Test
    void everyEngineCodeAndEveryBackendKeyHasHungarianAndEnglishText() throws IOException {
        Properties hu = load("/messages.properties");
        Properties en = load("/messages_en.properties");
        Set<String> needed = new TreeSet<>(keysUsedInBackendCode());
        for (MsgCode c : MsgCode.values()) needed.add(c.key);
        assertTrue(needed.size() > 100, "the scan found only " + needed.size() + " keys");
        for (String k : needed) {
            assertTrue(hu.containsKey(k), "messages.properties lacks " + k);
            assertTrue(en.containsKey(k), "messages_en.properties lacks " + k);
        }
    }

    @Test
    void bothFilesHaveTheSameKeysAndNoUnusedOnes() throws IOException {
        Properties hu = load("/messages.properties");
        Properties en = load("/messages_en.properties");
        assertEquals(new TreeSet<>(hu.stringPropertyNames()), new TreeSet<>(en.stringPropertyNames()));
        Set<String> used = new TreeSet<>(keysUsedInBackendCode());
        for (MsgCode c : MsgCode.values()) used.add(c.key);
        Set<String> unused = new TreeSet<>(hu.stringPropertyNames());
        unused.removeAll(used);
        assertTrue(unused.isEmpty(), "keys in the message files that no code uses: " + unused);
    }

    @Test
    void noMessageUsesASingleQuoteWhichMessageFormatWouldSwallow() throws IOException {
        for (String f : new String[] {"/messages.properties", "/messages_en.properties"}) {
            Properties p = load(f);
            for (String k : p.stringPropertyNames()) assertFalse(p.getProperty(k).contains("'"), f + " " + k);
        }
    }
}
