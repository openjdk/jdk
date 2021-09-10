/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8233674
 * @summary test FileNotFoundException doesn't occur in open non-shared file
 * @library /test/lib
 * @requires os.family == "windows"
 */

import java.io.File;
import java.net.URL;
import java.net.JarURLConnection;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import com.sun.nio.file.ExtendedOpenOption;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class RetryCreateFile {
    private static final String DONE_MSG = "done";

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if (args[0].endsWith(".jar")) {
                URL url = new URL("jar:file:" + args[0].replace('\\', '/') + "!/");
                JarURLConnection juc = (JarURLConnection) url.openConnection();
                juc.getJarFile();
            }
            System.out.println(DONE_MSG);
            return;
        }
        // else this is the main test
        runtests();
    }

    public static void runtests() throws Exception {
        // prepare
        String fs = System.getProperty("file.separator");
        String currentDir = System.getProperty("user.dir");

        Path clsFile = Paths.get(RetryCreateFile.class.getResource("RetryCreateFile.class").toURI());
        Path tmpClsFile = Paths.get("RetryCreateFile.class");
        Files.copy(clsFile, tmpClsFile); // copy class to current dir
        String tmpJar = currentDir + fs + "tmp.jar";
        JarUtils.createJar(tmpJar, tmpClsFile.toString());

        String subPath = null;
        final int MAX_PATH = 260;
        int subDirLen = MAX_PATH - currentDir.length() - 2;
        if (subDirLen > 0) {
            char[] subchars = new char[subDirLen];
            Arrays.fill(subchars, 'x');
            subPath = new String(subchars);
            Path destDir = Paths.get(currentDir, subPath);
            Files.createDirectories(destDir);
        }
        String tmpJar2 = currentDir + fs + (subPath != null ? (subPath + fs) : "") + "tmp.jar";
        JarUtils.createJar(tmpJar2, tmpClsFile.toString());

        // 1st case: zip file
        RetryCreateFile test1 = new RetryCreateFile(tmpJar, "java.io.FileNotFoundException");
        test1.testWithOpenedFile();
        test1.testWithReleaseFile();
        System.out.println("1st case OK\n");

        RetryCreateFile test1_2 = new RetryCreateFile(tmpJar2, "java.io.FileNotFoundException");
        test1_2.testWithOpenedFile();
        test1_2.testWithReleaseFile();
        System.out.println("1st case (long path) OK\n");

        // 2nd case: load class from jar file
        RetryCreateFile test2 = new RetryCreateFile(tmpJar, tmpJar, "java.lang.ClassNotFoundException");
        test2.testWithOpenedFile();
        test2.testWithReleaseFile();
        System.out.println("2nd case OK\n");

        RetryCreateFile test2_2 = new RetryCreateFile(tmpJar2, tmpJar2, "java.lang.ClassNotFoundException");
        test2_2.testWithOpenedFile();
        test2_2.testWithReleaseFile();
        System.out.println("2nd case (long path) OK\n");

        System.out.println("\nALL TESTS PASSED");
    }

    private String clsPath;
    private String targetFile;
    private String errorMessage;

    public RetryCreateFile(String targetFile, String errorMessage) {
        this(null, targetFile, errorMessage);
    }

    public RetryCreateFile(String clsPath, String targetFile, String errorMessage) {
        this.clsPath = clsPath;
        this.targetFile = targetFile;
        this.errorMessage = errorMessage;
    }

    private Process startJavaProcess() throws Exception {
        // see ProcessTools#createJavaProcessBuilder
        ArrayList<String> args = new ArrayList<>();
        args.add(JDKToolFinder.getJDKTool("java"));
        args.add("-cp");
        if (clsPath == null) {
            args.add(System.getProperty("java.class.path"));
        } else {
            args.add(clsPath);
        }
        args.add(RetryCreateFile.class.getName());
        args.add(targetFile);

        // Reporting
        StringBuilder cmdLine = new StringBuilder();
        for (String cmd : args)
            cmdLine.append(cmd).append(' ');
        System.out.println("Command line: [" + cmdLine.toString() + "]");

        ProcessBuilder pb = new ProcessBuilder(args);
        Process javaProcess = pb.start();
        return javaProcess;
    }

    public void testWithOpenedFile() throws Exception {
        FileChannel channel = null;
        try {
            System.out.println("opened: " + targetFile);
            channel = FileChannel.open(new File(targetFile).toPath(), ExtendedOpenOption.NOSHARE_READ,
                    ExtendedOpenOption.NOSHARE_WRITE, ExtendedOpenOption.NOSHARE_DELETE);
            Process javaProcess = startJavaProcess();
            OutputAnalyzer javaOutput = new OutputAnalyzer(javaProcess);
            javaOutput.shouldNotContain(DONE_MSG);
            javaOutput.shouldContain(errorMessage);
            javaOutput.reportDiagnosticSummary();
        } finally {
            System.out.println("closed: " + targetFile);
            if (channel != null) {
                channel.close();
            }
        }
        System.out.println("testWithOpenedFile: OK\n");
    }

    public void testWithReleaseFile() throws Exception {
        FileChannel channel = null;
        int sleepTime = 1000;
        try {
            System.out.println("opened: " + targetFile);
            channel = FileChannel.open(new File(targetFile).toPath(), ExtendedOpenOption.NOSHARE_READ,
                    ExtendedOpenOption.NOSHARE_WRITE, ExtendedOpenOption.NOSHARE_DELETE);
            Process javaProcess = startJavaProcess();
            System.out.println("sleep: " + sleepTime + " msec");
            Thread.sleep(sleepTime);
            System.out.println("closed: " + targetFile);
            channel.close();
            OutputAnalyzer javaOutput = new OutputAnalyzer(javaProcess);
            javaOutput.shouldContain(DONE_MSG);
            javaOutput.shouldNotContain(errorMessage);
            javaOutput.reportDiagnosticSummary();
        } finally {
            if (channel != null) {
                channel.close();
            }
        }
        System.out.println("testWithReleaseFile: OK\n");
    }
}
