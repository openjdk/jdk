/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires vm.aot
 * @modules jdk.aot/jdk.tools.jaotc
 *          jdk.aot/jdk.tools.jaotc.collect
 *
 * @build jdk.tools.jaotc.test.collect.Utils
 * @build jdk.tools.jaotc.test.collect.FakeFileSupport
 * @run junit/othervm jdk.tools.jaotc.test.collect.SearchPathTest
 */



package jdk.tools.jaotc.test.collect;

import static jdk.tools.jaotc.test.collect.Utils.mkpath;
import static jdk.tools.jaotc.test.collect.Utils.mkpaths;
import static jdk.tools.jaotc.test.collect.Utils.set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

import jdk.tools.jaotc.collect.SearchPath;

public class SearchPathTest {
    private FakeFileSupport fileSupport;
    private FileSystem fs;

    @Before
    public void setUp() throws Exception {
        fs = FileSystems.getDefault();
    }

    @Test
    public void itShouldUsePathIfPathIsAbsoluteAndExisting() {
        fileSupport = new FakeFileSupport(mkpaths("/foo"), set());
        SearchPath target = new SearchPath(fileSupport);
        Path foo = Paths.get(mkpath("/foo"));
        Path result = target.find(fs, foo);
        assertSame(result, foo);
    }

    @Test
    public void itShouldReturnNullIfPathIsAbsoluteAndNonExisting() {
        fileSupport = new FakeFileSupport(set(), set());
        SearchPath target = new SearchPath(fileSupport);
        Path result = target.find(fs, Paths.get(mkpath("/bar")));
        assertNull(result);
    }

    @Test
    public void itShouldUseRelativeExisting() {
        fileSupport = new FakeFileSupport(mkpaths("hello", "tmp/hello", "search/hello"), set());
        SearchPath target = new SearchPath(fileSupport);
        target.add("search");
        Path hello = Paths.get("hello");
        Path result = target.find(fs, hello, "tmp");
        assertSame(result, hello);
    }

    @Test
    public void itShouldSearchDefaultsBeforeSearchPaths() {
        fileSupport = new FakeFileSupport(mkpaths("bar/foobar"), set());
        SearchPath target = new SearchPath(fileSupport);
        Path result = target.find(fs, Paths.get("foobar"), "default1", "bar");
        assertEquals(mkpath("bar/foobar"), result.toString());
        assertEquals(mkpaths("foobar", "default1/foobar", "bar/foobar"), fileSupport.getCheckedExists());
    }

    @Test
    public void itShouldUseSearchPathsIfNotInDefaults() {
        fileSupport = new FakeFileSupport(mkpaths("bar/tmp/foobar"), set());
        SearchPath target = new SearchPath(fileSupport);
        target.add("foo/tmp", "bar/tmp");

        Path result = target.find(fs, Paths.get("foobar"), "foo", "bar");
        assertEquals(mkpath("bar/tmp/foobar"), result.toString());
        assertEquals(mkpaths("foobar", "foo/foobar", "bar/foobar", "bar/tmp/foobar", "foo/tmp/foobar"), fileSupport.getCheckedExists());
    }

    @Test
    public void itShouldReturnNullIfNoExistingPathIsFound() {
        fileSupport = new FakeFileSupport(set(), set());
        SearchPath target = new SearchPath(fileSupport);
        target.add("dir1", "dir2");

        Path result = target.find(fs, Paths.get("entry"), "dir3", "dir4");
        assertNull(result);
        assertEquals(mkpaths("entry", "dir1/entry", "dir2/entry", "dir3/entry", "dir4/entry"), fileSupport.getCheckedExists());
    }
}
