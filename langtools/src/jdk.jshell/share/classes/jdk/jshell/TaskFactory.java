/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import static jdk.jshell.Util.*;
import com.sun.source.tree.ImportTree;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.JavacMessages;
import jdk.jshell.MemoryFileManager.OutputMemoryJavaFileObject;
import java.util.Collections;
import java.util.Locale;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import jdk.jshell.MemoryFileManager.SourceMemoryJavaFileObject;
import jdk.jshell.ClassTracker.ClassInfo;
import jdk.Version;

/**
 * The primary interface to the compiler API.  Parsing, analysis, and
 * compilation to class files (in memory).
 * @author Robert Field
 */
class TaskFactory {

    private final JavaCompiler compiler;
    private final MemoryFileManager fileManager;
    private final JShell state;
    private String classpath = System.getProperty("java.class.path");
    private final static Version INITIAL_SUPPORTED_VER = Version.parse("9");

    TaskFactory(JShell state) {
        this.state = state;
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new UnsupportedOperationException("Compiler not available, must be run with full JDK 9.");
        }
        Version current = Version.parse(System.getProperty("java.specification.version"));
        if (INITIAL_SUPPORTED_VER.compareToIgnoreOpt(current) > 0)  {
            throw new UnsupportedOperationException("Wrong compiler, must be run with full JDK 9.");
        }
        this.fileManager = new MemoryFileManager(
                compiler.getStandardFileManager(null, null, null), state);
    }

    void addToClasspath(String path) {
        classpath = classpath + File.pathSeparator + path;
        List<String> args = new ArrayList<>();
        args.add(classpath);
        fileManager().handleOption("-classpath", args.iterator());
    }

    MemoryFileManager fileManager() {
        return fileManager;
    }

    private interface SourceHandler<T> {

        JavaFileObject sourceToFileObject(MemoryFileManager fm, T t);

        Diag diag(Diagnostic<? extends JavaFileObject> d);
    }

    private class StringSourceHandler implements SourceHandler<String> {

        @Override
        public JavaFileObject sourceToFileObject(MemoryFileManager fm, String src) {
            return fm.createSourceFileObject(src, "$NeverUsedName$", src);
        }

        @Override
        public Diag diag(final Diagnostic<? extends JavaFileObject> d) {
            return new Diag() {

                @Override
                public boolean isError() {
                    return d.getKind() == Diagnostic.Kind.ERROR;
                }

                @Override
                public long getPosition() {
                    return d.getPosition();
                }

                @Override
                public long getStartPosition() {
                    return d.getStartPosition();
                }

                @Override
                public long getEndPosition() {
                    return d.getEndPosition();
                }

                @Override
                public String getCode() {
                    return d.getCode();
                }

                @Override
                public String getMessage(Locale locale) {
                    return expunge(d.getMessage(locale));
                }
            };
        }
    }

    private class WrapSourceHandler implements SourceHandler<OuterWrap> {

        @Override
        public JavaFileObject sourceToFileObject(MemoryFileManager fm, OuterWrap w) {
            return fm.createSourceFileObject(w, w.classFullName(), w.wrapped());
        }

        @Override
        public Diag diag(Diagnostic<? extends JavaFileObject> d) {
            SourceMemoryJavaFileObject smjfo = (SourceMemoryJavaFileObject) d.getSource();
            OuterWrap w = (OuterWrap) smjfo.getOrigin();
            return w.wrapDiag(d);
        }
    }

    /**
     * Parse a snippet of code (as a String) using the parser subclass.  Return
     * the parse tree (and errors).
     */
    class ParseTask extends BaseTask {

        private final Iterable<? extends CompilationUnitTree> cuts;
        private final List<? extends Tree> units;

        ParseTask(final String source) {
            super(Stream.of(source),
                    new StringSourceHandler(),
                    "-XDallowStringFolding=false", "-proc:none");
            ReplParserFactory.instance(getContext());
            cuts = parse();
            units = Util.stream(cuts)
                    .flatMap(cut -> {
                        List<? extends ImportTree> imps = cut.getImports();
                        return (!imps.isEmpty() ? imps : cut.getTypeDecls()).stream();
                    })
                    .collect(toList());
        }

        private Iterable<? extends CompilationUnitTree> parse() {
            try {
                return task.parse();
            } catch (Exception ex) {
                throw new InternalError("Exception during parse - " + ex.getMessage(), ex);
            }
        }

        List<? extends Tree> units() {
            return units;
        }

        @Override
        Iterable<? extends CompilationUnitTree> cuTrees() {
            return cuts;
        }
    }

    /**
     * Run the normal "analyze()" pass of the compiler over the wrapped snippet.
     */
    class AnalyzeTask extends BaseTask {

        private final Iterable<? extends CompilationUnitTree> cuts;

        AnalyzeTask(final OuterWrap wrap) {
            this(Collections.singletonList(wrap));
        }

        AnalyzeTask(final Collection<OuterWrap> wraps) {
            this(wraps.stream(),
                    new WrapSourceHandler(),
                    "-XDshouldStopPolicy=FLOW", "-Xlint:unchecked", "-XaddExports:jdk.jshell/jdk.internal.jshell.remote=ALL-UNNAMED", "-proc:none");
        }

        <T>AnalyzeTask(final Stream<T> stream, SourceHandler<T> sourceHandler,
                String... extraOptions) {
            super(stream, sourceHandler, extraOptions);
            cuts = analyze();
        }

        private Iterable<? extends CompilationUnitTree> analyze() {
            try {
                Iterable<? extends CompilationUnitTree> cuts = task.parse();
                task.analyze();
                return cuts;
            } catch (Exception ex) {
                throw new InternalError("Exception during analyze - " + ex.getMessage(), ex);
            }
        }

        @Override
        Iterable<? extends CompilationUnitTree> cuTrees() {
            return cuts;
        }

        Elements getElements() {
            return task.getElements();
        }

        javax.lang.model.util.Types getTypes() {
            return task.getTypes();
        }
    }

    /**
     * Unit the wrapped snippet to class files.
     */
    class CompileTask extends BaseTask {

        private final Map<OuterWrap, List<OutputMemoryJavaFileObject>> classObjs = new HashMap<>();

        CompileTask(final Collection<OuterWrap> wraps) {
            super(wraps.stream(), new WrapSourceHandler(),
                    "-Xlint:unchecked", "-XaddExports:jdk.jshell/jdk.internal.jshell.remote=ALL-UNNAMED", "-proc:none");
        }

        boolean compile() {
            fileManager.registerClassFileCreationListener(this::listenForNewClassFile);
            boolean result = task.call();
            fileManager.registerClassFileCreationListener(null);
            return result;
        }


        List<ClassInfo> classInfoList(OuterWrap w) {
            List<OutputMemoryJavaFileObject> l = classObjs.get(w);
            if (l == null) return Collections.emptyList();
            return l.stream()
                    .map(fo -> state.classTracker.classInfo(fo.getName(), fo.getBytes()))
                    .collect(Collectors.toList());
        }

        private void listenForNewClassFile(OutputMemoryJavaFileObject jfo, JavaFileManager.Location location,
                String className, JavaFileObject.Kind kind, FileObject sibling) {
            //debug("listenForNewClassFile %s loc=%s kind=%s\n", className, location, kind);
            if (location == CLASS_OUTPUT) {
                state.debug(DBG_GEN, "Compiler generating class %s\n", className);
                OuterWrap w = ((sibling instanceof SourceMemoryJavaFileObject)
                        && (((SourceMemoryJavaFileObject) sibling).getOrigin() instanceof OuterWrap))
                        ? (OuterWrap) ((SourceMemoryJavaFileObject) sibling).getOrigin()
                        : null;
                classObjs.compute(w, (k, v) -> (v == null)? new ArrayList<>() : v)
                        .add(jfo);
            }
        }

        @Override
        Iterable<? extends CompilationUnitTree> cuTrees() {
            throw new UnsupportedOperationException("Not supported.");
        }
    }

    abstract class BaseTask {

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final JavacTaskImpl task;
        private DiagList diags = null;
        private final SourceHandler<?> sourceHandler;
        private final Context context = new Context();
        private Types types;
        private JavacMessages messages;
        private Trees trees;

        private <T>BaseTask(Stream<T> inputs,
                //BiFunction<MemoryFileManager, T, JavaFileObject> sfoCreator,
                SourceHandler<T> sh,
                String... extraOptions) {
            this.sourceHandler = sh;
            List<String> options = Arrays.asList(extraOptions);
            Iterable<? extends JavaFileObject> compilationUnits = inputs
                            .map(in -> sh.sourceToFileObject(fileManager, in))
                            .collect(Collectors.toList());
            this.task = (JavacTaskImpl) ((JavacTool) compiler).getTask(null,
                    fileManager, diagnostics, options, null,
                    compilationUnits, context);
        }

        abstract Iterable<? extends CompilationUnitTree> cuTrees();

        CompilationUnitTree firstCuTree() {
            return cuTrees().iterator().next();
        }

        Diag diag(Diagnostic<? extends JavaFileObject> diag) {
            return sourceHandler.diag(diag);
        }

        Context getContext() {
            return context;
        }

        Types types() {
            if (types == null) {
                types = Types.instance(context);
            }
            return types;
        }

        JavacMessages messages() {
            if (messages == null) {
                messages = JavacMessages.instance(context);
            }
            return messages;
        }

        Trees trees() {
            if (trees == null) {
                trees = Trees.instance(task);
            }
            return trees;
        }

        // ------------------ diags functionality

        DiagList getDiagnostics() {
            if (diags == null) {
                LinkedHashMap<String, Diag> diagMap = new LinkedHashMap<>();
                for (Diagnostic<? extends JavaFileObject> in : diagnostics.getDiagnostics()) {
                    Diag d = diag(in);
                    String uniqueKey = d.getCode() + ":" + d.getPosition() + ":" + d.getMessage(PARSED_LOCALE);
                    diagMap.put(uniqueKey, d);
                }
                diags = new DiagList(diagMap.values());
            }
            return diags;
        }

        boolean hasErrors() {
            return getDiagnostics().hasErrors();
        }

        String shortErrorMessage() {
            StringBuilder sb = new StringBuilder();
            for (Diag diag : getDiagnostics()) {
                for (String line : diag.getMessage(PARSED_LOCALE).split("\\r?\\n")) {
                    if (!line.trim().startsWith("location:")) {
                        sb.append(line);
                    }
                }
            }
            return sb.toString();
        }

        void debugPrintDiagnostics(String src) {
            for (Diag diag : getDiagnostics()) {
                state.debug(DBG_GEN, "ERROR --\n");
                for (String line : diag.getMessage(PARSED_LOCALE).split("\\r?\\n")) {
                    if (!line.trim().startsWith("location:")) {
                        state.debug(DBG_GEN, "%s\n", line);
                    }
                }
                int start = (int) diag.getStartPosition();
                int end = (int) diag.getEndPosition();
                if (src != null) {
                    String[] srcLines = src.split("\\r?\\n");
                    for (String line : srcLines) {
                        state.debug(DBG_GEN, "%s\n", line);
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < start; ++i) {
                        sb.append(' ');
                    }
                    sb.append('^');
                    if (end > start) {
                        for (int i = start + 1; i < end; ++i) {
                            sb.append('-');
                        }
                        sb.append('^');
                    }
                    state.debug(DBG_GEN, "%s\n", sb.toString());
                }
                state.debug(DBG_GEN, "printDiagnostics start-pos = %d ==> %d -- wrap = %s\n",
                        diag.getStartPosition(), start, this);
                state.debug(DBG_GEN, "Code: %s\n", diag.getCode());
                state.debug(DBG_GEN, "Pos: %d (%d - %d) -- %s\n", diag.getPosition(),
                        diag.getStartPosition(), diag.getEndPosition(), diag.getMessage(null));
            }
        }
    }

}
