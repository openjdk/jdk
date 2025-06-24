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

/*
 * @test
 * @bug 8356049
 * @key sound headful
 * @summary basic testing of javax.sound.SoundClip
 * @run main/othervm/timeout=60 SoundClipTest javasound.wav good
 * @run main/othervm/timeout=60 SoundClipTest badsound.wav bad
 */

public class SoundClipTest {

    public static void main(String[] args) throws Exception {

        // First verify IOException
        try {
            SoundClip.createSoundClip(new File("notafile.wav"));
            throw new RuntimeException("No IOException");
        } catch (IOException e) {
        }

        if (!isSoundcardInstalled()) {
            return;
        }
        String dir = System.getProperty("test.src", ".");
        String sep = System.getProperty("file.separator");
        File file = new File(dir + sep + args[0]);

        SoundClip clip = SoundClip.createSoundClip(file);

        // Check for bad clip case
        boolean bad = (args.length > 1) && (args[1].equals("bad"));
        if (bad) {
            if (clip.canPlay()) {
                throw new RuntimeException("Should not be able play clip");
            }
            return;
        } else {
            if (!clip.canPlay()) {
                throw new RuntimeException("Cannot play clip");
            }
        }

        boolean playing = false;
        int waitCount = 0;
        System.out.println("Call loop()");
        clip.loop();
        while (!playing && waitCount < 10) {
            Thread.sleep(500);
            if (clip.isPlaying()) {
                playing = true;
            }
            waitCount++;
        }
        if (!playing) {
            throw new RuntimeException("Clip does not play");
        }
        waitCount = 0;
        System.out.println("Call stop()");
        clip.stop();
        System.out.println("Called stop()");

        while (playing && waitCount < 10) {
            Thread.sleep(500);
            if (clip.isPlaying()) {
                playing = false;
            }
            waitCount++;
        }
        if (!playing) {
            throw new RuntimeException("Clip does not stop");
        }
        System.out.println("Clip has stopped.");

        // Should also test play() but don't check isPlaying() for test reliability reasons.
        System.out.println("Call play.");
        clip.play();
        System.out.println("Called play()");
        Thread.sleep(1000);
        System.out.println("Call stop()");
        clip.stop();
        System.out.println("Called stop()");
        clip.loop(); // Looping should not prevent exit
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
