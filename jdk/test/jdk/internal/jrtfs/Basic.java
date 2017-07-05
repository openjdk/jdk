/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic test of jrt file system provider
 * @run testng Basic
 */

import java.io.InputStream;
import java.io.DataInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

/**
 * Basic tests for jrt:/ file system provider.
 */

public class Basic {

    // Checks that the given FileSystem is a jrt file system.
    private void checkFileSystem(FileSystem fs) {
        assertTrue(fs.provider().getScheme().equalsIgnoreCase("jrt"));
        assertTrue(fs.isOpen());
        assertTrue(fs.isReadOnly());
        assertEquals(fs.getSeparator(), "/");

        // one root
        Iterator<Path> roots = fs.getRootDirectories().iterator();
        assertTrue(roots.next().toString().equals("/"));
        assertFalse(roots.hasNext());
    }

    @Test
    public void testGetFileSystem() {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        checkFileSystem(fs);

        // getFileSystem should return the same object each time
        assertTrue(fs == FileSystems.getFileSystem(URI.create("jrt:/")));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCloseFileSystem() throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        fs.close(); // should throw UOE
    }

    @Test
    public void testNewFileSystem() throws Exception {
        FileSystem theFileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
        Map<String, ?> env = Collections.emptyMap();
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), env)) {
            checkFileSystem(fs);
            assertTrue(fs != theFileSystem);
        }
    }

    @DataProvider(name = "knownClassFiles")
    private Object[][] knownClassFiles() {
        return new Object[][] {
            { "/java.base/java/lang/Object.class" },
            { "java.base/java/lang/Object.class" },
        };
    }

    @Test(dataProvider = "knownClassFiles")
    public void testKnownClassFiles(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path classFile = fs.getPath(path);

        assertTrue(Files.isRegularFile(classFile));
        assertTrue(Files.size(classFile) > 0L);

        // check magic number
        try (InputStream in = Files.newInputStream(classFile)) {
            int magic = new DataInputStream(in).readInt();
            assertEquals(magic, 0xCAFEBABE);
        }
    }

    @DataProvider(name = "knownDirectories")
    private Object[][] knownDirectories() {
        return new Object[][] {
            { "/"                     },
            { "."                     },
            { "./"                    },
            { "/."                    },
            { "/./"                   },
            { "/java.base/.."         },
            { "/java.base/../"        },
            { "/java.base/../."       },
            { "/java.base"            },
            { "/java.base/java/lang"  },
            { "java.base/java/lang"   },
            { "/java.base/java/lang/" },
            { "java.base/java/lang/"  }
        };
    }

    @Test(dataProvider = "knownDirectories")
    public void testKnownDirectories(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path dir = fs.getPath(path);

        assertTrue(Files.isDirectory(dir));

        // directory should not be empty
        try (Stream<Path> stream = Files.list(dir)) {
            assertTrue(stream.count() > 0L);
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            assertTrue(stream.count() > 0L);
        }
    }

    @DataProvider(name = "topLevelPkgDirs")
    private Object[][] topLevelPkgDirs() {
        return new Object[][] {
            { "/java/lang" },
            { "java/lang"  },
            { "/java/util" },
            { "java/util"  },
        };
    }

    @Test(dataProvider = "topLevelPkgDirs")
    public void testNotExists(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path dir = fs.getPath(path);

        // package directories should not be there at top level
        assertTrue(Files.notExists(dir));
    }

    /**
     * Test the URI of every file in the jrt file system
     */
    @Test
    public void testToAndFromUri() throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path top = fs.getPath("/");
        try (Stream<Path> stream = Files.walk(top)) {
            stream.forEach(path -> {
                URI u = path.toUri();
                assertTrue(u.getScheme().equalsIgnoreCase("jrt"));
                assertFalse(u.isOpaque());
                assertTrue(u.getAuthority() == null);
                assertEquals(u.getPath(), path.toAbsolutePath().toString());
                Path p = Paths.get(u);
                assertEquals(p, path);
            });
        }
    }

    @Test
    public void testDirectoryNames() throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path top = fs.getPath("/");
        // check that directory names do not have trailing '/' char
        try (Stream<Path> stream = Files.walk(top)) {
            stream.skip(1).filter(Files::isDirectory).forEach(path -> {
                assertFalse(path.toString().endsWith("/"));
            });
        }
    }

    @DataProvider(name = "pathPrefixs")
    private Object[][] pathPrefixes() {
        return new Object[][] {
            { "/"                       },
            { "java.base/java/lang"     },
            { "./java.base/java/lang"   },
            { "/java.base/java/lang"    },
            { "/./java.base/java/lang"  },
            { "java.base/java/lang/"    },
            { "./java.base/java/lang/"  },
            { "/./java.base/java/lang/" },
        };
    }

    @Test(dataProvider = "pathPrefixes")
    public void testParentInDirList(String dir) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path base = fs.getPath(dir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path entry: stream) {
                assertTrue( entry.getParent().equals(base) );
            }
        }
    }

    @DataProvider(name = "dirStreamStringFilterData")
    private Object[][] dirStreamStringFilterData() {
        return new Object[][] {
            { "/java.base/java/lang", "/reflect"      },
            { "/java.base/java/lang", "/Object.class" },
            { "/java.base/java/util", "/stream"       },
            { "/java.base/java/util", "/List.class"   },
        };
    }

    @Test(dataProvider = "dirStreamStringFilterData")
    public void testDirectoryStreamStringFilter(String dir, String filter) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path base = fs.getPath(dir);
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(base, p->!p.toString().endsWith(filter))) {
            for (Path entry: stream) {
                assertFalse(entry.toString().contains(filter),
                    "filtered path seen: " + filter);
            }
        }

        // make sure without filter, we do see that matching entry!
        boolean seen = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path entry: stream) {
                if (entry.toString().endsWith(filter)) {
                    seen = true;
                    break;
                }
            }
        }

        assertTrue(seen, "even without filter " + filter + " is missing");
    }

    @DataProvider(name = "dirStreamFilterData")
    private Object[][] dirStreamFilterData() {
        return new Object[][] {
            {
              "/",
              (DirectoryStream.Filter<Path>)(Files::isDirectory),
              "isDirectory"
            },
            {
              "/java.base/java/lang",
              (DirectoryStream.Filter<Path>)(Files::isRegularFile),
              "isFile"
            }
        };
    }

    @Test(dataProvider = "dirStreamFilterData")
    private void testDirectoryStreamFilter(String dir, DirectoryStream.Filter filter,
            String name) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path base = fs.getPath(dir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base, filter)) {
            for (Path entry: stream) {
                assertTrue(filter.accept(entry), "filtered path seen: " + name);
            }
        }

        // make sure without filter, we do see that matching entry!
        boolean seen = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path entry: stream) {
                if (filter.accept(entry)) {
                    seen = true;
                    break;
                }
            }
        }

        assertTrue(seen, "even without filter " + name + " is missing");
    }

    @Test
    private void testDirectoryStreamIterator() throws Exception {
        // run the tests with null filter (no filter)
        dirStreamIteratorTest(null);
        // run the same tests with trivial "accept all" filter
        dirStreamIteratorTest(p->true);
        // two other non-trivial ones
        dirStreamIteratorTest(Files::isDirectory);
        dirStreamIteratorTest(Files::isRegularFile);
    }

    private void dirStreamIteratorTest(DirectoryStream.Filter<Path> filter)
            throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        // This test assumes at least there are two elements in "java/lang"
        // package with any filter passed. don't change to different path here!
        Path dir = fs.getPath("/java.base/java/lang");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            Iterator<Path> itr = stream.iterator();
            itr.hasNext();
            Path path1 = itr.next();
            // missing second hasNext call
            Path path2 = itr.next();
            assertNotEquals(path1, path2);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            Iterator<Path> itr = stream.iterator();
            // no hasNext calls at all
            Path path1 = itr.next();
            Path path2 = itr.next();
            assertNotEquals(path1, path2);
        }

        int numEntries = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            Iterator<Path> itr = stream.iterator();
            while (itr.hasNext()) {
                numEntries++;
                itr.next();
            }

            // reached EOF, next call should result in exception
            try {
                itr.next();
                throw new AssertionError("should have thrown exception");
            } catch (NoSuchElementException nsee) {
                System.out.println("got NoSuchElementException as expected");
            }
        }

        // redundant hasNext calls
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            Iterator<Path> itr = stream.iterator();
            // any number of hasNext should definitely stay at first element
            for (int i = 0; i < 2*numEntries; i++) {
                itr.hasNext();
            }

            for (int j = 0; j < numEntries; j++) {
                itr.next();
            }
            // exactly count number of entries!
            assertFalse(itr.hasNext());
        }
    }

    @DataProvider(name = "hiddenPaths")
    private Object[][] hiddenPaths() {
        return new Object[][] {
            { "/META-INF" },
            { "/META-INF/services" },
            { "/META-INF/services/java.nio.file.spi.FileSystemProvider" },
            { "/java.base/packages.offsets" },
            { "/java.instrument/packages.offsets" },
            { "/jdk.zipfs/packages.offsets" },
            { "/java/lang" },
            { "/java/util" },
        };
    }

    @Test(dataProvider = "hiddenPaths")
    public void testHiddenPathsNotExposed(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        assertTrue(Files.notExists(fs.getPath(path)), path + " should not exist");
    }

    @DataProvider(name = "pathGlobPatterns")
    private Object[][] pathGlobPatterns() {
        return new Object[][] {
            { "/*", "/java.base", true },
            { "/*", "/java.base/java", false },
            { "/j*", "/java.base", true },
            { "/J*", "/java.base", false },
            { "**.class", "/java.base/java/lang/Object.class", true },
            { "**.java", "/java.base/java/lang/Object.class", false },
            { "**java/*", "/java.base/java/lang", true },
            { "**java/lang/ref*", "/java.base/java/lang/reflect", true },
            { "**java/lang/ref*", "/java.base/java/lang/ref", true },
            { "**java/lang/ref?", "/java.base/java/lang/ref", false },
            { "**java/lang/{ref,refl*}", "/java.base/java/lang/ref", true },
            { "**java/lang/{ref,refl*}", "/java.base/java/lang/reflect", true },
            { "**java/[a-u]?*/*.class", "/java.base/java/util/Map.class", true },
            { "**java/util/[a-z]*.class", "/java.base/java/util/TreeMap.class", false },
        };
    }

    @Test(dataProvider = "pathGlobPatterns")
    public void testGlobPathMatcher(String pattern, String path,
            boolean expectMatch) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        PathMatcher pm = fs.getPathMatcher("glob:" + pattern);
        Path p = fs.getPath(path);
        assertTrue(Files.exists(p), path);
        assertTrue(!(pm.matches(p) ^ expectMatch),
            p + (expectMatch? " should match " : " should not match ") +
            pattern);
    }

    @DataProvider(name = "pathRegexPatterns")
    private Object[][] pathRegexPatterns() {
        return new Object[][] {
            { "/.*", "/java.base", true },
            { "/[^/]*", "/java.base/java", false },
            { "/j.*", "/java.base", true },
            { "/J.*", "/java.base", false },
            { ".*\\.class", "/java.base/java/lang/Object.class", true },
            { ".*\\.java", "/java.base/java/lang/Object.class", false },
            { ".*java/.*", "/java.base/java/lang", true },
            { ".*java/lang/ref.*", "/java.base/java/lang/reflect", true },
            { ".*java/lang/ref.*", "/java.base/java/lang/ref", true },
            { ".*/java/lang/ref.+", "/java.base/java/lang/ref", false },
            { ".*/java/lang/(ref|refl.*)", "/java.base/java/lang/ref", true },
            { ".*/java/lang/(ref|refl.*)", "/java.base/java/lang/reflect", true },
            { ".*/java/[a-u]?.*/.*\\.class", "/java.base/java/util/Map.class", true },
            { ".*/java/util/[a-z]*\\.class", "/java.base/java/util/TreeMap.class", false },
        };
    }

    @Test(dataProvider = "pathRegexPatterns")
    public void testRegexPathMatcher(String pattern, String path,
            boolean expectMatch) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        PathMatcher pm = fs.getPathMatcher("regex:" + pattern);
        Path p = fs.getPath(path);
        assertTrue(Files.exists(p), path);
        assertTrue(!(pm.matches(p) ^ expectMatch),
            p + (expectMatch? " should match " : " should not match ") +
            pattern);
    }
}
