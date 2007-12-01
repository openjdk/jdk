/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug     6265137
 * @summary setOption() and setExtendedOption() of JavacTool will throw exception for some defined options
 * @author  Peter von der Ah\u00e9
 */

import java.io.File;
import java.util.Arrays;
import javax.tools.*;

public class T6265137 {
    public static void main(String... args) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticListener<JavaFileObject> dl =  new DiagnosticListener<JavaFileObject>() {
                public void report(Diagnostic<? extends JavaFileObject> message) {
                    System.out.println(message.getSource()
                                       +":"+message.getStartPosition()+":"
                                       +message.getStartPosition()+":"+message.getPosition());
                    System.out.println(message.toString());
                    System.out.format("Found problem: %s%n", message.getCode());
                    System.out.flush();
                }
        };
        StandardJavaFileManager fm = javac.getStandardFileManager(dl, null, null);
        String srcdir = System.getProperty("test.src");
        Iterable<? extends JavaFileObject> files =
            fm.getJavaFileObjectsFromFiles(Arrays.asList(new File(srcdir, "T6265137a.java")));
        javac.getTask(null, fm, dl, Arrays.asList("-target","1.5"), null, files).call();
    }
}
