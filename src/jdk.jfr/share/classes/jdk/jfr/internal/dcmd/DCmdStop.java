/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.SecuritySupport.SafePath;

/**
 * JFR.stop
 *
 */
// Instantiated by native
final class DCmdStop extends AbstractDCmd {

    /**
     * Execute JFR.stop
     *
     * Requires that either {@code name} or {@code id} is set.
     *
     * @param name name or id of the recording to stop.
     *
     * @param filename file path where data should be written after recording has
     *        been stopped, or {@code null} if recording shouldn't be written
     *        to disk.
     * @return result text
     *
     * @throws DCmdException if recording could not be stopped
     */
    public String[] execute(String name, String filename) throws DCmdException {
        if (Logger.shouldLog(LogTag.JFR_DCMD, LogLevel.DEBUG)) {
            Logger.log(LogTag.JFR_DCMD, LogLevel.DEBUG, "Executing DCmdStart: name=" + name + ", filename=" + filename);
        }

        try {
            SafePath safePath = null;
            Recording recording = findRecording(name);
            if (filename != null) {
                try {
                    // Ensure path is valid. Don't generate safePath if filename == null, as a user may
                    // want to stop recording without a dump
                    safePath = resolvePath(null, filename);
                    recording.setDestination(Paths.get(filename));
                } catch (IOException | InvalidPathException  e) {
                    throw new DCmdException("Failed to stop %s. Could not set destination for \"%s\" to file %s", recording.getName(), filename, e.getMessage());
                }
            }
            recording.stop();
            reportOperationComplete("Stopped", recording.getName(), safePath);
            recording.close();
            return getResult();
        } catch (InvalidPathException | DCmdException e) {
            if (filename != null) {
                throw new DCmdException("Could not write recording \"%s\" to file. %s", name, e.getMessage());
            }
            throw new DCmdException(e, "Could not stop recording \"%s\".", name, e.getMessage());
        }
    }
}
