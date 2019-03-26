/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.jdk9.test;

import static org.junit.Assume.assumeFalse;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.hotspot.replacements.StringUTF16Substitutions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test substitutions for (innate) methods StringUTF16.toBytes and StringUTF16.getChars provided by
 * {@link StringUTF16Substitutions}.
 */
@AddExports({"java.base/java.lang"})
public final class StringUTF16ToBytesGetCharsTest extends MethodSubstitutionTest {

    private static final int N = 1000;
    private static final int N_OVERFLOW = 10;

    public StringUTF16ToBytesGetCharsTest() {
        assumeFalse(Java8OrEarlier);
    }

    @Test
    public void testStringUTF16ToBytes() throws ClassNotFoundException {
        Class<?> javaclass = Class.forName("java.lang.StringUTF16");

        ResolvedJavaMethod caller = getResolvedJavaMethod(javaclass, "toBytes", char[].class, int.class, int.class);
        StructuredGraph graph = getReplacements().getIntrinsicGraph(caller, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
        assertInGraph(graph, NewArrayNode.class);
        assertInGraph(graph, ArrayCopyCallNode.class);

        InstalledCode code = getCode(caller, graph);

        for (int srcOffset = 0; srcOffset < 2; srcOffset++) {
            for (int i = 0; i < N; i++) {
                int length = i2sz(i);
                char[] src = fillUTF16Chars(new char[length]);
                int copiedLength = Math.max(0, length - srcOffset);
                int srcDelta = Math.min(srcOffset, copiedLength);
                byte[] dst = (byte[]) invokeSafe(caller, null, src, srcDelta, copiedLength);
                assert dst.length == copiedLength * 2;
                byte[] dst2 = (byte[]) executeVarargsSafe(code, src, srcDelta, copiedLength);
                assertDeepEquals(dst, dst2);
            }
        }
        for (int srcOff = 0; srcOff < N_OVERFLOW; ++srcOff) {
            for (int len = 0; len < N_OVERFLOW; ++len) {
                char[] src = fillUTF16Chars(new char[N_OVERFLOW]);
                test(caller, null, src, srcOff, len);
            }
        }
    }

    @Test
    public void testStringUTF16getChars() throws ClassNotFoundException {
        Class<?> javaclass = Class.forName("java.lang.StringUTF16");

        ResolvedJavaMethod caller = getResolvedJavaMethod(javaclass, "getChars", byte[].class, int.class, int.class, char[].class, int.class);
        StructuredGraph graph = getReplacements().getIntrinsicGraph(caller, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
        assertInGraph(graph, ArrayCopyCallNode.class);

        InstalledCode code = getCode(caller, graph);

        for (int dstOffset = 0; dstOffset < 2; dstOffset++) {
            for (int srcOffset = 0; srcOffset < 2; srcOffset++) {
                for (int i = 0; i < N; i++) {
                    int length = i2sz(i);
                    byte[] src = fillUTF16Bytes(new byte[length * 2]);
                    char[] dst = new char[length];
                    int copiedLength = Math.max(0, length - Math.max(dstOffset, srcOffset));
                    int dstDelta = Math.min(dstOffset, copiedLength);
                    int srcDelta = Math.min(srcOffset, copiedLength);
                    invokeSafe(caller, null, src, srcDelta, srcDelta + copiedLength, dst, dstDelta);
                    char[] dst2 = new char[length];
                    executeVarargsSafe(code, src, srcDelta, srcDelta + copiedLength, dst2, dstDelta);
                    assertDeepEquals(dst, dst2);
                }
            }
        }
        for (int srcOff = 0; srcOff < N_OVERFLOW; ++srcOff) {
            for (int dstOff = 0; dstOff < N_OVERFLOW; ++dstOff) {
                for (int len = 0; len < N_OVERFLOW; ++len) {
                    byte[] src = fillUTF16Bytes(new byte[N_OVERFLOW]);
                    char[] dst = new char[N_OVERFLOW];
                    test(caller, null, src, srcOff, len, dst, dstOff);
                }
            }
        }
    }

    private static char[] fillUTF16Chars(char[] v) {
        for (int ch = 0, i = 0; i < v.length; i++, ch += 0x101) {
            v[i] = (char) ch;
        }
        return v;
    }

    private static byte[] fillUTF16Bytes(byte[] v) {
        for (int ch = 1, i = 0; i < v.length; i += 2, ch++) {
            v[i] = (byte) (ch - 1);
            v[i + 1] = (byte) ch;
        }
        return v;
    }

    private static int i2sz(int i) {
        return i * 3;
    }
}
