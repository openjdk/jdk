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

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/*
 * @test
 * @key headful
 * @bug 8379953
 * @summary manual test for VoiceOver reading header/heading correctly
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual VoiceOverHeaderRole
 */

public class VoiceOverHeaderRole {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = "INSTRUCTIONS (Mac-only):\n" +
                "1. Open VoiceOver\n" +
                "2. Move the VoiceOver cursor over the JLabel.\n\n" +
                "Expected behavior: VoiceOver should identify it as a " +
                "\"heading\". It should not say \"header\".\n\n" +
                "If you select the link using \"Accessibility " +
                "Inspector\": it should identify its role as AXHeading.";

        PassFailJFrame.builder()
                .title("VoiceOverHeaderRole Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(VoiceOverHeaderRole::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JPanel p = new JPanel();
        JLabel label = new JLabel("Octopus") {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJLabel() {
                        @Override
                        public AccessibleRole getAccessibleRole() {
                            return AccessibleRole.HEADER;
                        }
                    };
                }
                return accessibleContext;
            }
        };
        p.add(label);

        JFrame frame = new JFrame();
        frame.getContentPane().add(p);
        frame.pack();
        return frame;
    }
}
