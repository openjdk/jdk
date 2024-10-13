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

import java.io.ByteArrayInputStream;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

/**
 * @test
 * @bug 8319598
 * @summary SMFParser bug with running status, interrupted by Meta or SysEx messages
 */
public class SMFInterruptedRunningStatus {

    public static void main(String[] args) throws Exception {

        byte[][] files = new byte[][] {SMF_1, SMF_2, SMF_3};
        for (int i = 0; i < files.length; i++) {
            Sequence seq = MidiSystem.getSequence(
                    new ByteArrayInputStream(files[i]));
            testSequence(seq, i + 1);
        }

        // no exception thrown, all files have been parsed correctly
        System.out.println("Test passed");
    }

    private static void testSequence(Sequence seq, int fileNumber) {

        // check number of tracks and number of events
        Track[] tracks = seq.getTracks();
        if (1 != tracks.length) {
            throw new RuntimeException("file number "
                    + fileNumber + " fails (incorrect number of tracks: "
                    + tracks.length + ")");
        }
        Track track = tracks[0];
        if (7 != track.size()) {
            throw new RuntimeException("file number " + fileNumber
                    + " fails (incorrect number of events: "
                    + track.size() + ")");
        }

        // check status byte of each message
        int[] expectedStatusBytes = new int[] {
                0x90, 0xFF, 0x90, 0x90, 0x90, 0xFF, 0xFF};
        for (int i = 0; i < expectedStatusBytes.length; i++) {
            int expected = expectedStatusBytes[i];
            if (expected != track.get(i).getMessage().getStatus()) {
                throw new RuntimeException("file number " + fileNumber
                        + " fails (wrong status byte in event " + i + ")");
            }
        }
    }

    // MIDI file without running status - should work equally before
    // and after the bugfix
    private static final byte[] SMF_1 = {
        0x4D, 0x54, 0x68, 0x64, 0x00, 0x00, 0x00, 0x06,  // file header (start)
        0x00, 0x01, 0x00, 0x01, 0x00, (byte) 0x80,       // file header (end)
        0x4D, 0x54, 0x72, 0x6B, 0x00, 0x00, 0x00, 0x24,  // track header
        0x00,                                            // delta time
        (byte) 0x90, 0x3C, 0x7F,                         // Note-ON (C)
        0x40,                                            // delta time
        (byte) 0xFF, 0x01, 0x04, 0x54, 0x65, 0x73, 0x74, // META (text)
        0x20,                                            // delta time
        (byte) 0x90, 0x3C, 0x00,                         // Note-OFF (C)
        0x20,                                            // delta time
        (byte) 0x90, 0x3E, 0x7F,                         // Note-ON (D)
        0x60,                                            // delta time
        (byte) 0x90, 0x3E, 0x00,                         // Note-OFF (D)
        0x20,                                            // delta time
        (byte) 0xFF, 0x01, 0x04, 0x54, 0x65, 0x73, 0x74, // META (text)
        0x00,                                            // delta time
        (byte) 0xFF, 0x2F, 0x00                          // META (end of track)
    };

    // MIDI file with running status, interrupted by a META message
    // - failed before the bugfix
    private static final byte[] SMF_2 = {
        0x4D, 0x54, 0x68, 0x64, 0x00, 0x00, 0x00, 0x06,  // file header (start)
        0x00, 0x01, 0x00, 0x01, 0x00, (byte) 0x80,       // file header (end)
        0x4D, 0x54, 0x72, 0x6B, 0x00, 0x00, 0x00, 0x21,  // track header
        0x00,                                            // delta time
        (byte) 0x90, 0x3C, 0x7F,                         // Note-ON (C)
        0x40,                                            // delta time
        (byte) 0xFF, 0x01, 0x04, 0x54, 0x65, 0x73, 0x74, // META (interruptor)
        0x20,                                            // delta time
        0x3C, 0x00,                                      // Note-OFF (C) - running status
        0x20,                                            // delta time
        0x3E, 0x7F,                                      // Note-ON (D) - running status
        0x60,                                            // delta time
        0x3E, 0x00,                                      // Note-OFF (D) - running status
        0x20,                                            // delta time
        (byte) 0xFF, 0x01, 0x04, 0x54, 0x65, 0x73, 0x74, // META (text)
        0x00,                                            // delta time
        (byte) 0xFF, 0x2F, 0x00                          // META (end of track)
    };

    // MIDI file with running status, interrupted by a META message
    // - succeeded before the bugfix but with wrong interpretation of the data
    private static final byte[] SMF_3 = {
        0x4D, 0x54, 0x68, 0x64, 0x00, 0x00, 0x00, 0x06,  // file header (start)
        0x00, 0x01, 0x00, 0x01, 0x00, (byte) 0x80,       // file header (end)
        0x4D, 0x54, 0x72, 0x6B, 0x00, 0x00, 0x00, 0x21,  // track header
        0x00,                                            // delta time
        (byte) 0x90, 0x3C, 0x7F,                         // Note-ON (C)
        0x40,                                            // delta time
        (byte) 0xFF, 0x01, 0x04, 0x54, 0x65, 0x73, 0x74, // META (interruptor)
        0x20,                                            // delta time
        0x3C, 0x00,                                      // Note-OFF (C) - running status
        0x0D,                                            // delta time
        0x3E, 0x7F,                                      // Note-ON (D) - running status
        0x60,                                            // delta time
        0x3E, 0x00,                                      // Note-OFF (D) - running status
        0x20,                                            // delta time
        (byte) 0xFF, 0x01, 0x04, 0x54, 0x65, 0x73, 0x74, // META (text)
        0x00,                                            // delta time
        (byte) 0xFF, 0x2F, 0x00                          // META (end of track)
    };
}

