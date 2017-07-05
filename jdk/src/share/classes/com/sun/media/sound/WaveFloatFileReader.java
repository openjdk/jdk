/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

/**
 * Floating-point encoded (format 3) WAVE file loader.
 *
 * @author Karl Helgason
 */
public class WaveFloatFileReader extends AudioFileReader {

    public AudioFileFormat getAudioFileFormat(InputStream stream)
            throws UnsupportedAudioFileException, IOException {

        stream.mark(200);
        AudioFileFormat format;
        try {
            format = internal_getAudioFileFormat(stream);
        } finally {
            stream.reset();
        }
        return format;
    }

    private AudioFileFormat internal_getAudioFileFormat(InputStream stream)
            throws UnsupportedAudioFileException, IOException {

        RIFFReader riffiterator = new RIFFReader(stream);
        if (!riffiterator.getFormat().equals("RIFF"))
            throw new UnsupportedAudioFileException();
        if (!riffiterator.getType().equals("WAVE"))
            throw new UnsupportedAudioFileException();

        boolean fmt_found = false;
        boolean data_found = false;

        int channels = 1;
        long samplerate = 1;
        int framesize = 1;
        int bits = 1;

        while (riffiterator.hasNextChunk()) {
            RIFFReader chunk = riffiterator.nextChunk();

            if (chunk.getFormat().equals("fmt ")) {
                fmt_found = true;

                int format = chunk.readUnsignedShort();
                if (format != 3) // WAVE_FORMAT_IEEE_FLOAT only
                    throw new UnsupportedAudioFileException();
                channels = chunk.readUnsignedShort();
                samplerate = chunk.readUnsignedInt();
                /* framerate = */chunk.readUnsignedInt();
                framesize = chunk.readUnsignedShort();
                bits = chunk.readUnsignedShort();
            }
            if (chunk.getFormat().equals("data")) {
                data_found = true;
                break;
            }
        }

        if (!fmt_found)
            throw new UnsupportedAudioFileException();
        if (!data_found)
            throw new UnsupportedAudioFileException();

        AudioFormat audioformat = new AudioFormat(
                AudioFloatConverter.PCM_FLOAT, samplerate, bits, channels,
                framesize, samplerate, false);
        AudioFileFormat fileformat = new AudioFileFormat(
                AudioFileFormat.Type.WAVE, audioformat,
                AudioSystem.NOT_SPECIFIED);
        return fileformat;
    }

    public AudioInputStream getAudioInputStream(InputStream stream)
            throws UnsupportedAudioFileException, IOException {

        AudioFileFormat format = getAudioFileFormat(stream);
        RIFFReader riffiterator = new RIFFReader(stream);
        if (!riffiterator.getFormat().equals("RIFF"))
            throw new UnsupportedAudioFileException();
        if (!riffiterator.getType().equals("WAVE"))
            throw new UnsupportedAudioFileException();
        while (riffiterator.hasNextChunk()) {
            RIFFReader chunk = riffiterator.nextChunk();
            if (chunk.getFormat().equals("data")) {
                return new AudioInputStream(chunk, format.getFormat(),
                        chunk.getSize());
            }
        }
        throw new UnsupportedAudioFileException();
    }

    public AudioFileFormat getAudioFileFormat(URL url)
            throws UnsupportedAudioFileException, IOException {
        InputStream stream = url.openStream();
        AudioFileFormat format;
        try {
            format = getAudioFileFormat(new BufferedInputStream(stream));
        } finally {
            stream.close();
        }
        return format;
    }

    public AudioFileFormat getAudioFileFormat(File file)
            throws UnsupportedAudioFileException, IOException {
        InputStream stream = new FileInputStream(file);
        AudioFileFormat format;
        try {
            format = getAudioFileFormat(new BufferedInputStream(stream));
        } finally {
            stream.close();
        }
        return format;
    }

    public AudioInputStream getAudioInputStream(URL url)
            throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(new BufferedInputStream(url.openStream()));
    }

    public AudioInputStream getAudioInputStream(File file)
            throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(new BufferedInputStream(new FileInputStream(
                file)));
    }
}
