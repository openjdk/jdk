/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.testng.annotations.Test;
import static org.testng.Assert.*;
/*
 * @test
 * @bug     8355636
 * @library /test/lib
 * @run testng PrintStackTrace
 * @summary Verify PrintStackTrace results
 */

public class PrintStackTrace {

    private static void fn() {
        throw new RuntimeException();
    }

    private static void f9() {
        fn();
    }

    private static void f8() {
        f9();
    }

    private static void f7() {
        f8();
    }

    private static void f6() {
        f7();
    }

    private static void f5() {
        f6();
    }

    private static void f4() {
        f5();
    }

    private static void f3() {
        f4();
    }

    private static void f2() {
        f3();
    }

    private static void f1() {
        f2();
    }

    private static void f0() {
        f1();
    }


    private static void yn() {
        try {
            f0();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void y9() {
        yn();
    }

    private static void y8() {
        y9();
    }

    private static void y7() {
        y8();
    }

    private static void y6() {
        y7();
    }

    private static void y5() {
        y6();
    }

    private static void y4() {
        y5();
    }

    private static void y3() {
        y4();
    }

    private static void y2() {
        y3();
    }

    private static void y1() {
        y2();
    }

    private static void y0() {
        y1();
    }

    private static void xn() {
        try {
            y0();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void x9() {
        xn();
    }

    private static void x8() {
        x9();
    }

    private static void x7() {
        x8();
    }

    private static void x6() {
        x7();
    }

    private static void x5() {
        x6();
    }

    private static void x4() {
        x5();
    }

    private static void x3() {
        x4();
    }

    private static void x2() {
        x3();
    }

    private static void x1() {
        x2();
    }

    private static void x0() {
        x1();
    }

    public static void main(String[] args) {
        Exception error = null;
        try {
            x0();
        } catch (Exception e1) {
            error = e1;
        }
        var byteout = new ByteArrayOutputStream();
        Charset charset = StandardCharsets.UTF_8;
        PrintStream printStream = new PrintStream(byteout, true, charset);
        error.printStackTrace(printStream);

        String expect =
                """
                        java.lang.RuntimeException: java.lang.RuntimeException: java.lang.RuntimeException
                        	at PrintStackTrace.xn(PrintStackTrace.java:139)
                        	at PrintStackTrace.x9(PrintStackTrace.java:144)
                        	at PrintStackTrace.x8(PrintStackTrace.java:148)
                        	at PrintStackTrace.x7(PrintStackTrace.java:152)
                        	at PrintStackTrace.x6(PrintStackTrace.java:156)
                        	at PrintStackTrace.x5(PrintStackTrace.java:160)
                        	at PrintStackTrace.x4(PrintStackTrace.java:164)
                        	at PrintStackTrace.x3(PrintStackTrace.java:168)
                        	at PrintStackTrace.x2(PrintStackTrace.java:172)
                        	at PrintStackTrace.x1(PrintStackTrace.java:176)
                        	at PrintStackTrace.x0(PrintStackTrace.java:180)
                        	at PrintStackTrace.main(PrintStackTrace.java:186)
                        Caused by: java.lang.RuntimeException: java.lang.RuntimeException
                        	at PrintStackTrace.yn(PrintStackTrace.java:91)
                        	at PrintStackTrace.y9(PrintStackTrace.java:96)
                        	at PrintStackTrace.y8(PrintStackTrace.java:100)
                        	at PrintStackTrace.y7(PrintStackTrace.java:104)
                        	at PrintStackTrace.y6(PrintStackTrace.java:108)
                        	at PrintStackTrace.y5(PrintStackTrace.java:112)
                        	at PrintStackTrace.y4(PrintStackTrace.java:116)
                        	at PrintStackTrace.y3(PrintStackTrace.java:120)
                        	at PrintStackTrace.y2(PrintStackTrace.java:124)
                        	at PrintStackTrace.y1(PrintStackTrace.java:128)
                        	at PrintStackTrace.y0(PrintStackTrace.java:132)
                        	at PrintStackTrace.xn(PrintStackTrace.java:137)
                        	... 11 more
                        Caused by: java.lang.RuntimeException
                        	at PrintStackTrace.fn(PrintStackTrace.java:43)
                        	at PrintStackTrace.f9(PrintStackTrace.java:47)
                        	at PrintStackTrace.f8(PrintStackTrace.java:51)
                        	at PrintStackTrace.f7(PrintStackTrace.java:55)
                        	at PrintStackTrace.f6(PrintStackTrace.java:59)
                        	at PrintStackTrace.f5(PrintStackTrace.java:63)
                        	at PrintStackTrace.f4(PrintStackTrace.java:67)
                        	at PrintStackTrace.f3(PrintStackTrace.java:71)
                        	at PrintStackTrace.f2(PrintStackTrace.java:75)
                        	at PrintStackTrace.f1(PrintStackTrace.java:79)
                        	at PrintStackTrace.f0(PrintStackTrace.java:83)
                        	at PrintStackTrace.yn(PrintStackTrace.java:89)
                        	... 22 more
                        """;
        String s = byteout.toString();
        if (!expect.equals(s)) {
            System.err.println(s);
            throw new RuntimeException("stackTrace error");
        }
    }

    /**
     * Execute "java" with the given arguments, returning the exit code.
     */
    @Test
    public void exec() throws Exception {
        int exitValue = jdk.test.lib.process.ProcessTools.executeTestJava("PrintStackTrace")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();
        assertTrue(exitValue == 0);
    }
}
