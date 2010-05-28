/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6306137
 * @summary JSR 199: encoding option doesn't affect standard file manager
 * @author  Peter von der Ahé
 * @ignore
 *    Need to make the contentCache in JavacFileManager be aware of changes to the encoding.
 *    Need to propogate -source (and -encoding?) down to the JavacFileManager
 */

import java.io.File;
import java.util.Arrays;
import javax.tools.*;

public class T6306137 {
    boolean error;
    final StandardJavaFileManager fm;
    final JavaCompiler compiler;
    Iterable<? extends JavaFileObject> files;
    DiagnosticListener<JavaFileObject> dl;

    T6306137() {
        dl = new DiagnosticListener<JavaFileObject>() {
                public void report(Diagnostic<? extends JavaFileObject> message) {
                    if (message.getKind() == Diagnostic.Kind.ERROR)
                        error = true;
                    System.out.println(message.getSource()
                                       +":"+message.getStartPosition()+":"
                                       +message.getStartPosition()+":"+message.getPosition());
                    System.out.println(message.toString());
                    System.out.format("Found problem: %s%n", message.getCode());
                    System.out.flush();
                }
        };
        compiler = ToolProvider.getSystemJavaCompiler();
        fm = compiler.getStandardFileManager(dl, null, null);
        String srcdir = System.getProperty("test.src");
        files =
            fm.getJavaFileObjectsFromFiles(Arrays.asList(new File(srcdir, "T6306137.java")));
    }

    void test(String encoding, boolean good) {
        error = false;
        Iterable<String> args = Arrays.asList("-source", "6", "-encoding", encoding, "-d", ".");
        compiler.getTask(null, fm, dl, args, null, files).call();
        if (error == good) {
            if (error) {
                throw new AssertionError("Error reported");
            } else {
                throw new AssertionError("No error reported");
            }
        }
    }

    public static void main(String[] args) {
        T6306137 self = new T6306137();
        self.test("utf-8", true);
        self.test("ascii", false);
        self.test("utf-8", true);
    }
}
