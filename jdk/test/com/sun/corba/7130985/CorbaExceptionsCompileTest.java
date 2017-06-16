/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7130985
 * @summary Four helper classes missing in Sun JDK
 * @library /lib/testlibrary /test/lib
 * @modules java.corba
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 *        jdk.testlibrary.*
 * @run main CorbaExceptionsCompileTest
 */

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;

import jdk.test.lib.util.FileUtils;
import jdk.testlibrary.JDKToolLauncher;

public class CorbaExceptionsCompileTest implements CorbaExceptionsTest {

    public CorbaExceptionsCompileTest() {
        super();
    }

    public void testExceptionInvalidName()
        throws java.rmi.RemoteException, InvalidName {}

    public void testExceptionBounds()
        throws java.rmi.RemoteException, Bounds {}

    public void testExceptionBadKind()
        throws java.rmi.RemoteException, BadKind {}

    public void testExceptionCorba_Bounds()
        throws java.rmi.RemoteException, org.omg.CORBA.Bounds {}

    public static void main(String[] args) throws Exception {
        final File f = new File(
            CorbaExceptionsCompileTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        System.out.println(f.getCanonicalPath());
        ProcessBuilder pb = new ProcessBuilder("ls", "-l");
        pb.directory(f);
        Process p = pb.start();
        p.waitFor();
        if (p.exitValue() == 0) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ( (line = br.readLine()) != null) {
                    builder.append(line + "\n");
                }
                String result = builder.toString();
                System.out.println(result);
            }
        }

        Path outDir = Paths.get("CorbaExceptionsCompileTest-compiled");
        outDir = Files.createDirectory(outDir).toAbsolutePath();
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("rmic");
        launcher.addToolArg("-classpath").addToolArg(f.getCanonicalPath())
            .addToolArg("-d").addToolArg(outDir.toString())
            .addToolArg("-iiop").addToolArg("CorbaExceptionsCompileTest");

        pb = new ProcessBuilder(launcher.getCommand());
        pb.directory(f);
        System.out.println("Working Directory: " + pb.directory());
        System.out.println("CorbaExceptionsCompileTest.class exists: "
            + new File(f, "CorbaExceptionsCompileTest.class").exists());

        p = pb.start();
        p.waitFor();
        if (p.exitValue() != 0) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ( (line = br.readLine()) != null) {
                    builder.append(line + "\n");
                }
                String result = builder.toString();
                System.out.println(result);
                throw new RuntimeException(launcher.getCommand() +
                    " -iiop CorbaExceptionsCompileTest failed with status: "
                    + p.exitValue());
            }
        }

        if (Files.exists(outDir))
            FileUtils.deleteFileTreeWithRetry(outDir);
    }
}
