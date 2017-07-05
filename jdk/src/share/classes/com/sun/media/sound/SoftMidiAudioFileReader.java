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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.spi.AudioFileReader;

/**
 * MIDI File Audio Renderer/Reader
 *
 * @author Karl Helgason
 */
public class SoftMidiAudioFileReader extends AudioFileReader {

    public static final Type MIDI = new Type("MIDI", "mid");
    private static AudioFormat format = new AudioFormat(44100, 16, 2, true, false);

    public AudioFileFormat getAudioFileFormat(Sequence seq)
            throws UnsupportedAudioFileException, IOException {

        long totallen = seq.getMicrosecondLength() / 1000000;
        long len = (long) (format.getFrameRate() * (totallen + 4));
        return new AudioFileFormat(MIDI, format, (int) len);
    }

    public AudioInputStream getAudioInputStream(Sequence seq)
            throws UnsupportedAudioFileException, IOException {
        AudioSynthesizer synth = (AudioSynthesizer) new SoftSynthesizer();
        AudioInputStream stream;
        Receiver recv;
        try {
            stream = synth.openStream(format, null);
            recv = synth.getReceiver();
        } catch (MidiUnavailableException e) {
            throw new IOException(e.toString());
        }
        float divtype = seq.getDivisionType();
        Track[] tracks = seq.getTracks();
        int[] trackspos = new int[tracks.length];
        int mpq = 500000;
        int seqres = seq.getResolution();
        long lasttick = 0;
        long curtime = 0;
        while (true) {
            MidiEvent selevent = null;
            int seltrack = -1;
            for (int i = 0; i < tracks.length; i++) {
                int trackpos = trackspos[i];
                Track track = tracks[i];
                if (trackpos < track.size()) {
                    MidiEvent event = track.get(trackpos);
                    if (selevent == null || event.getTick() < selevent.getTick()) {
                        selevent = event;
                        seltrack = i;
                    }
                }
            }
            if (seltrack == -1)
                break;
            trackspos[seltrack]++;
            long tick = selevent.getTick();
            if (divtype == Sequence.PPQ)
                curtime += ((tick - lasttick) * mpq) / seqres;
            else
                curtime = (long) ((tick * 1000000.0 * divtype) / seqres);
            lasttick = tick;
            MidiMessage msg = selevent.getMessage();
            if (msg instanceof MetaMessage) {
                if (divtype == Sequence.PPQ) {
                    if (((MetaMessage) msg).getType() == 0x51) {
                        byte[] data = ((MetaMessage) msg).getData();
                        mpq = ((data[0] & 0xff) << 16)
                                | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                    }
                }
            } else {
                recv.send(msg, curtime);
            }
        }

        long totallen = curtime / 1000000;
        long len = (long) (stream.getFormat().getFrameRate() * (totallen + 4));
        stream = new AudioInputStream(stream, stream.getFormat(), len);
        return stream;
    }

    public AudioInputStream getAudioInputStream(InputStream inputstream)
            throws UnsupportedAudioFileException, IOException {

        inputstream.mark(200);
        Sequence seq;
        try {
            seq = MidiSystem.getSequence(inputstream);
        } catch (InvalidMidiDataException e) {
            inputstream.reset();
            throw new UnsupportedAudioFileException();
        } catch (IOException e) {
            inputstream.reset();
            throw new UnsupportedAudioFileException();
        }
        return getAudioInputStream(seq);
    }

    public AudioFileFormat getAudioFileFormat(URL url)
            throws UnsupportedAudioFileException, IOException {
        Sequence seq;
        try {
            seq = MidiSystem.getSequence(url);
        } catch (InvalidMidiDataException e) {
            throw new UnsupportedAudioFileException();
        } catch (IOException e) {
            throw new UnsupportedAudioFileException();
        }
        return getAudioFileFormat(seq);
    }

    public AudioFileFormat getAudioFileFormat(File file)
            throws UnsupportedAudioFileException, IOException {
        Sequence seq;
        try {
            seq = MidiSystem.getSequence(file);
        } catch (InvalidMidiDataException e) {
            throw new UnsupportedAudioFileException();
        } catch (IOException e) {
            throw new UnsupportedAudioFileException();
        }
        return getAudioFileFormat(seq);
    }

    public AudioInputStream getAudioInputStream(URL url)
            throws UnsupportedAudioFileException, IOException {
        Sequence seq;
        try {
            seq = MidiSystem.getSequence(url);
        } catch (InvalidMidiDataException e) {
            throw new UnsupportedAudioFileException();
        } catch (IOException e) {
            throw new UnsupportedAudioFileException();
        }
        return getAudioInputStream(seq);
    }

    public AudioInputStream getAudioInputStream(File file)
            throws UnsupportedAudioFileException, IOException {
        if (!file.getName().toLowerCase().endsWith(".mid"))
            throw new UnsupportedAudioFileException();
        Sequence seq;
        try {
            seq = MidiSystem.getSequence(file);
        } catch (InvalidMidiDataException e) {
            throw new UnsupportedAudioFileException();
        } catch (IOException e) {
            throw new UnsupportedAudioFileException();
        }
        return getAudioInputStream(seq);
    }

    public AudioFileFormat getAudioFileFormat(InputStream inputstream)
            throws UnsupportedAudioFileException, IOException {

        inputstream.mark(200);
        Sequence seq;
        try {
            seq = MidiSystem.getSequence(inputstream);
        } catch (InvalidMidiDataException e) {
            inputstream.reset();
            throw new UnsupportedAudioFileException();
        } catch (IOException e) {
            inputstream.reset();
            throw new UnsupportedAudioFileException();
        }
        return getAudioFileFormat(seq);
    }
}
