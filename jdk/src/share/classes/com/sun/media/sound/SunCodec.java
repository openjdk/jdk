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

package com.sun.media.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import javax.sound.sampled.spi.FormatConversionProvider;


/**
 * A codec can encode and/or decode audio data.  It provides an
 * AudioInputStream from which processed data may be read.
 * <p>
 * Its input format represents the format of the incoming
 * audio data, or the format of the data in the underlying stream.
 * <p>
 * Its output format represents the format of the processed, outgoing
 * audio data.  This is the format of the data which may be read from
 * the filtered stream.
 *
 * @author Kara Kytle
 */
abstract class SunCodec extends FormatConversionProvider {

    private final AudioFormat.Encoding[] inputEncodings;
    private final AudioFormat.Encoding[] outputEncodings;

    /**
     * Constructs a new codec object.
     */
    SunCodec(final AudioFormat.Encoding[] inputEncodings,
             final AudioFormat.Encoding[] outputEncodings) {
        this.inputEncodings = inputEncodings;
        this.outputEncodings = outputEncodings;
    }


    /**
     */
    public final AudioFormat.Encoding[] getSourceEncodings() {
        AudioFormat.Encoding[] encodings = new AudioFormat.Encoding[inputEncodings.length];
        System.arraycopy(inputEncodings, 0, encodings, 0, inputEncodings.length);
        return encodings;
    }
    /**
     */
    public final AudioFormat.Encoding[] getTargetEncodings() {
        AudioFormat.Encoding[] encodings = new AudioFormat.Encoding[outputEncodings.length];
        System.arraycopy(outputEncodings, 0, encodings, 0, outputEncodings.length);
        return encodings;
    }

    /**
     */
    public abstract AudioFormat.Encoding[] getTargetEncodings(AudioFormat sourceFormat);


    /**
     */
    public abstract AudioFormat[] getTargetFormats(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat);


    /**
     */
    public abstract AudioInputStream getAudioInputStream(AudioFormat.Encoding targetEncoding, AudioInputStream sourceStream);
    /**
     */
    public abstract AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream);


}
