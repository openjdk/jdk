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
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

/**
 * @test TestIOTopFrame
 * @summary Tests that the top frames of I/O events are as expected.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestIOTopFrame
 */

// Lines commented with a number indicate that they are the nth event for that
// top frame. The invocation of the methods RandomAccessFile::readUTF(),
// SocketInputStream::readNBytes(...) and FileInputStream::readAllBytes()
// results in 2-3 events.
public class TestIOTopFrame {
    private static final String EVENT_FILE_READ = "jdk.FileRead";
    private static final String EVENT_FILE_FORCE = "jdk.FileForce";
    private static final String EVENT_FILE_WRITE = "jdk.FileWrite";
    private static final String EVENT_SOCKET_READ = "jdk.SocketRead";
    private static final String EVENT_SOCKET_WRITE = "jdk.SocketWrite";

    public static void main(String... args) throws Exception {
        testFileRead();
        testFileWrite();
        testSocketStreams();
        testSocketChannels();
    }

    private static void testFileRead() throws Exception {
        printTestDescription(EVENT_FILE_READ, "RandomAccessFile, FileInputStream, Files.newInputStream and Files.newByteChannel");
        File f1 = new File("testFileRead-1.bin");
        writeRAF(f1);
        File f2 = new File("testFileRead-2.bin");
        writeFileStream(f2);
        Path p = Path.of("testFileRead-3.bin");
        writeFilesNew(p);
        try (Recording r = new Recording()) {
            r.enable(EVENT_FILE_READ).withStackTrace();
            r.start();
            readRAF(f1);
            readFileStream(f2);
            readFilesNew(p);
            r.stop();
            assertTopFrames(r, "readFilesNew", 1, "readRAF", 20, "readStream", 15);
        }
    }

    private static void readFileStream(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            readStream(fis);
        }
    }

    private static void writeFileStream(File f) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            writeStream(fos);
        }
    }

    private static void readFilesNew(Path p) throws Exception {
        ByteBuffer b = ByteBuffer.allocateDirect(1000);
        try (SeekableByteChannel channel = Files.newByteChannel(p)) {
            channel.read(b); // 1
        }
        try (InputStream is = Files.newInputStream(p)) {
            readStream(is);
        }
    }

    private static void testFileWrite() throws Exception {
        printTestDescription(EVENT_FILE_WRITE + ", " + EVENT_FILE_FORCE, "RandomAccessFile, FileInputStream, Files.newOutputStream and Files.newByteChanneland");
        File f = new File("testFileWrite.bin");
        try (Recording r = new Recording()) {
            r.enable(EVENT_FILE_WRITE).withStackTrace();
            r.enable(EVENT_FILE_FORCE).withStackTrace();
            r.start();
            writeRAF(f);
            writeFileStream(f);
            writeAsync(f);
            writeFilesNew(f.toPath());
            r.stop();
            assertTopFrames(r, "writeFilesNew", 1, "writeRAF", 17, "writeStream", 6, "writeAsync", 1);
        }
    }

    private static void writeFilesNew(Path p) throws Exception {
        ByteBuffer b = ByteBuffer.allocateDirect(1000);
        OpenOption[] options = { StandardOpenOption.CREATE, StandardOpenOption.WRITE };
        try (SeekableByteChannel channel = Files.newByteChannel(p, options)) {
            channel.write(b); // 1
        }
        try (OutputStream os = Files.newOutputStream(p, options)) {
            writeStream(os);
        }
    }

    private static void writeAsync(File file) throws Exception {
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(file.toPath(), READ, WRITE);
        ByteBuffer[] buffers = createBuffers();
        channel.force(true);
    }

    private static void writeStream(OutputStream os) throws Exception {
        byte[] bytes = new byte[200];
        os.write(67); // 1
        os.write(bytes); // 2
        os.write(bytes, 0, 1); // 3
    }

    private static void writeRAF(File file) throws Exception {
        ByteBuffer[] buffers = createBuffers();
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
            fc.write(buffers[0]); // 15
            fc.write(buffers); // 16
            fc.force(true); // 17
        }
    }

    private static void readStream(InputStream is) throws Exception {
        byte[] bytes = new byte[10];
        is.read(); // 1
        is.read(bytes); // 2
        is.read(bytes, 0, 3); // 3
        is.readNBytes(2); // 4
        is.readNBytes(bytes, 0, 1); // 5
        byte[] leftOver = is.readAllBytes(); // 6, 7, 8 or 6, 7 for Files.newInputStream
        if (leftOver.length < 1) {
            throw new Exception("Expected some bytes to be read");
        }
    }

    private static void readRAF(File file) throws Exception {
        ByteBuffer[] buffers = createBuffers();
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
            fc.read(buffers[0]); // 19
            if (fc.read(buffers) < 1) { // 20
                throw new Exception("Expected some bytes to be read");
            };
        }
    }

    private static void testSocketChannels() throws Exception {
        printTestDescription(EVENT_SOCKET_READ + ", " + EVENT_SOCKET_WRITE, "SocketChannel and Socket adapters");
        try (Recording r = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                r.enable(EVENT_SOCKET_READ).withStackTrace();
                r.enable(EVENT_SOCKET_WRITE).withStackTrace();
                r.start();
                CountDownLatch latch = new CountDownLatch(1);
                Thread readerThread = Thread.ofPlatform().start(() -> readSocketChannel(ssc, latch));
                writeSocketChannel(ssc);
                latch.countDown();
                readerThread.join();
                r.stop();
                assertTopFrames(r, "readSocket", 6, "readSocketChannel", 2, "writeSocket", 3, "writeSocketChannel", 2);
            }
        }
    }

    private static void readSocketChannel(ServerSocketChannel ssc, CountDownLatch latch) {
        ByteBuffer[] buffers = createBuffers();
        try (SocketChannel sc = ssc.accept()) {
            sc.read(buffers[0]); // 1
            sc.read(buffers); // 2
            try (InputStream is = sc.socket().getInputStream()) {
                readSocket(is);
                latch.await();
            }
        } catch (Exception ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static void writeSocketChannel(ServerSocketChannel ssc) throws Exception {
        ByteBuffer[] buffers = createBuffers();
        try (SocketChannel sc = SocketChannel.open(ssc.getLocalAddress())) {
            sc.write(buffers[0]); // 1
            sc.write(buffers); // 2
            try (OutputStream out = sc.socket().getOutputStream()) {
                writeSocket(out);
            }
        }
    }

    private static void testSocketStreams() throws Exception {
        printTestDescription(EVENT_SOCKET_READ + ", " + EVENT_SOCKET_WRITE, "SocketInputStream and SocketOutputStream");
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket client = new Socket("localhost", serverSocket.getLocalPort());
             Socket server = serverSocket.accept();
             OutputStream socketOut = client.getOutputStream();
             InputStream socketIn = server.getInputStream();
             Recording r = new Recording()) {
            r.enable(EVENT_SOCKET_READ).withStackTrace();
            r.enable(EVENT_SOCKET_WRITE).withStackTrace();
            r.start();
            Thread readerThread = Thread.ofPlatform().start(() -> readSocket(socketIn));
            writeSocket(socketOut);
            readerThread.join();
            r.stop();
            assertTopFrames(r, "readSocket", 6, "writeSocket", 3);
        }
    }

    private static void writeSocket(OutputStream socketOut) throws Exception {
        byte[] bytes = "hello, world!".getBytes();
        socketOut.write(bytes); // 1
        socketOut.write(4711); // 2
        socketOut.write(bytes, 0, 3); // 3
    }

    private static void readSocket(InputStream socketIn) {
        try {
            byte[] bytes = new byte[100];
            socketIn.read(); // 1
            socketIn.read(bytes, 0, 3); // 2
            socketIn.readNBytes(3); // 3, 4
            socketIn.readNBytes(bytes, 0, 2); // 5
            if (socketIn.read(bytes) < 1) { // 6
                throw new RuntimeException("Expected some bytes to be read");
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static void assertTopFrames(Recording r, Object... frameCount) throws Exception {
        TreeMap<String, Integer> expected = new TreeMap<>();
        for (int i = 0; i < frameCount.length; i += 2) {
            String method = TestIOTopFrame.class.getName() + "::" + frameCount[i];
            Integer count = (Integer) frameCount[i + 1];
            expected.put(method, count);
        }
        Path dumpFile = Path.of("test-top-frame-" + r.getId() + ".jfr");
        r.dump(dumpFile);
        List<RecordedEvent> events = RecordingFile.readAllEvents(dumpFile);
        TreeMap<String, Integer> actual = new TreeMap<>();
        for (RecordedEvent e : events) {
            RecordedStackTrace st = e.getStackTrace();
            RecordedMethod topMethod = st.getFrames().get(0).getMethod();
            String methodName = topMethod.getType().getName() + "::" + topMethod.getName();
            actual.merge(methodName, 1, Integer::sum);
        }

        printMap("Expected", expected);
        printMap("Actual", actual);
        if (!expected.equals(actual)) {
            System.out.println(events);
            throw new Exception("Top methods are not as expected");
        }
        Files.delete(dumpFile);
    }

    private static void printTestDescription(String eventNames, String components) {
        String title = "Testing top frames for events: " + eventNames + " (" + components + ")";
        System.out.println(title);
        System.out.println("*".repeat(title.length()));
    }

    private static void printMap(String title, TreeMap<String, Integer> map) {
        System.out.println(title + ":");
        for (var entry : map.entrySet()) {
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }
        System.out.println();
    }

    private static ByteBuffer[] createBuffers() {
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ByteBuffer buffer = ByteBuffer.wrap(data);
        ByteBuffer[] buffers = new ByteBuffer[2];
        buffers[0] = buffer.duplicate();
        buffers[1] = buffer.duplicate();
        return buffers;
    }
}
