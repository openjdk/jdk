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
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.*;
import com.sun.tools.javac.model.*;
import com.sun.tools.javac.parser.Parser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.main.JavaCompiler;

/**
 * Provides access to functionality specific to the Sun Java Compiler, javac.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 * @author Jonathan Gibbons
 */
public class JavacTaskImpl extends JavacTask {
    private JavacTool tool;
    private Main compilerMain;
    private JavaCompiler compiler;
    private Locale locale;
    private String[] args;
    private Context context;
    private List<JavaFileObject> fileObjects;
    private Map<JavaFileObject, JCCompilationUnit> notYetEntered;
    private ListBuffer<Env<AttrContext>> genList;
    private TaskListener taskListener;
    private AtomicBoolean used = new AtomicBoolean();
    private Iterable<? extends Processor> processors;

    private Integer result = null;

    JavacTaskImpl(JavacTool tool,
                Main compilerMain,
                String[] args,
                Context context,
                List<JavaFileObject> fileObjects) {
        this.tool = tool;
        this.compilerMain = compilerMain;
        this.args = args;
        this.context = context;
        this.fileObjects = fileObjects;
        setLocale(Locale.getDefault());
        // null checks
        compilerMain.getClass();
        args.getClass();
        context.getClass();
        fileObjects.getClass();

        // force the use of the scanner that captures Javadoc comments
        com.sun.tools.javac.parser.DocCommentScanner.Factory.preRegister(context);
    }

    JavacTaskImpl(JavacTool tool,
                Main compilerMain,
                Iterable<String> flags,
                Context context,
                Iterable<String> classes,
                Iterable<? extends JavaFileObject> fileObjects) {
        this(tool, compilerMain, toArray(flags, classes), context, toList(fileObjects));
    }

    static private String[] toArray(Iterable<String> flags, Iterable<String> classes) {
        ListBuffer<String> result = new ListBuffer<String>();
        if (flags != null)
            for (String flag : flags)
                result.append(flag);
        if (classes != null)
            for (String cls : classes)
                result.append(cls);
        return result.toArray(new String[result.length()]);
    }

    static private List<JavaFileObject> toList(Iterable<? extends JavaFileObject> fileObjects) {
        if (fileObjects == null)
            return List.nil();
        ListBuffer<JavaFileObject> result = new ListBuffer<JavaFileObject>();
        for (JavaFileObject fo : fileObjects)
            result.append(fo);
        return result.toList();
    }

    public Boolean call() {
        if (!used.getAndSet(true)) {
            beginContext();
            notYetEntered = new HashMap<JavaFileObject, JCCompilationUnit>();
            try {
                compilerMain.setFatalErrors(true);
                result = compilerMain.compile(args, context, fileObjects, processors);
            } finally {
                endContext();
            }
            compilerMain = null;
            args = null;
            context = null;
            fileObjects = null;
            notYetEntered = null;
            return result == 0;
        } else {
            throw new IllegalStateException("multiple calls to method 'call'");
        }
    }

    public void setProcessors(Iterable<? extends Processor> processors) {
        processors.getClass(); // null check
        // not mt-safe
        if (used.get())
            throw new IllegalStateException();
        this.processors = processors;
    }

    public void setLocale(Locale locale) {
        if (used.get())
            throw new IllegalStateException();
        this.locale = locale;
    }

    private void prepareCompiler() throws IOException {
        if (!used.getAndSet(true)) {
            beginContext();
            compilerMain.setOptions(Options.instance(context));
            compilerMain.filenames = new ListBuffer<File>();
            List<File> filenames = compilerMain.processArgs(CommandLine.parse(args));
            if (!filenames.isEmpty())
                throw new IllegalArgumentException("Malformed arguments " + filenames.toString(" "));
            compiler = JavaCompiler.instance(context);
            compiler.keepComments = true;
            compiler.genEndPos = true;
            // NOTE: this value will be updated after annotation processing
            compiler.initProcessAnnotations(processors);
            notYetEntered = new HashMap<JavaFileObject, JCCompilationUnit>();
            for (JavaFileObject file: fileObjects)
                notYetEntered.put(file, null);
            genList = new ListBuffer<Env<AttrContext>>();
            // endContext will be called when all classes have been generated
            // TODO: should handle the case after each phase if errors have occurred
            args = null;
        }
    }

    private void beginContext() {
        context.put(JavacTaskImpl.class, this);
        if (context.get(TaskListener.class) != null)
            context.put(TaskListener.class, (TaskListener)null);
        if (taskListener != null)
            context.put(TaskListener.class, wrap(taskListener));
        tool.beginContext(context);
        //initialize compiler's default locale
        JavacMessages.instance(context).setCurrentLocale(locale);
    }
    // where
    private TaskListener wrap(final TaskListener tl) {
        tl.getClass(); // null check
        return new TaskListener() {
            public void started(TaskEvent e) {
                try {
                    tl.started(e);
                } catch (Throwable t) {
                    throw new ClientCodeException(t);
                }
            }

            public void finished(TaskEvent e) {
                try {
                    tl.finished(e);
                } catch (Throwable t) {
                    throw new ClientCodeException(t);
                }
            }

        };
    }

    private void endContext() {
        tool.endContext();
    }

    /**
     * Construct a JavaFileObject from the given file.
     *
     * <p><b>TODO: this method is useless here</b></p>
     *
     * @param file a file
     * @return a JavaFileObject from the standard file manager.
     */
    public JavaFileObject asJavaFileObject(File file) {
        JavacFileManager fm = (JavacFileManager)context.get(JavaFileManager.class);
        return fm.getRegularFile(file);
    }

    public void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    /**
     * Parse the specified files returning a list of abstract syntax trees.
     *
     * @throws java.io.IOException TODO
     * @return a list of abstract syntax trees
     */
    public Iterable<? extends CompilationUnitTree> parse() throws IOException {
        try {
            prepareCompiler();
            List<JCCompilationUnit> units = compiler.parseFiles(fileObjects);
            for (JCCompilationUnit unit: units) {
                JavaFileObject file = unit.getSourceFile();
                if (notYetEntered.containsKey(file))
                    notYetEntered.put(file, unit);
            }
            return units;
        }
        finally {
            parsed = true;
            if (compiler != null && compiler.log != null)
                compiler.log.flush();
        }
    }

    private boolean parsed = false;

    /**
     * Translate all the abstract syntax trees to elements.
     *
     * @throws IOException TODO
     * @return a list of elements corresponding to the top level
     * classes in the abstract syntax trees
     */
    public Iterable<? extends TypeElement> enter() throws IOException {
        return enter(null);
    }

    /**
     * Translate the given abstract syntax trees to elements.
     *
     * @param trees a list of abstract syntax trees.
     * @throws java.io.IOException TODO
     * @return a list of elements corresponding to the top level
     * classes in the abstract syntax trees
     */
    public Iterable<? extends TypeElement> enter(Iterable<? extends CompilationUnitTree> trees)
        throws IOException
    {
        prepareCompiler();

        ListBuffer<JCCompilationUnit> roots = null;

        if (trees == null) {
            // If there are still files which were specified to be compiled
            // (i.e. in fileObjects) but which have not yet been entered,
            // then we make sure they have been parsed and add them to the
            // list to be entered.
            if (notYetEntered.size() > 0) {
                if (!parsed)
                    parse(); // TODO would be nice to specify files needed to be parsed
                for (JavaFileObject file: fileObjects) {
                    JCCompilationUnit unit = notYetEntered.remove(file);
                    if (unit != null) {
                        if (roots == null)
                            roots = new ListBuffer<JCCompilationUnit>();
                        roots.append(unit);
                    }
                }
                notYetEntered.clear();
            }
        }
        else {
            for (CompilationUnitTree cu : trees) {
                if (cu instanceof JCCompilationUnit) {
                    if (roots == null)
                        roots = new ListBuffer<JCCompilationUnit>();
                    roots.append((JCCompilationUnit)cu);
                    notYetEntered.remove(cu.getSourceFile());
                }
                else
                    throw new IllegalArgumentException(cu.toString());
            }
        }

        if (roots == null)
            return List.nil();

        try {
            List<JCCompilationUnit> units = compiler.enterTrees(roots.toList());

            if (notYetEntered.isEmpty())
                compiler = compiler.processAnnotations(units);

            ListBuffer<TypeElement> elements = new ListBuffer<TypeElement>();
            for (JCCompilationUnit unit : units) {
                for (JCTree node : unit.defs)
                    if (node.getTag() == JCTree.CLASSDEF)
                        elements.append(((JCTree.JCClassDecl) node).sym);
            }
            return elements.toList();
        }
        finally {
            compiler.log.flush();
        }
    }

    /**
     * Complete all analysis.
     * @throws IOException TODO
     */
    @Override
    public Iterable<? extends Element> analyze() throws IOException {
        return analyze(null);
    }

    /**
     * Complete all analysis on the given classes.
     * This can be used to ensure that all compile time errors are reported.
     * The classes must have previously been returned from {@link #enter}.
     * If null is specified, all outstanding classes will be analyzed.
     *
     * @param classes a list of class elements
     */
    // This implementation requires that we open up privileges on JavaCompiler.
    // An alternative implementation would be to move this code to JavaCompiler and
    // wrap it here
    public Iterable<? extends Element> analyze(Iterable<? extends TypeElement> classes) throws IOException {
        enter(null);  // ensure all classes have been entered

        final ListBuffer<Element> results = new ListBuffer<Element>();
        try {
            if (classes == null) {
                handleFlowResults(compiler.flow(compiler.attribute(compiler.todo)), results);
            } else {
                Filter f = new Filter() {
                    public void process(Env<AttrContext> env) {
                        handleFlowResults(compiler.flow(compiler.attribute(env)), results);
                    }
                };
                f.run(compiler.todo, classes);
            }
        } finally {
            compiler.log.flush();
        }
        return results;
    }
    // where
        private void handleFlowResults(Queue<Env<AttrContext>> queue, ListBuffer<Element> elems) {
            for (Env<AttrContext> env: queue) {
                switch (env.tree.getTag()) {
                    case JCTree.CLASSDEF:
                        JCClassDecl cdef = (JCClassDecl) env.tree;
                        if (cdef.sym != null)
                            elems.append(cdef.sym);
                        break;
                    case JCTree.TOPLEVEL:
                        JCCompilationUnit unit = (JCCompilationUnit) env.tree;
                        if (unit.packge != null)
                            elems.append(unit.packge);
                        break;
                }
            }
            genList.addAll(queue);
        }


    /**
     * Generate code.
     * @throws IOException TODO
     */
    @Override
    public Iterable<? extends JavaFileObject> generate() throws IOException {
        return generate(null);
    }

    /**
     * Generate code corresponding to the given classes.
     * The classes must have previously been returned from {@link #enter}.
     * If there are classes outstanding to be analyzed, that will be done before
     * any classes are generated.
     * If null is specified, code will be generated for all outstanding classes.
     *
     * @param classes a list of class elements
     */
    public Iterable<? extends JavaFileObject> generate(Iterable<? extends TypeElement> classes) throws IOException {
        final ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();
        try {
            analyze(null);  // ensure all classes have been parsed, entered, and analyzed

            if (classes == null) {
                compiler.generate(compiler.desugar(genList), results);
                genList.clear();
            }
            else {
                Filter f = new Filter() {
                        public void process(Env<AttrContext> env) {
                            compiler.generate(compiler.desugar(ListBuffer.of(env)), results);
                        }
                    };
                f.run(genList, classes);
            }
            if (genList.isEmpty()) {
                compiler.reportDeferredDiagnostics();
                compiler.log.flush();
                endContext();
            }
        }
        finally {
            compiler.log.flush();
        }
        return results;
    }

    public TypeMirror getTypeMirror(Iterable<? extends Tree> path) {
        // TODO: Should complete attribution if necessary
        Tree last = null;
        for (Tree node : path)
            last = node;
        return ((JCTree)last).type;
    }

    public JavacElements getElements() {
        if (context == null)
            throw new IllegalStateException();
        return JavacElements.instance(context);
    }

    public JavacTypes getTypes() {
        if (context == null)
            throw new IllegalStateException();
        return JavacTypes.instance(context);
    }

    public Iterable<? extends Tree> pathFor(CompilationUnitTree unit, Tree node) {
        return TreeInfo.pathFor((JCTree) node, (JCTree.JCCompilationUnit) unit).reverse();
    }

    abstract class Filter {
        void run(Queue<Env<AttrContext>> list, Iterable<? extends TypeElement> classes) {
            Set<TypeElement> set = new HashSet<TypeElement>();
            for (TypeElement item: classes)
                set.add(item);

            ListBuffer<Env<AttrContext>> defer = ListBuffer.<Env<AttrContext>>lb();
            while (list.peek() != null) {
                Env<AttrContext> env = list.remove();
                ClassSymbol csym = env.enclClass.sym;
                if (csym != null && set.contains(csym.outermostClass()))
                    process(env);
                else
                    defer = defer.append(env);
            }

            list.addAll(defer);
        }

        abstract void process(Env<AttrContext> env);
    }

    /**
     * For internal use only.  This method will be
     * removed without warning.
     */
    public Context getContext() {
        return context;
    }

    /**
     * For internal use only.  This method will be
     * removed without warning.
     */
    public void updateContext(Context newContext) {
        context = newContext;
    }

    /**
     * For internal use only.  This method will be
     * removed without warning.
     */
    public Type parseType(String expr, TypeElement scope) {
        if (expr == null || expr.equals(""))
            throw new IllegalArgumentException();
        compiler = JavaCompiler.instance(context);
        JavaFileObject prev = compiler.log.useSource(null);
        ParserFactory parserFactory = ParserFactory.instance(context);
        Attr attr = Attr.instance(context);
        try {
            CharBuffer buf = CharBuffer.wrap((expr+"\u0000").toCharArray(), 0, expr.length());
            Parser parser = parserFactory.newParser(buf, false, false, false);
            JCTree tree = parser.parseType();
            return attr.attribType(tree, (Symbol.TypeSymbol)scope);
        } finally {
            compiler.log.useSource(prev);
        }
    }

}
