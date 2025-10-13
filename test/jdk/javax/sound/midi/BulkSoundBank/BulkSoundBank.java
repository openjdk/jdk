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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * @test
 * @bug 8350813
 * @summary Rendering of bulky sound bank from MIDI sequence can cause OutOfMemoryError.
 * @run main/othervm -Xmx1g BulkSoundBank
 */

public class BulkSoundBank {
    static final byte[] midi = {77, 84, 104, 100, 0, 0, 0, 6, 0, 0, 0, 1, 1,
            -32, 77, 84, 114, 107, 0, 0, 0, 50, 0, -1, 88, 4, 4, 2, 24, 8, 0, -1,
            81, 3, 7, -95, 32, 0, -112, 60, 64, -125, 96, -128, 60, 64, -125, -44,
            -51, 32, -112, 48, 64, 1, -128, 48, 64, -127, -64, -45, 127, -112, 60,
            64, 1, -128, 60, 64, 0, -1, 47, 0};

    public static void main(String[] args) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(midi)) {
            MidiSystem.getSoundbank(bis);
            throw new RuntimeException("Test should throw InvalidMidiDataException"
                                       + " but it did not.");
        } catch (InvalidMidiDataException imda) {
            System.out.println("Caught InvalidMidiDataException as expected");
        }
    }
}

