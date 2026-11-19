/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393882
 * @summary Tests the validation of the ICU UBiDiProps data
 * @modules java.base/jdk.internal.icu.impl:+open
 * @run junit UBiDiPropsDataValidation
 */

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import jdk.internal.icu.impl.ICUBinary;
import jdk.internal.icu.impl.UBiDiProps;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.Assert.assertThrows;

public class UBiDiPropsDataValidation {
    // Constants copied from UBiDiProps.java
    private static final String DATA_FILE_NAME =
            "/jdk/internal/icu/impl/data/icudata/ubidi.icu";
    private static final int FMT = 0x42694469;
    private static final int IX_MIRROR_LENGTH = 3;
    private static final int IX_JG_LIMIT = 5;
    private static final int IX_JG_LIMIT2 = 7;
    // end of copied constants

    private static final ByteBuffer ORIGINAL = ICUBinary.getRequiredData(DATA_FILE_NAME);
    private static final int SIZE = ORIGINAL.remaining();
    private static final int INDEXES_START;
    static {
        try {
            ICUBinary.readHeader(ORIGINAL, FMT, null);
        } catch (IOException ioe) {
            throw new ExceptionInInitializerError(ioe);
        }
        INDEXES_START = ORIGINAL.position();
    }

    @ParameterizedTest
    @ValueSource (ints = {0, IX_MIRROR_LENGTH, IX_JG_LIMIT, IX_JG_LIMIT2})
    void invalidDataSizeTest(int index) throws IOException {
        ORIGINAL.position(0);
        ByteBuffer data = ByteBuffer.allocate(SIZE)
                .order(ORIGINAL.order())
                .put(ORIGINAL)
                .flip();
        data.position(INDEXES_START);

        // Test with Integer.MAX_VALUE
        data.putInt(INDEXES_START + index * Integer.BYTES, Integer.MAX_VALUE);
        if (index == IX_JG_LIMIT || index == IX_JG_LIMIT2) {
            // set start to 0
            data.putInt(INDEXES_START + (index - 1) * Integer.BYTES, 0);
        }
        data.position(0);
        assertThrows(IOException.class, () -> readTestData(data));

        // Test with a negative size
        data.putInt(INDEXES_START + index * Integer.BYTES, -1);
        data.position(0);
        assertThrows(IOException.class, () -> readTestData(data));
    }

    private static void readTestData(ByteBuffer data) throws Throwable {
        var constructor = UBiDiProps.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var target = constructor.newInstance();
        var readData = UBiDiProps.class.getDeclaredMethod(
                "readData", ByteBuffer.class);
        readData.setAccessible(true);

        try {
            readData.invoke(target, data);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
