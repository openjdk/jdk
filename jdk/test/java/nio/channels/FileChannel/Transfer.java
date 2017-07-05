/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4434723 4482726 4559072 4638365 4795550 5081340 5103988 6253145
 *   6984545
 * @summary Test FileChannel.transferFrom and transferTo
 * @library ..
 * @key randomness
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.util.Random;


public class Transfer {

    private static Random generator = new Random();

    private static int[] testSizes = {
        0, 10, 1023, 1024, 1025, 2047, 2048, 2049 };

    public static void main(String[] args) throws Exception {
        testFileChannel();
        for (int i=0; i<testSizes.length; i++)
            testReadableByteChannel(testSizes[i]);
        xferTest02(); // for bug 4482726
        xferTest03(); // for bug 4559072
        xferTest04(); // for bug 4638365
        xferTest05(); // for bug 4638365
        xferTest06(); // for bug 5081340
        xferTest07(); // for bug 5103988
        xferTest08(); // for bug 6253145
        xferTest09(); // for bug 6984545
    }

    private static void testFileChannel() throws Exception {
        File source = File.createTempFile("source", null);
        source.deleteOnExit();
        File sink = File.createTempFile("sink", null);
        sink.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(source);
        FileChannel sourceChannel = fos.getChannel();
        sourceChannel.write(ByteBuffer.wrap(
            "Use the source, Luke!".getBytes()));
        sourceChannel.close();

        FileInputStream fis = new FileInputStream(source);
        sourceChannel = fis.getChannel();

        RandomAccessFile raf = new RandomAccessFile(sink, "rw");
        FileChannel sinkChannel = raf.getChannel();
        long oldSinkPosition = sinkChannel.position();
        long oldSourcePosition = sourceChannel.position();

        long bytesWritten = sinkChannel.transferFrom(sourceChannel, 0, 10);
        if (bytesWritten != 10)
            throw new RuntimeException("Transfer failed");

        if (sourceChannel.position() == oldSourcePosition)
            throw new RuntimeException("Source position didn't change");

        if (sinkChannel.position() != oldSinkPosition)
            throw new RuntimeException("Sink position changed");

        if (sinkChannel.size() != 10)
            throw new RuntimeException("Unexpected sink size");

        bytesWritten = sinkChannel.transferFrom(sourceChannel, 1000, 10);

        if (bytesWritten > 0)
            throw new RuntimeException("Wrote past file size");

        sourceChannel.close();
        sinkChannel.close();

        source.delete();
        sink.delete();
    }

    private static void testReadableByteChannel(int size) throws Exception {
        SelectorProvider sp = SelectorProvider.provider();
        Pipe p = sp.openPipe();
        Pipe.SinkChannel sink = p.sink();
        Pipe.SourceChannel source = p.source();
        sink.configureBlocking(false);

        ByteBuffer outgoingdata = ByteBuffer.allocateDirect(size + 10);
        byte[] someBytes = new byte[size + 10];
        generator.nextBytes(someBytes);
        outgoingdata.put(someBytes);
        outgoingdata.flip();

        int totalWritten = 0;
        while (totalWritten < size + 10) {
            int written = sink.write(outgoingdata);
            if (written < 0)
                throw new Exception("Write failed");
            totalWritten += written;
        }

        File f = File.createTempFile("blah"+size, null);
        f.deleteOnExit();
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        FileChannel fc = raf.getChannel();
        long oldPosition = fc.position();

        long bytesWritten = fc.transferFrom(source, 0, size);
        fc.force(true);
        if (bytesWritten != size)
            throw new RuntimeException("Transfer failed");

        if (fc.position() != oldPosition)
            throw new RuntimeException("Position changed");

        if (fc.size() != size)
            throw new RuntimeException("Unexpected sink size "+ fc.size());

        fc.close();
        sink.close();
        source.close();

        f.delete();
    }

    public static void xferTest02() throws Exception {
        byte[] srcData = new byte[5000];
        for (int i=0; i<5000; i++)
            srcData[i] = (byte)generator.nextInt();

        // get filechannel for the source file.
        File source = File.createTempFile("source", null);
        source.deleteOnExit();
        RandomAccessFile raf1 = new RandomAccessFile(source, "rw");
        FileChannel fc1 = raf1.getChannel();

        // write out data to the file channel
        long bytesWritten = 0;
        while (bytesWritten < 5000) {
            bytesWritten = fc1.write(ByteBuffer.wrap(srcData));
        }

        // get filechannel for the dst file.
        File dest = File.createTempFile("dest", null);
        dest.deleteOnExit();
        RandomAccessFile raf2 = new RandomAccessFile(dest, "rw");
        FileChannel fc2 = raf2.getChannel();

        int bytesToWrite = 3000;
        int startPosition = 1000;

        bytesWritten = fc1.transferTo(startPosition, bytesToWrite, fc2);

        fc1.close();
        fc2.close();
        raf1.close();
        raf2.close();

        source.delete();
        dest.delete();
    }

    public static void xferTest03() throws Exception {
        byte[] srcData = new byte[] {1,2,3,4} ;

        // get filechannel for the source file.
        File source = File.createTempFile("source", null);
        source.deleteOnExit();
        RandomAccessFile raf1 = new RandomAccessFile(source, "rw");
        FileChannel fc1 = raf1.getChannel();
        fc1.truncate(0);

        // write out data to the file channel
        int bytesWritten = 0;
        while (bytesWritten < 4) {
            bytesWritten = fc1.write(ByteBuffer.wrap(srcData));
        }

        // get filechannel for the dst file.
        File dest = File.createTempFile("dest", null);
        dest.deleteOnExit();
        RandomAccessFile raf2 = new RandomAccessFile(dest, "rw");
        FileChannel fc2 = raf2.getChannel();
        fc2.truncate(0);

        fc1.transferTo(0, srcData.length + 1, fc2);

        if (fc2.size() > 4)
            throw new Exception("xferTest03 failed");

        fc1.close();
        fc2.close();
        raf1.close();
        raf2.close();

        source.delete();
        dest.delete();
    }

    // Test transferTo with large file
    public static void xferTest04() throws Exception {
        // Windows and Linux can't handle the really large file sizes for a
        // truncate or a positional write required by the test for 4563125
        String osName = System.getProperty("os.name");
        if (!(osName.startsWith("SunOS") || osName.contains("OS X")))
            return;
        File source = File.createTempFile("blah", null);
        source.deleteOnExit();
        long testSize = ((long)Integer.MAX_VALUE) * 2;
        initTestFile(source, 10);
        RandomAccessFile raf = new RandomAccessFile(source, "rw");
        FileChannel fc = raf.getChannel();
        fc.write(ByteBuffer.wrap("Use the source!".getBytes()), testSize - 40);
        fc.close();
        raf.close();

        File sink = File.createTempFile("sink", null);
        sink.deleteOnExit();

        FileInputStream fis = new FileInputStream(source);
        FileChannel sourceChannel = fis.getChannel();

        raf = new RandomAccessFile(sink, "rw");
        FileChannel sinkChannel = raf.getChannel();

        long bytesWritten = sourceChannel.transferTo(testSize -40, 10,
                                                     sinkChannel);
        if (bytesWritten != 10) {
            throw new RuntimeException("Transfer test 4 failed " +
                                       bytesWritten);
        }
        sourceChannel.close();
        sinkChannel.close();

        source.delete();
        sink.delete();
    }

    // Test transferFrom with large file
    public static void xferTest05() throws Exception {
        // Create a source file & large sink file for the test
        File source = File.createTempFile("blech", null);
        source.deleteOnExit();
        initTestFile(source, 100);

        // Create the sink file as a sparse file if possible
        File sink = null;
        FileChannel fc = null;
        while (fc == null) {
            sink = File.createTempFile("sink", null);
            // re-create as a sparse file
            sink.delete();
            try {
                fc = FileChannel.open(sink.toPath(),
                                      StandardOpenOption.CREATE_NEW,
                                      StandardOpenOption.WRITE,
                                      StandardOpenOption.SPARSE);
            } catch (FileAlreadyExistsException ignore) {
                // someone else got it
            }
        }
        sink.deleteOnExit();

        long testSize = ((long)Integer.MAX_VALUE) * 2;
        try {
            fc.write(ByteBuffer.wrap("Use the source!".getBytes()),
                     testSize - 40);
        } catch (IOException e) {
            // Can't set up the test, abort it
            System.err.println("xferTest05 was aborted.");
            return;
        } finally {
            fc.close();
        }

        // Get new channels for the source and sink and attempt transfer
        FileChannel sourceChannel = new FileInputStream(source).getChannel();
        try {
            FileChannel sinkChannel = new RandomAccessFile(sink, "rw").getChannel();
            try {
                long bytesWritten = sinkChannel.transferFrom(sourceChannel,
                                                             testSize - 40, 10);
                if (bytesWritten != 10) {
                    throw new RuntimeException("Transfer test 5 failed " +
                                               bytesWritten);
                }
            } finally {
                sinkChannel.close();
            }
        } finally {
            sourceChannel.close();
        }

        source.delete();
        sink.delete();
    }

    static void checkFileData(File file, String expected) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        Reader r = new BufferedReader(new InputStreamReader(fis, "ASCII"));
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = r.read()) != -1)
            sb.append((char)c);
        String contents = sb.toString();
        if (! contents.equals(expected))
            throw new Exception("expected: " + expected
                                + ", got: " + contents);
        r.close();
    }

    // Test transferFrom asking for more bytes than remain in source
    public static void xferTest06() throws Exception {
        String data = "Use the source, Luke!";

        File source = File.createTempFile("source", null);
        source.deleteOnExit();
        File sink = File.createTempFile("sink", null);
        sink.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(source);
        fos.write(data.getBytes("ASCII"));
        fos.close();

        FileChannel sourceChannel =
            new RandomAccessFile(source, "rw").getChannel();
        sourceChannel.position(7);
        long remaining = sourceChannel.size() - sourceChannel.position();
        FileChannel sinkChannel =
            new RandomAccessFile(sink, "rw").getChannel();
        long n = sinkChannel.transferFrom(sourceChannel, 0L,
                                          sourceChannel.size()); // overflow
        if (n != remaining)
            throw new Exception("n == " + n + ", remaining == " + remaining);

        sinkChannel.close();
        sourceChannel.close();

        checkFileData(source, data);
        checkFileData(sink, data.substring(7,data.length()));

        source.delete();
    }

    // Test transferTo to non-blocking socket channel
    public static void xferTest07() throws Exception {
        File source = File.createTempFile("source", null);
        source.deleteOnExit();

        FileChannel sourceChannel = new RandomAccessFile(source, "rw")
            .getChannel();
        sourceChannel.position(32000L)
            .write(ByteBuffer.wrap("The End".getBytes()));

        // The sink is a non-blocking socket channel
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(0));
        InetSocketAddress sa = new InetSocketAddress(
            InetAddress.getLocalHost(), ssc.socket().getLocalPort());
        SocketChannel sink = SocketChannel.open(sa);
        sink.configureBlocking(false);
        SocketChannel other = ssc.accept();

        long size = sourceChannel.size();

        // keep sending until congested
        long n;
        do {
            n = sourceChannel.transferTo(0, size, sink);
        } while (n > 0);

        sourceChannel.close();
        sink.close();
        other.close();
        ssc.close();
        source.delete();
    }


    // Test transferTo with file positions larger than 2 and 4GB
    public static void xferTest08() throws Exception {
        // Creating a sparse 6GB file on Windows takes too long
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows"))
            return;

        final long G = 1024L * 1024L * 1024L;

        // Create 6GB file

        File file = File.createTempFile("source", null);
        file.deleteOnExit();

        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        FileChannel fc = raf.getChannel();

        try {
            fc.write(ByteBuffer.wrap("0123456789012345".getBytes("UTF-8")), 6*G);
        } catch (IOException x) {
            System.err.println("Unable to create test file:" + x);
            fc.close();
            return;
        }

        // Setup looback connection and echo server

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(0));

        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress isa = new InetSocketAddress(lh, ssc.socket().getLocalPort());
        SocketChannel source = SocketChannel.open(isa);
        SocketChannel sink = ssc.accept();

        Thread thr = new Thread(new EchoServer(sink));
        thr.start();

        // Test data is array of positions and counts

        long testdata[][] = {
            { 2*G-1,    1 },
            { 2*G-1,    10 },       // across 2GB boundary
            { 2*G,      1 },
            { 2*G,      10 },
            { 2*G+1,    1 },
            { 4*G-1,    1 },
            { 4*G-1,    10 },       // across 4GB boundary
            { 4*G,      1 },
            { 4*G,      10 },
            { 4*G+1,    1 },
            { 5*G-1,    1 },
            { 5*G-1,    10 },
            { 5*G,      1 },
            { 5*G,      10 },
            { 5*G+1,    1 },
            { 6*G,      1 },
        };

        ByteBuffer sendbuf = ByteBuffer.allocateDirect(100);
        ByteBuffer readbuf = ByteBuffer.allocateDirect(100);

        try {
            byte value = 0;
            for (int i=0; i<testdata.length; i++) {
                long position = testdata[(int)i][0];
                long count = testdata[(int)i][1];

                // generate bytes
                for (long j=0; j<count; j++) {
                    sendbuf.put(++value);
                }
                sendbuf.flip();

                // write to file and transfer to echo server
                fc.write(sendbuf, position);
                fc.transferTo(position, count, source);

                // read from echo server
                long nread = 0;
                while (nread < count) {
                    int n = source.read(readbuf);
                    if (n < 0)
                        throw new RuntimeException("Premature EOF!");
                    nread += n;
                }

                // check reply from echo server
                readbuf.flip();
                sendbuf.flip();
                if (!readbuf.equals(sendbuf))
                    throw new RuntimeException("Echo'ed bytes do not match!");
                readbuf.clear();
                sendbuf.clear();
            }
        } finally {
            source.close();
            ssc.close();
            fc.close();
            file.delete();
        }
    }

    // Test that transferFrom with FileChannel source that is not readable
    // throws NonReadableChannelException
    static void xferTest09() throws Exception {
        File source = File.createTempFile("source", null);
        source.deleteOnExit();

        File target = File.createTempFile("target", null);
        target.deleteOnExit();

        FileChannel fc1 = new FileOutputStream(source).getChannel();
        FileChannel fc2 = new RandomAccessFile(target, "rw").getChannel();
        try {
            fc2.transferFrom(fc1, 0L, 0);
            throw new RuntimeException("NonReadableChannelException expected");
        } catch (NonReadableChannelException expected) {
        } finally {
            fc1.close();
            fc2.close();
        }
    }

    /**
     * Creates file blah of specified size in bytes.
     */
    private static void initTestFile(File blah, long size) throws Exception {
        if (blah.exists())
            blah.delete();
        FileOutputStream fos = new FileOutputStream(blah);
        BufferedWriter awriter
            = new BufferedWriter(new OutputStreamWriter(fos, "8859_1"));

        for(int i=0; i<size; i++) {
            awriter.write("e");
        }
        awriter.flush();
        awriter.close();
    }

    /**
     * Simple in-process server to echo bytes read by a given socket channel
     */
    static class EchoServer implements Runnable {
        private SocketChannel sc;

        public EchoServer(SocketChannel sc) {
            this.sc = sc;
        }

        public void run() {
            ByteBuffer bb = ByteBuffer.allocateDirect(1024);
            try {
                for (;;) {
                    int n = sc.read(bb);
                    if (n < 0)
                        break;

                    bb.flip();
                    while (bb.remaining() > 0) {
                        sc.write(bb);
                    }
                    bb.clear();
                }
            } catch (IOException x) {
                x.printStackTrace();
            } finally {
                try {
                    sc.close();
                } catch (IOException ignore) { }
            }
        }
    }

}
