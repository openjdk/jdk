/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.Arrays;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8295248
 * @summary Verify select items of HTML form are cleared by reset
 */
public class TestResetSelectForm {
    private Robot robot;
    private JEditorPane editor;
    private JFrame frame;

    private int listHeight;
    private Point loc;
    private Color baseColor;

    private final int width = 200;
    private final int height = 200;
    private final int LIMIT = 10;
    private final String[] numbers = {"1","2","3","4","5"};

    private int getListHeight() {
        JList<String> dummy = new JList<>(numbers);
        return dummy.getPreferredSize().height;
    }

    private void setup() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><form action=\"http://localhost\">" +
                  "<select name=select id=\"mySelect\" size=\"" +
                  numbers.length + "\" multiple>");
        Arrays.stream(numbers).forEach(s->{sb.append(
                  "<option>" + s + "</option>");});
        sb.append("</select><br>" +
                  "<input type=reset name=reset value=\"reset\"/>" +
                  "<br>" +
                  "<input type=submit name=submit value=\"submit\"/>" +
                  "</form></body></html>");
        editor = new JEditorPane("text/html", sb.toString());
        frame = new JFrame();
        frame.setSize(width, height);
        frame.setLayout(new BorderLayout());
        frame.add(editor, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static int getMaxColorDiff(Color c1, Color c2) {
        return Math.max(Math.abs(c1.getRed() - c2.getRed()),
                 Math.max(Math.abs(c1.getGreen() - c2.getGreen()),
                          Math.abs(c1.getBlue() - c2.getBlue())));
    }

    private void checkNoListSelection() {
        int len = numbers.length;
        for (int i = 0; i < len; i++) {
            int pos = (int)(listHeight * (i + 0.5) / len);
            Color c = robot.getPixelColor(loc.x + 30, loc.y + pos);
            if (getMaxColorDiff(baseColor, c) > LIMIT) {
                throw new RuntimeException("Unexpected List selection at " +
                    (i + 1) + ", " + baseColor + "," + c);
            }
        }
    }

    private void execute() throws Exception{
        robot = new Robot();
        robot.setAutoDelay(100);

        try {
            SwingUtilities.invokeAndWait(() -> {
                listHeight = getListHeight();});

            // position of 4th item
            int pos4 = (int)(listHeight * 3.5 / numbers.length);

            SwingUtilities.invokeAndWait(() -> setup());
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                loc = editor.getLocationOnScreen();});
            robot.waitForIdle();
            robot.delay(500);
            baseColor = robot.getPixelColor(loc.x + 30, loc.y + pos4);

            //Click 4th item
            robot.mouseMove(loc.x + 30, loc.y + pos4);
            robot.waitForIdle();
            robot.delay(500);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);

            //Click reset button
            robot.mouseMove(loc.x + 30, loc.y + listHeight + 20);
            robot.waitForIdle();
            robot.delay(500);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);
            checkNoListSelection();

            // Repaint
            SwingUtilities.invokeAndWait(() -> {
                editor.revalidate();
                editor.repaint();});
            robot.waitForIdle();
            robot.delay(500);
            checkNoListSelection();
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    public static void main(String[] args) throws Exception {
        TestResetSelectForm test = new TestResetSelectForm();
        test.execute();
    }
}
