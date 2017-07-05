/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.jartool/sun.tools.jar java.base/jdk.internal.util.jar
 * @run testng TestVersionedStream
 */

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class TestVersionedStream {
    private String userdir;

    @BeforeClass
    public void initialize() {
        userdir = System.getProperty("user.dir", ".");

        // These are not real class files even though they end with .class.
        // They are resource files so jar tool validation won't reject them.
        // But they are what we want to test, especially q/Bar.class that
        // could be in a concealed package if this was a modular multi-release
        // jar.
        createFiles(
                "base/p/Foo.class",
                "base/p/Main.class",
                "v9/p/Foo.class",
                "v10/p/Foo.class",
                "v10/q/Bar.class",
                "v11/p/Foo.class"
        );

        jar("cf mmr.jar -C base . --release 9 -C v9 . --release 10 -C v10 . --release 11 -C v11 .");

        System.out.println("Contents of mmr.jar\n=======");
        jar("tf mmr.jar");
        System.out.println("=======");
    }

    @AfterClass
    public void close() throws IOException {
        Path root = Paths.get(userdir);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(root)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @DataProvider
    public Object[][] data() {
        List<String> p = List.of(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "p/",
                "p/Foo.class",
                "p/Main.class"
        );
        List<String> q = List.of(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "p/",
                "p/Foo.class",
                "p/Main.class",
                "q/",
                "q/Bar.class"
        );
        Runtime.Version rt = JarFile.runtimeVersion();
        return new Object[][] {
                {Runtime.Version.parse("8"), p},
                {Runtime.Version.parse("9"), p},
                {Runtime.Version.parse("10"), q},
                {Runtime.Version.parse("11"), q},
                {JarFile.baseVersion(), p},
                {rt, rt.major() > 9 ? q : p}
        };
    }

    @Test(dataProvider="data")
    public void test(Runtime.Version version, List<String> names) throws Exception {
        try (JarFile jf = new JarFile(new File("mmr.jar"), false, ZipFile.OPEN_READ, version);
            Stream<JarEntry> jes = jdk.internal.util.jar.VersionedStream.stream(jf))
        {
            Assert.assertNotNull(jes);

            List<JarEntry> entries = jes.collect(Collectors.toList());

            // verify the correct order
            List<String> enames = entries.stream()
                    .map(je -> je.getName())
                    .collect(Collectors.toList());
            Assert.assertEquals(enames, names);

            // verify the contents
            Map<String,String> contents = new HashMap<>();
            contents.put("p/Main.class", "base/p/Main.class\n");
            if (version.major() > 9) {
                contents.put("q/Bar.class", "v10/q/Bar.class\n");
            }
            switch (version.major()) {
                case 8:
                    contents.put("p/Foo.class", "base/p/Foo.class\n");
                    break;
                case 9:
                    contents.put("p/Foo.class", "v9/p/Foo.class\n");
                    break;
                case 10:
                    contents.put("p/Foo.class", "v10/p/Foo.class\n");
                    break;
                case 11:
                    contents.put("p/Foo.class", "v11/p/Foo.class\n");
                    break;
                default:
                    Assert.fail("Test out of date, please add more cases");
            }

            contents.entrySet().stream().forEach(e -> {
                String name = e.getKey();
                int i = enames.indexOf(name);
                Assert.assertTrue(i != -1, name + " not in enames");
                JarEntry je = entries.get(i);
                try (InputStream is = jf.getInputStream(je)) {
                    String s = new String(is.readAllBytes());
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
                .map(f -> "file:///" + userdir + "/" + f)
                .map(f -> URI.create(f))
                .filter(u -> u != null)
                .map(u -> Paths.get(u))
                .forEach(p -> {
                    try {
                        Files.createDirectories(p.getParent());
                        Files.createFile(p);
                        list.clear();
                        list.add(p.toString());
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
