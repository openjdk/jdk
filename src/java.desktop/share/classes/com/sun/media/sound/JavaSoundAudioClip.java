/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import javax.sound.midi.Sequencer;
import javax.sound.SoundClip;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

/**
 * Java Sound audio clip;
 *
 */
public final class JavaSoundAudioClip {

    public static JavaSoundAudioClip create(final File file) throws IOException {
        return new JavaSoundAudioClip(file);
    }

    /* Used [only] by sun.awt.www.content.MultiMediaContentHandlers */
    public static SoundClip create(final URLConnection uc) {
        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("javaurl", ".aud");
        } catch (IOException e) {
            return null;
        }

        try (InputStream in = uc.getInputStream();
             FileOutputStream out = new FileOutputStream(tmpFile)) {
             in.transferTo(out);
        } catch (IOException e) {
        }

        try {
             return SoundClip.createSoundClip(tmpFile);
        } catch (IOException e) {
        } finally {
            tmpFile.delete();
        }
        return null;
    }

    private JavaSoundAudioClipDelegate delegate;

    public JavaSoundAudioClip(final File file) throws IOException {
        disposerRecord = new AudioClipDisposerRecord();
        Disposer.addRecord(this, disposerRecord);
        delegate = new JavaSoundAudioClipDelegate(file, disposerRecord);
    }

    public synchronized boolean canPlay() {
        return delegate.canPlay();
    }

    public synchronized boolean isPlaying() {
        return delegate.isPlaying();
    }

    public synchronized void play() {
        delegate.play();
    }

    public synchronized void loop() {
        delegate.loop();
    }

    public synchronized void stop() {
        delegate.stop();
    }

    @Override
    public String toString() {
        return getClass().toString();
    }

    private AudioClipDisposerRecord disposerRecord;
    static class AudioClipDisposerRecord implements DisposerRecord {

        private volatile AutoClosingClip clip;
        private volatile DataPusher datapusher;
        private volatile Sequencer sequencer;

        void setClip(AutoClosingClip clip) {
            this.clip = clip;
        }

        void setDataPusher(DataPusher datapusher) {
            this.datapusher = datapusher;
        }

        void setSequencer(Sequencer sequencer) {
            this.sequencer = sequencer;
        }

        public void dispose() {
            if (clip != null) {
                clip.close();
            }

            if (datapusher != null) {
                datapusher.close();
            }

            if (sequencer != null) {
                sequencer.close();
            }
        }
    }
}
