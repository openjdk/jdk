/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test compress expand methods
 * @key randomness
 * @run testng CompressExpandTest
 */

public final class CompressExpandTest extends AbstractCompressExpandTest {
    @Override
    int actualCompress(int i, int mask) {
        return Integer.compress(i, mask);
    }

    @Override
    int actualExpand(int i, int mask) {
        return Integer.expand(i, mask);
    }

    @Override
    int expectedCompress(int i, int mask) {
        return testCompress(i, mask);
    }

    @Override
    int expectedExpand(int i, int mask) {
        return testExpand(i, mask);
    }


    @Override
    long actualCompress(long i, long mask) {
        return Long.compress(i, mask);
    }

    @Override
    long actualExpand(long i, long mask) {
        return Long.expand(i, mask);
    }

    @Override
    long expectedCompress(long i, long mask) {
        return testCompress(i, mask);
    }

    @Override
    long expectedExpand(long i, long mask) {
        return testExpand(i, mask);
    }
}
