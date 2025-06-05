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
 * @bug 4041703 4096228 4032657 4066152 4149866 4025223
 * @summary Ensures that an Panel/Canvas without heavyweight children
           receives focus on mouse click
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CanvasPanelFocusOnClickTest
 */

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class CanvasPanelFocusOnClickTest {

    private static final String INSTRUCTIONS = """

         Click on the red Canvas. Verify that it has focus by key pressing.
         Click on the yellow Panel.  Verify that it has focus by key pressing.
         Click on the blue heavyweight Panel (NOT ON THE BUTTON!).
           Verify that it doesn't have focus by key pressing.
         If two empty containers are able to the get focus by a mouse click
         and the container with heavyweight children are unable to get
         the focus by a mouse click which can be verified through messages in message dialog
         the test passes.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("CanvasPanelFocusOnClickTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(CanvasPanelFocusOnClickTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Canvas canvas = new Canvas();;
        Panel  emptyPanel = new Panel();
        Panel  panel = new Panel();
        Button buttonInPanel = new Button("BUTTON ON PANEL");

        Frame frame = new Frame("CanvasPanelFocusOnClickTest Frame");
        frame.setLayout(new GridLayout(3, 1));
        canvas.setBackground(Color.red);
        canvas.setName("RED CANVAS");
        canvas.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                println(e.toString());
            }
            public void focusLost(FocusEvent e) {
                println(e.toString());
            }
        });
        canvas.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                printKey(e);
            }

            public void keyTyped(KeyEvent e) {
                printKey(e);
            }

            public void keyReleased(KeyEvent e) {
                printKey(e);
            }
        });
        frame.add(canvas);

        emptyPanel.setBackground(Color.yellow);
        emptyPanel.setName("YELLOW PANEL");
        emptyPanel.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                println(e.toString());
            }
            public void focusLost(FocusEvent e) {
                println(e.toString());
            }
        });
        emptyPanel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                printKey(e);
            }

            public void keyTyped(KeyEvent e) {
                printKey(e);
            }

            public void keyReleased(KeyEvent e) {
                printKey(e);
            }
        });
        frame.add(emptyPanel);

        panel.setBackground(Color.blue);
        panel.setName("BLUE PANEL");
        buttonInPanel.setName("BUTTON ON PANEL");
        buttonInPanel.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                println(e.toString());
            }
            public void focusLost(FocusEvent e) {
                println(e.toString());
            }
        });
        buttonInPanel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                printKey(e);
            }

            public void keyTyped(KeyEvent e) {
                printKey(e);
            }

            public void keyReleased(KeyEvent e) {
                printKey(e);
            }
        });
        panel.add(buttonInPanel);
        panel.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                println(e.toString());
            }
            public void focusLost(FocusEvent e) {
                println(e.toString());
            }
        });
        panel.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                printKey(e);
            }

            public void keyTyped(KeyEvent e) {
                printKey(e);
            }

            public void keyReleased(KeyEvent e) {
                printKey(e);
            }
        });
        frame.add(panel);

        frame.setSize(200, 200);

        return frame;

    }

    static void printKey(KeyEvent e) {
        String typeStr;
        switch(e.getID()) {
          case KeyEvent.KEY_PRESSED:
              typeStr = "KEY_PRESSED";
              break;
          case KeyEvent.KEY_RELEASED:
              typeStr = "KEY_RELEASED";
              break;
          case KeyEvent.KEY_TYPED:
              typeStr = "KEY_TYPED";
              break;
          default:
              typeStr = "unknown type";
        }

        Object source = e.getSource();
        if (source instanceof Component) {
            typeStr += " on " + ((Component)source).getName();
        } else {
            typeStr += " on " + source;
        }

        println(typeStr);
    }

    static void println(String messageIn) {
        PassFailJFrame.log(messageIn);
    }
}
