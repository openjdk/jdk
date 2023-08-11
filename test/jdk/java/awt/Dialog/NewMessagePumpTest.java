/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4119383
  @summary Tests total rewrite of modality blocking model
  @key headful
  @run main/timeout=30 NewMessagePumpTest
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;

public class NewMessagePumpTest {
    public void start() {
        Frame1 frame = new Frame1();
        frame.validate();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        NewMessagePumpTest test = new NewMessagePumpTest();
        EventQueue.invokeAndWait(test::start);
    }
}

class Frame1 extends Frame {
    Frame1() {
        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        MyPanel panel1 = new MyPanel(this);
        this.setLayout(new BorderLayout());
        this.setSize(new Dimension(400, 300));
        this.setLocationRelativeTo(null);
        this.setTitle("Frame Title");
        panel1.setLayout(new BorderLayout());
        this.add(panel1, BorderLayout.CENTER);
    }
}

class Dialog1 extends Dialog {
    BorderLayout borderLayout1 = new BorderLayout();
    Button button1 = new Button();

    Dialog1(Frame f) {
        super(f, true);
        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void jbInit() throws Exception {
        button1.setLabel("close");
        button1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                button1_actionPerformed(e);
            }
        });
        this.setLayout(borderLayout1);
        this.add(button1, BorderLayout.NORTH);
    }

    void button1_actionPerformed(ActionEvent e) {
        dispose();
    }
}

class MyPanel extends Panel {
    Frame frame;

    MyPanel(Frame f) {
        frame = f;
    }

    public void addNotify() {
        super.addNotify();
        System.out.println("AddNotify bringing up modal dialog...");
        final Dialog1 dlg = new Dialog1(frame);
        dlg.pack();
        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            try {
                EventQueue.invokeAndWait(() -> {
                    dlg.setVisible(false);
                    dlg.dispose();
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }).start();
        dlg.setVisible(true);
        frame.setVisible(false);
        frame.dispose();
        System.out.println("Test passed");
    }
}
