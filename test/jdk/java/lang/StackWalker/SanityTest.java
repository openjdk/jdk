/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8140450 8268829
 * @summary Sanity test for exception cases
 * @run junit SanityTest
 */

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;
import static java.lang.StackWalker.Option.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class SanityTest {
    @Test
    public void testNPE() {
        assertThrows(NullPointerException.class, () ->
                StackWalker.getInstance((Set<StackWalker.Option>) null));
        assertThrows(NullPointerException.class, () ->
                StackWalker.getInstance((StackWalker.Option) null));
    }

    private static Stream<StackWalker> noRetainClassRef() {
        return Stream.of(StackWalker.getInstance(), StackWalker.getInstance(DROP_METHOD_INFO));
    }

    @ParameterizedTest
    @MethodSource("noRetainClassRef")
    public void testUOE(StackWalker sw) {
        assertThrows(UnsupportedOperationException.class, () -> sw.getCallerClass());
    }

    @Test
    public void testInvalidEstimateDepth() {
        assertThrows(IllegalArgumentException.class, () ->
                StackWalker.getInstance(Collections.emptySet(), 0));
    }

    @Test
    public void testNullFunction() {
        assertThrows(NullPointerException.class, () ->
                StackWalker.getInstance().walk(null));
    }

    @Test
    public void testNullConsumer() {
        assertThrows(NullPointerException.class, () ->
                StackWalker.getInstance().forEach(null));
    }

    @ParameterizedTest
    @MethodSource("noRetainClassRef")
    public void testUOEFromGetDeclaringClass(StackWalker sw) {
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getDeclaringClass));
    }

    @ParameterizedTest
    @MethodSource("noRetainClassRef")
    public void testUOEFromGetMethodType(StackWalker sw) {
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getMethodType));
    }

    private static Stream<StackWalker> noMethodInfo() {
        return Stream.of(StackWalker.getInstance(DROP_METHOD_INFO),
                         StackWalker.getInstance(Set.of(DROP_METHOD_INFO, RETAIN_CLASS_REFERENCE)));
    }

    @ParameterizedTest
    @MethodSource("noMethodInfo")
    public void testNoMethodInfo(StackWalker sw) {
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getMethodName));
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getMethodType));
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getDescriptor));
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getByteCodeIndex));
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::getFileName));
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::isNativeMethod));
        assertThrows(UnsupportedOperationException.class, () ->
                sw.forEach(StackWalker.StackFrame::toStackTraceElement));
    }
}
