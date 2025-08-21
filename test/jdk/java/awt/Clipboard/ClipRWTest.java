/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4177171 4180145 4180148
 * @summary Can't copy to clipboard
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ClipRWTest
 */

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class ClipRWTest {
    private static final String INSTRUCTIONS = """
            1. Type some text in the text field and press Copy Text.
            2. Switch to a _native_ application (e.g. Notepad) and paste the text in
            3. Verify the text that is pasted matches what you typed in the Java window
            4. In the native app, type some new text and copy it
            5. Switch back to the test frame and press Paste Text
            6. Verify the text that is pasted matches what you typed in the native app
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ClipRWTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(ClipFrame::new)
                .build()
                .awaitAndCheck();
    }

    private static class ClipFrame extends Frame {
        TextField   field =new TextField(50);
        Button      copyText = new Button("Copy Text");
        Button      pasteText = new Button("Paste Text");
        Clipboard   clipboard;

        public ClipFrame() {
            super("ClipRWTest 4177171");
            setLayout(new FlowLayout());

            clipboard = getToolkit().getSystemClipboard();

            add(field);
            add(copyText);
            add(pasteText);

            copyText.addActionListener(
                    ev -> {
                        String text = field.getText();
                        try {
                            clipboard.setContents(new StringSelection(text), null);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
            );

            pasteText.addActionListener(
                    ev -> {
                        String text = "";
                        try {
                            text = (String) clipboard.getContents(null)
                                    .getTransferData(DataFlavor.stringFlavor);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        field.setText(text);
                    }
            );

            pack();
        }
    }
}
