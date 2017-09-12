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
 * @build jdk.tools.jaotc.test.collect.Utils
 * @run junit/othervm jdk.tools.jaotc.test.collect.ClassSourceTest
 */

package jdk.tools.jaotc.test.collect;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;

import static jdk.tools.jaotc.collect.ClassSource.makeClassName;

import static jdk.tools.jaotc.test.collect.Utils.getpath;

public class ClassSourceTest {
    @Test(expected=IllegalArgumentException.class)
    public void itShouldThrowExceptionIfPathDoesntEndWithClass() {
        makeClassName(Paths.get("Bar.clazz"));
    }

    @Test
    public void itShouldReplaceSlashesWithDots() {
        Assert.assertEquals("foo.Bar", makeClassName(getpath("foo/Bar.class")));
    }

    @Test
    public void itShouldStripLeadingSlash() {
        Assert.assertEquals("Hello", makeClassName(getpath("/Hello.class")));
    }

    @Test
    public void itShouldReplaceMultipleDots() {
        Assert.assertEquals("some.foo.bar.FooBar", makeClassName(getpath("/some/foo/bar/FooBar.class")));
    }
}
