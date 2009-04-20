/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @summary Pass if no RuntimeException.
 * @bug 6812600
 */
import java.awt.*;
import java.awt.image.BufferedImage;

public class JoinMiterTest {

  public static void main(String[] args) throws Exception {
    BufferedImage image = new BufferedImage(200, 200,
BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setPaint(Color.WHITE);
    g.fill(new Rectangle(image.getWidth(), image.getHeight()));
    g.translate(25, 100);
    g.setPaint(Color.BLACK);
    g.setStroke(new BasicStroke(20, BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_MITER));
    g.draw(new Polygon(new int[] {0, 150, 0}, new int[] {75, 0, -75}, 3));
    if (image.getRGB(16, 10) == Color.WHITE.getRGB()) {
      throw new RuntimeException("Miter is not rendered.");
    }
  }
}
