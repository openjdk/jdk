/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.net.httpserver.simpleserver;

import java.io.PrintWriter;

/**
 * Programmatic entry point to start the jwebserver tool.
 */
public class JWebServer {

    private static final String SYS_PROP_MAX_CONNECTIONS = "jdk.httpserver.maxConnections";
    private static final String DEFAULT_JWEBSERVER_MAX_CONNECTIONS = "200";

    private static final String SYS_PROP_ENHANCED_EXCEP = "jdk.includeInExceptions";
    private static final String DEFAULT_ENHANCED_EXCEP = "net";
    /**
     * This constructor should never be called.
     */
    private JWebServer() { throw new AssertionError(); }

    /**
     * The main entry point.
     *
     * <p> The command line arguments are parsed and the server is started. If
     * started successfully, the server will run on a new non-daemon thread,
     * and this method will return. Otherwise, if the server is not started
     * successfully, e.g. an error is encountered while parsing the arguments
     * or an I/O error occurs, the server is not started and this method invokes
     * System::exit with an appropriate exit code.
     *
     * <p> If the system property "sun.net.httpserver.maxReqTime" has not been
     * set by the user, it is set to a value of 5 seconds. This is to prevent
     * the server from hanging indefinitely, for example in the case of an HTTPS
     * request.
     *
     * @param args the command-line options
     * @throws NullPointerException if {@code args} is {@code null}, or if there
     *         are any {@code null} values in the {@code args} array
     */
    public static void main(String... args) {
        setMaxReqTime();
        setEnhancedExceptions();
        setMaxConnectionsIfNotSet();

        int ec = SimpleFileServerImpl.start(new PrintWriter(System.out, true), "jwebserver", args);
        if (ec != 0) {
            System.exit(ec);
        }  // otherwise, the server has either been started successfully and
           // runs in another non-daemon thread, or -h or -version have been
           // passed and the main thread has exited normally.
    }

    public static final String MAXREQTIME_KEY = "sun.net.httpserver.maxReqTime";
    public static final String MAXREQTIME_VAL = "5";

    private static void setMaxReqTime() {
        if (System.getProperty(MAXREQTIME_KEY) == null) {
            System.setProperty(MAXREQTIME_KEY, MAXREQTIME_VAL);
        }
    }

    static void setEnhancedExceptions() {
        if (System.getProperty(SYS_PROP_ENHANCED_EXCEP) != null) {
            // an explicit value has already been set, so we don't override it
            return;
        }
        System.setProperty(SYS_PROP_ENHANCED_EXCEP, DEFAULT_ENHANCED_EXCEP);
    }

    static void setMaxConnectionsIfNotSet() {
        if (System.getProperty(SYS_PROP_MAX_CONNECTIONS) == null) {
            System.setProperty(SYS_PROP_MAX_CONNECTIONS, DEFAULT_JWEBSERVER_MAX_CONNECTIONS);
        }
    }
}
