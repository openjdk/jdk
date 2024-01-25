/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;

/**
  * This test is mean to isolate the speed of the JTextArea
  * It creates a JTextArea and then continuously appends text
  * to that string.
  *
  */

public class TextPaneTest extends AbstractSwingTest {

    JTextPane textArea1;
    final int repeat = 250;

    public JComponent getTestComponent() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(200,200));
        panel.setLayout(new BorderLayout());
        textArea1 = new CountTextArea(10, 30);
        JScrollPane scroller = new JScrollPane(textArea1);
        panel.add(scroller, BorderLayout.CENTER);
        return panel;
    }

    public String getTestName() {
        return "TextPane";
    }

    public void runTest() {
        testTextArea(textArea1, "Swing is Fast!  ");
    }

    public void testTextArea(JTextPane currentTextArea, String appendThis) {
        TextAppender appender = new TextAppender(currentTextArea, appendThis);
        for (int i = 0; i < repeat; i++) {
            try {
                SwingUtilities.invokeLater(appender);
                rest();
            } catch (Exception e) {System.out.println(e);}
        }
    }

    public static void main(String[] args) {
        runStandAloneTest(new TextPaneTest());
    }

    class CountTextArea extends JTextPane {
        public CountTextArea(int h, int w) {
            super();
        }

        public void paint(Graphics g) {
            super.paint(g);
            paintCount++;
        }
    }

    static class TextAppender implements Runnable {
        JTextPane area;
        String appendString;

        public TextAppender(JTextPane textArea, String appendThis) {
            area = textArea;
            appendString = appendThis;
        }

        public void run() {
            try {
                Document doc = area.getDocument();
                doc.insertString(doc.getLength(), appendString, null);
            } catch (Exception e) {
            }
        }
    }
}
