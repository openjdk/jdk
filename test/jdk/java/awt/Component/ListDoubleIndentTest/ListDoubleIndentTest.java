/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4185460
 * @summary Container list the indentation is 2x the indent param value
 * @key headful
 * @run main ListDoubleIndentTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PrintStream;
import java.io.PipedOutputStream;

import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

public class ListDoubleIndentTest {
    public static void main(final String[] args) throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(new ListDoubleIndentTest()::performTest);
    }

    public void performTest() {
        boolean bReturn = false;
        int iCompCount = 0;
        int iNotEqual = 0;
        int iIndentWrong = 0;
        System.out.println("Test: Check indentation");
        Vector v = new Vector();
        String sLine;
        String sReturn;
        String sExpTrim;
        Button b1, b2, b3, b4, b5;
        Frame f = null;

        try {
            f = new Frame("ListDoubleIndentTest");

            f.add(b1 = new Button("North"), BorderLayout.NORTH, 0);
            f.add(b2 = new Button("South"), BorderLayout.SOUTH, 1);
            f.add(b3 = new Button("East"), BorderLayout.EAST, 2);
            f.add(b4 = new Button("West"), BorderLayout.WEST, 3);
            f.add(b5 = new Button("Center"), BorderLayout.CENTER, -1);

            String[] sExpected = {f.toString(), b1.toString(), b2.toString(),
                    b3.toString(), b4.toString(), b5.toString()};

            iCompCount = f.getComponentCount();
            System.out.println("Component count: " + iCompCount);

            for (int j = 0; j <= 10; j++) {
                PipedInputStream pin = new PipedInputStream();
                PrintStream output = new PrintStream(new PipedOutputStream(pin), true);
                BufferedReader input = new BufferedReader(new InputStreamReader(pin));

                f.list(output, j);

                output.flush();
                output.close();

                while ((sLine = input.readLine()) != null) {
                    v.addElement(sLine);
                }

                for (int i = 0; i < v.size(); i++) {
                    sReturn = (String)v.elementAt(i);
                    sExpTrim = sExpected[i].trim();

                    if (!(sExpTrim.equals(sReturn.trim()))) {
                        System.out.println("iNotEqual");
                        ++iNotEqual;
                    }

                    int iSpace = sReturn.lastIndexOf(' ') + 1;

                    if (i == 0) {
                        System.out.println("Indent set at: " + j);
                        System.out.println("Indent return: " + iSpace);
                        if (iSpace != j) {
                            System.out.println("iIndentWrong1");
                            ++iIndentWrong;
                        }
                    } else {
                        if (iSpace != (j + 1)) {
                            System.out.println(iSpace + "; " + j);
                            ++iIndentWrong;
                        }
                    }
                    System.out.println(sReturn);
                }
                v.removeAllElements();
                v.trimToSize();
            }

            if (iNotEqual == 0 && iIndentWrong == 0) {
                bReturn = true;
            } else {
                bReturn = false;
            }

        } catch(IOException e) {
            bReturn = false;
            System.out.println ("Unexpected Exception thrown: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (f != null) {
                f.dispose();
            }
        }

        if (bReturn) {
            System.out.println("Test for Container.list Passed");
        } else {
            System.out.println("Test for Container.list Failed");
            throw new RuntimeException("Test FAILED");
        }
    }
}
