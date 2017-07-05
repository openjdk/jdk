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

import java.util.Vector;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.net.URL;
import java.net.MalformedURLException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.SequenceInputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;



/**
 * WAVE file reader.
 *
 * @author Kara Kytle
 * @author Jan Borgersen
 * @author Florian Bomers
 */
public class WaveFileReader extends SunFileReader {

    private static final int MAX_READ_LENGTH = 12;

    /**
     * WAVE reader type
     */

    public static final AudioFileFormat.Type types[] = {
        AudioFileFormat.Type.WAVE
    };


    /**
     * Constructs a new WaveFileReader object.
     */
    public WaveFileReader() {
    }


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
    public AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException {
        // fix for 4489272: AudioSystem.getAudioFileFormat() fails for InputStream, but works for URL
        AudioFileFormat aff = getFMT(stream, true);
        // the following is not strictly necessary - but was implemented like that in 1.3.0 - 1.4.1
        // so I leave it as it was. May remove this for 1.5.0
        stream.reset();
        return aff;
    }


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
    public AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream urlStream = url.openStream(); // throws IOException
        AudioFileFormat fileFormat = null;
        try {
            fileFormat = getFMT(urlStream, false);
        } finally {
            urlStream.close();
        }
        return fileFormat;
    }


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
    public AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        AudioFileFormat fileFormat = null;
        FileInputStream fis = new FileInputStream(file);       // throws IOException
        // part of fix for 4325421
        try {
            fileFormat = getFMT(fis, false);
        } finally {
            fis.close();
        }

        return fileFormat;
    }


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
    public AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        // getFMT leaves the input stream at the beginning of the audio data
        AudioFileFormat fileFormat = getFMT(stream, true); // throws UnsupportedAudioFileException, IOException

        // we've got everything, and the stream is at the
        // beginning of the audio data, so return an AudioInputStream.
        return new AudioInputStream(stream, fileFormat.getFormat(), fileFormat.getFrameLength());
    }


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
    public AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream urlStream = url.openStream();  // throws IOException
        AudioFileFormat fileFormat = null;
        try {
            fileFormat = getFMT(urlStream, false);
        } finally {
            if (fileFormat == null) {
                urlStream.close();
            }
        }
        return new AudioInputStream(urlStream, fileFormat.getFormat(), fileFormat.getFrameLength());
    }


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
    public AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        FileInputStream fis = new FileInputStream(file); // throws IOException
        AudioFileFormat fileFormat = null;
        // part of fix for 4325421
        try {
            fileFormat = getFMT(fis, false);
        } finally {
            if (fileFormat == null) {
                fis.close();
            }
        }
        return new AudioInputStream(fis, fileFormat.getFormat(), fileFormat.getFrameLength());
    }


    //--------------------------------------------------------------------


    private AudioFileFormat getFMT(InputStream stream, boolean doReset) throws UnsupportedAudioFileException, IOException {

        // assumes sream is rewound

        int bytesRead;
        int nread = 0;
        int fmt;
        int length = 0;
        int wav_type = 0;
        short channels;
        long sampleRate;
        long avgBytesPerSec;
        short blockAlign;
        int sampleSizeInBits;
        AudioFormat.Encoding encoding = null;

        DataInputStream dis = new DataInputStream( stream );

        if (doReset) {
            dis.mark(MAX_READ_LENGTH);
        }

        int magic = dis.readInt();
        int fileLength = rllong(dis);
        int waveMagic = dis.readInt();
        int totallength;
        if (fileLength <= 0) {
            fileLength = AudioSystem.NOT_SPECIFIED;
            totallength = AudioSystem.NOT_SPECIFIED;
        } else {
            totallength = fileLength + 8;
        }

        if ((magic != WaveFileFormat.RIFF_MAGIC) || (waveMagic != WaveFileFormat.WAVE_MAGIC)) {
            // not WAVE, throw UnsupportedAudioFileException
            if (doReset) {
                dis.reset();
            }
            throw new UnsupportedAudioFileException("not a WAVE file");
        }

        // find and read the "fmt" chunk
        // we break out of this loop either by hitting EOF or finding "fmt "
        while(true) {

            try {
                fmt = dis.readInt();
                nread += 4;
                if( fmt==WaveFileFormat.FMT_MAGIC ) {
                    // we've found the 'fmt' chunk
                    break;
                } else {
                    // else not 'fmt', skip this chunk
                    length = rllong(dis);
                    nread += 4;
                    if (length % 2 > 0) length++;
                    nread += dis.skipBytes(length);
                }
            } catch (EOFException eof) {
                                // we've reached the end of the file without finding the 'fmt' chunk
                throw new UnsupportedAudioFileException("Not a valid WAV file");
            }
        }

        // Read the format chunk size.
        length = rllong(dis);
        nread += 4;

        // This is the nread position at the end of the format chunk
        int endLength = nread + length;

        // Read the wave format data out of the format chunk.

        // encoding.
        wav_type = rlshort(dis); nread += 2;

        if (wav_type == WaveFileFormat.WAVE_FORMAT_PCM)
            encoding = AudioFormat.Encoding.PCM_SIGNED;  // if 8-bit, we need PCM_UNSIGNED, below...
        else if ( wav_type == WaveFileFormat.WAVE_FORMAT_ALAW )
            encoding = AudioFormat.Encoding.ALAW;
        else if ( wav_type == WaveFileFormat.WAVE_FORMAT_MULAW )
            encoding = AudioFormat.Encoding.ULAW;
        else {
            // we don't support any other WAVE formats....
            throw new UnsupportedAudioFileException("Not a supported WAV file");
        }
        // channels
        channels = rlshort(dis); nread += 2;

        // sample rate.
        sampleRate = rllong(dis); nread += 4;

        // this is the avgBytesPerSec
        avgBytesPerSec = rllong(dis); nread += 4;

        // this is blockAlign value
        blockAlign = rlshort(dis); nread += 2;

        // this is the PCM-specific value bitsPerSample
        sampleSizeInBits = (int)rlshort(dis); nread += 2;

        // if sampleSizeInBits==8, we need to use PCM_UNSIGNED
        if ((sampleSizeInBits==8) && encoding.equals(AudioFormat.Encoding.PCM_SIGNED))
            encoding = AudioFormat.Encoding.PCM_UNSIGNED;

        // skip any difference between the length of the format chunk
        // and what we read

        // if the length of the chunk is odd, there's an extra pad byte
        // at the end.  i've never seen this in the fmt chunk, but we
        // should check to make sure.

        if (length % 2 != 0) length += 1;

        // $$jb: 07.28.99: endLength>nread, not length>nread.
        //       This fixes #4257986
        if (endLength > nread)
            nread += dis.skipBytes(endLength - nread);

        // we have a format now, so find the "data" chunk
        // we break out of this loop either by hitting EOF or finding "data"
        // $$kk: if "data" chunk precedes "fmt" chunk we are hosed -- can this legally happen?
        nread = 0;
        while(true) {
            try{
                int datahdr = dis.readInt();
                nread+=4;
                if (datahdr == WaveFileFormat.DATA_MAGIC) {
                    // we've found the 'data' chunk
                    break;
                } else {
                    // else not 'data', skip this chunk
                    int thisLength = rllong(dis); nread += 4;
                    if (thisLength % 2 > 0) thisLength++;
                    nread += dis.skipBytes(thisLength);
                }
            } catch (EOFException eof) {
                // we've reached the end of the file without finding the 'data' chunk
                throw new UnsupportedAudioFileException("Not a valid WAV file");
            }
        }
        // this is the length of the data chunk
        int dataLength = rllong(dis); nread += 4;

        // now build the new AudioFileFormat and return

        AudioFormat format = new AudioFormat(encoding,
                                             (float)sampleRate,
                                             sampleSizeInBits, channels,
                                             calculatePCMFrameSize(sampleSizeInBits, channels),
                                             (float)sampleRate, false);

        return new WaveFileFormat(AudioFileFormat.Type.WAVE,
                                  totallength,
                                  format,
                                  dataLength / format.getFrameSize());
    }

}
