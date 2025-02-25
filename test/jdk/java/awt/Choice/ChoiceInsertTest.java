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

import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Robot;

/*
 * @test
 * @bug 4082078
 * @summary Test for bug(s): 4082078, Multiple calls to Choice.insert cause core dump
 * @key headful
 * @run main ChoiceInsertTest
 */

public class ChoiceInsertTest extends Frame {
    Choice c;
    Label  l;

    private static ChoiceInsertTest choiceInsertTest;

    public ChoiceInsertTest() {
        c = new Choice();
        l = new Label("If you see this, the choice insert bug is fixed!");
        c.add("Initial choice");
        add(c);
    }

    public void testInsertion() {
        // inserting 30 or so items aborts Solaris VM
        // in JDK's before 1.1.5
        for (int nchoice = 0; nchoice < 30; nchoice++) {
            c.insert("new choice", 0);
        }
        // if you made it to here the bug is not there anymore...
        remove(l);
        add(l);
        validate();
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() ->{
                choiceInsertTest = new ChoiceInsertTest();
                choiceInsertTest.setTitle("ChoiceInsertTest");
                choiceInsertTest.setLocationRelativeTo(null);
                choiceInsertTest.setSize(500, 300);
                choiceInsertTest.setLayout(new GridLayout());
                choiceInsertTest.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(500);
            EventQueue.invokeAndWait(choiceInsertTest::testInsertion);
            robot.delay(1000);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (choiceInsertTest != null) {
                    choiceInsertTest.dispose();
                }
            });
        }

        System.err.println("ChoiceInsertTest: Didn't abort VM inserting 30 items, so we passed!");
    }
}
