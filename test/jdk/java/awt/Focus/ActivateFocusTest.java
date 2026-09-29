/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4369903
 * @summary Focus on window activation does not work correctly
 * @key headful
 * @run main ActivateFocusTest
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ActivateFocusTest {

    public static void main(final String[] args) {
        ActivateFocusTest app = new ActivateFocusTest();
        app.doTest();
    }

    public void doTest() {
      ActivateFocus[] af = new ActivateFocus[2];
      boolean testFailed = false;
      Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
      for (int i = 0; i < 2; i++) {
          af[i] = new ActivateFocus(i);
          af[i].setLocation(i * 160 + scrSize.width / 2, scrSize.height / 2);
          af[i].setVisible(true);
      }
      try {
          Thread.sleep(5000);
      } catch (InterruptedException ie) {
          throw new RuntimeException("TEST FAILED - thread was interrupted");
      }
      for (int i = 0; i < 2; i++) {
          testFailed = (af[i].lw.focusCounter > 1);
      }
      if (testFailed) {
          throw new RuntimeException("TEST FAILED - focus is gained more than one time");
      } else {
          System.out.println("TEST PASSED");
      }
    }

 }

class ActivateFocus extends Frame {

    public LightWeight lw = null;
    int num;

    public String toString() {
        return ("Window " + num);
    }

    public ActivateFocus(int i) {
        setTitle("Window " + i);
        lw = new LightWeight(i);
        num=i;
        addWindowListener(new WindowAdapter() {
            public void windowActivated(WindowEvent e) {
                if(lw != null) {
                    lw.requestFocus();
                }
            }
        });
        add(lw);
        pack();
    }

    // A very simple lightweight component
    class LightWeight extends Component implements FocusListener {

        boolean focused = false;
        int num;
        public int focusCounter = 0;

        public LightWeight(int num) {
            this.num = num;
            addFocusListener(this);
        }

        public void paint(Graphics g) {
            Dimension size = getSize();
            int w = size.width;
            int h = size.height;
            g.setColor(getBackground());
            g.fillRect(0, 0, w, h);
            g.setColor(Color.black);
            g.drawOval(0, 0, w-1, h-1);
            if (focused) {
                g.drawLine(w/2, 0, w/2, h);
                g.drawLine(0, h/2, w, h/2);
            }

        }

        public Dimension getPreferredSize() {
            return new Dimension(150, 150);
        }

        public void focusGained(FocusEvent e) {
            focused = true;
            focusCounter++;
            System.out.println("focusGained on " + e.getComponent());
            repaint();
        }

        public void focusLost(FocusEvent e) {
            focused = false;
            System.out.println("focusLost on " + e.getComponent());
            repaint();
        }

        public String toString() {
            return ("Component " + num);
        }
    }
}
