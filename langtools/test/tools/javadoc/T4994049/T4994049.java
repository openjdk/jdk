/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     4994049
 * @summary Unit test for SourcePosition.column with respect to tab expansion
 * @author  Peter von der Ah\u00e9
 * @run main T4994049 FileWithTabs.java
 */

import com.sun.javadoc.*;
import java.io.*;
import static com.sun.tools.javadoc.Main.execute;

public class T4994049 extends Doclet {

    public static boolean start(RootDoc root) {
        for (ClassDoc klass : root.classes()) {
            for (MethodDoc method : klass.methods()) {
                if (method.name().equals("tabbedMethod")) {
                    if (method.position().column() == 21) {
                        System.out.println(method.position().column() + ": OK!");
                        return true;
                    } else {
                        System.err.println(method.position() + ": wrong tab expansion");
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public static void main(String... args) throws Exception {
        File testSrc = new File(System.getProperty("test.src"));
        File tmpSrc = new File("tmpSrc");
        initTabs(testSrc, tmpSrc);

        for (String file : args) {
            File source = new File(tmpSrc, file);
            int rc = execute("javadoc", "T4994049", T4994049.class.getClassLoader(),
                        new String[]{ source.getPath() } );
            if (rc != 0)
                throw new Error("Unexpected return code from javadoc: " + rc);
        }
    }

    static void initTabs(File from, File to) throws IOException {
        for (File f: from.listFiles()) {
            File t = new File(to, f.getName());
            if (f.isDirectory()) {
                initTabs(f, t);
            } else if (f.getName().endsWith(".java")) {
                write(t, read(f).replace("\\t", "\t"));
            }
        }
    }

    static String read(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    static void write(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        try (Writer out = new FileWriter(f)) {
            out.write(s);
        }
    }

}
