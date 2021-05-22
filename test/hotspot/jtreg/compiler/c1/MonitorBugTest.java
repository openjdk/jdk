/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c1;

import java.io.IOException;
import java.io.InterruptedIOException;

/*
 * @test
 * @author Chris Cole
 * @bug 8267042
 * @summary missing displaced_header initialization causes hangup
 * @run main/othervm -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *                   -XX:-BackgroundCompilation -XX:CompileThreshold=1
 *                   -XX:CompileOnly=compiler.c1.MonitorBugTest::receive
 *                   compiler.c1.MonitorBugTest
 */
public class MonitorBugTest {

    private static int DATA_SIZE = 1000;
    private static int BUFFER_SIZE = 256;

    private char buffer[] = new char[BUFFER_SIZE];
    private int writeIndex = -1;
    private int readIndex = 0;

    public Object lock = new Object();

    public static void main(String[] args) {
        MonitorBugTest test = new MonitorBugTest();
        test.run();
    }
    private void run() {
        System.out.println("Starting test");

        SourceThread source = new SourceThread();
        source.start();

        try {
            for (int i = 0; i < DATA_SIZE; i++) {
                read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Reading complete");

    }

    synchronized void receive(char data[], int offset, int length) throws IOException {
        while (--length >= 0) {
            getZeroOnStack(offset);
            receive(data[offset++]);
        }
    }

    private void getZeroOnStack(int offset) {
        int l1;
        int l2;
        int l3;
        int l4;
        int l5;
        int l6;
        int l7;
        int l8;
        int l9;
        int l10;
        int l11;
        int l12;
        int l13;
        int l14;
        int l15;
        int l16;

        l1 = 0;
        l2 = 0;
        l3 = 0;
        l4 = 0;
        l5 = 0;
        l6 = 0;
        l7 = 0;
        l8 = 0;
        l9 = 0;
        l10 = 0;
        l11 = 0;
        l12 = 0;
        l13 = 0;
        l14 = 0;
        l15 = 0;
        l16 = 0;
    }

    synchronized void receive(int c) throws IOException {
        while (writeIndex == readIndex) {
            notifyAll();
            try {
                wait(1000);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        if (writeIndex < 0) {
            writeIndex = 0;
            readIndex = 0;
        }
        buffer[writeIndex++] = (char) c;
        if (writeIndex >= buffer.length) {
            writeIndex = 0;
        }
    }

    synchronized void last() {
        notifyAll();
    }
    public synchronized int read() throws IOException {
        while (writeIndex < 0) {
            notifyAll();
            try {
                System.out.println("read() before wait");
                wait(1000);
                System.out.println("read() after wait");
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        int value = buffer[readIndex++];
        if (readIndex >= buffer.length) {
            readIndex = 0;
        }
        if (writeIndex == readIndex) {
            writeIndex = -1;
        }
        return value;
    }

    private class SourceThread extends Thread {
        @Override
        public void run() {
            System.out.println("Source thread start");
            char data[] = new char[DATA_SIZE];
            try {
                receive(data, 0, data.length);
                last();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Source thread done");
        }
    }
}

