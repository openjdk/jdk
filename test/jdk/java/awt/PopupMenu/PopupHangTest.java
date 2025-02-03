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
/*
 * @test
 * @bug 4145193
 * @summary Mouse event activates multiple pull-down menus when testing Oracle app
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PopupHangTest
*/

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class PopupHangTest {
    private static final String INSTRUCTIONS = """
         2 areas yellow and red should be seen.

          Clicking in these areas should cause a menu to popup. See if you can
          get the menu to stay up and grab all input. One way to do this is to
          click and hold the mouse to popup the menu, move away/outside of the
          menu and release the mouse. At that point, the input is grabbed and
          the *only* way out is to hit the escape key. Try this on both areas.

          To make things worse, when the popup menu is up, click repeatedly on
          the LightWeight component area. Hit escape. Do you see multiple menus appearing ?

          If you do not see either of the two problems above, the problem is fixed.
          Press pass, else press Fail""";

    static Frame frame;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("PopupHangTest")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 2)
                      .columns(45)
                      .testUI(PopupHangTest::createUI)
                      .logArea()
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createUI() {
        TestMenuButton    m1;
        TestHeavyButton   m2;
        frame = new Frame();

        frame.setLayout (new BorderLayout ());

        m1 = new TestMenuButton("LightWeight component");
        m1.setBackground(Color.yellow);

        m2 = new TestHeavyButton("HeavyWeight component");
        m2.setBackground(Color.red);

        frame.add("North", m1);
        frame.add("South", m2);

        m1.requestFocus();
        frame.setSize(200, 110);
        return frame;
    }

}

class TestMenuButton extends Component implements MouseListener,
                                                MouseMotionListener,
                                                KeyListener,
                                                FocusListener  {

    PopupMenu popupMenu = null;
    String      name;

    TestMenuButton(String name) {
        PopupMenu menu = popupMenu = new PopupMenu("Popup");
        menu.add(new MenuItem("item 1"));
        menu.add(new MenuItem("item 2"));
        menu.add(new MenuItem("item 3"));
        this.add(menu);
        this.name = name;
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        addFocusListener(this);
    }

    void println(String messageIn) {
        PassFailJFrame.log(messageIn);
    }

    public void mouseClicked(MouseEvent event) {
        /*
        popupMenu.show(this, event.getX(), event.getY());
        */
    }
    public void mousePressed(MouseEvent event) {
        println("TestMenuButton.mousePressed() called !!");
        popupMenu.show(this, event.getX(), event.getY());
    }
    public void mouseReleased(MouseEvent event) {
        println("TestMenuButton.mouseReleased() called !!");
    }
    public void mouseEntered(MouseEvent event) {
        println("TestMenuButton.mouseEntered() called !!");
        requestFocus();
    }
    public void mouseExited(MouseEvent event) {
    }

    public void mouseDragged(MouseEvent event) {
        println("TestMenuButton.mouseDragged() called !!");
    }
    public void mouseMoved(MouseEvent event) {
        println("TestMenuButton.mouseMoved() called !!");
    }

    public void keyPressed(KeyEvent event) {
        println("TestMenuButton.keyPressed() called !!");
    }
    public void keyReleased(KeyEvent event) {
        println("TestMenuButton.keyReleased() called !!");
    }
    public void keyTyped(KeyEvent event) {
        println("TestMenuButton.keyTyped() called !!");
    }


    public void focusGained(FocusEvent e){
        println("TestMenuButton.focusGained():" + e);
    }
    public void focusLost(FocusEvent e){
        println("TestMenuButton.focusLost():" + e);
    }


    public void paint(Graphics g)  {
        Dimension d = getSize();

        g.setColor(getBackground());
        g.fillRect(0, 0, d.width-1, d.height-1);

        g.setColor(Color.black);
        g.drawString(name, 15, 15);
    }

    public Dimension getPreferredSize() {
        return (new Dimension(200, 50));
    }

}

class TestHeavyButton extends Label implements MouseListener,
                                                MouseMotionListener,
                                                KeyListener,
                                                FocusListener  {

    PopupMenu popupMenu = null;
    String      name;

    TestHeavyButton(String name) {
        super(name);
        PopupMenu menu = popupMenu = new PopupMenu("Popup");
        menu.add(new MenuItem("item 1"));
        menu.add(new MenuItem("item 2"));
        menu.add(new MenuItem("item 3"));
        this.add(menu);
        this.name = name;
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        addFocusListener(this);
    }

    void println(String messageIn) {
        PassFailJFrame.log(messageIn);
    }

    public void mouseClicked(MouseEvent event) {
        /*
        popupMenu.show(this, event.getX(), event.getY());
        */
    }
    public void mousePressed(MouseEvent event) {
        println("TestHeavyButton.mousePressed() called !!");
        popupMenu.show(this, event.getX(), event.getY());
    }
    public void mouseReleased(MouseEvent event) {
        println("TestHeavyButton.mouseReleased() called !!");
    }
    public void mouseEntered(MouseEvent event) {
        println("TestHeavyButton.mouseEntered() called !!");
        requestFocus();
    }
    public void mouseExited(MouseEvent event) {
    }

    public void mouseDragged(MouseEvent event) {
        println("TestHeavyButton.mouseDragged() called !!");
    }
    public void mouseMoved(MouseEvent event) {
        println("TestHeavyButton.mouseMoved() called !!");
    }

    public void keyPressed(KeyEvent event) {
        println("TestHeavyButton.keyPressed() called !!");
    }
    public void keyReleased(KeyEvent event) {
        println("TestHeavyButton.keyReleased() called !!");
    }
    public void keyTyped(KeyEvent event) {
        println("TestHeavyButton.keyTyped() called !!");
    }

    public void focusGained(FocusEvent e){
        println("TestHeavyButton.focusGained():" + e);
    }
    public void focusLost(FocusEvent e){
        println("TestHeavyButton.focusLost():" + e);
    }

}

