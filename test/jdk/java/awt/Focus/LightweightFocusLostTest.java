/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4124119
 * @summary Checks that lightweight components do not lose focus after
        dragging the frame by using the mouse.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual LightweightFocusLostTest
*/

import java.awt.AWTEvent;
import java.awt.AWTEventMulticaster;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Label;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class LightweightFocusLostTest {

     private static final String INSTRUCTIONS = """
        Steps to try to reproduce this problem:
        When this test is run a window will display (Test Focus).
        Click in the text field to give it the focus (a blinking cursor
        will appear) and then move the frame with the mouse. The text field
        (component which uses a lightweight component) should still have focus.
        You should still see the blinking cursor in the text field after the
        frame has been moved. If this is the behavior that you observe, the
        test has passed, Press the Pass button. Otherwise the test has failed,
        Press the Fail button.""";


    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("LightweightFocusLostTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 5)
                .columns(35)
                .testUI(LightweightFocusLostTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame f = new Frame("LightweightFocusLostTest");
        f.setLayout(new BorderLayout());
        String sLabel = "Lightweight component below (text field)";
        Label TFL = new Label(sLabel, Label.LEFT);
        f.add(TFL, BorderLayout.NORTH);
        SimpleTextField canvas = new SimpleTextField(30, 5);
        f.add(canvas, BorderLayout.CENTER);
        f.setSize(new Dimension(300,125));
        f.requestFocus();
        return f;

    }

}

class SimpleTextField extends Component implements Runnable {
    int border;
    int length;
    Font font;
    FontMetrics fontM;
    char[] buffer;
    int bufferIx;

    boolean hasFocus;
    boolean cursorOn;

    SimpleTextField(int len, int bor) {
        super();
        border = bor;
        length = len;
        buffer = new char[len];
        font = getFont();
        if (font == null) {
            font = new Font("Dialog", Font.PLAIN, 20);
        }
        fontM = getFontMetrics(font);

        // Listen for key and mouse events.
        this.addMouseListener(new MouseEventHandler());
        this.addFocusListener(new FocusEventHandler());
        this.addKeyListener(new KeyEventHandler());

        // Set text cursor.
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        // Start the thread that blinks the cursor.
        (new Thread(this)).start();
    }

    public Dimension getMinimumSize() {
        // The minimum height depends on the point size.
        int w = fontM.charWidth('m') * length;
        return new Dimension(w + 2 * border, fontM.getHeight() + 2 * border);
    }
    public Dimension getPreferredSize() {
        return getMinimumSize();
    }
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
    }

    public boolean isFocusTraversable() {
        return true;
    }

    public void paint(Graphics g) {
        int y = (getSize().height-fontM.getHeight())/2;

        // Clear the background using the text background color.
        g.setColor(SystemColor.text);
        g.fillRect(0, 0, getSize().width, getSize().height);

        g.setFont(font);
        g.setColor(SystemColor.textText);
        g.drawChars(buffer, 0, bufferIx, border, y + fontM.getAscent());

        // Draw blinking cursor.
        int x = fontM.charsWidth(buffer, 0, bufferIx) + border;
        int w = fontM.charWidth('c');
        if (hasFocus) {
            g.setColor(getForeground());
            g.fillRect(x, y, w, fontM.getHeight());
            if (cursorOn) {
                if (bufferIx < buffer.length) {
                    g.setColor(SystemColor.text);
                    g.fillRect(x + 2, y + 2, w - 4, fontM.getHeight() - 4);
                }
            }
        }
    }

    // Event handlers
    class MouseEventHandler extends MouseAdapter {
        public void mousePressed(MouseEvent evt) {
            requestFocus();
        }
    }
    class FocusEventHandler extends FocusAdapter {
        public void focusGained(FocusEvent evt) {
            PassFailJFrame.log("Focus gained: " + evt);

            hasFocus = true;
            repaint();
        }
        public void focusLost(FocusEvent evt) {
            PassFailJFrame.log("Focus lost: " + evt);
            hasFocus = false;
            repaint();
        }
    }
    class KeyEventHandler extends KeyAdapter {
        public void keyPressed(KeyEvent evt) {
            switch (evt.getKeyCode()) {
              case KeyEvent.VK_DELETE:
              case KeyEvent.VK_BACK_SPACE:
                if (bufferIx > 0) {
                    bufferIx--;
                    repaint();
                }
                break;
              case KeyEvent.VK_ENTER:
                ActionEvent action =
                    new ActionEvent(SimpleTextField.this,
                                    ActionEvent.ACTION_PERFORMED,
                                    String.valueOf(buffer, 0, bufferIx));
                // Send contents of buffer to listeners
                processEvent(action);
                break;
              default:
                repaint();
            }
        }
        public void keyTyped(KeyEvent evt) {
            if (bufferIx < buffer.length
                    && !evt.isActionKey()
                    && !Character.isISOControl(evt.getKeyChar())) {
                buffer[bufferIx++] = evt.getKeyChar();
            }
        }
    }

    // Support for Action Listener.
    ActionListener actionListener;

    public void addActionListener(ActionListener l) {
        actionListener = AWTEventMulticaster.add(actionListener, l);
    }

    // Override processEvent() to deal with ActionEvent.
    protected void processEvent(AWTEvent evt) {
        if (evt instanceof ActionEvent) {
            processActionEvent((ActionEvent)evt);
        } else {
            super.processEvent(evt);
        }
    }

    // Supply method to process Action event.
    protected void processActionEvent(ActionEvent evt) {
        if (actionListener != null) {
            actionListener.actionPerformed(evt);
        }
    }

    public void run() {
        while (true) {
            try {
                // If component has focus, blink the cursor every 1/2 second.
                Thread.sleep(500);
                cursorOn = !cursorOn;
                if (hasFocus) {
                    repaint();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

