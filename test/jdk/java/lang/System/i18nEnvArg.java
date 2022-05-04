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
 * @modules jdk.charsets
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run main i18nEnvArg
 */

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
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
     * Checks OS is Linux and OS has ja_JP.eucjp locale or not.
     * Sets EUC_JP's environment variable and argunments against ProcessBuilder
     */
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        if (args.length == 0) {
            var cmds = List.of(
                "--add-modules=" + System.getProperty("test.modules"),
                "-classpath",
                System.getProperty("test.class.path"),
                "-Dtest.jdk=" + System.getProperty("test.jdk"),
                "-Dtest.class.path=" + System.getProperty("test.class.path"),
                "-Dtest.modules=" + System.getProperty("test.modules"),
                "i18nEnvArg",
                "Start");
            pb = ProcessTools.createTestJvm(cmds);
            Map<String, String> environ = pb.environment();
            environ.clear();
            environ.put("LANG", "ja_JP.eucjp");
        } else {
            String jnuEncoding = System.getProperty("sun.jnu.encoding");
            Charset dcs = jnuEncoding != null
                ? Charset.forName(jnuEncoding)
                : Charset.defaultCharset();
            Charset cs = Charset.forName("x-euc-jp-linux");
            if (!dcs.equals(cs)) {
                return;
            }
            var cmds = List.of(
                "--add-modules=" + System.getProperty("test.modules"),
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-classpath",
                System.getProperty("test.class.path"),
                "i18nEnvArg$Verify",
                EUC_JP_TEXT);
            pb = ProcessTools.createTestJvm(cmds);
            Map<String, String> environ = pb.environment();
            environ.clear();
            environ.put("LANG", "ja_JP.eucjp");
            environ.put(EUC_JP_TEXT, EUC_JP_TEXT);
        }
        ProcessTools.executeProcess(pb)
            .outputTo(System.out)
            .errorTo(System.err)
            .shouldHaveExitValue(0);
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            if (!EUC_JP_TEXT.equals(s)) {
                ps.println("getenv() returns unexpected data");
            }
            if (!EUC_JP_TEXT.equals(args[0])) {
                ps.print("Unexpected argument was received: ");
                for(char ch : EUC_JP_TEXT.toCharArray()) {
                   ps.printf("\\u%04X", (int)ch);
                }
                ps.print("<->");
                for(char ch : args[0].toCharArray()) {
                   ps.printf("\\u%04X", (int)ch);
                }
                ps.println();
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
                    ps.println("Unexpected environment variables: " +
                        hf.formatHex(envb));
                }
            }
            byte[] err = baos.toByteArray();
            if (err.length > 0) {
                System.err.write(err);
                System.exit(1);
            }
        }
    }
}
