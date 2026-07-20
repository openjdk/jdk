/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;

import java.awt.TextArea;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

/*
 * @test
 * @bug 4739757
 * @summary REGRESSION: Modal Dialog is not serializable after showing
 * @key headful
 * @run main ShownModalDialogSerializationTest
 */

public class ShownModalDialogSerializationTest {
    static volatile Frame frame;
    static volatile Frame outputFrame;
    static volatile Dialog dialog;

    public static void main(String[] args) throws Exception {

        EventQueue.invokeLater(ShownModalDialogSerializationTest::createTestUI);

        while (dialog == null || !dialog.isShowing()) {
            Thread.sleep(500);
        }
        File file = new File("dialog.ser");
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(dialog);
        oos.flush();
        file.delete();

        EventQueue.invokeAndWait(ShownModalDialogSerializationTest::deleteTestUI);
    }

    static void deleteTestUI() {
        if (dialog != null) {
            dialog.setVisible(false);
            dialog.dispose();
        }
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
        if (outputFrame != null) {
            outputFrame.setVisible(false);
            outputFrame.dispose();
        }
    }

    private static void createTestUI() {
        outputFrame = new Frame("ShownModalDialogSerializationTest");
        TextArea output = new TextArea(40, 50);
        outputFrame.add(output);

        frame = new Frame("invisible dialog owner");
        dialog = new Dialog(frame, "Dialog for Close", true);
        dialog.add(new Label("Close This Dialog"));
        outputFrame.setSize(200, 200);
        outputFrame.setVisible(true);
        dialog.pack();
        dialog.setVisible(true);
    }
}
