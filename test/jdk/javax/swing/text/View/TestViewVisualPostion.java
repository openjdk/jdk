/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8391985
 * @summary View.getNextVisualPositionFrom can throw NullPointerException
 *  @run main TestViewVisualPostion
 */

import java.awt.Rectangle;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.Shape;
import javax.swing.text.View;

public class TestViewVisualPostion {
    public static void main(String[] args) throws Exception {
        PlainDocument document = new PlainDocument();
        document.insertString(0, "x", null);
        View view = new View(document.getDefaultRootElement()) {
            @Override
            public float getPreferredSpan(int axis) {
                return 0;
            }

            @Override
            public void paint(Graphics g, Shape allocation) {

            }

            @Override
            public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
                return null;
            }

            @Override
            public int viewToModel(float x, float y, Shape a, Position.Bias[] biasReturn) {
                return 0;
            }
        };
        view.getNextVisualPositionFrom(
                0,
                Position.Bias.Forward,
                new Rectangle(0, 0, 10, 10),
                SwingConstants.NORTH,
                new Position.Bias[1]);
    }
}
