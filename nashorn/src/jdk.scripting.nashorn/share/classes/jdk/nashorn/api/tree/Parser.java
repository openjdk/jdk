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

package jdk.nashorn.api.tree;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Path;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 * Represents nashorn ECMAScript parser instance.
 *
 * @since 9
 */
public interface Parser {
    /**
     * Parses the source file and returns compilation unit tree
     *
     * @param file source file to parse
     * @param listener to receive diagnostic messages from the parser. This can be null.
     * if null is passed, a NashornException is thrown on the first parse error.
     * @return compilation unit tree
     * @throws NullPointerException if file is null
     * @throws IOException if parse source read fails
     * @throws NashornException is thrown if no listener is supplied and parser encounters error
     */
    public CompilationUnitTree parse(final File file, final DiagnosticListener listener) throws IOException, NashornException;

    /**
     * Parses the source Path and returns compilation unit tree
     *
     * @param path source Path to parse
     * @param listener to receive diagnostic messages from the parser. This can be null.
     * if null is passed, a NashornException is thrown on the first parse error.
     * @return compilation unit tree
     * @throws NullPointerException if path is null
     * @throws IOException if parse source read fails
     * @throws NashornException is thrown if no listener is supplied and parser encounters error
     */
    public CompilationUnitTree parse(final Path path, final DiagnosticListener listener) throws IOException, NashornException;

    /**
     * Parses the source url and returns compilation unit tree
     *
     * @param url source file to parse
     * @param listener to receive diagnostic messages from the parser. This can be null.
     * if null is passed, a NashornException is thrown on the first parse error.
     * @return compilation unit tree
     * @throws NullPointerException if url is null
     * @throws IOException if parse source read fails
     * @throws NashornException is thrown if no listener is supplied and parser encounters error
     */
    public CompilationUnitTree parse(final URL url, final DiagnosticListener listener) throws IOException, NashornException;

    /**
     * Parses the reader and returns compilation unit tree
     *
     * @param name name of the source file to parse
     * @param reader from which source is read
     * @param listener to receive diagnostic messages from the parser. This can be null.
     * if null is passed, a NashornException is thrown on the first parse error.
     * @return compilation unit tree
     * @throws NullPointerException if name or reader is null
     * @throws IOException if parse source read fails
     * @throws NashornException is thrown if no listener is supplied and parser encounters error
     */
    public CompilationUnitTree parse(final String name, Reader reader, final DiagnosticListener listener) throws IOException, NashornException;

    /**
     * Parses the string source and returns compilation unit tree
     *
     * @param name of the source
     * @param code string source
     * @param listener to receive diagnostic messages from the parser. This can be null.
     * if null is passed, a NashornException is thrown on the first parse error.
     * @return compilation unit tree
     * @throws NullPointerException if name or code is null
     * @throws NashornException is thrown if no listener is supplied and parser encounters error
     */
    public CompilationUnitTree parse(final String name, String code, final DiagnosticListener listener) throws NashornException;

    /**
     * Parses the source from script object and returns compilation unit tree
     *
     * @param scriptObj script object whose script and name properties are used for script source
     * @param listener to receive diagnostic messages from the parser. This can be null.
     * if null is passed, a NashornException is thrown on the first parse error.
     * @return compilation unit tree
     * @throws NullPointerException if scriptObj is null
     * @throws NashornException is thrown if no listener is supplied and parser encounters error
     */
    public CompilationUnitTree parse(final ScriptObjectMirror scriptObj, final DiagnosticListener listener) throws NashornException;

    /**
     * Factory method to create a new instance of Parser.
     *
     * @param options configuration options to initialize the Parser.
     *         Currently the following options are supported:
     *
     * <dl>
     * <dt>"--const-as-var"</dt><dd>treat "const" declaration as "var"</dd>
     * <dt>"-dump-on-error" or "-doe"</dt><dd>dump stack trace on error</dd>
     * <dt>"--empty-statements"</dt><dd>include empty statement nodes</dd>
     * <dt>"--no-syntax-extensions" or "-nse"</dt><dd>disable ECMAScript syntax extensions</dd>
     * <dt>"-scripting"</dt><dd>enable scripting mode extensions</dd>
     * <dt>"-strict"</dt><dd>enable ECMAScript strict mode</dd>
     * </dl>
     *
     * @throws NullPointerException if options array or any of its element is null
     * @throws IllegalArgumentException on unsupported option value.
     * @return a new Parser instance.
     */
    public static Parser create(final String... options) throws IllegalArgumentException {
        options.getClass();
        for (String opt : options) {
            switch (opt) {
                case "--const-as-var":
                case "-dump-on-error":
                case "-doe":
                case "--empty-statements":
                case "--no-syntax-extensions":
                case "-nse":
                case "-scripting":
                case "-strict":
                    break;
                default:
                    throw new IllegalArgumentException(opt);
            }
        }

        return new ParserImpl(options);
    }
}
