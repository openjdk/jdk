/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import static javax.swing.SwingUtilities.invokeAndWait;

/*
 * @test
 * @bug 8301846
 * @requires (os.family == "windows")
 * @summary Sound recording fails after screen lock and unlock.
 * @run main/manual OpenLineAfterScreenLock
 */
public class OpenLineAfterScreenLock {

    private static final String INSTRUCTIONS = """
            This test verifies it can record sound from the first sound capture device after
            locking and unlocking the screen. The first part of the test has already completed.

            Lock the screen and unlock it. Then click Continue to complete the test.

            The test will finish automatically.
            """;

    private static final CountDownLatch latch = new CountDownLatch(1);

    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            runTest();

            // Creating JFileChooser initializes COM
            // which affects ability to open audio lines
            new JFileChooser();

            invokeAndWait(OpenLineAfterScreenLock::createInstructionsUI);
            if (!latch.await(2, TimeUnit.MINUTES)) {
                throw new RuntimeException("Test failed: Test timed out!!");
            }

            runTest();
        } finally {
            invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("Test Passed");
    }

    private static void runTest() {
        try {
            Mixer mixer = getMixer();
            TargetDataLine line =
                    (TargetDataLine) mixer.getLine(mixer.getTargetLineInfo()[0]);
            line.open();
            line.close();
        } catch (LineUnavailableException e) {
            throw new RuntimeException("Test failed: Line unavailable", e);
        }
    }

    private static Mixer getMixer() {
        return Arrays.stream(AudioSystem.getMixerInfo())
                     .map(AudioSystem::getMixer)
                     .filter(OpenLineAfterScreenLock::isRecordingDevice)
                     .skip(1) // Skip the primary driver and choose one directly
                     .findAny()
                     .orElseThrow();
    }

    private static boolean isRecordingDevice(Mixer mixer) {
        Line.Info[] lineInfos = mixer.getTargetLineInfo();
        return lineInfos.length > 0
               && lineInfos[0].getLineClass() == TargetDataLine.class;
    }

    private static void createInstructionsUI() {
        frame = new JFrame("Instructions for OpenLineAfterScreenLock");

        JTextArea textArea = new JTextArea(INSTRUCTIONS);
        textArea.setEditable(false);

        JScrollPane pane = new JScrollPane(textArea);
        frame.getContentPane().add(pane, BorderLayout.NORTH);

        JButton button = new JButton("Continue");
        button.addActionListener(e -> latch.countDown());
        frame.getContentPane().add(button, BorderLayout.PAGE_END);

        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new CloseWindowHandler());
        frame.setVisible(true);
    }

    private static class CloseWindowHandler extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            latch.countDown();
            throw new RuntimeException("Test window closed abruptly");
        }
    }
}
