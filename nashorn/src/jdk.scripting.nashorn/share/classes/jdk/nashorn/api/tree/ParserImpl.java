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
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.options.Options;

final class ParserImpl implements Parser {

    private final ScriptEnvironment env;

    ParserImpl(final String... args) throws IllegalArgumentException {
       Objects.requireNonNull(args);
       Options options = new Options("nashorn");
       options.process(args);
       this.env = new ScriptEnvironment(options,
               new PrintWriter(System.out), new PrintWriter(System.err));
    }

    @Override
    public CompilationUnitTree parse(final File file, final DiagnosticListener listener) throws IOException, NashornException {
        final Source src = Source.sourceFor(Objects.requireNonNull(file).getName(), file);
        return translate(makeParser(src, listener).parse());
    }

    @Override
    public CompilationUnitTree parse(final Path path, final DiagnosticListener listener) throws IOException, NashornException {
        final Source src = Source.sourceFor(Objects.requireNonNull(path).toString(), path);
        return translate(makeParser(src, listener).parse());
    }

    @Override
    public CompilationUnitTree parse(final URL url, final DiagnosticListener listener) throws IOException, NashornException {
        final Source src = Source.sourceFor(url.toString(), url);
        return translate(makeParser(src, listener).parse());
    }

    @Override
    public CompilationUnitTree parse(final String name, final Reader reader, final DiagnosticListener listener) throws IOException, NashornException {
        final Source src = Source.sourceFor(Objects.requireNonNull(name), Objects.requireNonNull(reader));
        return translate(makeParser(src, listener).parse());
    }

    @Override
    public CompilationUnitTree parse(final String name, final String code, final DiagnosticListener listener) throws NashornException {
        final Source src = Source.sourceFor(name, code);
        return translate(makeParser(src, listener).parse());
    }

    @Override
    public CompilationUnitTree parse(final ScriptObjectMirror scriptObj, final DiagnosticListener listener) throws NashornException {
        final Map<?,?> map = Objects.requireNonNull(scriptObj);
        if (map.containsKey("script") && map.containsKey("name")) {
            final String script = JSType.toString(map.get("script"));
            final String name   = JSType.toString(map.get("name"));
            final Source src = Source.sourceFor(name, script);
            return translate(makeParser(src, listener).parse());
        } else {
            throw new IllegalArgumentException("can't find 'script' and 'name' properties");
        }
    }

    private jdk.nashorn.internal.parser.Parser makeParser(final Source source, final DiagnosticListener listener) {
        final ErrorManager errMgr = listener != null? new ListenerErrorManager(listener) : new Context.ThrowErrorManager();
        return new jdk.nashorn.internal.parser.Parser(env, source, errMgr);
    }

    private static class ListenerErrorManager extends ErrorManager {
        private final DiagnosticListener listener;

        ListenerErrorManager(final DiagnosticListener listener) {
            // null check
            listener.getClass();
            this.listener = listener;
        }

        @Override
        public void error(final String msg) {
            error(new ParserException(msg));
        }

        @Override
        public void error(final ParserException e) {
            listener.report(new DiagnosticImpl(e, Diagnostic.Kind.ERROR));
        }

        @Override
        public void warning(final String msg) {
            warning(new ParserException(msg));
        }

        @Override
        public void warning(final ParserException e) {
            listener.report(new DiagnosticImpl(e, Diagnostic.Kind.WARNING));
        }
    }

    private static CompilationUnitTree translate(final FunctionNode node) {
        return new IRTranslator().translate(node);
    }
}
