/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4115696
 * @key intermittent
 * @summary synopsis: cannot use socket factories with Activatable objects
 * @author Ann Wollrath
 *
 * @library ../../../../testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 *          java.base/sun.nio.ch
 * @build TestLibrary Echo EchoImpl EchoImpl_Stub RMIDSelectorProvider
 * @run main/othervm/policy=security.policy/timeout=360 UseCustomSocketFactory
 */

import java.io.*;
import java.net.MalformedURLException;
import java.rmi.*;
import java.rmi.registry.*;

public class UseCustomSocketFactory {
    static int registryPort = -1;

    static String[] protocol = new String[] { "", "compress", "xor" };

    public static void main(String[] args) {

        System.out.println("\nRegression test for bug 4115696\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        try {
            Registry reg = LocateRegistry.createRegistry(0);
            registryPort = TestLibrary.getRegistryPort(reg);
        } catch (RemoteException e) {
            TestLibrary.bomb("creating registry", e);
        }

        RMID rmid = null;

        try {
            rmid = RMID.createRMIDOnEphemeralPort();
            rmid.addArguments(new String[] {
                "-C-Djava.security.policy=" +
                    TestParams.defaultGroupPolicy +
                    " -C-Djava.security.manager=java.rmi.RMISecurityManager "});
            rmid.start();

            Echo[] echo = spawnAndTest(rmid.getPort());
            reactivateAndTest(echo);
        } catch (IOException e) {
            TestLibrary.bomb("creating rmid", e);
        } finally {
            if (rmid != null)
                rmid.destroy();
        }
    }

    private static Echo[] spawnAndTest(int rmidPort) {

        System.err.println("\nCreate Test-->");

        Echo[] echo = new Echo[protocol.length];

        for (int i = 0; i < protocol.length; i++) {
            JavaVM serverVM = new JavaVM("EchoImpl",
                                         "-Djava.security.policy=" +
                                         TestParams.defaultPolicy +
                                         " -Drmi.registry.port=" +
                                         registryPort +
                                         " -Djava.rmi.activation.port=" +
                                         rmidPort,
                                         protocol[i]);

            System.err.println("\nusing protocol: " +
                    ("".equals(protocol[i]) ? "none" : protocol[i]));

            try {
                /* spawn VM for EchoServer */
                serverVM.start();

                /* lookup server */
                echo[i] = null;
                // 24 seconds timeout
                long stopTime = System.currentTimeMillis() + 24000;
                do {
                    try {
                        echo[i] = (Echo) Naming.lookup("//:" + registryPort +
                                                       "/EchoServer");
                        break;
                    } catch (NotBoundException e) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ignore) {
                        }
                    }
                } while (System.currentTimeMillis() < stopTime);

                if (echo[i] == null)
                    TestLibrary.bomb("server not bound in 120 tries", null);

                /* invoke remote method and print result*/
                System.err.println("Bound to " + echo[i]);
                byte[] data = ("Greetings, citizen " +
                               System.getProperty("user.name") + "!"). getBytes();
                byte[] result = echo[i].echoNot(data);
                for (int j = 0; j < result.length; j++)
                    result[j] = (byte) ~result[j];
                System.err.println("Result: " + new String(result));
                echo[i].shutdown();

            } catch (Exception e) {
                TestLibrary.bomb("test failed", e);

            } finally {
                serverVM.destroy();
                try {
                    Naming.unbind("//:" + registryPort + "/EchoServer");
                } catch (RemoteException | NotBoundException | MalformedURLException e) {
                    TestLibrary.bomb("unbinding EchoServer", e);
                }
            }
        }
        return echo;
    }


    private static void reactivateAndTest(Echo[] echo) {

        System.err.println("\nReactivate Test-->");

        for (int i = 0; i < echo.length; i++) {
            try {
                System.err.println("\nusing protocol: " +
                           ("".equals(protocol[i]) ? "none" : protocol[i]));
                byte[] data = ("Greetings, citizen " +
                               System.getProperty("user.name") + "!").getBytes();
                byte[] result = echo[i].echoNot(data);
                for (int j = 0; j < result.length; j++)
                    result[j] = (byte) ~result[j];
                System.err.println("Result: " + new String(result));
                echo[i].shutdown();
            } catch (Exception e) {
                TestLibrary.bomb("activating EchoServer for protocol " + protocol[i], e);
            }
        }
    }
}
