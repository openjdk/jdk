/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Checkbox;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4017504
 * @summary Test dynamically adding and removing a menu bar
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AddRemoveMenuBarTest_3
 */

public class AddRemoveMenuBarTest_3  {
    private static final String INSTRUCTIONS = """
            A frame at (100,100) contains two (2) rows of three (3) text
            fields each, and under this, a checkbox labelled 'Use menubar'.

            The first row's text fields pertain to the x coordinates and
            the second row's text fields pertain to the y coordinates.

            The first column, 'request', is an input only field for frame
            location. (press enter to apply).

            The second column, 'reported', is an output only
            field reporting frame location.

            The third column, 'inset', is an output only field reporting
            the frame's inset values.

            You can click the 'Use menubar' checkbox to alternately add
            and remove a menu bar containing an (empty) 'Help' menu.

            After a menubar is added or removed, the frame should not
            have been resized nor repositioned on the screen and the
            y inset should accurately reflect the presence or absence
            of the menubar within the inset.

            The insets always include the window manager's title and border
            decorations, if any.

            Upon test completion, click Pass or Fail appropriately.
            """;
    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("AddRemoveMenuBarTest_3 Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(30)
                .columns(38)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            AddRemoveMenuBar_3 frame = new AddRemoveMenuBar_3();

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(null,
                    PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });

        passFailJFrame.awaitAndCheck();
    }
}

class AddRemoveMenuBar_3 extends Frame {
    TextField xfield;
    TextField yfield;

    TextField xfield_out;
    TextField yfield_out;
    TextField xinset_out;
    TextField yinset_out;

    Checkbox menu_checkbox;
    MenuBar menubar;

    public AddRemoveMenuBar_3() {
        super("AddRemoveMenuBar_3");

        menubar = new MenuBar();
        menubar.setHelpMenu(new Menu("Help"));

        setLayout(new BorderLayout());
        Panel p = new Panel();
        add("Center", p);
        p.setLayout(new GridLayout(3, 3));

        menu_checkbox = new Checkbox("Use menubar");
        add("South", menu_checkbox);

        xfield = new TextField();
        yfield = new TextField();
        xfield_out = new TextField();
        xfield_out.setEditable(false);
        xfield_out.setFocusable(false);
        yfield_out = new TextField();
        yfield_out.setEditable(false);
        yfield_out.setFocusable(false);

        xinset_out = new TextField();
        xinset_out.setEditable(false);
        xinset_out.setFocusable(false);
        yinset_out = new TextField();
        yinset_out.setEditable(false);
        yinset_out.setFocusable(false);

        p.add(new Label("request"));
        p.add(new Label("reported"));
        p.add(new Label("inset"));

        p.add(xfield);
        p.add(xfield_out);
        p.add(xinset_out);

        p.add(yfield);
        p.add(yfield_out);
        p.add(yinset_out);

        setSize(200, 200);
        setLocation(100, 100);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                xfield_out.setText(Integer.toString(getLocation().x));
                yfield_out.setText(Integer.toString(getLocation().y));

                xinset_out.setText(Integer.toString(getInsets().left));
                yinset_out.setText(Integer.toString(getInsets().top));
            }
        });

        ActionListener setLocationListener = e -> {
            Rectangle r = getBounds();
            try {
                r.x = Integer.parseInt(xfield.getText());
                r.y = Integer.parseInt(yfield.getText());
            } catch (java.lang.NumberFormatException ignored) {
            }

            setLocation(r.x, r.y);
        };

        xfield.addActionListener(setLocationListener);
        yfield.addActionListener(setLocationListener);

        menu_checkbox.addItemListener(e -> {
            if (menu_checkbox.getState()) {
                setMenuBar(menubar);
            } else {
                setMenuBar(null);
            }

            validate();
            xinset_out.setText(Integer.toString(getInsets().left));
            yinset_out.setText(Integer.toString(getInsets().top));
        });
    }
}
