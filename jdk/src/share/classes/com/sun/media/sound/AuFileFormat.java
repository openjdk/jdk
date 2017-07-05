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


import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;


/**
 * AU file format.
 *
 * @author Jan Borgersen
 */

class AuFileFormat extends AudioFileFormat {

    // magic numbers
    static final int AU_SUN_MAGIC =     0x2e736e64;
    static final int AU_SUN_INV_MAGIC = 0x646e732e;
    static final int AU_DEC_MAGIC =         0x2e736400;
    static final int AU_DEC_INV_MAGIC = 0x0064732e;

    // encodings
    static final int AU_ULAW_8       = 1;  /* 8-bit ISDN u-law */
    static final int AU_LINEAR_8     = 2;  /* 8-bit linear PCM */
    static final int AU_LINEAR_16    = 3;  /* 16-bit linear PCM */
    static final int AU_LINEAR_24    = 4;  /* 24-bit linear PCM */
    static final int AU_LINEAR_32    = 5;  /* 32-bit linear PCM */
    static final int AU_FLOAT        = 6;  /* 32-bit IEEE floating point */
    static final int AU_DOUBLE       = 7;  /* 64-bit IEEE floating point */
    static final int AU_ADPCM_G721   = 23; /* 4-bit CCITT g.721 ADPCM */
    static final int AU_ADPCM_G722   = 24; /* CCITT g.722 ADPCM */
    static final int AU_ADPCM_G723_3 = 25; /* CCITT g.723 3-bit ADPCM */
    static final int AU_ADPCM_G723_5 = 26; /* CCITT g.723 5-bit ADPCM */
    static final int AU_ALAW_8       = 27; /* 8-bit ISDN A-law */

    static final int AU_HEADERSIZE       = 24;

    int auType;

    AuFileFormat( AudioFileFormat aff ) {

        this( aff.getType(), aff.getByteLength(), aff.getFormat(), aff.getFrameLength() );
    }

    AuFileFormat(AudioFileFormat.Type type, int lengthInBytes, AudioFormat format, int lengthInFrames) {

        super(type,lengthInBytes,format,lengthInFrames);

        AudioFormat.Encoding encoding = format.getEncoding();

        auType = -1;

        if( AudioFormat.Encoding.ALAW.equals(encoding) ) {
            if( format.getSampleSizeInBits()==8 ) {
                auType = AU_ALAW_8;
            }
        } else if( AudioFormat.Encoding.ULAW.equals(encoding) ) {
            if( format.getSampleSizeInBits()==8 ) {
                auType = AU_ULAW_8;
            }
        } else if( AudioFormat.Encoding.PCM_SIGNED.equals(encoding) ) {
            if( format.getSampleSizeInBits()==8 ) {
                auType = AU_LINEAR_8;
            } else if( format.getSampleSizeInBits()==16 ) {
                auType = AU_LINEAR_16;
            } else if( format.getSampleSizeInBits()==24 ) {
                auType = AU_LINEAR_24;
            } else if( format.getSampleSizeInBits()==32 ) {
                auType = AU_LINEAR_32;
            }
        }

    }

    public int getAuType() {

        return auType;
    }

}
