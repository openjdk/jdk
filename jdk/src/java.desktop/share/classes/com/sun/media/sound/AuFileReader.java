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
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * AU file reader.
 *
 * @author Kara Kytle
 * @author Jan Borgersen
 * @author Florian Bomers
 */
public final class AuFileReader extends SunFileReader {

    @Override
    public AudioFileFormat getAudioFileFormatImpl(final InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        boolean bigendian  = false;
        int headerSize     = -1;
        int dataSize       = -1;
        int encoding_local = -1;
        int sampleRate     = -1;
        int frameRate      = -1;
        int frameSize      = -1;
        int channels       = -1;
        final int sampleSizeInBits;
        int length = 0;
        int nread = 0;
        AudioFormat.Encoding encoding = null;

        DataInputStream dis = new DataInputStream( stream );

        final int magic = dis.readInt(); nread += 4;

        if (! (magic == AuFileFormat.AU_SUN_MAGIC) || (magic == AuFileFormat.AU_DEC_MAGIC) ||
            (magic == AuFileFormat.AU_SUN_INV_MAGIC) || (magic == AuFileFormat.AU_DEC_INV_MAGIC) ) {

            // not AU, throw exception
            throw new UnsupportedAudioFileException("not an AU file");
        }

        if ((magic == AuFileFormat.AU_SUN_MAGIC) || (magic == AuFileFormat.AU_DEC_MAGIC)) {
            bigendian = true;        // otherwise little-endian
        }

        headerSize     = (bigendian==true ? dis.readInt() : rllong(dis) );  nread += 4;
        dataSize       = (bigendian==true ? dis.readInt() : rllong(dis) );  nread += 4;
        encoding_local = (bigendian==true ? dis.readInt() : rllong(dis) );  nread += 4;
        sampleRate     = (bigendian==true ? dis.readInt() : rllong(dis) );  nread += 4;
        channels       = (bigendian==true ? dis.readInt() : rllong(dis) );  nread += 4;
        if (channels <= 0) {
            throw new UnsupportedAudioFileException("Invalid number of channels");
        }

        frameRate = sampleRate;

        switch (encoding_local) {
        case AuFileFormat.AU_ULAW_8:
            encoding = AudioFormat.Encoding.ULAW;
            sampleSizeInBits = 8;
            break;
        case AuFileFormat.AU_ALAW_8:
            encoding = AudioFormat.Encoding.ALAW;
            sampleSizeInBits = 8;
            break;
        case AuFileFormat.AU_LINEAR_8:
            // $$jb: 04.29.99: 8bit linear is *signed*, not *unsigned*
            encoding = AudioFormat.Encoding.PCM_SIGNED;
            sampleSizeInBits = 8;
            break;
        case AuFileFormat.AU_LINEAR_16:
            encoding = AudioFormat.Encoding.PCM_SIGNED;
            sampleSizeInBits = 16;
            break;
        case AuFileFormat.AU_LINEAR_24:
            encoding = AudioFormat.Encoding.PCM_SIGNED;

            sampleSizeInBits = 24;
            break;
        case AuFileFormat.AU_LINEAR_32:
            encoding = AudioFormat.Encoding.PCM_SIGNED;

            sampleSizeInBits = 32;
            break;
            // $jb: 03.19.99: we don't support these ...
            /*          case AuFileFormat.AU_FLOAT:
                        encoding = new AudioFormat.FLOAT;
                        sampleSizeInBits = 32;
                        break;
                        case AuFileFormat.AU_DOUBLE:
                        encoding = new AudioFormat.DOUBLE;
                        sampleSizeInBits = 8;
                        break;
                        case AuFileFormat.AU_ADPCM_G721:
                        encoding = new AudioFormat.G721_ADPCM;
                        sampleSizeInBits = 16;
                        break;
                        case AuFileFormat.AU_ADPCM_G723_3:
                        encoding = new AudioFormat.G723_3;
                        sampleSize = 24;
                        SamplePerUnit = 8;
                        break;
                        case AuFileFormat.AU_ADPCM_G723_5:
                        encoding = new AudioFormat.G723_5;
                        sampleSize = 40;
                        SamplePerUnit = 8;
                        break;
            */
        default:
                // unsupported filetype, throw exception
                throw new UnsupportedAudioFileException("not a valid AU file");
        }

        frameSize = calculatePCMFrameSize(sampleSizeInBits, channels);
        //$$fb 2002-11-02: fix for 4629669: AU file reader: problems with empty files
        if( dataSize < 0 ) {
            length = AudioSystem.NOT_SPECIFIED;
        } else {
            //$$fb 2003-10-20: fix for 4940459: AudioInputStream.getFrameLength() returns 0 instead of NOT_SPECIFIED
            length = dataSize / frameSize;
        }
        // now seek past the header
        dis.skipBytes(headerSize - nread);
        AudioFormat format = new AudioFormat(encoding, sampleRate,
                                             sampleSizeInBits, channels,
                                             frameSize, (float) frameRate,
                                             bigendian);
        return new AuFileFormat(AudioFileFormat.Type.AU, dataSize + headerSize,
                                format, length);
    }
}
