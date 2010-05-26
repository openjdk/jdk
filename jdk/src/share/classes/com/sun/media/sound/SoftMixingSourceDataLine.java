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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * SourceDataLine implemention for the SoftMixingMixer.
 *
 * @author Karl Helgason
 */
public class SoftMixingSourceDataLine extends SoftMixingDataLine implements
        SourceDataLine {

    private boolean open = false;

    private AudioFormat format = new AudioFormat(44100.0f, 16, 2, true, false);

    private int framesize;

    private int bufferSize = -1;

    private float[] readbuffer;

    private boolean active = false;

    private byte[] cycling_buffer;

    private int cycling_read_pos = 0;

    private int cycling_write_pos = 0;

    private int cycling_avail = 0;

    private long cycling_framepos = 0;

    private AudioFloatInputStream afis;

    private static class NonBlockingFloatInputStream extends
            AudioFloatInputStream {
        AudioFloatInputStream ais;

        public NonBlockingFloatInputStream(AudioFloatInputStream ais) {
            this.ais = ais;
        }

        public int available() throws IOException {
            return ais.available();
        }

        public void close() throws IOException {
            ais.close();
        }

        public AudioFormat getFormat() {
            return ais.getFormat();
        }

        public long getFrameLength() {
            return ais.getFrameLength();
        }

        public void mark(int readlimit) {
            ais.mark(readlimit);
        }

        public boolean markSupported() {
            return ais.markSupported();
        }

        public int read(float[] b, int off, int len) throws IOException {
            int avail = available();
            if (len > avail) {
                int ret = ais.read(b, off, avail);
                Arrays.fill(b, off + ret, off + len, 0);
                return len;
            }
            return ais.read(b, off, len);
        }

        public void reset() throws IOException {
            ais.reset();
        }

        public long skip(long len) throws IOException {
            return ais.skip(len);
        }

    }

    protected SoftMixingSourceDataLine(SoftMixingMixer mixer, DataLine.Info info) {
        super(mixer, info);
    }

    public int write(byte[] b, int off, int len) {
        if (!isOpen())
            return 0;
        if (len % framesize != 0)
            throw new IllegalArgumentException(
                    "Number of bytes does not represent an integral number of sample frames.");

        byte[] buff = cycling_buffer;
        int buff_len = cycling_buffer.length;

        int l = 0;
        while (l != len) {
            int avail;
            synchronized (cycling_buffer) {
                int pos = cycling_write_pos;
                avail = cycling_avail;
                while (l != len) {
                    if (avail == buff_len)
                        break;
                    buff[pos++] = b[off++];
                    l++;
                    avail++;
                    if (pos == buff_len)
                        pos = 0;
                }
                cycling_avail = avail;
                cycling_write_pos = pos;
                if (l == len)
                    return l;
            }
            if (avail == buff_len) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    return l;
                }
                if (!isRunning())
                    return l;
            }
        }

        return l;
    }

    //
    // BooleanControl.Type.APPLY_REVERB
    // BooleanControl.Type.MUTE
    // EnumControl.Type.REVERB
    //
    // FloatControl.Type.SAMPLE_RATE
    // FloatControl.Type.REVERB_SEND
    // FloatControl.Type.VOLUME
    // FloatControl.Type.PAN
    // FloatControl.Type.MASTER_GAIN
    // FloatControl.Type.BALANCE

    private boolean _active = false;

    private AudioFormat outputformat;

    private int out_nrofchannels;

    private int in_nrofchannels;

    private float _rightgain;

    private float _leftgain;

    private float _eff1gain;

    private float _eff2gain;

    protected void processControlLogic() {
        _active = active;
        _rightgain = rightgain;
        _leftgain = leftgain;
        _eff1gain = eff1gain;
        _eff2gain = eff2gain;
    }

    protected void processAudioLogic(SoftAudioBuffer[] buffers) {
        if (_active) {
            float[] left = buffers[SoftMixingMainMixer.CHANNEL_LEFT].array();
            float[] right = buffers[SoftMixingMainMixer.CHANNEL_RIGHT].array();
            int bufferlen = buffers[SoftMixingMainMixer.CHANNEL_LEFT].getSize();

            int readlen = bufferlen * in_nrofchannels;
            if (readbuffer == null || readbuffer.length < readlen) {
                readbuffer = new float[readlen];
            }
            int ret = 0;
            try {
                ret = afis.read(readbuffer);
                if (ret != in_nrofchannels)
                    Arrays.fill(readbuffer, ret, readlen, 0);
            } catch (IOException e) {
            }

            int in_c = in_nrofchannels;
            for (int i = 0, ix = 0; i < bufferlen; i++, ix += in_c) {
                left[i] += readbuffer[ix] * _leftgain;
            }
            if (out_nrofchannels != 1) {
                if (in_nrofchannels == 1) {
                    for (int i = 0, ix = 0; i < bufferlen; i++, ix += in_c) {
                        right[i] += readbuffer[ix] * _rightgain;
                    }
                } else {
                    for (int i = 0, ix = 1; i < bufferlen; i++, ix += in_c) {
                        right[i] += readbuffer[ix] * _rightgain;
                    }
                }

            }

            if (_eff1gain > 0.0001) {
                float[] eff1 = buffers[SoftMixingMainMixer.CHANNEL_EFFECT1]
                        .array();
                for (int i = 0, ix = 0; i < bufferlen; i++, ix += in_c) {
                    eff1[i] += readbuffer[ix] * _eff1gain;
                }
                if (in_nrofchannels == 2) {
                    for (int i = 0, ix = 1; i < bufferlen; i++, ix += in_c) {
                        eff1[i] += readbuffer[ix] * _eff1gain;
                    }
                }
            }

            if (_eff2gain > 0.0001) {
                float[] eff2 = buffers[SoftMixingMainMixer.CHANNEL_EFFECT2]
                        .array();
                for (int i = 0, ix = 0; i < bufferlen; i++, ix += in_c) {
                    eff2[i] += readbuffer[ix] * _eff2gain;
                }
                if (in_nrofchannels == 2) {
                    for (int i = 0, ix = 1; i < bufferlen; i++, ix += in_c) {
                        eff2[i] += readbuffer[ix] * _eff2gain;
                    }
                }
            }

        }
    }

    public void open() throws LineUnavailableException {
        open(format);
    }

    public void open(AudioFormat format) throws LineUnavailableException {
        if (bufferSize == -1)
            bufferSize = ((int) (format.getFrameRate() / 2))
                    * format.getFrameSize();
        open(format, bufferSize);
    }

    public void open(AudioFormat format, int bufferSize)
            throws LineUnavailableException {

        LineEvent event = null;

        if (bufferSize < format.getFrameSize() * 32)
            bufferSize = format.getFrameSize() * 32;

        synchronized (control_mutex) {

            if (!isOpen()) {
                if (!mixer.isOpen()) {
                    mixer.open();
                    mixer.implicitOpen = true;
                }

                event = new LineEvent(this, LineEvent.Type.OPEN, 0);

                this.bufferSize = bufferSize - bufferSize
                        % format.getFrameSize();
                this.format = format;
                this.framesize = format.getFrameSize();
                this.outputformat = mixer.getFormat();
                out_nrofchannels = outputformat.getChannels();
                in_nrofchannels = format.getChannels();

                open = true;

                mixer.getMainMixer().openLine(this);

                cycling_buffer = new byte[framesize * bufferSize];
                cycling_read_pos = 0;
                cycling_write_pos = 0;
                cycling_avail = 0;
                cycling_framepos = 0;

                InputStream cycling_inputstream = new InputStream() {

                    public int read() throws IOException {
                        byte[] b = new byte[1];
                        int ret = read(b);
                        if (ret < 0)
                            return ret;
                        return b[0] & 0xFF;
                    }

                    public int available() throws IOException {
                        synchronized (cycling_buffer) {
                            return cycling_avail;
                        }
                    }

                    public int read(byte[] b, int off, int len)
                            throws IOException {

                        synchronized (cycling_buffer) {
                            if (len > cycling_avail)
                                len = cycling_avail;
                            int pos = cycling_read_pos;
                            byte[] buff = cycling_buffer;
                            int buff_len = buff.length;
                            for (int i = 0; i < len; i++) {
                                b[off++] = buff[pos];
                                pos++;
                                if (pos == buff_len)
                                    pos = 0;
                            }
                            cycling_read_pos = pos;
                            cycling_avail -= len;
                            cycling_framepos += len / framesize;
                        }
                        return len;
                    }

                };

                afis = AudioFloatInputStream
                        .getInputStream(new AudioInputStream(
                                cycling_inputstream, format,
                                AudioSystem.NOT_SPECIFIED));
                afis = new NonBlockingFloatInputStream(afis);

                if (Math.abs(format.getSampleRate()
                        - outputformat.getSampleRate()) > 0.000001)
                    afis = new AudioFloatInputStreamResampler(afis,
                            outputformat);

            } else {
                if (!format.matches(getFormat())) {
                    throw new IllegalStateException(
                            "Line is already open with format " + getFormat()
                                    + " and bufferSize " + getBufferSize());
                }
            }

        }

        if (event != null)
            sendEvent(event);

    }

    public int available() {
        synchronized (cycling_buffer) {
            return cycling_buffer.length - cycling_avail;
        }
    }

    public void drain() {
        while (true) {
            int avail;
            synchronized (cycling_buffer) {
                avail = cycling_avail;
            }
            if (avail != 0)
                return;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void flush() {
        synchronized (cycling_buffer) {
            cycling_read_pos = 0;
            cycling_write_pos = 0;
            cycling_avail = 0;
        }
    }

    public int getBufferSize() {
        synchronized (control_mutex) {
            return bufferSize;
        }
    }

    public AudioFormat getFormat() {
        synchronized (control_mutex) {
            return format;
        }
    }

    public int getFramePosition() {
        return (int) getLongFramePosition();
    }

    public float getLevel() {
        return AudioSystem.NOT_SPECIFIED;
    }

    public long getLongFramePosition() {
        synchronized (cycling_buffer) {
            return cycling_framepos;
        }
    }

    public long getMicrosecondPosition() {
        return (long) (getLongFramePosition() * (1000000.0 / (double) getFormat()
                .getSampleRate()));
    }

    public boolean isActive() {
        synchronized (control_mutex) {
            return active;
        }
    }

    public boolean isRunning() {
        synchronized (control_mutex) {
            return active;
        }
    }

    public void start() {

        LineEvent event = null;

        synchronized (control_mutex) {
            if (isOpen()) {
                if (active)
                    return;
                active = true;
                event = new LineEvent(this, LineEvent.Type.START,
                        getLongFramePosition());
            }
        }

        if (event != null)
            sendEvent(event);
    }

    public void stop() {
        LineEvent event = null;

        synchronized (control_mutex) {
            if (isOpen()) {
                if (!active)
                    return;
                active = false;
                event = new LineEvent(this, LineEvent.Type.STOP,
                        getLongFramePosition());
            }
        }

        if (event != null)
            sendEvent(event);
    }

    public void close() {

        LineEvent event = null;

        synchronized (control_mutex) {
            if (!isOpen())
                return;
            stop();

            event = new LineEvent(this, LineEvent.Type.CLOSE,
                    getLongFramePosition());

            open = false;
            mixer.getMainMixer().closeLine(this);
        }

        if (event != null)
            sendEvent(event);

    }

    public boolean isOpen() {
        synchronized (control_mutex) {
            return open;
        }
    }

}
