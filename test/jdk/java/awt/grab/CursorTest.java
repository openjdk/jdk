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

/*
 * @test
 * @bug 6364746 6400007
 * @summary Cursor should be changed correctly while Swing menu is open (input is grabbed).
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CursorTest
*/

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class CursorTest {

    static final String INSTRUCTIONS = """
        After the test starts you will see a frame titled "Cursor Test Window",
        with two menus in the menubar (menu1 and menu2), and a textfield and
        a button, labeled "JButton".
        1. Open menu1 (it should be small and fit within the border of the frame),
        2. Verify that the pointer image (cursor) is the default desktop cursor.
        3. Move the mouse over the text field - the cursor should change its shape to caret,
        4. Move the mouse over the button - the cursor should be default one,
        5. Move the mouse to the border of the frame - cursor should be a resize one
           (exact shape is dependent on the border you move over),
        6. Move the mouse out of the frame - cursor should be default one,
        7. Perform steps 2-6 in reverse order (after this the mouse should be over the open menu1),
        8. Open menu2, it should be big enough to not fit within the frame,
        9. Repeat steps 2-7 (you should end up with mouse over opened menu2 :),
        10. Close the menu.
        11. If on every step the cursor was as described, press Pass, press Fail otherwise.
        """;

    static JFrame createUI() {

        JButton but = new JButton("JButton");
        JPanel panel = new JPanel();
        JTextField jtf = new JTextField("JTextField", 20);

        JFrame.setDefaultLookAndFeelDecorated(true);
        JFrame frame = new JFrame("Cursor Test Window");
        frame.setLayout(new FlowLayout());
        panel.add(but);

        frame.getContentPane().add(jtf);
        frame.getContentPane().add(panel);

        JMenu menu1 = new JMenu("menu1");
        menu1.add(new JMenuItem("menu1,item1"));
        JMenuBar mb = new JMenuBar();
        mb.add(menu1);
        JMenu menu2 = new JMenu("menu2");
        for (int i = 0; i < 10; i++) {
            menu2.add(new JMenuItem("menu2,item"+i));
        }
        mb.add(menu2);
        frame.setJMenuBar(mb);
        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws Exception {
       PassFailJFrame.builder()
                .title("Cursor Test")
                .instructions(INSTRUCTIONS)
                .columns(60)
                .testUI(CursorTest::createUI)
                .build()
                .awaitAndCheck();

    }
}
