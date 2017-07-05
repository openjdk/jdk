/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.media.sound;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.RandomAccessFile;
import java.net.URL;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileWriter;




/**
 * Abstract File Writer class.
 *
 * @author Jan Borgersen
 */
abstract class SunFileWriter extends AudioFileWriter {


    // buffer size for write
    protected static final int bufferSize = 16384;

    // buffer size for temporary input streams
    protected static final int bisBufferSize = 4096;


    final AudioFileFormat.Type types[];


    /**
     * Constructs a new SunParser object.
     */
    SunFileWriter(AudioFileFormat.Type types[]) {
        this.types = types;
    }



    // METHODS TO IMPLEMENT AudioFileWriter

    // new, 10.27.99

    public AudioFileFormat.Type[] getAudioFileTypes(){

        AudioFileFormat.Type[] localArray = new AudioFileFormat.Type[types.length];
        System.arraycopy(types, 0, localArray, 0, types.length);
        return localArray;
    }


    public abstract AudioFileFormat.Type[] getAudioFileTypes(AudioInputStream stream);

    public abstract int write(AudioInputStream stream, AudioFileFormat.Type fileType, OutputStream out) throws IOException;

    public abstract int write(AudioInputStream stream, AudioFileFormat.Type fileType, File out) throws IOException;


    // HELPER METHODS


    /**
     * rllong
     * Protected helper method to read 64 bits and changing the order of
     * each bytes.
     * @param DataInputStream
     * @return 32 bits swapped value.
     * @exception IOException
     */
    protected int rllong(DataInputStream dis) throws IOException {

        int b1, b2, b3, b4 ;
        int i = 0;

        i = dis.readInt();

        b1 = ( i & 0xFF ) << 24 ;
        b2 = ( i & 0xFF00 ) << 8;
        b3 = ( i & 0xFF0000 ) >> 8;
        b4 = ( i & 0xFF000000 ) >>> 24;

        i = ( b1 | b2 | b3 | b4 );

        return i;
    }

    /**
     * big2little
     * Protected helper method to swap the order of bytes in a 32 bit int
     * @param int
     * @return 32 bits swapped value
     */
    protected int big2little(int i) {

        int b1, b2, b3, b4 ;

        b1 = ( i & 0xFF ) << 24 ;
        b2 = ( i & 0xFF00 ) << 8;
        b3 = ( i & 0xFF0000 ) >> 8;
        b4 = ( i & 0xFF000000 ) >>> 24;

        i = ( b1 | b2 | b3 | b4 );

        return i;
    }

    /**
     * rlshort
     * Protected helper method to read 16 bits value. Swap high with low byte.
     * @param DataInputStream
     * @return the swapped value.
     * @exception IOException
     */
    protected short rlshort(DataInputStream dis)  throws IOException {

        short s=0;
        short high, low;

        s = dis.readShort();

        high = (short)(( s & 0xFF ) << 8) ;
        low = (short)(( s & 0xFF00 ) >>> 8);

        s = (short)( high | low );

        return s;
    }

    /**
     * big2little
     * Protected helper method to swap the order of bytes in a 16 bit short
     * @param int
     * @return 16 bits swapped value
     */
    protected short big2littleShort(short i) {

        short high, low;

        high = (short)(( i & 0xFF ) << 8) ;
        low = (short)(( i & 0xFF00 ) >>> 8);

        i = (short)( high | low );

        return i;
    }

}
