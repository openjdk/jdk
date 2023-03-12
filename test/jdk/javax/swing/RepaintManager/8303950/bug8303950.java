/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.PanelUI;

/* @test
   @bug 8303950
 * @summary translucent windows flicker on repaint
   @author Jeremy Wood
*/
public class bug8303950 {

    // pick two random and distinct colors:
    static final Color FLICKER_OF_BACKGROUND_COLOR = new Color(91, 152, 214);
    static final Color CORRECT_FOREGROUND_COLOR = new Color(119, 33, 236);

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                FlickerTestWindow flickeringWindow = new FlickerTestWindow();
                flickeringWindow.pack();
                flickeringWindow.setLocationRelativeTo(null);

                // put a solid sheet under our test window. If we see this window's background color
                // we know our test is failing because there's a flicker.

                JDialog backgroundWindow = new JDialog();
                backgroundWindow.getContentPane().setBackground(FLICKER_OF_BACKGROUND_COLOR);
                backgroundWindow.setBounds(flickeringWindow.getBounds());
                backgroundWindow.setVisible(true);

                // now show our test window on top:
                flickeringWindow.setVisible(true);
                flickeringWindow.toFront();

                Thread watcherThread = new Thread() {
                    @Override
                    public void run() {
                        waitUntilReady();

                        // now grab the center pixel for 3 seconds and see if it ever flickers to reveal
                        // the background window:
                        Robot robot;
                        try {
                            robot = new Robot();

                            int flickerSamples = 0;
                            int noFlickerSamples = 0;
                            int notSetupSamples = 0;

                            Point loc = new Point(flickeringWindow.label.getLocationOnScreen());
                            loc.x += 150;
                            loc.y += 150;
                            long t = System.currentTimeMillis();
                            while (true) {
                                long elapsed = System.currentTimeMillis() - t;
                                if (elapsed > 3_000) {
                                    System.out.println("flicker samples: " + flickerSamples);
                                    System.out.println("successful samples: " + noFlickerSamples);
                                    if (notSetupSamples > 0) {
                                        System.out.println("setup failed samples: " + notSetupSamples);
                                        System.out.println("This means the windows weren't configured correctly, or the Robot was unable to capture pixels.");
                                    }
                                    if (notSetupSamples + flickerSamples > 0) {
                                        System.err.println("This test failed.");
                                        System.exit(1);
                                    } else {
                                        System.out.println("This test passed.");
                                        System.exit(0);
                                    }
                                }
                                Color c = robot.getPixelColor(loc.x, loc.y);
                                if (matches(CORRECT_FOREGROUND_COLOR, c)) {
                                    noFlickerSamples++;
                                } else if (matches(FLICKER_OF_BACKGROUND_COLOR, c)) {
                                    flickerSamples++;
                                } else {
                                    notSetupSamples++;
                                }
                            }
                        } catch (AWTException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }

                    private void waitUntilReady() {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                        while (true) {
                            if (flickeringWindow.isShowing() && backgroundWindow.isShowing()) {
                                break;
                            }
                            Thread.yield();
                        }
                    }

                    /**
                     * Return true if two colors are very similar.
                     * <p>
                     * This exists because I don't get an exact match on the static
                     * colors in this test, but the RGB values we get back are recognizable
                     * close to what we're aiming for.
                     * </p>
                     */
                    private boolean matches(Color c1, Color c2) {
                        return Math.abs(c1.getRed() - c2.getRed()) +
                                Math.abs(c1.getGreen() - c2.getGreen()) +
                                Math.abs(c1.getBlue() - c2.getBlue()) < 60;
                    }
                };
                watcherThread.start();
            }
        });
        Thread.currentThread().sleep(5000);
    }

    static class FlickerTestWindow extends JWindow {
        JTextPane instructions = new JTextPane();
        JLabel label = new JLabel();

        public FlickerTestWindow() {
            instructions.setText("Instructions:\nCheck if the center of this window (which is constantly repainting) ever flickers.");
            instructions.setBorder(new EmptyBorder(10, 10, 10, 10));
            instructions.setOpaque(false);
            instructions.setEditable(false);

            setBackground(new Color(0,0,0,0));

            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setBorder(new EmptyBorder(10,10,10,10));
            p.setUI(new PanelUI() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(220, 180, 0, 200));
                    g2.fill(new RoundRectangle2D.Double(0,0,c.getWidth(),c.getHeight(),20,20));
                    c.repaint();
                }
            });
            p.setLayout(new BorderLayout());
            p.add(instructions, BorderLayout.NORTH);
            p.add(label, BorderLayout.CENTER);

            Icon icon = new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    int startAngle = (int)( (System.currentTimeMillis() % 1000) * 360 / 1000 );

                    g.setColor(CORRECT_FOREGROUND_COLOR);
                    g.fillRect(x, y, getIconWidth(), getIconHeight());

                    g.setColor(new Color(0,0,0,100));
                    ((Graphics2D)g).setStroke(new BasicStroke(8));
                    g.drawArc(10, 10, 280, 280, startAngle, 200);

                    c.repaint();
                }

                @Override
                public int getIconWidth() {
                    return 300;
                }

                @Override
                public int getIconHeight() {
                    return 300;
                }
            };
            label.setIcon(icon);

            getContentPane().add(p);
        }
    }
}
