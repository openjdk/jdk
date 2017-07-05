/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6409194
 * @summary There should be no console output caused by the RMI
 * implementation's logging, except as explicitly configured in the
 * logging properties file, if none of the legacy sun.rmi.*.logLevel
 * system properties are set.
 *
 * @author Peter Jones
 *
 * @library ../../../../../java/rmi/testlibrary
 * @build JavaVM
 * @build NoConsoleOutput
 * @run main/othervm NoConsoleOutput
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class NoConsoleOutput {

    public static void main(String[] args) throws Exception {
        System.err.println("\nRegression test for bug 6409194\n");

        /*
         * Exdecute a subprocess VM that does a bunch of RMI activity
         * with a logging configuration file that does not specify a
         * ConsoleHandler and with no legacy sun.rmi.*.logLevel system
         * properties set.
         */
        String loggingPropertiesFile =
            System.getProperty("test.src", ".") +
            File.separatorChar + "logging.properties";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        JavaVM vm = new JavaVM(DoRMIStuff.class.getName(),
            "-Djava.util.logging.config.file=" + loggingPropertiesFile,
                               "", out, err);
        vm.start();
        vm.getVM().waitFor();

        /*
         * Verify that the subprocess had no System.out or System.err
         * output.
         */
        String outString = out.toString();
        String errString = err.toString();

        System.err.println("-------- subprocess standard output: --------");
        System.err.print(out);
        System.err.println("-------- subprocess standard error:  --------");
        System.err.print(err);
        System.err.println("---------------------------------------------");

        if (outString.length() > 0 || errString.length() > 0) {
            throw new Error("TEST FAILED: unexpected subprocess output");
        }

        System.err.println("TEST PASSED");
    }

    public static class DoRMIStuff {
        private static final int PORT = 2020;
        private interface Foo extends Remote {
            Object echo(Object obj) throws RemoteException;
        }
        private static class FooImpl implements Foo {
            FooImpl() { }
            public Object echo(Object obj) { return obj; }
        }
        public static void main(String[] args) throws Exception {
            LocateRegistry.createRegistry(PORT);
            Registry reg = LocateRegistry.getRegistry("", PORT);
            FooImpl fooimpl = new FooImpl();
            UnicastRemoteObject.exportObject(fooimpl, 0);
            reg.rebind("foo", fooimpl);
            Foo foostub = (Foo) reg.lookup("foo");
            FooImpl fooimpl2 = new FooImpl();
            UnicastRemoteObject.exportObject(fooimpl2, 0);
            foostub.echo(fooimpl2);
            UnicastRemoteObject.unexportObject(fooimpl, true);
            UnicastRemoteObject.unexportObject(fooimpl2, true);
        }
    }
}
