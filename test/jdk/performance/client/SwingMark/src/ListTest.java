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
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
  * This test is mean to isolate the speed of the JList.
  * It creates a JList and adds many items to it.  It then
  * scrolls through the list.
  */

public class ListTest extends AbstractSwingTest {
    JList list1;
    final int list1ItemCount = 250;
    String DISPLAY_STRING = "List Item ";

    public JComponent getTestComponent() {
        loadBundle();
        JPanel panel = new JPanel();
        String[] list1Data = new String[list1ItemCount];
        for ( int i = 0; i < list1ItemCount; i++) {
           list1Data[i] = DISPLAY_STRING+" " + i;
        }
        list1 = new CountList(list1Data);
        JScrollPane scrollPane = new JScrollPane(list1);
        if (SwingMark.useBlitScrolling) {
           scrollPane.getViewport().putClientProperty("EnableWindowBlit", Boolean.TRUE);
        }
        panel.add(scrollPane);
        return panel;
     }

     private void loadBundle() {
         ResourceBundle bundle = ResourceBundle.getBundle("resources.ListTest");
         DISPLAY_STRING = bundle.getString("DisplayString");
     }

     public String getTestName() {
         return "Lists";
     }

     public void runTest() {
         testList(list1, 1);
     }

     public void testList(JList currentList, int scrollBy) {
         ListScroller scroll = new ListScroller(currentList, scrollBy);
         for (int i = currentList.getSelectedIndex() ;
              i < currentList.getModel().getSize();
              i++) {
           try {
              SwingUtilities.invokeLater(scroll);
              rest();
           } catch (Exception e) {System.out.println(e);}
        }
    }

    public static void main(String[] args) {
        runStandAloneTest(new ListTest());
    }

    class CountList extends JList {
        public CountList(String[] s) {
           super(s);
        }

        public void paint(Graphics g) {
           super.paint(g);
           paintCount++;
        }
    }

}


class ListScroller implements Runnable {
   JList list;
   int scrollAmount = 1;


   public ListScroller(JList listToScroll, int scrollBy) {
      list = listToScroll;
      scrollAmount = scrollBy;
   }

   public void run() {
                int currentVal = list.getSelectedIndex();
      list.setSelectedIndex(currentVal+scrollAmount);
                list.ensureIndexIsVisible(currentVal+scrollAmount);
   }
}
