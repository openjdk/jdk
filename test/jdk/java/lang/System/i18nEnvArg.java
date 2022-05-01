/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8285517
 * @summary System.getenv() and argument don't return locale dependent data by JEP400
 * @requires (os.family == "linux")
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run main i18nEnvArg
 */

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import jdk.test.lib.process.ProcessTools;

public class i18nEnvArg {
    final static String EUC_JP_TEXT = "\u6F22\u5B57";

    /*
     * Generates test process which runs with ja_JP.eucjp locale
     */
    public static void main(String[] args) throws Exception {
        var cmds = List.of("i18nEnvArg$Start");
        var pb = ProcessTools.createTestJvm(cmds);
        Map<String, String> environ = pb.environment();
        environ.clear();
        environ.put("LANG", "ja_JP.eucjp");
        ProcessTools.executeProcess(pb)
            .outputTo(System.out)
            .errorTo(System.err)
            .shouldHaveExitValue(0);
    }

    public static class Start {
        /*
         * Checks OS is Linux and OS has ja_JP.eucjp locale or not.
         * Sets EUC_JP's environment variable and argunments against ProcessBuilder
         */
        public static void main(String[] args) throws Exception {
            String nativeEncoding = System.getProperty("native.encoding");
            Charset dcs = nativeEncoding == null ?
                Charset.defaultCharset() :
                Charset.forName(nativeEncoding);
            Charset cs = Charset.forName("x-euc-jp-linux");
            if (!dcs.equals(cs)) {
                return;
            }
            String javeExe = System.getProperty("java.home") +
                File.separator +
                "bin" +
                File.separator +
                "java";
            ProcessBuilder pb = new ProcessBuilder(javeExe,
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-classpath",
                System.getProperty("java.class.path"),
                "i18nEnvArg$Verify",
                EUC_JP_TEXT);
            pb.redirectErrorStream(true);
            Map<String, String> environ = pb.environment();
            environ.clear();
            environ.put("LANG", "ja_JP.eucjp");
            environ.put(EUC_JP_TEXT, EUC_JP_TEXT);
            Process p = pb.start();
            InputStream is = p.getInputStream();
            byte[] ba = is.readAllBytes();
            int rc = p.waitFor();
            if (ba.length > 0)
                throw new Exception(new String(ba));
        }
    }

    public static class Verify {
        private final static int maxSize = 4096;
        /*
         * Verify environment variable and argument are encoded by Linux's eucjp or not
         */
        public static void main(String[] args) throws Exception {
            Charset cs = Charset.forName("x-euc-jp-linux");
            byte[] euc = (EUC_JP_TEXT + "=" + EUC_JP_TEXT).getBytes(cs);
            byte[] eucjp = "LANG=ja_JP.eucjp".getBytes(cs);
            String s = System.getenv(EUC_JP_TEXT);
            if (!EUC_JP_TEXT.equals(s)) {
                System.out.println("getenv() returns unexpected data");
            }
            if (!EUC_JP_TEXT.equals(args[0])) {
                System.out.print("Unexpected argument was received: ");
                for(char ch : EUC_JP_TEXT.toCharArray()) {
                   System.out.printf("\\u%04X", (int)ch);
                }
                System.out.print("<->");
                for(char ch : args[0].toCharArray()) {
                   System.out.printf("\\u%04X", (int)ch);
                }
                System.out.println();
            }
            Class<?> cls = Class.forName("java.lang.ProcessEnvironment");
            Method environ_mid = cls.getDeclaredMethod("environ");
            environ_mid.setAccessible(true);
            byte[][] environ = (byte[][]) environ_mid.invoke(null,
                (Object[])null);
            HexFormat hf = HexFormat.of().withUpperCase().withPrefix("\\x");
            byte[] ba = new byte[maxSize];
            for(int i = 0; i < environ.length; i += 2) {
                ByteBuffer bb = ByteBuffer.wrap(ba);
                bb.put(environ[i]);
                bb.put((byte)'=');
                bb.put(environ[i+1]);
                byte[] envb = Arrays.copyOf(ba, bb.position());
                if (Arrays.equals(eucjp, envb)) continue;
                if (!Arrays.equals(euc, envb)) {
                    System.out.println("Unexpected environment variables: " +
                        hf.formatHex(envb));
                }
            }
        }
    }
}
