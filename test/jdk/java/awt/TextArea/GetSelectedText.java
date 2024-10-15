/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

/*
 * Licensed Materials - Property of IBM
 *
 * GetSelectedText.java
 *
 * (C) Copyright IBM Corporation 1998  All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or disclosure
 * restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/*
 * @test
 * @bug 4116436
 * @key headful
 * @summary StringOutOfBoundsException with getSelectedText()
 * @run main GetSelectedText
 */

public class GetSelectedText extends Frame implements KeyListener {
    TextArea ta;
    static volatile GetSelectedText test;
    static volatile Point p;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            test = new GetSelectedText();
        });

        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);

        EventQueue.invokeAndWait(() -> {
            p = test.ta.getLocationOnScreen();
        });

        robot.mouseMove(p.x + 10, p.y + 10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);

        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
    }

    public GetSelectedText() {
        setLayout(new BorderLayout());
        ta = new TextArea("Select all of this text");
        add("Center", ta);
        ta.addKeyListener(this);
        pack();
        setVisible(true);
    }

    public void keyPressed(KeyEvent ke) {
        if (ke.getKeyCode() != KeyEvent.VK_BACK_SPACE) {
                System.out.println("getSelectedText().length() = "
                        + (ta.getSelectedText()).length());
        }
    }

    public void keyTyped(KeyEvent ke) {
    }

    public void keyReleased(KeyEvent ke) {
    }
}
