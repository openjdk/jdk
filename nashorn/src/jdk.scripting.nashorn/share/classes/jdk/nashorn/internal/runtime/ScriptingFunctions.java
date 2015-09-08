/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.objects.NativeArray;

/**
 * Global functions supported only in scripting mode.
 */
public final class ScriptingFunctions {

    /** Handle to implementation of {@link ScriptingFunctions#readLine} - Nashorn extension */
    public static final MethodHandle READLINE = findOwnMH("readLine", Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#readFully} - Nashorn extension */
    public static final MethodHandle READFULLY = findOwnMH("readFully",     Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#exec} - Nashorn extension */
    public static final MethodHandle EXEC = findOwnMH("exec",     Object.class, Object.class, Object[].class);

    /** EXEC name - special property used by $EXEC API. */
    public static final String EXEC_NAME = "$EXEC";

    /** OUT name - special property used by $EXEC API. */
    public static final String OUT_NAME  = "$OUT";

    /** ERR name - special property used by $EXEC API. */
    public static final String ERR_NAME  = "$ERR";

    /** EXIT name - special property used by $EXEC API. */
    public static final String EXIT_NAME = "$EXIT";

    /** THROW_ON_ERROR name - special property of the $EXEC function used by $EXEC API. */
    public static final String THROW_ON_ERROR_NAME = "throwOnError";

    /** Names of special properties used by $ENV API. */
    public  static final String ENV_NAME  = "$ENV";

    /** Name of the environment variable for the current working directory. */
    public static final String PWD_NAME  = "PWD";

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
        } else if (JSType.isString(file)) {
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
     * @param args   string to execute, input and additional arguments, to be appended to {@code string}. Additional
     *               arguments can be passed as either one JavaScript array, whose elements will be converted to
     *               strings; or as a sequence of varargs, each of which will be converted to a string.
     *
     * @return output string from the request
     *
     * @throws IOException           if any stream access fails
     * @throws InterruptedException  if execution is interrupted
     */
    public static Object exec(final Object self, final Object... args) throws IOException, InterruptedException {
        // Current global is need to fetch additional inputs and for additional results.
        final ScriptObject global = Context.getGlobal();
        final Object string = args.length > 0? args[0] : UNDEFINED;
        final Object input = args.length > 1? args[1] : UNDEFINED;
        final Object[] argv = (args.length > 2)? Arrays.copyOfRange(args, 2, args.length) : ScriptRuntime.EMPTY_ARRAY;
        // Assemble command line, process additional arguments.
        final List<String> cmdLine = tokenizeString(JSType.toString(string));
        final Object[] additionalArgs = argv.length == 1 && argv[0] instanceof NativeArray ?
                ((NativeArray) argv[0]).asObjectArray() :
                argv;
        for (Object arg : additionalArgs) {
            cmdLine.add(JSType.toString(arg));
        }

        // Set up initial process.
        final ProcessBuilder processBuilder = new ProcessBuilder(cmdLine);

        // Current ENV property state.
        final Object env = global.get(ENV_NAME);
        if (env instanceof ScriptObject) {
            final ScriptObject envProperties = (ScriptObject)env;

            // If a working directory is present, use it.
            final Object pwd = envProperties.get(PWD_NAME);
            if (pwd != UNDEFINED) {
                final File pwdFile = new File(JSType.toString(pwd));
                if (pwdFile.exists()) {
                    processBuilder.directory(pwdFile);
                }
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
        if (!JSType.nullOrUndefined(input)) {
            try (OutputStreamWriter outputStream = new OutputStreamWriter(process.getOutputStream())) {
                final String in = JSType.toString(input);
                outputStream.write(in, 0, in.length());
            } catch (final IOException ex) {
                // Process was not expecting input.  May be normal state of affairs.
            }
        }

        // Wait for the process to complete.
        final int exit = process.waitFor();
        outThread.join();
        errThread.join();

        final String out = outBuffer.toString();
        final String err = errBuffer.toString();

        // Set globals for secondary results.
        global.set(OUT_NAME, out, 0);
        global.set(ERR_NAME, err, 0);
        global.set(EXIT_NAME, exit, 0);

        // Propagate exception if present.
        for (final IOException element : exception) {
            if (element != null) {
                throw element;
            }
        }

        // if we got a non-zero exit code ("failure"), then we have to decide to throw error or not
        if (exit != 0) {
            // get the $EXEC function object from the global object
            final Object exec = global.get(EXEC_NAME);
            assert exec instanceof ScriptObject : EXEC_NAME + " is not a script object!";

            // Check if the user has set $EXEC.throwOnError property to true. If so, throw RangeError
            // If that property is not set or set to false, then silently proceed with the rest.
            if (JSType.toBoolean(((ScriptObject)exec).get(THROW_ON_ERROR_NAME))) {
                throw rangeError("exec.returned.non.zero", ScriptRuntime.safeToString(exit));
            }
        }

        // Return the result from stdout.
        return out;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptingFunctions.class, name, MH.type(rtype, types));
    }

    /**
     * Break a string into tokens, honoring quoted arguments and escaped spaces.
     *
     * @param str a {@link String} to tokenize.
     * @return a {@link List} of {@link String}s representing the tokens that
     * constitute the string.
     * @throws IOException in case {@link StreamTokenizer#nextToken()} raises it.
     */
    public static List<String> tokenizeString(final String str) throws IOException {
        final StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(str));
        tokenizer.resetSyntax();
        tokenizer.wordChars(0, 255);
        tokenizer.whitespaceChars(0, ' ');
        tokenizer.commentChar('#');
        tokenizer.quoteChar('"');
        tokenizer.quoteChar('\'');
        final List<String> tokenList = new ArrayList<>();
        final StringBuilder toAppend = new StringBuilder();
        while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
            final String s = tokenizer.sval;
            // The tokenizer understands about honoring quoted strings and recognizes
            // them as one token that possibly contains multiple space-separated words.
            // It does not recognize quoted spaces, though, and will split after the
            // escaping \ character. This is handled here.
            if (s.endsWith("\\")) {
                // omit trailing \, append space instead
                toAppend.append(s.substring(0, s.length() - 1)).append(' ');
            } else {
                tokenList.add(toAppend.append(s).toString());
                toAppend.setLength(0);
            }
        }
        if (toAppend.length() != 0) {
            tokenList.add(toAppend.toString());
        }
        return tokenList;
    }
}
