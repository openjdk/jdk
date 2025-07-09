/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.io;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

/**
 * @test TestIOTopFrame
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestIOTopFrame
 */
public class TestIOTopFrame {

    // Lines commented with a number indicate that they emit an event.
    // The invocation of the following RandomAccessFile::readUTF() and
    // SocketInputStream::readNBytes(...) result in two events.
    //
    public static void main(String... args) throws Exception {
        testFileWrite();
        testFileRead();
        testSocketStreams();
        testSocketChannels();
    }

    private static void testSocketChannels() throws Exception {
        try (Recording r = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                r.enable("jdk.SocketRead").withStackTrace();
                r.enable("jdk.SocketWrite").withStackTrace();
                r.start();
                Thread readerThread = Thread.ofPlatform().start(() -> readSocketChannel(ssc));
                writeSocketChannel(ssc);
                readerThread.join();
                assertTopFrames(Map.of("readSocketChannel", 2, "writeSocketChannel", 2), r, "jdk.SocketRead and jdk.SocketWrite (SocketChannel)");
            }
        }
    }

    private static void writeSocketChannel(ServerSocketChannel ssc) throws IOException {
        try (SocketChannel sc = SocketChannel.open(ssc.getLocalAddress())) {
            ByteBuffer[] buffers = createBuffers();
            sc.write(buffers[0]); // 1
            sc.write(buffers); // 2
        }
    }

    private static void readSocketChannel(ServerSocketChannel ssc) {
        ByteBuffer[] buffers = createBuffers();
        try (SocketChannel sc = ssc.accept()) {
            sc.read(buffers[0]); // 1
            sc.read(buffers); // 2
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static ByteBuffer[] createBuffers() {
        ByteBuffer[] buffers = new ByteBuffer[2];
        buffers[0] = ByteBuffer.allocate(10); // 1
        buffers[1] = ByteBuffer.allocate(10); // 2
        return buffers;
    }

    private static void testSocketStreams() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
                Socket client = new Socket("localhost", serverSocket.getLocalPort());
                Socket server = serverSocket.accept();
                OutputStream socketOut = client.getOutputStream();
                InputStream socketIn = server.getInputStream();
                Recording r = new Recording()) {
            r.enable("jdk.SocketRead").withStackTrace();
            r.enable("jdk.SocketWrite").withStackTrace();
            byte[] bytes = "hello, world!".getBytes();
            r.start();
            writeSocket(socketOut, bytes);
            readSocket(socketIn, bytes);
            assertTopFrames(Map.of("readSocket", 6, "writeSocket", 3), r, "jdk.SocketRead and jdk.SocketWrite (SocketInputStream and SocketOutputStream)");
        }
    }

    private static void readSocket(InputStream socketIn, byte[] bytes) throws IOException {
        socketIn.read(); // 1
        socketIn.read(bytes, 0, 3); // 2
        socketIn.readNBytes(3); // 3, 4
        socketIn.readNBytes(bytes, 0, 2); // 5
        socketIn.read(bytes); // 6
    }

    private static void writeSocket(OutputStream socketOut, byte[] bytes) throws IOException {
        socketOut.write(bytes); // 1
        socketOut.write(4711); // 2
        socketOut.write(bytes, 0, 3); // 3
    }

    private static void testFileRead() throws Exception {
        File f1 = new File("testFileRead-1.bin");
        writeRAF(f1);
        File f2 = new File("testFileRead-2.bin");
        writeStream(f2);
        try (Recording r = new Recording()) {
            r.enable("jdk.FileRead").withStackTrace();
            r.start();
            readRAF(f1);
            readStream(f2);
            r.stop();
            assertTopFrames(Map.of("readRAF", 20, "readStream", 6), r, "jdk.FileRead (RandomAccessFile and FileInputStream)");
        }
    }

    private static void testFileWrite() throws Exception {
        File f1 = new File("testFileWrite-1.bin");
        File f2 = new File("testFileWrite-2.bin");
        File f3 = new File("testFileWrite-3.bin");
        try (Recording r = new Recording()) {
            r.enable("jdk.FileWrite").withStackTrace();
            r.enable("jdk.FileForce").withStackTrace();
            r.start();
            writeRAF(f1);
            writeStream(f2);
            writeAsync(f1);
            r.stop();
            assertTopFrames(Map.of("writeRAF", 17, "writeStream", 3, "writeAsync", 1), r, "jdk.FileWrite and jdk.FileForce (RandomAccessFile and FileOutputStream)");
        }
    }

    private static void writeAsync(File file) throws Exception {
        AsynchronousFileChannel ch = AsynchronousFileChannel.open(file.toPath(), READ, WRITE);
        ByteBuffer[] buffers = createBuffers();
        ch.force(true);
    }

    private static void writeStream(File f) throws IOException {
        byte[] bytes = new byte[2];
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(67); // 1
            fos.write(bytes); // 2
            fos.write(bytes, 0, 1); // 3
        }
    }

    private static void writeRAF(File file) throws IOException {
        byte[] bytes = new byte[100];
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.writeUTF("o\n"); // 1
            raf.writeUTF("hello"); // 2
            raf.write(23); // 3
            raf.write(bytes); // 4
            raf.write(bytes, 0, 50); // 5
            raf.writeBoolean(true); // 6
            raf.writeByte(23); // 7
            raf.writeBytes("hello"); // 8
            raf.writeChar('h'); // 9
            raf.writeChars("hello"); // 10
            raf.writeDouble(76.0); // 11
            raf.writeFloat(21.7f); // 12
            raf.writeInt(4711); // 13
            raf.writeLong(Long.MAX_VALUE); // 14
            FileChannel fc = raf.getChannel();
            ByteBuffer[] buffers = createBuffers();
            fc.write(buffers[0]); // 15
            fc.write(buffers); // 16
            fc.force(true); // 17
        }
    }

    private static void readStream(File f) throws IOException {
        byte[] bytes = new byte[100];
        try (FileInputStream fis = new FileInputStream(f)) {
            fis.read(); // 1
            fis.read(bytes); // 2
            fis.read(bytes, 0, 3); // 3
            fis.readNBytes(2); // 4
            fis.readNBytes(bytes, 0, 1); // 5
            fis.readAllBytes(); // 6
        }
    }

    private static void readRAF(File file) throws IOException {
        byte[] bytes = new byte[100];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readLine(); // 1
            raf.readUTF(); // 2, 3 (size and content)
            raf.read(); // 4
            raf.read(bytes); // 5
            raf.read(bytes, 0, 0); // 6
            raf.readBoolean(); // 7
            raf.readByte(); // 8
            raf.readChar(); // 9
            raf.readDouble(); // 10
            raf.readFloat(); // 11
            raf.seek(0);
            raf.readFully(bytes); // 12
            raf.seek(0);
            raf.readFully(bytes, 10, 10); // 13
            raf.readInt(); // 14
            raf.readLong(); // 15
            raf.readShort(); // 16
            raf.readUnsignedByte(); // 17
            raf.readUnsignedShort(); // 18
            FileChannel fc = raf.getChannel();
            ByteBuffer[] buffers = createBuffers();
            fc.read(buffers[0]); // 19
            fc.read(buffers); // 20
        }
    }

    private static void assertTopFrames(Map<String, Integer> map, Recording r, String testName) throws Exception {
        TreeMap<String, Integer> expected = new TreeMap<>();
        for (var e : map.entrySet()) {
            expected.put(TestIOTopFrame.class.getName() + "::" + e.getKey(), e.getValue());
        }
        Path recording = Path.of("test-top-frame-" + r.getId() + ".jfr");
        r.dump(recording);
        List<RecordedEvent> events = RecordingFile.readAllEvents(recording);
        TreeMap<String, Integer> actual = new TreeMap<>();
        for (RecordedEvent e : events) {
            RecordedStackTrace st = e.getStackTrace();
            RecordedMethod topMethod = st.getFrames().get(0).getMethod();
            String methodName = topMethod.getType().getName() + "::" + topMethod.getName();
            actual.merge(methodName, 1, Integer::sum);
        }
        String title = "Testing top frames for event " + testName;
        System.out.println(title);
        System.out.println("*".repeat(title.length()));
        printMap("Expected", expected);
        printMap("Actual", actual);
        if (!expected.equals(actual)) {
            System.out.println(events);
            throw new Exception("Top methods not as expected");
        }
    }

    private static void printMap(String title, TreeMap<String, Integer> map) {
        System.out.println(title + ":");
        for (var entry : map.entrySet()) {
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }
        System.out.println();
    }
}
