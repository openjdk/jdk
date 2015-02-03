/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8065957
 * @library ../../../../java/rmi/testlibrary
 * @build TestLibrary
 * @summary Compiles a PortableRemoteObject with rmic -iiop and ensures that stub and tie classes are generated.
 * @run main IIOPCompilation
 * @author Felix Yang
 *
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

import javax.rmi.PortableRemoteObject;

public class IIOPCompilation {

    public static void main(String args[]) throws IOException, InterruptedException {
        IIOPCompilation test = new IIOPCompilation();
        test.doTest();
    }

    private void doTest() throws IOException, InterruptedException {
        String className = DummyImpl.class.getName();
        int exitCode = runRmic(className);
        if (exitCode != 0) {
            throw new RuntimeException("Rmic failed. The exit code is " + exitCode);
        }

        // Check the stub class generated correctly
        String stubFile = "_" + Dummy.class.getName() + "_Stub.class";
        assertFileExists(stubFile);

        // Check the tie class generated correctly
        String tieFile = "_" + className + "_Tie.class";
        assertFileExists(tieFile);
    }

    private void assertFileExists(String fileName) throws FileNotFoundException {
        if (!new File(fileName).exists()) {
            throw new FileNotFoundException(fileName + " doesn't exist!");
        }
    }

    private int runRmic(String classname) throws IOException, InterruptedException {
        String rmicProgramStr = TestLibrary.getProperty("java.home", "") + File.separator + "bin" + File.separator + "rmic";
        String testClasses = TestLibrary.getProperty("test.classes", "");
        List<String> command = Arrays.asList(rmicProgramStr, "-iiop", "-classpath", testClasses, classname);
        System.out.println("Running command: " + command);

        Process p = null;
        try {
            p = new ProcessBuilder(command).inheritIO().start();
            p.waitFor();
            return p.exitValue();
        } finally {
            if (p != null && p.isAlive()) {
                p.destroy();
            }
        }
    }
}

interface Dummy extends java.rmi.Remote {
}

class DummyImpl extends PortableRemoteObject implements Dummy {
    public DummyImpl() throws RemoteException {
    }
}
