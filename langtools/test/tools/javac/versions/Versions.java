/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4981566 5028634 5094412 6304984 7025786 7025789 8001112 8028545 8000961 8030610 8028546
 * @summary Check interpretation of -target and -source options
 * @modules java.compiler
 *          jdk.compiler
 * @run main Versions
 */

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;


public class Versions {

    protected JavaCompiler javacompiler;
    protected int failedCases;

    public Versions() throws IOException {
        javacompiler = ToolProvider.getSystemJavaCompiler();
        genSourceFiles();
        failedCases = 0;
    }

    public static void main(String... args) throws IOException {
        Versions versions = new Versions();
        versions.run();
    }

    void run() {

        String TC = "";
        System.out.println("Version.java: Starting");

        check("53.0");
        check("53.0", "-source 1.6");
        check("53.0", "-source 1.7");
        check("53.0", "-source 1.8");
        check("53.0", "-source 1.9");
        check("53.0", "-source 1.10");

        check_source_target("50.0", "6", "6");
        check_source_target("51.0", "6", "7");
        check_source_target("51.0", "7", "7");
        check_source_target("52.0", "6", "8");
        check_source_target("52.0", "7", "8");
        check_source_target("52.0", "8", "8");
        check_source_target("53.0", "6", "9");
        check_source_target("53.0", "7", "9");
        check_source_target("53.0", "8", "9");
        check_source_target("53.0", "9", "9");
        check_source_target("53.0", "10", "10");

        checksrc16("-source 1.6");
        checksrc16("-source 6");
        checksrc16("-source 1.6", "-target 1.6");
        checksrc16("-source 6", "-target 6");
        checksrc17("-source 1.7");
        checksrc17("-source 7");
        checksrc17("-source 1.7", "-target 1.7");
        checksrc17("-source 7", "-target 7");
        checksrc18("-source 1.8");
        checksrc18("-source 8");
        checksrc18("-source 1.8", "-target 1.8");
        checksrc18("-source 8", "-target 8");
        checksrc19("-source 1.9");
        checksrc19("-source 9");
        checksrc19("-source 1.9", "-target 1.9");
        checksrc19("-source 9", "-target 9");

        checksrc110();
        checksrc110("-source 1.10");
        checksrc110("-source 10");
        checksrc110("-source 1.10", "-target 1.10");
        checksrc110("-source 10", "-target 10");
        checksrc110("-target 1.10");
        checksrc110("-target 10");

        fail("-source 7", "-target 1.6", "Base.java");
        fail("-source 8", "-target 1.6", "Base.java");
        fail("-source 8", "-target 1.7", "Base.java");
        fail("-source 9", "-target 1.7", "Base.java");
        fail("-source 9", "-target 1.8", "Base.java");
        fail("-source 10", "-target 1.7", "Base.java");
        fail("-source 10", "-target 1.8", "Base.java");

        fail("-source 1.5", "-target 1.5", "Base.java");
        fail("-source 1.4", "-target 1.4", "Base.java");
        fail("-source 1.3", "-target 1.3", "Base.java");
        fail("-source 1.2", "-target 1.2", "Base.java");

        if (failedCases > 0) {
            System.err.println("failedCases = " + String.valueOf(failedCases));
            throw new Error("Test failed");
        }

    }

    protected void printargs(String fname,String... args) {
        System.out.printf("test: %s", fname);
        for (String onearg : args) {
            System.out.printf(" %s", onearg);
        }
        System.out.printf("\n", fname);
    }

    protected void check_source_target(String... args) {
        printargs("check_source_target", args);
        check_target(args[0], args[1], args[2]);
        check_target(args[0], "1." + args[1], args[2]);
    }

    protected void check_target(String... args) {
        check(args[0], "-source " + args[1], "-target " + args[2]);
        check(args[0], "-source " + args[1], "-target 1." + args[2]);
    }

    protected void check(String major, String... args) {
        printargs("check", args);
        List<String> jcargs = new ArrayList<String>();
        jcargs.add("-Xlint:-options");

        // add in args conforming to List requrements of JavaCompiler
        for (String onearg : args) {
            String[] fields = onearg.split(" ");
            for (String onefield : fields) {
                jcargs.add(onefield);
            }
        }

        boolean creturn = compile("Base.java", jcargs);
        if (!creturn) {
            // compilation errors note and return.. assume no class file
            System.err.println("check: Compilation Failed");
            System.err.println("\t classVersion:\t" + major);
            System.err.println("\t arguments:\t" + jcargs);
            failedCases++;

        } else if (!checkClassFileVersion("Base.class", major)) {
            failedCases++;
        }
    }

    protected void checksrc16(String... args) {
        printargs("checksrc16", args);
        int asize = args.length;
        String[] newargs = new String[asize + 1];
        System.arraycopy(args, 0, newargs, 0, asize);
        newargs[asize] = "Base.java";
        pass(newargs);
        newargs[asize] = "New17.java";
        fail(newargs);
    }

    protected void checksrc17(String... args) {
        printargs("checksrc17", args);
        int asize = args.length;
        String[] newargs = new String[asize+1];
        System.arraycopy(args, 0, newargs,0 , asize);
        newargs[asize] = "New17.java";
        pass(newargs);
        newargs[asize] = "New18.java";
        fail(newargs);
    }

    protected void checksrc18(String... args) {
        printargs("checksrc18", args);
        int asize = args.length;
        String[] newargs = new String[asize+1];
        System.arraycopy(args, 0, newargs,0 , asize);
        newargs[asize] = "New17.java";
        pass(newargs);
        newargs[asize] = "New18.java";
        pass(newargs);
    }

    protected void checksrc19(String... args) {
        printargs("checksrc19", args);
        checksrc18(args);
    }

    protected void checksrc110(String... args) {
        printargs("checksrc110", args);
        checksrc19(args);
    }

    protected void pass(String... args) {
        printargs("pass", args);

        List<String> jcargs = new ArrayList<String>();
        jcargs.add("-Xlint:-options");

        // add in args conforming to List requrements of JavaCompiler
        for (String onearg : args) {
            String[] fields = onearg.split(" ");
            for (String onefield : fields) {
                jcargs.add(onefield);
            }
        }

        // empty list is error
        if (jcargs.isEmpty()) {
            System.err.println("error: test error in pass() - No arguments");
            System.err.println("\t arguments:\t" + jcargs);
            failedCases++;
            return;
        }

        // the last argument is the filename *.java
        String filename = jcargs.get(jcargs.size() - 1);
        jcargs.remove(jcargs.size() - 1);

        boolean creturn = compile(filename, jcargs);
        // expect a compilation failure, failure if otherwise
        if (!creturn) {
            System.err.println("pass: Compilation erroneously failed");
            System.err.println("\t arguments:\t" + jcargs);
            System.err.println("\t file     :\t" + filename);
            failedCases++;

        }

    }

    protected void fail(String... args) {
        printargs("fail", args);

        List<String> jcargs = new ArrayList<String>();
        jcargs.add("-Xlint:-options");

        // add in args conforming to List requrements of JavaCompiler
        for (String onearg : args) {
            String[] fields = onearg.split(" ");
            for (String onefield : fields) {
                jcargs.add(onefield);
            }
        }

        // empty list is error
        if (jcargs.isEmpty()) {
            System.err.println("error: test error in fail()- No arguments");
            System.err.println("\t arguments:\t" + jcargs);
            failedCases++;
            return;
        }

        // the last argument is the filename *.java
        String filename = jcargs.get(jcargs.size() - 1);
        jcargs.remove(jcargs.size() - 1);

        boolean creturn = compile(filename, jcargs);
        // expect a compilation failure, failure if otherwise
        if (creturn) {
            System.err.println("fail: Compilation erroneously succeeded");
            System.err.println("\t arguments:\t" + jcargs);
            System.err.println("\t file     :\t" + filename);
            failedCases++;
        }
    }

    protected boolean compile(String sourceFile, List<String>options) {
        JavaCompiler.CompilationTask jctask;
        try (StandardJavaFileManager fm = javacompiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects(sourceFile);

            jctask = javacompiler.getTask(
                null,    // Writer
                fm,      // JavaFileManager
                null,    // DiagnosticListener
                options, // Iterable<String>
                null,    // Iterable<String> classes
                files);  // Iterable<? extends JavaFileObject>

            try {
                return jctask.call();
            } catch (IllegalStateException e) {
                System.err.println(e);
                return false;
            }
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    protected void writeSourceFile(String fileName, String body) throws IOException{
        try (Writer fw = new FileWriter(fileName)) {
            fw.write(body);
        }
    }

    protected void genSourceFiles() throws IOException{
        /* Create a file that executes with all supported versions. */
        writeSourceFile("Base.java","public class Base { }\n");

        /*
         * Create a file with a new feature in 1.7, not in 1.6 : "<>"
         */
        writeSourceFile("New17.java",
            "import java.util.List;\n" +
            "import java.util.ArrayList;\n" +
            "class New17 { List<String> s = new ArrayList<>(); }\n"
        );

        /*
         * Create a file with a new feature in 1.8, not in 1.7 : lambda
         */
        writeSourceFile("New18.java",
            "public class New18 { \n" +
            "    void m() { \n" +
            "    new Thread(() -> { }); \n" +
            "    } \n" +
            "} \n"
        );

    }

    protected boolean checkClassFileVersion
        (String filename,String classVersionNumber) {
        ByteBuffer bb = ByteBuffer.allocate(1024);
        try (FileChannel fc = new FileInputStream(filename).getChannel()) {
            bb.clear();
            if (fc.read(bb) < 0)
                throw new IOException("Could not read from file : " + filename);
            bb.flip();
            int minor = bb.getShort(4);
            int major = bb.getShort(6);
            String fileVersion = major + "." + minor;
            if (fileVersion.equals(classVersionNumber)) {
                return true;
            } else {
                System.err.println("checkClassFileVersion : Failed");
                System.err.println("\tclassfile version mismatch");
                System.err.println("\texpected : " + classVersionNumber);
                System.err.println("\tfound    : " + fileVersion);
                return false;
            }
        }
        catch (IOException e) {
            System.err.println("checkClassFileVersion : Failed");
            System.err.println("\terror :\t" + e.getMessage());
            System.err.println("\tfile:\tfilename");
        }
        return false;
    }
}

