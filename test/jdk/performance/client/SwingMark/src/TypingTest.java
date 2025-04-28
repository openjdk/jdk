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

import java.util.Date;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class TypingTest extends AbstractSwingTest {

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
        return "Typing";
    }

    public void runTest() {
        testTyping(textArea1, "Write once, run anywhere!  ");
    }

    public void testTyping(JTextArea currentTextArea, String stuff) {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();

        int n = stuff.length();
        for (int i = 0; i < repeat; i++)
            for (int j = 0; j < n; j++) {
                char c = stuff.charAt(j);
                KeyEvent key = new KeyEvent(currentTextArea,
                                    KeyEvent.KEY_TYPED, new Date().getTime(),
                                    0, KeyEvent.VK_UNDEFINED, c);
                queue.postEvent(key);
                rest();
        }
    }

    public static void main(String[] args) {
        runStandAloneTest(new TypingTest());
    }
}
