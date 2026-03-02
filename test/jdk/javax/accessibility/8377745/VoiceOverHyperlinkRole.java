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
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/*
 * @test
 * @key headful
 * @bug 8377745
 * @summary manual test for VoiceOver reading links correctly
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual VoiceOverHyperlinkRole
 */

public class VoiceOverHyperlinkRole {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = "INSTRUCTIONS (Mac-only):\n" +
                "1. Open VoiceOver\n" +
                "2. Move the VoiceOver cursor over the link.\n" +
                "3. Observe how VoiceOver identifies the link.\n\n" +
                "Expected behavior: VoiceOver should identify it as a " +
                "\"link\". It should not say \"text element\", \"text\" " +
                "or \"hyperlink\".\n\n" +
                "If you select the link using \"Accessibility " +
                "Inspector\": it should identify its role as AXLink.";

        PassFailJFrame.builder()
                .title("VoiceOverHyperlinkRole Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(VoiceOverHyperlinkRole::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        p.add(createText("This button uses `AccessibleRole.HYPERLINK:`"));
        p.add(createLink(AccessibleRole.HYPERLINK));

        // for debugging / experimentation:
        boolean tryOtherRoles = false;
        if (tryOtherRoles) {
            p.add(createText(
                    "This button uses `new AccessibleRole(\"Link\") {}`:"));
            p.add(createLink(new AccessibleRole("Link") {}));
            p.add(createText(
                    "This button uses `new AccessibleRole(\"link\") {}`:"));
            p.add(createLink(new AccessibleRole("link") {}));
            p.add(createText(
                    "This button uses `new AccessibleRole(\"AXLink\") {}`:"));
            p.add(createLink(new AccessibleRole("AXLink") {}));
        }

        JFrame frame = new JFrame();
        frame.getContentPane().add(p);
        frame.pack();
        return frame;
    }

    private static JTextArea createText(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setBorder(new EmptyBorder(20, 10, 3, 10));
        return textArea;
    }

    private static JButton createLink(AccessibleRole role) {
        String text = "<html><u>https://bugs.openjdk.org/</u></html>";
        JButton button = new JButton(text) {
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJButton() {
                        @Override
                        public AccessibleRole getAccessibleRole() {
                            return role;
                        }
                    };
                }
                return accessibleContext;
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        return button;
    }
}
