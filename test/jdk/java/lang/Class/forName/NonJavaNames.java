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
 * Used by NonJavaNames.sh; needs to be run with a classpath including
 * test/java/lang/Class/forName/classes
 */

public class NonJavaNames {
    public static class Baz {
        public Baz(){}
    }

    public static interface myInterface {
    }

     NonJavaNames.myInterface create(){
         // With target 1.5, this class's name will include a '+'
         // instead of a '$'.
         class Baz2 implements NonJavaNames.myInterface {
             public Baz2(){}
         }

        return new Baz2();
     }

    public static void main(String[] args) throws Exception {
        NonJavaNames.Baz bz = new NonJavaNames.Baz();

        String name;

        if (Class.forName(name=bz.getClass().getName()) != NonJavaNames.Baz.class) {
            System.err.println("Class object from forName does not match object.class.");
            System.err.println("Failures for class ``" + name + "''.");
            throw new RuntimeException();
        }

        NonJavaNames.myInterface bz2 = (new NonJavaNames()).create();
        if (Class.forName(name=bz2.getClass().getName()) != bz2.getClass()) {
            System.err.println("Class object from forName does not match getClass.");
            System.err.println("Failures for class ``" + name + "''.");
            throw new RuntimeException();
        }

        String goodNonJavaClassNames []  = {
            ",",
            "+",
            "-",
            "0",
            "3",
            // ":", These names won't work under windows.
            // "<",
            // ">",
            "Z",
            "]"
        };

        for(String s : goodNonJavaClassNames) {
            System.out.println("Testing good class name ``" + s + "''");
            Class.forName(s);
        }

        String badNonJavaClassNames []  = {
            ";",
            "[",
            "."
        };

        for(String s : badNonJavaClassNames) {
            System.out.println("Testing bad class name ``" + s + "''");
            try {
                Class.forName(s);
            } catch (Exception e) {
                // Expected behavior
                continue;
            }
            throw new RuntimeException("Bad class name ``" + s + "'' accepted.");
        }
    }
}
