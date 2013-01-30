/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Global functions supported only in scripting mode.
 */
public class ScriptingFunctions {

    /** Handle to implementation of {@link ScriptingFunctions#readLine} - Nashorn extension */
    public static final MethodHandle READLINE = findOwnMH("readLine", Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#readFully} - Nashorn extension */
    public static final MethodHandle READFULLY = findOwnMH("readFully",     Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#quit} - Nashorn extension */
    public static final MethodHandle QUIT = findOwnMH("quit",     Object.class, Object.class, Object.class);

    private ScriptingFunctions() {
    }

    /**
     * Nashorn extension: global.readLine (scripting-mode-only)
     * Read one line of input from the standard input.
     *
     * @param self self reference
     *
     * @return line that was read
     *
     * @throws IOException if an exception occurs
     */
    public static Object readLine(final Object self) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    /**
     * Nashorn extension: Read the entire contents of a text file and return as String.
     *
     * @param self self reference
     * @param file The input file whose content is read.
     *
     * @return String content of the input file.
     *
     * @throws IOException if an exception occurs
     */
    public static Object readFully(final Object self, final Object file) throws IOException {
        File f = null;

        if (file instanceof File) {
            f = (File)file;
        } else if (file instanceof String) {
            f = new java.io.File((String)file);
        }

        if (f == null || !f.isFile()) {
            typeError("not.a.file", ScriptRuntime.safeToString(file));
            return UNDEFINED;
        }

        return new String(Source.readFully(f));
    }

    /**
     * Nashorn extension: perform a {@code System.exit} call from the script
     *
     * @param self  self reference
     * @param code  exit code
     *
     * @return undefined (will never be reacheD)
     */
    public static Object quit(final Object self, final Object code) {
        System.exit(JSType.toInt32(code));
        return UNDEFINED;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptingFunctions.class, name, MH.type(rtype, types));
    }
}
