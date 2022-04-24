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
 * @run main i18nEnvArg
 */

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;

public class i18nEnvArg {
    final static String text = "\u6F22\u5B57";
    final static String javeExe = System.getProperty("java.home")
        + File.separator
        + "bin"
        + File.separator
        + "java";
    final static int maxSize = 4096;

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(javeExe,
            "-classpath",
            System.getProperty("java.class.path"),
            "i18nEnvArg$Start");
        pb.redirectErrorStream(true);
        Map<String, String> environ = pb.environment();
        environ.clear();
        environ.put("LANG", "ja_JP.eucjp");
        Process p = pb.start();
        InputStream is = p.getInputStream();
        byte[] ba = new byte[maxSize];
        ByteBuffer bb = ByteBuffer.wrap(ba);
        int ch;
        while((ch = is.read()) != -1) {
            bb.put((byte)ch);
        }
        int rc = p.waitFor();
        if (bb.position() > 0) {
            throw new Exception(new String(ba, 0, bb.position()));
        }
    }

    public static class Start {
        public static void main(String[] args) throws Exception {
            String nativeEncoding = System.getProperty("native.encoding");
            Charset dcs = nativeEncoding == null ?
                Charset.defaultCharset() :
                Charset.forName(nativeEncoding);
            Charset cs = Charset.forName("x-euc-jp-linux");
            if (!dcs.equals(cs)) {
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(javeExe,
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-classpath",
                System.getProperty("java.class.path"),
                "i18nEnvArg$Verify",
                text);
            pb.redirectErrorStream(true);
            Map<String, String> environ = pb.environment();
            environ.clear();
            environ.put("LANG", "ja_JP.eucjp");
            environ.put(text, text);
            Process p = pb.start();
            InputStream is = p.getInputStream();
            byte[] ba = new byte[maxSize];
            ByteBuffer bb = ByteBuffer.wrap(ba);
            int ch;
            while((ch = is.read()) != -1) {
                bb.put((byte)ch);
            }
            int rc = p.waitFor();
            System.out.write(ba, 0, bb.position());
        }
    }

    public static class Verify {
        public static void main(String[] args) throws Exception {
            Charset cs = Charset.forName("x-euc-jp-linux");
            byte[] kanji = (text + "=" + text).getBytes(cs);
            String s = System.getenv(text);
            if (!text.equals(s)) {
                throw new Exception("getenv() returns unexpected data");
            }
            if (!text.equals(args[0])) {
                System.out.print("Unexpected argument was received: ");
                for(char ch : text.toCharArray()) {
                   System.out.printf("\\u%04X", (int)ch);
                }
                System.out.print("<->");
                for(char ch : args[0].toCharArray()) {
                   System.out.printf("\\u%04X", (int)ch);
                }
                System.out.println();
            }
            Class<?> cls = Class.forName("java.lang.ProcessEnvironment");
            Method enviorn_mid = cls.getDeclaredMethod("environ");
            enviorn_mid.setAccessible(true);
            byte[][] environ = (byte[][]) enviorn_mid.invoke(null,
                (Object[])null);
            byte[] ba = new byte[maxSize];
            StringJoiner sj = new StringJoiner(", ");
            for(int i = 0; i < environ.length; i += 2) {
                ByteBuffer bb = ByteBuffer.wrap(ba);
                bb.put(environ[i]);
                bb.put((byte)'=');
                bb.put(environ[i+1]);
                byte[] envb = Arrays.copyOf(ba, bb.position());
                if (Arrays.equals(kanji, envb)) return;
                StringBuilder sb = new StringBuilder();
                for(byte b : envb) {
                    if (b == 0x5C) {
                        sb.append("\\x5C");
                    } else if (b >= 0x20 && b <= 0x7F) {
                        sb.append((char)b);
                    } else {
                        sb.append(String.format("\\x%02X", (int)b & 0xFF));
                    }
                }
                sj.add(sb.toString());
            }
            System.out.println("Unexpected environment variables: "
                + sj.toString());
        }
    }
}
