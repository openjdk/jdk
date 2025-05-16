/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8165943
 * @summary LineBreakMeasurer does not measure correctly if TextAttribute.TRACKING is set
 * @library ../../regtesthelpers
 * @build PassFailJFrame
 * @run main/manual LineBreakWithTracking
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;
import java.util.Hashtable;
import java.lang.reflect.InvocationTargetException;

class LineBreakPanel extends JPanel implements ActionListener {

  private float textTracking = 0.0f;
  private static String fontName = "Dialog";
  private static String text = "This is a long line of text that should be broken across multiple lines. "
          + "Please set the different tracking values to test via menu! This test should pass if "
          + "these lines are broken to fit the width, and fail otherwise.  It should "
          + "also format the hebrew (אבג דהו) and arabic "
          + "(ابتج خلاخ) and CJK "
          + "(一丁丂가각쓺枱枲枳枴枵架枷"
          + "枸枹) text correctly.";

  private LineBreakMeasurer lineMeasurer;

  public void actionPerformed(ActionEvent e) {
    textTracking = (float)((JRadioButtonMenuItem)e.getSource()).getClientProperty( "tracking" );
    lineMeasurer = null;
    invalidate();
    repaint();
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    setBackground(Color.white);

    Graphics2D g2d = (Graphics2D)g;

    if (lineMeasurer == null) {
      Float regular = Float.valueOf(16.0f);
      Float big = Float.valueOf(24.0f);

      Hashtable map = new Hashtable();
      map.put(TextAttribute.SIZE, (float)18.0);
      map.put(TextAttribute.TRACKING, (float)textTracking);

      AttributedString astr = new AttributedString(text, map);
      astr.addAttribute(TextAttribute.SIZE, regular, 0, text.length());
      astr.addAttribute(TextAttribute.FAMILY, fontName, 0, text.length());

      int ix = text.indexOf("broken");
      astr.addAttribute(TextAttribute.SIZE, big, ix, ix + 6);
      ix = text.indexOf("hebrew");
      astr.addAttribute(TextAttribute.SIZE, big, ix, ix + 6);
      ix = text.indexOf("arabic");
      astr.addAttribute(TextAttribute.SIZE, big, ix, ix + 6);
      ix = text.indexOf("CJK");
      astr.addAttribute(TextAttribute.SIZE, big, ix, ix + 3);

      FontRenderContext frc = g2d.getFontRenderContext();
      lineMeasurer = new LineBreakMeasurer(astr.getIterator(), frc);
    }

    lineMeasurer.setPosition(0);

    float w = (float)getSize().width;
    float x = 0, y = 0;
    TextLayout layout;
    while ((layout = lineMeasurer.nextLayout(w)) != null) {
      x = layout.isLeftToRight() ? 0 : w - layout.getAdvance();
      y += layout.getAscent();
      layout.draw(g2d, x, y);
      y += layout.getDescent() + layout.getLeading();
    }
  }
}

public class LineBreakWithTracking {

  private static final String INSTRUCTIONS = """
     This manual test verifies that LineBreakMeasurer measures the lines'
     breaks correctly taking into account the TextAttribute.TRACKING value.
     The test string includes Latin, Arabic, CJK and Hebrew.

     You should choose a tracking value from the menu and resize the window.
     If the text lines break exactly to the wrapping width:
     no room for one more word exists and
     the text lines are not too long for given wrapping width, -
     then press PASS, otherwise - FAIL.
     """;

  public void createGUI(JFrame frame) {

    LineBreakPanel panel = new LineBreakPanel();
    frame.getContentPane().add(panel, BorderLayout.CENTER);

    JMenuBar menuBar = new JMenuBar();

    JMenu menu = new JMenu("Tracking");
    ButtonGroup btnGroup = new ButtonGroup();
    String btnLabels[] = {"-0.1", "0", "0.1", "0.2", "0.3"};
    float val = -0.1f;
    for (String label : btnLabels) {
      JRadioButtonMenuItem btn = new JRadioButtonMenuItem(label);
      btn.putClientProperty( "tracking", val );
      btn.addActionListener(panel);
      btnGroup.add(btn);
      menu.add(btn);
      val += 0.1f;
    }
    menuBar.add(menu);

    frame.setJMenuBar(menuBar);
  }

  public static void main(String[] args) throws InterruptedException, InvocationTargetException {

    JFrame frame = new JFrame("LineBreakMeasurer with Tracking");
    frame.setSize(new Dimension(640, 480));

    LineBreakWithTracking controller = new LineBreakWithTracking();
    controller.createGUI(frame);

    PassFailJFrame passFailJFrame = new PassFailJFrame(INSTRUCTIONS);
    PassFailJFrame.addTestWindow(frame);
    PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
    frame.setVisible(true);
    passFailJFrame.awaitAndCheck();
  }
}
