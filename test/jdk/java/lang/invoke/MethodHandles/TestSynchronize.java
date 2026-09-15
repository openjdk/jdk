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
 * @run junit/othervm -Xverify:all TestSynchronize
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TestSynchronize {

    static final MethodHandle MH_payload;
    static final MethodHandle MH_payloadExceptional;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_payload = lookup.findStatic(TestSynchronize.class, "payload",
                    MethodType.methodType(void.class, Object.class));
            MH_payloadExceptional = lookup.findStatic(TestSynchronize.class, "payloadExceptional",
                    MethodType.methodType(void.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    public void testSynchronize() throws Throwable {
        MethodHandle mh = MethodHandles.synchronize(MH_payload);

        Object lock = new Object();
        assertFalse(Thread.holdsLock(lock));
        mh.invokeExact(lock, lock);
        assertFalse(Thread.holdsLock(lock));
    }

    private static void payload(Object lock) {
        assertTrue(Thread.holdsLock(lock));
    }

    private static final String EXCEPTION_MESSAGE = "Testing lock release";

    @Test
    public void testSynchronizeException() {
        MethodHandle mh = MethodHandles.synchronize(MH_payloadExceptional);

        Object lock = new Object();
        assertFalse(Thread.holdsLock(lock));
        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            /* (void) */ mh.invokeExact(lock, lock);
        });
        assertEquals(EXCEPTION_MESSAGE, e.getMessage());
        assertFalse(Thread.holdsLock(lock));
    }

    private static void payloadExceptional(Object lock) {
        assertTrue(Thread.holdsLock(lock));
        throw new RuntimeException(EXCEPTION_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("passThroughCases")
    public void testArgumentPassThrough(Class<?> type, Object testValue) throws Throwable {
        MethodHandle mh = MethodHandles.identity(type);
        mh = MethodHandles.synchronize(mh);
        mh = mh.asType(mh.type().generic());

        Object lock = new Object();
        assertFalse(Thread.holdsLock(lock));
        Object result = mh.invokeExact(lock, testValue);
        assertEquals(testValue, result);
        assertFalse(Thread.holdsLock(lock));
    }

    public static Stream<Object> passThroughCases() {
        return Stream.of(
            arguments(boolean.class, true),
            arguments(byte.class, (byte) 123),
            arguments(short.class, (short) 123),
            arguments(char.class, 'a'),
            arguments(int.class, 123),
            arguments(long.class, 123L),
            arguments(float.class, 12.3F),
            arguments(double.class, 12.3D),
            arguments(Object.class, new Object()),
            arguments(String.class, "asdf")
        );
    }
}
