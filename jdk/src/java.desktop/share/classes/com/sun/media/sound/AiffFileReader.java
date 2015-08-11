/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * AIFF file reader and writer.
 *
 * @author Kara Kytle
 * @author Jan Borgersen
 * @author Florian Bomers
 */
public final class AiffFileReader extends SunFileReader {

    @Override
    AudioFileFormat getAudioFileFormatImpl(final InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        DataInputStream dis = new DataInputStream(stream);

        // assumes a stream at the beginning of the file which has already
        // passed the magic number test...
        // leaves the input stream at the beginning of the audio data
        int fileRead = 0;
        int dataLength = 0;
        AudioFormat format = null;

        // Read the magic number
        int magic = dis.readInt();

        // $$fb: fix for 4369044: javax.sound.sampled.AudioSystem.getAudioInputStream() works wrong with Cp037
        if (magic != AiffFileFormat.AIFF_MAGIC) {
            // not AIFF, throw exception
            throw new UnsupportedAudioFileException("not an AIFF file");
        }

        int length = dis.readInt();
        int iffType = dis.readInt();
        fileRead += 12;

        int totallength;
        if(length <= 0 ) {
            length = AudioSystem.NOT_SPECIFIED;
            totallength = AudioSystem.NOT_SPECIFIED;
        } else {
            totallength = length + 8;
        }

        // Is this an AIFC or just plain AIFF file.
        boolean aifc = false;
        // $$fb: fix for 4369044: javax.sound.sampled.AudioSystem.getAudioInputStream() works wrong with Cp037
        if (iffType ==  AiffFileFormat.AIFC_MAGIC) {
            aifc = true;
        }

        // Loop through the AIFF chunks until
        // we get to the SSND chunk.
        boolean ssndFound = false;
        while (!ssndFound) {
            // Read the chunk name
            int chunkName = dis.readInt();
            int chunkLen = dis.readInt();
            fileRead += 8;

            int chunkRead = 0;

            // Switch on the chunk name.
            switch (chunkName) {
            case AiffFileFormat.FVER_MAGIC:
                // Ignore format version for now.
                break;

            case AiffFileFormat.COMM_MAGIC:
                // AIFF vs. AIFC
                // $$fb: fix for 4399551: Repost of bug candidate: cannot replay aif file (Review ID: 108108)
                if ((!aifc && chunkLen < 18) || (aifc && chunkLen < 22)) {
                    throw new UnsupportedAudioFileException("Invalid AIFF/COMM chunksize");
                }
                // Read header info.
                int channels = dis.readUnsignedShort();
                if (channels <= 0) {
                    throw new UnsupportedAudioFileException("Invalid number of channels");
                }
                dis.readInt(); // numSampleFrames
                int sampleSizeInBits = dis.readUnsignedShort();
                if (sampleSizeInBits < 1 || sampleSizeInBits > 32) {
                    throw new UnsupportedAudioFileException("Invalid AIFF/COMM sampleSize");
                }
                float sampleRate = (float) read_ieee_extended(dis);
                chunkRead += (2 + 4 + 2 + 10);

                // If this is not AIFC then we assume it's
                // a linearly encoded file.
                AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;

                if (aifc) {
                    int enc = dis.readInt(); chunkRead += 4;
                    switch (enc) {
                    case AiffFileFormat.AIFC_PCM:
                        encoding = AudioFormat.Encoding.PCM_SIGNED;
                        break;
                    case AiffFileFormat.AIFC_ULAW:
                        encoding = AudioFormat.Encoding.ULAW;
                        sampleSizeInBits = 8; // Java Sound convention
                        break;
                    default:
                        throw new UnsupportedAudioFileException("Invalid AIFF encoding");
                    }
                }
                int frameSize = calculatePCMFrameSize(sampleSizeInBits, channels);
                //$fb what's that ??
                //if (sampleSizeInBits == 8) {
                //    encoding = AudioFormat.Encoding.PCM_SIGNED;
                //}
                format =  new AudioFormat(encoding, sampleRate,
                                          sampleSizeInBits, channels,
                                          frameSize, sampleRate, true);
                break;
            case AiffFileFormat.SSND_MAGIC:
                // Data chunk.
                // we are getting *weird* numbers for chunkLen sometimes;
                // this really should be the size of the data chunk....
                int dataOffset = dis.readInt();
                int blocksize = dis.readInt();
                chunkRead += 8;

                // okay, now we are done reading the header.  we need to set the size
                // of the data segment.  we know that sometimes the value we get for
                // the chunksize is absurd.  this is the best i can think of:if the
                // value seems okay, use it.  otherwise, we get our value of
                // length by assuming that everything left is the data segment;
                // its length should be our original length (for all AIFF data chunks)
                // minus what we've read so far.
                // $$kk: we should be able to get length for the data chunk right after
                // we find "SSND."  however, some aiff files give *weird* numbers.  what
                // is going on??

                if (chunkLen < length) {
                    dataLength = chunkLen - chunkRead;
                } else {
                    // $$kk: 11.03.98: this seems dangerous!
                    dataLength = length - (fileRead + chunkRead);
                }
                ssndFound = true;
                break;
            } // switch
            fileRead += chunkRead;
            // skip the remainder of this chunk
            if (!ssndFound) {
                int toSkip = chunkLen - chunkRead;
                if (toSkip > 0) {
                    fileRead += dis.skipBytes(toSkip);
                }
            }
        } // while

        if (format == null) {
            throw new UnsupportedAudioFileException("missing COMM chunk");
        }
        AudioFileFormat.Type type = aifc?AudioFileFormat.Type.AIFC:AudioFileFormat.Type.AIFF;

        return new AiffFileFormat(type, totallength, format, dataLength / format.getFrameSize());
    }

    // HELPER METHODS
    /** write_ieee_extended(DataOutputStream dos, double f) throws IOException {
     * Extended precision IEEE floating-point conversion routine.
     * @argument DataOutputStream
     * @argument double
     * @return void
     * @exception IOException
     */
    private void write_ieee_extended(DataOutputStream dos, double f) throws IOException {

        int exponent = 16398;
        double highMantissa = f;

        // For now write the integer portion of f
        // $$jb: 03.30.99: stay in synch with JMF on this!!!!
        while (highMantissa < 44000) {
            highMantissa *= 2;
            exponent--;
        }
        dos.writeShort(exponent);
        dos.writeInt( ((int) highMantissa) << 16);
        dos.writeInt(0); // low Mantissa
    }


    /**
     * read_ieee_extended
     * Extended precision IEEE floating-point conversion routine.
     * @argument DataInputStream
     * @return double
     * @exception IOException
     */
    private double read_ieee_extended(DataInputStream dis) throws IOException {

        double f = 0;
        int expon = 0;
        long hiMant = 0, loMant = 0;
        long t1, t2;
        double HUGE = 3.40282346638528860e+38;


        expon = dis.readUnsignedShort();

        t1 = (long)dis.readUnsignedShort();
        t2 = (long)dis.readUnsignedShort();
        hiMant = t1 << 16 | t2;

        t1 = (long)dis.readUnsignedShort();
        t2 = (long)dis.readUnsignedShort();
        loMant = t1 << 16 | t2;

        if (expon == 0 && hiMant == 0 && loMant == 0) {
            f = 0;
        } else {
            if (expon == 0x7FFF)
                f = HUGE;
            else {
                expon -= 16383;
                expon -= 31;
                f = (hiMant * Math.pow(2, expon));
                expon -= 32;
                f += (loMant * Math.pow(2, expon));
            }
        }

        return f;
    }
}
