/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib;

import java.util.ArrayList;

public class StringArrayUtils {
    /**
     * The various concat() functions in this class can be used for building
     * a command-line argument array for ProcessTools.createTestJavaProcessBuilder(),
     * etc. When some of the arguments are conditional, this is more convenient
     * than alternatives like ArrayList.
     *
     * Example:
     *
     * <pre>
     *     String args[] = StringArrayUtils.concat("-Xint", "-Xmx32m");
     *     if (verbose) {
     *         args = StringArrayUtils.concat(args, "-verbose");
     *     }
     *     args = StringArrayUtils.concat(args, "HelloWorld");
     *     ProcessTools.createTestJavaProcessBuilder(args);
     * </pre>
     */
    public static String[] concat(String... args) {
        return args;
    }

    public static String[] concat(String[] prefix, String... extra) {
        String[] ret = new String[prefix.length + extra.length];
        System.arraycopy(prefix, 0, ret, 0, prefix.length);
        System.arraycopy(extra, 0, ret, prefix.length, extra.length);
        return ret;
    }

    public static String[] concat(String prefix, String[] extra) {
        String[] ret = new String[1 + extra.length];
        ret[0] = prefix;
        System.arraycopy(extra, 0, ret, 1, extra.length);
        return ret;
    }
}
