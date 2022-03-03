/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 8279614
 * @summary The left line of the TitledBorder is not painted on 150 scale factor
 * @requires (os.family == "windows")
 * @run main TitledBorderTest
 */
public class TitledBorderTest {

  public static JFrame frame;
  public static JPanel parentPanel;
  public static JPanel childPanel;

  public static void main(String[] args) throws Exception {
    LookAndFeelInfo laf = UIManager.getInstalledLookAndFeels()[3];
    System.out.println(laf);
    SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
    SwingUtilities.invokeAndWait(() -> createAndShowGUI());

    Robot robot = new Robot();

    BufferedImage buff = new BufferedImage(frame.getWidth()*2, frame.getHeight()*2,
            BufferedImage.TYPE_INT_ARGB);
    Graphics2D graph = buff.createGraphics();
    graph.scale(1.5, 1.5);
    frame.paint(graph);
    graph.dispose();

    robot.waitForIdle();
    boolean testFail = true;
    for (int i = 15; i < 25 && testFail == true; i++) {
      for (int j = 80; j < 100; j++) {
        if (buff.getRGB(i, j) == -6250336) {
          System.out.println(i + " " + j + " Color " + buff.getRGB(i, j));
          testFail = false;
          break;
        }
      }
    }

    if (testFail) {
      saveImage(buff, "test.png");
      throw new RuntimeException("Border was clipped or overdrawn.");
    }

    frame.dispose();
  }

  private static void createAndShowGUI() {
    frame = new JFrame("Swing Test");
    frame.setSize(new java.awt.Dimension(300, 200));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    parentPanel = new JPanel(new BorderLayout());
    parentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    childPanel = new JPanel(new BorderLayout());
    childPanel.setBorder(BorderFactory.createTitledBorder("Title"));
    childPanel.add(new JCheckBox(), BorderLayout.CENTER);

    parentPanel.add(childPanel, BorderLayout.CENTER);

    frame.getContentPane().add(parentPanel, BorderLayout.CENTER);

    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  private static void saveImage(BufferedImage image, String filename) {
    try {
      ImageIO.write(image, "png", new File(filename));
    } catch (IOException e) {
      // Don’t propagate the exception
      e.printStackTrace();
    }
  }

  private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
    try {
      UIManager.setLookAndFeel(laf.getClassName());
      System.out.println(laf.getName());
    } catch (UnsupportedLookAndFeelException ignored){
      System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
    } catch (ClassNotFoundException | InstantiationException |
            IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
