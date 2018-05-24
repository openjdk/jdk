/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * JDK major versions.
 */
public enum JdkRelease {

    JDK6(6, "1.6"),
    JDK7(7, "1.7"),
    JDK8(8, "1.8"),
    JDK9(9, "9"),
    JDK10(10, "10"),
    JDK11(11, "11");

    public final int sequence;
    public final String release;

    private JdkRelease(int sequence, String release) {
        this.sequence = sequence;
        this.release = release;
    }

    public static JdkRelease getRelease(String jdkVersion) {
        if (jdkVersion.startsWith(JDK6.release)) {
            return JDK6;
        } else if (jdkVersion.startsWith(JDK7.release)) {
            return JDK7;
        } else if (jdkVersion.startsWith(JDK8.release)) {
            return JDK8;
        } else if (jdkVersion.startsWith(JDK9.release)) {
            return JDK9;
        } else if (jdkVersion.startsWith(JDK10.release)) {
            return JDK10;
        } else if (jdkVersion.startsWith(JDK11.release)) {
            return JDK11;
        }

        return null;
    }
}
