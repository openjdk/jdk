/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*")
 * @modules jdk.charsets
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run main i18nEnvArg
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
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
        List<String> cmds = new ArrayList<>();
        cmds.addAll(List.of(
            "--add-modules=" + System.getProperty("test.modules"),
            "-classpath",
            System.getProperty("test.class.path"),
            "-Dtest.class.path=" + System.getProperty("test.class.path"),
            "-Dtest.modules=" + System.getProperty("test.modules")));
        if (args.length == 0) {
            cmds.addAll(
                List.of("-Dtest.jdk=" + System.getProperty("test.jdk"),
                        "i18nEnvArg",
                        "Start"));
        } else {
            String jnuEncoding = System.getProperty("sun.jnu.encoding");
            Charset dcs = jnuEncoding != null
                ? Charset.forName(jnuEncoding)
                : Charset.defaultCharset();
            Charset cs = Charset.forName("x-euc-jp-linux");
            if (!dcs.equals(cs)) {
                return;
            }
            cmds.addAll(
                List.of("--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "i18nEnvArg$Verify",
                        EUC_JP_TEXT));
        }
        pb = ProcessTools.createTestJavaProcessBuilder(cmds);
        Map<String, String> environ = pb.environment();
        environ.clear();
        environ.put("LANG", "ja_JP.eucjp");
        if (args.length != 0) {
            environ.put(EUC_JP_TEXT, EUC_JP_TEXT);
        }
        ProcessTools.executeProcess(pb)
            .outputTo(System.out)
            .errorTo(System.err)
            .shouldHaveExitValue(0);
    }

    public static class Verify {

        private static String toReadable(String s) {
            if (s == null)
                return "null";
            StringBuilder sb = new StringBuilder();
            for(char ch : s.toCharArray()) {
                sb.append(String.format("\\u%04X", (int)ch));
            }
            return sb.toString();
        }

        /*
         * Verify environment variable and argument are encoded by Linux's
         * eucjp or not
         */
        public static void main(String[] args) throws Exception {
            Charset cs = Charset.forName("x-euc-jp-linux");
            byte[] euc = EUC_JP_TEXT.getBytes(cs);
            try ( ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos); ) {
                if (!EUC_JP_TEXT.equals(args[0])) {
                    ps.println("argument EUC_JP_TEXT is:");
                    ps.println("  Actual:   " + toReadable(args[0]));
                    ps.println("  Expected: " + toReadable(EUC_JP_TEXT));
                }
                String s = System.getenv(EUC_JP_TEXT);
                if (!EUC_JP_TEXT.equals(s)) {
                    ps.println("getenv(\"EUC_JP_TEXT\") is:");
                    ps.println("  Actual:   " + toReadable(s));
                    ps.println("  Expected: " + toReadable(EUC_JP_TEXT));
                } else {
                    try {
                        Class<?> ProcessEnvironment_cls =
                            Class.forName("java.lang.ProcessEnvironment");
                        Field theEnvironment_fid =
                            ProcessEnvironment_cls.getDeclaredField("theEnvironment");
                        theEnvironment_fid.setAccessible(true);
                        HashMap theEnvironment =
                            (HashMap) theEnvironment_fid.get(null);
                        Class<?> ExternalData_cls =
                            Class.forName("java.lang.ProcessEnvironment$ExternalData");
                        Method getBytes_mid =
                            ExternalData_cls.getDeclaredMethod("getBytes");
                        getBytes_mid.setAccessible(true);
                        HexFormat hf = HexFormat.of()
                            .withUpperCase()
                            .withPrefix("\\x");
                        for (Object k : theEnvironment.keySet()) {
                            if (EUC_JP_TEXT.equals(k.toString())) {
                                byte[] ba = (byte[]) getBytes_mid.invoke(k,
                                    (Object[])null);
                                if (!Arrays.equals(euc, ba)) {
                                    ps.println(
                                        "Variable EUC_JP_TEXT is encoded by:");
                                    ps.println("  Actual:   "
                                        + hf.formatHex(ba));
                                    ps.println("  Expected: "
                                        + hf.formatHex(euc));
                                }
                                ba = (byte[]) getBytes_mid.invoke(
                                    theEnvironment.get(k),
                                    (Object[])null);
                                if (!Arrays.equals(euc, ba)) {
                                    ps.println(
                                        "Value EUC_JP_TEXT is encoded by:");
                                    ps.println("  Actual:   "
                                        + hf.formatHex(ba));
                                    ps.println("  Expected: "
                                        + hf.formatHex(euc));
                                }
                            }
                        }
                    } catch (Exception e) {
                        ps.println(
                            "Check ProcessEnvironment class implementation");
                        e.printStackTrace(ps);
                    }
                }
                byte[] ba = baos.toByteArray();
                if (ba.length > 0) {
                    System.err.write(ba);
                    System.exit(1);
                }
            }
        }
    }
}
