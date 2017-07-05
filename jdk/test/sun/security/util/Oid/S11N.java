/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4811968 6908628 8006564 8130696
 * @modules java.base/sun.misc
 *          java.base/sun.security.util
 * @run main S11N check
 * @summary Serialization compatibility with old versions (and fixes)
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import sun.misc.BASE64Encoder;
import sun.security.util.ObjectIdentifier;

public class S11N {
    static String[] SMALL= {
        "0.0",
        "1.1",
        "2.2",
        "1.2.3456",
        "1.2.2147483647.4",
        "1.2.268435456.4",
    };

    static String[] HUGE = {
        "2.16.764.1.3101555394.1.0.100.2.1",
        "1.2.2147483648.4",
        "2.3.4444444444444444444444",
        "1.2.8888888888888888.33333333333333333.44444444444444",
    };

    // Do not use j.u.Base64, the test needs to run in jdk6
    static BASE64Encoder encoder = new BASE64Encoder() {
        @Override
        protected int bytesPerLine() {
            return 48;
        }
    };

    public static void main(String[] args) throws Exception {
        if (args[0].equals("check")) {
            String jv = System.getProperty("java.version");
            // java.version format: $VNUM\-$PRE
            String [] va = (jv.split("-")[0]).split("\\.");
            String v = (va.length == 1 || !va[0].equals("1")) ? va[0] : va[1];
            int version = Integer.valueOf(v);
            System.out.println("version is " + version);
            if (version >= 7) {
                for (String oid: SMALL) {
                    // 7 -> 7
                    check(out(oid), oid);
                    // 6 -> 7
                    check(out6(oid), oid);
                }
                for (String oid: HUGE) {
                    // 7 -> 7
                    check(out(oid), oid);
                }
            } else {
                for (String oid: SMALL) {
                    // 6 -> 6
                    check(out(oid), oid);
                    // 7 -> 6
                    check(out7(oid), oid);
                }
                for (String oid: HUGE) {
                    // 7 -> 6 fails for HUGE oids
                    boolean ok = false;
                    try {
                        check(out7(oid), oid);
                        ok = true;
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                    if (ok) {
                        throw new Exception();
                    }
                }
            }
        } else {
            // Generates the JDK6 serialized string inside this test, call
            //      $JDK7/bin/java S11N dump7
            //      $JDK6/bin/java S11N dump6
            // and paste the output at the end of this test.
            dump(args[0], SMALL);
            // For jdk6, the following line will throw an exception, ignore it
            dump(args[0], HUGE);
        }
    }

    // Gets the serialized form for jdk6
    private static byte[] out6(String oid) throws Exception {
        return new sun.misc.BASE64Decoder().decodeBuffer(dump6.get(oid));
    }

    // Gets the serialized form for jdk7
    private static byte[] out7(String oid) throws Exception {
        return new sun.misc.BASE64Decoder().decodeBuffer(dump7.get(oid));
    }

    // Gets the serialized form for this java
    private static byte[] out(String oid) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        new ObjectOutputStream(bout).writeObject(new ObjectIdentifier(oid));
        return bout.toByteArray();
    }

    // Makes sure serialized form can be deserialized
    private static void check(byte[] in, String oid) throws Exception {
        ObjectIdentifier o = (ObjectIdentifier) (
                new ObjectInputStream(new ByteArrayInputStream(in)).readObject());
        if (!o.toString().equals(oid)) {
            throw new Exception("Read Fail " + o + ", not " + oid);
        }
    }

    // dump serialized form to java code style text
    private static void dump(String title, String[] oids) throws Exception {
        for (String oid: oids) {
            String[] base64 = encoder.encodeBuffer(out(oid)).split("\n");
            System.out.println("        " + title + ".put(\"" + oid + "\",");
            for (int i = 0; i<base64.length; i++) {
                System.out.print("            \"" + base64[i] + "\"");
                if (i == base64.length - 1) {
                    System.out.println(");");
                } else {
                    System.out.println(" +");
                }
            }
        }
    }

    // Do not use diamond operator, this test is also meant to run in jdk6
    private static Map<String,String> dump7 = new HashMap<String,String>();
    private static Map<String,String> dump6 = new HashMap<String,String>();

    static {
        //////////////  PASTE BEGIN //////////////
        dump7.put("0.0",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhwAAAAAnVyAAJbSU26YCZ26rKlAgAAeHAA" +
            "AAACAAAAAAAAAAB1cgACW0Ks8xf4BghU4AIAAHhwAAAAAQB4");
        dump7.put("1.1",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhwAAAAAnVyAAJbSU26YCZ26rKlAgAAeHAA" +
            "AAACAAAAAQAAAAF1cgACW0Ks8xf4BghU4AIAAHhwAAAAASl4");
        dump7.put("2.2",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhwAAAAAnVyAAJbSU26YCZ26rKlAgAAeHAA" +
            "AAACAAAAAgAAAAJ1cgACW0Ks8xf4BghU4AIAAHhwAAAAAVJ4");
        dump7.put("1.2.3456",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhwAAAAA3VyAAJbSU26YCZ26rKlAgAAeHAA" +
            "AAADAAAAAQAAAAIAAA2AdXIAAltCrPMX+AYIVOACAAB4cAAAAAMqmwB4");
        dump7.put("1.2.2147483647.4",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhwAAAABHVyAAJbSU26YCZ26rKlAgAAeHAA" +
            "AAAEAAAAAQAAAAJ/////AAAABHVyAAJbQqzzF/gGCFTgAgAAeHAAAAAHKof///9/" +
            "BHg=");
        dump7.put("1.2.268435456.4",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhwAAAABHVyAAJbSU26YCZ26rKlAgAAeHAA" +
            "AAAEAAAAAQAAAAIQAAAAAAAABHVyAAJbQqzzF/gGCFTgAgAAeHAAAAAHKoGAgIAA" +
            "BHg=");
        dump7.put("2.16.764.1.3101555394.1.0.100.2.1",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhw/////3NyAD5zdW4uc2VjdXJpdHkudXRp" +
            "bC5PYmplY3RJZGVudGlmaWVyJEh1Z2VPaWROb3RTdXBwb3J0ZWRCeU9sZEpESwAA" +
            "AAAAAAABAgAAeHB1cgACW0Ks8xf4BghU4AIAAHhwAAAADmCFfAGLxvf1QgEAZAIB" +
            "eA==");
        dump7.put("1.2.2147483648.4",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhw/////3NyAD5zdW4uc2VjdXJpdHkudXRp" +
            "bC5PYmplY3RJZGVudGlmaWVyJEh1Z2VPaWROb3RTdXBwb3J0ZWRCeU9sZEpESwAA" +
            "AAAAAAABAgAAeHB1cgACW0Ks8xf4BghU4AIAAHhwAAAAByqIgICAAAR4");
        dump7.put("2.3.4444444444444444444444",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhw/////3NyAD5zdW4uc2VjdXJpdHkudXRp" +
            "bC5PYmplY3RJZGVudGlmaWVyJEh1Z2VPaWROb3RTdXBwb3J0ZWRCeU9sZEpESwAA" +
            "AAAAAAABAgAAeHB1cgACW0Ks8xf4BghU4AIAAHhwAAAADFOD4e+HpNG968eOHHg=");
        dump7.put("1.2.8888888888888888.33333333333333333.44444444444444",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4DAANJAAxjb21wb25lbnRMZW5MAApjb21wb25lbnRzdAASTGphdmEvbGFuZy9P" +
            "YmplY3Q7WwAIZW5jb2Rpbmd0AAJbQnhw/////3NyAD5zdW4uc2VjdXJpdHkudXRp" +
            "bC5PYmplY3RJZGVudGlmaWVyJEh1Z2VPaWROb3RTdXBwb3J0ZWRCeU9sZEpESwAA" +
            "AAAAAAABAgAAeHB1cgACW0Ks8xf4BghU4AIAAHhwAAAAGCqP5Yzbxa6cOLubj9ek" +
            "japVio3AusuOHHg=");

        dump6.put("0.0",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4CAAJJAAxjb21wb25lbnRMZW5bAApjb21wb25lbnRzdAACW0l4cAAAAAJ1cgAC" +
            "W0lNumAmduqypQIAAHhwAAAAAgAAAAAAAAAA");
        dump6.put("1.1",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4CAAJJAAxjb21wb25lbnRMZW5bAApjb21wb25lbnRzdAACW0l4cAAAAAJ1cgAC" +
            "W0lNumAmduqypQIAAHhwAAAAAgAAAAEAAAAB");
        dump6.put("2.2",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4CAAJJAAxjb21wb25lbnRMZW5bAApjb21wb25lbnRzdAACW0l4cAAAAAJ1cgAC" +
            "W0lNumAmduqypQIAAHhwAAAAAgAAAAIAAAAC");
        dump6.put("1.2.3456",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4CAAJJAAxjb21wb25lbnRMZW5bAApjb21wb25lbnRzdAACW0l4cAAAAAN1cgAC" +
            "W0lNumAmduqypQIAAHhwAAAAAwAAAAEAAAACAAANgA==");
        dump6.put("1.2.2147483647.4",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4CAAJJAAxjb21wb25lbnRMZW5bAApjb21wb25lbnRzdAACW0l4cAAAAAR1cgAC" +
            "W0lNumAmduqypQIAAHhwAAAABAAAAAEAAAACf////wAAAAQ=");
        dump6.put("1.2.268435456.4",
            "rO0ABXNyACJzdW4uc2VjdXJpdHkudXRpbC5PYmplY3RJZGVudGlmaWVyeLIO7GQX" +
            "fy4CAAJJAAxjb21wb25lbnRMZW5bAApjb21wb25lbnRzdAACW0l4cAAAAAR1cgAC" +
            "W0lNumAmduqypQIAAHhwAAAABAAAAAEAAAACEAAAAAAAAAQ=");
        //////////////  PASTE END //////////////
    }
}
