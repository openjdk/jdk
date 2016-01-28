/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that StringConcat is working for JDK >= 9
 * @compile -source 6 -target 6 TestIndyStringConcat.java
 * @run main TestIndyStringConcat false
 * @clean TestIndyStringConcat*
 * @compile -source 7 -target 7 TestIndyStringConcat.java
 * @run main TestIndyStringConcat false
 * @clean TestIndyStringConcat*
 * @compile -source 8 -target 8 TestIndyStringConcat.java
 * @run main TestIndyStringConcat false
 * @clean TestIndyStringConcat*
 * @compile -XDstringConcat=inline -source 9 -target 9 TestIndyStringConcat.java
 * @run main TestIndyStringConcat false
 * @clean TestIndyStringConcat*
 * @compile -XDstringConcat=indy -source 9 -target 9 TestIndyStringConcat.java
 * @run main TestIndyStringConcat true
 * @clean TestIndyStringConcat*
 * @compile -XDstringConcat=indyWithConstants -source 9 -target 9 TestIndyStringConcat.java
 * @run main TestIndyStringConcat true
 */
public class TestIndyStringConcat {

    private static class MyObject {
        public String toString() {
            throw new RuntimeException("Boyyaa");
        }
    }

    class Inner { }

    public static void main(String[] args) {
        boolean useIndyConcat = Boolean.valueOf(args[0]);
        try {
            String s = "Foo" + new MyObject();
        } catch (RuntimeException ex) {
            boolean indifiedStringConcat = false;
            ex.printStackTrace();
            for (StackTraceElement e : ex.getStackTrace()) {
                if (e.getClassName().startsWith("java.lang.String$Concat") &&
                        e.getMethodName().equals("concat")) {
                    indifiedStringConcat = true;
                    break;
                }
            }
            if (indifiedStringConcat != useIndyConcat) {
                throw new AssertionError();
            }
        }
    }
}
