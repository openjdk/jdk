/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package jdk.test.lib.cds;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.StringArrayUtils;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.spi.ToolProvider;

public class CDSJarUtils {
    // to turn DEBUG on via command line: -DCDSJarUtils.DEBUG=[true, TRUE]
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("CDSJarUtils.DEBUG", "false"));
    private static final String classDir = System.getProperty("test.classes");
    private static final ToolProvider JAR = ToolProvider.findFirst("jar")
        .orElseThrow(() -> new RuntimeException("ToolProvider for jar not found"));

    public static String getJarFilePath(String jarName) {
        return CDSTestUtils.getOutputDir() +  File.separator + jarName + ".jar";
    }

    // jar all files under dir, with manifest file man, with an optional versionArgs
    // for generating a multi-release jar.
    // The jar command is as follows:
    // jar cmf \
    //  <path to output jar> <path to the manifest file>\
    //   -C <path to the base classes> .\
    //    --release 9 -C <path to the versioned classes> .
    // the last line begins with "--release" corresponds to the optional versionArgs.
    public static String build(String jarName, File dir, String man, String ...versionArgs)
        throws Exception {
        ArrayList<String> args = new ArrayList<String>();
        if (man != null) {
            args.add("cfm");
        } else {
            args.add("cf");
        }
        String jarFile = getJarFilePath(jarName);
        args.add(jarFile);
        if (man != null) {
            args.add(man);
        }
        args.add("-C");
        args.add(dir.getAbsolutePath());
        args.add(".");
        for (String verArg : versionArgs) {
            args.add(verArg);
        }
        createJar(args);
        return jarFile;
    }

    public static String build(String jarName, String ...classNames)
        throws Exception {

        return createSimpleJar(classDir, getJarFilePath(jarName), classNames);
    }

    public static String build(boolean classesInWorkDir, String jarName, String ...classNames)
        throws Exception {
        if (classesInWorkDir) {
            return createSimpleJar(".", getJarFilePath(jarName), classNames);
        } else {
            return build(jarName, classNames);
        }
    }


    public static String buildWithManifest(String jarName, String manifest,
        String jarClassesDir, String ...classNames) throws Exception {
        String jarPath = getJarFilePath(jarName);
        ArrayList<String> args = new ArrayList<String>();
        args.add("cvfm");
        args.add(jarPath);
        args.add(System.getProperty("test.src") + File.separator + "test-classes"
            + File.separator + manifest);
        addClassArgs(args, jarClassesDir, classNames);
        createJar(args);

        return jarPath;
    }


    // Execute: jar uvf $jarFile -C $dir .
    public static void update(String jarFile, String dir) throws Exception {
        String jarExe = JDKToolFinder.getJDKTool("jar");

        ArrayList<String> args = new ArrayList<>();
        args.add(jarExe);
        args.add("uvf");
        args.add(jarFile);
        args.add("-C");
        args.add(dir);
        args.add(".");

        executeProcess(args.toArray(new String[1]));
    }

    private static String createSimpleJar(String jarclassDir, String jarName,
        String[] classNames) throws Exception {

        ArrayList<String> args = new ArrayList<String>();
        args.add("cf");
        args.add(jarName);
        addClassArgs(args, jarclassDir, classNames);
        createJar(args);

        return jarName;
    }

    private static void addClassArgs(ArrayList<String> args, String jarclassDir,
        String[] classNames) {

        for (String name : classNames) {
            args.add("-C");
            args.add(jarclassDir);
            args.add(name + ".class");
        }
    }

    /*
     * This class is for passing extra options to the "jar" command-line tool
     * for buildFromDirectory() and buildFromSourceDirectory().
     *
     * E.g.
     *
     * buildFromSourceDirectory("out.jar", "src", JarOptions.of().setMainClass("MyMainClass"),
     *                          "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
     */
    public static class JarOptions {
        private String [] options;
        private JarOptions() {}

        public static JarOptions of(String... options) {
            JarOptions jo = new JarOptions();
            jo.options = options;
            return jo;
        }

        public JarOptions setMainClass(String mainClass) {
            if (mainClass != null) {
                options = StringArrayUtils.concat(options, "--main-class=" + mainClass);
            }
            return this;
        }
        public JarOptions setManifest(String manifest) {
            if (manifest != null) {
                options = StringArrayUtils.concat(options, "--manifest=" + manifest);
            }
            return this;
        }
    }

    public static void buildFromDirectory(String jarPath,
                                          String classesDir) throws Exception {
        buildFromDirectory(jarPath, classesDir, null);
    }

    public static void buildFromDirectory(String jarPath,
                                          String classesDir,
                                          JarOptions jarOptions) throws Exception {
        ArrayList<String> argList = new ArrayList<String>();
        argList.add("--create");
        argList.add("--file=" + jarPath);
        if (jarOptions != null) {
            for (String s : jarOptions.options) {
                argList.add(s);
            }
        }
        argList.add("-C");
        argList.add(classesDir);
        argList.add(".");
        createJar(argList);
    }

    public static void buildFromSourceDirectory(String jarName, String srcDir, String... extraJavacArgs) throws Exception  {
        buildFromSourceDirectory(jarName, srcDir, null, extraJavacArgs);
    }

    /*
     * Compile all source files under srcDir using javac with extraJavacArgs. Package
     * all the classes into the specified JAR file
     */
    public static void buildFromSourceDirectory(String jarName, String srcDir, JarOptions jarOptions,
                                                String... extraJavacArgs) throws Exception
    {
        System.out.print("Compiling " + srcDir + " into " + jarName);
        if (extraJavacArgs.length > 0) {
            System.out.print(" with");
            for (String s : extraJavacArgs) {
                System.out.print(" " + s);
            }
        }
        System.out.println();

        Path dst = Files.createTempDirectory(Paths.get(""), "tmp-classes");

        if (!CompilerUtils.compile(Paths.get(srcDir), dst, extraJavacArgs)) {
            throw new RuntimeException("Compilation of " + srcDir + " failed");
        }

        CDSJarUtils.buildFromDirectory(jarName, dst.toString(), jarOptions);

        try {
            // Remove temp files to avoid clutter
            FileUtils.deleteFileTreeWithRetry(dst);
        } catch (Exception e) {
            // Might fail on Windows due to anti-virus. Just ignore
        }
    }

    private static void createJar(ArrayList<String> args) {
        if (DEBUG) printIterable("createJar args: ", args);

        if (JAR.run(System.out, System.err, args.toArray(new String[1])) != 0) {
            throw new RuntimeException("jar operation failed");
        }
    }

    // Many AppCDS tests use the same simple "hello.jar" which contains
    // simple Hello.class and does not specify additional attributes.
    // For this common use case, use this method to get the jar path.
    // The method will check if the jar already exists
    // (created by another test or test run), and will create the jar
    // if it does not exist
    public static String getOrCreateHelloJar() throws Exception {
        String jarPath = getJarFilePath("hello");

        File jarFile = new File(jarPath);
        if (jarFile.exists()) {
            return jarPath;
        } else {
            return build("hello", "Hello");
        }
    }

    public static void compile(String dstPath, String source, String... extraArgs) throws Exception {
        ArrayList<String> args = new ArrayList<String>();
        args.add(JDKToolFinder.getCompileJDKTool("javac"));
        args.add("-d");
        args.add(dstPath);
        if (extraArgs != null) {
            for (String s : extraArgs) {
                args.add(s);
            }
        }
        args.add(source);

        if (DEBUG) printIterable("compile args: ", args);

        ProcessBuilder pb = new ProcessBuilder(args);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }

    public static void compileModule(Path src,
                                     Path dest,
                                     String modulePathArg // arg to --module-path
                                     ) throws Exception {
        boolean compiled = false;
        if (modulePathArg == null) {
            compiled = CompilerUtils.compile(src, dest);
        } else {
            compiled = CompilerUtils.compile(src, dest,
                                           "--module-path", modulePathArg);
        }
        if (!compiled) {
            throw new RuntimeException("module did not compile");
        }
    }

    static final String keyTool = JDKToolFinder.getJDKTool("keytool");
    static final String jarSigner = JDKToolFinder.getJDKTool("jarsigner");

    public static void signJarWithDisabledAlgorithm(String jarName) throws Exception {
        String keyName = "key_with_disabled_alg";
        executeProcess(keyTool,
            "-genkey", "-keystore", "./keystore", "-alias", keyName,
            "-storepass", "abc123", "-keypass", "abc123", "-keyalg", "dsa",
            "-sigalg", "SHA1withDSA", "-keysize", "512", "-dname", "CN=jvmtest2")
            .shouldHaveExitValue(0);

        doSigning(jarName, keyName);
    }

    public static void signJar(String jarName) throws Exception {
        String keyName = "mykey";
        executeProcess(keyTool,
            "-genkey", "-keystore", "./keystore", "-alias", keyName,
            "-storepass", "abc123", "-keypass", "abc123", "-keyalg", "dsa",
            "-dname", "CN=jvmtest")
            .shouldHaveExitValue(0);

        doSigning(jarName, keyName);
    }

    private static void doSigning(String jarName, String keyName) throws Exception {
        executeProcess(jarSigner,
           "-keystore", "./keystore", "-storepass", "abc123", "-keypass",
           "abc123", "-signedjar", getJarFilePath("signed_" + jarName),
           getJarFilePath(jarName), keyName)
           .shouldHaveExitValue(0);
    }

    private static OutputAnalyzer executeProcess(String... cmds)
        throws Exception {

        printArray("executeProcess: ", cmds);
        return ProcessTools.executeProcess(new ProcessBuilder(cmds));
    }

    // diagnostic
    public static void printIterable(String msg, Iterable<String> l) {
        StringBuilder sum = new StringBuilder();
        for (String s : l) {
            sum.append(s).append(' ');
        }
        System.out.println(msg + sum.toString());
    }

    public static void printArray(String msg, String[] l) {
        StringBuilder sum = new StringBuilder();
        for (String s : l) {
            sum.append(s).append(' ');
        }
        System.out.println(msg + sum.toString());
    }
}
