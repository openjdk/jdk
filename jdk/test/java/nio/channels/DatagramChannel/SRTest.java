/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Test DatagramChannel's send and receive methods
 * @author Mike McCloskey
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;


public class SRTest {

    static PrintStream log = System.err;

    public static void main(String[] args) throws Exception {
        test();
    }

    static void test() throws Exception {
        invoke(new ClassicReader(), new ClassicWriter());
        log.println("Classic RW: OK");

        invoke(new ClassicReader(), new NioWriter());
        log.println("Classic R, Nio W: OK");

        invoke(new NioReader(), new ClassicWriter());
        log.println("Classic W, Nio R: OK");

        invoke(new NioReader(), new NioWriter());
        log.println("Nio RW: OK");
    }

    static void invoke(Sprintable reader, Sprintable writer) throws Exception {
        Thread readerThread = new Thread(reader);
        readerThread.start();
        Thread.sleep(50);

        Thread writerThread = new Thread(writer);
        writerThread.start();

        writerThread.join();
        readerThread.join();

        reader.throwException();
        writer.throwException();
    }

    public interface Sprintable extends Runnable {
        public void throwException() throws Exception;
    }

    public static class ClassicWriter implements Sprintable {
        Exception e = null;

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public void run() {
            try {
                DatagramSocket ds = new DatagramSocket();
                String dataString = "hello";
                byte[] data = dataString.getBytes();
                InetAddress address = InetAddress.getLocalHost();
                DatagramPacket dp = new DatagramPacket(data, data.length,
                                                       address, 8888);
                ds.send(dp);
                Thread.sleep(50);
                ds.send(dp);
            } catch (Exception ex) {
                e = ex;
            }
        }
    }

    public static class NioWriter implements Sprintable {
        Exception e = null;

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public void run() {
            try {
                DatagramChannel dc = DatagramChannel.open();
                ByteBuffer bb = ByteBuffer.allocateDirect(256);
                bb.put("hello".getBytes());
                bb.flip();
                InetAddress address = InetAddress.getLocalHost();
                InetSocketAddress isa = new InetSocketAddress(address, 8888);
                dc.send(bb, isa);
                Thread.sleep(50);
                dc.send(bb, isa);
            } catch (Exception ex) {
                e = ex;
            }
        }
    }

    public static class ClassicReader implements Sprintable {
        Exception e = null;

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public void run() {
            try {
                byte[] buf = new byte[256];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                DatagramSocket ds = new DatagramSocket(8888);
                ds.receive(dp);
                String received = new String(dp.getData());
                log.println(received);
                ds.close();
            } catch (Exception ex) {
                e = ex;
            }
        }
    }

    public static class NioReader implements Sprintable {
        Exception e = null;

        public void throwException() throws Exception {
            if (e != null)
                throw e;
        }

        public void run() {
            try {
                DatagramChannel dc = DatagramChannel.open();
                dc.socket().bind(new InetSocketAddress(8888));
                ByteBuffer bb = ByteBuffer.allocateDirect(100);
                SocketAddress sa = dc.receive(bb);
                bb.flip();
                CharBuffer cb = Charset.forName("US-ASCII").
                    newDecoder().decode(bb);
                log.println("From: "+sa+ " said " +cb);
                dc.close();
            } catch (Exception ex) {
                e = ex;
            }
        }
    }

}
