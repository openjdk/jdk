/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/*
 * @test
 * @bug 4472852
 * @summary Tests DefaultStyledDocument.split(int, int)
 */

public class bug4472852 {

    public static void main(String[] args) throws Exception {
        // create a Document and insert some text there
        StyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "this", null);

        // add style to the last word
        Element root = doc.getDefaultRootElement();
        int end = root.getEndOffset();
        MutableAttributeSet as = new SimpleAttributeSet();
        StyleConstants.setBackground(as, Color.BLUE);
        doc.setCharacterAttributes(end-5, 5, as, true);

        // inspect Elements of the only Paragraph --
        // there should be no empty Elements
        Element para = root.getElement(0);
        for (int i = 0; i < para.getElementCount(); i++) {
            Element el = para.getElement(i);
            if (el.getStartOffset() == el.getEndOffset()) {
                throw new RuntimeException("Failed: empty Element found");
            }
        }
    }
}
