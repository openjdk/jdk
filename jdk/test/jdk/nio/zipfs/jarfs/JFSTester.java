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
 * @bug 8164389
 * @summary walk entries in a jdk.nio.zipfs.JarFileSystem
 * @modules jdk.jartool/sun.tools.jar
 *          jdk.zipfs
 * @run testng JFSTester
 */

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JFSTester {
    private URI jarURI;
    private Path jarfile;

    @BeforeClass
    public void initialize() throws Exception {
        String userdir = System.getProperty("user.dir",".");
        jarfile = Paths.get(userdir, "test.jar");
        String srcdir = System.getProperty("test.src");
        String[] args = (
                        "-cf "
                        + jarfile.toString()
                        + " -C "
                        + srcdir
                        + " root --release 9 -C "
                        + srcdir
                        + System.getProperty("file.separator")
                        + "v9 root"
        ).split(" +");
        new sun.tools.jar.Main(System.out, System.err, "jar").run(args);
        String ssp = jarfile.toUri().toString();
        jarURI = new URI("jar", ssp, null);
    }

    @AfterClass
    public void close() throws IOException {
        Files.deleteIfExists(jarfile);
    }

    @Test
    public void testWalk() throws IOException {

        // no configuration, treat multi-release jar as unversioned
        Map<String,String> env = new HashMap<>();
        Set<String> contents = doTest(env);
        Set<String> baseContents = Set.of(
                "This is leaf 1.\n",
                "This is leaf 2.\n",
                "This is leaf 3.\n",
                "This is leaf 4.\n"
        );
        Assert.assertEquals(contents, baseContents);

        // a configuration and jar file is multi-release
        env.put("multi-release", "9");
        contents = doTest(env);
        Set<String> versionedContents = Set.of(
                "This is versioned leaf 1.\n",
                "This is versioned leaf 2.\n",
                "This is versioned leaf 3.\n",
                "This is versioned leaf 4.\n"
        );
        Assert.assertEquals(contents, versionedContents);
    }

    private Set<String> doTest(Map<String,String> env) throws IOException {
        Set<String> contents;
        try (FileSystem fs = FileSystems.newFileSystem(jarURI, env)) {
            Path root = fs.getPath("root");
            contents = Files.walk(root)
                    .filter(p -> !Files.isDirectory(p))
                    .map(this::pathToContents)
                    .collect(Collectors.toSet());
        }
        return contents;
    }

    private String pathToContents(Path path) {
        try {
            return new String(Files.readAllBytes(path));
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }
}
