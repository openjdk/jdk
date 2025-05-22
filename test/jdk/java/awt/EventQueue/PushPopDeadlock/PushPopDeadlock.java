/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4212687
 * @summary Verifies that calling EventQueue.push() and EventQueue.pop()
 *          does not deadlock.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PushPopDeadlock
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Robot;
import java.awt.Toolkit;

public class PushPopDeadlock {
    static int counter = 0;
    static Robot robot;
    static Frame f;
    static Label l;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        String INSTRUCTIONS = """
                Click rapidly in the Frame labeled 'Click Here!'.
                The number in the Frame should continue to increase. If the number
                stops increasing (remains at a constant value), the test fails.
                """;

        PassFailJFrame pfJFrame = PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(PushPopDeadlock::createUI)
                .build();
        PushPopDeadlock.test();
        pfJFrame.awaitAndCheck();
    }

    public static Frame createUI() {
        f = new Frame("Click Here!");
        l = new Label("Counter: " + counter);
        f.add(l);
        f.setSize(200, 200);
        return f;
    }

    public static void test() {
        EventQueue q = new EventQueue() {
            public void push(EventQueue queue) {
                super.push(queue);
                pop();
            }
        };
        EventQueue q2 = new EventQueue();

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(q);

        new Thread(() -> {
            while (true) {
                robot.delay(500);
                l.setText("Counter: " + ++counter);
                q.push(q2);
                try {
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }).start();
    }
}
