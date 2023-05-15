/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4245382
  @summary Tests that Button.setLabel(null) does not cause NPE in Java code or VM crash
  @key headful
*/

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;

public class ButtonNullLabelTest {

    public static void main(String args[]) throws Exception {
        EventQueue.invokeAndWait(() -> runTest());
    }

   static void runTest() {
        // Native code test
        Frame frame = new Frame("Test null in native");
        Button button = new Button();
        try {
            button.setLabel(null);
            System.out.println("Set to null - test native");
            frame.add(button);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            System.out.println("Test null in native **successful**");
        } catch (NullPointerException npe) {
            System.out.println("Test failed - test native");
            throw new RuntimeException("Test failed - test native");
        } finally {
            frame.dispose();
        }

        // Peer code test
        frame = new Frame("Test null in peer before show");
        button = new Button();
        try {
            System.out.println("Set to null - test native before show");
            frame.add(button);
            frame.pack();
            button.setLabel(null);
            frame.setVisible(true);
            System.out.println("Set null in peer before show **successful**");
        } catch (NullPointerException npe) {
            System.out.println("Test failed - test peer before show");
            throw new RuntimeException("Test failed - test peer before show");
        } finally {
            frame.dispose();
        }

        // Peer code test
        frame = new Frame("Test null in peer after show");
        button = new Button();
        try {
            System.out.println("Set to null - test peer after show");
            frame.add(button);
            frame.pack();
            frame.setVisible(true);
            button.setLabel(null);
            System.out.println("Test null in peer after show **successful**");
        } catch (NullPointerException npe) {
            System.out.println("Test failed - peer after show");
            throw new RuntimeException("Test failed - peer after show");
        } finally {
            frame.dispose();
        }
    }
}
