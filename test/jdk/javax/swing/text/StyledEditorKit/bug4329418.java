/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;

/*
 * @test
 * @bug 4329418
 * @key headful
 * @summary Tests if setCharacterAttributes() is maintained
 *          after return in J(Editor/Text)Pane
 */

public class bug4329418 {
    private static JFrame jf;
    private static StyledEditorKit sek;

    private static volatile boolean passed = false;
    private static final int FONT_SIZE = 36;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(bug4329418::createAndShowUI);
            robot.waitForIdle();
            robot.delay(500);

            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.delay(300);

            if (!passed) {
                throw new RuntimeException("Test failed." +
                        " setCharacterAttributes() does not work correctly");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jf != null) {
                    jf.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        jf = new JFrame("setCharacterAttributes Test");
        sek = new StyledEditorKit();
        JEditorPane jep = new JEditorPane();
        jep.setEditorKit(sek);

        MutableAttributeSet attrs = sek.getInputAttributes();
        StyleConstants.setFontSize(attrs, FONT_SIZE);

        jep.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                MutableAttributeSet attrs = sek.getInputAttributes();
                passed = (StyleConstants.getFontSize(attrs) == FONT_SIZE);
            }
        });

        jep.setText("aaa");
        Document doc = jep.getDocument();
        jep.setCaretPosition(doc.getLength());

        jf.getContentPane().add(jep);
        jf.setLocationRelativeTo(null);
        jf.setSize(200, 200);
        jf.setVisible(true);
    }
}
