/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools;

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.objects.Global;

/**
 * Global functions supported only in shell interactive mode.
 */
public final class ShellFunctions {

    /** Handle to implementation of {@link ShellFunctions#input} - Nashorn extension */
    public static final MethodHandle INPUT = findOwnMH("input", Object.class, Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ShellFunctions#evalinput} - Nashorn extension */
    public static final MethodHandle EVALINPUT = findOwnMH("evalinput",     Object.class, Object.class, Object.class, Object.class);

    private ShellFunctions() {
    }

    /**
     * Nashorn extension: global.input (shell-interactive-mode-only)
     * Read one or more lines of input from the standard input till the
     * given end marker is seen in standard input.
     *
     * @param self   self reference
     * @param endMarker String used as end marker for input
     * @param prompt String used as input prompt
     *
     * @return line that was read
     *
     * @throws IOException if an exception occurs
     */
    public static Object input(final Object self, final Object endMarker, final Object prompt) throws IOException {
        final String endMarkerStr = (endMarker != UNDEFINED)? JSType.toString(endMarker) : "";
        final String promptStr = (prompt != UNDEFINED)? JSType.toString(prompt)  : ">> ";
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        final StringBuilder buf = new StringBuilder();
        while (true) {
            System.out.print(promptStr);
            final String line = reader.readLine();
            if (line == null || line.equals(endMarkerStr)) {
                break;
            }
            buf.append(line);
            buf.append('\n');
        }
        return buf.toString();
    }

    /**
     * Nashorn extension: Reads zero or more lines from standard input and
     * evaluates the concatenated string as code
     *
     * @param self self reference
     * @param endMarker String used as end marker for input
     * @param prompt String used as input prompt
     *
     * @return output from evaluating the script
     *
     * @throws IOException if an exception occurs
     */
    public static Object evalinput(final Object self, final Object endMarker, final Object prompt) throws IOException {
        return Global.eval(self, input(self, endMarker, prompt));
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ShellFunctions.class, name, MH.type(rtype, types));
    }
}
