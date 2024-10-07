/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @key headful
 * @summary Test dynamically changing frame component visibility and repacking
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameRepackTest
 */

public class FrameRepackTest {

    private static final String INSTRUCTIONS = """
            There is a green frame with a menubar.
            The menubar has one menu, labelled 'Flip'.
            The menu has two items, labelled 'visible' and 'not visible'.
            The frame also contains a red panel that contains two line labels,
            'This panel is always displayed' and 'it is a test'.

            If you select the menu item 'Flip->visible', then another panel is
            added below the red panel.
            The added panel is blue and has yellow horizontal and vertical scrollbars.

            If you select menu item 'Flip->not visible', the second panel
            is removed and the frame appears as it did originally.

            You can repeatedly add and remove the second panel in this way.
            After such an addition or removal, the frame's location on the screen
            should not change, while the size changes to accommodate
            either just the red panel or both the red and the blue panels.

            If you resize the frame larger, the red panel remains at the
            top of the frame with its height fixed and its width adjusted
            to the width of the frame.

            Similarly, if it is present, the blue panel and its yellow scroolbars
            remain at the bottom of the frame with fixed height and width adjusted
            to the size of the frame.  But selecting 'visible' or 'not visible'
            repacks the frame, thereby adjusting its size tightly to its panel(s).

            Upon test completion, click Pass or Fail appropriately.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FrameRepackTest Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(30)
                .columns(45)
                .testUI(FrameRepack::new)
                .build()
                .awaitAndCheck();
    }

}

class FrameRepack extends Frame implements ActionListener {

    Panel south;

    public FrameRepack() {
        super("FrameRepack");

        // create the menubar
        MenuBar menubar = new MenuBar();
        this.setMenuBar(menubar);
        // create the options
        Menu flip = new Menu("Flip");
        MenuItem mi;
        mi = new MenuItem("visible");
        mi.addActionListener(this);
        flip.add(mi);
        mi = new MenuItem("not visible");
        mi.addActionListener(this);
        flip.add(mi);

        menubar.add(flip);

        setLayout(new BorderLayout(2, 2));
        setBackground(Color.green);

        // north panel is always displayed
        Panel north = new Panel();
        north.setBackground(Color.red);
        north.setLayout(new BorderLayout(2, 2));
        north.add("North", new Label("This panel is always displayed"));
        north.add("Center", new Label("it is a test"));
        north.setSize(200, 200);
        add("North", north);

        // south panel can be visible or not...
        // The problem seems to occur when I put this panel not visible
        south = new Panel();
        south.setBackground(Color.white);
        south.setLayout(new BorderLayout());

        ScrollPane scroller = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        scroller.setBackground(Color.yellow);
        Panel pan1 = new Panel();
        pan1.setBackground(Color.blue);
        pan1.setLayout(new BorderLayout());

        pan1.setSize(400, 150);
        scroller.add("Center", pan1);

        south.add("South", scroller);

        add("South", south);

        south.setVisible(false);

        setSize(350, 300);

        pack();
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() instanceof MenuItem) {
            if (evt.getActionCommand().equals("visible")) {
                south.setVisible(true);
                pack();
            } else if (evt.getActionCommand().equals("not visible")) {
                south.setVisible(false);
                pack();
            }
        }
    }
}
