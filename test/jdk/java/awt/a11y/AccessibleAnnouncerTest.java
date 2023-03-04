/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug JDK-8302687
 * @summary Test implementation of accessibility announcing
 * @run main/manual AccessibleAnnouncerTest
 */

import javax.accessibility.AccessibleAnnouncer;

import javax.swing.JAccessibleAnnouncer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.Rectangle;
import java.util.concurrent.CountDownLatch;
import java.lang.Thread;

public class AccessibleAnnouncerTest extends AccessibleComponentTest {

    @java.lang.Override
    public CountDownLatch createCountDownLatch() {
        return new CountDownLatch(1);
    }

    void createTest() {
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check announcing.\n\n"
                + "Turn screen reader on, and Tab to the say button and press it.\n\n"
                + "If you can hear text from text field tab further and press PASS, otherwise press FAIL.\n";
        ;

        JPanel frame = new JPanel();

        JButton button = new JButton("Say");
        button.setPreferredSize(new Dimension(100, 35));
        JTextField textField = new JTextField("This is text");

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String str = textField.getText();
                AccessibleAnnouncer accessibleAnnouncer = JAccessibleAnnouncer.getJAccessibleAnnouncer();
                if (accessibleAnnouncer== null) {
                    return;
                }
                accessibleAnnouncer.announce(button, str, AccessibleAnnouncer.ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT);
            }
        });

        frame.setLayout(new FlowLayout());
        frame.add(textField);
        frame.add(button);
        exceptionString = "Accessible announcer test failed!";
        super.createUI(frame, "Accessible Anouncer test");
    }

    void createPriorityTest() {
        String firstMessage = "This is first message";
        String secondMessage = "This is second message";
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check announcing priority.\n\n"
                + "Turn screen reader on, and Tab to the say button and press.\n\n"
                + "If you can hear \"" + firstMessage
                + "\" and \"" + secondMessage
                + "\" tab further and press PASS, otherwise press FAIL.\n";;

        JPanel frame = new JPanel();

        JButton button = new JButton("Say");
        button.setPreferredSize(new Dimension(100, 35));

        button.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                AccessibleAnnouncer accessibleAnnouncer = JAccessibleAnnouncer.getJAccessibleAnnouncer();
                if (accessibleAnnouncer == null) {
                    return;
                }
                accessibleAnnouncer.announce(button, firstMessage, AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
                try {
                    Thread.sleep(3000);
                    accessibleAnnouncer.announce(button, "You must not hear this message.", AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
                    accessibleAnnouncer.announce(button, secondMessage, AccessibleAnnouncer.ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT);
                 } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        frame.setLayout(new FlowLayout());
        frame.add(button);
        exceptionString = "Accessible announcer priority test failed!";
        super.createUI(frame, "Accessible Anouncer test");
    }

    public static void main(String[] args)  throws Exception {
        AccessibleAnnouncerTest test = new AccessibleAnnouncerTest();

        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeLater(test::createTest);
        countDownLatch.await();

        if (!testResult) {
            throw new RuntimeException(a11yTest.exceptionString);
        }

        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeLater(test::createPriorityTest);
        countDownLatch.await();

        if (!testResult) {
            throw new RuntimeException(a11yTest.exceptionString);
        }
    }
}
