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

package jdk.nashorn.internal.runtime.regexp.joni.test;

import jdk.nashorn.internal.runtime.regexp.joni.Regex;
import org.testng.annotations.Test;

/**
 * Joni coverage tests
 *
 * @test
 * @run testng jdk.nashorn.internal.runtime.regexp.joni.test.JoniTest
 */
@SuppressWarnings("javadoc")
public class JoniTest {

    @Test
    public void testDump() {
        new Regex("^a{3,}(.*)[z]++\\s\\1x$").dumpTree();
        new Regex("^a{3,}(.*)[z]++\\s\\1x$").dumpByteCode();
        new Regex("(abc){4,}{2,5}").dumpTree();
        new Regex("(abc){4,}{2,5}").dumpByteCode();
        new Regex("aaa|aa|bbbb|ccc").dumpTree();
        new Regex("aaa|aa|bbbb|ccc").dumpByteCode();
        new Regex("(?:ZFVR.(\\d+\\.\\d+))|(?:(?:Sversbk|TenaCnenqvfb|Vprjrnfry).(\\d+\\.\\d+))|(?:Bcren.(\\d+\\.\\d+))|(?:NccyrJroXvg.(\\d+(?:\\.\\d+)?))").dumpTree();
        new Regex("(?:ZFVR.(\\d+\\.\\d+))|(?:(?:Sversbk|TenaCnenqvfb|Vprjrnfry).(\\d+\\.\\d+))|(?:Bcren.(\\d+\\.\\d+))|(?:NccyrJroXvg.(\\d+(?:\\.\\d+)?))").dumpByteCode();
    }
}
