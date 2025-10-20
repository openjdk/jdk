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
 * <p>
 * This class is intended for easy playback of short clips or snippets of sound.
 * Examples of when this might be used is to play an audible alert or effect in a UI app,
 * or to make a short announcement or to provide audible feedback such announcing as the
 * function of a button or control.
 * The application will typically let such clips play once to completion.
 * <p>
 * Applications needing more precise control or advanced
 * features should look into other parts of the {@code javax.sound} API.
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
     * <p>
     * The file contents will be fully read before this method returns.
     * If the file does not contain recognizable and supported sound data, or
     * if the implementation does not find a suitable output device for the data,
     * playing the clip will be a no-op.
     *
     * @param file the file from which to obtain the sound data
     * @return a {@code SoundClip}
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws IOException if there is an error reading from {@code file}
     */
    public static SoundClip createSoundClip(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("file must not be null");
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
    public boolean isPlaying() {
        return clip.isPlaying();
    }

    /**
     * Starts playing this sound clip.
     * Each time this method is called, the clip is restarted from the beginning.
     * This method will return immediately whether or not sound is played,
     * and possibly before the sound has started playing.
     * <p>
     * Threading notes : Most applications will not need to do anything except call {@code play()}.
     * The following is therefore something most applications need not be concerned about.
     * Play back is managed in a background thread, which is usually a daemon thread.
     * Running daemon threads do not prevent the VM from exiting.
     * So at least one thread must be alive to prevent the VM from terminating.
     * A UI application with any window displayed automatically satisfies this requirement.
     * Conversely, if the application wants to guarantee VM exit before the play() has completed,
     * it should call the {@code stop()} method.
     */
    public void play() {
        clip.play();
    }

    /**
     * Starts playing this sound clip in a loop.
     * Each time this method is called, the clip is restarted from the beginning.
     * This method will return immediately whether or not sound is played,
     * and possibly before the sound has started playing.
     * <p>
     * Threading notes : Most applications will not need to do anything except call {@code loop()}.
     * The following is therefore something most applications need not be concerned about.
     * Play back is managed in a background thread, which is ususally a daemon thread.
     * Running daemon threads do not prevent the VM from exiting.
     * So at least one thread must be alive to prevent the VM from terminating.
     * A UI application with any window displayed automatically satisfies this requirement.
     * Conversely, if the application wants to guarantee VM exit before the play() has completed,
     * it should call the {@code stop()} method.
     */
    public void loop() {
        clip.loop();
    }

    /**
     * Stops playing this sound clip.
     * Call this if the clip is playing and the application needs to stop
     * it early, for example so that the application can ensure the clip
     * playing does not block exit. It is also required to stop a {@code loop()}.
     */
    public void stop() {
        clip.stop();
    }
}
