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
import java.net.URL;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;



/**
 * Abstract File Reader class.
 *
 * @author Jan Borgersen
 */
abstract class SunFileReader extends AudioFileReader {

    // buffer size for temporary input streams
    protected static final int bisBufferSize = 4096;

    /**
     * Constructs a new SunFileReader object.
     */
    public SunFileReader() {
    }


    // METHODS TO IMPLEMENT AudioFileReader

    /**
     * Obtains the audio file format of the input stream provided.  The stream must
     * point to valid audio file data.  In general, audio file providers may
     * need to read some data from the stream before determining whether they
     * support it.  These parsers must
     * be able to mark the stream, read enough data to determine whether they
     * support the stream, and, if not, reset the stream's read pointer to its original
     * position.  If the input stream does not support this, this method may fail
     * with an IOException.
     * @param stream the input stream from which file format information should be
     * extracted
     * @return an <code>AudioFileFormat</code> object describing the audio file format
     * @throws UnsupportedAudioFileException if the stream does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    abstract public AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException;


    /**
     * Obtains the audio file format of the URL provided.  The URL must
     * point to valid audio file data.
     * @param url the URL from which file format information should be
     * extracted
     * @return an <code>AudioFileFormat</code> object describing the audio file format
     * @throws UnsupportedAudioFileException if the URL does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    abstract public AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException;


    /**
     * Obtains the audio file format of the File provided.  The File must
     * point to valid audio file data.
     * @param file the File from which file format information should be
     * extracted
     * @return an <code>AudioFileFormat</code> object describing the audio file format
     * @throws UnsupportedAudioFileException if the File does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    abstract public AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException;


    /**
     * Obtains an audio stream from the input stream provided.  The stream must
     * point to valid audio file data.  In general, audio file providers may
     * need to read some data from the stream before determining whether they
     * support it.  These parsers must
     * be able to mark the stream, read enough data to determine whether they
     * support the stream, and, if not, reset the stream's read pointer to its original
     * position.  If the input stream does not support this, this method may fail
     * with an IOException.
     * @param stream the input stream from which the <code>AudioInputStream</code> should be
     * constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data contained
     * in the input stream.
     * @throws UnsupportedAudioFileException if the stream does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    abstract public AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException;


    /**
     * Obtains an audio stream from the URL provided.  The URL must
     * point to valid audio file data.
     * @param url the URL for which the <code>AudioInputStream</code> should be
     * constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data pointed
     * to by the URL
     * @throws UnsupportedAudioFileException if the URL does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    abstract public AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException;


    /**
     * Obtains an audio stream from the File provided.  The File must
     * point to valid audio file data.
     * @param file the File for which the <code>AudioInputStream</code> should be
     * constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data pointed
     * to by the File
     * @throws UnsupportedAudioFileException if the File does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    abstract public AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException;


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


        /** Calculates the frame size for PCM frames.
         * Note that this method is appropriate for non-packed samples.
         * For instance, 12 bit, 2 channels will return 4 bytes, not 3.
         * @param sampleSizeInBits the size of a single sample in bits
         * @param channels the number of channels
         * @return the size of a PCM frame in bytes.
         */
        protected static int calculatePCMFrameSize(int sampleSizeInBits,
                                                                                        int channels) {
                return ((sampleSizeInBits + 7) / 8) * channels;
        }
}
