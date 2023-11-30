/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4981566 5028634 5094412 6304984 7025786 7025789 8001112 8028545
 * 8000961 8030610 8028546 8188870 8173382 8173382 8193290 8205619 8028563
 * 8245147 8245586 8257453 8286035 8306586 8320806
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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/*
 * If not explicitly specified the latest source and latest target
 * values are the defaults. If explicitly specified, the target value
 * has to be greater than or equal to the source value.
 */
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

    public static final Set<String> RETIRED_SOURCES =
        Set.of("1.2", "1.3", "1.4", "1.5", "1.6", "1.7");

    public static final Set<String> VALID_SOURCES =
        Set.of("1.8", "1.9", "1.10", "11", "12", "13", "14",
               "15", "16", "17", "18", "19", "20", "21", "22");

    public static final String LATEST_MAJOR_VERSION = "66.0";

    static enum SourceTarget {
        EIGHT(true,      "52.0",  "8"),
        NINE(true,       "53.0",  "9"),
        TEN(true,        "54.0", "10"),
        ELEVEN(false,    "55.0", "11"),
        TWELVE(false,    "56.0", "12"),
        THIRTEEN(false,  "57.0", "13"),
        FOURTEEN(false,  "58.0", "14"),
        FIFTEEN(false,   "59.0", "15"),
        SIXTEEN(false,   "60.0", "16"),
        SEVENTEEN(false, "61.0", "17"),
        EIGHTEEN(false,  "62.0", "18"),
        NINETEEN(false,  "63.0", "19"),
        TWENTY(false,    "64.0", "20"),
        TWENTY_ONE(false,"65.0", "21"),
        TWENTY_TWO(false,"66.0", "22"),
        ; // Reduce code churn when appending new constants

        private final boolean dotOne;
        private final String classFileVer;
        private final String target;
        private final int intTarget;

        private SourceTarget(boolean dotOne, String classFileVer, String target) {
            this.dotOne = dotOne;
            this.classFileVer = classFileVer;
            this.target = target;
            this.intTarget = Integer.parseInt(target);
        }

        public void checksrc(Versions versions, List<String> args) {
            // checker.accept(version, args);
            versions.printargs("checksrc" + target, args);
            List<String> expectedPassFiles = new ArrayList<>();
            List<String> expectedFailFiles = new ArrayList<>();

            for (SourceExample srcEg : SourceExample.values()) {
                var x = (srcEg.sourceLevel <= this.intTarget) ?
                    expectedPassFiles.add(srcEg.fileName()):
                    expectedFailFiles.add(srcEg.fileName());
            }

            versions.expectedPass(args, expectedPassFiles);
            versions.expectedFail(args, expectedFailFiles);
        }

        public boolean dotOne() {
            return dotOne;
        }

        public String classFileVer() {
            return classFileVer;
        }

        public String target() {
            return target;
        }

        public int intTarget() {
            return intTarget;
        }
    }

    void run() {
        String TC = "";
        System.out.println("Version.java: Starting");

        check(LATEST_MAJOR_VERSION);
        for (String source : VALID_SOURCES) {
            check(LATEST_MAJOR_VERSION, List.of("-source " + source));
        }

        // Verify that a -source value less than a -target value is
        // accepted and that the resulting class files are dependent
        // on the target setting alone.
        SourceTarget[] sourceTargets = SourceTarget.values();
        for (int i = 0; i < sourceTargets.length; i++) {
            SourceTarget st = sourceTargets[i];
            String classFileVer = st.classFileVer();
            String target = st.target();
            boolean dotOne = st.dotOne();
            check_source_target(dotOne, List.of(classFileVer, target, target));
            for (int j = i - 1; j >= 0; j--) {
                String source = sourceTargets[j].target();
                check_source_target(dotOne, List.of(classFileVer, source, target));
            }
        }

        // Verify acceptance of different combinations of -source N,
        // -target M; N <= M
        for (int i = 0; i < sourceTargets.length; i++) {
            SourceTarget st = sourceTargets[i];

            st.checksrc(this, List.of("-source " + st.target()));
            st.checksrc(this, List.of("-source " + st.target(), "-target " + st.target()));

            if (st.dotOne()) {
                st.checksrc(this, List.of("-source 1." + st.target()));
                st.checksrc(this, List.of("-source 1." + st.target(), "-target 1." + st.target()));
            }

            if (i == sourceTargets.length - 1) {
                // Can use -target without -source setting only for
                // most recent target since the most recent source is
                // the default.
                st.checksrc(this, List.of("-target " + st.target()));

                if (!st.classFileVer().equals(LATEST_MAJOR_VERSION)) {
                    throw new RuntimeException(st +
                                               "does not have class file version" +
                                               LATEST_MAJOR_VERSION);
                }
            }
        }

        // Verify that -source N -target (N-1) is rejected
        for (int i = 1 /* Skip zeroth value */; i < sourceTargets.length; i++) {
            fail(List.of("-source " + sourceTargets[i].target(),
                 "-target " + sourceTargets[i-1].target(),
                         "Base.java"));
        }

        // Previously supported source/target values
        for (String source  : RETIRED_SOURCES) {
            fail(List.of("-source " + source, "-target " + source, "Base.java"));
        }

        if (failedCases > 0) {
            System.err.println("failedCases = " + String.valueOf(failedCases));
            throw new Error("Test failed");
        }

    }

    protected void printargs(String fname, List<String> args) {
        System.out.printf("test: %s", fname);
        for (String onearg : args) {
            System.out.printf(" %s", onearg);
        }
        System.out.printf("\n", fname);
    }

    protected void check_source_target(boolean dotOne, List<String> args) {
        printargs("check_source_target", args);
        check_target(dotOne, List.of(args.get(0), args.get(1), args.get(2)));
        if (dotOne) {
            check_target(dotOne, List.of(args.get(0), "1." + args.get(1), args.get(2)));
        }
    }

    protected void check_target(boolean dotOne, List<String> args) {
        check(args.get(0), List.of("-source " + args.get(1), "-target " + args.get(2)));
        if (dotOne) {
            check(args.get(0), List.of("-source " + args.get(1), "-target 1." + args.get(2)));
        }
    }

    protected void check(String major) {
        check(major, List.of());
    }

    protected void check(String major, List<String> args) {
        printargs("check", args);
        List<String> jcargs = new ArrayList<>();
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

    /**
     * The BASE source example is expected to compile on all source
     * levels. Otherwise, an example is expected to compile on its
     * declared source level and later, but to _not_ compile on
     * earlier source levels. (This enum is _not_ intended to capture
     * the uncommon program that is accepted in one version of the
     * language and rejected in a later version.)
     *
     * When a version of the language gets a new, non-preview feature,
     * a new source example enum constant should be added.
     */
    enum SourceExample {
        BASE(7, "Base.java", "public class Base { }\n"),


        SOURCE_8(8, "New8.java",
            // New feature in 8: lambda
            """
            public class New8 {
                void m() {
                    new Thread(() -> { });
                }
            }
             """),

        SOURCE_10(10, "New10.java",
            // New feature in 10: var
            """
            public class New10 {
                void m() {
                    var tmp = new Thread(() -> { });
                }
            }
            """),

        SOURCE_11(11, "New11.java",
            // New feature in 11: var for lambda parameters
            """
            public class New11 {
                static java.util.function.Function<String,String> f = (var x) -> x.substring(0);
                void m(String name) {
                    var tmp = new Thread(() -> { }, f.apply(name));
                }
            }
            """),

         SOURCE_14(14, "New14.java",
             // New feature in 14: text blocks
             """
             public class New14 {
                 static {
                     int i = 5;
                     System.out.println(
                         switch(i) {
                             case 0 -> false;
                             default -> true;
                         }
                     );
                 }
             }
             """),

         SOURCE_15(15, "New15.java",
             // New feature in 15: text blocks
             """
             public class New15 {
                 public static final String s =
                 \"\"\"
                 Hello, World.
                 \"\"\"
                 ;
             }
             """),

         SOURCE_16(16, "New16.java",
             // New feature in 16: records
             """
             public class New16 {
                 public record Record(double rpm) {
                     public static final Record LONG_PLAY = new Record(100.0/3.0);
                 }
             }
             """),

         SOURCE_17(17, "New17.java",
             // New feature in 17: sealed classes
             """
             public class New17 {
                 public static sealed class Seal {}

                 public static final class Pinniped extends Seal {}
                 public static final class TaperedThread extends Seal {}
                 public static final class Wax extends Seal {}
             }
             """),

         SOURCE_21(21, "New21.java",
             // New feature in 21: pattern matching for switch
             """
             public class New21 {
                 public static void main(String... args) {
                     Object o = new Object(){};

                     System.out.println(switch (o) {
                                        case Integer i -> String.format("%d", i);
                                        default        -> o.toString();
                                        });
                 }
             }
             """),

         SOURCE_22(22, "New22.java",
             // New feature in 22: Unnamed Variables & Patterns
             """
             public class New22 {
                 public static void main(String... args) {
                     Object o = new Object(){};

                     System.out.println(switch (o) {
                                        case Integer _ -> "Hello world.";
                                        default        -> o.toString();
                                        });
                 }
             }
             """),
            ; // Reduce code churn when appending new constants

        private int sourceLevel;
        private String fileName;
        private String fileContents;

        private SourceExample(int sourceLevel, String fileName, String fileContents) {
            this.sourceLevel = sourceLevel;
            this.fileName = fileName;
            this.fileContents = fileContents;
        }

        public String fileName() {return fileName;}
        public String fileContents() {return fileContents;}
    }

    protected void expected(List<String> args, List<String> fileNames,
                            Consumer<List<String>> passOrFail) {
        ArrayList<String> fullArguments = new ArrayList<>(args);
        // Issue compile with each filename in turn.
        for(String fileName : fileNames) {
            fullArguments.add(fileName);
            passOrFail.accept(fullArguments);
            fullArguments.remove(fullArguments.size() - 1);
        }
    }

    protected void expectedPass(List<String> args, List<String> fileNames) {
        expected(args, fileNames, this::pass);
    }

    protected void expectedFail(List<String> args, List<String> fileNames) {
        expected(args, fileNames, this::fail);
    }

    protected void pass(List<String> args) {
        printargs("pass", args);

        List<String> jcargs = new ArrayList<>();
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

    protected void fail(List<String> args) {
        printargs("fail", args);

        List<String> jcargs = new ArrayList<>();
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

    protected boolean compile(String sourceFile, List<String> options) {
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
        for (SourceExample srcEg : SourceExample.values()) {
            writeSourceFile(srcEg.fileName(), srcEg.fileContents());
        }
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

