/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8163798 8189611 8211728
 * @summary basic tests for multi-release jar versioned streams
 * @library /test/lib
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run junit TestVersionedStream
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import jdk.test.lib.util.FileUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestVersionedStream {

    private static final Path userdir;
    private static final Set<String> unversionedEntryNames;
    private static final int LATEST_VERSION = Runtime.version().feature();

    static {
        userdir = Paths.get(System.getProperty("user.dir", "."));

        // These are not real class files even though they end with .class.
        // They are resource files so jar tool validation won't reject them.
        // But they are what we want to test, especially q/Bar.class that
        // could be in a concealed package if this was a modular multi-release
        // jar.
        createFiles(
                "base/p/Bar.class",
                "base/p/Foo.class",
                "base/p/Main.class",
                "v9/p/Foo.class",
                "v10/p/Foo.class",
                "v10/q/Bar.class",
                "v" + LATEST_VERSION + "/p/Bar.class",
                "v" + LATEST_VERSION + "/p/Foo.class",
                "v" + LATEST_VERSION + "/META-INF/Foo.class"
        );

        jar("cf mmr.jar -C base . " +
            "--release 9 -C v9 . " +
            "--release 10 -C v10 . " +
            "--release " + LATEST_VERSION + " -C v" + LATEST_VERSION + " .");

        System.out.println("Contents of mmr.jar\n=======");
        try (JarFile jf = new JarFile("mmr.jar")) {
            unversionedEntryNames = jf.stream()
                    .map(je -> je.getName())
                    .peek(System.out::println)
                    .map(nm -> nm.startsWith("META-INF/versions/")
                            ? nm.replaceFirst("META-INF/versions/\\d+/", "")
                            : nm)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new RuntimeException("Failed to init \"unversionedEntryNames\"", e);
        }
        System.out.println("=======");
    }

    @AfterAll
    public static void close() throws IOException {
        Files.walk(userdir, 1)
                .filter(p -> !p.equals(userdir))
                .forEach(p -> {
                    try {
                        if (Files.isDirectory(p)) {
                            FileUtils.deleteFileTreeWithRetry(p);
                        } else {
                            FileUtils.deleteFileIfExistsWithRetry(p);
                        }
                    } catch (IOException x) {
                        throw new UncheckedIOException(x);
                    }
                });
    }

    public static Stream<Runtime.Version> arguments() {
        return Stream.of(
            Runtime.Version.parse("8"),
            Runtime.Version.parse("9"),
            Runtime.Version.parse("10"),
            Runtime.Version.parse(Integer.toString(LATEST_VERSION)),
            JarFile.baseVersion(),
            JarFile.runtimeVersion()
        );
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void versionTest(Runtime.Version version) throws Exception {
        try (JarFile jf = new JarFile(new File("mmr.jar"), false, ZipFile.OPEN_READ, version);
             Stream<JarEntry> jes = jf.versionedStream())
        {
            assertNotNull(jes);

            // put versioned entries in list so we can reuse them
            List<JarEntry> versionedEntries = jes.collect(Collectors.toList());

            assertTrue(versionedEntries.size() > 0);

            // also keep the names
            List<String> versionedNames = versionedEntries.stream()
                    .map(JarEntry::getName)
                    .collect(Collectors.toList());

            // verify the correct order while building enames
            List<String> unversionedOrder = new ArrayList<>(unversionedEntryNames);
            unversionedOrder.retainAll(versionedNames);

            assertIterableEquals(unversionedOrder, versionedNames,
                    "versioned entries not in same order as unversioned entries");

            // verify the contents:
            // value.[0] end of the path
            // value.[1] versioned path/real name
            Map<String,String[]> expected = new HashMap<>();

            expected.put("p/Bar.class", new String[] { "base/p/Bar.class", "p/Bar.class" });
            expected.put("p/Main.class", new String[] { "base/p/Main.class", "p/Main.class" });
            int majorVersion  = version.major();
            switch (majorVersion) {
                case 8:
                    expected.put("p/Foo.class", new String[]
                        { "base/p/Foo.class", "p/Foo.class" });
                    break;
                case 9:
                    expected.put("p/Foo.class", new String[]
                        { "v9/p/Foo.class", "META-INF/versions/9/p/Foo.class" });
                    break;
                case 10:
                    expected.put("p/Foo.class", new String[]
                        { "v10/p/Foo.class", "META-INF/versions/10/p/Foo.class" });

                    expected.put("q/Bar.class", new String[]
                        { "v10/q/Bar.class", "META-INF/versions/10/q/Bar.class" });
                    break;
                default:
                    if (majorVersion == LATEST_VERSION) {
                        expected.put("p/Bar.class",
                                     new String[] { "v" + LATEST_VERSION + "/p/Bar.class",
                                                    "META-INF/versions/" + LATEST_VERSION + "/p/Bar.class"});
                        expected.put("p/Foo.class",
                                     new String[]{ "v" + LATEST_VERSION + "/p/Foo.class",
                                                   "META-INF/versions/" + LATEST_VERSION + "/p/Foo.class"});
                        expected.put("q/Bar.class",
                                     new String[] { "q/Bar.class", "META-INF/versions/10/q/Bar.class"});
                    } else {
                        fail("Test out of date, please add more cases");
                    }
            }

            expected.entrySet().stream().forEach(e -> {
                String name = e.getKey();
                int i = versionedNames.indexOf(name);
                assertTrue(i != -1, name + " not in enames");
                JarEntry je = versionedEntries.get(i);
                try (InputStream is = jf.getInputStream(je)) {
                    String s = new String(is.readAllBytes()).replaceAll(System.lineSeparator(), "");
                    // end of the path
                    assertTrue(s.endsWith(e.getValue()[0]), s);
                    // getRealName()
                    assertTrue(je.getRealName().equals(e.getValue()[1]));
                } catch (IOException x) {
                    throw new UncheckedIOException(x);
                }
            });

            if (!unversionedEntryNames.contains("META-INF/Foo.class") ||
                versionedNames.indexOf("META-INF/Foo.class") != -1) {
                fail("versioned META-INF/Foo.class test failed");
            }
        }
    }

    private static void createFiles(String... files) {
        ArrayList<String> list = new ArrayList();
        Arrays.stream(files)
                .map(f -> Paths.get(userdir.toAbsolutePath().toString(), f))
                .forEach(p -> {
                    try {
                        Files.createDirectories(p.getParent());
                        Files.createFile(p);
                        list.clear();
                        list.add(p.toString().replace(File.separatorChar, '/'));
                        Files.write(p, list);
                    } catch (IOException x) {
                        throw new UncheckedIOException(x);
                    }});
    }

    private static void jar(String args) {
        ToolProvider jar = ToolProvider.findFirst("jar").orElseThrow();
        jar.run(System.out, System.err, args.split(" +"));
    }
}
