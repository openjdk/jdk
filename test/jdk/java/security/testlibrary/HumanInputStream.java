/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * HumanInputStream tries to act like a human sitting in front of a computer
 * terminal typing on the keyboard while a program is running.
 * <p>
 * The program may call InputStream.read() and BufferedReader.readLine() in
 * various places. a call to B.readLine() will try to buffer as much input as
 * possible. Thus, a trivial InputStream will find it impossible to feed
 * anything to I.read() after a B.readLine() call.
 * <p>
 * This is why HumanInputStream was created, which will only send a single line
 * to B.readLine(), no more, no less, and the next I.read() can have a chance
 * to read the exact character right after "\n".
 *
 */

public class HumanInputStream extends InputStream {
    byte[] src;
    int pos;
    int length;
    boolean inLine;
    int stopIt;

    public HumanInputStream(String input) {
        src = input.getBytes();
        pos = 0;
        length = src.length;
        stopIt = 0;
        inLine = false;
    }

    // the trick: when called through read(byte[], int, int),
    // return -1 twice after "\n"

    @Override public int read() throws IOException {
        int re;
        if(pos < length) {
            re = src[pos];
            if(inLine) {
                if(stopIt > 0) {
                    stopIt--;
                    re = -1;
                } else {
                    if(re == '\n') {
                        stopIt = 2;
                    }
                    pos++;
                }
            } else {
                pos++;
            }
        } else {
            re = -1; //throws new IOException("NO MORE TO READ");
        }
        return re;
    }
    @Override public int read(byte[] buffer, int offset, int len) {
        inLine = true;
        try {
            return super.read(buffer, offset, len);
        } catch(Exception e) {
            throw new RuntimeException("HumanInputStream error");
        } finally {
            inLine = false;
        }
    }
    @Override public int available() {
        if (pos < length) return 1;
        return 0;
    }

    // test part
    static void assertTrue(boolean bool) {
        if (!bool)
            throw new RuntimeException();
    }

    public static void test() throws Exception {
        class Tester {
            HumanInputStream is;
            BufferedReader reader;
            Tester(String s) {
                is = new HumanInputStream(s);
                reader = new BufferedReader(new InputStreamReader(is));
            }

            // three kinds of test method
            // 1. read byte by byte from InputStream
            void testStreamReadOnce(int expection) throws Exception {
                assertTrue(is.read() == expection);
            }
            void testStreamReadMany(String expectation) throws Exception {
                char[] keys = expectation.toCharArray();
                for (char key : keys) {
                    assertTrue(is.read() == key);
                }
            }
            // 2. read a line with a newly created Reader
            void testReaderReadline(String expectation) throws Exception {
                String s = new BufferedReader(new InputStreamReader(is)).readLine();
                if(s == null) assertTrue(expectation == null);
                else assertTrue(s.equals(expectation));
            }
            // 3. read a line with the old Reader
            void testReaderReadline2(String expectation) throws Exception  {
                String s = reader.readLine();
                if(s == null) assertTrue(expectation == null);
                else assertTrue(s.equals(expectation));
            }
        }

        Tester test;

        test = new Tester("111\n222\n\n444\n\n");
        test.testReaderReadline("111");
        test.testReaderReadline("222");
        test.testReaderReadline("");
        test.testReaderReadline("444");
        test.testReaderReadline("");
        test.testReaderReadline(null);

        test = new Tester("111\n222\n\n444\n\n");
        test.testReaderReadline2("111");
        test.testReaderReadline2("222");
        test.testReaderReadline2("");
        test.testReaderReadline2("444");
        test.testReaderReadline2("");
        test.testReaderReadline2(null);

        test = new Tester("111\n222\n\n444\n\n");
        test.testReaderReadline2("111");
        test.testReaderReadline("222");
        test.testReaderReadline2("");
        test.testReaderReadline2("444");
        test.testReaderReadline("");
        test.testReaderReadline2(null);

        test = new Tester("1\n2");
        test.testStreamReadMany("1\n2");
        test.testStreamReadOnce(-1);

        test = new Tester("12\n234");
        test.testStreamReadOnce('1');
        test.testReaderReadline("2");
        test.testStreamReadOnce('2');
        test.testReaderReadline2("34");
        test.testReaderReadline2(null);

        test = new Tester("changeit\n");
        test.testStreamReadMany("changeit\n");
        test.testReaderReadline(null);

        test = new Tester("changeit\nName\nCountry\nYes\n");
        test.testStreamReadMany("changeit\n");
        test.testReaderReadline("Name");
        test.testReaderReadline("Country");
        test.testReaderReadline("Yes");
        test.testReaderReadline(null);

        test = new Tester("Me\nHere\n");
        test.testReaderReadline2("Me");
        test.testReaderReadline2("Here");
    }
}
