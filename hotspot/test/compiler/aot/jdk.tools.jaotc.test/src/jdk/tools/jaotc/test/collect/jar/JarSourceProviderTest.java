/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.aot/jdk.tools.jaotc
 *          jdk.aot/jdk.tools.jaotc.collect
 *          jdk.aot/jdk.tools.jaotc.collect.jar
 * @compile ../Utils.java
 * @compile ../FakeFileSupport.java
 * @compile ../FakeSearchPath.java
 *
 * @run junit/othervm jdk.tools.jaotc.test.collect.jar.JarSourceProviderTest
 */

package jdk.tools.jaotc.test.collect.jar;

import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.jar.JarSourceProvider;
import jdk.tools.jaotc.test.collect.FakeFileSupport;
import jdk.tools.jaotc.test.collect.FakeSearchPath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.Set;

import static jdk.tools.jaotc.test.collect.Utils.set;

public class JarSourceProviderTest {

    private FakeFileSupport fileSupport;
    private JarSourceProvider target;

    @Before
    public void setUp() throws Exception {
        fileSupport = new FakeFileSupport(set(), set());
        target = new JarSourceProvider(fileSupport);
    }

    @Test
    public void itShouldUseSearchPathToFindPath() {
        Set<String> visited = set();
        JarSourceProvider target = new JarSourceProvider(fileSupport);
        FakeSearchPath searchPath = new FakeSearchPath(null);
        ClassSource source = target.findSource("hello", searchPath);

        Assert.assertEquals(set("hello"), searchPath.entries);
    }

    @Test
    public void itShouldReturnNullIfPathIsNull() {
        JarSourceProvider target = new JarSourceProvider(fileSupport);
        ClassSource source = target.findSource("foobar", new FakeSearchPath(null));
        Assert.assertNull(source);
    }

    @Test
    public void itShouldReturnNullIfPathIsDirectory() {
        fileSupport.addDirectory("hello/foobar");
        ClassSource source = target.findSource("foobar", new FakeSearchPath("hello/foobar"));

        Assert.assertNull(source);
        Assert.assertEquals(set("hello/foobar"), fileSupport.getCheckedDirectory());
    }

    @Test
    public void itShouldReturnNullIfUnableToMakeJarFileSystem() {
        fileSupport.setJarFileSystemRoot(null);
        ClassSource result = target.findSource("foobar", new FakeSearchPath("foo/bar"));

        Assert.assertEquals(set("foo/bar"), fileSupport.getCheckedJarFileSystemRoots());
        Assert.assertNull(result);
    }

    @Test
    public void itShouldReturnNullIfNotValidJarProvider() {
        fileSupport = new FakeFileSupport(set(), set()) {

            @Override
            public Path getJarFileSystemRoot(Path jarFile) {
                super.getJarFileSystemRoot(jarFile);
                throw new ProviderNotFoundException();
            }
        };
        fileSupport.setJarFileSystemRoot(null);
        target = new JarSourceProvider(fileSupport);

        ClassSource result = target.findSource("foobar", new FakeSearchPath("foo/bar"));

        Assert.assertEquals(set("foo/bar"), fileSupport.getCheckedJarFileSystemRoots());
        Assert.assertNull(result);
    }

    @Test
    public void itShouldReturnSourceWhenAllIsValid() {
        fileSupport.setJarFileSystemRoot(Paths.get("some/bar"));
        ClassSource result = target.findSource("foobar", new FakeSearchPath("this/bar"));

        Assert.assertEquals(set("this/bar"), fileSupport.getClassloaderPaths());
        Assert.assertEquals("jar:this/bar", result.toString());
    }
}
