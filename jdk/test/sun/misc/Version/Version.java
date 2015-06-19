/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.misc
 * @compile -XDignore.symbol.file Version.java
 * @run main Version
 */

import static sun.misc.Version.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version {

    public static void main(String[] args) throws Exception {
        VersionInfo jdk = newVersionInfo(System.getProperty("java.runtime.version"));
        VersionInfo v1 = new VersionInfo(jdkMajorVersion(),
                                         jdkMinorVersion(),
                                         jdkSecurityVersion(),
                                         jdkPatchVersion(),
                                         jdkBuildNumber());
        System.out.println("JDK version = " + jdk + "  " + v1);
        if (!jdk.equals(v1)) {
            throw new RuntimeException("Unmatched version: " + jdk + " vs " + v1);
        }
        VersionInfo jvm = newVersionInfo(System.getProperty("java.vm.version"));
        VersionInfo v2 = new VersionInfo(jvmMajorVersion(),
                                         jvmMinorVersion(),
                                         jvmSecurityVersion(),
                                         jvmPatchVersion(),
                                         jvmBuildNumber());
        System.out.println("JVM version = " + jvm + " " + v2);
        if (!jvm.equals(v2)) {
            throw new RuntimeException("Unmatched version: " + jvm + " vs " + v2);
        }
    }

    static class VersionInfo {
        final int major;
        final int minor;
        final int security;
        final int patch;
        final int build;
        VersionInfo(int major, int minor, int security,
                    int patch, int build) {
            this.major = major;
            this.minor = minor;
            this.security = security;
            this.patch = patch;
            this.build = build;
        }

        VersionInfo(int[] fields) {
            this.major = fields[0];
            this.minor = fields[1];
            this.security = fields[2];
            this.patch = fields[3];
            this.build = fields[4];
        }

        public boolean equals(VersionInfo v) {
            return (this.major == v.major && this.minor == v.minor &&
                    this.security == v.security && this.patch == v.patch &&
                    this.build == v.build);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(major + "." + minor + "." + security);
            if (patch > 0) {
                sb.append("." + patch);
            }

            sb.append("+" + build);
            return sb.toString();
        }
    }

    private static VersionInfo newVersionInfo(String version) throws Exception {
        // Version string fromat as defined by JEP-223
        String jep223Pattern =
                "^([0-9]+)(\\.([0-9]+))?(\\.([0-9]+))?(\\.([0-9]+))?" + // $VNUM
                "(-([a-zA-Z]+))?(\\.([a-zA-Z]+))?" + // $PRE
                "(\\+([0-9]+))?" +                   // Build Number
                "(([-a-zA-Z0-9.]+))?$";              // $OPT

        // Pattern group index for: Major, Minor, Security, Patch, Build
        int[] groups = {1, 3, 5, 7, 13};
        // Default values for Major, Minor, Security, Patch, Build
        int[] versionFields = {0, 0, 0, 0, 0};

        Pattern pattern = Pattern.compile(jep223Pattern);
        Matcher matcher = pattern.matcher(version);
        if (matcher.matches()) {
            for (int i = 0; i < versionFields.length; i++) {
                String field = matcher.group(groups[i]);
                versionFields[i] = (field != null) ? Integer.parseInt(field) : 0;
            }
        }

        VersionInfo vi = new VersionInfo(versionFields);
        System.out.printf("newVersionInfo: input=%s output=%s\n", version, vi);
        return vi;
    }
}
