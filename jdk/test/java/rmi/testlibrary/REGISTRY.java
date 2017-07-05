/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.IOException;

/**
 * Class to run and control rmiregistry in a sub-process.
 *
 * We can't kill a registry if we have too-close control
 * over it.  We must make it in a subprocess, and then kill the
 * subprocess when it has served our needs.
 */
public class REGISTRY extends JavaVM {

    private static final double START_TIMEOUT =
            20_000 * TestLibrary.getTimeoutFactor();
    private static final String DEFAULT_RUNNER = "RegistryRunner";

    private int port = -1;

    private REGISTRY(String runner, OutputStream out, OutputStream err,
                    String options, int port) {
        super(runner, options, Integer.toString(port), out, err);
        try {
            Class runnerClass = Class.forName(runner);
            if (!RegistryRunner.class.isAssignableFrom(runnerClass)) {
                throw new RuntimeException("runner class must be RegistryRunner"
                        + " or its sub class");
            }
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
        this.port = port;
    }

    public static REGISTRY createREGISTRY() {
        return createREGISTRYWithRunner(DEFAULT_RUNNER, System.out, System.err, "", 0);
    }

    public static REGISTRY createREGISTRY(OutputStream out, OutputStream err,
                                    String options, int port) {
        return createREGISTRYWithRunner(DEFAULT_RUNNER, out, err, options, port);
    }

    public static REGISTRY createREGISTRYWithRunner(String runner, String options) {
        return createREGISTRYWithRunner(runner, System.out, System.err, options, 0);
    }

    public static REGISTRY createREGISTRYWithRunner(String runner, OutputStream out,
                                        OutputStream err, String options, int port) {
        options += " --add-exports=java.rmi/sun.rmi.registry=ALL-UNNAMED"
                + " --add-exports=java.rmi/sun.rmi.server=ALL-UNNAMED"
                + " --add-exports=java.rmi/sun.rmi.transport=ALL-UNNAMED"
                + " --add-exports=java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED";
       REGISTRY reg = new REGISTRY(runner, out, err, options, port);
       return reg;
    }

    /**
     * Starts the registry in a sub-process and waits up to
     * the given timeout period to confirm that it's running,
     * and get the port where it's running.
     */
    public void start() throws IOException {
        super.start();
        long startTime = System.currentTimeMillis();
        long deadline = TestLibrary.computeDeadline(startTime, (long)START_TIMEOUT);
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) { }

            String output = outputStream.ba.toString();
            port = RegistryRunner.getRegistryPort(output);
            if (port != -1) {
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                TestLibrary.bomb("Failed to start registry, giving up after " +
                    (System.currentTimeMillis() - startTime) + "ms.", null);
            }
        }
    }

    /**
     * Shuts down the registry.
     */
    public void shutdown() {
        RegistryRunner.requestExit(port);
    }

    /**
     * Gets the port where the registry is serving.
     */
    public int getPort() {
        return port;
    }
}
