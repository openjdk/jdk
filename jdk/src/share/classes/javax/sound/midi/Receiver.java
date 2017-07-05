/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.sound.midi;


/**
 * A <code>Receiver</code> receives <code>{@link MidiEvent}</code> objects and
 * typically does something useful in response, such as interpreting them to
 * generate sound or raw MIDI output.  Common MIDI receivers include
 * synthesizers and MIDI Out ports.
 *
 * @see MidiDevice
 * @see Synthesizer
 * @see Transmitter
 *
 * @author Kara Kytle
 */
public interface Receiver {


    //$$fb 2002-04-12: fix for 4662090: Contradiction in Receiver specification
    /**
     * Sends a MIDI message and time-stamp to this receiver.
     * If time-stamping is not supported by this receiver, the time-stamp
     * value should be -1.
     * @param message the MIDI message to send
     * @param timeStamp the time-stamp for the message, in microseconds.
     * @throws IllegalStateException if the receiver is closed
     */
    public void send(MidiMessage message, long timeStamp);

    /**
     * Indicates that the application has finished using the receiver, and
     * that limited resources it requires may be released or made available.
     *
     * <p>If the creation of this <code>Receiver</code> resulted in
     * implicitly opening the underlying device, the device is
     * implicitly closed by this method. This is true unless the device is
     * kept open by other <code>Receiver</code> or <code>Transmitter</code>
     * instances that opened the device implicitly, and unless the device
     * has been opened explicitly. If the device this
     * <code>Receiver</code> is retrieved from is closed explicitly by
     * calling {@link MidiDevice#close MidiDevice.close}, the
     * <code>Receiver</code> is closed, too.  For a detailed
     * description of open/close behaviour see the class description
     * of {@link javax.sound.midi.MidiDevice MidiDevice}.
     *
     * @see javax.sound.midi.MidiSystem#getReceiver
     */
    public void close();
}
