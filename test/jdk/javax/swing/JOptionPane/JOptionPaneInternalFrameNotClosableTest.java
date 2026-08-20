/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388824
 * @summary Verifies that JOptionPane internal frames are not closable
 *          for all installed look and feels
 * @run main JOptionPaneInternalFrameNotClosableTest
 */

import java.util.ArrayList;
import java.util.List;

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JOptionPaneInternalFrameNotClosableTest {

    private static final int[] MESSAGE_TYPES = {
        JOptionPane.ERROR_MESSAGE,
        JOptionPane.INFORMATION_MESSAGE,
        JOptionPane.WARNING_MESSAGE,
        JOptionPane.QUESTION_MESSAGE,
        JOptionPane.PLAIN_MESSAGE
    };

    public static void main(String[] args) throws Exception {
        List<String> closableLafs = new ArrayList<>();

        
        for (UIManager.LookAndFeelInfo laf
                    : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F: " + laf.getClassName());
            SwingUtilities.invokeAndWait(
                        () -> testLookAndFeel(laf, closableLafs));
        }

        if (!closableLafs.isEmpty()) {
            throw new RuntimeException(
                    "Option-pane internal frames are closable for:\n "
                    + String.join(" ", closableLafs));
        }
    }

    private static void testLookAndFeel(UIManager.LookAndFeelInfo laf,
                                        List<String> closableLafs) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot set look and feel: " + laf.getName(), e);
        }

        JDesktopPane desktop = new JDesktopPane();

        for (int messageType : MESSAGE_TYPES) {
            JOptionPane optionPane =
                    new JOptionPane("Message", messageType);
            JInternalFrame internalFrame =
                    optionPane.createInternalFrame(desktop, "Title");

            try {
                if (internalFrame.isClosable()) {
                    closableLafs.add(laf.getName() + " ("
                            + messageTypeName(messageType) + ")\n");
                }
            } finally {
                internalFrame.dispose();
            }
        }
    }

    private static String messageTypeName(int messageType) {
        return switch (messageType) {
            case JOptionPane.ERROR_MESSAGE ->
                "JOptionPane.ERROR_MESSAGE";
            case JOptionPane.INFORMATION_MESSAGE ->
                "JOptionPane.INFORMATION_MESSAGE";
            case JOptionPane.WARNING_MESSAGE ->
                "JOptionPane.WARNING_MESSAGE";
            case JOptionPane.QUESTION_MESSAGE ->
                "JOptionPane.QUESTION_MESSAGE";
            case JOptionPane.PLAIN_MESSAGE ->
                "JOptionPane.PLAIN_MESSAGE";
            default -> "message type " + messageType;
        };
    }
}
