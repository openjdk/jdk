/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
/* @test
   @bug 8281966
   @key headful
   @requires (os.family == "windows")
   @library /java/awt/regtesthelpers
   @build PassFailJFrame
   @summary Test to check if the absolute path of Symbolic Link folder
            is valid on ValueChanged property listener.
   @run main/manual FileChooserSymLinkTest
*/

import java.awt.Dimension;
import java.awt.Robot;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class FileChooserSymLinkTest{
    static JFrame frame = null;
    static PassFailJFrame passFailJFrame = null;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        robot.delay(2000);
        robot.waitForIdle();

        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws InterruptedException, InvocationTargetException {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Create a regular directory in any specific path.
                    ex: mkdir c:\\target
                2. Create a Symbolic link targeting the created test directory.
                    ex : mklink /D c:\\link c:\\target
                3. In JFileChooser, navigate to "link" created directed.
                4. On click of the "link" directory, if the Absolute path of
                    Symbolic Link is valid then Click PASS, else if it is
                    null then Click FAIL.
                """;
        frame = new JFrame("JFileChooser Symbolic Link test");
        passFailJFrame = new PassFailJFrame(INSTRUCTIONS);
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);

        frame.setPreferredSize(new Dimension(600, 600));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogType(JFileChooser.CUSTOM_DIALOG);
        jfc.setControlButtonsAreShown(false);
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jfc.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                    System.out.println(String.format("Absolute Path : %s",evt.getNewValue()));
                }
            }
        });
        frame.add(jfc);
        frame.pack();
        frame.setVisible(true);
    }
}
