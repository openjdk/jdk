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
import java.util.Objects;

/**
 * Provides external entry points (tool and programmatic) to start the
 * simpleserver tool.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Main {

    /**
     * This constructor should never be called.
     */
    private Main() { throw new AssertionError(); }

    /**
     * The main entry point. The status code from starting the simple server is
     * ignored.
     *
     * @param args the command-line options
     * @throws NullPointerException if {@code args} is {@code null}, or if there
     *         are any {@code null} values in the {@code args} array
     */
    public static void main(String... args) {
        start(new PrintWriter(System.out, true), args);
    }

    /**
     * Programmatic interface.
     *
     * <p>A status code of 0 means that the server has been started successfully;
     * any other value indicates that an error occurred during option parsing or
     * server start-up.
     *
     * @param writer a writer to which output should be written
     * @param args the command-line options
     * @return The status code
     * @throws NullPointerException if any of the arguments are {@code null},
     *         or if there are any {@code null} values in the {@code args} array
     */
    public static int start(PrintWriter writer, String[] args) {
        return SimpleFileServerImpl.start(writer, args);
    }
}
