/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror.apt;

import com.sun.mirror.apt.RoundState;
import java.util.Map;

@SuppressWarnings("deprecation")
public class RoundStateImpl implements RoundState {
    private final boolean finalRound;
    private final boolean errorRaised;
    private final boolean sourceFilesCreated;
    private final boolean classFilesCreated;

    public RoundStateImpl(boolean errorRaised,
                          boolean sourceFilesCreated,
                          boolean classFilesCreated,
                          Map<String,String> options) {
        /*
         * In the default mode of operation, this round is the final
         * round if an error was raised OR there were no new source
         * files generated.  If classes are being treated as
         * declarations, this is the final round if an error was
         * raised OR neither new source files nor new class files were
         * generated.
         */
        this.finalRound =
            errorRaised ||
            (!sourceFilesCreated &&
            !(classFilesCreated && options.keySet().contains("-XclassesAsDecls")) );
        this.errorRaised = errorRaised;
        this.sourceFilesCreated = sourceFilesCreated;
        this.classFilesCreated = classFilesCreated;
    }

    public boolean finalRound() {
        return finalRound;
    }

    public boolean errorRaised() {
        return errorRaised;
    }

    public boolean sourceFilesCreated() {
        return sourceFilesCreated;
    }

    public boolean classFilesCreated() {
        return classFilesCreated;
    }

    public String toString() {
        return
            "[final round: " +  finalRound +
            ", error raised: " +  errorRaised +
            ", source files created: " + sourceFilesCreated +
            ", class files created: " + classFilesCreated + "]";
    }
}
