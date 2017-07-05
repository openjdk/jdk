/*
 * Copyright 2009 Google, Inc.  All Rights Reserved.
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
 * @test
 * @bug 4206909
 * @summary Test basic functionality of DeflaterOutputStream and InflaterInputStream including flush
 */

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class InflateIn_DeflateOut {

    private static class PairedInputStream extends ByteArrayInputStream {
        private PairedOutputStream out = null;
        private Random random;

        public PairedInputStream() {
            // The ByteArrayInputStream needs to start with a buffer, but we
            // need to set it to have no data
            super(new byte[1]);
            count = 0;
            pos = 0;
            random = new Random(new Date().getTime());
        }

        public void setPairedOutputStream(PairedOutputStream out) {
            this.out = out;
        }

        private void maybeFlushPair() {
            if (random.nextInt(100) < 10) {
                out.flush();
            }
        }

        public int read() {
            maybeFlushPair();
            return super.read();
        }

        public int read(byte b[], int off, int len) {
            maybeFlushPair();
            return super.read(b, off, len);
        }

        public void addBytes(byte[] bytes, int len) {
            int oldavail = count - pos;
            int newcount = oldavail + len;
            byte[] newbuf = new byte[newcount];
            System.arraycopy(buf, pos, newbuf, 0, oldavail);
            System.arraycopy(bytes, 0, newbuf, oldavail, len);
            pos = 0;
            count = newcount;
            buf = newbuf;
        }
    }

    private static class PairedOutputStream extends ByteArrayOutputStream {
      private PairedInputStream pairedStream = null;

      public PairedOutputStream(PairedInputStream inputPair) {
        super();
        this.pairedStream = inputPair;
      }

      public void flush() {
        if (count > 0) {
          pairedStream.addBytes(buf, count);
          reset();
        }
      }

      public void close() {
        flush();
      }
    }

    private static boolean readFully(InputStream in, byte[] buf, int length)
            throws IOException {
        int pos = 0;
        int n;
        while ((n = in.read(buf, pos, length - pos)) > 0) {
            pos += n;
            if (pos == length) return true;
        }
        return false;
    }

    private static boolean readLineIfAvailable(InputStream in, StringBuilder sb)
            throws IOException {
        try {
            while (in.available() > 0) {
                int i = in.read();
                if (i < 0) break;
                char c = (char) (((byte) i) & 0xff);
                sb.append(c);
                if (c == '\n') return true;
            }
        } catch (EOFException e) {
          // empty
        }
        return false;
    }

    /** Check that written, closed and read */
    private static void WriteCloseRead() throws Throwable {
        Random random = new Random(new Date().getTime());

        PairedInputStream pis = new PairedInputStream();
        InflaterInputStream iis = new InflaterInputStream(pis);

        PairedOutputStream pos = new PairedOutputStream(pis);
        pis.setPairedOutputStream(pos);
        DeflaterOutputStream dos = new DeflaterOutputStream(pos, true);

        byte[] data = new byte[random.nextInt(1024 * 1024)];
        byte[] buf = new byte[data.length];
        random.nextBytes(data);

        dos.write(data);
        dos.close();
        check(readFully(iis, buf, buf.length));
        check(Arrays.equals(data, buf));
    }

    /** Check that written, flushed and read */
    private static void WriteFlushRead() throws Throwable {
        Random random = new Random(new Date().getTime());

        PairedInputStream pis = new PairedInputStream();
        InflaterInputStream iis = new InflaterInputStream(pis);

        PairedOutputStream pos = new PairedOutputStream(pis);
        pis.setPairedOutputStream(pos);
        DeflaterOutputStream dos = new DeflaterOutputStream(pos, true);

        // Large writes
        for (int x = 0; x < 200 ; x++) {
            // byte[] data = new byte[random.nextInt(1024 * 1024)];
            byte[] data = new byte[1024];
            byte[] buf = new byte[data.length];
            random.nextBytes(data);

            dos.write(data);
            dos.flush();
            check(readFully(iis, buf, buf.length));
            check(Arrays.equals(data, buf));
        }

        // Small writes
        for (int x = 0; x < 2000 ; x++) {
            byte[] data = new byte[random.nextInt(20) + 10];
            byte[] buf = new byte[data.length];
            random.nextBytes(data);

            dos.write(data);
            dos.flush();
            if (!readFully(iis, buf, buf.length)) {
                fail("Didn't read full buffer of " + buf.length);
            }
            check(Arrays.equals(data, buf));
        }

        String quit = "QUIT\r\n";

        // Close it out
        dos.write(quit.getBytes());
        dos.close();

        StringBuilder sb = new StringBuilder();
        check(readLineIfAvailable(iis, sb));
        equal(sb.toString(), quit);
    }

    /** Validate that we need to use flush at least once on a line
     * oriented protocol */
    private static void LineOrientedProtocol() throws Throwable {
        PairedInputStream pis = new PairedInputStream();
        InflaterInputStream iis = new InflaterInputStream(pis);

        PairedOutputStream pos = new PairedOutputStream(pis);
        pis.setPairedOutputStream(pos);
        DeflaterOutputStream dos = new DeflaterOutputStream(pos, true);

        boolean flushed = false;
        int count = 0;

        // Do at least a certain number of lines, but too many without a
        // flush means this test isn't testing anything
        while ((count < 10 && flushed) || (count < 1000 && !flushed)) {
            String command = "PING " + count + "\r\n";
            dos.write(command.getBytes());

            StringBuilder buf = new StringBuilder();
            if (!readLineIfAvailable(iis, buf)) {
                flushed = true;
                dos.flush();
                check(readLineIfAvailable(iis, buf));
            }
            equal(buf.toString(), command);
            count++;
        }
        check(flushed);
    }

    public static void realMain(String[] args) throws Throwable {
        WriteCloseRead();

        WriteFlushRead();

        LineOrientedProtocol();
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
