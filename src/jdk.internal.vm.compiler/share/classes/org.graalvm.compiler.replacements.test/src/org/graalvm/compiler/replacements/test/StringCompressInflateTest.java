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


package org.graalvm.compiler.replacements.test;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.UnsupportedEncodingException;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.replacements.amd64.AMD64StringLatin1InflateNode;
import org.graalvm.compiler.replacements.amd64.AMD64StringLatin1Substitutions;
import org.graalvm.compiler.replacements.amd64.AMD64StringUTF16CompressNode;
import org.graalvm.compiler.replacements.amd64.AMD64StringUTF16Substitutions;
import org.graalvm.compiler.test.AddExports;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test intrinsic/node substitutions for (innate) methods StringLatin1.inflate and
 * StringUTF16.compress provided by {@link AMD64StringLatin1Substitutions} and
 * {@link AMD64StringUTF16Substitutions}.
 */
@AddExports({"java.base/java.lang"})
public final class StringCompressInflateTest extends MethodSubstitutionTest {

    static final int N = 1000;

    @Before
    public void checkAMD64() {
        assumeFalse(Java8OrEarlier);
        // Test case is (currently) AMD64 only.
        assumeTrue(getTarget().arch instanceof AMD64);
    }

    @Test
    public void testStringLatin1Inflate() throws ClassNotFoundException, UnsupportedEncodingException {
        Class<?> javaclass = Class.forName("java.lang.StringLatin1");
        Class<?> testclass = AMD64StringLatin1InflateNode.class;

        TestMethods tms = new TestMethods("testInflate", javaclass, AMD64StringLatin1InflateNode.class, "inflate",
                        byte[].class, int.class, char[].class, int.class, int.class);

        tms.testSubstitution(testclass);

        for (int i = 0; i < N; i++) {
            byte[] src = fillLatinBytes(new byte[i2sz(i)]);
            char[] dst = new char[i2sz(i)];

            // Invoke void StringLatin1.inflate(byte[], 0, char[], 0, length)
            Object nil = tms.invokeJava(src, 0, dst, 0, i2sz(i));

            assert nil == null;

            // Perform a sanity check:
            for (int j = 0; j < i2sz(i); j++) {
                assert (dst[j] & 0xff00) == 0;
                assert (32 <= dst[j] && dst[j] <= 126) || (160 <= dst[j] && dst[j] <= 255);
                assert ((byte) dst[j] == src[j]);
            }

            String str = new String(src, 0, src.length, "ISO8859_1");

            for (int j = 0; j < src.length; j++) {
                assert ((char) src[j] & 0xff) == str.charAt(j);
            }

            // Invoke char[] testInflate(String)
            char[] inflate1 = (char[]) tms.invokeTest(str);

            // Another sanity check:
            for (int j = 0; j < i2sz(i); j++) {
                assert (inflate1[j] & 0xff00) == 0;
                assert (32 <= inflate1[j] && inflate1[j] <= 126) || (160 <= inflate1[j] && inflate1[j] <= 255);
            }

            assertDeepEquals(dst, inflate1);

            // Invoke char[] testInflate(String) through code handle.
            char[] inflate2 = (char[]) tms.invokeCode(str);
            assertDeepEquals(dst, inflate2);
        }
    }

    @Test
    public void testStringLatin1InflateByteByte() throws ClassNotFoundException {
        Class<?> javaclass = Class.forName("java.lang.StringLatin1");

        ResolvedJavaMethod caller = getResolvedJavaMethod(javaclass, "inflate", byte[].class, int.class, byte[].class, int.class, int.class);
        StructuredGraph graph = getReplacements().getIntrinsicGraph(caller, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
        assertInGraph(graph, AMD64StringLatin1InflateNode.class);

        InstalledCode code = getCode(caller, graph);

        for (int dstOffset = 0; dstOffset < 2; dstOffset++) {
            for (int srcOffset = 0; srcOffset < 2; srcOffset++) {
                for (int i = 0; i < N; i++) {
                    int length = i2sz(i);
                    byte[] src = fillLatinBytes(new byte[length]);
                    int resultLength = length * 2;
                    byte[] dst = new byte[resultLength];
                    int copiedLength = Math.max(0, length - Math.max(dstOffset, srcOffset));
                    int dstDelta = Math.min(dstOffset, copiedLength);
                    int srcDelta = Math.min(srcOffset, copiedLength);
                    invokeSafe(caller, null, src, srcDelta, dst, dstDelta, copiedLength);

                    // Perform a sanity check:
                    for (int j = 0; j < copiedLength; j++) {
                        assert (dst[j * 2 + 1 + dstDelta * 2]) == 0;
                        int c = dst[j * 2 + dstDelta * 2] & 0xFF;
                        assert (32 <= c && c <= 126) || (160 <= c && c <= 255);
                        assert (c == (src[j + srcDelta] & 0xFF));
                    }

                    byte[] dst2 = new byte[resultLength];
                    executeVarargsSafe(code, src, srcDelta, dst2, dstDelta, copiedLength);
                    assertDeepEquals(dst, dst2);
                }
            }
        }
    }

    @Test
    public void testStringLatin1InflateByteChar() throws ClassNotFoundException {
        Class<?> javaclass = Class.forName("java.lang.StringLatin1");

        ResolvedJavaMethod caller = getResolvedJavaMethod(javaclass, "inflate", byte[].class, int.class, char[].class, int.class, int.class);
        StructuredGraph graph = getReplacements().getIntrinsicGraph(caller, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
        assertInGraph(graph, AMD64StringLatin1InflateNode.class);

        InstalledCode code = getCode(caller, graph);

        for (int dstOffset = 0; dstOffset < 2; dstOffset++) {
            for (int srcOffset = 0; srcOffset < 2; srcOffset++) {
                for (int i = 0; i < N; i++) {
                    int length = i2sz(i);
                    byte[] src = fillLatinBytes(new byte[length]);
                    char[] dst = new char[length];
                    int copiedLength = Math.max(0, length - Math.max(dstOffset, srcOffset));
                    int dstDelta = Math.min(dstOffset, copiedLength);
                    int srcDelta = Math.min(srcOffset, copiedLength);
                    invokeSafe(caller, null, src, srcDelta, dst, dstDelta, copiedLength);

                    // Perform a sanity check:
                    for (int j = 0; j < copiedLength; j++) {
                        int c = dst[j + dstDelta] & 0xFF;
                        assert (32 <= c && c <= 126) || (160 <= c && c <= 255);
                        assert (c == (src[j + srcDelta] & 0xFF));
                    }

                    char[] dst2 = new char[length];
                    executeVarargsSafe(code, src, srcDelta, dst2, dstDelta, copiedLength);
                    assertDeepEquals(dst, dst2);
                }
            }
        }
    }

    @Test
    public void testStringUTF16Compress() throws ClassNotFoundException, UnsupportedEncodingException {
        Class<?> javaclass = Class.forName("java.lang.StringUTF16");
        Class<?> testclass = AMD64StringUTF16CompressNode.class;
        TestMethods tms = new TestMethods("testCompress", javaclass, AMD64StringUTF16CompressNode.class, "compress",
                        char[].class, int.class, byte[].class, int.class, int.class);
        tms.testSubstitution(testclass);

        for (int i = 0; i < N; i++) {
            char[] src = fillLatinChars(new char[i2sz(i)]);
            byte[] dst = new byte[i2sz(i)];

            // Invoke int StringUTF16.compress(char[], 0, byte[], 0, length)
            Object len = tms.invokeJava(src, 0, dst, 0, i2sz(i));

            assert (int) len == i2sz(i);

            // Invoke String testCompress(char[])
            String str1 = (String) tms.invokeTest(src);

            assertDeepEquals(dst, str1.getBytes("ISO8859_1"));

            // Invoke String testCompress(char[]) through code handle.
            String str2 = (String) tms.invokeCode(src);

            assertDeepEquals(dst, str2.getBytes("ISO8859_1"));
        }
    }

    @Test
    public void testStringUTF16CompressByteByte() throws ClassNotFoundException {
        Class<?> javaclass = Class.forName("java.lang.StringUTF16");

        ResolvedJavaMethod caller = getResolvedJavaMethod(javaclass, "compress", byte[].class, int.class, byte[].class, int.class, int.class);
        StructuredGraph graph = getReplacements().getIntrinsicGraph(caller, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
        assertInGraph(graph, AMD64StringUTF16CompressNode.class);

        InstalledCode code = getCode(caller, graph);

        for (int dstOffset = 0; dstOffset < 2; dstOffset++) {
            for (int srcOffset = 0; srcOffset < 2; srcOffset++) {
                for (int i = 0; i < N; i++) {
                    int length = i2sz(i);
                    byte[] src = fillLatinChars(new byte[length * 2]);
                    byte[] dst = new byte[length];
                    int copiedLength = Math.max(0, length - Math.max(dstOffset, srcOffset));
                    int dstDelta = Math.min(dstOffset, copiedLength);
                    int srcDelta = Math.min(srcOffset, copiedLength);
                    invokeSafe(caller, null, src, srcDelta, dst, dstDelta, copiedLength);

                    // Perform a sanity check:
                    for (int j = 0; j < copiedLength; j++) {
                        int c = dst[j + dstDelta] & 0xFF;
                        assert (32 <= c && c <= 126) || (160 <= c && c <= 255);
                        assert (c == (src[(j + srcDelta) * 2] & 0xFF));
                    }

                    byte[] dst2 = new byte[length];
                    executeVarargsSafe(code, src, srcDelta, dst2, dstDelta, copiedLength);
                    assertDeepEquals(dst, dst2);
                }
            }
        }
    }

    @Test
    public void testStringUTF16CompressCharByte() throws ClassNotFoundException {
        Class<?> javaclass = Class.forName("java.lang.StringUTF16");

        ResolvedJavaMethod caller = getResolvedJavaMethod(javaclass, "compress", char[].class, int.class, byte[].class, int.class, int.class);
        StructuredGraph graph = getReplacements().getIntrinsicGraph(caller, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
        assertInGraph(graph, AMD64StringUTF16CompressNode.class);

        InstalledCode code = getCode(caller, graph);

        for (int dstOffset = 0; dstOffset < 2; dstOffset++) {
            for (int srcOffset = 0; srcOffset < 2; srcOffset++) {
                for (int i = 0; i < N; i++) {
                    int length = i2sz(i);
                    char[] src = fillLatinChars(new char[length]);
                    byte[] dst = new byte[length];
                    int copiedLength = Math.max(0, length - Math.max(dstOffset, srcOffset));
                    int dstDelta = Math.min(dstOffset, copiedLength);
                    int srcDelta = Math.min(srcOffset, copiedLength);
                    invokeSafe(caller, null, src, srcDelta, dst, dstDelta, copiedLength);

                    // Perform a sanity check:
                    for (int j = 0; j < copiedLength; j++) {
                        int c = dst[j + dstDelta] & 0xFF;
                        assert (32 <= c && c <= 126) || (160 <= c && c <= 255);
                        assert (c == (src[j + srcDelta] & 0xFF));
                    }

                    byte[] dst2 = new byte[length];
                    executeVarargsSafe(code, src, srcDelta, dst2, dstDelta, copiedLength);
                    assertDeepEquals(dst, dst2);
                }
            }
        }
    }

    @SuppressWarnings("all")
    public static String testCompress(char[] a) {
        return new String(a);
    }

    @SuppressWarnings("all")
    public static char[] testInflate(String a) {
        return a.toCharArray();
    }

    private class TestMethods {

        TestMethods(String testmname, Class<?> javaclass, Class<?> intrinsicClass, String javamname, Class<?>... params) {
            javamethod = getResolvedJavaMethod(javaclass, javamname, params);
            testmethod = getResolvedJavaMethod(testmname);
            testgraph = getReplacements().getIntrinsicGraph(javamethod, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext());
            assertInGraph(testgraph, intrinsicClass);

            assert javamethod != null;
            assert testmethod != null;

            // Force the test method to be compiled.
            testcode = getCode(testmethod);

            assert testcode != null;
        }

        StructuredGraph replacementGraph() {
            return getReplacements().getSubstitution(javamethod, -1, false, null);
        }

        StructuredGraph testMethodGraph() {
            return testgraph;
        }

        void testSubstitution(Class<?> intrinsicclass) {
            // Check if the resulting graph contains the expected node.
            if (replacementGraph() == null) {
                assertInGraph(testMethodGraph(), intrinsicclass);
            }
        }

        Object invokeJava(Object... args) {
            return invokeSafe(javamethod, null, args);
        }

        Object invokeTest(Object... args) {
            return invokeSafe(testmethod, null, args);
        }

        Object invokeCode(Object... args) {
            return executeVarargsSafe(testcode, args);
        }

        // Private data section:
        private ResolvedJavaMethod javamethod;
        private ResolvedJavaMethod testmethod;
        private StructuredGraph testgraph;
        private InstalledCode testcode;
    }

    private static byte[] fillLatinBytes(byte[] v) {
        for (int ch = 32, i = 0; i < v.length; i++) {
            v[i] = (byte) (ch & 0xff);
            ch = ch == 126 ? 160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static char[] fillLatinChars(char[] v) {
        for (int ch = 32, i = 0; i < v.length; i++) {
            v[i] = (char) (ch & 0xff);
            ch = ch == 126 ? 160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static byte[] fillLatinChars(byte[] v) {
        for (int ch = 32, i = 0; i < v.length; i += 2) {
            v[i] = (byte) (ch & 0xff);
            ch = ch == 126 ? 160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static int i2sz(int i) {
        return i * 3;
    }
}
