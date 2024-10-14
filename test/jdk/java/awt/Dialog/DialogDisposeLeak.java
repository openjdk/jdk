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

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;

/*
 * @test
 * @bug 4193022
 * @summary Test for bug(s): 4193022, disposing dialog leaks memory
 * @key headful
 * @run main/othervm -Xmx128m DialogDisposeLeak
 */

public class DialogDisposeLeak {
    static Frame frame;

    public static void main(String args[]) throws Exception {
        EventQueue.invokeLater(() -> {
            try {
                frame = new DisposeFrame();
            } catch (Exception e) {
                throw new RuntimeException("Test failed.");
            } finally {
                if (!DisposeFrame.passed) {
                    throw new RuntimeException("Test failed.");
                }
            }
        });
    }
}

class DisposeFrame extends Frame {
    Label label = new Label("Test not passed yet");
    static boolean passed;

    DisposeFrame() {
        super("DisposeLeak test");
        passed = false;
        setLayout(new FlowLayout());
        add(label);
        pack();

        for (int i = 0; !passed && i < 10; i++) {
            Dialog dlg = new DisposeDialog(this);
            dlg.setVisible(true);
        }
    }

    public void testOK() {
        passed = true;
        label.setText("Test has passed. Dialog finalized.");
    }
}

class DisposeDialog extends Dialog {
    DisposeDialog(Frame frame) {
        super(frame, "DisposeDialog", false);

        setLayout(new FlowLayout());
        setVisible(false);
        dispose();
        // try to force GC and finalization
        for (int n = 0; n < 100; n++) {
            byte bytes[] = new byte[1024 * 1024 * 8];
            System.gc();
            pack();
        }
    }

    @SuppressWarnings("removal")
    public void finalize() {
        ((DisposeFrame) getParent()).testOK();
    }
}
