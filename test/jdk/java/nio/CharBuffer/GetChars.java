/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.CharBuffer;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8343110
 * @summary Check for expected behavior of CharBuffer.getChars().
 * @run testng GetChars
 */
public class GetChars {
    private static CharBuffer CB = CharBuffer.wrap("Test");

    @Test
    public void testExactCopy() {
        var dst = new char[4];
        CB.getChars(0, 4, dst, 0);
        Assert.assertEquals(dst, new char[] {'T', 'e', 's', 't'});
    }

    @Test
    public void testPartialCopy() {
        var dst = new char[2];
        CB.getChars(1, 3, dst, 0);
        Assert.assertEquals(dst, new char[] {'e', 's'});
    }

    @Test
    public void testPositionedCopy() {
        var dst = new char[] {1, 2, 3, 4, 5, 6};
        CB.getChars(0, 4, dst, 1);
        Assert.assertEquals(dst, new char[] {1, 'T', 'e', 's', 't', 6});
    }

    @Test
    public void testSrcBeginIsNegative() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(-1, 3, new char[4], 0));
    }

    @Test
    public void testDstBeginIsNegative() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(0, 4, new char[4], -1));
    }

    @Test
    public void testSrcBeginIsGreaterThanSrcEnd() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(4, 0, new char[4], 0));
    }

    @Test
    public void testSrcEndIsGreaterThanSequenceLength() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(0, 5, new char[4], 0));
    }

    @Test
    public void testRequestedLengthIsGreaterThanDstLength() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(0, 4, new char[3], 0));
    }

    @Test
    public void testDstIsNull() {
        Assert.assertThrows(NullPointerException.class,
                () -> CB.getChars(0, 4, null, 0));
    }

}
