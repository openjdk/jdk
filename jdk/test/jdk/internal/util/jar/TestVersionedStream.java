/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8163798
 * @summary basic tests for multi-release jar versioned streams
 * @library /lib/testlibrary
 * @modules jdk.jartool/sun.tools.jar java.base/jdk.internal.util.jar
 * @build jdk.testlibrary.FileUtils
 * @run testng TestVersionedStream
 */

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import jdk.testlibrary.FileUtils;

public class TestVersionedStream {
    private final Path userdir;
    private final Set<String> unversionedEntryNames;

    public TestVersionedStream() throws IOException {
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
                "v11/p/Bar.class",
                "v11/p/Foo.class"
        );

        jar("cf mmr.jar -C base . --release 9 -C v9 . " +
                "--release 10 -C v10 . --release 11 -C v11 .");

        System.out.println("Contents of mmr.jar\n=======");

        try(JarFile jf = new JarFile("mmr.jar")) {
            unversionedEntryNames = jf.stream()
                    .map(je -> je.getName())
                    .peek(System.out::println)
                    .map(nm -> nm.startsWith("META-INF/versions/")
                            ? nm.replaceFirst("META-INF/versions/\\d+/", "")
                            : nm)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        System.out.println("=======");
    }

    @AfterClass
    public void close() throws IOException {
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

    @DataProvider
    public Object[][] data() {
        return new Object[][] {
            {Runtime.Version.parse("8")},
            {Runtime.Version.parse("9")},
            {Runtime.Version.parse("10")},
            {Runtime.Version.parse("11")},
            {JarFile.baseVersion()},
            {JarFile.runtimeVersion()}
        };
    }

    @Test(dataProvider="data")
    public void test(Runtime.Version version) throws Exception {
        try (JarFile jf = new JarFile(new File("mmr.jar"), false, ZipFile.OPEN_READ, version);
             Stream<JarEntry> jes = jdk.internal.util.jar.VersionedStream.stream(jf))
        {
            Assert.assertNotNull(jes);

            // put versioned entries in list so we can reuse them
            List<JarEntry> versionedEntries = jes.collect(Collectors.toList());

            Assert.assertTrue(versionedEntries.size() > 0);

            // also keep the names
            List<String> versionedNames = new ArrayList<>(versionedEntries.size());

            // verify the correct order while building enames
            Iterator<String> allIt = unversionedEntryNames.iterator();
            Iterator<JarEntry> verIt = versionedEntries.iterator();
            boolean match = false;

            while (verIt.hasNext()) {
                match = false;
                if (!allIt.hasNext()) break;
                String name = verIt.next().getName();
                versionedNames.add(name);
                while (allIt.hasNext()) {
                    if (name.equals(allIt.next())) {
                        match = true;
                        break;
                    }
                }
            }
            if (!match) {
                Assert.fail("versioned entries not in same order as unversioned entries");
            }

            // verify the contents
            Map<String,String> contents = new HashMap<>();
            contents.put("p/Bar.class", "base/p/Bar.class");
            contents.put("p/Main.class", "base/p/Main.class");
            switch (version.major()) {
                case 8:
                    contents.put("p/Foo.class", "base/p/Foo.class");
                    break;
                case 9:
                    contents.put("p/Foo.class", "v9/p/Foo.class");
                    break;
                case 10:
                    contents.put("p/Foo.class", "v10/p/Foo.class");
                    contents.put("q/Bar.class", "v10/q/Bar.class");
                    break;
                case 11:
                    contents.put("p/Bar.class", "v11/p/Bar.class");
                    contents.put("p/Foo.class", "v11/p/Foo.class");
                    contents.put("q/Bar.class", "v10/q/Bar.class");
                    break;
                default:
                    Assert.fail("Test out of date, please add more cases");
            }

            contents.entrySet().stream().forEach(e -> {
                String name = e.getKey();
                int i = versionedNames.indexOf(name);
                Assert.assertTrue(i != -1, name + " not in enames");
                JarEntry je = versionedEntries.get(i);
                try (InputStream is = jf.getInputStream(je)) {
                    String s = new String(is.readAllBytes()).replaceAll(System.lineSeparator(), "");
                    Assert.assertTrue(s.endsWith(e.getValue()), s);
                } catch (IOException x) {
                    throw new UncheckedIOException(x);
                }
            });
        }
    }

    private void createFiles(String... files) {
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

    private void jar(String args) {
        new sun.tools.jar.Main(System.out, System.err, "jar")
                .run(args.split(" +"));
    }
}
