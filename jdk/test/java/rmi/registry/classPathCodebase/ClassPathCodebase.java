/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4242317
 * @summary When a class that can be found in the CLASSPATH of the rmiregistry
 * tool is marshalled via RMI, it should be annotated with the value of the
 * java.rmi.server.codebase property, not the list of "file:" URLs for the
 * actual elements of the CLASSPATH.
 * @author Peter Jones
 *
 * @library ../../testlibrary
 * @build ClassPathCodebase Dummy TestLibrary
 * @run main/othervm/policy=security.policy ClassPathCodebase
 */

import java.io.*;
import java.net.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Arrays;

public class ClassPathCodebase {

    /** wait 10 seconds for the registry process to be ready to call */
    private final static long REGISTRY_WAIT = 15000;

    private final static String dummyClassName = "Dummy";

    private final static String dummyBinding = "DummyObject";

    private final static String importCodebase = "codebase_IMPORT_";
    private final static String exportCodebase = "codebase_EXPORT_";

    public static void main(String[] args) {

        System.err.println("\nRegression test for bug 4242317\n");

        TestLibrary.suggestSecurityManager("java.lang.SecurityManager");

        Process rmiregistry = null;

        try {
            /*
             * Install a dummy class in two codebases: one that will be in
             * the rmiregistry's CLASSPATH (the "import" codebase) and one
             * that will be in the rmiregistry's "java.rmi.server.codebase"
             * property (the "export" codebase).
             */
            URL importCodebaseURL = TestLibrary.installClassInCodebase(
                dummyClassName, importCodebase, false);
            URL exportCodebaseURL = TestLibrary.installClassInCodebase(
                dummyClassName, exportCodebase, true);

            /*
             * Spawn an rmiregistry in the "import" codebase directory.
             */
            File rmiregistryDir =
                new File(System.getProperty("user.dir", "."), importCodebase);

            String rmiregistryCommand =
                System.getProperty("java.home") + File.separator +
                "bin" + File.separator + "rmiregistry";

            int port = TestLibrary.getUnusedRandomPort();
            String cmdarray[] = new String[] {
                rmiregistryCommand,
                "-J-Denv.class.path=.",
                "-J-Djava.rmi.server.codebase=" + exportCodebaseURL,
                Integer.toString(port) };

            System.err.println("\nCommand used to spawn rmiregistry process:");
            System.err.println("\t" + Arrays.asList(cmdarray).toString());

            rmiregistry = Runtime.getRuntime().exec(cmdarray, null, rmiregistryDir);

            // pipe rmiregistry output to our output, for debugging failures
            StreamPipe.plugTogether(rmiregistry.getInputStream(), System.err);
            StreamPipe.plugTogether(rmiregistry.getErrorStream(), System.err);

            /*
             * Wait for the registry to initialize and be ready to call.
             */
            Thread.sleep(REGISTRY_WAIT);
            System.err.println();

            /*
             * Create an instance of the dummy class, finding it from the
             * "import" codebase.
             */
            ClassLoader loader = URLClassLoader.newInstance(
                new URL[] { importCodebaseURL });
            Class dummyClass = Class.forName(dummyClassName, false, loader);
            Remote dummyObject = (Remote) dummyClass.newInstance();

            /*
             * Find the registry that we created and bind the
             * dummy object to it.
             */
            Registry registry = LocateRegistry.getRegistry(
                "localhost", port);

            try {
                registry.bind(dummyBinding, dummyObject);
                System.err.println("Bound dummy object in registry");
            } catch (java.rmi.ConnectException e) {
                System.err.println("Error: rmiregistry not started in time");
                throw e;
            } catch (ServerException e) {
                if (e.detail instanceof UnmarshalException &&
                    ((UnmarshalException) e.detail).detail instanceof
                        ClassNotFoundException)
                {
                    System.err.println(
                        "Error: another registry running on port " +
                        port + "?");
                }
                throw e;
            }

            /*
             * Look up the dummy object from our registry and make sure
             * that its class was annotated with the "export" codebase.
             */
            Remote dummyLookup = registry.lookup(dummyBinding);
            System.err.println(
                "Looked up dummy object from registry: " + dummyLookup);
            Class dummyLookupClass = dummyLookup.getClass();
            String dummyLookupAnnotation =
                RMIClassLoader.getClassAnnotation(dummyLookupClass);
            System.err.println(
                "Class annotation from registry: " + dummyLookupAnnotation);

            System.err.println();
            if (dummyLookupAnnotation.indexOf(exportCodebase) >= 0) {
                System.err.println("TEST PASSED");
            } else if (dummyLookupAnnotation.indexOf(importCodebase) >= 0) {
                throw new RuntimeException(
                    "rmiregistry annotated with CLASSPATH element URL");
            } else {
                throw new RuntimeException(
                    "rmiregistry used unexpected annotation: \"" +
                    dummyLookupAnnotation + "\"");
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("TEST FAILED: " + e.toString());
        } finally {
            if (rmiregistry != null) {
                rmiregistry.destroy();
            }
        }
    }
}
