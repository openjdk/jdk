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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
  * This test is mean to isolate the speed of JLabel painting
  * It creates a grid of JLabels (some with icons) and
  * proceeds to change their color and repaint them.
  */

public class LabelTest extends AbstractSwingTest {

   JLabel[] labels;
   final int repeat = 255;
   final int gridDimension = 6;
   JPanel panel;

   public JComponent getTestComponent() {
      panel = new JPanel();
      panel.setLayout(new GridLayout( gridDimension, gridDimension) );
      labels = new JLabel[gridDimension*gridDimension];
      for (int i = 0; i < labels.length; i++) {
         labels[i] = new CounterLabel( "Label #" + i);
         if (i % 2 == 0) {
            labels[i].setOpaque(true);
         } else {
            labels[i].setOpaque(false);
         }
         panel.add(labels[i]);
      }
      labels[0].setIcon(UIManager.getIcon("Tree.openIcon"));
      labels[5].setIcon(UIManager.getIcon("Tree.closedIcon"));
      labels[10].setIcon(UIManager.getIcon("Tree.leafIcon"));
      labels[15].setIcon(UIManager.getIcon("Tree.expandedIcon"));
      labels[20].setIcon(UIManager.getIcon("Tree.collapsedIcon"));

      return panel;
   }

   public String getTestName() {
      return "Labels";
   }

        public void runTest() {
      LabelChanger changer = new LabelChanger(labels);
      for (int i = 0; i < repeat; i++) {
         try {
            changer.setColor( new Color( i,i,i) );
            SwingUtilities.invokeLater(changer);
            //panel.repaint();
            rest();
         } catch (Exception e) {System.out.println(e);}
      }
   }

   public static void main(String[] args) {
      runStandAloneTest(new LabelTest());
   }


   class CounterLabel extends JLabel {
      CounterLabel(String s) {
         super(s);
      }
      public void paint(Graphics g) {
         paintCount++;
         super.paint(g);
      }
   }
}

class LabelChanger implements Runnable {
   JLabel[] labels;
   Color color;


   public LabelChanger(JLabel[] labelsToChange) {
      labels = labelsToChange;
   }

   public void setColor(Color newColor) {
      color = newColor;
   }

   public void run() {
      for (int i = 0; i < labels.length; i++) {
          labels[i].setForeground(color);
      }
   }
}
