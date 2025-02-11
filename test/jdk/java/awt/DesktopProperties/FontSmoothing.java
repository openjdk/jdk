/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/*
 * @test
 * @bug 4808569
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary add desktop property for the Windows XP or later font smoothing settings
 * @run main/manual FontSmoothing
 */

public class FontSmoothing {

    private static final String PROP_NAME = "win.text.fontSmoothingType";

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test should be run on Windows XP or later.

                On Windows 11:
                1. Open Run dialog by typing 'run' in search bar.
                2. Type 'cttune' and press Ok.
                3. Uncheck the "Turn On ClearType" checkbox and follow next instructions on screen.
                4. Repeat Step 1-2.
                5. Check the "Turn On ClearType" checkbox and follow next instructions on screen.
                6. Take a look at the output window to determine if the test passed or failed.
                """;

        PassFailJFrame.builder()
                .title("FontSmoothing Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testTimeOut(5)
                .testUI(FontSmoothing::createUI)
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("FontSmoothing Test");
        f.setSize(50, 50);

        Object value = Toolkit.getDefaultToolkit().getDesktopProperty(PROP_NAME);
        PassFailJFrame.log("toolkit.getDesktopProperty: " + PROP_NAME + " = " + value + "\n");

        Toolkit.getDefaultToolkit().addPropertyChangeListener(PROP_NAME, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                PassFailJFrame.log("PropertyChangeEvent: " + e.getPropertyName() +
                        "\n   old value=" + e.getOldValue() +
                        "\n   new value=" + e.getNewValue());

                Integer value = (Integer) Toolkit.getDefaultToolkit().getDesktopProperty(PROP_NAME);
                PassFailJFrame.log("toolkit.getDesktopProperty:" + PROP_NAME + "=" + value);

                if (value.equals((Integer) e.getNewValue())) {
                    PassFailJFrame.log("test PASSED");
                } else {
                    PassFailJFrame.log("test FAILED");
                }
            }
        });
        return f;
    }
}
