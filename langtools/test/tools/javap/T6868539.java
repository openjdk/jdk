/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6868539 6868548
 * @summary javap should use current names for constant pool entries,
 *              remove spurious ';' from constant pool entries
 */

import java.io.*;
import java.util.*;

public class T6868539

{
    public static void main(String... args) {
        new T6868539().run();
    }

    void run() {
        verify("T6868539", "Utf8 +java/lang/String");                                   // 1: Utf8
                                                                                        // 2: currently unused
        verify("T6868539", "Integer +123456");                                          // 3: Integer
        verify("T6868539", "Float +123456.0f");                                         // 4: Float
        verify("T6868539", "Long +123456l");                                            // 5: Long
        verify("T6868539", "Double +123456.0d");                                        // 6: Double
        verify("T6868539", "Class +#[0-9]+ +// + T6868539");                            // 7: Class
        verify("T6868539", "String +#[0-9]+ +// + not found");                          // 8: String
        verify("T6868539", "Fieldref +#[0-9]+\\.#[0-9]+ +// +T6868539.errors:I");       // 9: Fieldref
        verify("T6868539", "Methodref +#[0-9]+\\.#[0-9]+ +// +T6868539.run:\\(\\)V");   // 10: Methodref
        verify("T6868539", "InterfaceMethodref +#[0-9]+\\.#[0-9]+ +// +java/lang/Runnable\\.run:\\(\\)V");
                                                                                        // 11: InterfaceMethodref
        verify("T6868539", "NameAndType +#[0-9]+:#[0-9]+ +// +run:\\(\\)V");            // 12: NameAndType
        if (errors > 0)
            throw new Error(errors + " found.");
    }

    void verify(String className, String... expects) {
        String output = javap(className);
        for (String expect: expects) {
            if (!output.matches("(?s).*" + expect + ".*"))
                error(expect + " not found");
        }
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;

    String javap(String className) {
        String testClasses = System.getProperty("test.classes", ".");
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        String[] args = { "-v", "-classpath", testClasses, className };
        int rc = com.sun.tools.javap.Main.run(args, out);
        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        out.close();
        String output = sw.toString();
        System.out.println("class " + className);
        System.out.println(output);
        return output;
    }

    int i = 123456;
    float f = 123456.f;
    double d = 123456.;
    long l = 123456L;

    void m(Runnable r) { r.run(); }
}

