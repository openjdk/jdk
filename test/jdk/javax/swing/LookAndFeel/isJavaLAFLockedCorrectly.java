/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4236213
 * @summary Regression test isJavaLAFLockedCorrectly.java Failing with JDK-1.2.2-R
 */

import javax.swing.LookAndFeel;

public class isJavaLAFLockedCorrectly {
    public static void main(String[] args) {
        System.out.println(" === isJavaLAFLockedCorrectly === ");

        LookAndFeel newJLF;
        try {
            // try to make a version of the JLF
            Class jlfClass = Class.forName("javax.swing.plaf.metal.MetalLookAndFeel");
            newJLF = (LookAndFeel) (jlfClass.newInstance());
        } catch (Exception e) {
            // if any of these things didn't work, throw an exception
            throw new RuntimeException("JLF not correctly (un)locked " +
                    "- Class files probably missing");
        }

        // see if the JLF is supported here
        // it sure better be as it's supposed to be supported everywhere
        if (newJLF.isSupportedLookAndFeel() == true) {
            System.out.println("\t JLF correctly locked");
        } else {
            throw new RuntimeException("JLF not correctly (un)locked");
        }
    }
}
