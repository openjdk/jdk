/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.trace;

import java.io.*;

/**
 * StreamRedirectThread is a thread which copies it's input to
 * it's output and terminates when it completes.
 *
 * @author Robert Field
 */
class StreamRedirectThread extends Thread {

    private final Reader in;
    private final Writer out;

    private static final int BUFFER_SIZE = 2048;

    /**
     * Set up for copy.
     * @param name  Name of the thread
     * @param in    Stream to copy from
     * @param out   Stream to copy to
     */
    StreamRedirectThread(String name, InputStream in, OutputStream out) {
        super(name);
        this.in = new InputStreamReader(in);
        this.out = new OutputStreamWriter(out);
        setPriority(Thread.MAX_PRIORITY-1);
    }

    /**
     * Copy.
     */
    @Override
    public void run() {
        try {
            char[] cbuf = new char[BUFFER_SIZE];
            int count;
            while ((count = in.read(cbuf, 0, BUFFER_SIZE)) >= 0) {
                out.write(cbuf, 0, count);
            }
            out.flush();
        } catch(IOException exc) {
            System.err.println("Child I/O Transfer - " + exc);
        }
    }
}
