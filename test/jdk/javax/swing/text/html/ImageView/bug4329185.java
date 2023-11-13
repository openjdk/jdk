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

/*
 * @test
 * @bug 4329185
 * @summary  Tests if vertical image alignment is working
 * @key headful
 * @run main bug4329185
 */

import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

public class bug4329185 {

    private static final View[] views = new View[3];
    private static JFrame f;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                 bug4329185 test = new bug4329185();
                 test.start();
            });
            robot.waitForIdle();
            robot.delay(1000);
            boolean passed = ((views[0].getAlignment(View.Y_AXIS) == 0.0)
                             && (views[1].getAlignment(View.Y_AXIS) == 0.5)
                             && (views[2].getAlignment(View.Y_AXIS) == 1.0));
            if (!passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    public void start() {
        String text = "aaa<IMG align=top><IMG align=middle><IMG align=bottom>";
        f = new JFrame("bug4329185");
        JEditorPane jep = new JEditorPane();
        jep.setEditorKit(new MyHTMLEditorKit());
        jep.setEditable(false);

        jep.setText(text);

        f.getContentPane().add(jep);
        f.setSize(500, 500);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }


    static class MyHTMLEditorKit extends HTMLEditorKit {

        private final ViewFactory defaultFactory = new MyHTMLFactory();

        @Override
        public ViewFactory getViewFactory() {
            return defaultFactory;
        }

        static class MyHTMLFactory extends HTMLEditorKit.HTMLFactory {
            private int i = 0;

            @Override
            public View create(Element elem) {
                Object o = elem.getAttributes()
                               .getAttribute(StyleConstants.NameAttribute);
                if (o instanceof HTML.Tag kind) {
                    if (kind == HTML.Tag.IMG) {
                        View v = super.create(elem);
                        views[i++] = v;
                        return v;
                    }
                }
                return super.create(elem);
            }
        }
    }

}
