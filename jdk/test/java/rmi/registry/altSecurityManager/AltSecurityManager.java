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
 * @bug 4183202
 * @summary rmid and rmiregistry could allow alternate security manager
 * @author Laird Dornin
 *
 * @library ../../testlibrary
 * @build TestLibrary JavaVM RMID TestSecurityManager
 * @run main/othervm AltSecurityManager
 */

/**
 * Ensure that a user is able to specify alternate security managers to
 * be used in rmiregistry and rmid.  Test specifies a security manager
 * that throws a runtime exception in its checkListen method, this
 * will cause rmiregistry and rmid to exit early because those
 * utilities will be unable to export any remote objects; test fails
 * if registry and rmid take too long to exit.
 */
public class AltSecurityManager implements Runnable {
    private final int regPort;
    // variable to hold registry and rmid children
    static JavaVM vm = null;

    // names of utilities
    static String utilityToStart = null;
    static final String REGISTRY_IMPL = "sun.rmi.registry.RegistryImpl";
    static final String ACTIVATION = "sun.rmi.server.Activation";

    // children should exit in at least this time.
    static long TIME_OUT = 15000;

    public AltSecurityManager(int port) {
        if (port <= 0) {
            TestLibrary.bomb("Port must be greater then 0.");
        }

        this.regPort = port;
    }

    public void run() {
        try {
            if (utilityToStart.equals(REGISTRY_IMPL)) {
                vm = new JavaVM(utilityToStart,
                        " -Djava.security.manager=TestSecurityManager",
                        Integer.toString(regPort));
            } else if (utilityToStart.contains(ACTIVATION)) {
                vm = new JavaVM(utilityToStart,
                        " -Djava.security.manager=TestSecurityManager",
                        "-port " + Integer.toString(regPort));
            } else {
                TestLibrary.bomb("Utility to start must be " + REGISTRY_IMPL +
                        " or " + ACTIVATION);
            }

            System.err.println("starting " + utilityToStart);
            vm.start();
            vm.getVM().waitFor();

        } catch (Exception e) {
            TestLibrary.bomb(e);
        }
    }

    /**
     * Wait to make sure that the registry and rmid exit after
     * their security manager is set.
     */
    public static void ensureExit(String utility) throws Exception {
        utilityToStart = utility;

        try {
            int port = TestLibrary.getUnusedRandomPort();
            Thread thread = new Thread(new AltSecurityManager(port));
            System.err.println("expecting RuntimeException for " +
                               "checkListen in child process");
            long start = System.currentTimeMillis();
            thread.start();
            thread.join(TIME_OUT);

            long time = System.currentTimeMillis() - start;
            System.err.println("waited " + time + " millis for " +
                               utilityToStart + " to die");

            if (time >= TIME_OUT) {

                // dont pollute other tests; increase the likelihood
                // that rmid will go away if it did not exit already.
                if (utility.equals(ACTIVATION)) {
                    RMID.shutdown(port);
                }

                TestLibrary.bomb(utilityToStart +
                                 " took too long to die...");
            } else {
                System.err.println(utilityToStart +
                                   " terminated on time");
            }
        } finally {
            vm.destroy();
            vm = null;
        }
    }

    public static void main(String[] args) {
        try {
            System.err.println("\nRegression test for bug 4183202\n");

            // make sure the registry exits early.
            ensureExit(REGISTRY_IMPL);

            // make sure rmid exits early
            ensureExit(ACTIVATION);

            System.err.println("test passed");

        } catch (Exception e) {
            TestLibrary.bomb(e);
        } finally {
            RMID.removeLog();
        }
    }
}
