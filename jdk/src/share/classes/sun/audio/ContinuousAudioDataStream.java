/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.audio;

/**
 * Create a continuous audio stream. This wraps a stream
 * around an AudioData object, the stream is restarted
 * at the beginning everytime the end is reached, thus
 * creating continuous sound.<p>
 * For example:
 * <pre>
 *   AudioData data = AudioData.getAudioData(url);
 *   ContinuousAudioDataStream audiostream = new ContinuousAudioDataStream(data);
 *   AudioPlayer.player.start(audiostream);
 * </pre>
 *
 * @see AudioPlayer
 * @see AudioData
 * @author Arthur van Hoff
 */

public final class ContinuousAudioDataStream extends AudioDataStream {


    /**
         * Create a continuous stream of audio.
         */
        public ContinuousAudioDataStream(AudioData data) {

            super(data);
        }


        public int read() {

            int i = super.read();

            if (i == -1) {
                reset();
                i = super.read();
            }

            return i;
        }


        public int read(byte ab[], int i1, int j) {

            int k;

            for (k = 0; k < j; ) {
                int i2 = super.read(ab, i1 + k, j - k);
                if (i2 >= 0) k += i2;
                else reset();
            }

            return k;
        }
    }
