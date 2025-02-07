/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.swing;

import java.awt.KeyEventPostProcessor;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

public final class AltProcessor implements KeyEventPostProcessor {

    private AltProcessor() {}

    private static final AltProcessor altProcessor = new AltProcessor();

    public static KeyEventPostProcessor getInstance() {
        return altProcessor;
    }

    @Override
    public boolean postProcessKeyEvent(final KeyEvent ev) {
        if (ev.getKeyCode() != KeyEvent.VK_ALT) {
            return false;
        }

        final JRootPane root = SwingUtilities.getRootPane(ev.getComponent());
        final Window winAncestor = (root == null ? null : SwingUtilities.getWindowAncestor(root));

        switch (ev.getID()) {
            case KeyEvent.KEY_PRESSED:
                MnemonicHandler.setMnemonicHidden(false);
                break;
            case KeyEvent.KEY_RELEASED:
                MnemonicHandler.setMnemonicHidden(true);
                break;
        }

        MnemonicHandler.repaintMnemonicsInWindow(winAncestor);

        return false;
    }
}
