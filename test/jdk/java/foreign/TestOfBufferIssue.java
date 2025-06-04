/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import org.testng.annotations.*;

import java.lang.foreign.MemorySegment;
import java.nio.CharBuffer;

import static org.testng.Assert.*;

/*
 * @test
 * @bug 8294621
 * @summary test that StringCharBuffer is not accepted by MemorySegment::ofBuffer
 * @run testng TestOfBufferIssue
 */

public class TestOfBufferIssue {

    @Test
    public void ensure8294621Fixed() {
        try {
            final CharBuffer cb = CharBuffer.wrap("Hello");
            MemorySegment src2 = MemorySegment.ofBuffer(cb);
            fail("A StringCharBuffer is not allowed as an argument.");
        } catch (IllegalArgumentException iae) {
            // Ignored. Happy path
        }
    }

}
