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

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static javax.sound.sampled.AudioSystem.NOT_SPECIFIED;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.applet.AudioClip;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/*
 * @test
 * @key headful
 * @bug 8279673
 * @summary Verify no NPE creating threads
 * @run main/othervm DataPusherThreadCheck
 */
public class DataPusherThreadCheck {

    public static void main(String[] args) throws Exception {
        // Prepare the audio file
        File file = new File("audio.wav");
        try {
            AudioFormat format =
                    new AudioFormat(PCM_SIGNED, 44100, 8, 1, 1, 44100, false);
            int dataSize = 6000*1000 * format.getFrameSize();
            InputStream in = new ByteArrayInputStream(new byte[dataSize]);
            AudioInputStream audioStream = new AudioInputStream(in, format, NOT_SPECIFIED);
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, file);
        } catch (Exception ignored) {
            return; // the test is not applicable
        }
        try {
            checkThread(file);
        } finally {
            Files.delete(file.toPath());
        }
    }

    private static void checkThread(File file) throws Exception {
        AudioClip clip = (AudioClip) file.toURL().getContent();
        clip.loop();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        boolean found = isDataPushedThreadExist();
        clip.stop();
        if (!found) {
            throw new RuntimeException("Thread 'DataPusher' isn't found");
        }
    }

    private static boolean isDataPushedThreadExist() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals("DataPusher")) {
                return true;
            }
        }
        return false;
    }

}
