/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 */

import java.io.*;

/**
 * Pipe output of one stream into input of another.
 */
public class StreamPipe extends Thread {

    private InputStream in;
    private OutputStream out;
    private String preamble;
    private static Object lock = new Object();
    private static int count = 0;

    public StreamPipe(InputStream in, OutputStream out, String name) {
        super(name);
        this.in  = in;
        this.out = out;
        this.preamble = "# ";
    }

    public void run() {
        BufferedReader r = new BufferedReader(new InputStreamReader(in), 1);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out));
        byte[] buf = new byte[256];
        boolean bol = true;     // beginning-of-line
        int count;

        try {
            String line;
            while ((line = r.readLine()) != null) {
                w.write(preamble);
                w.write(line);
                w.newLine();
                w.flush();
            }
        } catch (IOException e) {
            System.err.println("*** IOException in StreamPipe.run:");
            e.printStackTrace();
        }
    }

    public static void plugTogether(InputStream in, OutputStream out) {
        String name = null;

        synchronized (lock) {
            name = "TestLibrary: StreamPipe-" + (count ++ );
        }

        Thread pipe = new StreamPipe(in, out, name);
        pipe.setDaemon(true);
        pipe.start();
    }
}
