/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import static javax.swing.BorderFactory.createEmptyBorder;

/*
 * @test id=windows
 * @bug 8348760 8365375 8365389 8365625
 * @requires (os.family == "windows")
 * @summary Verify that Windows Look & Feel allows changing
 *          accelerator colors
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuItemAcceleratorColor
 */

/*
 * @test id=classic
 * @bug 8348760 8365375 8365389 8365625
 * @requires (os.family == "windows")
 * @summary Verify that Windows Classic Look & Feel allows changing
 *          accelerator colors
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuItemAcceleratorColor classic
 */
public final class MenuItemAcceleratorColor {
    private static final String INSTRUCTIONS =
            "Click the Menu to open it.\n" +
            "\n" +
            "Verify that the first and the last menu items render " +
            "their accelerators using the default colors, the color " +
            "should match that of the menu item itself in regular and " +
            "selected states.\n" +
            "\n" +
            "Verify that the second menu item renders its accelerator " +
            "with green and that the color changes to red when selected.\n" +
            "\n" +
            "Verify that the third menu item renders its accelerator " +
            "with magenta and yellow correspondingly.\n" +
            "\n" +
            "Verify that only the fifth menu item renders its accelerator " +
            "with blue; both the fourth and sixth should render their " +
            "accelerator with a shade of gray.\n" +
            "\n" +
            "If the above conditions are satisfied, press the Pass button; " +
            "otherwise, press the Fail button.";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel((args.length > 0 && "classic".equals(args[0]))
                                 ? "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel"
                                 : "com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(20)
                      .columns(60)
                      .testUI(MenuItemAcceleratorColor::createUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Box createInfoPanel() {
        Box box = Box.createVerticalBox();
        box.add(new JLabel("Look and Feel: "
                           + UIManager.getLookAndFeel()
                                      .getName()));
        box.add(new JLabel("Java version: "
                           + System.getProperty("java.runtime.version")));
        return box;
    }

    private static JFrame createUI() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(createEmptyBorder(8, 8, 8, 8));
        content.add(createInfoPanel(),
                    BorderLayout.SOUTH);

        JFrame frame = new JFrame("Accelerator colors in Windows L&F");
        frame.setJMenuBar(createMenuBar());
        frame.add(content, BorderLayout.CENTER);
        frame.setSize(350, 370);
        return frame;
    }

    private static JMenuBar createMenuBar() {
        JMenuItem first = new JMenuItem("First menu item");
        first.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                                                    InputEvent.CTRL_DOWN_MASK));

        // Modify colors for accelerator rendering
        Color acceleratorForeground = UIManager.getColor("MenuItem.acceleratorForeground");
        Color acceleratorSelectionForeground = UIManager.getColor("MenuItem.acceleratorSelectionForeground");
        UIManager.put("MenuItem.acceleratorForeground", Color.GREEN);
        UIManager.put("MenuItem.acceleratorSelectionForeground", Color.RED);

        JMenuItem second = new JMenuItem("Second menu item");
        second.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                                                     InputEvent.SHIFT_DOWN_MASK
                                                     | InputEvent.CTRL_DOWN_MASK));

        UIManager.put("MenuItem.acceleratorForeground", Color.MAGENTA);
        UIManager.put("MenuItem.acceleratorSelectionForeground", Color.YELLOW);
        JMenuItem third = new JMenuItem("Third menu item");
        third.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                                                    InputEvent.ALT_DOWN_MASK));

        // Restore colors
        UIManager.put("MenuItem.acceleratorForeground", acceleratorForeground);
        UIManager.put("MenuItem.acceleratorSelectionForeground", acceleratorSelectionForeground);


        // Disabled foreground
        JMenuItem fourth = new JMenuItem("Fourth menu item");
        fourth.setEnabled(false);
        fourth.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                                                     InputEvent.CTRL_DOWN_MASK));

        Color disabledForeground = UIManager.getColor("MenuItem.disabledForeground");
        UIManager.put("MenuItem.disabledForeground", Color.BLUE);

        JMenuItem fifth = new JMenuItem("Fifth menu item");
        fifth.setEnabled(false);
        fifth.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                                                    InputEvent.CTRL_DOWN_MASK
                                                    | InputEvent.SHIFT_DOWN_MASK));

        // Restore disabled foreground
        UIManager.put("MenuItem.disabledForeground", disabledForeground);

        JMenuItem sixth = new JMenuItem("Sixth menu item");
        sixth.setEnabled(false);
        sixth.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X,
                                                    InputEvent.CTRL_DOWN_MASK
                                                    | InputEvent.ALT_DOWN_MASK));


        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                                                   InputEvent.CTRL_DOWN_MASK));

        JMenu menu = new JMenu("Menu");
        menu.add(first);
        menu.add(second);
        menu.add(third);
        menu.addSeparator();
        menu.add(fourth);
        menu.add(fifth);
        menu.add(sixth);
        menu.addSeparator();
        menu.add(quit);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);

        return menuBar;
    }
}
