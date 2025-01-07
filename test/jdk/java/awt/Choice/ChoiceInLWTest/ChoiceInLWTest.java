/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4130788
 * @summary Choice components move unexpectedly when in lightweight containers
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ChoiceInLWTest
 */

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

public class ChoiceInLWTest extends Frame implements Runnable {
    private final Choice choices;
    static final String INSTRUCTIONS = """
            After test starts wait for two seconds and open a choice.
            If choice's popup obscures the label above it press Fail.
            Otherwise press Pass.
            """;

    public ChoiceInLWTest() {
        setLayout(new BorderLayout());
        Container lwCont = new Container();
        lwCont.setLayout(new FlowLayout());
        choices = new Choice();
        choices.add("This is just a token item to get a nice width.");
        lwCont.add(choices);
        add("Center", lwCont);
        Label label = new Label("You should see an unobscured Choice below.");
        label.setAlignment(Label.CENTER);
        add("North", label);
        addChoiceItem();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                super.windowOpened(e);
                new Thread(ChoiceInLWTest.this).start();
            }
        });
        pack();
    }

    private void addChoiceItem() {
        choices.add("Adding an item used to move the Choice!");
    }

    public void run() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {
        }
        addChoiceItem();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Choice in LW Container Test")
                .testUI(ChoiceInLWTest::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .build()
                .awaitAndCheck();

    }
}
