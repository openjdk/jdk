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

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Frame;
import jdk.test.lib.Platform;

/*
 * @test
 * @bug 4134619
 * @summary    Tests that the EventDispatchThread doesn't deadlock with
 *             user threads which are modifying a Choice component.
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @run main/manual DeadlockTest
 */

public class DeadlockTest extends Thread {

    static volatile Choice choice1;
    static volatile Choice choice2;
    static volatile Choice choice3;
    static volatile Frame frame;
    static int itemCount = 0;

    private static final boolean isWindows = Platform.isWindows();

    private static final String INSTRUCTIONS = """
            Click on the top Choice component and hold the mouse still briefly.
            Then, without releasing the mouse button, move the cursor to a menu
            item and then again hold the mouse still briefly.
            %s
            Release the button and repeat this process.

            Verify that this does not cause a deadlock
            or crash within a reasonable amount of time.
            """.formatted(
                isWindows
                    ? "(menu can automatically collapse sometimes, this is ok)\n"
                    : ""

    )       ;

    public static void main(String[] args) throws Exception {
        DeadlockTest deadlockTest = new DeadlockTest();
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("DeadlockTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(deadlockTest::createAndShowUI)
                .build();

        deadlockTest.start();

        passFailJFrame.awaitAndCheck();
    }

   public Frame createAndShowUI() {
       frame = new Frame("Check Choice");
       frame.setLayout(new BorderLayout());
       choice1 = new Choice();
       choice2 = new Choice();
       choice3 = new Choice();
       frame.add(choice1, BorderLayout.NORTH);
       frame.add(choice3, BorderLayout.CENTER);
       frame.add(choice2, BorderLayout.SOUTH);
       frame.pack();
       return frame;
   }

    public void run() {
        while (true) {
            if (choice1 != null && itemCount < 40) {
                choice1.add("I am Choice, yes I am : " + itemCount * itemCount);
                choice2.add("I am the same, yes I am : " + itemCount * itemCount);
                choice3.add("I am the same, yes I am : " + itemCount * itemCount);
                itemCount++;
            }
            if (itemCount >= 20 && choice1 != null &&
                    choice1.getItemCount() > 0) {
                choice1.removeAll();
                choice2.removeAll();
                choice3.removeAll();
                itemCount = 0;
            }
            frame.validate();
            try {
                Thread.sleep(1000);
            } catch (Exception ignored) {}
        }
    }
}
