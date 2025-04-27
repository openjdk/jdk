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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.AbstractButton;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
  * This tests Swing Menus by posting key events to the EventQueue
  * Each time a menu is selected ActionEvent/MenuEvent is generated
  * and that event causes the menu text to be appended to a JList
  */

public class JMTest_05 extends AbstractSwingTest {
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
        JMenu           jsubmenu;

        panel.setLayout(new BorderLayout());

        jmenubar = new JMenuBar();

        for (int i = 0; i < nMenuCount; i ++) {
            jmenu = new JMenu("JMenu" + i);
            jmenu.setMnemonic('0' + i);
            jmenubar.add(jmenu);

            for (int j = 0; j < nMenuItemCount; j ++) {
                int mn = 'A';
                mn = mn + j;
                jsubmenu = new JMenu("SubMenu" + String.valueOf((char) mn));
                jsubmenu.setMnemonic('A' + j);
                jmenu.add(jsubmenu);
                for (int k = 0; k <= j; k ++) {
                    jmenuitem = new JMenuItem("SubMenu - JMenuItem" + i + (char) mn + k);
                    jmenuitem.setMnemonic('0' + k);
                    jmenuitem.addActionListener(new MyActionListener());
                    jsubmenu.add(jmenuitem);
                }
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
        return "JMTest_05";
    }

    public void runTest() {
        for (int i = 0; i < 10; i++) {
            testMenu();
        }
    }

    public void testMenu() {
        FireEvents();
    }

   @SuppressWarnings("deprecation")
    public void FireEvents() {
        int nCount = jmenubar.getMenuCount();
        int mnem;
        JMenuItem menuitem;
        KeyEvent key;
        int firstMnem;
        JMenu   menu;

        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        for (int i = 0; i < nCount; i++) {
            menu = jmenubar.getMenu(i);
            int nItemCount = menu.getItemCount();

            for (int j = 0; j < nItemCount; j ++) {
                JMenuItem mi = menu.getItem(j);

                if (mi instanceof JMenu) {
                    JMenu targetmenu = (JMenu) mi;

                    int nC = targetmenu.getItemCount();
                    for (int k = 0; k < nC; k ++) {
                        firstMnem = menu.getMnemonic();
                        key = new KeyEvent(menu, KeyEvent.KEY_PRESSED,
                                           new Date().getTime(), KeyEvent.ALT_DOWN_MASK, firstMnem);
                        queue.postEvent(key);
                        rest();

                        mnem = mi.getMnemonic();
                        key = new KeyEvent(menu, KeyEvent.KEY_PRESSED,
                                           new Date().getTime(), 0, mnem);
                        queue.postEvent(key);
                        try {
                                Thread.sleep(10);
                        } catch (Exception e) {
                                System.out.println(e);
                        }

                        JMenuItem menui = targetmenu.getItem(k);
                        mnem = menui.getMnemonic();
                        key = new KeyEvent(targetmenu, KeyEvent.KEY_PRESSED,
                                           new Date().getTime(), 0, mnem);

                        queue.postEvent(key);
                        try {
                                Thread.sleep(10);
                        } catch (Exception e) {
                                System.out.println(e);
                        }
                    }
                }
            }
        }
    }


    public static void main(String[] args) {
        runStandAloneTest(new JMTest_05());
    }

    public class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            AbstractButton comp = (AbstractButton) e.getSource();
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
