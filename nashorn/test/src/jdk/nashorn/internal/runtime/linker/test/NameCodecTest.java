/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.nashorn.internal.runtime.linker.test;

import static org.testng.Assert.assertEquals;

import jdk.nashorn.internal.runtime.linker.NameCodec;
import org.testng.annotations.Test;

/**
 * Test for jdk.nashorn.intenal.runtime.linker.NameCodec.java. This test is
 * derived from BytecodeNameTest.java from (older) mlvm code @
 * http://hg.openjdk.java.net/mlvm/mlvm/file/tip/netbeans/meth/test/sun/invoke/util/BytecodeNameTest.java
 *
 * @bug 8141285: NameCode should pass tests from BytecodeNameTest.java
 */
public class NameCodecTest {

    static String[][] SAMPLES = {
        // mangled, source
        {"foo", "foo"},
        {"ba\\r", "ba\\r"},
        {"\\=ba\\-%z", "ba\\%z"},
        {"\\=ba\\--z", "ba\\-z"},
        {"=\\=", "=\\="},
        {"\\==\\|\\=", "=/\\="},
        {"\\|\\=", "/\\="},
        {"\\=ba\\!", "ba:"},
        {"\\|", "/"},
        {"\\", "\\"},
        {"\\\\%", "\\$"},
        {"\\\\", "\\\\"},
        {"\\=", ""}

    };

    static final String DANGEROUS_CHARS = "\\/.;:$[]<>";
    static final String REPLACEMENT_CHARS = "-|,?!%{}^_";

    static String[][] canonicalSamples() {
        final int ndc = DANGEROUS_CHARS.length();
        final String[][] res = new String[2 * ndc][];
        for (int i = 0; i < ndc; i++) {
            final char dc = DANGEROUS_CHARS.charAt(i);
            final char rc = REPLACEMENT_CHARS.charAt(i);
            if (dc == '\\') {
                res[2 * i + 0] = new String[]{"\\-%", "\\%"};
            } else {
                res[2 * i + 0] = new String[]{"\\" + rc, "" + dc};
            }
            res[2 * i + 1] = new String[]{"" + rc, "" + rc};
        }
        return res;
    }

    @Test
    public void testEncode() {
        System.out.println("testEncode");
        testEncode(SAMPLES);
        testEncode(canonicalSamples());
    }

    private void testEncode(final String[][] samples) {
        for (final String[] sample : samples) {
            final String s = sample[1];
            final String expResult = sample[0];
            final String result = NameCodec.encode(s);
            if (!result.equals(expResult)) {
                System.out.println(s + " => " + result + " != " + expResult);
            }
            assertEquals(expResult, result);
        }
    }

    @Test
    public void testDecode() {
        System.out.println("testDecode");
        testDecode(SAMPLES);
        testDecode(canonicalSamples());
    }

    private void testDecode(final String[][] samples) {
        for (final String[] sample : samples) {
            final String s = sample[0];
            final String expResult = sample[1];
            final String result = NameCodec.decode(s);
            assertEquals(expResult, result);
        }
    }
}
