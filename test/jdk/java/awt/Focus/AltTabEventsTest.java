/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4524015
 * @summary Tests that when user switches between windows using Alt-tab then the appropriate events are generated
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AltTabEventsTest
 */

import java.awt.Button;
import java.awt.Choice;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class AltTabEventsTest {

    private static final String INSTRUCTIONS = """
           This test verifies that when user switches between windows using Alt-tab
           key combination then appropriate window events are generated. Also, when
           user interacts with Menu bar, Popup menu, Choice then no excessive window
           event is generated.

           After test started you will see Frame('Test for 4524015')-F1 with some
           components and Frame('Another frame')-F2 with no components.
           1. Make F1 active by clicking on it.
           2. Press Alt-tab.
           In the messqge dialog area you should see that
           WINDOW_DEACTIVATED, WINDOW_LOST_FOCUS event were generated.
           If you switched to F2 then also WINDOW_ACTIVATED, WINDOW_GAINED_FOCUS
           were generated.
           If no events were generated the test FAILED.
           Repeat the 2) with different circumstances.

           3. Make F1 active by clicking on it.
           4. Click on Menu bar/Button 'popup'/Choice and select some item from
           the list shown. If any of the window events appeared in the output then
           the test FAILED.

           else the test PASSED.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("AltTabEventsTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 5)
                .columns(35)
                .testUI(Test::new)
                .logArea()
                .build()
                .awaitAndCheck();
    }

}


class Test extends Frame {
    PopupMenu pop;
    Frame f;

    void println(String messageIn) {
        PassFailJFrame.log(messageIn);
    }

    public Test() {
        super("Test for 4524015");
        WindowAdapter wa = new WindowAdapter() {
                public void windowActivated(WindowEvent e) {
                    println(e.toString());
                }
                public void windowDeactivated(WindowEvent e) {
                    println(e.toString());
                }
                public void windowGainedFocus(WindowEvent e) {
                    println(e.toString());
                }
                public void windowLostFocus(WindowEvent e) {
                    println(e.toString());
                }
            };
        addWindowListener(wa);
        addWindowFocusListener(wa);

        f = new Frame("Another frame");
        f.addWindowListener(wa);
        f.addWindowFocusListener(wa);
        f.setBounds(800, 300, 300, 100);
        f.setVisible(true);

        setLayout(new FlowLayout());
        Button b = new Button("popup");
        add(b);
        b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    pop.show((Component)e.getSource(), 10, 10);
                }
            });
        Choice cho = new Choice();
        add(cho);
        cho.addItem("1");
        cho.addItem("2");
        cho.addItem("3");

        MenuBar bar = new MenuBar();
        Menu menu = new Menu("menu");
        MenuItem item = new MenuItem("first");
        menu.add(item);
        item = new MenuItem("second");
        menu.add(item);
        bar.add(menu);
        setMenuBar(bar);

        pop = new PopupMenu();
        pop.add("1");
        pop.add("@");
        add(pop);
        setSize(300, 100);
    }
}

