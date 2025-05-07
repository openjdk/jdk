/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound;

import java.io.File;
import java.io.IOException;

import com.sun.media.sound.JavaSoundAudioClip;

/**
 * The {@code SoundClip} class is a simple abstraction for playing a sound clip.
 * It will play any format that is recognized by the {@code javax.sound} API,
 * and for which it has support. This includes midi data.
 * Playing sound requires that the environment grants access to audio devices.
 * Typically this means running the application in a desktop environment.
 * <p>
 * Multiple {@code SoundClip} items can be playing at the same time, and
 * the resulting sound is mixed together to produce a composite.
 *
 * @since 25
 */
public final class SoundClip {

    private final JavaSoundAudioClip clip;

    /**
     * Creates a {@code SoundClip} instance which will play a clip from the supplied file.
     *
     * If the file does not contain recognizable and supported sound data, or
     * if the implementation does not find a suitable output device for the data,
     * playing the clip will be a no-op.
     *
     * @param file the file from which to obtain the sound data
     * @return a {@code SoundClip}
     * @throws IllegalArgumentException if {@code file} is {@code null}
     * @throws IOException if there is an error reading from {@code file}
     */
    public static SoundClip createSoundClip(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        return new SoundClip(file);
    }

    private SoundClip(File file) throws IOException {
        this.clip = JavaSoundAudioClip.create(file);
    }

    /**
     * {@return whether this is a playable sound clip}
     * <p>
     * A value of {@code false} means that calling any of the other methods
     * of this class is a no-op.
     */
    public boolean canPlay() {
        return clip.canPlay();
    }

    /**
     * {@return whether sound is currently playing}
     */
    public synchronized boolean isPlaying() {
        return clip.isPlaying();
    }

    /**
     * Starts playing this sound clip.
     * Each time this method is called, the clip is restarted from the beginning.
     */
    public synchronized void play() {
        clip.play();
    }

    /**
     * Starts playing this sound clip in a loop.
     * Each time this method is called, the clip is restarted from the beginning.
     */
    public synchronized void loop() {
        clip.loop();
    }

    /**
     * Stops playing this sound clip.
     */
    public synchronized void stop() {
        clip.stop();
    }
}
