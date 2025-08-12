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
  @test
  @bug 5010944 6248072
  @summary List's rows overlap one another
  @library /java/awt/regtesthelpers
  @run main/manual SetFontTest
*/

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SetFontTest {

    static final String INSTRUCTIONS = """
        1) Click on the 'Enlarge font' button to enlarge font of the list.
        2) If you see that the rows of the list overlap one another
        then the test failed. Otherwise, go to step 3.
        3) Click on the 'Change mode' button to set multiple-selection mode.
        4) If you see that the rows of the list overlap one another
        then the test failed. Otherwise, the test passed.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("AWT List Font Test")
                       .instructions(INSTRUCTIONS)
                       .rows(10)
                       .columns(40)
                       .testUI(SetFontTest::createFontTest)
                       .build()
                       .awaitAndCheck();
    }

    static Frame createFontTest() {

        Frame frame = new Frame("List Font Test");
        List list = new List(8, false);
        Button button1 = new Button("Enlarge font");
        Button button2 = new Button("Change mode");

        list.add("111");
        list.add("222");
        list.add("333");
        list.add("444");

        button1.addActionListener(
            new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    list.setFont(new Font("SansSerif", Font.PLAIN, 30));
                    list.repaint();
                }
            });

        button2.addActionListener(
            new ActionListener(){
                public void actionPerformed(ActionEvent ae){
                    list.setMultipleMode(true);
                }
            });

        frame.setLayout(new FlowLayout());
        frame.add(list);
        frame.add(button1);
        frame.add(button2);
        frame.setSize(200, 250);
        return frame;
    }
}
