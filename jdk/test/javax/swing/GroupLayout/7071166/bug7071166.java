/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7071166
 * @summary LayoutStyle.getPreferredGap() - IAE is expected but not thrown
 * @author Pavel Porvatov
 */

import javax.swing.*;
import static javax.swing.SwingConstants.*;
import java.awt.*;

public class bug7071166 {
    private static final int[] POSITIONS = {NORTH, EAST, SOUTH, WEST, // valid positions
            NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST, 123, -456}; // invalid positions

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo lookAndFeelInfo : UIManager.getInstalledLookAndFeels()) {
            UIManager.setLookAndFeel(lookAndFeelInfo.getClassName());

            System.out.println("LookAndFeel: " + lookAndFeelInfo.getName());

            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    LayoutStyle layoutStyle = LayoutStyle.getInstance();

                    System.out.println("LayoutStyle: " + layoutStyle);

                    for (int i = 0; i < POSITIONS.length; i++) {
                        int position = POSITIONS[i];

                        try {
                            layoutStyle.getPreferredGap(new JButton(), new JButton(),
                                    LayoutStyle.ComponentPlacement.RELATED, position, new Container());

                            if (i > 3) {
                                throw new RuntimeException("IllegalArgumentException is not thrown for position " +
                                        position);
                            }
                        } catch (IllegalArgumentException e) {
                            if (i <= 3) {
                                throw new RuntimeException("IllegalArgumentException is thrown for position " +
                                        position);
                            }
                        }
                    }
                }
            });

            System.out.println("passed");
        }
    }
}
