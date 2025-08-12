/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4090493
 * @summary Test for Checkbox indicator size
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckboxIndicatorSizeTest
 */

public class CheckboxIndicatorSizeTest implements ActionListener {
    private static final String INSTRUCTIONS = """
            Indicator size of Checkbox depends
            on the platform font used to render the label.

            In the frame you can see a group of checkboxes
            and radio buttons.
            Verify that all checkboxes and radio buttons have
            indicators of the same size and proportional to
            the uiScale and/or font-size.

            Use menu to change the font size and the indicators
            should scale proportionally.

            If there is a bug, the checkbox/radiobutton with
            dingbats label will have a smaller indicator.

            Press PASS if the above conditions are true else Press FAIL.
            """;
    private static Frame frame;
    private static Panel testPanel;

    public static void main(String[] args) throws Exception {

        CheckboxIndicatorSizeTest obj = new CheckboxIndicatorSizeTest();
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 3)
                      .columns(60)
                      .testUI(obj::createAndShowUI)
                      .build()
                      .awaitAndCheck();
    }

    private Frame createAndShowUI() {
        frame = new Frame("CheckboxIndicatorSizeTest");

        testPanel = new Panel(new GridLayout(0, 1));
        testPanel.setFont(new Font("Dialog", Font.PLAIN, 12));
        frame.add(testPanel);

        MenuBar menuBar = new MenuBar();
        Menu fontSizeMenu = new Menu("FontSize");

        MenuItem size10 = new MenuItem("10");
        size10.addActionListener(this);
        fontSizeMenu.add(size10);

        MenuItem size12 = new MenuItem("12");
        size12.addActionListener(this);
        fontSizeMenu.add(size12);

        MenuItem size14 = new MenuItem("14");
        size14.addActionListener(this);
        fontSizeMenu.add(size14);

        MenuItem size18 = new MenuItem("18");
        size18.addActionListener(this);
        fontSizeMenu.add(size18);

        MenuItem size24 = new MenuItem("24");
        size24.addActionListener(this);
        fontSizeMenu.add(size24);

        MenuItem size36 = new MenuItem("36");
        size36.addActionListener(this);
        fontSizeMenu.add(size36);

        menuBar.add(fontSizeMenu);
        frame.setMenuBar(menuBar);

        Checkbox cbEnglishOnly
            = new Checkbox("Toggle", true);
        Checkbox cbDingbatsOnly
            = new Checkbox("\u274a\u274b\u274c\u274d", true);
        Checkbox cbEnglishDingbats
            = new Checkbox("Toggle \u274a\u274d", true);
        Checkbox cbDingbatsEnglish
            = new Checkbox("\u274a\u274d toggle", true);

        CheckboxGroup radioGroup = new CheckboxGroup();
        Checkbox rbEnglishOnly
            = new Checkbox("Radio", true, radioGroup);
        Checkbox rbDingbatsOnly
            = new Checkbox("\u274a\u274b\u274c\u274d", false, radioGroup);
        Checkbox rbEnglishDingbats
            = new Checkbox("Radio \u274a\u274d", false, radioGroup);
        Checkbox rbDingbatsEnglish
            = new Checkbox("\u274a\u274d radio", false, radioGroup);

        Label cbLabel = new Label("Checkboxes");
        cbLabel.setBackground(Color.YELLOW);
        testPanel.add(cbLabel);
        testPanel.add(cbEnglishOnly);
        testPanel.add(cbDingbatsOnly);
        testPanel.add(cbEnglishDingbats);
        testPanel.add(cbDingbatsEnglish);

        Label rbLabel = new Label("Radio buttons");
        rbLabel.setBackground(Color.YELLOW);
        testPanel.add(rbLabel);
        testPanel.add(rbEnglishOnly);
        testPanel.add(rbDingbatsOnly);
        testPanel.add(rbEnglishDingbats);
        testPanel.add(rbDingbatsEnglish);

        frame.pack();
        return frame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String sizeStr = e.getActionCommand();
        int size = Integer.parseInt(sizeStr);
        Font oldFont = testPanel.getFont();
        Font newFont = new Font(oldFont.getName(), oldFont.getStyle(), size);
        testPanel.setFont(newFont);
        frame.pack();
        frame.setVisible(true);
    }
}
