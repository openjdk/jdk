/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;

/**
 * Clip implementation for the SoftMixingMixer.
 *
 * @author Karl Helgason
 */
public final class SoftMixingClip extends SoftMixingDataLine implements Clip {

    private AudioFormat format;

    private int framesize;

    private byte[] data;

    private final InputStream datastream = new InputStream() {

        public int read() throws IOException {
            byte[] b = new byte[1];
            int ret = read(b);
            if (ret < 0)
                return ret;
            return b[0] & 0xFF;
        }

        public int read(byte[] b, int off, int len) throws IOException {

            if (_loopcount != 0) {
                int bloopend = _loopend * framesize;
                int bloopstart = _loopstart * framesize;
                int pos = _frameposition * framesize;

                if (pos + len >= bloopend)
                    if (pos < bloopend) {
                        int offend = off + len;
                        int o = off;
                        while (off != offend) {
                            if (pos == bloopend) {
                                if (_loopcount == 0)
                                    break;
                                pos = bloopstart;
                                if (_loopcount != LOOP_CONTINUOUSLY)
                                    _loopcount--;
                            }
                            len = offend - off;
                            int left = bloopend - pos;
                            if (len > left)
                                len = left;
                            System.arraycopy(data, pos, b, off, len);
                            off += len;
                        }
                        if (_loopcount == 0) {
                            len = offend - off;
                            int left = bloopend - pos;
                            if (len > left)
                                len = left;
                            System.arraycopy(data, pos, b, off, len);
                            off += len;
                        }
                        _frameposition = pos / framesize;
                        return o - off;
                    }
            }

            int pos = _frameposition * framesize;
            int left = bufferSize - pos;
            if (left == 0)
                return -1;
            if (len > left)
                len = left;
            System.arraycopy(data, pos, b, off, len);
            _frameposition += len / framesize;
            return len;
        }

    };

    private int offset;

    private int bufferSize;

    private float[] readbuffer;

    private boolean open = false;

    private AudioFormat outputformat;

    private int out_nrofchannels;

    private int in_nrofchannels;

    private int frameposition = 0;

    private boolean frameposition_sg = false;

    private boolean active_sg = false;

    private int loopstart = 0;

    private int loopend = -1;

    private boolean active = false;

    private int loopcount = 0;

    private boolean _active = false;

    private int _frameposition = 0;

    private boolean loop_sg = false;

    private int _loopcount = 0;

    private int _loopstart = 0;

    private int _loopend = -1;

    private float _rightgain;

    private float _leftgain;

    private float _eff1gain;

    private float _eff2gain;

    private AudioFloatInputStream afis;

    SoftMixingClip(SoftMixingMixer mixer, DataLine.Info info) {
        super(mixer, info);
    }

    protected void processControlLogic() {

        _rightgain = rightgain;
        _leftgain = leftgain;
        _eff1gain = eff1gain;
        _eff2gain = eff2gain;

        if (active_sg) {
            _active = active;
            active_sg = false;
        } else {
            active = _active;
        }

        if (frameposition_sg) {
            _frameposition = frameposition;
            frameposition_sg = false;
            afis = null;
        } else {
            frameposition = _frameposition;
        }
        if (loop_sg) {
            _loopcount = loopcount;
            _loopstart = loopstart;
            _loopend = loopend;
        }

        if (afis == null) {
            afis = AudioFloatInputStream.getInputStream(new AudioInputStream(
                    datastream, format, AudioSystem.NOT_SPECIFIED));

            if (Math.abs(format.getSampleRate() - outputformat.getSampleRate()) > 0.000001)
                afis = new AudioFloatInputStreamResampler(afis, outputformat);
        }

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
                if (ret == -1) {
                    _active = false;
                    return;
                }
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

            if (_eff1gain > 0.0002) {

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

            if (_eff2gain > 0.0002) {
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

    public int getFrameLength() {
        return bufferSize / format.getFrameSize();
    }

    public long getMicrosecondLength() {
        return (long) (getFrameLength() * (1000000.0 / (double) getFormat()
                .getSampleRate()));
    }

    public void loop(int count) {
        LineEvent event = null;

        synchronized (control_mutex) {
            if (isOpen()) {
                if (active)
                    return;
                active = true;
                active_sg = true;
                loopcount = count;
                event = new LineEvent(this, LineEvent.Type.START,
                        getLongFramePosition());
            }
        }

        if (event != null)
            sendEvent(event);

    }

    public void open(AudioInputStream stream) throws LineUnavailableException,
            IOException {
        if (isOpen()) {
            throw new IllegalStateException("Clip is already open with format "
                    + getFormat() + " and frame lengh of " + getFrameLength());
        }
        if (AudioFloatConverter.getConverter(stream.getFormat()) == null)
            throw new IllegalArgumentException("Invalid format : "
                    + stream.getFormat().toString());

        if (stream.getFrameLength() != AudioSystem.NOT_SPECIFIED) {
            byte[] data = new byte[(int) stream.getFrameLength()
                    * stream.getFormat().getFrameSize()];
            int readsize = 512 * stream.getFormat().getFrameSize();
            int len = 0;
            while (len != data.length) {
                if (readsize > data.length - len)
                    readsize = data.length - len;
                int ret = stream.read(data, len, readsize);
                if (ret == -1)
                    break;
                if (ret == 0)
                    Thread.yield();
                len += ret;
            }
            open(stream.getFormat(), data, 0, len);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] b = new byte[512 * stream.getFormat().getFrameSize()];
            int r = 0;
            while ((r = stream.read(b)) != -1) {
                if (r == 0)
                    Thread.yield();
                baos.write(b, 0, r);
            }
            open(stream.getFormat(), baos.toByteArray(), 0, baos.size());
        }

    }

    public void open(AudioFormat format, byte[] data, int offset, int bufferSize)
            throws LineUnavailableException {
        synchronized (control_mutex) {
            if (isOpen()) {
                throw new IllegalStateException(
                        "Clip is already open with format " + getFormat()
                                + " and frame lengh of " + getFrameLength());
            }
            if (AudioFloatConverter.getConverter(format) == null)
                throw new IllegalArgumentException("Invalid format : "
                        + format.toString());
            if (bufferSize % format.getFrameSize() != 0)
                throw new IllegalArgumentException(
                        "Buffer size does not represent an integral number of sample frames!");

            if (data != null) {
                this.data = Arrays.copyOf(data, data.length);
            }
            this.offset = offset;
            this.bufferSize = bufferSize;
            this.format = format;
            this.framesize = format.getFrameSize();

            loopstart = 0;
            loopend = -1;
            loop_sg = true;

            if (!mixer.isOpen()) {
                mixer.open();
                mixer.implicitOpen = true;
            }

            outputformat = mixer.getFormat();
            out_nrofchannels = outputformat.getChannels();
            in_nrofchannels = format.getChannels();

            open = true;

            mixer.getMainMixer().openLine(this);
        }

    }

    public void setFramePosition(int frames) {
        synchronized (control_mutex) {
            frameposition_sg = true;
            frameposition = frames;
        }
    }

    public void setLoopPoints(int start, int end) {
        synchronized (control_mutex) {
            if (end != -1) {
                if (end < start)
                    throw new IllegalArgumentException("Invalid loop points : "
                            + start + " - " + end);
                if (end * framesize > bufferSize)
                    throw new IllegalArgumentException("Invalid loop points : "
                            + start + " - " + end);
            }
            if (start * framesize > bufferSize)
                throw new IllegalArgumentException("Invalid loop points : "
                        + start + " - " + end);
            if (0 < start)
                throw new IllegalArgumentException("Invalid loop points : "
                        + start + " - " + end);
            loopstart = start;
            loopend = end;
            loop_sg = true;
        }
    }

    public void setMicrosecondPosition(long microseconds) {
        setFramePosition((int) (microseconds * (((double) getFormat()
                .getSampleRate()) / 1000000.0)));
    }

    public int available() {
        return 0;
    }

    public void drain() {
    }

    public void flush() {
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public int getFramePosition() {
        synchronized (control_mutex) {
            return frameposition;
        }
    }

    public float getLevel() {
        return AudioSystem.NOT_SPECIFIED;
    }

    public long getLongFramePosition() {
        return getFramePosition();
    }

    public long getMicrosecondPosition() {
        return (long) (getFramePosition() * (1000000.0 / (double) getFormat()
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
                active_sg = true;
                loopcount = 0;
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
                active_sg = true;
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
        return open;
    }

    public void open() throws LineUnavailableException {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Illegal call to open() in interface Clip");
        }
        open(format, data, offset, bufferSize);
    }

}
