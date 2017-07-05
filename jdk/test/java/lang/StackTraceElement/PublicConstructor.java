/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4712607
 * @summary Basic test for StackTraceElementPublic constructor
 * @author  Josh Bloch
 */

import java.util.*;

public class PublicConstructor {
    public static void main(String args[]) {
        StackTraceElement ste = new StackTraceElement("com.acme.Widget",
            "frobnicate", "Widget.java", 42);
        if (!(ste.getClassName().equals("com.acme.Widget")  &&
              ste.getFileName().equals("Widget.java") &&
              ste.getMethodName().equals("frobnicate") &&
              ste.getLineNumber() == 42))
            throw new RuntimeException("1");
        if (ste.isNativeMethod())
            throw new RuntimeException("2");
        StackTraceElement ste2
            = new StackTraceElement("jdk.module",
                                    "9.0",
                                    "com.acme.Widget",
                                    "frobnicate",
                                    "Widget.java",
                                    42);
        if (!(ste2.getClassName().equals("com.acme.Widget")  &&
                ste2.getModuleName().equals("jdk.module") &&
                ste2.getModuleVersion().equals("9.0") &&
                ste2.getFileName().equals("Widget.java") &&
                ste2.getMethodName().equals("frobnicate") &&
                ste2.getLineNumber() == 42))
            throw new RuntimeException("3");
        if (ste2.isNativeMethod())
            throw new RuntimeException("4");
        StackTraceElement ste3 = new StackTraceElement("com.acme.Widget",
            "frobnicate", "Widget.java", -2);
        if (!ste3.isNativeMethod())
            throw new RuntimeException("5");
    }
}
