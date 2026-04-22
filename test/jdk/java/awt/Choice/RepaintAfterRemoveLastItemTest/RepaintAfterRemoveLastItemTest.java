/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6292186
 * @summary Choice is not refreshed properly when the last item gets removed
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RepaintAfterRemoveLastItemTest
 */

import java.awt.Button;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;

public class RepaintAfterRemoveLastItemTest extends Frame implements ActionListener {
    Choice ch = new Choice();

    static final String INSTRUCTIONS = """
            Press on the 'remove' button after that if the choice does not display
            'only item' press Pass. If 'only item' is still displayed press Fail.
            """;

    public RepaintAfterRemoveLastItemTest() {
        ch.add("only item");
        add(ch);

        Button b = new Button("remove");
        add(b);
        b.addActionListener(this);
        setLayout(new FlowLayout());
        setSize(200, 200);
        validate();
    }

    public void actionPerformed(ActionEvent ae) {
        if (ch.getItemCount() != 0) {
            ch.remove(0);
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Repaint After Remove Test")
                .testUI(RepaintAfterRemoveLastItemTest::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
