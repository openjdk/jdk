/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6308332
  @summary FileDialog.setDirectory() throws exception on Linux & Solaris
  @key headful
  @run main ExceptionAfterSetDirectory
*/

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

public class ExceptionAfterSetDirectory {
    FileDialog fd = null;
    Frame frame;

    public void start() throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("ExceptionAfterSetDirectory");
            frame.setLayout(new FlowLayout());
            frame.setBounds(100, 100, 100, 100);
            frame.setVisible(true);
            fd = new FileDialog(frame, "file dialog", FileDialog.LOAD);
        });

        try {
            test();
        } catch (Exception e) {
            throw new RuntimeException("Test failed.", e);
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
            if (fd != null) {
                EventQueue.invokeAndWait(fd::dispose);;
            }
        }
    }

    private void test() throws InterruptedException, InvocationTargetException {
        final Robot r;

        try {
            r = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Can not initialize Robot.", e);
        }

        r.setAutoDelay(200);
        r.delay(500);

        EventQueue.invokeLater(() -> {
            fd.setVisible(true);
        });
        r.delay(2000);
        r.waitForIdle();

        if (System.getProperty("os.name").contains("OS X")) {
            // Workaround for JDK-7186009 - try to close file dialog pressing escape
            r.keyPress(KeyEvent.VK_ESCAPE);
            r.keyRelease(KeyEvent.VK_ESCAPE);
            r.delay(2000);
            r.waitForIdle();
        }

        if (fd.isVisible()) {
            EventQueue.invokeAndWait(() -> {
                fd.setVisible(false);
            });
            r.delay(2000);
            r.waitForIdle();
        }

        // Changing directory on hidden file dialog should not cause an exception
        EventQueue.invokeAndWait(() -> {
            fd.setDirectory("/");
        });
        r.delay(2000);
        r.waitForIdle();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        ExceptionAfterSetDirectory test = new ExceptionAfterSetDirectory();
        test.start();
    }
}
