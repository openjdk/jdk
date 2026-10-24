/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6405689
 * @key headful
 * @summary Reg: Painting is not happening properly,
 *          when Choice scrollbar gets disappeared after selected item
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PaintArtefacts
*/

import java.awt.Button;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class PaintArtefacts {

    static boolean removeItems = true;
    private static final String INSTRUCTIONS = """
        The problem is seen on XToolkit only.
        Open the choice and press down or up key several times
        until the scrollbar gets disappeared.
        At that moment you may see a painting artefacts on dropdown menu
        If you see them, press Fail.
        If you don't see them press Add/Remove switch button and
        again open the choice and press Up/Down key several times
        until Scrollbar gets appeared back.
        If you still don't see any painting artefacts press Pass.
        """;

    private static Frame init() {
        Frame frame = new Frame("Painting Frame");
        Button b = new Button ("Add/Remove switch");
        final Choice ch = new Choice();
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
        ch.add("Suresh");
        ch.add("Chandar");
        ch.select(1);
        frame.setLayout(new FlowLayout());
        frame.add(ch);
        frame.add(b);
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                PassFailJFrame.log(ae.toString());
                PassFailJFrame.log("selected index = " + ch.getSelectedIndex());
                removeItems = !removeItems;
            }
        });
        ch.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent ie) {
                PassFailJFrame.log(ie.toString());
                PassFailJFrame.log("selected index = " + ch.getSelectedIndex());
                if (removeItems) {
                    PassFailJFrame.log("REMOVE : " + ch.getSelectedIndex());
                    ch.remove(ch.getSelectedIndex());
                } else {
                    PassFailJFrame.log("ADD : "+ch.getSelectedIndex() + "/" + "new item");
                    ch.add("new item");
                }
            }
        });
        frame.setSize(200, 200);

        for (int i = 0; i < 5; i++){
            ch.remove(ch.getSelectedIndex());
        }

        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(PaintArtefacts::init)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
