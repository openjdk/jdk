/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
   @bug 6593649
   @summary Word wrap does not work in JTextArea: long lines are not wrapped
   @author Lillian Angel
   @run main Test6593649
*/

import javax.swing.*;
import java.awt.*;

public class Test6593649 extends JFrame {
  static JTextArea txt;
  static JPanel innerPanel;

  public Test6593649(Dimension d)
  {
    super("Word Wrap Testcase");

    setSize(d);

    final Container contentPane = getContentPane();

    innerPanel = new JPanel();
    innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.LINE_AXIS));

    txt = new JTextArea("This is a long line that should wrap, but doesn't...");
    txt.setLineWrap(true);
    txt.setWrapStyleWord(true);

    innerPanel.add(txt);

    contentPane.add(innerPanel, BorderLayout.SOUTH);
  }

  public static void main(String[] args) throws InterruptedException
  {
    int size = 100;
    Dimension d;
    Test6593649 cp;
    Dimension txtSize;
    Dimension innerSize;
    Dimension cpSize;

    while (size <= 600)
    {
      d = new Dimension(size, size);
      cp = new Test6593649(d);
      cp.setVisible(true);

      txtSize = txt.getPreferredSize();
      innerSize = innerPanel.getPreferredSize();
      cpSize = cp.getSize();

      if (!(txtSize.getWidth() == innerPanel.getWidth() && txtSize.getHeight() == innerPanel.getHeight() &&
           txtSize.getWidth() <= cpSize.getWidth() && txtSize.getHeight() <= cpSize.getHeight()))
      {
        throw new RuntimeException("Test failed: Text area size does not properly match panel and frame sizes");
      }

      Thread.sleep(2000);

      cp.hide();
      size += 50;
    }
  }
}
