/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.ProtectionDomain;
import java.util.concurrent.CountDownLatch;


/**
 * @test
 * @key jfr
 * @summary This test runs JFR with a javaagent that reads/writes files and
 * sockets during every class definition. This is to verify that the i/o
 * instrumentation in JFR does not interfere with javaagents.
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules java.instrument
 *
 * @run shell MakeJAR.sh EvilInstrument 'Can-Redefine-Classes: true'
 * @run main/othervm -javaagent:EvilInstrument.jar jdk.jfr.event.io.EvilInstrument
 */

public class EvilInstrument {

    CountDownLatch socketEchoReady = new CountDownLatch(1);
    ServerSocket ss;

    /**
     * Thread that echos everything from a socket.
     */
    class SocketEcho extends Thread
    {
        public SocketEcho() {
            setDaemon(true);
        }

        public void run() {
            try {
                Socket s = ss.accept();
                OutputStream os = s.getOutputStream();
                InputStream is = s.getInputStream();
                socketEchoReady.countDown();
                for(;;) {
                    int b = is.read();
                    os.write(b);
                }
            } catch(Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        }
    }

    public static File createScratchFile() throws IOException {
        return File.createTempFile("EvilTransformer", null, new File(".")).getAbsoluteFile();
    }

    class EvilTransformer implements ClassFileTransformer {
        File scratch;
        Socket s;
        volatile boolean inited = false;

        public EvilTransformer() throws Exception {
            scratch = createScratchFile();
            ss = new ServerSocket(0);
            new SocketEcho().start();
            s = new Socket(ss.getInetAddress(), ss.getLocalPort());
            socketEchoReady.await();
            inited = true;
        }

        public byte[] transform(ClassLoader loader, String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer)
        {
            if (!inited) {
                return null;
            }
            // Do i/o operations during every transform call.
            try {
                FileOutputStream fos = new FileOutputStream(scratch);
                fos.write(31);
                fos.close();

                FileInputStream fis = new FileInputStream(scratch);
                fis.read();
                fis.close();

                RandomAccessFile raf = new RandomAccessFile(scratch, "rw");
                raf.read();
                raf.write(31);
                raf.close();

                s.getOutputStream().write(31);
                s.getInputStream().read();

            } catch(Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
            return null;
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        new EvilInstrument().addTransformer(inst);
    }

    private void addTransformer(Instrumentation inst) {
        try {
            inst.addTransformer(new EvilTransformer());
        } catch(Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String... args) throws Exception {
        System.out.println("Hello");
    }

}
