/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6441871
 * @summary spurious compiler error elicited by packageElement.getEnclosedElements()
 * @build b6341534
 * @run main T6430209
 */

// Note that 6441871 is an interim partial fix for 6430209 that just removes the javac
// crash message and stacktrace

import java.io.*;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.*;
import com.sun.source.util.*;
import com.sun.tools.javac.api.*;


public class T6430209 {
    public static void main(String... args) throws IOException {
        // set up dir1/test0.java
        File dir1 = new File("dir1");
        dir1.mkdir();
        BufferedWriter fout = new BufferedWriter(new FileWriter(new File(dir1, "test0.java")));
        fout.write("public class test0 { }");
        fout.close();

        // run annotation processor b6341534 so we can check diagnostics
        // -proc:only -processor b6341534 -cp . ./src/*.java
        String testSrc = System.getProperty("test.src", ".");
        String testClasses = System.getProperty("test.classes");
        JavacTool tool = JavacTool.create();
        MyDiagListener dl = new MyDiagListener();
        StandardJavaFileManager fm = tool.getStandardFileManager(dl, null, null);
        fm.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(new File(".")));
        Iterable<? extends JavaFileObject> files = fm.getJavaFileObjectsFromFiles(Arrays.asList(
            new File(testSrc, "test0.java"), new File(testSrc, "test1.java")));
        Iterable<String> opts = Arrays.asList("-proc:only",
                                              "-processor", "b6341534",
                                              "-processorpath", testClasses);
        StringWriter out = new StringWriter();
        JavacTask task = tool.getTask(out, fm, dl, opts, null, files);
        task.call();
        String s = out.toString();
        System.err.print(s);
        // Expect the following 2 diagnostics, and no output to log
        System.err.println(dl.count + " diagnostics; " + s.length() + " characters");
        if (dl.count != 2 || s.length() != 0)
            throw new AssertionError("unexpected output from compiler");
    }

    static class MyDiagListener implements DiagnosticListener<JavaFileObject> {
        public void report(Diagnostic d) {
            System.err.println(d);
            count++;
        }

        public int count;
    }
}
