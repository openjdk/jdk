/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6729471
 * @summary javap should accept class files on the command line
 * @library /tools/lib
 * @build ToolBox
 * @run main T6729471
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class T6729471
{
    public static void main(String... args) {
        new T6729471().run();
    }

    void run() {
        File testClasses = new File(System.getProperty("test.classes"));

        // simple class
        verify("java.util.Map",
                "public abstract boolean containsKey(java.lang.Object)");

        // inner class
        verify("java.util.Map.Entry",
                "public abstract K getKey()");

        // file name
        verify(new File(testClasses, "T6729471.class").getPath(),
                "public static void main(java.lang.String...)");

        // file url
        verify(new File(testClasses, "T6729471.class").toURI().toString(),
                "public static void main(java.lang.String...)");

        // jar url
        // Create a temp jar
        ToolBox tb = new ToolBox();
        tb.new JavacTask()
          .sources("class Foo { public void sayHello() {} }")
          .run();
        String foo_jar = "foo.jar";
        tb.new JarTask(foo_jar)
          .files("Foo.class")
          .run();
        File foo_jarFile = new File(foo_jar);

        // Verify
        try {
            verify("jar:" + foo_jarFile.toURL() + "!/Foo.class",
                "public void sayHello()");
        } catch (MalformedURLException e) {
            error(e.toString());
        }

        if (errors > 0)
            throw new Error(errors + " found.");
    }

    void verify(String className, String... expects) {
        String output = javap(className);
        for (String expect: expects) {
            if (output.indexOf(expect)< 0)
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
        String[] args = { "-classpath", testClasses, className };
        int rc = com.sun.tools.javap.Main.run(args, out);
        out.close();
        String output = sw.toString();
        System.out.println("class " + className);
        System.out.println(output);
        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        if (output.indexOf("Error:") != -1)
            throw new Error("javap reported error.");
        return output;
    }
}

