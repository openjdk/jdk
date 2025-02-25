/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;

/*
 * @test
 * @bug 4320050
 * @key headful
 * @summary Minimum size for java.awt.Frame is not being enforced.
 * @run main FrameSetMinimumSizeTest
 */

public class FrameSetMinimumSizeTest {
    private static Frame f;
    private static volatile boolean passed;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                createAndShowUI();

                f.setSize(200, 200);
                passed = verifyFrameSize(new Dimension(300, 300));
                isFrameSizeOk(passed);

                f.setSize(200, 400);
                passed = verifyFrameSize(new Dimension(300, 400));
                isFrameSizeOk(passed);

                f.setSize(400, 200);
                passed = verifyFrameSize(new Dimension(400, 300));
                isFrameSizeOk(passed);

                f.setMinimumSize(null);

                f.setSize(200, 200);
                passed = verifyFrameSize(new Dimension(200, 200));
                isFrameSizeOk(passed);
            } finally {
                if (f != null) {
                    f.dispose();
                }
            }
        });
    }

    private static void createAndShowUI() {
        f = new Frame("Minimum Size Test");
        f.setSize(300, 300);
        f.setMinimumSize(new Dimension(300, 300));
        f.setVisible(true);
    }

    private static boolean verifyFrameSize(Dimension expected) {

        if (f.getSize().width != expected.width || f.getSize().height != expected.height) {
            return false;
        }
        return true;
    }

    private static void isFrameSizeOk(boolean passed) {
        if (!passed) {
            throw new RuntimeException("Frame's setMinimumSize not honoured for the" +
                    " frame size: " + f.getSize());
        }
    }
}
