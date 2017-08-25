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
 * @requires vm.aot
 * @modules jdk.aot/jdk.tools.jaotc
 *          jdk.aot/jdk.tools.jaotc.collect
 *          jdk.aot/jdk.tools.jaotc.collect.directory
 * @compile ../Utils.java
 * @compile ../FakeFileSupport.java
 * @run junit/othervm jdk.tools.jaotc.test.collect.directory.DirectorySourceProviderTest
 */

package jdk.tools.jaotc.test.collect.directory;

import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.directory.DirectorySourceProvider;
import jdk.tools.jaotc.test.collect.FakeFileSupport;
import jdk.tools.jaotc.collect.FileSupport;
import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Set;

import static jdk.tools.jaotc.test.collect.Utils.set;

public class DirectorySourceProviderTest {
    @Test
    public void itShouldReturnNullForNonExistantPath() {
        DirectorySourceProvider target = new DirectorySourceProvider(new FakeFileSupport(set(), set()));
        ClassSource result = target.findSource("hello", null);
        Assert.assertNull(result);
    }

    @Test
    public void itShouldReturnNullForNonDirectory() {
        DirectorySourceProvider target = new DirectorySourceProvider(new FakeFileSupport(set("foobar"), set()));
        ClassSource result = target.findSource("foobar", null);
        Assert.assertNull(result);
    }

    @Test
    public void itShouldReturnNullForMalformedURI() {
        Set<String> visited = set();
        DirectorySourceProvider target = new DirectorySourceProvider(new FakeFileSupport(set("foobar"), set("foobar")) {
            @Override
            public ClassLoader createClassLoader(Path path) throws MalformedURLException {
                visited.add("1");
                throw new MalformedURLException("...");
            }
        });
        ClassSource result = target.findSource("foobar", null);
        Assert.assertNull(result);
        Assert.assertEquals(set("1"), visited);
    }

    @Test
    public void itShouldCreateSourceIfNameExistsAndIsADirectory() {
        FileSupport fileSupport = new FakeFileSupport(set("foo"), set("foo"));
        DirectorySourceProvider target = new DirectorySourceProvider(fileSupport);
        ClassSource foo = target.findSource("foo", null);
        Assert.assertNotNull(foo);
        Assert.assertEquals("directory:foo", foo.toString());
    }

}
