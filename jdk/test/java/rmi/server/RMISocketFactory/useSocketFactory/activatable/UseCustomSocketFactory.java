/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

 * @summary synopsis: cannot use socket factories with Activatable objects
 * @author Ann Wollrath
 *
 * @library ../../../../testlibrary
 * @build Echo
 * @build EchoImpl
 * @build EchoImpl_Stub
 * @build UseCustomSocketFactory
 * @build TestLibrary
 * @run main/othervm/policy=security.policy/timeout=360 UseCustomSocketFactory
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;

public class UseCustomSocketFactory {
    static final int REGISTRY_PORT = TestLibrary.getUnusedRandomPort();

    static String[] protocol = new String[] { "", "compress", "xor" };

    public static void main(String[] args) {

        System.out.println("\nRegression test for bug 4115696\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        try {
            LocateRegistry.createRegistry(REGISTRY_PORT);
        } catch (Exception e) {
            TestLibrary.bomb("creating registry", e);
        }

        RMID rmid = null;

        try {
            rmid = RMID.createRMID(true);
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
                                         REGISTRY_PORT +
                                         " -Djava.rmi.activation.port=" +
                                         rmidPort,
                                         protocol[i]);

            System.err.println("\nusing protocol: " +
                               (protocol[i] == "" ? "none" : protocol[i]));

            try {
                /* spawn VM for EchoServer */
                serverVM.start();

                /* lookup server */
                int tries = 12;        // need enough tries for slow machine.
                echo[i] = null;
                do {
                    try {
                        echo[i] = (Echo) Naming.lookup("//:" + REGISTRY_PORT +
                                                       "/EchoServer");
                        break;
                    } catch (NotBoundException e) {
                        try {
                            Thread.sleep(2000);
                        } catch (Exception ignore) {
                        }
                        continue;
                    }
                } while (--tries > 0);

                if (echo[i] == null)
                    TestLibrary.bomb("server not bound in 12 tries", null);

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
                    Naming.unbind("//:" + REGISTRY_PORT + "/EchoServer");
                } catch (Exception e) {
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
                                   (protocol[i] == "" ? "none" : protocol[i]));
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
