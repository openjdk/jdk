/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
/* @test
 * @bug 4359123
 * @summary  Test loading of classes with # in the path
 * @library /test/lib
 * @build jdk.test.lib.process.ProcessTools
 * @run main EscapePath
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class EscapePath {

    private static String testPath;

    static {
        testPath = System.getProperty("test.src");
        if (testPath == null)
            testPath = "";
        else
            testPath = testPath + File.separator;
    }

    public static void main(String[] args) throws Exception {
        createTestDir();
        copyClassFile();
        invokeJava();
        eraseTestDir();
    }

    private static void createTestDir() throws Exception {
        File testDir = new File("a#b");
        boolean result = testDir.mkdir();
    }

    private static void eraseTestDir() throws Exception {
        File classFile = new File("a#b/Hello.class");
        classFile.delete();
        File testDir = new File("a#b");
        testDir.delete();
    }

    private static void copyClassFile() throws Exception {
        FileInputStream fis = new FileInputStream(testPath + "Hello.class");
        FileOutputStream fos = new FileOutputStream("a#b/Hello.class");

        int bytesRead;
        byte buf[] = new byte[410];
        do {
            bytesRead = fis.read(buf);
            if (bytesRead > 0)
                fos.write(buf, 0, bytesRead);
        } while (bytesRead != -1);
        fis.close();
        fos.flush();
        fos.close();
    }

    private static void invokeJava() {
        List<String> commands = new ArrayList<>();

        commands.add("-classpath");
        commands.add("a#b");
        commands.add("Hello");
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(commands);

        try {
            OutputAnalyzer outputAnalyzer = ProcessTools.executeProcess(pb);
            outputAnalyzer.shouldHaveExitValue(0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
