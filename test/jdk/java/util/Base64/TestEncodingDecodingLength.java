/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8210583 8217969 8218265 8295153
 * @summary White-box test that effectively checks Base64.Encoder.encode and
 *          Base64.Decoder.decode behavior with large, (Integer.MAX_VALUE) sized
 *          input array/buffer. Tests the private methods "encodedOutLength" and
 *          "decodedOutLength".
 * @run junit/othervm --add-opens java.base/java.util=ALL-UNNAMED TestEncodingDecodingLength
 */

// We perform a white-box test due to the heavy memory usage that testing
// the public API would require which has shown to cause intermittent issues
public class TestEncodingDecodingLength {

    private static final int size = Integer.MAX_VALUE - 8;

    // Effectively tests the overloaded Base64.Encoder.encode() methods and
    // encodeToString() throw OOME instead of NASE with large array values.
    // All the encode methods call encodedOutLength() which is where the OOME
    // is expected to be thrown from
    @Test
    public void largeEncodeTest() throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        Base64.Encoder encoder = Base64.getEncoder();
        Method m = encoder.getClass().getDeclaredMethod(
                "encodedOutLength", int.class, boolean.class);
        m.setAccessible(true);

        // IAE case
        // When throwOOME param is false, encodedOutLength should return -1 in
        // this situation, which encode() uses to throw IAE
        assertEquals(-1, m.invoke(encoder, size, false));

        // OOME case
        try {
            m.invoke(encoder, size, true);
        } catch (InvocationTargetException ex) {
            Throwable rootEx = ex.getCause();
            assertEquals(OutOfMemoryError.class, rootEx.getClass(), "00ME should be thrown");
            assertEquals("Encoded size is too large", rootEx.getMessage());
        }
    }

    // Effectively tests the overloaded Base64.Decoder.decode() methods do not
    // throw OOME nor NASE with large array values. All the decode methods call
    // decodedOutLength(), which is where the potential overflow situation occurs.
    @Test
    public void largeDecodeTest() throws NoSuchMethodException, IllegalAccessException {
        Base64.Decoder decoder = Base64.getDecoder();
        Method m = decoder.getClass().getDeclaredMethod(
                "decodedOutLength", byte[].class, int.class, int.class);
        m.setAccessible(true);
        byte[] src = {1};
        try {
            // decodedOutLength() takes the src array, position, and limit as params.
            // The src array will be indexed at limit-1 to search for padding.
            // To avoid passing an array with Integer.MAX_VALUE memory allocated, we
            // set position param to be -size. Since the initial length
            // is calculated as limit - position. This mocks the potential overflow
            // calculation and still allows the array to be indexed without an AIOBE.
            m.invoke(decoder, src, -size + 1, 1);
        } catch (InvocationTargetException ex) {
            fail("Decode should neither throw NASE or OOME: " + ex.getCause());
        }
    }
}
