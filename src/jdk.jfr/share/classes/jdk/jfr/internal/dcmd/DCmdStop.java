/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.dcmd;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

import jdk.jfr.Recording;
import jdk.jfr.internal.SecuritySupport.SafePath;

/**
 * JFR.stop
 *
 */
//Instantiated by native
final class DCmdStop extends AbstractDCmd {

    /**
     * Execute JFR.stop
     *
     * Requires that either <code>name or <code>id</code> is set.
     *
     * @param recordingText name or id of the recording to stop.
     *
     * @param textPath file path where data should be written after recording
     *        has been stopped, or <code>null</code> if recording shouldn't be
     *        written to disk.
     * @return result text
     *
     * @throws DCmdException if recording could not be stopped
     */
    public String execute(String recordingText, String textPath) throws DCmdException {
        try {
            SafePath path = resolvePath(textPath, "Failed to stop %s");
            Recording recording = findRecording(recordingText);
            if (textPath != null) {
                try {
                    recording.setDestination(Paths.get(textPath));
                } catch (IOException e) {
                    throw new DCmdException("Failed to stop %s. Could not set destination for \"%s\" to file %s", recording.getName(), textPath, e.getMessage());
                }
            }
            recording.stop();
            reportOperationComplete("Stopped", recording, path);
            recording.close();
            return getResult();
        } catch (InvalidPathException | DCmdException e) {
            if (textPath != null) {
                throw new DCmdException("Could not write recording \"%s\" to file. %s", recordingText, e.getMessage());
            }
            throw new DCmdException(e, "Could not stop recording \"%s\".", recordingText, e.getMessage());
        }
    }
}
