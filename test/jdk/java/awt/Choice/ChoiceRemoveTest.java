/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4079027
 * @summary Removing an item dynamically from a Choice object breaks lower items.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ChoiceRemoveTest
 */

import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

public class ChoiceRemoveTest extends Frame {
    Choice selector;
    static final String INSTRUCTIONS = """
            After window 'Choice Remove Test' appears wait for three seconds
            and then click on the choice. In popup there should be no
            'Choice A' variant. Try selecting each variant with mouse
            and verify by the log that the correct variant gets selected.
            If after selecting item in the list the correct item gets selected
            and correct item name appears in the log press Pass otherwise press Fail.
            """;

    public static void main(String[] argv) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .testUI(ChoiceRemoveTest::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    public ChoiceRemoveTest() {
        super("Choice Remove Test");
        Panel p;
        Label prompt;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                super.windowOpened(e);
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignore) {
                    }
                    removeFirst();
                }).start();
            }
        });

        setLayout(new GridLayout());
        p = new Panel();

        prompt = new Label("Select different items including the last one");
        p.add(prompt);

        selector = new Choice();
        selector.add("Choice A");
        selector.add("Choice B");
        selector.add("Choice C");
        selector.add("Choice D");
        selector.add("Choice E");
        selector.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                Object selected = e.getItem();
                PassFailJFrame.log(selected.toString());
            }
        });
        p.add(selector);
        add(p);
        pack();
    }

    public void removeFirst() {
        selector.remove("Choice A");
    }
}
