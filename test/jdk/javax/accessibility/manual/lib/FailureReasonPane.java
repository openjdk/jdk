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
package lib;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.util.function.Consumer;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;

/**
 * Allows to enter reason for the test failure.
 */
class FailureReasonPane extends JPanel {

    private final JTextArea text;

    FailureReasonPane(Consumer<String> listener) {
        setLayout(new BorderLayout(10, 10));
        add(new JLabel("Failure reason:"), NORTH);
        text = new JTextArea(3, 10);
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                listener.accept(text.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                listener.accept(text.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                listener.accept(text.getText());
            }
        });
        add(text, CENTER);
    }

    public String getReason() {
        return text.getText();
    }

    public void requestFocus() {
        text.requestFocus();
    }
}
