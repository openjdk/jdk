/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.OutputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.spi.AudioFileWriter;

/**
 * Floating-point encoded (format 3) WAVE file writer.
 *
 * @author Karl Helgason
 */
public class WaveFloatFileWriter extends AudioFileWriter {

    public Type[] getAudioFileTypes() {
        return new Type[] { Type.WAVE };
    }

    public Type[] getAudioFileTypes(AudioInputStream stream) {

        if (!stream.getFormat().getEncoding().equals(
                AudioFloatConverter.PCM_FLOAT))
            return new Type[0];
        return new Type[] { Type.WAVE };
    }

    private void checkFormat(AudioFileFormat.Type type, AudioInputStream stream) {
        if (!Type.WAVE.equals(type))
            throw new IllegalArgumentException("File type " + type
                    + " not supported.");
        if (!stream.getFormat().getEncoding().equals(
                AudioFloatConverter.PCM_FLOAT))
            throw new IllegalArgumentException("File format "
                    + stream.getFormat() + " not supported.");
    }

    public void write(AudioInputStream stream, RIFFWriter writer)
            throws IOException {

        RIFFWriter fmt_chunk = writer.writeChunk("fmt ");

        AudioFormat format = stream.getFormat();
        fmt_chunk.writeUnsignedShort(3); // WAVE_FORMAT_IEEE_FLOAT
        fmt_chunk.writeUnsignedShort(format.getChannels());
        fmt_chunk.writeUnsignedInt((int) format.getSampleRate());
        fmt_chunk.writeUnsignedInt(((int) format.getFrameRate())
                * format.getFrameSize());
        fmt_chunk.writeUnsignedShort(format.getFrameSize());
        fmt_chunk.writeUnsignedShort(format.getSampleSizeInBits());
        fmt_chunk.close();
        RIFFWriter data_chunk = writer.writeChunk("data");
        byte[] buff = new byte[1024];
        int len;
        while ((len = stream.read(buff, 0, buff.length)) != -1)
            data_chunk.write(buff, 0, len);
        data_chunk.close();
    }

    private static class NoCloseOutputStream extends OutputStream {
        OutputStream out;

        public NoCloseOutputStream(OutputStream out) {
            this.out = out;
        }

        public void write(int b) throws IOException {
            out.write(b);
        }

        public void flush() throws IOException {
            out.flush();
        }

        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        public void write(byte[] b) throws IOException {
            out.write(b);
        }
    }

    private AudioInputStream toLittleEndian(AudioInputStream ais) {
        AudioFormat format = ais.getFormat();
        AudioFormat targetFormat = new AudioFormat(format.getEncoding(), format
                .getSampleRate(), format.getSampleSizeInBits(), format
                .getChannels(), format.getFrameSize(), format.getFrameRate(),
                false);
        return AudioSystem.getAudioInputStream(targetFormat, ais);
    }

    public int write(AudioInputStream stream, Type fileType, OutputStream out)
            throws IOException {

        checkFormat(fileType, stream);
        if (stream.getFormat().isBigEndian())
            stream = toLittleEndian(stream);
        RIFFWriter writer = new RIFFWriter(new NoCloseOutputStream(out), "WAVE");
        write(stream, writer);
        int fpointer = (int) writer.getFilePointer();
        writer.close();
        return fpointer;
    }

    public int write(AudioInputStream stream, Type fileType, File out)
            throws IOException {
        checkFormat(fileType, stream);
        if (stream.getFormat().isBigEndian())
            stream = toLittleEndian(stream);
        RIFFWriter writer = new RIFFWriter(out, "WAVE");
        write(stream, writer);
        int fpointer = (int) writer.getFilePointer();
        writer.close();
        return fpointer;
    }

}
