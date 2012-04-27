/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
   @bug 7146146
   @summary Deadlock between subclass of AbstractDocument and UndoManager
   @author Pavel Porvatov
*/

import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.StringContent;
import javax.swing.undo.UndoManager;

public class bug7146146 {
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 1000; i++) {
            System.out.print("Iteration " + i);

            test();

            System.out.print(" passed");
        }
    }

    private static void test() throws Exception {
        final PlainDocument doc = new PlainDocument(new StringContent());
        final UndoManager undoManager = new UndoManager();

        doc.addUndoableEditListener(undoManager);
        doc.insertString(0, "<Test 1>", null);

        Thread t1 = new Thread("Thread 1") {
            @Override
            public void run() {
                try {
                    doc.insertString(0, "<Test 2>", null);
                } catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Thread t2 = new Thread("Thread 2") {
            @Override
            public void run() {
                undoManager.undo();
            }
        };

        t1.start();
        t2.start();

        t1.join();
        t2.join();
    }
}
