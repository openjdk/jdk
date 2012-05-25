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
    private JavaVM javaVM;
    private static Object lock = new Object();
    private static int count = 0;


    /* StreamPipe constructor : should only be called by plugTogether() method !!
     * If passed vm is not null :
     * -  This is StreamPipe usage when streams to pipe come from a given
     *    vm (JavaVM) process (the vm process must be started with a prefixed
     *    "-showversion" option to be able to determine as soon as possible when
     *    the vm process is started through the redirection of the streams).
     *    There must be a close connection between the StreamPipe instance and
     *    the JavaVM object on which a start() call has been done.
     *    run() method will flag distant JavaVM as started.
     * If passed vm is null :
     * -  We don't have control on the process which we want to redirect the passed
     *    streams.
     *    run() method will ignore distant process.
     */
    private StreamPipe(JavaVM vm, InputStream in, OutputStream out, String name) {
        super(name);
        this.in  = in;
        this.out = out;
        this.preamble = "# ";
        this.javaVM = vm;
    }

    // Install redirection of passed InputStream and OutputStream from passed JavaVM
    // to this vm standard output and input streams.
    public static void plugTogether(JavaVM vm, InputStream in, OutputStream out) {
        String name = null;

        synchronized (lock) {
            name = "TestLibrary: StreamPipe-" + (count ++ );
        }

        Thread pipe = new StreamPipe(vm, in, out, name);
        pipe.setDaemon(true);
        pipe.start();
    }

    /* Redirects the InputStream and OutputStream passed by caller to this
     * vm standard output and input streams.
     * (we just have to use fully parametered plugTogether() call with a null
     *  JavaVM input to do this).
     */
    public static void plugTogether(InputStream in, OutputStream out) {
        plugTogether(null, in, out);
    }

    // Starts redirection of streams.
    public void run() {
        BufferedReader r = new BufferedReader(new InputStreamReader(in), 1);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out));
        byte[] buf = new byte[256];

        try {
            String line;

            /* This is to check that the distant vm has started,
             * if such a vm has been provided at construction :
             * - As soon as we can read something from r BufferedReader,
             *   that means the distant vm is already started.
             * Thus we signal associated JavaVM object that it is now started.
             */
            if (((line = r.readLine()) != null) &&
                (javaVM != null)) {
                javaVM.setStarted();
            }

            // Redirects r on w.
            while (line != null) {
                w.write(preamble);
                w.write(line);
                w.newLine();
                w.flush();
                line = r.readLine();
            }

        } catch (IOException e) {
            System.err.println("*** IOException in StreamPipe.run:");
            e.printStackTrace();
        }
    }

}
