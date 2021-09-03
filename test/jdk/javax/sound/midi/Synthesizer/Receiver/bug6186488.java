/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/*
 * @test
 * @bug 6186488
 * @summary Tests that software Java Syntesizer processed
 *          non-ShortMessage-derived messages
 * @run main/manual bug6186488
 */
public class bug6186488 {
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);
    private static final int testTimeout = 300000;
    private static volatile String testFailureMsg;
    private static volatile boolean testPassed;
    private static volatile boolean testFinished;

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> createAndShowTestDialog());
        try {
            if (!countDownLatch.await(testTimeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(String.format("Test timeout '%d ms' elapsed.", testTimeout));
            }
            if (!testPassed) {
                String failureMsg = testFailureMsg;
                if ((failureMsg != null) && (!failureMsg.trim().isEmpty())) {
                    throw new RuntimeException(failureMsg);
                } else {
                    throw new RuntimeException("Test failed.");
                }
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        } finally {
            testFinished = true;
        }
    }

    private static void pass() {
        testPassed = true;
        countDownLatch.countDown();
    }

    private static void fail(String failureMsg) {
        testFailureMsg = failureMsg;
        testPassed = false;
        countDownLatch.countDown();
    }

    private static String convertMillisToTimeStr(int millis) {
        if (millis < 0) {
            return "00:00:00";
        }
        int hours = millis / 3600000;
        int minutes = (millis - hours * 3600000) / 60000;
        int seconds = (millis - hours * 3600000 - minutes * 60000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static void createAndShowTestDialog() {
        String testInstruction = "This test verify that software Java Syntesizer processed non-ShortMessage-derived messages.\n" +
                "Close all other programs that may use the sound card.\n" +
                "Make sure that the speakers are connected and the volume is up.\n" +
                "Click on 'Start Test' button. If you listen a sound then test pass else test fail.";

        final JDialog dialog = new JDialog();
        dialog.setTitle("Test Sound");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.dispose();
                fail("Main dialog was closed.");
            }
        });

        final JLabel testTimeoutLabel = new JLabel(String.format("Test timeout: %s", convertMillisToTimeStr(testTimeout)));
        final long startTime = System.currentTimeMillis();
        final Timer timer = new Timer(0, null);
        timer.setDelay(1000);
        timer.addActionListener((e) -> {
            int leftTime = testTimeout - (int) (System.currentTimeMillis() - startTime);
            if ((leftTime < 0) || testFinished) {
                timer.stop();
                dialog.dispose();
            }
            testTimeoutLabel.setText(String.format("Test timeout: %s", convertMillisToTimeStr(leftTime)));
        });
        timer.start();

        JTextArea textArea = new JTextArea(testInstruction);
        textArea.setEditable(false);

        final JButton startTestButton = new JButton("Start Test");
        final JButton passButton = new JButton("PASS");
        final JButton failButton = new JButton("FAIL");
        startTestButton.addActionListener((e) -> {
            new Thread(() -> {
                try {
                    doTest();

                    SwingUtilities.invokeLater(() -> {
                        passButton.setEnabled(true);
                        failButton.setEnabled(true);
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                    dialog.dispose();
                    fail("Exception occurred in a thread executing the test.");
                }
            }).start();
        });
        passButton.setEnabled(false);
        passButton.addActionListener((e) -> {
            dialog.dispose();
            pass();
        });
        failButton.setEnabled(false);
        failButton.addActionListener((e) -> {
            dialog.dispose();
            fail("Expected that sound will be heard but did not hear sound");
        });

        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel labelPanel = new JPanel(new FlowLayout());
        labelPanel.add(testTimeoutLabel);
        mainPanel.add(labelPanel, BorderLayout.NORTH);
        mainPanel.add(textArea, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(startTestButton);
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel);
        dialog.pack();
        dialog.setVisible(true);
    }

    public static void waitForSynToOpen(MidiDevice synth) throws InterruptedException {
        int count = 0;
        do {
            if (synth.isOpen()) {
                System.out.println("synth is opened");
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        } while( ++count >= 5);
        throw new RuntimeException(synth + " did not open even after 5 seconds");
    }

    private static void doTest() throws MidiUnavailableException, InterruptedException {
        try (MidiDevice synth = MidiSystem.getSynthesizer()) {
            System.out.println("Synthesizer: " + synth.getDeviceInfo());
            synth.open();
            waitForSynToOpen(synth);
            MidiMessage msg = new GenericMidiMessage(0x90, 0x3C, 0x40);
            synth.getReceiver().send(msg, 0);
            Thread.sleep(2000);
        }
    }

    private static class GenericMidiMessage extends MidiMessage {
        GenericMidiMessage(int... message) {
            super(new byte[message.length]);
            for (int i=0; i<data.length; i++) {
                data[i] = (byte)(0xFF & message[i]);
            }
        }

        GenericMidiMessage(byte... message) {
            super(message);
        }

        public Object clone() {
            return new GenericMidiMessage((byte[])data.clone());
        }
    }
}
