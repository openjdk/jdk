/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8225093
 * @summary Check that JVMTI GetSystemProperty API returns the right values for
 *          property jdk.boot.class.path.append.
 * @requires vm.jvmti
 * @library /test/lib
 * @run main/othervm/native -agentlib:GetBootClassPathAppendProp GetBootClassPathAppendProp
 * @run main/othervm/native -Xbootclasspath/a:blah -agentlib:GetBootClassPathAppendProp GetBootClassPathAppendProp one_arg
 *
 */

public class GetBootClassPathAppendProp {
    private static native String getSystemProperty();

    public static void main(String[] args) throws Exception {
        String path = getSystemProperty();
        if (args.length > 0) {
            if (!path.equals("blah")) {
                throw new RuntimeException("Wrong value returned for jdk.boot.class.path.append: " +
                                           path);
           }
        } else {
            if (path != null) {
                throw new RuntimeException("Null value expected for jdk.boot.class.path.append: " +
                                           path);
            }
        }
    }
}
