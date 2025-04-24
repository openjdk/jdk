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

/*
 * @test
 * @bug 6347994
 * @summary REG: Scrollbar, Choice, Checkbox flickers and grays out when scrolling, XToolkit
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FlickeringOnScroll
 */

import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

public class FlickeringOnScroll extends Frame {

    static final String INSTRUCTIONS = """
            There are five components in the frame:
            Scrollbars(vertical and horizontal), a Choice,
            a Checkbox and a TextArea
            1) Drag the thumbs of each Scrollbar.
            2) Do the same with Choice's scrollbar.
            3) Focus on Checkbox and press left mouse button or SPACE repeatedly.
            4) Right click inside TextArea and navigate through all menu items
               in PopupMenu using the arrow keys.
            If you notice some component or its scrollbar flickers on
            key/mouse press or drag, press Fail. Otherwise press Pass.
            """;

    public FlickeringOnScroll() {
        Choice ch = new Choice();
        ch.add("Praveen");
        ch.add("Mohan");
        ch.add("Rakesh");
        ch.add("Menon");
        ch.add("Girish");
        ch.add("Ramachandran");
        ch.add("Elancheran");
        ch.add("Subramanian");
        ch.add("Raju");
        ch.add("Pallath");
        ch.add("Mayank");
        ch.add("Joshi");
        ch.add("Sundar");
        ch.add("Srinivas");
        ch.add("Mandalika");
        Checkbox chb = new Checkbox ("Checkbox", false);
        TextArea ta = new TextArea("Text Area");
        Panel panel = new Panel();
        PopupMenu popup = new PopupMenu("Popup");
        MenuItem mi1 = new MenuItem("mi1");
        MenuItem mi2 = new MenuItem("mi2");
        MenuItem mi3 = new MenuItem("mi3");
        MenuItem mi4 = new MenuItem("mi4");

        setTitle("Flickering Scroll Area Testing Frame");
        setLayout(new FlowLayout());
        add(ch);
        add(chb);
        add(ta);

        panel.setLayout(new BorderLayout());
        panel.setPreferredSize(new Dimension(200, 200));
        add(panel);
        panel.add("Center",new java.awt.Label("Scrollbar flickering test..." ,java.awt.Label.CENTER));
        panel.add("South",new Scrollbar(Scrollbar.HORIZONTAL, 0, 100, 0, 255));
        panel.add("East",new Scrollbar(Scrollbar.VERTICAL, 0, 100, 0, 255));

        ta.add(popup);
        popup.add (mi1);
        popup.add (mi2);
        popup.add (mi3);
        popup.add (mi4);

        ta.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent me) {
                    if (me.isPopupTrigger()) {
                        if (popup != null) {
                            popup.show(me.getComponent(), me.getX(), me.getY());
                        }
                    }
                }
                public void mouseReleased(MouseEvent me) {
                    if (me.isPopupTrigger()) {
                        if (popup != null) {
                            popup.show(me.getComponent(), me.getX(), me.getY());
                        }
                    }
                }
        });

        pack();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Scroll Area Flickering Repaint")
                .testUI(FlickeringOnScroll::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
