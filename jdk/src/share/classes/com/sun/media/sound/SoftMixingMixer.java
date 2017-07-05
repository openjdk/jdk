/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.media.sound;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.Control.Type;

/**
 * Software audio mixer
 *
 * @author Karl Helgason
 */
public class SoftMixingMixer implements Mixer {

    private static class Info extends Mixer.Info {
        public Info() {
            super(INFO_NAME, INFO_VENDOR, INFO_DESCRIPTION, INFO_VERSION);
        }
    }

    protected static final String INFO_NAME = "Gervill Sound Mixer";

    protected static final String INFO_VENDOR = "OpenJDK Proposal";

    protected static final String INFO_DESCRIPTION = "Software Sound Mixer";

    protected static final String INFO_VERSION = "1.0";

    protected final static Mixer.Info info = new Info();

    protected Object control_mutex = this;

    protected boolean implicitOpen = false;

    private boolean open = false;

    private SoftMixingMainMixer mainmixer = null;

    private AudioFormat format = new AudioFormat(44100, 16, 2, true, false);

    private SourceDataLine sourceDataLine = null;

    private SoftAudioPusher pusher = null;

    private AudioInputStream pusher_stream = null;

    private float controlrate = 147f;

    private long latency = 100000; // 100 msec

    private boolean jitter_correction = false;

    private List<LineListener> listeners = new ArrayList<LineListener>();

    private javax.sound.sampled.Line.Info[] sourceLineInfo;

    public SoftMixingMixer() {

        sourceLineInfo = new javax.sound.sampled.Line.Info[2];

        ArrayList<AudioFormat> formats = new ArrayList<AudioFormat>();
        for (int channels = 1; channels <= 2; channels++) {
            formats.add(new AudioFormat(Encoding.PCM_SIGNED,
                    AudioSystem.NOT_SPECIFIED, 8, channels, channels,
                    AudioSystem.NOT_SPECIFIED, false));
            formats.add(new AudioFormat(Encoding.PCM_UNSIGNED,
                    AudioSystem.NOT_SPECIFIED, 8, channels, channels,
                    AudioSystem.NOT_SPECIFIED, false));
            for (int bits = 16; bits < 32; bits += 8) {
                formats.add(new AudioFormat(Encoding.PCM_SIGNED,
                        AudioSystem.NOT_SPECIFIED, bits, channels, channels
                                * bits / 8, AudioSystem.NOT_SPECIFIED, false));
                formats.add(new AudioFormat(Encoding.PCM_UNSIGNED,
                        AudioSystem.NOT_SPECIFIED, bits, channels, channels
                                * bits / 8, AudioSystem.NOT_SPECIFIED, false));
                formats.add(new AudioFormat(Encoding.PCM_SIGNED,
                        AudioSystem.NOT_SPECIFIED, bits, channels, channels
                                * bits / 8, AudioSystem.NOT_SPECIFIED, true));
                formats.add(new AudioFormat(Encoding.PCM_UNSIGNED,
                        AudioSystem.NOT_SPECIFIED, bits, channels, channels
                                * bits / 8, AudioSystem.NOT_SPECIFIED, true));
            }
            formats.add(new AudioFormat(AudioFloatConverter.PCM_FLOAT,
                    AudioSystem.NOT_SPECIFIED, 32, channels, channels * 4,
                    AudioSystem.NOT_SPECIFIED, false));
            formats.add(new AudioFormat(AudioFloatConverter.PCM_FLOAT,
                    AudioSystem.NOT_SPECIFIED, 32, channels, channels * 4,
                    AudioSystem.NOT_SPECIFIED, true));
            formats.add(new AudioFormat(AudioFloatConverter.PCM_FLOAT,
                    AudioSystem.NOT_SPECIFIED, 64, channels, channels * 8,
                    AudioSystem.NOT_SPECIFIED, false));
            formats.add(new AudioFormat(AudioFloatConverter.PCM_FLOAT,
                    AudioSystem.NOT_SPECIFIED, 64, channels, channels * 8,
                    AudioSystem.NOT_SPECIFIED, true));
        }
        AudioFormat[] formats_array = formats.toArray(new AudioFormat[formats
                .size()]);
        sourceLineInfo[0] = new DataLine.Info(SourceDataLine.class,
                formats_array, AudioSystem.NOT_SPECIFIED,
                AudioSystem.NOT_SPECIFIED);
        sourceLineInfo[1] = new DataLine.Info(Clip.class, formats_array,
                AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED);
    }

    public Line getLine(Line.Info info) throws LineUnavailableException {

        if (!isLineSupported(info))
            throw new IllegalArgumentException("Line unsupported: " + info);

        if ((info.getLineClass() == SourceDataLine.class)) {
            return new SoftMixingSourceDataLine(this, (DataLine.Info) info);
        }
        if ((info.getLineClass() == Clip.class)) {
            return new SoftMixingClip(this, (DataLine.Info) info);
        }

        throw new IllegalArgumentException("Line unsupported: " + info);
    }

    public int getMaxLines(Line.Info info) {
        if (info.getLineClass() == SourceDataLine.class)
            return AudioSystem.NOT_SPECIFIED;
        if (info.getLineClass() == Clip.class)
            return AudioSystem.NOT_SPECIFIED;
        return 0;
    }

    public javax.sound.sampled.Mixer.Info getMixerInfo() {
        return info;
    }

    public javax.sound.sampled.Line.Info[] getSourceLineInfo() {
        Line.Info[] localArray = new Line.Info[sourceLineInfo.length];
        System.arraycopy(sourceLineInfo, 0, localArray, 0,
                sourceLineInfo.length);
        return localArray;
    }

    public javax.sound.sampled.Line.Info[] getSourceLineInfo(
            javax.sound.sampled.Line.Info info) {
        int i;
        ArrayList<javax.sound.sampled.Line.Info> infos = new ArrayList<javax.sound.sampled.Line.Info>();

        for (i = 0; i < sourceLineInfo.length; i++) {
            if (info.matches(sourceLineInfo[i])) {
                infos.add(sourceLineInfo[i]);
            }
        }
        return infos.toArray(new Line.Info[infos.size()]);
    }

    public Line[] getSourceLines() {

        Line[] localLines;

        synchronized (control_mutex) {

            if (mainmixer == null)
                return new Line[0];
            SoftMixingDataLine[] sourceLines = mainmixer.getOpenLines();

            localLines = new Line[sourceLines.length];

            for (int i = 0; i < localLines.length; i++) {
                localLines[i] = sourceLines[i];
            }
        }

        return localLines;
    }

    public javax.sound.sampled.Line.Info[] getTargetLineInfo() {
        return new javax.sound.sampled.Line.Info[0];
    }

    public javax.sound.sampled.Line.Info[] getTargetLineInfo(
            javax.sound.sampled.Line.Info info) {
        return new javax.sound.sampled.Line.Info[0];
    }

    public Line[] getTargetLines() {
        return new Line[0];
    }

    public boolean isLineSupported(javax.sound.sampled.Line.Info info) {
        if (info != null) {
            for (int i = 0; i < sourceLineInfo.length; i++) {
                if (info.matches(sourceLineInfo[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) {
        return false;
    }

    public void synchronize(Line[] lines, boolean maintainSync) {
        throw new IllegalArgumentException(
                "Synchronization not supported by this mixer.");
    }

    public void unsynchronize(Line[] lines) {
        throw new IllegalArgumentException(
                "Synchronization not supported by this mixer.");
    }

    public void addLineListener(LineListener listener) {
        synchronized (control_mutex) {
            listeners.add(listener);
        }
    }

    private void sendEvent(LineEvent event) {
        if (listeners.size() == 0)
            return;
        LineListener[] listener_array = listeners
                .toArray(new LineListener[listeners.size()]);
        for (LineListener listener : listener_array) {
            listener.update(event);
        }
    }

    public void close() {
        if (!isOpen())
            return;

        sendEvent(new LineEvent(this, LineEvent.Type.CLOSE,
                AudioSystem.NOT_SPECIFIED));

        SoftAudioPusher pusher_to_be_closed = null;
        AudioInputStream pusher_stream_to_be_closed = null;
        synchronized (control_mutex) {
            if (pusher != null) {
                pusher_to_be_closed = pusher;
                pusher_stream_to_be_closed = pusher_stream;
                pusher = null;
                pusher_stream = null;
            }
        }

        if (pusher_to_be_closed != null) {
            // Pusher must not be closed synchronized against control_mutex
            // this may result in synchronized conflict between pusher and
            // current thread.
            pusher_to_be_closed.stop();

            try {
                pusher_stream_to_be_closed.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        synchronized (control_mutex) {

            if (mainmixer != null)
                mainmixer.close();
            open = false;

            if (sourceDataLine != null) {
                sourceDataLine.drain();
                sourceDataLine.close();
                sourceDataLine = null;
            }

        }

    }

    public Control getControl(Type control) {
        throw new IllegalArgumentException("Unsupported control type : "
                + control);
    }

    public Control[] getControls() {
        return new Control[0];
    }

    public javax.sound.sampled.Line.Info getLineInfo() {
        return new Line.Info(Mixer.class);
    }

    public boolean isControlSupported(Type control) {
        return false;
    }

    public boolean isOpen() {
        synchronized (control_mutex) {
            return open;
        }
    }

    public void open() throws LineUnavailableException {
        if (isOpen()) {
            implicitOpen = false;
            return;
        }
        open(null);
    }

    public void open(SourceDataLine line) throws LineUnavailableException {
        if (isOpen()) {
            implicitOpen = false;
            return;
        }
        synchronized (control_mutex) {

            try {

                if (line != null)
                    format = line.getFormat();

                AudioInputStream ais = openStream(getFormat());

                if (line == null) {
                    synchronized (SoftMixingMixerProvider.mutex) {
                        SoftMixingMixerProvider.lockthread = Thread
                                .currentThread();
                    }

                    try {
                        Mixer defaultmixer = AudioSystem.getMixer(null);
                        if (defaultmixer != null)
                        {
                            // Search for suitable line

                            DataLine.Info idealinfo = null;
                            AudioFormat idealformat = null;

                            Line.Info[] lineinfos = defaultmixer.getSourceLineInfo();
                            idealFound:
                            for (int i = 0; i < lineinfos.length; i++) {
                                if(lineinfos[i].getLineClass() == SourceDataLine.class)
                                {
                                    DataLine.Info info = (DataLine.Info)lineinfos[i];
                                    AudioFormat[] formats = info.getFormats();
                                    for (int j = 0; j < formats.length; j++) {
                                        AudioFormat format = formats[j];
                                        if(format.getChannels() == 2 ||
                                                format.getChannels() == AudioSystem.NOT_SPECIFIED)
                                        if(format.getEncoding().equals(Encoding.PCM_SIGNED) ||
                                                format.getEncoding().equals(Encoding.PCM_UNSIGNED))
                                        if(format.getSampleRate() == AudioSystem.NOT_SPECIFIED ||
                                                format.getSampleRate() == 48000.0)
                                        if(format.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED ||
                                                format.getSampleSizeInBits() == 16)
                                        {
                                            idealinfo = info;
                                            int ideal_channels = format.getChannels();
                                            boolean ideal_signed = format.getEncoding().equals(Encoding.PCM_SIGNED);
                                            float ideal_rate = format.getSampleRate();
                                            boolean ideal_endian = format.isBigEndian();
                                            int ideal_bits = format.getSampleSizeInBits();
                                            if(ideal_bits == AudioSystem.NOT_SPECIFIED) ideal_bits = 16;
                                            if(ideal_channels == AudioSystem.NOT_SPECIFIED) ideal_channels = 2;
                                            if(ideal_rate == AudioSystem.NOT_SPECIFIED) ideal_rate = 48000;
                                            idealformat = new AudioFormat(ideal_rate, ideal_bits,
                                                    ideal_channels, ideal_signed, ideal_endian);
                                            break idealFound;
                                        }
                                    }
                                }
                            }

                            if(idealformat != null)
                            {
                                format = idealformat;
                                line = (SourceDataLine) defaultmixer.getLine(idealinfo);
                            }
                        }

                        if(line == null)
                            line = AudioSystem.getSourceDataLine(format);
                    } finally {
                        synchronized (SoftMixingMixerProvider.mutex) {
                            SoftMixingMixerProvider.lockthread = null;
                        }
                    }

                    if (line == null)
                        throw new IllegalArgumentException("No line matching "
                                + info.toString() + " is supported.");
                }

                double latency = this.latency;

                if (!line.isOpen()) {
                    int bufferSize = getFormat().getFrameSize()
                            * (int) (getFormat().getFrameRate() * (latency / 1000000f));
                    line.open(getFormat(), bufferSize);

                    // Remember that we opened that line
                    // so we can close again in SoftSynthesizer.close()
                    sourceDataLine = line;
                }
                if (!line.isActive())
                    line.start();

                int controlbuffersize = 512;
                try {
                    controlbuffersize = ais.available();
                } catch (IOException e) {
                }

                // Tell mixer not fill read buffers fully.
                // This lowers latency, and tells DataPusher
                // to read in smaller amounts.
                // mainmixer.readfully = false;
                // pusher = new DataPusher(line, ais);

                int buffersize = line.getBufferSize();
                buffersize -= buffersize % controlbuffersize;

                if (buffersize < 3 * controlbuffersize)
                    buffersize = 3 * controlbuffersize;

                if (jitter_correction) {
                    ais = new SoftJitterCorrector(ais, buffersize,
                            controlbuffersize);
                }
                pusher = new SoftAudioPusher(line, ais, controlbuffersize);
                pusher_stream = ais;
                pusher.start();

            } catch (LineUnavailableException e) {
                if (isOpen())
                    close();
                throw new LineUnavailableException(e.toString());
            }

        }
    }

    public AudioInputStream openStream(AudioFormat targetFormat)
            throws LineUnavailableException {

        if (isOpen())
            throw new LineUnavailableException("Mixer is already open");

        synchronized (control_mutex) {

            open = true;

            implicitOpen = false;

            if (targetFormat != null)
                format = targetFormat;

            mainmixer = new SoftMixingMainMixer(this);

            sendEvent(new LineEvent(this, LineEvent.Type.OPEN,
                    AudioSystem.NOT_SPECIFIED));

            return mainmixer.getInputStream();

        }

    }

    public void removeLineListener(LineListener listener) {
        synchronized (control_mutex) {
            listeners.remove(listener);
        }
    }

    public long getLatency() {
        synchronized (control_mutex) {
            return latency;
        }
    }

    public AudioFormat getFormat() {
        synchronized (control_mutex) {
            return format;
        }
    }

    protected float getControlRate() {
        return controlrate;
    }

    protected SoftMixingMainMixer getMainMixer() {
        if (!isOpen())
            return null;
        return mainmixer;
    }

}
