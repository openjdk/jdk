/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime.regexp.test;

import jdk.nashorn.internal.runtime.regexp.RegExp;
import jdk.nashorn.internal.runtime.regexp.RegExpFactory;
import jdk.nashorn.internal.runtime.regexp.RegExpMatcher;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

/**
 * Basic tests for the JDK based RegExp implementation.
 *
 * @test
 * @run testng jdk.nashorn.internal.runtime.regexp.test.JdkRegExpTest
 */
public class JdkRegExpTest {

    /**
     * Compile a regular expression using the JDK implementation
     */
    @Test
    public void testMatcher() {
        final RegExp regexp = new RegExpFactory().compile("f(o)o", "");
        final RegExpMatcher matcher = regexp.match("foo");
        assertNotNull(matcher);
        assertTrue(matcher.search(0));
        assertEquals(matcher.getInput(), "foo");
        assertEquals(matcher.groupCount(), 1);
        assertEquals(matcher.group(), "foo");
        assertEquals(matcher.start(), 0);
        assertEquals(matcher.end(), 3);
        assertEquals(matcher.group(1), "o");
        assertEquals(matcher.start(1), 1);
        assertEquals(matcher.end(1), 2);
    }
}
