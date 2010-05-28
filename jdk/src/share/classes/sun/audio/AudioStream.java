/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;

import javax.sound.sampled.*;
import javax.sound.midi.*;

/**
 * Convert an InputStream to an AudioStream.
 *
 */


public class AudioStream extends FilterInputStream {

    // AudioContainerInputStream acis;
    protected AudioInputStream ais = null;
    protected AudioFormat format = null;
    protected MidiFileFormat midiformat = null;
    protected InputStream stream = null;


    /*
     * create the AudioStream; if we survive without throwing
     * an exception, we should now have some subclass of
     * ACIS with all the header info already read
     */

    public AudioStream(InputStream in) throws IOException {

        super(in);

        stream = in;

        if( in.markSupported() == false ) {

            stream = new BufferedInputStream( in, 1024 );
        }

        try {
            ais = AudioSystem.getAudioInputStream( stream );
            format = ais.getFormat();
            this.in = ais;

        } catch (UnsupportedAudioFileException e ) {

            // not an audio file, see if it's midi...
            try {
                midiformat = MidiSystem.getMidiFileFormat( stream );

            } catch (InvalidMidiDataException e1) {
                throw new IOException("could not create audio stream from input stream");
            }
        }
    }




    /**
     * A blocking read.
     */
    /*    public int read(byte buf[], int pos, int len) throws IOException {

          return(acis.readFully(buf, pos, len));
          }
    */

    /**
     * Get the data.
     */
    public AudioData getData() throws IOException {
        int length = getLength();

        //limit the memory to 1M, so too large au file won't load
        if (length < 1024*1024) {
            byte [] buffer = new byte[length];
            try {
                ais.read(buffer, 0, length);
            } catch (IOException ex) {
                throw new IOException("Could not create AudioData Object");
            }
            return new AudioData(format, buffer);
        }

        /*              acis.setData();

                        if (acis.stream instanceof ByteArrayInputStream) {
                        Format[] format = acis.getFormat();
                        byte[] bytes = acis.getBytes();
                        if (bytes == null)
                        throw new IOException("could not create AudioData object: no data received");
                        return new AudioData((AudioFormat)format[0], bytes);
                        }
        */

        throw new IOException("could not create AudioData object");
    }


    public int getLength() {

        if( ais != null && format != null ) {
            return (int) (ais.getFrameLength() *
                          ais.getFormat().getFrameSize() );

        } else if ( midiformat != null ) {
            return (int) midiformat.getByteLength();

        } else {
            return -1;
        }
    }
}
