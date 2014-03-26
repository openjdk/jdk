/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7157626 8001112
 * @summary Test major version for all legal combinations for -source and -target
 * @author sgoel
 *
 */

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.regex.*;

public class ClassVersionChecker {

    int errors;
    String[] jdk = {"","1.6","1.7","1.8"};
    File javaFile = null;

    public static void main(String[] args) throws Throwable {
        new ClassVersionChecker().run();
    }

    void run() throws Exception {
        writeTestFile();
        /* Rules applicable for -source and -target combinations
         * 1. If both empty, version num is for the current release
         * 2. If source is not empty and target is empty, version is based on source
         * 3. If both non-empty, version is based on target
         */

        /* -source (0=>empty,1=>1.2,...) X -target (0=>empty,1=>1.2,...)
         * ver[0][0] => no -source or -target was given
         * -1 => invalid combinations
         */
        int[][] ver =
                {{52, -1, -1, -1},
                 {52, 50, 51, 52},
                 {52, -1, 51, 52},
                 {52, -1, -1, 52}};

        // Loop to run all possible combinations of source/target values
        for (int i = 0; i< ver.length; i++) {
            for (int j = 0 ; j< ver[i].length; j++) {
                if(ver[i][j] != -1) {
                    logMsg("Index values for i = " + i + " j = " + j);
                    logMsg("Running for src = " + jdk[i] + " target = "+jdk[j] +" expected = " + ver[i][j]);
                    test(i,j, ver[i][j]);
                }
            }
        }

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    void test (int i, int j, int expected) {
        File classFile = compileTestFile(i, j, javaFile);
        short majorVer = getMajorVersion(classFile);
        checkVersion(majorVer, expected);
    }

    void writeTestFile() throws IOException {
        javaFile = new File("Test.java");
        try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(javaFile)));) {
            out.println("class Test { ");
            out.println("  public void foo() { }");
            out.println("}");
        } catch (IOException ioe) {
            error("IOException while creating Test.java" + ioe);
        }
    }

    File compileTestFile(int i , int j, File f) {
        int rc = -1;
        // Src and target are empty
        if (i == 0 && j == 0 ) {
            rc = compile("-g", f.getPath());
        } else if( j == 0 ) {  // target is empty
            rc = compile("-source", jdk[i], "-g", f.getPath());
        } else {
            rc = compile("-source", jdk[i], "-target", jdk[j], "-g", f.getPath());
        }
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getPath();
        return new File(path.substring(0, path.length() - 5) + ".class");
    }

    int compile(String... args) {
        return com.sun.tools.javac.Main.compile(args);
    }

    void logMsg (String str) {
        System.out.println(str);
    }

    short getMajorVersion(File f) {
        List<String> args = new ArrayList<String>();
        short majorVer = 0;
        try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));) {
            in.readInt();
            in.readShort();
            majorVer = in.readShort();
            System.out.println("major version:" +  majorVer);
        } catch (IOException e) {
            error("IOException while reading Test.class" + e);
        }
        return majorVer;
    }

    void checkVersion(short majorVer, int expected) {
        if (majorVer != expected ) {
            error("versions did not match, Expected: " + expected + "Got: " + majorVer);
        }
    }

    void error(String msg) {
       System.out.println("error: " + msg);
       errors++;
    }
}
