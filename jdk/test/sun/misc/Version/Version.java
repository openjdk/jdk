/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6994413
 * @summary Check the JDK and JVM version returned by sun.misc.Version
 *          matches the versions defined in the system properties
 * @compile -XDignore.symbol.file Version.java
 * @run main Version
 */

import static sun.misc.Version.*;
public class Version {

    public static void main(String[] args) throws Exception {
        VersionInfo jdk = newVersionInfo(System.getProperty("java.runtime.version"));
        VersionInfo v1 = new VersionInfo(jdkMajorVersion(),
                                         jdkMinorVersion(),
                                         jdkMicroVersion(),
                                         jdkUpdateVersion(),
                                         jdkSpecialVersion(),
                                         jdkBuildNumber());
        System.out.println("JDK version = " + jdk + "  " + v1);
        if (!jdk.equals(v1)) {
            throw new RuntimeException("Unmatched version: " + jdk + " vs " + v1);
        }
        VersionInfo jvm = newVersionInfo(System.getProperty("java.vm.version"));
        VersionInfo v2 = new VersionInfo(jvmMajorVersion(),
                                         jvmMinorVersion(),
                                         jvmMicroVersion(),
                                         jvmUpdateVersion(),
                                         jvmSpecialVersion(),
                                         jvmBuildNumber());
        System.out.println("JVM version = " + jvm + " " + v2);
        if (!jvm.equals(v2)) {
            throw new RuntimeException("Unmatched version: " + jvm + " vs " + v2);
        }
    }

    static class VersionInfo {
        final int major;
        final int minor;
        final int micro;
        final int update;
        final String special;
        final int build;
        VersionInfo(int major, int minor, int micro,
                    int update, String special, int build) {
            this.major = major;
            this.minor = minor;
            this.micro = micro;
            this.update = update;
            this.special = special;
            this.build = build;
        }

        public boolean equals(VersionInfo v) {
            return (this.major == v.major && this.minor == v.minor &&
                    this.micro == v.micro && this.update == v.update &&
                    this.special.equals(v.special) && this.build == v.build);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(major + "." + minor + "." + micro);
            if (update > 0) {
                sb.append("_" + update);
            }

            if (!special.isEmpty()) {
                sb.append(special);
            }
            sb.append("-b" + build);
            return sb.toString();
        }
    }

    private static VersionInfo newVersionInfo(String version) throws Exception {
        // valid format of the version string is:
        // n.n.n[_uu[c]][-<identifer>]-bxx
        int major = 0;
        int minor = 0;
        int micro = 0;
        int update = 0;
        String special = "";
        int build = 0;
        CharSequence cs = version;
        if (cs.length() >= 5) {
            if (Character.isDigit(cs.charAt(0)) && cs.charAt(1) == '.' &&
                Character.isDigit(cs.charAt(2)) && cs.charAt(3) == '.' &&
                Character.isDigit(cs.charAt(4))) {
                major = Character.digit(cs.charAt(0), 10);
                minor = Character.digit(cs.charAt(2), 10);
                micro = Character.digit(cs.charAt(4), 10);
                cs = cs.subSequence(5, cs.length());
            } else if (Character.isDigit(cs.charAt(0)) &&
                       Character.isDigit(cs.charAt(1)) && cs.charAt(2) == '.' &&
                       Character.isDigit(cs.charAt(3))) {
                // HSX has nn.n (major.minor) version
                major = Integer.valueOf(version.substring(0, 2)).intValue();
                minor = Character.digit(cs.charAt(3), 10);
                cs = cs.subSequence(4, cs.length());
            }
            if (cs.charAt(0) == '_' && cs.length() >= 3 &&
                Character.isDigit(cs.charAt(1)) &&
                Character.isDigit(cs.charAt(2))) {
                int nextChar = 3;
                String uu = cs.subSequence(1, 3).toString();
                update = Integer.valueOf(uu).intValue();
                if (cs.length() >= 4) {
                    char c = cs.charAt(3);
                    if (c >= 'a' && c <= 'z') {
                        special = Character.toString(c);
                        nextChar++;
                    }
                }
                cs = cs.subSequence(nextChar, cs.length());
            }
            if (cs.charAt(0) == '-') {
                // skip the first character
                // valid format: <identifier>-bxx or bxx
                // non-product VM will have -debug|-release appended
                cs = cs.subSequence(1, cs.length());
                String[] res = cs.toString().split("-");
                for (int i = res.length - 1; i >= 0; i--) {
                    String s = res[i];
                    if (s.charAt(0) == 'b') {
                        try {
                            build = Integer.parseInt(s.substring(1, s.length()));
                            break;
                        } catch (NumberFormatException nfe) {
                            // ignore
                        }
                    }
                }
            }
        }
        VersionInfo vi = new VersionInfo(major, minor, micro, update, special, build);
        System.out.printf("newVersionInfo: input=%s output=%s\n", version, vi);
        return vi;
    }
}
