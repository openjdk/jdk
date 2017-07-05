/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This class is used by test i18nTest.sh
 *
 * Class to create various i18n Hello World Java source files using
 * the platform's default encoding of a non-ASCII name; create plain
 * ASCII Hello World if the platform's default is charset is US-ASCII.
 */

import java.io.PrintWriter;
import java.io.FileOutputStream;

public class CreatePlatformFile {
    public static void main(String argv[])  {
        String fileSep = System.getProperty("file.separator");
        String defaultEncoding = System.getProperty("file.encoding");

        if(defaultEncoding == null) {
            System.err.println("Default encoding not found; Error.");
            return;
        }

        if (defaultEncoding.equals("Cp1252") ) {
            // "HelloWorld" with an accented e
            String fileName = "i18nH\u00e9lloWorld.java";
            try {
                PrintWriter pw = new PrintWriter(new FileOutputStream("."+fileSep+fileName));
                pw.println("public class i18nH\u00e9lloWorld {");
                pw.println("    public static void main(String [] argv) {");
                pw.println("        System.out.println(\"Hello Cp1252 World\");");
                pw.println("    }");
                pw.println("}");
                pw.flush();
                pw.close();
            }
            catch (java.io.FileNotFoundException e) {
                System.err.println("Problem opening file; test fails");
            }

        } else {
            // ASCII "HelloWorld"
            String fileName = "i18nHelloWorld.java";
            try {
                PrintWriter pw = new PrintWriter(new FileOutputStream("."+fileSep+fileName));
                pw.println("public class i18nHelloWorld {");
                pw.println("    public static void main(String [] argv) {");
                pw.println("        System.out.println(\"Warning: US-ASCII assumed; filenames with\");");
                pw.println("        System.out.println(\"non-ASCII characters will not be tested\");");
                pw.println("    }");
                pw.println("}");
                pw.flush();
                pw.close();
            }
            catch (java.io.FileNotFoundException e) {
                System.err.println("Problem opening file; test fails");
            }
        }
    }
}
