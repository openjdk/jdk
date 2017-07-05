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

package jdk.nashorn.internal.test.framework;

import static jdk.nashorn.internal.runtime.Source.sourceFor;
import static jdk.nashorn.tools.Shell.COMPILATION_ERROR;
import static jdk.nashorn.tools.Shell.RUNTIME_ERROR;
import static jdk.nashorn.tools.Shell.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * A script evaluator that shares a single Nashorn Context instance to run
 * scripts many times on it.
 */
public final class SharedContextEvaluator implements ScriptEvaluator {
    // The shared Nashorn Context
    private final Context context;

    // We can't replace output and error streams after Context is created
    // So, we create these delegating streams - so that we can replace underlying
    // delegate streams for each script run call
    private final DelegatingOutputStream ctxOut;
    private final DelegatingOutputStream ctxErr;

    private static class DelegatingOutputStream extends OutputStream {
        private OutputStream underlying;

        public DelegatingOutputStream(final OutputStream out) {
            this.underlying = out;
        }

        @Override
        public void close() throws IOException {
            underlying.close();
        }

        @Override
        public void flush() throws IOException {
            underlying.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            underlying.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            underlying.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            underlying.write(b);
        }

        void setDelegatee(OutputStream stream) {
            this.underlying = stream;
        }
    }

    /**
     * SharedContextEvaluator constructor
     * @param args initial script arguments to create shared context
     */
    public SharedContextEvaluator(final String[] args) {
        this.ctxOut = new DelegatingOutputStream(System.out);
        this.ctxErr = new DelegatingOutputStream(System.err);
        PrintWriter wout = new PrintWriter(ctxOut, true);
        PrintWriter werr = new PrintWriter(ctxErr, true);
        Options options = new Options("nashorn", werr);
        options.process(args);
        ErrorManager errors = new ErrorManager(werr);
        this.context = new Context(options, errors, wout, werr, Thread.currentThread().getContextClassLoader());
    }

    @Override
    public int run(final OutputStream out, final OutputStream err, final String[] args) throws IOException {
        final Global oldGlobal = Context.getGlobal();
        try {
            ctxOut.setDelegatee(out);
            ctxErr.setDelegatee(err);
            final ErrorManager errors = context.getErrorManager();
            final Global global = context.createGlobal();
            Context.setGlobal(global);

            // For each file on the command line.
            for (final String fileName : args) {
                if (fileName.startsWith("-")) {
                    // ignore options in shared context mode (which was initialized upfront!)
                    continue;
                }
                final File file = new File(fileName);
                ScriptFunction script = context.compileScript(sourceFor(fileName, file.toURI().toURL()), global);

                if (script == null || errors.getNumberOfErrors() != 0) {
                    return COMPILATION_ERROR;
                }

                try {
                    ScriptRuntime.apply(script, global);
                } catch (final NashornException e) {
                    errors.error(e.toString());
                    if (context.getEnv()._dump_on_error) {
                        e.printStackTrace(context.getErr());
                    }

                    return RUNTIME_ERROR;
                }
            }
        } finally {
            context.getOut().flush();
            context.getErr().flush();
            Context.setGlobal(oldGlobal);
        }

        return SUCCESS;
    }
}
