/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
 * @author Eric Wang <yiming.wang@oracle.com>
 * @summary tests java.util.Base64
 * @library /test/lib /
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 *
 * @run main/othervm/timeout=600 -Xbatch -DcheckOutput=true
 *       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.intrinsics.base64.TestBase64
 */

package compiler.intrinsics.base64;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Objects;
import java.util.Random;

import compiler.whitebox.CompilerWhiteBoxTest;
import sun.hotspot.code.Compiler;
import jtreg.SkippedException;

public class TestBase64 {
    static boolean checkOutput = Boolean.getBoolean("checkOutput");

    public static void main(String[] args) throws Exception {
        if (!Compiler.isIntrinsicAvailable(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION, "java.util.Base64$Encoder", "encodeBlock", byte[].class, int.class, int.class, byte[].class, int.class, boolean.class)) {
            throw new SkippedException("Base64 intrinsic is not available");
        }
        int iters = (args.length > 0 ? Integer.valueOf(args[0]) : 100000);
        System.out.println(iters + " iterations");

        test0(Base64Type.BASIC, Base64.getEncoder(), Base64.getDecoder(),"plain.txt", "baseEncode.txt", iters);
        test0(Base64Type.URLSAFE, Base64.getUrlEncoder(), Base64.getUrlDecoder(),"plain.txt", "urlEncode.txt", iters);
        test0(Base64Type.MIME, Base64.getMimeEncoder(), Base64.getMimeDecoder(),"plain.txt", "mimeEncode.txt", iters);
    }

    public static void test0(Base64Type type, Encoder encoder, Decoder decoder, String srcFile, String encodedFile, int numIterations) throws Exception {

        String[] srcLns = Files.readAllLines(Paths.get(SRCDIR, srcFile), DEF_CHARSET)
                               .toArray(new String[0]);
        String[] encodedLns = Files.readAllLines(Paths.get(SRCDIR, encodedFile), DEF_CHARSET)
                                   .toArray(new String[0]);

        for (int i = 0; i < numIterations; i++) {
            int lns = 0;
            for (String srcStr : srcLns) {
                String encodedStr = null;
                if (type != Base64Type.MIME) {
                    encodedStr = encodedLns[lns++];
                } else {
                    while (lns < encodedLns.length) {
                        String s = encodedLns[lns++];
                        if (s.length() == 0)
                            break;
                        if (encodedStr != null) {
                            encodedStr += DEFAULT_CRLF + s;
                        } else {
                            encodedStr = s;
                        }
                    }
                    if (encodedStr == null && srcStr.length() == 0) {
                        encodedStr = "";
                    }
                }

                byte[] srcArr = srcStr.getBytes(DEF_CHARSET);
                byte[] encodedArr = encodedStr.getBytes(DEF_CHARSET);

                ByteBuffer srcBuf = ByteBuffer.wrap(srcArr);
                ByteBuffer encodedBuf = ByteBuffer.wrap(encodedArr);
                byte[] resArr = new byte[encodedArr.length];

                // test int encode(byte[], byte[])
                int len = encoder.encode(srcArr, resArr);
                assertEqual(len, encodedArr.length);
                assertEqual(resArr, encodedArr);

                // test byte[] encode(byte[])
                resArr = encoder.encode(srcArr);
                assertEqual(resArr, encodedArr);

                // test ByteBuffer encode(ByteBuffer)
                int limit = srcBuf.limit();
                ByteBuffer resBuf = encoder.encode(srcBuf);
                assertEqual(srcBuf.position(), limit);
                assertEqual(srcBuf.limit(), limit);
                assertEqual(resBuf, encodedBuf);
                srcBuf.rewind(); // reset for next test

                // test String encodeToString(byte[])
                String resEncodeStr = encoder.encodeToString(srcArr);
                assertEqual(resEncodeStr, encodedStr);

                // test int decode(byte[], byte[])
                resArr = new byte[srcArr.length];
                len = decoder.decode(encodedArr, resArr);
                assertEqual(len, srcArr.length);
                assertEqual(resArr, srcArr);

                // test byte[] decode(byte[])
                resArr = decoder.decode(encodedArr);
                assertEqual(resArr, srcArr);

                // test ByteBuffer decode(ByteBuffer)
                limit = encodedBuf.limit();
                resBuf = decoder.decode(encodedBuf);
                assertEqual(encodedBuf.position(), limit);
                assertEqual(encodedBuf.limit(), limit);
                assertEqual(resBuf, srcBuf);
                encodedBuf.rewind(); // reset for next test

                // test byte[] decode(String)
                resArr = decoder.decode(encodedStr);
                assertEqual(resArr, srcArr);

            }
        }
    }

    // helper
    enum Base64Type {
        BASIC, URLSAFE, MIME
    }

    private static final String SRCDIR = System.getProperty("test.src", "compiler/intrinsics/base64/");
    private static final Charset DEF_CHARSET = StandardCharsets.US_ASCII;
    private static final String DEF_EXCEPTION_MSG =
        "Assertion failed! The result is not same as expected\n";
    private static final String DEFAULT_CRLF = "\r\n";

    private static void assertEqual(Object result, Object expect) {
        if (checkOutput) {
            if (!Objects.deepEquals(result, expect)) {
                String resultStr = result.toString();
                String expectStr = expect.toString();
                if (result instanceof byte[]) {
                    resultStr = new String((byte[]) result, DEF_CHARSET);
                }
                if (expect instanceof byte[]) {
                    expectStr = new String((byte[]) expect, DEF_CHARSET);
                }
                throw new RuntimeException(DEF_EXCEPTION_MSG +
                    " result: " + resultStr + " expected: " + expectStr);
            }
        }
    }
}
