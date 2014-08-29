/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled.spi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static javax.sound.sampled.AudioFileFormat.Type;

/**
 * Provider for audio file writing services. Classes providing concrete
 * implementations can write one or more types of audio file from an audio
 * stream.
 *
 * @author Kara Kytle
 * @since 1.3
 */
public abstract class AudioFileWriter {

    /**
     * Obtains the file types for which file writing support is provided by this
     * audio file writer.
     *
     * @return array of file types. If no file types are supported, an array of
     *         length 0 is returned.
     */
    public abstract Type[] getAudioFileTypes();

    /**
     * Indicates whether file writing support for the specified file type is
     * provided by this audio file writer.
     *
     * @param  fileType the file type for which write capabilities are queried
     * @return {@code true} if the file type is supported, otherwise
     *         {@code false}
     */
    public boolean isFileTypeSupported(Type fileType) {

        Type types[] = getAudioFileTypes();

        for(int i=0; i<types.length; i++) {
            if( fileType.equals( types[i] ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtains the file types that this audio file writer can write from the
     * audio input stream specified.
     *
     * @param  stream the audio input stream for which audio file type support
     *         is queried
     * @return array of file types. If no file types are supported, an array of
     *         length 0 is returned.
     */
    public abstract Type[] getAudioFileTypes(AudioInputStream stream);

    /**
     * Indicates whether an audio file of the type specified can be written from
     * the audio input stream indicated.
     *
     * @param  fileType file type for which write capabilities are queried
     * @param  stream for which file writing support is queried
     * @return {@code true} if the file type is supported for this audio input
     *         stream, otherwise {@code false}
     */
    public boolean isFileTypeSupported(Type fileType, AudioInputStream stream) {

        Type types[] = getAudioFileTypes( stream );

        for(int i=0; i<types.length; i++) {
            if( fileType.equals( types[i] ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes a stream of bytes representing an audio file of the file type
     * indicated to the output stream provided. Some file types require that
     * the length be written into the file header, and cannot be written from
     * start to finish unless the length is known in advance. An attempt to
     * write such a file type will fail with an IOException if the length in the
     * audio file format is {@link AudioSystem#NOT_SPECIFIED}.
     *
     * @param  stream the audio input stream containing audio data to be written
     *         to the output stream
     * @param  fileType file type to be written to the output stream
     * @param  out stream to which the file data should be written
     * @return the number of bytes written to the output stream
     * @throws IOException if an I/O exception occurs
     * @throws IllegalArgumentException if the file type is not supported by the
     *         system
     * @see #isFileTypeSupported(Type, AudioInputStream)
     * @see #getAudioFileTypes
     */
    public abstract int write(AudioInputStream stream, Type fileType,
                              OutputStream out) throws IOException;

    /**
     * Writes a stream of bytes representing an audio file of the file format
     * indicated to the external file provided.
     *
     * @param  stream the audio input stream containing audio data to be written
     *         to the file
     * @param  fileType file type to be written to the file
     * @param  out external file to which the file data should be written
     * @return the number of bytes written to the file
     * @throws IOException if an I/O exception occurs
     * @throws IllegalArgumentException if the file format is not supported by
     *         the system
     * @see #isFileTypeSupported
     * @see #getAudioFileTypes
     */
    public abstract int write(AudioInputStream stream, Type fileType, File out)
            throws IOException;
}
