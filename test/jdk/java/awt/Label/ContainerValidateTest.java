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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/*
 * @test
 * @key headful
 * @bug 4247913
 * @summary Tests that Label repaints after call Container.validate()
 * @run main ContainerValidateTest
 */

public class ContainerValidateTest extends Frame implements MouseListener {
    private static Robot robot;
    private static Panel currentPanel;
    private static Button currentBtn;
    private static Panel updatedPanel;
    private static Label updatedLabel;
    private static TextField updatedTxtField;
    private static Button updatedBtn;

    private static volatile Rectangle btnBounds;

    Panel pnl1 = new Panel();
    Panel pnl2 = new Panel();
    Label lbl1 = new Label("Label 1");
    Label lbl2 = new Label("Label 2");
    TextField txt1 = new TextField("field1", 20);
    TextField txt2 = new TextField("field2", 20);
    Button btn1 = new Button("Swap 1");
    Button btn2 = new Button("Swap 2");

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        ContainerValidateTest containerValidate = new ContainerValidateTest();
        EventQueue.invokeAndWait(containerValidate::createAndShowUI);
        robot.waitForIdle();
        robot.delay(1000);

        containerValidate.testUI();
    }

    private void createAndShowUI() {
        this.setTitle("ContainerValidateTest Test");
        pnl1.add(lbl1);
        pnl1.add(txt1);
        pnl1.add(btn1);

        pnl2.add(lbl2);
        pnl2.add(txt2);
        pnl2.add(btn2);

        btn1.addMouseListener(this);
        btn2.addMouseListener(this);

        this.add(pnl1, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void testUI() throws Exception {
        EventQueue.invokeAndWait(() -> btnBounds
                = new Rectangle(btn1.getLocationOnScreen().x,
                                btn1.getLocationOnScreen().y,
                                btn1.getWidth(),
                                btn1.getHeight()));
        for (int i= 1; i < 4 ; i++) {
            EventQueue.invokeAndWait(() -> {
                currentPanel = (Panel) this.getComponent(0);
                currentBtn = (Button) currentPanel.getComponent(2);
            });

            robot.mouseMove(btnBounds.x + (int) btnBounds.getWidth() / 2,
                            btnBounds.y + (int) btnBounds.getHeight() / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            //large delay set for completion of UI validate()
            robot.delay(500);

            EventQueue.invokeAndWait(() -> {
                updatedPanel = (Panel) this.getComponent(0);
                updatedLabel = (Label) updatedPanel.getComponent(0);
                updatedTxtField = (TextField) updatedPanel.getComponent(1);
                updatedBtn = (Button) updatedPanel.getComponent(2);
            });
            testPanelComponents(currentBtn.getLabel());
        }
    }

    private void testPanelComponents(String btnLabel) {
        if (btnLabel.equals("Swap 1")) {
            if (!(updatedLabel.getText().equals(lbl2.getText())
                  && updatedTxtField.getText().equals(txt2.getText())
                  && updatedBtn.getLabel().equals(btn2.getLabel()))) {
                throw new RuntimeException("Test Failed!! Labels not repainted"
                                           + " after Container.validate()");
            }
        } else {
            if (!(updatedLabel.getText().equals(lbl1.getText())
                  && updatedTxtField.getText().equals(txt1.getText())
                  && updatedBtn.getLabel().equals(btn1.getLabel()))) {
                throw new RuntimeException("Test Failed!! Labels not repainted"
                                           + " after Container.validate()");
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent evt) {
        if (evt.getComponent() instanceof Button btn) {
            if (btn.equals(btn1)) {
                remove(pnl1);
                add(pnl2, BorderLayout.CENTER);
            } else {
                remove(pnl2);
                add(pnl1, BorderLayout.CENTER);
            }
            invalidate();
            validate();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {}
}
