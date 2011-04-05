/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package transparentruler;


import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D.Float;
import java.lang.reflect.InvocationTargetException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;


/**
 * This sample demonstrates shaped and translucent window feature.
 * @author Alexander Kouznetsov
 */
@SuppressWarnings("serial")
public class Ruler extends JFrame {

    private static final Color BACKGROUND = Color.RED;
    private static final Color FOREGROUND = Color.WHITE;
    private static final int OPACITY = 180;
    private static final int W = 70;
    private static final int F_HEIGHT = 400;
    private static final int F_WIDTH = (int) (F_HEIGHT * 1.618 + 0.5);

    private static void checkTranslucencyMode(WindowTranslucency arg) {
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        if (!gd.isWindowTranslucencySupported(arg)) {
            System.err.println("'" + arg
                    + "' translucency mode isn't supported.");
            System.exit(-1);
        }
    }
    private final ComponentAdapter componentListener = new ComponentAdapter() {

        /**
         * Applies the shape to window. It is recommended to apply shape in
         * componentResized() method
         */
        @Override
        public void componentResized(ComponentEvent e) {
            int h = getHeight();
            int w = getWidth();
            float a = (float) Math.hypot(h, w);
            Float path = new java.awt.geom.Path2D.Float();
            path.moveTo(0, 0);
            path.lineTo(w, 0);
            path.lineTo(0, h);
            path.closePath();
            path.moveTo(W, W);
            path.lineTo(W, h - W * (a + h) / w);
            path.lineTo(w - W * (a + w) / h, W);
            path.closePath();
            setShape(path);
        }
    };
    private final Action exitAction = new AbstractAction("Exit") {

        {
            putValue(Action.MNEMONIC_KEY, KeyEvent.VK_X);
        }

        public void actionPerformed(ActionEvent e) {
            System.exit(0);
        }
    };
    private final JPopupMenu jPopupMenu = new JPopupMenu();

    {
        jPopupMenu.add(new JMenuItem(exitAction));
    }
    /**
     * Implements mouse-related behavior: window dragging and popup menu
     * invocation
     */
    private final MouseAdapter mouseListener = new MouseAdapter() {

        int x, y;

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                x = e.getX();
                y = e.getY();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                setLocation(e.getXOnScreen() - x, e.getYOnScreen() - y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                jPopupMenu.show(getContentPane(), e.getX(), e.getY());
            }
        }
    };
    /**
     * Implements keyboard navigation. Arrows move by 5 pixels, Ctrl + arrows
     * move by 50 pixels, Alt + arrows move by 1 pixel.
     * Esc exits the application.
     */
    private final KeyAdapter keyboardListener = new KeyAdapter() {

        @Override
        public void keyPressed(KeyEvent e) {
            int step = e.isControlDown() ? 50 : e.isAltDown() ? 1 : 5;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    setLocation(getX() - step, getY());
                    break;
                case KeyEvent.VK_RIGHT:
                    setLocation(getX() + step, getY());
                    break;
                case KeyEvent.VK_UP:
                    setLocation(getX(), getY() - step);
                    break;
                case KeyEvent.VK_DOWN:
                    setLocation(getX(), getY() + step);
                    break;
                case KeyEvent.VK_ESCAPE:
                    exitAction.actionPerformed(null);
            }
        }
    };

    public Ruler() {
        setUndecorated(true);

        // Enables perpixel translucency
        setBackground(new Color(BACKGROUND.getRed(), BACKGROUND.getGreen(),
                BACKGROUND.getBlue(), OPACITY));

        addMouseListener(mouseListener);
        addMouseMotionListener(mouseListener);
        addComponentListener(componentListener);
        addKeyListener(keyboardListener);
        setContentPane(new JPanel() {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics gg = g.create();
                int w = getWidth();
                int h = getHeight();
                int hh = gg.getFontMetrics().getAscent();
                gg.setColor(FOREGROUND);
                for (int x = 0; x < w * (h - 8) / h - 5; x += 5) {
                    boolean hi = x % 50 == 0;
                    gg.drawLine(x + 5, 0, x + 5,
                            hi ? 20 : (x % 25 == 0 ? 13 : 8));
                    if (hi) {
                        String number = Integer.toString(x);
                        int ww = gg.getFontMetrics().stringWidth(number);
                        gg.drawString(number, x + 5 - ww / 2, 20 + hh);
                    }
                }
                gg.dispose();
            }
        });
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(F_WIDTH, F_HEIGHT);
        setLocationByPlatform(true);
    }

    /**
     * @param args the command line arguments are ignored
     */
    public static void main(String[] args) throws InterruptedException, InvocationTargetException {

        SwingUtilities.invokeAndWait(new Runnable() {

            public void run() {
                checkTranslucencyMode(WindowTranslucency.PERPIXEL_TRANSLUCENT);
                checkTranslucencyMode(WindowTranslucency.PERPIXEL_TRANSPARENT);

                Ruler ruler = new Ruler();
                ruler.setVisible(true);
            }
        });
    }
}
