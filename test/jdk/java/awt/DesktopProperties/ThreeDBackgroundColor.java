/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Frame;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/*
 * @test
 * @bug 4368193
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "windows")
 * @summary Toolkit's getDesktopProperty returns stale values on Microsoft Windows
 * @run main/manual ThreeDBackgroundColor
 */

public class ThreeDBackgroundColor {

    private static final String PROP_NAME = "win.3d.backgroundColor";

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                On Windows 10:
                1. Open Windows Settings, in the search bar type
                   'high contrast', in the list of suggestions choose option
                   'Turn high contrast on or off'
                2. In the High contrast control panel click on the on/off switch
                   to initialize High contrast mode
                3. Wait for the High contrast mode to finish initialization
                4. Click on the same switch again to turn off High contrast mode

                On Windows 11:
                1. Open Windows settings, in the search bar type
                   'Contrast Theme'.
                2. Select any value from 'Contrast themes' dropdown menu and press 'Apply'.
                3. Wait for the High contrast mode to finish initialization
                4. Select 'None' from 'Contrast themes' dropdown menu to revert the changes.

                Take a look at the output window to determine if the test passed or failed.""";

        PassFailJFrame.builder()
                .title("ThreeDBackgroundColor Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testTimeOut(5)
                .testUI(ThreeDBackgroundColor::createUI)
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("ThreeDBackgroundColor Test");
        f.setSize(50, 50);

        Object value = Toolkit.getDefaultToolkit().getDesktopProperty(PROP_NAME);
        PassFailJFrame.log("toolkit.getDesktopProperty:" + PROP_NAME + "=" + value);

        Toolkit.getDefaultToolkit().addPropertyChangeListener(PROP_NAME, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                PassFailJFrame.log("PropertyChangeEvent: " + e.getPropertyName() +
                        "\n   old value=" + e.getOldValue() +
                        "\n   new value=" + e.getNewValue());

                Color value = (Color) Toolkit.getDefaultToolkit().getDesktopProperty(PROP_NAME);
                PassFailJFrame.log("toolkit.getDesktopProperty:" + PROP_NAME + "=" + value);
                if (value.equals((Color) e.getNewValue())) {
                    PassFailJFrame.log("test PASSED");
                } else {
                    PassFailJFrame.log("test FAILED");
                }
            }
        });
        return f;
    }
}
