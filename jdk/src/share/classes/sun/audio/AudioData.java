/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.audio;

import java.io.*;
import javax.sound.sampled.*;


/**
 * A clip of audio data. This data can be used to construct an
 * AudioDataStream, which can be played. <p>
 *
 * @author  Arthur van Hoff
 * @author  Kara Kytle
 * @see     AudioDataStream
 * @see     AudioPlayer
 */

 /*
  * the idea here is that the AudioData object encapsulates the
  * data you need to play an audio clip based on a defined set
  * of data.  to do this, you require the audio data (a byte
  * array rather than an arbitrary input stream) and a format
  * object.
  */


public final class AudioData {

    private static final AudioFormat DEFAULT_FORMAT =
        new AudioFormat(AudioFormat.Encoding.ULAW,
                        8000,   // sample rate
                        8,      // sample size in bits
                        1,      // channels
                        1,      // frame size in bytes
                        8000,   // frame rate
                        true ); // bigendian (irrelevant for 8-bit data)

    AudioFormat format;   // carry forth the format array amusement
    byte buffer[];

    /**
     * Constructor
     */
    public AudioData(byte buffer[]) {

        this.buffer = buffer;
        // if we cannot extract valid format information, we resort to assuming the data will be 8k mono u-law
        // in order to provide maximal backwards compatibility....
        this.format = DEFAULT_FORMAT;

        // okay, we need to extract the format and the byte buffer of data
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(buffer));
            this.format = ais.getFormat();
            ais.close();
            // $$fb 2002-10-27: buffer contains the file header now!
        } catch (IOException e) {
            // use default format
        } catch (UnsupportedAudioFileException e1 ) {
            // use default format
        }
    }


    /**
     * Non-public constructor; this is the one we use in ADS and CADS
     * constructors.
     */
    AudioData(AudioFormat format, byte[] buffer) {

        this.format = format;
        this.buffer = buffer;
    }
}
