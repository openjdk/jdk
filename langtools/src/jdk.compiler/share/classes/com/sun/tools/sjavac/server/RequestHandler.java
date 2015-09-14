/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.sjavac.server;

import static com.sun.tools.sjavac.server.SjavacServer.LINE_TYPE_RC;
import static com.sun.tools.sjavac.server.SjavacServer.LINE_TYPE_STDERR;
import static com.sun.tools.sjavac.server.SjavacServer.LINE_TYPE_STDOUT;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.Socket;

import com.sun.tools.sjavac.AutoFlushWriter;
import com.sun.tools.sjavac.Log;


/**
 * A RequestHandler handles requests performed over a socket. Specifically it
 *  - Reads the command string specifying which method is to be invoked
 *  - Reads the appropriate arguments
 *  - Delegates the actual invocation to the given sjavac implementation
 *  - Writes the result back to the socket output stream
 *
 * None of the work performed by this class is really bound by the CPU. It
 * should be completely fine to have a large number of RequestHandlers active.
 * To limit the number of concurrent compilations, use PooledSjavac.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class RequestHandler implements Runnable {

    private final Socket socket;
    private final Sjavac sjavac;

    public RequestHandler(Socket socket, Sjavac sjavac) {
        this.socket = socket;
        this.sjavac = sjavac;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Read argument array
            int n = Integer.parseInt(in.readLine());
            String[] args = new String[n];
            for (int i = 0; i < n; i++) {
                args[i] = in.readLine();
            }

            // Perform compilation
            Writer stdout = new LinePrefixFilterWriter(new AutoFlushWriter(out), LINE_TYPE_STDOUT + ":");
            Writer stderr = new LinePrefixFilterWriter(new AutoFlushWriter(out), LINE_TYPE_STDERR + ":");
            int rc = sjavac.compile(args, stdout, stderr);
            stdout.flush();
            stderr.flush();

            // Send return code back to client
            out.println(LINE_TYPE_RC + ":" + rc);

        } catch (Exception ex) {
            // Not much to be done at this point. The client side request
            // code will most likely throw an IOException and the
            // compilation will fail.
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.error(sw.toString());
        }
    }
}
