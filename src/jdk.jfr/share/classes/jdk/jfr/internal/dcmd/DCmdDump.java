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
import java.util.HashMap;
import java.util.Map;

import jdk.jfr.Recording;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.SecuritySupport.SafePath;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.WriteableUserPath;

/**
 * JFR.dump
 *
 */
//Instantiated by native
final class DCmdDump extends AbstractDCmd {
    /**
     * Execute JFR.dump.
     *
     * @param recordingText name or id of the recording to dump, or
     *        <code>null</code>
     *
     * @param textPath file path where recording should be written.
     *
     * @return result output
     *
     * @throws DCmdException if the dump could not be completed
     */
    public String execute(String recordingText, String textPath,  Boolean pathToGcRoots) throws DCmdException {
        if (textPath == null) {
            throw new DCmdException("Failed to dump %s, missing filename.", recordingText);
        }
        Recording recording = findRecording(recordingText);
        try {
            SafePath dumpFile = resolvePath(textPath, "Failed to dump %s");
            // create file for JVM
            Utils.touch(dumpFile.toPath());
            PlatformRecording r = PrivateAccess.getInstance().getPlatformRecording(recording);
            WriteableUserPath wup = new WriteableUserPath(dumpFile.toPath());

            Map<String, String> overlay = new HashMap<>();
            Utils.updateSettingPathToGcRoots(overlay, pathToGcRoots);

            r.copyTo(wup, "Dumped by user", overlay);
            reportOperationComplete("Dumped", recording, dumpFile);
        } catch (IOException | InvalidPathException e) {
            throw new DCmdException("Failed to dump %s. Could not copy recording for dump. %s", recordingText, e.getMessage());
        }
        return getResult();
    }

}
