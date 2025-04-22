import java.util.Date;
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

import java.util.ResourceBundle;
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

public class JMTest_04 extends AbstractSwingTest {
    JList       list;
    JMenuBar    jmenubar = null;

    int     nMenuCount = 2;
    int     nMenuItemCount = 4;
    int     ENTER = 10;
    int     LEFT = 37;
    int     RIGHT = 39;
    int     DOWN = 40;
    int     UP = 38;

    String MENU_STRING =  "JMenu";
    String MENU_ITEM_STRING =  "JMenuItem";
    String SUB_MENU_STRING =  "SubMenu";

    public JComponent getTestComponent() {
        loadBundle();
        JPanel panel = new JPanel();

        JMenu       jmenu;
        JMenuItem   jmenuitem;
        JMenu       jsubmenu;

        panel.setLayout(new BorderLayout());

        jmenubar = new JMenuBar();
        for (int i = 0; i < nMenuCount; i ++) {
            jmenu = new JMenu(MENU_STRING + i);
            jmenu.setMnemonic('0' + i);
            jmenubar.add(jmenu);

            for (int j = 0; j < nMenuItemCount; j ++) {
                int mn = 'A';
                mn = mn + j;
                jsubmenu = new JMenu(SUB_MENU_STRING + String.valueOf((char) mn));
                jsubmenu.setMnemonic('A' + j);
                jmenu.add(jsubmenu);
                for (int k = 0; k <= j; k ++) {
                    jmenuitem = new JMenuItem(SUB_MENU_STRING+" - "+MENU_ITEM_STRING + i + (char) mn + k);
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

    private void loadBundle() {
        ResourceBundle bundle = ResourceBundle.getBundle("resources.JMTest_04");
        MENU_STRING =  bundle.getString("MenuString");
        MENU_ITEM_STRING =  bundle.getString("MenuItemString");
        SUB_MENU_STRING =  bundle.getString("SubMenuString");
    }

    public String getTestName() {
        return "Sub-Menus";
    }

    public void runTest() {
        for (int i = 0; i < 4; i++) {
            testMenu();
        }
    }

    public void testMenu() {
        FireEvents();
    }


   @SuppressWarnings("deprecation")
    public void FireEvents() {
        int nMenuCount = jmenubar.getMenuCount();

        KeyEvent key;
        int firstMnem;
        int direction;

        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        jmenubar.requestFocus();
        for (int i = 0; i < nMenuCount; i ++) {
            JMenu currentmenu = jmenubar.getMenu(i);
            int currentmenuMnem = currentmenu.getMnemonic();

            int nMenuItemCount = currentmenu.getItemCount();

            for (int j = 0; j < nMenuItemCount; j ++) {
                JMenuItem tempmenuitem = currentmenu.getItem(j);
                if (tempmenuitem instanceof JMenu) {
                    JMenu targetmenu = (JMenu) tempmenuitem;
                    int iTargetMenuCount = targetmenu.getItemCount();

                    for (int k = 0; k < iTargetMenuCount; k ++) {
                        key = new KeyEvent(currentmenu, KeyEvent.KEY_PRESSED,
                             new Date().getTime(), KeyEvent.ALT_MASK, currentmenuMnem);
                        queue.postEvent(key);

                        rest();

                        direction = DOWN;
                        for (int iTemp = 0; iTemp < j; iTemp ++) {
                            key = new KeyEvent(currentmenu, KeyEvent.KEY_PRESSED,
                                new Date().getTime(), 0, direction);
                            queue.postEvent(key);
                            rest();
                        }


                        direction = RIGHT;
                        key = new KeyEvent(currentmenu, KeyEvent.KEY_PRESSED,
                                           new Date().getTime(), 0, direction);
                        queue.postEvent(key);
                        rest();

                        direction = DOWN;
                        for (int iTemp = 0; iTemp < k; iTemp ++) {
                            key = new KeyEvent(targetmenu, KeyEvent.KEY_PRESSED,
                                                new Date().getTime(), 0, direction);
                            queue.postEvent(key);
                            rest();
                        }

                        key = new KeyEvent(targetmenu, KeyEvent.KEY_PRESSED,
                                           new Date().getTime(), 0, ENTER);
                        queue.postEvent(key);
                        rest();
                    }
                }
            }
        }
    }


    public static void main(String[] args) {
        runStandAloneTest(new JMTest_04());
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
