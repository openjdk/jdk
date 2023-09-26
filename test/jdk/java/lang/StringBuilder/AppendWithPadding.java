/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @test
 * @summary Test StringBuilder.append(int,int,char) sanity tests
 * @run testng/othervm -XX:-CompactStrings AppendWithPadding
 * @run testng/othervm -XX:+CompactStrings AppendWithPadding
 */
@Test
public class AppendWithPadding {
    public void appendWithPadding() {
        // latin1
        assertEquals("abc001", new StringBuilder("abc").append(1, 3, '0').toString());
        assertEquals("abc  1", new StringBuilder("abc").append(1, 3, ' ').toString());
        assertEquals("abc123", new StringBuilder("abc").append(123, 3, '0').toString());
        assertEquals("abc1234", new StringBuilder("abc").append(1234, 3, '0').toString());

        // utf16
        assertEquals("\u8336001", new StringBuilder("\u8336").append(1, 3, '0').toString());
        assertEquals("\u8336  1", new StringBuilder("\u8336").append(1, 3, ' ').toString());
        assertEquals("\u8336123", new StringBuilder("\u8336").append(123, 3, ' ').toString());
        assertEquals("\u83361234", new StringBuilder("\u8336").append(1234, 3, ' ').toString());
    }
}
