/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
   @summary Test SoftTuning load method */

import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.sampled.*;

import com.sun.media.sound.*;

public class Load2 {

    private static void assertEquals(Object a, Object b) throws Exception
    {
        if(!a.equals(b))
            throw new RuntimeException("assertEquals fails!");
    }

    private static void assertTrue(boolean value) throws Exception
    {
        if(!value)
            throw new RuntimeException("assertTrue fails!");
    }

    public static void main(String[] args) throws Exception {
        // http://www.midi.org/about-midi/tuning.shtml
        // 0x02 SINGLE NOTE TUNING CHANGE (REAL-TIME)
        SoftTuning tuning = new SoftTuning();
        int[] msg = {0xf0,0x7f,0x7f,0x08,0x02,0x10,0x02,
                36,36,64,0,
                40,70,0,0,
                0xf7};
        byte[] bmsg = new byte[msg.length];
        for (int i = 0; i < bmsg.length; i++)
            bmsg[i] = (byte)msg[i];
        tuning.load(bmsg);
        double[] tunings = tuning.getTuning();
        for (int i = 0; i < tunings.length; i++) {
            if(i == 36)
                assertTrue(Math.abs(tunings[i]-3650)< 0.00001);
            else if(i == 40)
                assertTrue(Math.abs(tunings[i]-7000) < 0.00001);
            else
                assertTrue(Math.abs(tunings[i]-i*100) < 0.00001);
        }
    }
}
