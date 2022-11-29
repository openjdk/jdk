/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * This is a common library for building UI for testing purposes.
 */
public class UIBuilder {

    /**
     * Creates a {@link javax.swing.JDialog} object with a fixed layout that contains
     * an instructions {@link javax.swing.JTextArea} and a message
     * {@link javax.swing.JTextArea} for displaying text contents. Text contents can
     * be set by using {@code setInstruction} method and {@code setMessage} method.
     *
     * The {@link javax.swing.JDialog} object also a pass {@link javax.swing.JButton}
     * and a fail {@link javax.swing.JButton} to indicate test result. Buttons' action
     * can be bound by using {@code setPassAction} and {@code setFailAction}.
     */
    public static class DialogBuilder {
        private JDialog dialog;
        private JTextArea instructionsText;
        private JTextArea messageText;
        private JButton pass;
        private JButton fail;

        /**
         * Construct a new DialogBuilder object.
         */
        public DialogBuilder() {
            dialog = new JDialog(new JFrame());
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            instructionsText = new JTextArea("", 5, 100);

            dialog.add("North", new JScrollPane(instructionsText,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS));

            messageText = new JTextArea("", 20, 100);
            dialog.add("Center", new JScrollPane(messageText,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS));

            JPanel buttons = new JPanel();
            pass = new JButton("pass");
            pass.setActionCommand("pass");
            buttons.add("East", pass);

            fail = new JButton("fail");
            fail.setActionCommand("fail");
            buttons.add("West", fail);

            dialog.add("South", buttons);
        }

        /**
         * Returns this {@code DialogBuilder} setting the dialog's title to a new value.
         * @param title a string value
         * @returns this DialogBuilder
         */
        public DialogBuilder setTitle(String title) {
            dialog.setTitle(title);
            return this;
        }

        /**
         * Returns this {@code DialogBuilder} setting the instruction text to a new
         * value.
         * @param instruction a string value
         * @returns this DialogBuilder
         */
        public DialogBuilder setInstruction(String instruction) {
            instructionsText.setText("Test instructions:\n" + instruction);
            return this;
        }

        /**
         * Returns this {@code DialogBuilder} setting the message text to a new value.
         * @param message a string value
         * @returns this DialogBuilder
         */
        public DialogBuilder setMessage(String message) {
            messageText.setText(message);
            return this;
        }

        /**
         * Returns this {@code DialogBuilder} setting pass button action to
         * {@link java.awt.event.ActionListener}.
         * @param action an action to perform on button click
         * @returns this DialogBuilder
         */
        public DialogBuilder setPassAction(ActionListener action) {
            pass.addActionListener(action);
            return this;
        }

        /**
         * Returns this {@code DialogBuilder} setting fail button action to
         * {@link java.awt.event.ActionListener}.
         * @param action an action to perform on button click
         * @returns this DialogBuilder
         */
        public DialogBuilder setFailAction(ActionListener action) {
            fail.addActionListener(action);
            return this;
        }

        /**
         * Returns this {@code DialogBuilder} setting window-closing action to
         * {@link java.lang.Runnable}.
         * @param action a runnerable action to perform on window close
         * @returns this DialogBuilder
         */
        public DialogBuilder setCloseAction(Runnable action) {
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    super.windowClosing(e);
                    action.run();
                }
            });
            return this;
        }

        /**
         * Returns a {@link javax.swing.JDialog} window.
         * @returns a JDialog
         */
        public JDialog build() {
            dialog.pack();
            return dialog;
        }
    }
}
