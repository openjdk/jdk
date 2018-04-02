/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8152974
 * @key headful
 * @modules java.desktop/sun.awt
 * @summary AWT hang occurs when sequenced events arrive out of sequence
 * @run main SequencedEventTest
 */
import sun.awt.AppContext;
import sun.awt.SunToolkit;

import java.awt.Robot;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;

public class SequencedEventTest extends JFrame implements ActionListener {
    private JButton spamMeButton;
    private static Robot robot;
    private static SequencedEventTest window;
    private static AppContext context;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() ->  {
            window = new SequencedEventTest();
            window.setVisible(true);
        });

        robot = new Robot();
        robot.waitForIdle();

        Point pt  = window.spamMeButton.getLocationOnScreen();
        Dimension d = window.spamMeButton.getSize();

        robot.mouseMove(pt.x + d.width / 2, pt.y + d.height / 2);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        /*
         *Cannot have robot.waitForIdle() here since it will block the test forever,
         * in the case of failure and the test will timeout.
         */

        try {
            /*
             * Wait for 2 seconds, and then see if all the sequenced events are dispatched.
             */
            Thread.sleep(2000);
            AWTEvent ev = Toolkit.getDefaultToolkit().getSystemEventQueue().
                    peekEvent(java.awt.event.FocusEvent.FOCUS_LAST + 1);

            if (ev != null)
                throw new RuntimeException("Test case failed, since all the sequenced events" +
                "are not flushed!" + ev);
        } catch (InterruptedException e) {
            throw new RuntimeException("Test case failed." + e.getMessage());
        }

        /*
         * In the case of failure, the cleanup job cannot be executed, since it
         * will block the test.
         */
        System.out.println("Test case succeeded.");
        context.dispose();
        SwingUtilities.invokeAndWait(() -> window.dispose());
    }

    public SequencedEventTest() {
        super("Test Window");

        setLayout(new FlowLayout());
        JTextArea textBlock = new JTextArea("Lorem ipsum dolor sit amet...");
        add(textBlock);

        spamMeButton = new JButton("Press me!");
        spamMeButton.addActionListener(this);
        add(spamMeButton);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource() == spamMeButton) {
            AWTEvent eventOne = getSequencedEvent();
            AWTEvent eventFour = getSequencedEvent();
            ThreadGroup tg = new ThreadGroup("TestThreadGroup" );
            CountDownLatch latch = new CountDownLatch(1);
            Thread t = new Thread(tg, () -> {
                context = SunToolkit.createNewAppContext();
                AWTEvent eventTwo = getSequencedEvent();
                AWTEvent eventThree = getSequencedEvent();

                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(eventThree);
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new ActionEvent(this, 0, null));
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new ActionEvent(this, 1, null));
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(eventTwo);

                latch.countDown();
            });

            t.start();
            try {
                latch.await();
            }catch (InterruptedException ex) {
                throw new RuntimeException("Test case failed.");
            }

            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(eventFour);
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new ActionEvent(this, 2, null));
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new ActionEvent(this, 3, null));
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(eventOne);

            try {
                t.join();
            } catch (InterruptedException ex) {
                throw new RuntimeException("Test case failed.");
            }
        }
    }

    private AWTEvent getSequencedEvent()
    {
        AWTEvent wrapMe = new AWTEvent(this, AWTEvent.RESERVED_ID_MAX) {};

        try {
            /*
             * SequencedEvent is a package private class, which cannot be instantiated
             * by importing. So use reflection to create an instance.
             */
            Class<? extends AWTEvent> seqClass = (Class<? extends AWTEvent>) Class.forName("java.awt.SequencedEvent");
            Constructor<? extends AWTEvent> seqConst = seqClass.getConstructor(AWTEvent.class);
            seqConst.setAccessible(true);
            return seqConst.newInstance(wrapMe);
        } catch (Throwable err) {
            throw new RuntimeException("Unable to instantiate SequencedEvent",err);
        }
    }
}
