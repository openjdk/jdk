/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/*
 * @test
 * @bug 8291792
 * @key headful
 * @summary Test to check if negative length check is implemented in
 * setCharacterAttributes(). Test should not throw any exception on
 * negative length.
 * @run main DocNegLenCharAttrTest
 */
public class DocNegLenCharAttrTest {
    private static JFrame frame;
    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    test();
                }
            });
        } finally {
            frame.dispose();
        }
        System.out.println("Test Pass!");
    }

    public static void test() {
        DefaultStyledDocument doc;
        frame = new JFrame();
        doc = new DefaultStyledDocument();
        JTextPane text = new JTextPane();
        text.setDocument(doc);
        text.setText("hello world");
        doc.setCharacterAttributes(6, -5,
                createLabelAttribute("world"), true);

        frame.setPreferredSize(new Dimension(100,70));
        frame.add(text);
        frame.setLayout(new BorderLayout());
        frame.add(text,BorderLayout.SOUTH);
        frame.setVisible(true);
        frame.pack();
    }

    private static AttributeSet createLabelAttribute(String text){
        JLabel lbl = new JLabel(text.toUpperCase());
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setComponent(attr,lbl);
        return attr;
    }
}
