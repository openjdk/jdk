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

/* @test
 * @bug 4694598
 * @key headful
 * @summary JEditor pane throws NullPointerException on mouse movement.
 * @run main bug4694598
 */

import java.awt.AWTException;
import java.awt.Robot;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4694598 {
    JFrame frame;
    volatile int bottom;
    final Path frameContentFile = Path.of("FrameContent.html");

    public void setupGUI() {
        frame = new JFrame("Test 4694598");
        JEditorPane jep = new JEditorPane();
        jep.setEditable(false);
        frame.getContentPane().add(jep);
        frame.setLocation(50, 50);
        frame.setSize(400, 400);

        String frameContentString = "<HTML><BODY>\n" +
                "    Frame content.\n" +
                "</BODY></HTML>";
        try (Writer writer = Files.newBufferedWriter(frameContentFile)) {
            writer.write(frameContentString);
        } catch (IOException ioe){
            throw new RuntimeException("Could not create html file to embed", ioe);
        }
        URL frameContentUrl;
        try {
            frameContentUrl = frameContentFile.toUri().toURL();
        } catch (MalformedURLException mue) {
            throw new RuntimeException("Can not convert path to URL", mue);
        }
        jep.setContentType("text/html");
        String html = "<HTML> <BODY>" +
                "<FRAMESET cols=\"100%\">" +
                "<FRAME src=\"" + frameContentUrl + "\">" +
                "</FRAMESET>" +
                // ! Without <noframes> bug is not reproducable
                "<NOFRAMES>" +
                "</NOFRAMES>" +
                "</BODY> </HTML>";
        jep.setText(html);

        frame.setVisible(true);
    }

    public void performTest() throws InterruptedException,
            InvocationTargetException, AWTException {
        Robot robo = new Robot();
        robo.waitForIdle();

        final int range = 20;
        SwingUtilities.invokeAndWait(() -> {
            bottom = frame.getLocationOnScreen().y
                    + frame.getSize().height - range;
        });
        for (int i = 0; i < range; i++) {
            robo.mouseMove(300, bottom + i);
            robo.waitForIdle();
            robo.delay(50);
        }
    }

    public void cleanupGUI() {
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
        try {
            Files.deleteIfExists(frameContentFile);
        } catch (IOException ignore) {}
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        bug4694598 app = new bug4694598();
        SwingUtilities.invokeAndWait(app::setupGUI);
        app.performTest();
        SwingUtilities.invokeAndWait(app::cleanupGUI);
    }
}
