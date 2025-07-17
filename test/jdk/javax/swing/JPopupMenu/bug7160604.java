/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7160604
 * @summary Using non-opaque windows - popups are initially not painted correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug7160604
*/
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import static java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

public class bug7160604 {

    private static final String INSTRUCTIONS = """
            Click on the top-bar and combo-box at the bottom more than once.
            Check top-bar popup menu and combo-box drop-down list have a border
            and their items are drawn properly.
            If yes, Click Pass else click Fail.""";

    public static void main(String[] args) throws Exception {
        if (!GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .isWindowTranslucencySupported(PERPIXEL_TRANSLUCENT)) {
                // Tested translucency is not supported. Test passed
                return;
        }
         PassFailJFrame.builder()
                .title("PopupMenu Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(35)
                .testUI(bug7160604::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JWindow createTestUI() {

        final JWindow window = new JWindow();
        window.setLocation(200, 200);
        window.setSize(300, 300);

        final JLabel label = new JLabel("...click to invoke JPopupMenu");
        label.setOpaque(true);
        final JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(BorderFactory.createLineBorder(Color.RED));
        window.setContentPane(contentPane);
        contentPane.add(label, BorderLayout.NORTH);

        final JComboBox comboBox = new JComboBox(new Object[]{"1", "2", "3", "4"});
        contentPane.add(comboBox, BorderLayout.SOUTH);

        final JPopupMenu jPopupMenu = new JPopupMenu();

        jPopupMenu.add("string");
        jPopupMenu.add(new AbstractAction("action") {
            @Override
            public void actionPerformed(final ActionEvent e) {
            }
        });
        jPopupMenu.add(new JLabel("label"));
        jPopupMenu.add(new JMenuItem("MenuItem"));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(final MouseEvent e) {
                jPopupMenu.show(label, 0, 0);
            }
        });

        window.setBackground(new Color(0, 0, 0, 0));

        return window;
    }
}
