/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires sun.arch.data.model == "64"
 * @bug 8292851
 * @run testng/othervm -Xmx4G TestLargeSegmentCopy
 */

import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class TestLargeSegmentCopy {

    @Test
    public void testLargeSegmentCopy() {
        // Make sure the byte size is bigger than Integer.MAX_VALUE
        final int longArrayLength = Integer.MAX_VALUE / Long.BYTES + 100;
        final long[] array = new long[longArrayLength];

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate((long) longArrayLength * Long.BYTES, Long.SIZE);
            // Should not throw an exception or error
            MemorySegment.copy(segment, JAVA_LONG, 0, array, 0, longArrayLength);
            // Should not throw an exception or error
            MemorySegment.copy(array,0, segment, JAVA_LONG, 0, longArrayLength);
        }

    }

}
