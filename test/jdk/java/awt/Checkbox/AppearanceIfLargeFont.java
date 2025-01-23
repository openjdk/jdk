/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.CheckboxMenuItem;
import java.awt.Frame;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 6401956
 * @summary The right mark of the CheckboxMenu item is broken
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AppearanceIfLargeFont
 */

public class AppearanceIfLargeFont extends Frame {
    private static final String INSTRUCTIONS = """
            1) Make sure that font-size is large.
               You could change this using 'Appearance' dialog.
            2) Press button 'Press'
               You will see a menu item with check-mark.
            3) If check-mark is correctly painted then the test passed.
               Otherwise, test failed.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("AppearanceIfLargeFont")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(AppearanceIfLargeFont::new)
                .build()
                .awaitAndCheck();
    }

    public AppearanceIfLargeFont() {
        createComponents();

        setSize(200, 200);
        validate();
    }

    void createComponents() {
        final Button press = new Button("Press");
        final PopupMenu popup = new PopupMenu();
        press.add(popup);
        add(press);

        CheckboxMenuItem item = new CheckboxMenuItem("CheckboxMenuItem", true);
        popup.add(item);

        press.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent ae) {
                        popup.show(press, press.getSize().width, 0);
                    }
                }
        );
    }
}
