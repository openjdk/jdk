/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4419914
 * @summary Tests that tab movement is correct in RTL component orientation.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4419914
 */

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Frame;
import java.awt.Window;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JWindow;

public class bug4419914 {
    private static JFrame frame;
    private static final String INSTRUCTIONS = """
        This test verifies tab movement on RTL component orientation
        in JWindow, JFrame and JDialog.

        When test starts 3 test windows are displayed - JFrame, JWindow and JDialog.
        Follow the instructions below and if any condition does not hold
        press FAIL.

        1. Confirm that each button in the child window is placed as follows:

            For JFrame:
                 NORTH
            END  CENTER  START
                 SOUTH

            For JWindow:
            END  CENTER  START
                  QUIT

            For JDialog:
            END  CENTER  START

        3. Press on the "START" button in case of JWindow & JDialog and "NORTH"
           in case of JFrame, confirm that the respective button is focused.

        4. Press TAB repeatedly and confirm that the TAB focus moves
           from right to left.

            For JFrame:
            (NORTH - START - CENTER - END - SOUTH - NORTH - START - CENTER - ...)

            For JWindow:
            (START - CENTER - END - QUIT - START - CENTER - END - QUIT - ...)

            For JDialog:
            (START - CENTER - END - START - CENTER - END - ...)

        If all of the above conditions are true press PASS else FAIL.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testTimeOut(10)
                .testUI(bug4419914::createAndShowUI)
                .positionTestUI(WindowLayouts::rightOneColumn)
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createAndShowUI() {
        return List.of(createJFrame(), createJWindow(), createJDialog());
    }

    private static JFrame createJFrame() {
        frame = new JFrame("bug4419914 JFrame");
        frame.setFocusCycleRoot(true);
        // Tab movement set to RTL
        frame.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        frame.setLocale(Locale.ENGLISH);
        frame.enableInputMethods(false);

        // Component placement within content pane set to RTL
        frame.getContentPane().setComponentOrientation(
                ComponentOrientation.RIGHT_TO_LEFT);
        frame.getContentPane().setLocale(Locale.ENGLISH);
        frame.setLayout(new BorderLayout());
        frame.add(new JButton("SOUTH"), BorderLayout.SOUTH);
        frame.add(new JButton("CENTER"), BorderLayout.CENTER);
        frame.add(new JButton("END"), BorderLayout.LINE_END);
        frame.add(new JButton("START"), BorderLayout.LINE_START);
        frame.add(new JButton("NORTH"), BorderLayout.NORTH);
        frame.setSize(300, 160);
        return frame;
    }

    private static JWindow createJWindow() {
        JWindow window = new JWindow(frame);
        window.setFocusableWindowState(true);
        // Tab movement set to RTL
        window.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        window.setLocale(new Locale("en"));
        window.enableInputMethods(false);

        // Component placement within content pane set to RTL
        window.getContentPane().setComponentOrientation(
                ComponentOrientation.RIGHT_TO_LEFT);
        window.getContentPane().setLocale(new Locale("en"));
        window.setLayout(new BorderLayout());
        window.add(new JLabel("bug4419914 JWindow"), BorderLayout.NORTH);
        window.add(new JButton("START"), BorderLayout.LINE_START);
        window.add(new JButton("CENTER"), BorderLayout.CENTER);
        window.add(new JButton("END"), BorderLayout.LINE_END);

        JButton quitButton = new JButton("QUIT");
        quitButton.addActionListener(e1 -> window.dispose());
        window.add(quitButton, BorderLayout.SOUTH);
        window.setSize(300, 153);
        window.requestFocus();
        return window;
    }

    private static JDialog createJDialog() {
        JDialog dialog = new JDialog((Frame) null, "bug4419914 JDialog");
        // Tab movement set to RTL
        dialog.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        dialog.setLocale(new Locale("en"));
        dialog.enableInputMethods(false);

        // Component placement within content pane set to RTL
        dialog.getContentPane().setComponentOrientation(
                ComponentOrientation.RIGHT_TO_LEFT);
        dialog.getContentPane().setLocale(new Locale("en"));
        dialog.setLayout(new BorderLayout());
        dialog.add(new JButton("CENTER"), BorderLayout.CENTER);
        dialog.add(new JButton("END"), BorderLayout.LINE_END);
        dialog.add(new JButton("START"), BorderLayout.LINE_START);
        dialog.setSize(300, 160);
        return dialog;
    }
}
