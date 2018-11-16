/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Tests the overheads of I/O API.
 * This test is known to depend heavily on network conditions and paltform.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class SocketReadWrite {

    private OutputStream os;
    private InputStream is;
    private ServerSocket ss;
    private Socket s1, s2;
    private ReadThread rt;

    @Setup
    public void beforeRun() throws IOException {
        InetAddress iaddr = InetAddress.getLocalHost();

        ss = new ServerSocket(0);
        s1 = new Socket(iaddr, ss.getLocalPort());
        s2 = ss.accept();

        os = s1.getOutputStream();
        is = s2.getInputStream();

        rt = new ReadThread(is);
        rt.start();
    }

    @TearDown
    public void afterRun() throws IOException, InterruptedException {
        os.write(0);
        os.close();
        is.close();
        s1.close();
        s2.close();
        ss.close();
        rt.join();
    }

    @Benchmark
    public void test() throws IOException {
        os.write((byte) 4711);
    }

    static class ReadThread extends Thread {
        private InputStream is;

        public ReadThread(InputStream is) {
            this.is = is;
        }

        public void run() {
            try {
                while (is.read() > 0);
            } catch (SocketException ex) {
                // ignore - most likely "socket closed", which means shutdown
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
