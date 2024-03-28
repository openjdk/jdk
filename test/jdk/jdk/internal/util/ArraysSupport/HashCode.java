/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8311864
 * @modules java.base/jdk.internal.util
 * @run junit HashCode
 * @summary Tests for ArraysSupport.hashCode
 */

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import jdk.internal.util.ArraysSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class HashCode {

    @ParameterizedTest
    @MethodSource
    public void testHashCode(Arguments args) {
        assertEqualBehavior(
                () ->      referenceHashCode(args.array, args.offset, args.length, args.initialValue),
                () -> ArraysSupport.hashCode(args.array, args.offset, args.length, args.initialValue)
        );
    }

    // implement leveraging JUnit assertions
    private static <T> void assertEqualBehavior(Callable<? extends T> expected,
                                                Callable<? extends T> actual) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(actual);
        T expectedResult;
        try {
            expectedResult = expected.call();
        } catch (Throwable expectedException) {
            var actualException = assertThrows(expectedException.getClass(),
                    actual::call);
            System.err.println(expectedException.getClass() + ", "
                    + actualException.getClass());
            return;
        }
        T actualResult = assertDoesNotThrow(actual::call);
        assertEquals(expectedResult, actualResult);
    }

    static Stream<Arguments> testHashCode() {
        // use array initializer instead of vararg syntax because
        // the former allows trailing comma
        var array = new Arguments[]{
                new Arguments(null, 0, 0, 0),

                new Arguments(new int[]{}, 0, 0, 0),
                new Arguments(new int[]{}, 0, 0, 1),

                new Arguments(new int[]{2, 5, 7}, 0, 0, 0),
                new Arguments(new int[]{2, 5, 7}, 0, 0, 1),
                new Arguments(new int[]{2, 5, 7}, 0, 1, 0),
                new Arguments(new int[]{2, 5, 7}, 0, 1, 1),
                new Arguments(new int[]{2, 5, 7}, 1, 0, 0),
                new Arguments(new int[]{2, 5, 7}, 1, 0, 1),
                new Arguments(new int[]{2, 5, 7}, 1, 1, 0),
                new Arguments(new int[]{2, 5, 7}, 1, 1, 1),

                new Arguments(new int[]{2, 5, 7}, 0, 3, 0),

                new Arguments(new int[]{2, 5, 7}, 0, 3, 1),
                new Arguments(new int[]{2, 5, 7}, 0, 5, 5),
        };
        return Stream.of(array);
    }

    record Arguments(int[] array, int offset, int length, int initialValue) {

        // override for string representation of int[] more helpful
        // than int[].toString()
        @Override
        public String toString() {
            return "%s, offset=%s, length=%s, initialValue=%s".formatted(
                    Arrays.toString(array), offset, length, initialValue);
        }
    }

    private static int referenceHashCode(int[] a,
                                         int offset,
                                         int length,
                                         int initialValue) {
        int result = initialValue;
        for (int i = offset; i < offset + length; i++)
            result = 31 * result + a[i];
        return result;
    }
}
