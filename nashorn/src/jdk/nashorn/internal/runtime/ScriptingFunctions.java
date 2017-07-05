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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Global functions supported only in scripting mode.
 */
public final class ScriptingFunctions {

    /** Handle to implementation of {@link ScriptingFunctions#readLine} - Nashorn extension */
    public static final MethodHandle READLINE = findOwnMH("readLine", Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#readFully} - Nashorn extension */
    public static final MethodHandle READFULLY = findOwnMH("readFully",     Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#exec} - Nashorn extension */
    public static final MethodHandle EXEC = findOwnMH("exec",     Object.class, Object.class, Object.class, Object.class);

    /** EXEC name - special property used by $EXEC API. */
    public static final String EXEC_NAME = "$EXEC";

    /** OUT name - special property used by $EXEC API. */
    public static final String OUT_NAME  = "$OUT";

    /** ERR name - special property used by $EXEC API. */
    public static final String ERR_NAME  = "$ERR";

    /** EXIT name - special property used by $EXEC API. */
    public static final String EXIT_NAME = "$EXIT";

    /** Names of special properties used by $ENV API. */
    public  static final String ENV_NAME  = "$ENV";

    private static final String PWD_NAME  = "PWD";

    private ScriptingFunctions() {
    }

    /**
     * Nashorn extension: global.readLine (scripting-mode-only)
     * Read one line of input from the standard input.
     *
     * @param self   self reference
     * @param prompt String used as input prompt
     *
     * @return line that was read
     *
     * @throws IOException if an exception occurs
     */
    public static Object readLine(final Object self, final Object prompt) throws IOException {
        if (prompt != UNDEFINED) {
            System.out.print(JSType.toString(prompt));
        }
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
        } else if (file instanceof String || file instanceof ConsString) {
            f = new java.io.File(((CharSequence)file).toString());
        }

        if (f == null || !f.isFile()) {
            throw typeError("not.a.file", ScriptRuntime.safeToString(file));
        }

        return new String(Source.readFully(f));
    }

    /**
     * Nashorn extension: exec a string in a separate process.
     *
     * @param self   self reference
     * @param string string to execute
     * @param input  input
     *
     * @return output string from the request
     * @throws IOException           if any stream access fails
     * @throws InterruptedException  if execution is interrupted
     */
    public static Object exec(final Object self, final Object string, final Object input) throws IOException, InterruptedException {
        // Current global is need to fetch additional inputs and for additional results.
        final ScriptObject global = Context.getGlobal();

        // Break exec string into tokens.
        final StringTokenizer tokenizer = new StringTokenizer(JSType.toString(string));
        final String[] cmdArray = new String[tokenizer.countTokens()];
        for (int i = 0; tokenizer.hasMoreTokens(); i++) {
            cmdArray[i] = tokenizer.nextToken();
        }

        // Set up initial process.
        final ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);

        // Current ENV property state.
        final Object env = global.get(ENV_NAME);
        if (env instanceof ScriptObject) {
            final ScriptObject envProperties = (ScriptObject)env;

            // If a working directory is present, use it.
            final Object pwd = envProperties.get(PWD_NAME);
            if (pwd != UNDEFINED) {
                processBuilder.directory(new File(JSType.toString(pwd)));
            }

            // Set up ENV variables.
            final Map<String, String> environment = processBuilder.environment();
            environment.clear();
            for (final Map.Entry<Object, Object> entry : envProperties.entrySet()) {
                environment.put(JSType.toString(entry.getKey()), JSType.toString(entry.getValue()));
            }
        }

        // Start the process.
        final Process process = processBuilder.start();
        final IOException exception[] = new IOException[2];

        // Collect output.
        final StringBuilder outBuffer = new StringBuilder();
        final Thread outThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final char buffer[] = new char[1024];
                try (final InputStreamReader inputStream = new InputStreamReader(process.getInputStream())) {
                    for (int length; (length = inputStream.read(buffer, 0, buffer.length)) != -1; ) {
                        outBuffer.append(buffer, 0, length);
                    }
                } catch (final IOException ex) {
                    exception[0] = ex;
                }
            }
        }, "$EXEC output");

        // Collect errors.
        final StringBuilder errBuffer = new StringBuilder();
        final Thread errThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final char buffer[] = new char[1024];
                try (final InputStreamReader inputStream = new InputStreamReader(process.getErrorStream())) {
                    for (int length; (length = inputStream.read(buffer, 0, buffer.length)) != -1; ) {
                        errBuffer.append(buffer, 0, length);
                    }
                } catch (final IOException ex) {
                    exception[1] = ex;
                }
            }
        }, "$EXEC error");

        // Start gathering output.
        outThread.start();
        errThread.start();

        // If input is present, pass on to process.
        try (OutputStreamWriter outputStream = new OutputStreamWriter(process.getOutputStream())) {
            if (input != UNDEFINED) {
                final String in = JSType.toString(input);
                outputStream.write(in, 0, in.length());
            }
        } catch (final IOException ex) {
            // Process was not expecting input.  May be normal state of affairs.
        }

        // Wait for the process to complete.
        final int exit = process.waitFor();
        outThread.join();
        errThread.join();

        final String out = outBuffer.toString();
        final String err = errBuffer.toString();

        // Set globals for secondary results.
        global.set(OUT_NAME, out, false);
        global.set(ERR_NAME, err, false);
        global.set(EXIT_NAME, exit, false);

        // Propagate exception if present.
        for (final IOException element : exception) {
            if (element != null) {
                throw element;
            }
        }

        // Return the result from stdout.
        return out;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptingFunctions.class, name, MH.type(rtype, types));
    }
}
