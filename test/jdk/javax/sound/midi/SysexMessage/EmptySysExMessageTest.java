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

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * @test
 * @key sound
 * @bug 8282578
 * @summary AIOOBE in javax.sound.sampled.Clip
 * @run main EmptySysExMessageTest
 */

public class EmptySysExMessageTest {
    public static void main(String[] args) {
        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String name = "zerosysex.mid";
        try {
            readAudioFile(dir + sep + name);
        } catch (Throwable t) {
            throw new RuntimeException("Invalid file " + name
                    + " caused unexpected exception during read: "
                    + t + System.lineSeparator());
        }
    }

    static void readAudioFile(String name) throws IOException {
        File soundFile = new File(name);
        Path path = Paths.get(soundFile.getAbsolutePath());
        byte[] samples = Files.readAllBytes(path);

        try {
            AudioInputStream audioInputStream =
                    AudioSystem.getAudioInputStream(new ByteArrayInputStream(samples));
            try (Clip clip = AudioSystem.getClip()) {
                clip.open(audioInputStream);
                clip.start();
                Thread.sleep(1000);
                clip.stop();
            }
        } catch (UnsupportedAudioFileException
                 | LineUnavailableException
                 | IOException
                 | InterruptedException
                 | IllegalArgumentException
                 | IllegalStateException
                 | SecurityException expected) {
            // Do nothing, these types of exception are expected on invalid file
        }
    }
}
