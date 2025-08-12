/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.Date;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
  * This tests Swing Menus by posting key events to the EventQueue
  * Each time a menu is selected ActionEvent/MenuEvent is generated
  * and that event causes the menu text to be appended to a JList
  *
  */

public class JMTest_01 extends AbstractSwingTest {

    JList           list;
    JMenuBar        jmenubar = null;

    int             nMenuCount = 2;
    int             nMenuItemCount = 4;
    int             ENTER = 10;
    int             LEFT = 37;
    int             RIGHT = 39;
    int             DOWN = 40;
    int             UP = 38;

    public JComponent getTestComponent() {
        JPanel panel = new JPanel();

        JMenu           jmenu;
        JMenuItem       jmenuitem;

        panel.setLayout(new BorderLayout());

        jmenubar = new JMenuBar();
        for (int i = 0; i < nMenuCount; i ++) {
            jmenu = new JMenu("JMenu" + i);
            jmenu.setMnemonic('0' + i);
            jmenu.addMenuListener(new MyMenuListener());
            jmenubar.add(jmenu);

            for (int j = 0; j < nMenuItemCount; j ++) {
                jmenuitem = new JMenuItem("JMenuItem" + i + j);
                jmenuitem.setMnemonic('0' + j);
                jmenu.add(jmenuitem);
            }
        }

        panel.add("North", jmenubar);

        list = new JList(new DefaultListModel());
        list.setFont(new Font("Serif", Font.BOLD, 14));
        JScrollPane scrollPane = new JScrollPane(list);
        panel.add("Center", scrollPane);

        return panel;
    }

    public String getTestName() {
        return "JMTest_01";
    }

    public void runTest() {
        for (int i = 0; i < 10; i++) {
            testMenu();
        }
    }

    @SuppressWarnings("deprecation")
    public void testMenu() {
        JMenu menu;
        KeyEvent key;
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        int direction = RIGHT;

        int nCount = jmenubar.getMenuCount();

        menu = jmenubar.getMenu(0);
        int firstMnem = menu.getMnemonic();

        key = new KeyEvent(menu, KeyEvent.KEY_PRESSED,
                            new Date().getTime(), KeyEvent.ALT_DOWN_MASK, firstMnem);
        queue.postEvent(key);

        for (int i = 1; i < nCount; i ++) {
            key = new KeyEvent(menu, KeyEvent.KEY_PRESSED,
                               new Date().getTime(), 0, direction);
            queue.postEvent(key);
            rest();

            menu = jmenubar.getMenu(i);

            try {
                Thread.sleep(10);
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        direction = LEFT;
        for (int i = 1; i < nCount; i ++) {
            key = new KeyEvent(menu, KeyEvent.KEY_PRESSED,
                               new Date().getTime(), 0, direction);
            queue.postEvent(key);
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                System.out.println(e);
            }

            menu = jmenubar.getMenu(i);

            try {
                Thread.sleep(10);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    public static void main(String[] args) {
        runStandAloneTest(new JMTest_01());
    }

    public class MyMenuListener implements MenuListener {
        public void menuCanceled(MenuEvent e) {
        }

        public void menuDeselected(MenuEvent e) {
        }

        public void menuSelected(MenuEvent e) {
            JMenu comp = (JMenu) e.getSource();
            Display(comp.getText());
        }
    }

    public void Display(String str) {
        DefaultListModel  lm = (DefaultListModel) list.getModel();
        lm.addElement(str);
        int nSize = lm.getSize();
        list.setSelectedIndex(nSize - 1);
        list.requestFocus();
    }
}
