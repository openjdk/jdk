/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

/*
 * @test
 * @bug 5092883 6513478 7154025
 * @requires (os.family == "linux")
 * @summary REGRESSION: SystemColor class gives back wrong values under Linux
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual XAWTDifference
 */

public class XAWTDifference {

    private static final String INSTRUCTIONS = """
                You would see a frame with title "XAWTDifference Test Frame".

                Test Frame (1)

                a) It has three columns in it. The 1st one with ordinary components.
                   The 2nd one with disabled components.
                   The 3rd one with uneditable components (only text components
                   are there). Verify that the difference between different states
                   is visible.

                Standard Frame (2)

                b) You would also see a frame named StandardFrame (2)
                   with a lot of components in it. Actually this is just a jpg-image
                   in a frame. Verify that every component in the frame (1) looks
                   similar to the same component in (2).

                   They might differ in colors and be darker or brighter but
                   the whole picture should be the same.

                c) Also check the color of the MenuBar Items in the MenuBar and
                   the PopupMenu assigned to TextArea.
                   As you can't compare the colors of menu items with the picture
                   so just look if the are adequate enough.
                """;
    private static final int HGAP = 20;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .columns(40)
                      .testUI(XAWTDifference::createAndShowUI)
                      .positionTestUI(XAWTDifference::positionMultiTestUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Panel addComponentsIntoPanel(boolean enabled, boolean editable) {
        TextField tf = new TextField("TextField");
        TextArea ta = new TextArea("TextArea", 10, 10);

        Choice levelChooser = new Choice();
        levelChooser.add("Item #1");
        levelChooser.add("Item #2");

        Button b = new Button("BUTTON");
        Label label = new Label("LABEL");
        java.awt.List list = new java.awt.List(4, false);
        list.add("one");
        list.add("two");
        list.add("three");

        Checkbox chb = new Checkbox();
        Scrollbar sb = new Scrollbar(Scrollbar.HORIZONTAL);
        ScrollPane sp = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        sp.add(new TextArea("this is a textarea in scrollpane"));
        sp.setSize(200, 200);
        Canvas canvas = new Canvas();
        canvas.setSize(100, 100);

        //add popup menu to Button
        final PopupMenu pm = new PopupMenu();
        MenuItem i1 = new MenuItem("Item1");
        MenuItem i2 = new MenuItem("Item2");
        MenuItem i3 = new MenuItem("Item3");
        i3.setEnabled(false);
        pm.add(i1);
        pm.add(i2);
        pm.add(i3);
        canvas.add(pm);

        ta.add(pm);
        ta.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent me) {
                    if (me.isPopupTrigger()) {
                        pm.show(me.getComponent(), me.getX(), me.getY());
                    }
                }
            });

        ArrayList<Component> componentList = new ArrayList<>();

        componentList.add(tf);
        componentList.add(ta);
        if (editable){
            componentList.add(levelChooser);
            componentList.add(b);
            componentList.add(label);
            componentList.add(list);
            componentList.add(chb);
            componentList.add(sb);
            componentList.add(sp);
            componentList.add(canvas);
        } else {
            tf.setEditable(false);
            ta.setEditable(false);
        }

        Panel panel = new Panel();
        panel.setLayout(new GridLayout(0, 1));
        for (Component c : componentList) {
            if (!enabled) {
                c.setEnabled(false);
            }
            panel.add(c);
        }
        return panel;
    }

    private static List<Window> createAndShowUI() {
        Frame testFrame = new Frame("XAWTDifference Test Frame");
        StandardFrame standardFrame = new StandardFrame("StandardFrame");
        standardFrame.pack();

        testFrame.setLayout(new GridLayout(1, 3));
        testFrame.add(addComponentsIntoPanel(true, true));
        testFrame.add(addComponentsIntoPanel(false, true));
        testFrame.add(addComponentsIntoPanel(true, false));

        MenuItem mi1 = new MenuItem("Item1");
        MenuItem mi2 = new MenuItem("Item2");
        MenuItem mi3 = new MenuItem("Disabled Item3");
        mi3.setEnabled(false);

        MenuBar mb = new MenuBar();
        Menu enabledMenu = new Menu("Enabled Menu");
        Menu disabledMenu = new Menu("Disabled Menu");
        disabledMenu.setEnabled(false);
        mb.add(enabledMenu);
        mb.add(disabledMenu);
        enabledMenu.add(mi1);
        enabledMenu.add(mi2);
        enabledMenu.add(mi3);

        testFrame.setMenuBar(mb);
        testFrame.setSize(standardFrame.getWidth(), standardFrame.getHeight());
        return List.of(testFrame, standardFrame);
    }

    private static void positionMultiTestUI(List<? extends Window> windows,
                                            PassFailJFrame.InstructionUI instructionUI) {
        int x = instructionUI.getLocation().x + instructionUI.getSize().width + HGAP;
        for (Window w : windows) {
            w.setLocation(x, instructionUI.getLocation().y);
            x += w.getWidth() + HGAP;
        }
    }

    private static class StandardFrame extends Frame {
        public StandardFrame(String name) {
            super(name);
            String testPath = System.getProperty("test.src", ".");
            Panel panel = new Panel();
            panel.add(new JLabel(new ImageIcon(testPath + File.separator + "XAWTColors.jpg")));
            add(panel);
        }
    }
}
