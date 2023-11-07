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

import javax.sound.midi.*;

/**
 * @test
 * @key sound
 * @bug 8074211 8250667
 * @summary Test if part of the previous sysex message in the output buffer
 *          is sent again with the next sysex message
 * @comment This test does not fail when the bug occurs. It requires manual
 *          monitoring of the output.
 */
public class OutputBuffer {
    public static class RawMidiMessage extends MidiMessage {
        public RawMidiMessage(byte[] data) {
            super(data);
        }

        @Override
        public Object clone() {
            return new RawMidiMessage(this.getMessage());
        }
    }

    public static void main(String[] args) {
        var deviceInfos = MidiSystem.getMidiDeviceInfo();
        for (var info : deviceInfos) {
            try (MidiDevice device = MidiSystem.getMidiDevice(info)) {
                if (device.getMaxReceivers() != 0) {
                    System.out.println("Open MIDI port: " + info.getName());
                    device.open();
                    Receiver receiver = device.getReceiver();
                    // Send two sysex messages at once
                    receiver.send(new RawMidiMessage(new byte[]{
                            (byte) 0xF0, 0x7D, 0x01, (byte) 0xF7,
                            (byte) 0xF0, 0x7D, 0x02, (byte) 0xF7
                    }), -1);
                    // Send another sysex message
                    receiver.send(new RawMidiMessage(new byte[]{(byte) 0xF0, 0x7D, 0x03, (byte) 0xF7}), -1);
                    // The MIDI device should receive 3 sysex messages
                    // F0 7D 01 F7
                    // F0 7D 02 F7
                    // F0 7D 03 F7
                }
            } catch (MidiUnavailableException e) {
                // ignore
            }
        }
    }
}
