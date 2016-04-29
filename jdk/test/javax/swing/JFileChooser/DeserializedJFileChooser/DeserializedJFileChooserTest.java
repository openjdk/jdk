/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8146301
 * @summary Enter key does not work in a deserialized JFileChooser.
 * @run main DeserializedJFileChooserTest
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DeserializedJFileChooserTest {

    private static int state = -1;
    private static JFileChooser deserialized;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater( () -> {
            try {
                JFileChooser jfc = new JFileChooser();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(jfc);
                oos.close();
                ByteArrayInputStream bis =
                        new ByteArrayInputStream(bos.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bis);
                deserialized = (JFileChooser) ois.readObject();
                state = deserialized.showOpenDialog(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        robot.waitForIdle();
        robot.delay(1000);
        if (state != JFileChooser.APPROVE_OPTION) {
            deserialized.cancelSelection();
            throw new RuntimeException("Failed");
        }
    }
}
