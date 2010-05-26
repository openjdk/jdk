/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6358166
 * @summary -verbose reports absurd times when annotation processing involved
 */

import java.io.*;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.*;
import com.sun.tools.javac.file.*;
import com.sun.tools.javac.file.JavacFileManager; // disambiguate
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List; // disambiguate


@SupportedAnnotationTypes("*")
public class T6358166 extends AbstractProcessor {
    public static void main(String... args) throws Throwable {
        String self = T6358166.class.getName();

        String testSrc = System.getProperty("test.src");

        JavacFileManager fm = new JavacFileManager(new Context(), false, null);
        JavaFileObject f = fm.getFileForInput(testSrc + File.separatorChar + self + ".java");

        test(fm, f, "-verbose", "-d", ".");

        test(fm, f, "-verbose", "-d", ".", "-XprintRounds", "-processorpath", ".", "-processor", self);
    }

    static void test(JavacFileManager fm, JavaFileObject f, String... args) throws Throwable {
        Context context = new Context();
        fm.setContext(context);

        Main compilerMain = new Main("javac", new PrintWriter(System.err, true));
        compilerMain.setOptions(Options.instance(context));
        compilerMain.filenames = new ListBuffer<File>();
        compilerMain.processArgs(args);

        JavaCompiler c = JavaCompiler.instance(context);

        c.compile(List.of(f));

        if (c.errorCount() != 0)
            throw new AssertionError("compilation failed");

        long msec = c.elapsed_msec;
        if (msec < 0 || msec > 5 * 60 * 1000) // allow test 5 mins to execute, should be more than enough!
            throw new AssertionError("elapsed time is suspect: " + msec);
    }

    public boolean process(Set<? extends TypeElement> tes, RoundEnvironment renv) {
        return true;
    }
}
