/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jpackage.internal.util.CommandOutputControl.Result;


@FunctionalInterface
interface DesktopEntryFileValidator {

    Result validate(Path desktopEntryFile);

    /**
     * Creates desktop entry file validator that will run the
     * {@code desktop-file-validate} command in every invocation of the
     * {@link #validate(Path)} until the first failure to execute the command. In
     * such an event, the validator will return a {@link Result} without the exit
     * code and will keep returning such a value in subsequent invocations without
     * calling the command.
     *
     * @return the desktop entry file validator
     */
    static DesktopEntryFileValidator createDefault() {
        return new DesktopEntryFileValidator() {

            @Override
            public Result validate(Path desktopEntryFile) {
                if (stop.get()) {
                    return EMPTY_RESULT;
                } else {
                    try {
                        return Executor.of("desktop-file-validate".toString(), desktopEntryFile.toString()).execute();
                    } catch (IOException ex) {
                        // The command probably isn't available.
                        Log.trace(ex);
                        // Return result without the exit code.
                        stop.set(true);
                        return EMPTY_RESULT;
                    }
                }
            }

            private final AtomicBoolean stop = new AtomicBoolean();

            private static final Result EMPTY_RESULT = Result.build().create();
        };
    }

}
