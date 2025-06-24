/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import javax.sound.SoundClip;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8356049
 * @key sound headful
 * @library /test/lib
 * @summary test that loop exits
 * @run main/othervm/timeout=120 LoopExitTest javasound.wav
 */

public class LoopExitTest {

    public static void main(String[] args) throws Exception {

        if (!isSoundcardInstalled()) { // will re-test in child but that's OK.
            return;
        }

        /* Parent */
        if (args.length == 1) { // run child
            System.out.println("Parent running");
                String dir = System.getProperty("test.src", ".");
            String sep = System.getProperty("file.separator");
            String fileName = dir + sep + args[0];
            ProcessBuilder pb =
                ProcessTools.createTestJavaProcessBuilder(
                             LoopExitTest.class.getName(), fileName, "loop");
            Process process = ProcessTools.startProcess("Loop", pb);
            OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new TimeoutException("Timed out waiting for Loop child");
            }
            System.out.println("Child Exited");
            outputAnalyzer.shouldHaveExitValue(0);
            System.out.println("Parent exiting");
            return;
        }

        /* Child */
        File file = new File(args[0]);
        SoundClip clip = SoundClip.createSoundClip(file);
        System.out.println("Call loop.");
        clip.loop();
        Thread.sleep(5000); // make sure loop REALLY started
        System.out.println("Test should exit now.");
    }

    /**
     * Returns true if at least one soundcard is correctly installed
     * on the system.
     */
    public static boolean isSoundcardInstalled() {
        boolean result = false;
        try {
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            if (mixers.length > 0) {
                result = AudioSystem.getSourceDataLine(null) != null;
            }
        } catch (Exception e) {
            System.err.println("Exception occurred: " + e);
            e.printStackTrace();
        }
        if (!result) {
            System.err.println("Soundcard does not exist or sound drivers not installed!");
            System.err.println("This test requires sound drivers for execution.");
        }
        return result;
    }
}
