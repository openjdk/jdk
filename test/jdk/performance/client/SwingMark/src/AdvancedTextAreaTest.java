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

import java.util.Random;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
  * This test is mean to isolate the speed of the JTextArea
  * It creates a JTextArea and then perform the following
  * behavios :
  * (1) Append text
  * (2) Change Font with random size and type
  * (3) Cut with random selection
  * (4) Copy with random selection
  * (5) Paste with random selection
  *
  */

public class AdvancedTextAreaTest extends AbstractSwingTest {

    JTextArea textArea1;
    final int repeat = 100;

    public JComponent getTestComponent() {
        JPanel panel = new JPanel();
        textArea1 = new JTextArea(10, 30);
        textArea1.setLineWrap(true);
        JScrollPane scroller = new JScrollPane(textArea1);
        panel.add(scroller);
        return panel;
    }

    public String getTestName() {
        return "Adv TextArea";
    }

    public void runTest() {
        testTextArea(textArea1, "Swing is Fast!  ");
    }

    public void testTextArea(JTextArea currentTextArea, String appendThis) {
        TextAppender appender = new TextAppender(currentTextArea, appendThis);
        TextSelector selector = new TextSelector(currentTextArea);
        TextCutter cutter = new TextCutter (currentTextArea);
        TextPaster paster = new TextPaster (currentTextArea);
        CaretSetter caretSetter = new CaretSetter(currentTextArea);
        TextCopier copier = new TextCopier(currentTextArea);
        TextFontSetter fonter = new TextFontSetter(currentTextArea);

        for (int i = 0; i < repeat; i++) {
            try {
                SwingUtilities.invokeLater(appender);
                currentTextArea.repaint();
                rest();
            } catch (Exception e) {System.out.println(e);}
        }


        for (int i = 0; i < repeat; i++) {
            try {
                // Change font
                SwingUtilities.invokeAndWait(fonter);

                // Cut
                selector.setSelection();
                SwingUtilities.invokeAndWait(selector);
                SwingUtilities.invokeAndWait(cutter);

                // Copy
                caretSetter.setCaretPosition();
                SwingUtilities.invokeAndWait(caretSetter);
                SwingUtilities.invokeAndWait(copier);

                // Paste
                caretSetter.setCaretPosition();
                SwingUtilities.invokeAndWait(caretSetter);
                SwingUtilities.invokeAndWait(paster);

        } catch (Exception e) {System.out.println(e);}
        }
    }

    public static void main(String[] args) {
        runStandAloneTest(new AdvancedTextAreaTest());
    }

    static class TextAppender implements Runnable {
        JTextArea area;
        String appendString;


        public TextAppender(JTextArea textArea, String appendThis) {
            area = textArea;
            appendString = appendThis;
        }

        public void run() {
            area.append(appendString);
        }
    }

    static class TextCutter implements Runnable {
        JTextArea area;

        public TextCutter(JTextArea textArea) {
            area = textArea;
        }

        public void run() {
            area.cut();
        }
    }

    static class TextCopier implements Runnable {
        JTextArea area;

        public TextCopier(JTextArea textArea) {
                area = textArea;
        }

        public void run() {
                area.copy();
        }
    }

    static class TextFontSetter implements Runnable {
        JTextArea area;
        String[] fonts;
        Random random;
        int index = 0;

        @SuppressWarnings("deprecation")
        public TextFontSetter(JTextArea textArea) {
            area = textArea;
            random = new Random();
            fonts = Toolkit.getDefaultToolkit().getFontList();
        }

        public void run() {
            area.setFont(new Font( fonts[index],
                         Math.abs(random.nextInt()) % 3,
                         Math.abs(random.nextInt()) % 20));
            area.repaint();
            index ++ ;
            index = index % fonts.length ;
        }
    }

    static class TextPaster implements Runnable {
        JTextArea area;

        public TextPaster(JTextArea textArea) {
            area = textArea;
        }

        public void run() {
            area.paste();
        }
    }

    static class TextSelector implements Runnable {
        JTextArea area;
        int start;
        int end;
        Random random;

        public TextSelector(JTextArea textArea) {
            area = textArea;
            random = new Random();
        }

        public void setSelection() {
            int length = area.getText().length();
            start = Math.abs( random.nextInt()) % length;
            end = start +  50;

            if ( end >= length ) {
                end = length - 1 ;
            }
       }

        public void run() {
            area.setSelectionStart(start);
            area.setSelectionEnd(end);
            area.setSelectionColor(new Color(Math.abs(random.nextInt()) % 256,
                                   Math.abs(random.nextInt()) % 256,
                                   Math.abs(random.nextInt()) % 256));
        }
    }

    static class CaretSetter implements Runnable {
        JTextArea area;
        int position;
        Random random;

        public CaretSetter(JTextArea textArea) {
            area = textArea;
            random = new Random();
        }

        public void setCaretPosition() {
            position =Math.abs( random.nextInt()) % area.getText().length();
        }

        public void run() {
            area.setCaretPosition(position);
        }
    }
}
