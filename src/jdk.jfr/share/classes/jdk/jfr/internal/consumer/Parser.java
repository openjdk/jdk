/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.consumer;

import java.io.IOException;

/**
 * Base class for parsing data from a {@link RecordingInput}.
 */
abstract class Parser {
    /**
     * Parses data from a {@link RecordingInput} and return an object.
     *
     * @param input input to read from
     * @return an {@code Object}, an {@code Object[]}, or {@code null}
     * @throws IOException if operation couldn't be completed due to I/O
     *         problems
     */
    public abstract Object parse(RecordingInput input) throws IOException;

    /**
     * Parses data from a {@link RecordingInput} to find references to constants. If
     * data is not a reference, {@code null} is returned.
     * <p>
     * @implSpec The default implementation of this method skips data and returns
     * {@code Object}.
     *
     * @param input input to read from, not {@code null}
     * @return a {@code Reference}, a {@code Reference[]}, or {@code null}
     * @throws IOException if operation couldn't be completed due to I/O problems
     */
    public Object parseReferences(RecordingInput input) throws IOException {
        skip(input);
        return null;
    }

    /**
     * Skips data that would usually be by parsed the {@link #parse(RecordingInput)} method.
     *
     * @param input input to read from
     * @throws IOException if operation couldn't be completed due to I/O
     *         problems
     */
    public abstract void skip(RecordingInput input) throws IOException;
}
