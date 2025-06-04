/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 @bug 4115130
 @summary Tests Inserting/Removing items doesn't cause crash.
 @key headful
 @run main InsertRemoveTest
 */

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;

public class InsertRemoveTest {
    Choice choice1;
    Choice choice2;
    Choice choice3;
    Frame f;
    int itemCount = 0;
    int iterCount = 0;

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> new InsertRemoveTest().start());
    }

    public void start() {
        f = new Frame("Check Choice");
        f.setLayout(new BorderLayout());

        choice1 = new Choice();
        choice2 = new Choice();
        choice3 = new Choice();

        f.add(choice1, BorderLayout.NORTH);
        f.add(choice3, BorderLayout.CENTER);
        f.add(choice2, BorderLayout.SOUTH);

        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);

        try {
            for (int i = 0; i < 50; i++) {
                if (choice1 != null && itemCount < 40) {
                    choice1.insert("I am Choice, yes I am : " + iterCount,
                            0);
                    choice2.add("I am the same, yes I am : " + iterCount);
                    choice3.insert("I am the same, yes I am : " + iterCount,
                            10);
                    itemCount++;
                    iterCount++;
                }
                if (itemCount >= 20 && choice1 != null
                        && choice1.getItemCount() > 0) {
                    choice1.remove(0);
                    choice2.remove(10);
                    choice3.remove(19);
                    itemCount--;
                }
                f.validate();
            }
        } finally {
            f.dispose();
        }
    }

}
