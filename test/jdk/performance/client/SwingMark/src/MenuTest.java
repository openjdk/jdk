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
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
  * This tests Swing Menus by posting key events to the EventQueue
  * Each time a menu is selected and ActionEvent is generated
  * and that event causes some text to be appended to a JTextArea
  *
  * note: this test has been replaced by JMTest_02
  */

public class MenuTest extends AbstractSwingTest {

   JMenuBar menuBar;
   JMenu menu1;
   JMenu menu2;
   JMenu menu3;
   JMenu menu4;

   JMenu subMenu;

   JTextArea textArea;

   ActionListener listener;

   int repeat = 50;

   public JComponent getTestComponent() {
      listener = new MyListener();

      JPanel panel = new JPanel();
      panel.setLayout( new BorderLayout() );
      menuBar = new JMenuBar();
      panel.add(menuBar, BorderLayout.NORTH);

      menu1 = new JMenu("Menu1");
      menu1.setMnemonic('M');
      menuBar.add(menu1);
      loadMenu(menu1, 5);

      menu2 = new JMenu("Menu2");
      menu2.setMnemonic('e');
      menuBar.add(menu2);
      loadMenu(menu2, 4);

      menu3 = new JMenu("Menu3");
      menu3.setMnemonic('n');
      menuBar.add(menu3);
      loadMenu(menu3, 6);

      menu4 = new JMenu("Menu4");
      menu4.setMnemonic('u');
      menuBar.add(menu4);

      textArea = new JTextArea(10,10);
      textArea.setLineWrap(true);
      JScrollPane scroll = new JScrollPane(textArea);
      panel.add(scroll, BorderLayout.CENTER);
      return panel;
   }

    private void loadMenu(JMenu menu, int numItems) {
       for (int i = 0; i < numItems; i++) {
          JMenuItem item = new JMenuItem("Item " + i, String.valueOf(i).toCharArray()[0]);
          menu.add(item);
          item.addActionListener(listener);
       }
    }

    public String getTestName() {
        return "Menus";
    }

    public void runTest() {
       for (int i = 0; i < repeat; i++) {
           testMenu(menu1);
           testMenu(menu2);
           testMenu(menu3);
       }
   }

   @SuppressWarnings("deprecation")
   public void testMenu(JMenu currentMenu) {
       int c = currentMenu.getMnemonic();
       EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();

       for (int i = 0; i < currentMenu.getItemCount(); i++) {

          KeyEvent key = new KeyEvent(currentMenu,
                    KeyEvent.KEY_PRESSED,
                    new Date().getTime(),
                    KeyEvent.ALT_DOWN_MASK,
                    c);
          queue.postEvent(key);

          rest();
          key = new KeyEvent(currentMenu,
                    KeyEvent.KEY_PRESSED,
                    new Date().getTime(),
                    0,
                    currentMenu.getItem(i).getMnemonic() );
          queue.postEvent(key);

       }
   }

   public static void main(String[] args) {
      runStandAloneTest(new MenuTest());
   }

   class MyListener implements ActionListener {
      public void actionPerformed(ActionEvent e) {
         JMenuItem item = (JMenuItem)e.getSource();
         textArea.append(item.getText() + " ");
      }
   }
}
