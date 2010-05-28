/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.*;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavacOption.OptionKind;
import com.sun.tools.javac.main.JavacOption;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.RecognizedOptions.GrumpyHelper;
import com.sun.tools.javac.main.RecognizedOptions;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Pair;
import java.nio.charset.Charset;

/**
 * TODO: describe com.sun.tools.javac.api.Tool
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah\u00e9
 */
public final class JavacTool implements JavaCompiler {
    private final List<Pair<String,String>> options
        = new ArrayList<Pair<String,String>>();
    private final Context dummyContext = new Context();

    private final PrintWriter silent = new PrintWriter(new OutputStream(){
        public void write(int b) {}
    });

    private final Main sharedCompiler = new Main("javac", silent);
    {
        sharedCompiler.setOptions(Options.instance(dummyContext));
    }

    /**
     * Constructor used by service provider mechanism.  The correct way to
     * obtain an instance of this class is using create or the service provider
     * mechanism.
     * @see javax.tools.JavaCompilerTool
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

    private String argsToString(Object... args) {
        String newArgs = null;
        if (args.length > 0) {
            StringBuilder sb = new StringBuilder();
            String separator = "";
            for (Object arg : args) {
                sb.append(separator).append(arg.toString());
                separator = File.pathSeparator;
            }
            newArgs = sb.toString();
        }
        return newArgs;
    }

    private void setOption1(String name, OptionKind kind, Object... args) {
        String arg = argsToString(args);
        JavacOption option = sharedCompiler.getOption(name);
        if (option == null || !match(kind, option.getKind()))
            throw new IllegalArgumentException(name);
        if ((args.length != 0) != option.hasArg())
            throw new IllegalArgumentException(name);
        if (option.hasArg()) {
            if (option.process(null, name, arg)) // FIXME
                throw new IllegalArgumentException(name);
        } else {
            if (option.process(null, name)) // FIXME
                throw new IllegalArgumentException(name);
        }
        options.add(new Pair<String,String>(name,arg));
    }

    public void setOption(String name, Object... args) {
        setOption1(name, OptionKind.NORMAL, args);
    }

    public void setExtendedOption(String name, Object... args)  {
        setOption1(name, OptionKind.EXTENDED, args);
    }

    private static boolean match(OptionKind clientKind, OptionKind optionKind) {
        return (clientKind == (optionKind == OptionKind.HIDDEN ? OptionKind.EXTENDED : optionKind));
    }

    public JavacFileManager getStandardFileManager(
        DiagnosticListener<? super JavaFileObject> diagnosticListener,
        Locale locale,
        Charset charset) {
        Context context = new Context();
        JavacMessages.instance(context).setCurrentLocale(locale);
        if (diagnosticListener != null)
            context.put(DiagnosticListener.class, diagnosticListener);
        context.put(Log.outKey, new PrintWriter(System.err, true)); // FIXME
        return new JavacFileManager(context, true, charset);
    }

    private boolean compilationInProgress = false;

    /**
     * Register that a compilation is about to start.
     */
    void beginContext(final Context context) {
        if (compilationInProgress)
            throw new IllegalStateException("Compilation in progress");
        compilationInProgress = true;
        final JavaFileManager givenFileManager = context.get(JavaFileManager.class);
        context.put(JavaFileManager.class, (JavaFileManager)null);
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            public JavaFileManager make() {
                if (givenFileManager != null) {
                    context.put(JavaFileManager.class, givenFileManager);
                    return givenFileManager;
                } else {
                    return new JavacFileManager(context, true, null);
                }
            }
        });
    }

    /**
     * Register that a compilation is completed.
     */
    void endContext() {
        compilationInProgress = false;
    }

    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits)
    {
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
            for (JavaFileObject cu : compilationUnits) {
                if (cu.getKind() != JavaFileObject.Kind.SOURCE) // implicit null check
                    throw new IllegalArgumentException(kindMsg);
            }
        }

        Context context = new Context();

        if (diagnosticListener != null)
            context.put(DiagnosticListener.class, diagnosticListener);

        if (out == null)
            context.put(Log.outKey, new PrintWriter(System.err, true));
        else
            context.put(Log.outKey, new PrintWriter(out, true));

        if (fileManager == null)
            fileManager = getStandardFileManager(diagnosticListener, null, null);
        context.put(JavaFileManager.class, fileManager);
        processOptions(context, fileManager, options);
        Main compiler = new Main("javacTask", context.get(Log.outKey));
        return new JavacTaskImpl(this, compiler, options, context, classes, compilationUnits);
    }

    private static void processOptions(Context context,
                                       JavaFileManager fileManager,
                                       Iterable<String> options)
    {
        if (options == null)
            return;

        Options optionTable = Options.instance(context);

        JavacOption[] recognizedOptions =
            RecognizedOptions.getJavacToolOptions(new GrumpyHelper());
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
                    String msg = Main.getLocalizedString("err.invalid.flag", flag);
                    throw new IllegalArgumentException(msg);
                }
            }

            JavacOption option = recognizedOptions[j];
            if (option.hasArg()) {
                if (!flags.hasNext()) {
                    String msg = Main.getLocalizedString("err.req.arg", flag);
                    throw new IllegalArgumentException(msg);
                }
                String operand = flags.next();
                if (option.process(optionTable, flag, operand))
                    // should not happen as the GrumpyHelper will throw exceptions
                    // in case of errors
                    throw new IllegalArgumentException(flag + " " + operand);
            } else {
                if (option.process(optionTable, flag))
                    // should not happen as the GrumpyHelper will throw exceptions
                    // in case of errors
                    throw new IllegalArgumentException(flag);
            }
        }
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
        JavacOption[] recognizedOptions =
            RecognizedOptions.getJavacToolOptions(new GrumpyHelper());
        for (JavacOption o : recognizedOptions) {
            if (o.matches(option))
                return o.hasArg() ? 1 : 0;
        }
        return -1;
    }

}
