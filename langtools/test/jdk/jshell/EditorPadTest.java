/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing built-in editor.
 * @ignore 8139872
 * @modules jdk.jshell/jdk.internal.jshell.tool
 * @build ReplToolTesting EditorTestBase
 * @run testng EditorPadTest
 */

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EditorPadTest extends EditorTestBase {

    private static final int DELAY = 500;

    private static Robot robot;
    private static JFrame frame = null;
    private static JTextArea area = null;
    private static JButton cancel = null;
    private static JButton accept = null;
    private static JButton exit = null;

    @BeforeClass
    public static void setUpEditorPadTest() {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(DELAY);
        } catch (AWTException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterClass
    public static void shutdown() {
        executorShutdown();
    }

    @Override
    public void writeSource(String s) {
        SwingUtilities.invokeLater(() -> area.setText(s));
    }

    @Override
    public String getSource() {
        try {
            String[] s = new String[1];
            SwingUtilities.invokeAndWait(() -> s[0] = area.getText());
            return s[0];
        } catch (InvocationTargetException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void accept() {
        clickOn(accept);
    }

    @Override
    public void exit() {
        clickOn(exit);
    }

    @Override
    public void cancel() {
        clickOn(cancel);
    }

    @Override
    public void shutdownEditor() {
        SwingUtilities.invokeLater(this::clearElements);
        waitForIdle();
    }

    @Test
    public void testShuttingDown() {
        testEditor(
                (a) -> assertEditOutput(a, "/e", "", this::shutdownEditor)
        );
    }

    private void waitForIdle() {
        robot.waitForIdle();
        robot.delay(DELAY);
    }

    private Future<?> task;
    @Override
    public void assertEdit(boolean after, String cmd,
                           Consumer<String> checkInput, Consumer<String> checkOutput, Action action) {
        if (!after) {
            setCommandInput(cmd + "\n");
            task = getExecutor().submit(() -> {
                try {
                    waitForIdle();
                    SwingUtilities.invokeLater(this::seekElements);
                    waitForIdle();
                    checkInput.accept(getSource());
                    action.accept();
                } catch (Throwable e) {
                    shutdownEditor();
                    if (e instanceof AssertionError) {
                        throw (AssertionError) e;
                    }
                    throw new RuntimeException(e);
                }
            });
        } else {
            try {
                task.get();
                waitForIdle();
                checkOutput.accept(getCommandOutput());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AssertionError) {
                    throw (AssertionError) e.getCause();
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                shutdownEditor();
            }
        }
    }

    private void seekElements() {
        for (Frame f : Frame.getFrames()) {
            if (f.getTitle().contains("Edit Pad")) {
                frame = (JFrame) f;
                // workaround
                frame.setLocation(0, 0);
                Container root = frame.getContentPane();
                for (Component c : root.getComponents()) {
                    if (c instanceof JScrollPane) {
                        JScrollPane scrollPane = (JScrollPane) c;
                        for (Component comp : scrollPane.getComponents()) {
                            if (comp instanceof JViewport) {
                                JViewport view = (JViewport) comp;
                                area = (JTextArea) view.getComponent(0);
                            }
                        }
                    }
                    if (c instanceof JPanel) {
                        JPanel p = (JPanel) c;
                        for (Component comp : p.getComponents()) {
                            if (comp instanceof JButton) {
                                JButton b = (JButton) comp;
                                switch (b.getText()) {
                                    case "Cancel":
                                        cancel = b;
                                        break;
                                    case "Exit":
                                        exit = b;
                                        break;
                                    case "Accept":
                                        accept = b;
                                        break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearElements() {
        if (frame != null) {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            frame = null;
        }
        area = null;
        accept = null;
        cancel = null;
        exit = null;
    }

    private void clickOn(JButton button) {
        waitForIdle();
        Point p = button.getLocationOnScreen();
        Dimension d = button.getSize();
        robot.mouseMove(p.x + d.width / 2, p.y + d.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
}
