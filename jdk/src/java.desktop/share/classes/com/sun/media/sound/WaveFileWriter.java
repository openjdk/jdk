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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.util.Objects;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

//$$fb this class is buggy. Should be replaced in future.

/**
 * WAVE file writer.
 *
 * @author Jan Borgersen
 */
public final class WaveFileWriter extends SunFileWriter {

    // magic numbers
    static  final int RIFF_MAGIC = 1380533830;
    static  final int WAVE_MAGIC = 1463899717;
    static  final int FMT_MAGIC  = 0x666d7420; // "fmt "
    static  final int DATA_MAGIC = 0x64617461; // "data"

    // encodings
    static final int WAVE_FORMAT_UNKNOWN   = 0x0000;
    static final int WAVE_FORMAT_PCM       = 0x0001;
    static final int WAVE_FORMAT_ADPCM     = 0x0002;
    static final int WAVE_FORMAT_ALAW      = 0x0006;
    static final int WAVE_FORMAT_MULAW     = 0x0007;
    static final int WAVE_FORMAT_OKI_ADPCM = 0x0010;
    static final int WAVE_FORMAT_DIGISTD   = 0x0015;
    static final int WAVE_FORMAT_DIGIFIX   = 0x0016;
    static final int WAVE_IBM_FORMAT_MULAW = 0x0101;
    static final int WAVE_IBM_FORMAT_ALAW  = 0x0102;
    static final int WAVE_IBM_FORMAT_ADPCM = 0x0103;
    static final int WAVE_FORMAT_DVI_ADPCM = 0x0011;
    static final int WAVE_FORMAT_SX7383    = 0x1C07;

    /**
     * Constructs a new WaveFileWriter object.
     */
    public WaveFileWriter() {
        super(new AudioFileFormat.Type[]{AudioFileFormat.Type.WAVE});
    }


    // METHODS TO IMPLEMENT AudioFileWriter


    public AudioFileFormat.Type[] getAudioFileTypes(AudioInputStream stream) {

        AudioFileFormat.Type[] filetypes = new AudioFileFormat.Type[types.length];
        System.arraycopy(types, 0, filetypes, 0, types.length);

        // make sure we can write this stream
        AudioFormat format = stream.getFormat();
        AudioFormat.Encoding encoding = format.getEncoding();

        if( AudioFormat.Encoding.ALAW.equals(encoding) ||
            AudioFormat.Encoding.ULAW.equals(encoding) ||
            AudioFormat.Encoding.PCM_SIGNED.equals(encoding) ||
            AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding) ) {

            return filetypes;
        }

        return new AudioFileFormat.Type[0];
    }


    public int write(AudioInputStream stream, AudioFileFormat.Type fileType, OutputStream out) throws IOException {
        Objects.requireNonNull(stream);
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(out);

        //$$fb the following check must come first ! Otherwise
        // the next frame length check may throw an IOException and
        // interrupt iterating File Writers. (see bug 4351296)

        // throws IllegalArgumentException if not supported
        WaveFileFormat waveFileFormat = (WaveFileFormat)getAudioFileFormat(fileType, stream);

        //$$fb when we got this far, we are committed to write this file

        // we must know the total data length to calculate the file length
        if( stream.getFrameLength() == AudioSystem.NOT_SPECIFIED ) {
            throw new IOException("stream length not specified");
        }

        int bytesWritten = writeWaveFile(stream, waveFileFormat, out);
        return bytesWritten;
    }


    public int write(AudioInputStream stream, AudioFileFormat.Type fileType, File out) throws IOException {
        Objects.requireNonNull(stream);
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(out);

        // throws IllegalArgumentException if not supported
        WaveFileFormat waveFileFormat = (WaveFileFormat)getAudioFileFormat(fileType, stream);

        // first write the file without worrying about length fields
        FileOutputStream fos = new FileOutputStream( out );     // throws IOException
        BufferedOutputStream bos = new BufferedOutputStream( fos, bisBufferSize );
        int bytesWritten = writeWaveFile(stream, waveFileFormat, bos );
        bos.close();

        // now, if length fields were not specified, calculate them,
        // open as a random access file, write the appropriate fields,
        // close again....
        if( waveFileFormat.getByteLength()== AudioSystem.NOT_SPECIFIED ) {

            int dataLength=bytesWritten-waveFileFormat.getHeaderSize();
            int riffLength=dataLength + waveFileFormat.getHeaderSize() - 8;

            RandomAccessFile raf=new RandomAccessFile(out, "rw");
            // skip RIFF magic
            raf.skipBytes(4);
            raf.writeInt(big2little( riffLength ));
            // skip WAVE magic, fmt_ magic, fmt_ length, fmt_ chunk, data magic
            raf.skipBytes(4+4+4+WaveFileFormat.getFmtChunkSize(waveFileFormat.getWaveType())+4);
            raf.writeInt(big2little( dataLength ));
            // that's all
            raf.close();
        }

        return bytesWritten;
    }

    //--------------------------------------------------------------------

    /**
     * Returns the AudioFileFormat describing the file that will be written from this AudioInputStream.
     * Throws IllegalArgumentException if not supported.
     */
    private AudioFileFormat getAudioFileFormat(AudioFileFormat.Type type, AudioInputStream stream) {
        AudioFormat format = null;
        WaveFileFormat fileFormat = null;
        AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;

        AudioFormat streamFormat = stream.getFormat();
        AudioFormat.Encoding streamEncoding = streamFormat.getEncoding();

        float sampleRate;
        int sampleSizeInBits;
        int channels;
        int frameSize;
        float frameRate;
        int fileSize;

        if (!types[0].equals(type)) {
            throw new IllegalArgumentException("File type " + type + " not supported.");
        }
        int waveType = WaveFileFormat.WAVE_FORMAT_PCM;

        if( AudioFormat.Encoding.ALAW.equals(streamEncoding) ||
            AudioFormat.Encoding.ULAW.equals(streamEncoding) ) {

            encoding = streamEncoding;
            sampleSizeInBits = streamFormat.getSampleSizeInBits();
            if (streamEncoding.equals(AudioFormat.Encoding.ALAW)) {
                waveType = WAVE_FORMAT_ALAW;
            } else {
                waveType = WAVE_FORMAT_MULAW;
            }
        } else if ( streamFormat.getSampleSizeInBits()==8 ) {
            encoding = AudioFormat.Encoding.PCM_UNSIGNED;
            sampleSizeInBits=8;
        } else {
            encoding = AudioFormat.Encoding.PCM_SIGNED;
            sampleSizeInBits=streamFormat.getSampleSizeInBits();
        }


        format = new AudioFormat( encoding,
                                  streamFormat.getSampleRate(),
                                  sampleSizeInBits,
                                  streamFormat.getChannels(),
                                  streamFormat.getFrameSize(),
                                  streamFormat.getFrameRate(),
                                  false);       // WAVE is little endian

        if( stream.getFrameLength()!=AudioSystem.NOT_SPECIFIED ) {
            fileSize = (int)stream.getFrameLength()*streamFormat.getFrameSize()
                + WaveFileFormat.getHeaderSize(waveType);
        } else {
            fileSize = AudioSystem.NOT_SPECIFIED;
        }

        fileFormat = new WaveFileFormat( AudioFileFormat.Type.WAVE,
                                         fileSize,
                                         format,
                                         (int)stream.getFrameLength() );

        return fileFormat;
    }


    private int writeWaveFile(InputStream in, WaveFileFormat waveFileFormat, OutputStream out) throws IOException {

        int bytesRead = 0;
        int bytesWritten = 0;
        InputStream fileStream = getFileStream(waveFileFormat, in);
        byte buffer[] = new byte[bisBufferSize];
        int maxLength = waveFileFormat.getByteLength();

        while( (bytesRead = fileStream.read( buffer )) >= 0 ) {

            if (maxLength>0) {
                if( bytesRead < maxLength ) {
                    out.write( buffer, 0, bytesRead );
                    bytesWritten += bytesRead;
                    maxLength -= bytesRead;
                } else {
                    out.write( buffer, 0, maxLength );
                    bytesWritten += maxLength;
                    maxLength = 0;
                    break;
                }
            } else {
                out.write( buffer, 0, bytesRead );
                bytesWritten += bytesRead;
            }
        }

        return bytesWritten;
    }

    private InputStream getFileStream(WaveFileFormat waveFileFormat, InputStream audioStream) throws IOException {
        // private method ... assumes audioFileFormat is a supported file type

        // WAVE header fields
        AudioFormat audioFormat = waveFileFormat.getFormat();
        int headerLength       = waveFileFormat.getHeaderSize();
        int riffMagic          = WaveFileFormat.RIFF_MAGIC;
        int waveMagic          = WaveFileFormat.WAVE_MAGIC;
        int fmtMagic           = WaveFileFormat.FMT_MAGIC;
        int fmtLength          = WaveFileFormat.getFmtChunkSize(waveFileFormat.getWaveType());
        short wav_type         = (short) waveFileFormat.getWaveType();
        short channels         = (short) audioFormat.getChannels();
        short sampleSizeInBits = (short) audioFormat.getSampleSizeInBits();
        int sampleRate         = (int) audioFormat.getSampleRate();
        int frameSizeInBytes   = audioFormat.getFrameSize();
        int frameRate              = (int) audioFormat.getFrameRate();
        int avgBytesPerSec     = channels * sampleSizeInBits * sampleRate / 8;;
        short blockAlign       = (short) ((sampleSizeInBits / 8) * channels);
        int dataMagic              = WaveFileFormat.DATA_MAGIC;
        int dataLength             = waveFileFormat.getFrameLength() * frameSizeInBytes;
        int length                         = waveFileFormat.getByteLength();
        int riffLength = dataLength + headerLength - 8;

        byte header[] = null;
        ByteArrayInputStream headerStream = null;
        ByteArrayOutputStream baos = null;
        DataOutputStream dos = null;
        SequenceInputStream waveStream = null;

        AudioFormat audioStreamFormat = null;
        AudioFormat.Encoding encoding = null;
        InputStream codedAudioStream = audioStream;

        // if audioStream is an AudioInputStream and we need to convert, do it here...
        if(audioStream instanceof AudioInputStream) {
            audioStreamFormat = ((AudioInputStream)audioStream).getFormat();

            encoding = audioStreamFormat.getEncoding();

            if(AudioFormat.Encoding.PCM_SIGNED.equals(encoding)) {
                if( sampleSizeInBits==8 ) {
                    wav_type = WaveFileFormat.WAVE_FORMAT_PCM;
                    // plug in the transcoder to convert from PCM_SIGNED to PCM_UNSIGNED
                    codedAudioStream = AudioSystem.getAudioInputStream( new AudioFormat(
                                                                                        AudioFormat.Encoding.PCM_UNSIGNED,
                                                                                        audioStreamFormat.getSampleRate(),
                                                                                        audioStreamFormat.getSampleSizeInBits(),
                                                                                        audioStreamFormat.getChannels(),
                                                                                        audioStreamFormat.getFrameSize(),
                                                                                        audioStreamFormat.getFrameRate(),
                                                                                        false),
                                                                        (AudioInputStream)audioStream);
                }
            }
            if( (AudioFormat.Encoding.PCM_SIGNED.equals(encoding) && audioStreamFormat.isBigEndian()) ||
                (AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding) && !audioStreamFormat.isBigEndian()) ||
                (AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding) && audioStreamFormat.isBigEndian()) ) {
                if( sampleSizeInBits!=8) {
                    wav_type = WaveFileFormat.WAVE_FORMAT_PCM;
                    // plug in the transcoder to convert to PCM_SIGNED_LITTLE_ENDIAN
                    codedAudioStream = AudioSystem.getAudioInputStream( new AudioFormat(
                                                                                        AudioFormat.Encoding.PCM_SIGNED,
                                                                                        audioStreamFormat.getSampleRate(),
                                                                                        audioStreamFormat.getSampleSizeInBits(),
                                                                                        audioStreamFormat.getChannels(),
                                                                                        audioStreamFormat.getFrameSize(),
                                                                                        audioStreamFormat.getFrameRate(),
                                                                                        false),
                                                                        (AudioInputStream)audioStream);
                }
            }
        }


        // Now push the header into a stream, concat, and return the new SequenceInputStream

        baos = new ByteArrayOutputStream();
        dos = new DataOutputStream(baos);

        // we write in littleendian...
        dos.writeInt(riffMagic);
        dos.writeInt(big2little( riffLength ));
        dos.writeInt(waveMagic);
        dos.writeInt(fmtMagic);
        dos.writeInt(big2little(fmtLength));
        dos.writeShort(big2littleShort(wav_type));
        dos.writeShort(big2littleShort(channels));
        dos.writeInt(big2little(sampleRate));
        dos.writeInt(big2little(avgBytesPerSec));
        dos.writeShort(big2littleShort(blockAlign));
        dos.writeShort(big2littleShort(sampleSizeInBits));
        //$$fb 2002-04-16: Fix for 4636355: RIFF audio headers could be _more_ spec compliant
        if (wav_type != WaveFileFormat.WAVE_FORMAT_PCM) {
            // add length 0 for "codec specific data length"
            dos.writeShort(0);
        }

        dos.writeInt(dataMagic);
        dos.writeInt(big2little(dataLength));

        dos.close();
        header = baos.toByteArray();
        headerStream = new ByteArrayInputStream( header );
        waveStream = new SequenceInputStream(headerStream,
                            new NoCloseInputStream(codedAudioStream));

        return (InputStream)waveStream;
    }
}
