/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver.simpleserver;

import java.io.PrintWriter;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Programmatic entry point to start the jwebserver tool.
 *
 * <p><b> This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interface are subject to change or deletion
 * without notice.</b>
 */
public class JWebServer {

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
     * @param args the command-line options
     * @throws NullPointerException if {@code args} is {@code null}, or if there
     *         are any {@code null} values in the {@code args} array
     */
    public static void main(String... args) {
        int ec = SimpleFileServerImpl.start(new PrintWriter(System.out, true, UTF_8), "jwebserver", args);
        if (ec != 0) {
            System.exit(ec);
        }  // otherwise, the server has either been started successfully and
           // runs in another non-daemon thread, or -h or -version have been
           // passed and the main thread has exited normally.
    }
}
