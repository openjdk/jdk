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

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.HeadlessException;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Allows to chose if a test fails or passes. It is a multi-use component. A chosen answer can be confirmed later
 * upon providing additional information.
 */
class PassFailPane extends JPanel {
    private final Consumer<Boolean> listener;

    private final JButton btnPass = new JButton("Pass");
    private final JButton btnFail = new JButton("Fail");

    /**
     * @param listener gets called with true (pass) or false (fail).
     * @throws HeadlessException
     */
    PassFailPane(Consumer<Boolean> listener)
            throws HeadlessException, IOException {
        this.listener = listener;

        add(btnPass);
        add(btnFail);

        btnPass.requestFocus();

        btnPass.addActionListener((e) -> {
            disableButtons();
            listener.accept(true);
        });

        btnFail.addActionListener((e) -> {
            disableButtons();
            listener.accept(false);
        });
    }

    private void disableButtons() {
        btnFail.setEnabled(false);
        btnPass.setEnabled(false);
    }

    public void setFailEnabled(boolean enabled) {
        btnFail.setEnabled(enabled);
    }

}
