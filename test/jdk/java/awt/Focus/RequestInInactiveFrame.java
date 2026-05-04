/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6458497
 * @summary check focus requests in inactive frames
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RequestInInactiveFrame
 */

import java.util.ArrayList;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class RequestInInactiveFrame {

    private static final String INSTRUCTIONS = """
            After the tests starts you will see two frames: \"test frame\" and \"opposite frame\"
            activate the former by click on its title
            Focus should be on \"press me\" button (if it's not, the test fails)
            press on \"press me\" button and activate \"opposite frame\"
            wait for several seconds.
            Focus should either remain on button in the \"opposite frame\"
            or goes to \"focus target\" button (in this case \"test frame\" should be activated
            if it's not, the test failed.
            Activate \"test frame\" one more time, press on \"press me\" button and switch focus
            to some native window.  Wait for several seconds,
            If you see focus border around
            \"focus target\" and \"test frame\" is not active then the test failed.
            if focus transfered to that button and the frame is activated, or if there is no focus
            in java - tests passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("RequestInInactiveFrame Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(RequestInInactiveFrame::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static ArrayList<Window> createTestUI() {
        JFrame frame = new JFrame("test frame");
        final JButton btn2 = new JButton("focus target");
        JButton btn1 = new JButton("press me");
        btn1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("waiting...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                System.out.println("requesting focus");
                btn2.requestFocus();
            }
        });
        frame.setLayout(new FlowLayout());
        frame.add(btn1);
        frame.add(btn2);
        frame.pack();
        frame.setLocation(200, 100);

        JFrame frame2 = new JFrame("opposite frame");
        JButton btn3 = new JButton("just a button");
        frame2.add(btn3);
        frame2.pack();
        frame2.setLocation(200, 200);

        ArrayList<Window> list = new ArrayList<>();
        list.add(frame);
        list.add(frame2);
        return list;
    }

}
