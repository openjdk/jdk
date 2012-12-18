/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.*;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.main.OptionHelper;
import com.sun.tools.javac.main.OptionHelper.GrumpyHelper;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.PrefixKind;
import com.sun.tools.javac.util.Options;

/**
 * TODO: describe com.sun.tools.javac.api.Tool
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah\u00e9
 */
public final class JavacTool implements JavaCompiler {
    /**
     * Constructor used by service provider mechanism.  The recommended way to
     * obtain an instance of this class is by using {@link #create} or the
     * service provider mechanism.
     * @see javax.tools.JavaCompiler
     * @see javax.tools.ToolProvider
     * @see #create
     */
    @Deprecated
    public JavacTool() {}

    /**
     * Static factory method for creating new instances of this tool.
     * @return new instance of this tool
     */
    public static JavacTool create() {
        return new JavacTool();
    }

    public JavacFileManager getStandardFileManager(
        DiagnosticListener<? super JavaFileObject> diagnosticListener,
        Locale locale,
        Charset charset) {
        Context context = new Context();
        context.put(Locale.class, locale);
        if (diagnosticListener != null)
            context.put(DiagnosticListener.class, diagnosticListener);
        PrintWriter pw = (charset == null)
                ? new PrintWriter(System.err, true)
                : new PrintWriter(new OutputStreamWriter(System.err, charset), true);
        context.put(Log.outKey, pw);
        return new JavacFileManager(context, true, charset);
    }

    @Override
    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits) {
        Context context = new Context();
        return getTask(out, fileManager, diagnosticListener,
                options, classes, compilationUnits,
                context);
    }

    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits,
                             Context context)
    {
        try {
            ClientCodeWrapper ccw = ClientCodeWrapper.instance(context);

            final String kindMsg = "All compilation units must be of SOURCE kind";
            if (options != null)
                for (String option : options)
                    option.getClass(); // null check
            if (classes != null) {
                for (String cls : classes)
                    if (!SourceVersion.isName(cls)) // implicit null check
                        throw new IllegalArgumentException("Not a valid class name: " + cls);
            }
            if (compilationUnits != null) {
                compilationUnits = ccw.wrapJavaFileObjects(compilationUnits); // implicit null check
                for (JavaFileObject cu : compilationUnits) {
                    if (cu.getKind() != JavaFileObject.Kind.SOURCE)
                        throw new IllegalArgumentException(kindMsg);
                }
            }

            if (diagnosticListener != null)
                context.put(DiagnosticListener.class, ccw.wrap(diagnosticListener));

            if (out == null)
                context.put(Log.outKey, new PrintWriter(System.err, true));
            else
                context.put(Log.outKey, new PrintWriter(out, true));

            if (fileManager == null)
                fileManager = getStandardFileManager(diagnosticListener, null, null);
            fileManager = ccw.wrap(fileManager);

            context.put(JavaFileManager.class, fileManager);

            processOptions(context, fileManager, options);
            Main compiler = new Main("javacTask", context.get(Log.outKey));
            return new JavacTaskImpl(compiler, options, context, classes, compilationUnits);
        } catch (ClientCodeException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    public static void processOptions(Context context,
                                       JavaFileManager fileManager,
                                       Iterable<String> options)
    {
        if (options == null)
            return;

        final Options optionTable = Options.instance(context);
        Log log = Log.instance(context);

        Option[] recognizedOptions =
                Option.getJavacToolOptions().toArray(new Option[0]);
        OptionHelper optionHelper = new GrumpyHelper(log) {
            @Override
            public String get(Option option) {
                return optionTable.get(option.getText());
            }

            @Override
            public void put(String name, String value) {
                optionTable.put(name, value);
            }

            @Override
            public void remove(String name) {
                optionTable.remove(name);
            }
        };

        Iterator<String> flags = options.iterator();
        while (flags.hasNext()) {
            String flag = flags.next();
            int j;
            for (j=0; j<recognizedOptions.length; j++)
                if (recognizedOptions[j].matches(flag))
                    break;

            if (j == recognizedOptions.length) {
                if (fileManager.handleOption(flag, flags)) {
                    continue;
                } else {
                    String msg = log.localize(PrefixKind.JAVAC, "err.invalid.flag", flag);
                    throw new IllegalArgumentException(msg);
                }
            }

            Option option = recognizedOptions[j];
            if (option.hasArg()) {
                if (!flags.hasNext()) {
                    String msg = log.localize(PrefixKind.JAVAC, "err.req.arg", flag);
                    throw new IllegalArgumentException(msg);
                }
                String operand = flags.next();
                if (option.process(optionHelper, flag, operand))
                    // should not happen as the GrumpyHelper will throw exceptions
                    // in case of errors
                    throw new IllegalArgumentException(flag + " " + operand);
            } else {
                if (option.process(optionHelper, flag))
                    // should not happen as the GrumpyHelper will throw exceptions
                    // in case of errors
                    throw new IllegalArgumentException(flag);
            }
        }

        optionTable.notifyListeners();
    }

    public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
        if (err == null)
            err = System.err;
        for (String argument : arguments)
            argument.getClass(); // null check
        return com.sun.tools.javac.Main.compile(arguments, new PrintWriter(err, true));
    }

    public Set<SourceVersion> getSourceVersions() {
        return Collections.unmodifiableSet(EnumSet.range(SourceVersion.RELEASE_3,
                                                         SourceVersion.latest()));
    }

    public int isSupportedOption(String option) {
        Set<Option> recognizedOptions = Option.getJavacToolOptions();
        for (Option o : recognizedOptions) {
            if (o.matches(option))
                return o.hasArg() ? 1 : 0;
        }
        return -1;
    }

}
