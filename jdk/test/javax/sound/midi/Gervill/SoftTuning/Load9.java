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

public class Load9 {

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
        // http://www.midi.org/about-midi/tuning-scale.shtml
        // 0x09 scale/octave tuning 2-byte form (Non Real-Time/REAL-TIME)
        SoftTuning tuning = new SoftTuning();
        int[] msg = {0xf0,0x7f,0x7f,0x08,0x09,0x03,0x7f,0x7f,
                5,10,15,20,25,30,35,40,45,50,51,52,
                5,10,15,20,25,30,35,40,45,50,51,52,
                0xf7};
        int[] oct = {5,10,15,20,25,30,35,40,45,50,51,52,5,10,15,20,25,30,35,40,45,50,51,52};
        byte[] bmsg = new byte[msg.length];
        for (int i = 0; i < bmsg.length; i++)
            bmsg[i] = (byte)msg[i];
        tuning.load(bmsg);
        double[] tunings = tuning.getTuning();
        for (int i = 0; i < tunings.length; i++)
        {
            double c = (oct[(i%12)*2]*128 + oct[(i%12)*2+1] -8192)*(100.0/8192.0);
            assertTrue(Math.abs(tunings[i]-(i*100 + (c))) < 0.00001);
        }
    }
}
