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

package javax.sound.midi.spi;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;

/**
 * A <code>MidiDeviceProvider</code> is a factory or provider for a particular
 * type of MIDI device.
 * This mechanism allows the implementation to determine
 * how resources are managed in the creation and management of
 * a device.
 *
 * @author Kara Kytle
 */
public abstract class MidiDeviceProvider {


    /**
     * Indicates whether the device provider supports the device represented by
     * the specified device info object.
     * @param info an info object that describes the device for which support is queried
     * @return <code>true</code> if the specified device is supported,
     * otherwise <code>false</code>
     */
    public boolean isDeviceSupported(MidiDevice.Info info) {

        MidiDevice.Info infos[] = getDeviceInfo();

        for(int i=0; i<infos.length; i++) {
            if( info.equals( infos[i] ) ) {
                return true;
            }
        }
        return false;
    }


    /**
     * Obtains the set of info objects representing the device
     * or devices provided by this <code>MidiDeviceProvider</code>.
     * @return set of device info objects
     */
    public abstract MidiDevice.Info[] getDeviceInfo();


    /**
     * Obtains an instance of the device represented by the info object.
     * @param info an info object that describes the desired device
     * @return device instance
     * @throws IllegalArgumentException if the info object specified does not
     * match the info object for a device supported by this <code>MidiDeviceProvider</code>.
     */
    public abstract MidiDevice getDevice(MidiDevice.Info info);
}
