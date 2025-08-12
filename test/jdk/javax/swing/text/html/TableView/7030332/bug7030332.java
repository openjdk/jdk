/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;

import java.net.URL;

/* @test
 * @bug 7030332
 * @summary Default borders in tables looks incorrect
 * when rendered using JEditorPane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual/othervm -Dsun.java2d.uiScale=1 bug7030332
 */

public class bug7030332 {
    public static void main(String[] args) throws Exception {
        String testInstructions = """
                Compare Golden Images with rendered JEditorPane.
                They should look similar in each line.
                Pay attention to:
                1. Border width around tables
                2. Border width around cells
                Note: The test was written before there was hidpi.
                Hence we are considering the border width being
                "similar enough" with 1.0 scaling.
                                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(testInstructions)
                .rows(9)
                .columns(35)
                .splitUI(bug7030332::createContentPane)
                .build()
                .awaitAndCheck();
    }

    public static final String[] HTML_SAMPLES = new String[]{
            "<table border><tr><th>Column1</th><th>Column2</th></tr></table>",
            "<table border=\"\"><tr><th>Column1</th><th>Column2</th></tr></table>",
            "<table border=\"1\"><tr><th>Column1</th><th>Column2</th></tr></table>",
            "<table border=\"2\"><tr><th>Column1</th><th>Column2</th></tr></table>",
    };

    private static JComponent createContentPane() {
        JPanel result = new JPanel(new GridLayout(HTML_SAMPLES.length + 1, 3, 10, 10));

        result.add(new JLabel("Html code"));
        result.add(new JLabel("Golden image"));
        result.add(new JLabel("JEditorPane"));

        for (int i = 0; i < HTML_SAMPLES.length; i++) {
            String htmlSample = HTML_SAMPLES[i];
            JTextArea textArea = new JTextArea(htmlSample);
            textArea.setLineWrap(true);
            result.add(textArea);

            String imageName = "sample" + i + ".png";
            URL resource = bug7030332.class.getResource(imageName);
            result.add(resource == null ? new JLabel(imageName + " not found") :
                    new JLabel(new ImageIcon(resource), SwingConstants.LEFT));
            result.add(new JEditorPane("text/html", htmlSample));
        }
        return result;
    }
}
