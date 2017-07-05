/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * @test
 * @bug 7058662 7058666 7058672
 * @author Sergey Bylokhov
 */
public final class ReadersExceptions {

    // empty channels
    static byte[] wrongAIFFCh =
            {0x46, 0x4f, 0x52, 0x4d, // AiffFileFormat.AIFF_MAGIC
             0, 0, 0, 0, // length
             0, 0, 0, 0, // iffType
             0x43, 0x4f, 0x4d, 0x4d, // chunkName
             0, 0, 0, 100, // chunkLen
             0, 0, // channels
             0, 0, 0, 0, //
             0, 10  // sampleSize
                    , 0, 0, 0, 0};
    // empty sampleSize
    static byte[] wrongAIFFSSL =
            {0x46, 0x4f, 0x52, 0x4d, //AiffFileFormat.AIFF_MAGIC
             0, 0, 0, 0, // length
             0, 0, 0, 0, // iffType
             0x43, 0x4f, 0x4d, 0x4d, // chunkName
             0, 0, 0, 100, // chunkLen
             0, 10, // channels
             0, 0, 0, 0, //
             0, 0  // sampleSize
                    , 0, 0, 0, 0};
    // big sampleSize
    static byte[] wrongAIFFSSH =
            {0x46, 0x4f, 0x52, 0x4d, //AiffFileFormat.AIFF_MAGIC
             0, 0, 0, 0, // length
             0, 0, 0, 0, // iffType
             0x43, 0x4f, 0x4d, 0x4d, // chunkName
             0, 0, 0, 100, // chunkLen
             0, 10, // channels
             0, 0, 0, 0, //
             0, 33  // sampleSize
                    , 0, 0, 0, 0};
    // empty channels
    static byte[] wrongAUCh =
            {0x2e, 0x73, 0x6e, 0x64,//AiffFileFormat.AU_SUN_MAGIC
             0, 0, 0, 0, // headerSize
             0, 0, 0, 0, // dataSize
             0, 0, 0, 1, // encoding_local AuFileFormat.AU_ULAW_8
             0, 0, 0, 0, // sampleRate
             0, 0, 0, 0 // channels
            };
    // empty channels
    static byte[] wrongWAVCh =
            {0x52, 0x49, 0x46, 0x46, // WaveFileFormat.RIFF_MAGIC
             1, 1, 1, 1, // fileLength
             0x57, 0x41, 0x56, 0x45, //  waveMagic
             0x66, 0x6d, 0x74, 0x20, // FMT_MAGIC
             3, 0, 0, 0, // length
             1, 0, // wav_type  WAVE_FORMAT_PCM
             0, 0, // channels
             0, 0, 0, 0, // sampleRate
             0, 0, 0, 0, // avgBytesPerSec
             0, 0, // blockAlign
             1, 0, // sampleSizeInBits
             0x64, 0x61, 0x74, 0x61, // WaveFileFormat.DATA_MAGIC
             0, 0, 0, 0, // dataLength
            };
    // empty sampleSizeInBits
    static byte[] wrongWAVSSB =
            {0x52, 0x49, 0x46, 0x46, // WaveFileFormat.RIFF_MAGIC
             1, 1, 1, 1, // fileLength
             0x57, 0x41, 0x56, 0x45, //  waveMagic
             0x66, 0x6d, 0x74, 0x20, // FMT_MAGIC
             3, 0, 0, 0, // length
             1, 0, // wav_type  WAVE_FORMAT_PCM
             1, 0, // channels
             0, 0, 0, 0, // sampleRate
             0, 0, 0, 0, // avgBytesPerSec
             0, 0, // blockAlign
             0, 0, // sampleSizeInBits
             0x64, 0x61, 0x74, 0x61, // WaveFileFormat.DATA_MAGIC
             0, 0, 0, 0, // dataLength
            };

    public static void main(final String[] args) throws IOException {
        test(wrongAIFFCh);
        test(wrongAIFFSSL);
        test(wrongAIFFSSH);
        test(wrongAUCh);
        test(wrongWAVCh);
        test(wrongWAVSSB);
    }

    private static void test(final byte[] buffer) throws IOException {
        final InputStream is = new ByteArrayInputStream(buffer);
        try {
            AudioSystem.getAudioFileFormat(is);
        } catch (UnsupportedAudioFileException ignored) {
            // Expected.
            return;
        }
        throw new RuntimeException("Test Failed");
    }
}
