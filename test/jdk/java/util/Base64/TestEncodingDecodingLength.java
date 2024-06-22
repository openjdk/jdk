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

    // A value large enough to test the desired memory conditions in encode and decode
    private static final int LARGE_MEM_SIZE = Integer.MAX_VALUE - 8;
    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    // Effectively tests that encode(byte[] src, byte[] dst) throws an
    // IllegalArgumentException with array sized near Integer.MAX_VALUE. All the
    // encode() methods call encodedOutLength(), which is where the OOME is expected
    @Test
    public void largeEncodeIAETest() throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        Method m = getMethod(ENCODER,
                "encodedOutLength", int.class, boolean.class);
        // When throwOOME param is false, encodedOutLength should return -1 in
        // this situation, which encode() uses to throw IAE
        assertEquals(-1, m.invoke(ENCODER, LARGE_MEM_SIZE, false));
    }

    // Effectively tests that the overloaded encode() and encodeToString() methods
    // throw OutOfMemoryError with array/buffer sized near Integer.MAX_VALUE
    @Test
    public void largeEncodeOOMETest() throws IllegalAccessException, NoSuchMethodException {
        Method m = getMethod(ENCODER,
                "encodedOutLength", int.class, boolean.class);
        try {
            m.invoke(ENCODER, LARGE_MEM_SIZE, true);
        } catch (InvocationTargetException ex) {
            Throwable rootEx = ex.getCause();
            assertEquals(OutOfMemoryError.class, rootEx.getClass(),
                    "OOME should be thrown with Integer.MAX_VALUE input");
            assertEquals("Encoded size is too large", rootEx.getMessage());
        }
    }

    // Effectively tests that the overloaded decode() methods do not throw
    // OOME nor NASE with array/buffer sized near Integer.MAX_VALUE All the decode
    // methods call decodedOutLength(), which is where the previously thrown
    // OOME or NASE would occur at.
    @Test
    public void largeDecodeTest() throws IllegalAccessException, NoSuchMethodException {
        Method m = getMethod(DECODER,
                "decodedOutLength", byte[].class, int.class, int.class);
        byte[] src = {1};
        try {
            /*
             decodedOutLength() takes the src array, position, and limit as params.
             The src array will be indexed at limit-1 to search for padding.
             To avoid passing an array with Integer.MAX_VALUE memory allocated, we
             set position param to be -size. Since the initial length
             is calculated as limit - position. This mocks the potential overflow
             calculation and still allows the array to be indexed without an AIOBE.
            */
            m.invoke(DECODER, src, -LARGE_MEM_SIZE + 1, 1);
        } catch (InvocationTargetException ex) {
            // 8210583 - decode no longer throws NASE
            // 8217969 - decode no longer throws OOME
            fail("Decode threw an unexpected exception with " +
                    "Integer.MAX_VALUE sized input: " + ex.getCause());
        }
    }

    // Utility to get the private visibility method
    private static Method getMethod(Object obj, String methodName, Class<?>... params)
            throws NoSuchMethodException {
        Method m = obj.getClass().getDeclaredMethod(methodName, params);
        m.setAccessible(true);
        return m;
    }
}
