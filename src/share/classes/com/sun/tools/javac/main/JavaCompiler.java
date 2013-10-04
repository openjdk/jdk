/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.main;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.comp.CompileStates.CompileState;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.parser.*;
import com.sun.tools.javac.processing.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Log.WriterKind;

import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.main.Option.*;
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag.*;


/** This class could be the main entry point for GJC when GJC is used as a
 *  component in a larger software system. It provides operations to
 *  construct a new compiler, and to run a new compiler on a set of source
 *  files.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavaCompiler {
    /** The context key for the compiler. */
    protected static final Context.Key<JavaCompiler> compilerKey =
        new Context.Key<JavaCompiler>();

    /** Get the JavaCompiler instance for this context. */
    public static JavaCompiler instance(Context context) {
        JavaCompiler instance = context.get(compilerKey);
        if (instance == null)
            instance = new JavaCompiler(context);
        return instance;
    }

    /** The current version number as a string.
     */
    public static String version() {
        return version("release");  // mm.nn.oo[-milestone]
    }

    /** The current full version number as a string.
     */
    public static String fullVersion() {
        return version("full"); // mm.mm.oo[-milestone]-build
    }

    private static final String versionRBName = "com.sun.tools.javac.resources.version";
    private static ResourceBundle versionRB;

    private static String version(String key) {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return Log.getLocalizedString("version.not.available");
            }
        }
        try {
            return versionRB.getString(key);
        }
        catch (MissingResourceException e) {
            return Log.getLocalizedString("version.not.available");
        }
    }

    /**
     * Control how the compiler's latter phases (attr, flow, desugar, generate)
     * are connected. Each individual file is processed by each phase in turn,
     * but with different compile policies, you can control the order in which
     * each class is processed through its next phase.
     *
     * <p>Generally speaking, the compiler will "fail fast" in the face of
     * errors, although not aggressively so. flow, desugar, etc become no-ops
     * once any errors have occurred. No attempt is currently made to determine
     * if it might be safe to process a class through its next phase because
     * it does not depend on any unrelated errors that might have occurred.
     */
    protected static enum CompilePolicy {
        /**
         * Just attribute the parse trees.
         */
        ATTR_ONLY,

        /**
         * Just attribute and do flow analysis on the parse trees.
         * This should catch most user errors.
         */
        CHECK_ONLY,

        /**
         * Attribute everything, then do flow analysis for everything,
         * then desugar everything, and only then generate output.
         * This means no output will be generated if there are any
         * errors in any classes.
         */
        SIMPLE,

        /**
         * Groups the classes for each source file together, then process
         * each group in a manner equivalent to the {@code SIMPLE} policy.
         * This means no output will be generated if there are any
         * errors in any of the classes in a source file.
         */
        BY_FILE,

        /**
         * Completely process each entry on the todo list in turn.
         * -- this is the same for 1.5.
         * Means output might be generated for some classes in a compilation unit
         * and not others.
         */
        BY_TODO;

        static CompilePolicy decode(String option) {
            if (option == null)
                return DEFAULT_COMPILE_POLICY;
            else if (option.equals("attr"))
                return ATTR_ONLY;
            else if (option.equals("check"))
                return CHECK_ONLY;
            else if (option.equals("simple"))
                return SIMPLE;
            else if (option.equals("byfile"))
                return BY_FILE;
            else if (option.equals("bytodo"))
                return BY_TODO;
            else
                return DEFAULT_COMPILE_POLICY;
        }
    }

    private static final CompilePolicy DEFAULT_COMPILE_POLICY = CompilePolicy.BY_TODO;

    protected static enum ImplicitSourcePolicy {
        /** Don't generate or process implicitly read source files. */
        NONE,
        /** Generate classes for implicitly read source files. */
        CLASS,
        /** Like CLASS, but generate warnings if annotation processing occurs */
        UNSET;

        static ImplicitSourcePolicy decode(String option) {
            if (option == null)
                return UNSET;
            else if (option.equals("none"))
                return NONE;
            else if (option.equals("class"))
                return CLASS;
            else
                return UNSET;
        }
    }

    /** The log to be used for error reporting.
     */
    public Log log;

    /** Factory for creating diagnostic objects
     */
    JCDiagnostic.Factory diagFactory;

    /** The tree factory module.
     */
    protected TreeMaker make;

    /** The class reader.
     */
    protected ClassReader reader;

    /** The class writer.
     */
    protected ClassWriter writer;

    /** The native header writer.
     */
    protected JNIWriter jniWriter;

    /** The module for the symbol table entry phases.
     */
    protected Enter enter;

    /** The symbol table.
     */
    protected Symtab syms;

    /** The language version.
     */
    protected Source source;

    /** The module for code generation.
     */
    protected Gen gen;

    /** The name table.
     */
    protected Names names;

    /** The attributor.
     */
    protected Attr attr;

    /** The attributor.
     */
    protected Check chk;

    /** The flow analyzer.
     */
    protected Flow flow;

    /** The type eraser.
     */
    protected TransTypes transTypes;

    /** The lambda translator.
     */
    protected LambdaToMethod lambdaToMethod;

    /** The syntactic sugar desweetener.
     */
    protected Lower lower;

    /** The annotation annotator.
     */
    protected Annotate annotate;

    /** Force a completion failure on this name
     */
    protected final Name completionFailureName;

    /** Type utilities.
     */
    protected Types types;

    /** Access to file objects.
     */
    protected JavaFileManager fileManager;

    /** Factory for parsers.
     */
    protected ParserFactory parserFactory;

    /** Broadcasting listener for progress events
     */
    protected MultiTaskListener taskListener;

    /**
     * Annotation processing may require and provide a new instance
     * of the compiler to be used for the analyze and generate phases.
     */
    protected JavaCompiler delegateCompiler;

    /**
     * SourceCompleter that delegates to the complete-method of this class.
     */
    protected final ClassReader.SourceCompleter thisCompleter =
            new ClassReader.SourceCompleter() {
                @Override
                public void complete(ClassSymbol sym) throws CompletionFailure {
                    JavaCompiler.this.complete(sym);
                }
            };

    /**
     * Command line options.
     */
    protected Options options;

    protected Context context;

    /**
     * Flag set if any annotation processing occurred.
     **/
    protected boolean annotationProcessingOccurred;

    /**
     * Flag set if any implicit source files read.
     **/
    protected boolean implicitSourceFilesRead;

    protected CompileStates compileStates;

    /** Construct a new compiler using a shared context.
     */
    public JavaCompiler(Context context) {
        this.context = context;
        context.put(compilerKey, this);

        // if fileManager not already set, register the JavacFileManager to be used
        if (context.get(JavaFileManager.class) == null)
            JavacFileManager.preRegister(context);

        names = Names.instance(context);
        log = Log.instance(context);
        diagFactory = JCDiagnostic.Factory.instance(context);
        reader = ClassReader.instance(context);
        make = TreeMaker.instance(context);
        writer = ClassWriter.instance(context);
        jniWriter = JNIWriter.instance(context);
        enter = Enter.instance(context);
        todo = Todo.instance(context);

        fileManager = context.get(JavaFileManager.class);
        parserFactory = ParserFactory.instance(context);
        compileStates = CompileStates.instance(context);

        try {
            // catch completion problems with predefineds
            syms = Symtab.instance(context);
        } catch (CompletionFailure ex) {
            // inlined Check.completionError as it is not initialized yet
            log.error("cant.access", ex.sym, ex.getDetailValue());
            if (ex instanceof ClassReader.BadClassFile)
                throw new Abort();
        }
        source = Source.instance(context);
        Target target = Target.instance(context);
        attr = Attr.instance(context);
        chk = Check.instance(context);
        gen = Gen.instance(context);
        flow = Flow.instance(context);
        transTypes = TransTypes.instance(context);
        lower = Lower.instance(context);
        annotate = Annotate.instance(context);
        types = Types.instance(context);
        taskListener = MultiTaskListener.instance(context);

        reader.sourceCompleter = thisCompleter;

        options = Options.instance(context);

        lambdaToMethod = LambdaToMethod.instance(context);

        verbose       = options.isSet(VERBOSE);
        sourceOutput  = options.isSet(PRINTSOURCE); // used to be -s
        stubOutput    = options.isSet("-stubs");
        relax         = options.isSet("-relax");
        printFlat     = options.isSet("-printflat");
        attrParseOnly = options.isSet("-attrparseonly");
        encoding      = options.get(ENCODING);
        lineDebugInfo = options.isUnset(G_CUSTOM) ||
                        options.isSet(G_CUSTOM, "lines");
        genEndPos     = options.isSet(XJCOV) ||
                        context.get(DiagnosticListener.class) != null;
        devVerbose    = options.isSet("dev");
        processPcks   = options.isSet("process.packages");
        werror        = options.isSet(WERROR);

        if (source.compareTo(Source.DEFAULT) < 0) {
            if (options.isUnset(XLINT_CUSTOM, "-" + LintCategory.OPTIONS.option)) {
                if (fileManager instanceof BaseFileManager) {
                    if (((BaseFileManager) fileManager).isDefaultBootClassPath())
                        log.warning(LintCategory.OPTIONS, "source.no.bootclasspath", source.name);
                }
            }
        }

        checkForObsoleteOptions(target);

        verboseCompilePolicy = options.isSet("verboseCompilePolicy");

        if (attrParseOnly)
            compilePolicy = CompilePolicy.ATTR_ONLY;
        else
            compilePolicy = CompilePolicy.decode(options.get("compilePolicy"));

        implicitSourcePolicy = ImplicitSourcePolicy.decode(options.get("-implicit"));

        completionFailureName =
            options.isSet("failcomplete")
            ? names.fromString(options.get("failcomplete"))
            : null;

        shouldStopPolicyIfError =
            options.isSet("shouldStopPolicy") // backwards compatible
            ? CompileState.valueOf(options.get("shouldStopPolicy"))
            : options.isSet("shouldStopPolicyIfError")
            ? CompileState.valueOf(options.get("shouldStopPolicyIfError"))
            : CompileState.INIT;
        shouldStopPolicyIfNoError =
            options.isSet("shouldStopPolicyIfNoError")
            ? CompileState.valueOf(options.get("shouldStopPolicyIfNoError"))
            : CompileState.GENERATE;

        if (options.isUnset("oldDiags"))
            log.setDiagnosticFormatter(RichDiagnosticFormatter.instance(context));
    }

    private void checkForObsoleteOptions(Target target) {
        // Unless lint checking on options is disabled, check for
        // obsolete source and target options.
        boolean obsoleteOptionFound = false;
        if (options.isUnset(XLINT_CUSTOM, "-" + LintCategory.OPTIONS.option)) {
            if (source.compareTo(Source.JDK1_5) <= 0) {
                log.warning(LintCategory.OPTIONS, "option.obsolete.source", source.name);
                obsoleteOptionFound = true;
            }

            if (target.compareTo(Target.JDK1_5) <= 0) {
                log.warning(LintCategory.OPTIONS, "option.obsolete.target", target.name);
                obsoleteOptionFound = true;
            }

            if (obsoleteOptionFound)
                log.warning(LintCategory.OPTIONS, "option.obsolete.suppression");
        }
    }

    /* Switches:
     */

    /** Verbose output.
     */
    public boolean verbose;

    /** Emit plain Java source files rather than class files.
     */
    public boolean sourceOutput;

    /** Emit stub source files rather than class files.
     */
    public boolean stubOutput;

    /** Generate attributed parse tree only.
     */
    public boolean attrParseOnly;

    /** Switch: relax some constraints for producing the jsr14 prototype.
     */
    boolean relax;

    /** Debug switch: Emit Java sources after inner class flattening.
     */
    public boolean printFlat;

    /** The encoding to be used for source input.
     */
    public String encoding;

    /** Generate code with the LineNumberTable attribute for debugging
     */
    public boolean lineDebugInfo;

    /** Switch: should we store the ending positions?
     */
    public boolean genEndPos;

    /** Switch: should we debug ignored exceptions
     */
    protected boolean devVerbose;

    /** Switch: should we (annotation) process packages as well
     */
    protected boolean processPcks;

    /** Switch: treat warnings as errors
     */
    protected boolean werror;

    /** Switch: is annotation processing requested explicitly via
     * CompilationTask.setProcessors?
     */
    protected boolean explicitAnnotationProcessingRequested = false;

    /**
     * The policy for the order in which to perform the compilation
     */
    protected CompilePolicy compilePolicy;

    /**
     * The policy for what to do with implicitly read source files
     */
    protected ImplicitSourcePolicy implicitSourcePolicy;

    /**
     * Report activity related to compilePolicy
     */
    public boolean verboseCompilePolicy;

    /**
     * Policy of how far to continue compilation after errors have occurred.
     * Set this to minimum CompileState (INIT) to stop as soon as possible
     * after errors.
     */
    public CompileState shouldStopPolicyIfError;

    /**
     * Policy of how far to continue compilation when no errors have occurred.
     * Set this to maximum CompileState (GENERATE) to perform full compilation.
     * Set this lower to perform partial compilation, such as -proc:only.
     */
    public CompileState shouldStopPolicyIfNoError;

    /** A queue of all as yet unattributed classes.
     */
    public Todo todo;

    /** A list of items to be closed when the compilation is complete.
     */
    public List<Closeable> closeables = List.nil();

    /** The set of currently compiled inputfiles, needed to ensure
     *  we don't accidentally overwrite an input file when -s is set.
     *  initialized by `compile'.
     */
    protected Set<JavaFileObject> inputFiles = new HashSet<JavaFileObject>();

    protected boolean shouldStop(CompileState cs) {
        CompileState shouldStopPolicy = (errorCount() > 0 || unrecoverableError())
            ? shouldStopPolicyIfError
            : shouldStopPolicyIfNoError;
        return cs.isAfter(shouldStopPolicy);
    }

    /** The number of errors reported so far.
     */
    public int errorCount() {
        if (delegateCompiler != null && delegateCompiler != this)
            return delegateCompiler.errorCount();
        else {
            if (werror && log.nerrors == 0 && log.nwarnings > 0) {
                log.error("warnings.and.werror");
            }
        }
        return log.nerrors;
    }

    protected final <T> Queue<T> stopIfError(CompileState cs, Queue<T> queue) {
        return shouldStop(cs) ? new ListBuffer<T>() : queue;
    }

    protected final <T> List<T> stopIfError(CompileState cs, List<T> list) {
        return shouldStop(cs) ? List.<T>nil() : list;
    }

    /** The number of warnings reported so far.
     */
    public int warningCount() {
        if (delegateCompiler != null && delegateCompiler != this)
            return delegateCompiler.warningCount();
        else
            return log.nwarnings;
    }

    /** Try to open input stream with given name.
     *  Report an error if this fails.
     *  @param filename   The file name of the input stream to be opened.
     */
    public CharSequence readSource(JavaFileObject filename) {
        try {
            inputFiles.add(filename);
            return filename.getCharContent(false);
        } catch (IOException e) {
            log.error("error.reading.file", filename, JavacFileManager.getMessage(e));
            return null;
        }
    }

    /** Parse contents of input stream.
     *  @param filename     The name of the file from which input stream comes.
     *  @param content      The characters to be parsed.
     */
    protected JCCompilationUnit parse(JavaFileObject filename, CharSequence content) {
        long msec = now();
        JCCompilationUnit tree = make.TopLevel(List.<JCTree.JCAnnotation>nil(),
                                      null, List.<JCTree>nil());
        if (content != null) {
            if (verbose) {
                log.printVerbose("parsing.started", filename);
            }
            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.PARSE, filename);
                taskListener.started(e);
                keepComments = true;
                genEndPos = true;
            }
            Parser parser = parserFactory.newParser(content, keepComments(), genEndPos, lineDebugInfo);
            tree = parser.parseCompilationUnit();
            if (verbose) {
                log.printVerbose("parsing.done", Long.toString(elapsed(msec)));
            }
        }

        tree.sourcefile = filename;

        if (content != null && !taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.PARSE, tree);
            taskListener.finished(e);
        }

        return tree;
    }
    // where
        public boolean keepComments = false;
        protected boolean keepComments() {
            return keepComments || sourceOutput || stubOutput;
        }


    /** Parse contents of file.
     *  @param filename     The name of the file to be parsed.
     */
    @Deprecated
    public JCTree.JCCompilationUnit parse(String filename) {
        JavacFileManager fm = (JavacFileManager)fileManager;
        return parse(fm.getJavaFileObjectsFromStrings(List.of(filename)).iterator().next());
    }

    /** Parse contents of file.
     *  @param filename     The name of the file to be parsed.
     */
    public JCTree.JCCompilationUnit parse(JavaFileObject filename) {
        JavaFileObject prev = log.useSource(filename);
        try {
            JCTree.JCCompilationUnit t = parse(filename, readSource(filename));
            if (t.endPositions != null)
                log.setEndPosTable(filename, t.endPositions);
            return t;
        } finally {
            log.useSource(prev);
        }
    }

    /** Resolve an identifier which may be the binary name of a class or
     * the Java name of a class or package.
     * @param name      The name to resolve
     */
    public Symbol resolveBinaryNameOrIdent(String name) {
        try {
            Name flatname = names.fromString(name.replace("/", "."));
            return reader.loadClass(flatname);
        } catch (CompletionFailure ignore) {
            return resolveIdent(name);
        }
    }

    /** Resolve an identifier.
     * @param name      The identifier to resolve
     */
    public Symbol resolveIdent(String name) {
        if (name.equals(""))
            return syms.errSymbol;
        JavaFileObject prev = log.useSource(null);
        try {
            JCExpression tree = null;
            for (String s : name.split("\\.", -1)) {
                if (!SourceVersion.isIdentifier(s)) // TODO: check for keywords
                    return syms.errSymbol;
                tree = (tree == null) ? make.Ident(names.fromString(s))
                                      : make.Select(tree, names.fromString(s));
            }
            JCCompilationUnit toplevel =
                make.TopLevel(List.<JCTree.JCAnnotation>nil(), null, List.<JCTree>nil());
            toplevel.packge = syms.unnamedPackage;
            return attr.attribIdent(tree, toplevel);
        } finally {
            log.useSource(prev);
        }
    }

    /** Emit plain Java source for a class.
     *  @param env    The attribution environment of the outermost class
     *                containing this class.
     *  @param cdef   The class definition to be printed.
     */
    JavaFileObject printSource(Env<AttrContext> env, JCClassDecl cdef) throws IOException {
        JavaFileObject outFile
            = fileManager.getJavaFileForOutput(CLASS_OUTPUT,
                                               cdef.sym.flatname.toString(),
                                               JavaFileObject.Kind.SOURCE,
                                               null);
        if (inputFiles.contains(outFile)) {
            log.error(cdef.pos(), "source.cant.overwrite.input.file", outFile);
            return null;
        } else {
            BufferedWriter out = new BufferedWriter(outFile.openWriter());
            try {
                new Pretty(out, true).printUnit(env.toplevel, cdef);
                if (verbose)
                    log.printVerbose("wrote.file", outFile);
            } finally {
                out.close();
            }
            return outFile;
        }''
    }

    /** Generate code and emit a class file for a given class
     *  @param env    The attribution environment of the outermost class
     *                containing this class.
     *  @param cdef   The class definition from which code is generated.
     */
    JavaFileObject genCode(Env<AttrContext> env, JCClassDecl cdef) throws IOException {
        try {
            if (gen.genClass(env, cdef) && (errorCount() == 0))
                return writer.writeClass(cdef.sym);
        } catch (ClassWriter.PoolOverflow ex) {
            log.error(cdef.pos(), "limit.pool");
        } catch (ClassWriter.StringOverflow ex) {
            log.error(cdef.pos(), "limit.string.overflow",
                      ex.value.substring(0, 20));
        } catch (CompletionFailure ex) {
            chk.completionError(cdef.pos(), ex);
        }
        return null;
    }

    /** Complete compiling a source file that has been accessed
     *  by the class file reader.
     *  @param c          The class the source file of which needs to be compiled.
     */
    public void complete(ClassSymbol c) throws CompletionFailure {
//      System.err.println("completing " + c);//DEBUG
        if (completionFailureName == c.fullname) {
            throw new CompletionFailure(c, "user-selected completion failure by class name");
        }
        JCCompilationUnit tree;
        JavaFileObject filename = c.classfile;
        JavaFileObject prev = log.useSource(filename);

        try {
            tree = parse(filename, filename.getCharContent(false));
        } catch (IOException e) {
            log.error("error.reading.file", filename, JavacFileManager.getMessage(e));
            tree = make.TopLevel(List.<JCTree.JCAnnotation>nil(), null, List.<JCTree>nil());
        } finally {
            log.useSource(prev);
        }

        if (!taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, tree);
            taskListener.started(e);
        }

        enter.complete(List.of(tree), c);

        if (!taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, tree);
            taskListener.finished(e);
        }

        if (enter.getEnv(c) == null) {
            boolean isPkgInfo =
                tree.sourcefile.isNameCompatible("package-info",
                                                 JavaFileObject.Kind.SOURCE);
            if (isPkgInfo) {
                if (enter.getEnv(tree.packge) == null) {
                    JCDiagnostic diag =
                        diagFactory.fragment("file.does.not.contain.package",
                                                 c.location());
                    throw reader.new BadClassFile(c, filename, diag);
                }
            } else {
                JCDiagnostic diag =
                        diagFactory.fragment("file.doesnt.contain.class",
                                            c.getQualifiedName());
                throw reader.new BadClassFile(c, filename, diag);
            }
        }

        implicitSourceFilesRead = true;
    }

    /** Track when the JavaCompiler has been used to compile something. */
    private boolean hasBeenUsed = false;
    private long start_msec = 0;
    public long elapsed_msec = 0;

    public void compile(List<JavaFileObject> sourceFileObject)
        throws Throwable {
        compile(sourceFileObject, List.<String>nil(), null);
    }

    /**
     * Main method: compile a list of files, return all compiled classes
     *
     * @param sourceFileObjects file objects to be compiled
     * @param classnames class names to process for annotations
     * @param processors user provided annotation processors to bypass
     * discovery, {@code null} means that no processors were provided
     */
    public void compile(List<JavaFileObject> sourceFileObjects,
                        List<String> classnames,
                        Iterable<? extends Processor> processors)
    {
        if (processors != null && processors.iterator().hasNext())
            explicitAnnotationProcessingRequested = true;
        // as a JavaCompiler can only be used once, throw an exception if
        // it has been used before.
        if (hasBeenUsed)
            throw new AssertionError("attempt to reuse JavaCompiler");
        hasBeenUsed = true;

        // forcibly set the equivalent of -Xlint:-options, so that no further
        // warnings about command line options are generated from this point on
        options.put(XLINT_CUSTOM.text + "-" + LintCategory.OPTIONS.option, "true");
        options.remove(XLINT_CUSTOM.text + LintCategory.OPTIONS.option);

        start_msec = now();

        try {
            initProcessAnnotations(processors);

            // These method calls must be chained to avoid memory leaks
            delegateCompiler =
                processAnnotations(
                    enterTrees(stopIfError(CompileState.PARSE, parseFiles(sourceFileObjects))),
                    classnames);

            delegateCompiler.compile2();
            delegateCompiler.close();
            elapsed_msec = delegateCompiler.elapsed_msec;
        } catch (Abort ex) {
            if (devVerbose)
                ex.printStackTrace(System.err);
        } finally {
            if (procEnvImpl != null)
                procEnvImpl.close();
        }
    }

    /**
     * The phases following annotation processing: attribution,
     * desugar, and finally code generation.
     */
    private void compile2() {
        try {
            switch (compilePolicy) {
            case ATTR_ONLY:
                attribute(todo);
                break;

            case CHECK_ONLY:
                flow(attribute(todo));
                break;

            case SIMPLE:
                generate(desugar(flow(attribute(todo))));
                break;

            case BY_FILE: {
                    Queue<Queue<Env<AttrContext>>> q = todo.groupByFile();
                    while (!q.isEmpty() && !shouldStop(CompileState.ATTR)) {
                        generate(desugar(flow(attribute(q.remove()))));
                    }
                }
                break;

            case BY_TODO:
                while (!todo.isEmpty())
                    generate(desugar(flow(attribute(todo.remove()))));
                break;

            default:
                Assert.error("unknown compile policy");
            }
        } catch (Abort ex) {
            if (devVerbose)
                ex.printStackTrace(System.err);
        }

        if (verbose) {
            elapsed_msec = elapsed(start_msec);
            log.printVerbose("total", Long.toString(elapsed_msec));
        }

        reportDeferredDiagnostics();

        if (!log.hasDiagnosticListener()) {
            printCount("error", errorCount());
            printCount("warn", warningCount());
        }
    }

    /**
     * Set needRootClasses to true, in JavaCompiler subclass constructor
     * that want to collect public apis of classes supplied on the command line.
     */
    protected boolean needRootClasses = false;

    /**
     * The list of classes explicitly supplied on the command line for compilation.
     * Not always populated.
     */
    private List<JCClassDecl> rootClasses;

    /**
     * Parses a list of files.
     */
   public List<JCCompilationUnit> parseFiles(Iterable<JavaFileObject> fileObjects) {
       if (shouldStop(CompileState.PARSE))
           return List.nil();

        //parse all files
        ListBuffer<JCCompilationUnit> trees = new ListBuffer<>();
        Set<JavaFileObject> filesSoFar = new HashSet<JavaFileObject>();
        for (JavaFileObject fileObject : fileObjects) {
            if (!filesSoFar.contains(fileObject)) {
                filesSoFar.add(fileObject);
                trees.append(parse(fileObject));
            }
        }
        return trees.toList();
    }

    /**
     * Enter the symbols found in a list of parse trees if the compilation
     * is expected to proceed beyond anno processing into attr.
     * As a side-effect, this puts elements on the "todo" list.
     * Also stores a list of all top level classes in rootClasses.
     */
    public List<JCCompilationUnit> enterTreesIfNeeded(List<JCCompilationUnit> roots) {
       if (shouldStop(CompileState.ATTR))
           return List.nil();
        return enterTrees(roots);
    }

    /**
     * Enter the symbols found in a list of parse trees.
     * As a side-effect, this puts elements on the "todo" list.
     * Also stores a list of all top level classes in rootClasses.
     */
    public List<JCCompilationUnit> enterTrees(List<JCCompilationUnit> roots) {
        //enter symbols for all files
        if (!taskListener.isEmpty()) {
            for (JCCompilationUnit unit: roots) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, unit);
                taskListener.started(e);
            }
        }

        enter.main(roots);

        if (!taskListener.isEmpty()) {
            for (JCCompilationUnit unit: roots) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, unit);
                taskListener.finished(e);
            }
        }

        // If generating source, or if tracking public apis,
        // then remember the classes declared in
        // the original compilation units listed on the command line.
        if (needRootClasses || sourceOutput || stubOutput) {
            ListBuffer<JCClassDecl> cdefs = new ListBuffer<>();
            for (JCCompilationUnit unit : roots) {
                for (List<JCTree> defs = unit.defs;
                     defs.nonEmpty();
                     defs = defs.tail) {
                    if (defs.head instanceof JCClassDecl)
                        cdefs.append((JCClassDecl)defs.head);
                }
            }
            rootClasses = cdefs.toList();
        }

        // Ensure the input files have been recorded. Although this is normally
        // done by readSource, it may not have been done if the trees were read
        // in a prior round of annotation processing, and the trees have been
        // cleaned and are being reused.
        for (JCCompilationUnit unit : roots) {
            inputFiles.add(unit.sourcefile);
        }

        return roots;
    }

    /**
     * Set to true to enable skeleton annotation processing code.
     * Currently, we assume this variable will be replaced more
     * advanced logic to figure out if annotation processing is
     * needed.
     */
    boolean processAnnotations = false;

    Log.DeferredDiagnosticHandler deferredDiagnosticHandler;

    /**
     * Object to handle annotation processing.
     */
    private JavacProcessingEnvironment procEnvImpl = null;

    /**
     * Check if we should process annotations.
     * If so, and if no scanner is yet registered, then set up the DocCommentScanner
     * to catch doc comments, and set keepComments so the parser records them in
     * the compilation unit.
     *
     * @param processors user provided annotation processors to bypass
     * discovery, {@code null} means that no processors were provided
     */
    public void initProcessAnnotations(Iterable<? extends Processor> processors) {
        // Process annotations if processing is not disabled and there
        // is at least one Processor available.
        if (options.isSet(PROC, "none")) {
            processAnnotations = false;
        } else if (procEnvImpl == null) {
            procEnvImpl = JavacProcessingEnvironment.instance(context);
            procEnvImpl.setProcessors(processors);
            processAnnotations = procEnvImpl.atLeastOneProcessor();

            if (processAnnotations) {
                options.put("save-parameter-names", "save-parameter-names");
                reader.saveParameterNames = true;
                keepComments = true;
                genEndPos = true;
                if (!taskListener.isEmpty())
                    taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));
                deferredDiagnosticHandler = new Log.DeferredDiagnosticHandler(log);
            } else { // free resources
                procEnvImpl.close();
            }
        }
    }

    // TODO: called by JavacTaskImpl
    public JavaCompiler processAnnotations(List<JCCompilationUnit> roots) {
        return processAnnotations(roots, List.<String>nil());
    }

    /**
     * Process any annotations found in the specified compilation units.
     * @param roots a list of compilation units
     * @return an instance of the compiler in which to complete the compilation
     */
    // Implementation note: when this method is called, log.deferredDiagnostics
    // will have been set true by initProcessAnnotations, meaning that any diagnostics
    // that are reported will go into the log.deferredDiagnostics queue.
    // By the time this method exits, log.deferDiagnostics must be set back to false,
    // and all deferredDiagnostics must have been handled: i.e. either reported
    // or determined to be transient, and therefore suppressed.
    public JavaCompiler processAnnotations(List<JCCompilationUnit> roots,
                                           List<String> classnames) {
        if (shouldStop(CompileState.PROCESS)) {
            // Errors were encountered.
            // Unless all the errors are resolve errors, the errors were parse errors
            // or other errors during enter which cannot be fixed by running
            // any annotation processors.
            if (unrecoverableError()) {
                deferredDiagnosticHandler.reportDeferredDiagnostics();
                log.popDiagnosticHandler(deferredDiagnosticHandler);
                return this;
            }
        }

        // ASSERT: processAnnotations and procEnvImpl should have been set up by
        // by initProcessAnnotations

        // NOTE: The !classnames.isEmpty() checks should be refactored to Main.

        if (!processAnnotations) {
            // If there are no annotation processors present, and
            // annotation processing is to occur with compilation,
            // emit a warning.
            if (options.isSet(PROC, "only")) {
                log.warning("proc.proc-only.requested.no.procs");
                todo.clear();
            }
            // If not processing annotations, classnames must be empty
            if (!classnames.isEmpty()) {
                log.error("proc.no.explicit.annotation.processing.requested",
                          classnames);
            }
            Assert.checkNull(deferredDiagnosticHandler);
            return this; // continue regular compilation
        }

        Assert.checkNonNull(deferredDiagnosticHandler);

        try {
            List<ClassSymbol> classSymbols = List.nil();
            List<PackageSymbol> pckSymbols = List.nil();
            if (!classnames.isEmpty()) {
                 // Check for explicit request for annotation
                 // processing
                if (!explicitAnnotationProcessingRequested()) {
                    log.error("proc.no.explicit.annotation.processing.requested",
                              classnames);
                    deferredDiagnosticHandler.reportDeferredDiagnostics();
                    log.popDiagnosticHandler(deferredDiagnosticHandler);
                    return this; // TODO: Will this halt compilation?
                } else {
                    boolean errors = false;
                    for (String nameStr : classnames) {
                        Symbol sym = resolveBinaryNameOrIdent(nameStr);
                        if (sym == null ||
                            (sym.kind == Kinds.PCK && !processPcks) ||
                            sym.kind == Kinds.ABSENT_TYP) {
                            log.error("proc.cant.find.class", nameStr);
                            errors = true;
                            continue;
                        }
                        try {
                            if (sym.kind == Kinds.PCK)
                                sym.complete();
                            if (sym.exists()) {
                                if (sym.kind == Kinds.PCK)
                                    pckSymbols = pckSymbols.prepend((PackageSymbol)sym);
                                else
                                    classSymbols = classSymbols.prepend((ClassSymbol)sym);
                                continue;
                            }
                            Assert.check(sym.kind == Kinds.PCK);
                            log.warning("proc.package.does.not.exist", nameStr);
                            pckSymbols = pckSymbols.prepend((PackageSymbol)sym);
                        } catch (CompletionFailure e) {
                            log.error("proc.cant.find.class", nameStr);
                            errors = true;
                            continue;
                        }
                    }
                    if (errors) {
                        deferredDiagnosticHandler.reportDeferredDiagnostics();
                        log.popDiagnosticHandler(deferredDiagnosticHandler);
                        return this;
                    }
                }
            }
            try {
                JavaCompiler c = procEnvImpl.doProcessing(context, roots, classSymbols, pckSymbols,
                        deferredDiagnosticHandler);
                if (c != this)
                    annotationProcessingOccurred = c.annotationProcessingOccurred = true;
                // doProcessing will have handled deferred diagnostics
                return c;
            } finally {
                procEnvImpl.close();
            }
        } catch (CompletionFailure ex) {
            log.error("cant.access", ex.sym, ex.getDetailValue());
            deferredDiagnosticHandler.reportDeferredDiagnostics();
            log.popDiagnosticHandler(deferredDiagnosticHandler);
            return this;
        }
    }

    private boolean unrecoverableError() {
        if (deferredDiagnosticHandler != null) {
            for (JCDiagnostic d: deferredDiagnosticHandler.getDiagnostics()) {
                if (d.getKind() == JCDiagnostic.Kind.ERROR && !d.isFlagSet(RECOVERABLE))
                    return true;
            }
        }
        return false;
    }

    boolean explicitAnnotationProcessingRequested() {
        return
            explicitAnnotationProcessingRequested ||
            explicitAnnotationProcessingRequested(options);
    }

    static boolean explicitAnnotationProcessingRequested(Options options) {
        return
            options.isSet(PROCESSOR) ||
            options.isSet(PROCESSORPATH) ||
            options.isSet(PROC, "only") ||
            options.isSet(XPRINT);
    }

    /**
     * Attribute a list of parse trees, such as found on the "todo" list.
     * Note that attributing classes may cause additional files to be
     * parsed and entered via the SourceCompleter.
     * Attribution of the entries in the list does not stop if any errors occur.
     * @returns a list of environments for attributd classes.
     */
    public Queue<Env<AttrContext>> attribute(Queue<Env<AttrContext>> envs) {
        ListBuffer<Env<AttrContext>> results = new ListBuffer<>();
        while (!envs.isEmpty())
            results.append(attribute(envs.remove()));
        return stopIfError(CompileState.ATTR, results);
    }

    /**
     * Attribute a parse tree.
     * @returns the attributed parse tree
     */
    public Env<AttrContext> attribute(Env<AttrContext> env) {
        if (compileStates.isDone(env, CompileState.ATTR))
            return env;

        if (verboseCompilePolicy)
            printNote("[attribute " + env.enclClass.sym + "]");
        if (verbose)
            log.printVerbose("checking.attribution", env.enclClass.sym);

        if (!taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.ANALYZE, env.toplevel, env.enclClass.sym);
            taskListener.started(e);
        }

        JavaFileObject prev = log.useSource(
                                  env.enclClass.sym.sourcefile != null ?
                                  env.enclClass.sym.sourcefile :
                                  env.toplevel.sourcefile);
        try {
            attr.attrib(env);
            if (errorCount() > 0 && !shouldStop(CompileState.ATTR)) {
                //if in fail-over mode, ensure that AST expression nodes
                //are correctly initialized (e.g. they have a type/symbol)
                attr.postAttr(env.tree);
            }
            compileStates.put(env, CompileState.ATTR);
            if (rootClasses != null && rootClasses.contains(env.enclClass)) {
                // This was a class that was explicitly supplied for compilation.
                // If we want to capture the public api of this class,
                // then now is a good time to do it.
                reportPublicApi(env.enclClass.sym);
            }
        }
        finally {
            log.useSource(prev);
        }

        return env;
    }

    /** Report the public api of a class that was supplied explicitly for compilation,
     *  for example on the command line to javac.
     * @param sym The symbol of the class.
     */
    public void reportPublicApi(ClassSymbol sym) {
       // Override to collect the reported public api.
    }

    /**
     * Perform dataflow checks on attributed parse trees.
     * These include checks for definite assignment and unreachable statements.
     * If any errors occur, an empty list will be returned.
     * @returns the list of attributed parse trees
     */
    public Queue<Env<AttrContext>> flow(Queue<Env<AttrContext>> envs) {
        ListBuffer<Env<AttrContext>> results = new ListBuffer<>();
        for (Env<AttrContext> env: envs) {
            flow(env, results);
        }
        return stopIfError(CompileState.FLOW, results);
    }

    /**
     * Perform dataflow checks on an attributed parse tree.
     */
    public Queue<Env<AttrContext>> flow(Env<AttrContext> env) {
        ListBuffer<Env<AttrContext>> results = new ListBuffer<>();
        flow(env, results);
        return stopIfError(CompileState.FLOW, results);
    }

    /**
     * Perform dataflow checks on an attributed parse tree.
     */
    protected void flow(Env<AttrContext> env, Queue<Env<AttrContext>> results) {
        try {
            if (shouldStop(CompileState.FLOW))
                return;

            if (relax || compileStates.isDone(env, CompileState.FLOW)) {
                results.add(env);
                return;
            }

            if (verboseCompilePolicy)
                printNote("[flow " + env.enclClass.sym + "]");
            JavaFileObject prev = log.useSource(
                                                env.enclClass.sym.sourcefile != null ?
                                                env.enclClass.sym.sourcefile :
                                                env.toplevel.sourcefile);
            try {
                make.at(Position.FIRSTPOS);
                TreeMaker localMake = make.forToplevel(env.toplevel);
                flow.analyzeTree(env, localMake);
                compileStates.put(env, CompileState.FLOW);

                if (shouldStop(CompileState.FLOW))
                    return;

                results.add(env);
            }
            finally {
                log.useSource(prev);
            }
        }
        finally {
            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.ANALYZE, env.toplevel, env.enclClass.sym);
                taskListener.finished(e);
            }
        }
    }

    /**
     * Prepare attributed parse trees, in conjunction with their attribution contexts,
     * for source or code generation.
     * If any errors occur, an empty list will be returned.
     * @returns a list containing the classes to be generated
     */
    public Queue<Pair<Env<AttrContext>, JCClassDecl>> desugar(Queue<Env<AttrContext>> envs) {
        ListBuffer<Pair<Env<AttrContext>, JCClassDecl>> results = new ListBuffer<>();
        for (Env<AttrContext> env: envs)
            desugar(env, results);
        return stopIfError(CompileState.FLOW, results);
    }

    HashMap<Env<AttrContext>, Queue<Pair<Env<AttrContext>, JCClassDecl>>> desugaredEnvs =
            new HashMap<Env<AttrContext>, Queue<Pair<Env<AttrContext>, JCClassDecl>>>();

    /**
     * Prepare attributed parse trees, in conjunction with their attribution contexts,
     * for source or code generation. If the file was not listed on the command line,
     * the current implicitSourcePolicy is taken into account.
     * The preparation stops as soon as an error is found.
     */
    protected void desugar(final Env<AttrContext> env, Queue<Pair<Env<AttrContext>, JCClassDecl>> results) {
        if (shouldStop(CompileState.TRANSTYPES))
            return;

        if (implicitSourcePolicy == ImplicitSourcePolicy.NONE
                && !inputFiles.contains(env.toplevel.sourcefile)) {
            return;
        }

        if (compileStates.isDone(env, CompileState.LOWER)) {
            results.addAll(desugaredEnvs.get(env));
            return;
        }

        /**
         * Ensure that superclasses of C are desugared before C itself. This is
         * required for two reasons: (i) as erasure (TransTypes) destroys
         * information needed in flow analysis and (ii) as some checks carried
         * out during lowering require that all synthetic fields/methods have
         * already been added to C and its superclasses.
         */
        class ScanNested extends TreeScanner {
            Set<Env<AttrContext>> dependencies = new LinkedHashSet<Env<AttrContext>>();
            @Override
            public void visitClassDef(JCClassDecl node) {
                Type st = types.supertype(node.sym.type);
                boolean envForSuperTypeFound = false;
                while (!envForSuperTypeFound && st.hasTag(CLASS)) {
                    ClassSymbol c = st.tsym.outermostClass();
                    Env<AttrContext> stEnv = enter.getEnv(c);
                    if (stEnv != null && env != stEnv) {
                        if (dependencies.add(stEnv)) {
                            scan(stEnv.tree);
                        }
                        envForSuperTypeFound = true;
                    }
                    st = types.supertype(st);
                }
                super.visitClassDef(node);
            }
        }
        ScanNested scanner = new ScanNested();
        scanner.scan(env.tree);
        for (Env<AttrContext> dep: scanner.dependencies) {
        if (!compileStates.isDone(dep, CompileState.FLOW))
            desugaredEnvs.put(dep, desugar(flow(attribute(dep))));
        }

        //We need to check for error another time as more classes might
        //have been attributed and analyzed at this stage
        if (shouldStop(CompileState.TRANSTYPES))
            return;

        if (verboseCompilePolicy)
            printNote("[desugar " + env.enclClass.sym + "]");

        JavaFileObject prev = log.useSource(env.enclClass.sym.sourcefile != null ?
                                  env.enclClass.sym.sourcefile :
                                  env.toplevel.sourcefile);
        try {
            //save tree prior to rewriting
            JCTree untranslated = env.tree;

            make.at(Position.FIRSTPOS);
            TreeMaker localMake = make.forToplevel(env.toplevel);

            if (env.tree instanceof JCCompilationUnit) {
                if (!(stubOutput || sourceOutput || printFlat)) {
                    if (shouldStop(CompileState.LOWER))
                        return;
                    List<JCTree> pdef = lower.translateTopLevelClass(env, env.tree, localMake);
                    if (pdef.head != null) {
                        Assert.check(pdef.tail.isEmpty());
                        results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, (JCClassDecl)pdef.head));
                    }
                }
                return;
            }

            if (stubOutput) {
                //emit stub Java source file, only for compilation
                //units enumerated explicitly on the command line
                JCClassDecl cdef = (JCClassDecl)env.tree;
                if (untranslated instanceof JCClassDecl &&
                    rootClasses.contains((JCClassDecl)untranslated) &&
                    ((cdef.mods.flags & (Flags.PROTECTED|Flags.PUBLIC)) != 0 ||
                     cdef.sym.packge().getQualifiedName() == names.java_lang)) {
                    results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, removeMethodBodies(cdef)));
                }
                return;
            }

            if (shouldStop(CompileState.TRANSTYPES))
                return;

            env.tree = transTypes.translateTopLevelClass(env.tree, localMake);
            compileStates.put(env, CompileState.TRANSTYPES);

            if (source.allowLambda()) {
                if (shouldStop(CompileState.UNLAMBDA))
                    return;

                env.tree = lambdaToMethod.translateTopLevelClass(env, env.tree, localMake);
                compileStates.put(env, CompileState.UNLAMBDA);
            }

            if (shouldStop(CompileState.LOWER))
                return;

            if (sourceOutput) {
                //emit standard Java source file, only for compilation
                //units enumerated explicitly on the command line
                JCClassDecl cdef = (JCClassDecl)env.tree;
                if (untranslated instanceof JCClassDecl &&
                    rootClasses.contains((JCClassDecl)untranslated)) {
                    results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, cdef));
                }
                return;
            }

            //translate out inner classes
            List<JCTree> cdefs = lower.translateTopLevelClass(env, env.tree, localMake);
            compileStates.put(env, CompileState.LOWER);

            if (shouldStop(CompileState.LOWER))
                return;

            //generate code for each class
            for (List<JCTree> l = cdefs; l.nonEmpty(); l = l.tail) {
                JCClassDecl cdef = (JCClassDecl)l.head;
                results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, cdef));
            }
        }
        finally {
            log.useSource(prev);
        }

    }

    /** Generates the source or class file for a list of classes.
     * The decision to generate a source file or a class file is
     * based upon the compiler's options.
     * Generation stops if an error occurs while writing files.
     */
    public void generate(Queue<Pair<Env<AttrContext>, JCClassDecl>> queue) {
        generate(queue, null);
    }

    public void generate(Queue<Pair<Env<AttrContext>, JCClassDecl>> queue, Queue<JavaFileObject> results) {
        if (shouldStop(CompileState.GENERATE))
            return;

        boolean usePrintSource = (stubOutput || sourceOutput || printFlat);

        for (Pair<Env<AttrContext>, JCClassDecl> x: queue) {
            Env<AttrContext> env = x.fst;
            JCClassDecl cdef = x.snd;

            if (verboseCompilePolicy) {
                printNote("[generate "
                               + (usePrintSource ? " source" : "code")
                               + " " + cdef.sym + "]");
            }

            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.GENERATE, env.toplevel, cdef.sym);
                taskListener.started(e);
            }

            JavaFileObject prev = log.useSource(env.enclClass.sym.sourcefile != null ?
                                      env.enclClass.sym.sourcefile :
                                      env.toplevel.sourcefile);
            try {
                JavaFileObject file;
                if (usePrintSource)
                    file = printSource(env, cdef);
                else {
                    if (fileManager.hasLocation(StandardLocation.NATIVE_HEADER_OUTPUT)
                            && jniWriter.needsHeader(cdef.sym)) {
                        jniWriter.write(cdef.sym);
                    }
                    file = genCode(env, cdef);
                }
                if (results != null && file != null)
                    results.add(file);
            } catch (IOException ex) {
                log.error(cdef.pos(), "class.cant.write",
                          cdef.sym, ex.getMessage());
                return;
            } finally {
                log.useSource(prev);
            }

            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.GENERATE, env.toplevel, cdef.sym);
                taskListener.finished(e);
            }
        }
    }

        // where
        Map<JCCompilationUnit, Queue<Env<AttrContext>>> groupByFile(Queue<Env<AttrContext>> envs) {
            // use a LinkedHashMap to preserve the order of the original list as much as possible
            Map<JCCompilationUnit, Queue<Env<AttrContext>>> map = new LinkedHashMap<JCCompilationUnit, Queue<Env<AttrContext>>>();
            for (Env<AttrContext> env: envs) {
                Queue<Env<AttrContext>> sublist = map.get(env.toplevel);
                if (sublist == null) {
                    sublist = new ListBuffer<Env<AttrContext>>();
                    map.put(env.toplevel, sublist);
                }
                sublist.add(env);
            }
            return map;
        }

        JCClassDecl removeMethodBodies(JCClassDecl cdef) {
            final boolean isInterface = (cdef.mods.flags & Flags.INTERFACE) != 0;
            class MethodBodyRemover extends TreeTranslator {
                @Override
                public void visitMethodDef(JCMethodDecl tree) {
                    tree.mods.flags &= ~Flags.SYNCHRONIZED;
                    for (JCVariableDecl vd : tree.params)
                        vd.mods.flags &= ~Flags.FINAL;
                    tree.body = null;
                    super.visitMethodDef(tree);
                }
                @Override
                public void visitVarDef(JCVariableDecl tree) {
                    if (tree.init != null && tree.init.type.constValue() == null)
                        tree.init = null;
                    super.visitVarDef(tree);
                }
                @Override
                public void visitClassDef(JCClassDecl tree) {
                    ListBuffer<JCTree> newdefs = new ListBuffer<>();
                    for (List<JCTree> it = tree.defs; it.tail != null; it = it.tail) {
                        JCTree t = it.head;
                        switch (t.getTag()) {
                        case CLASSDEF:
                            if (isInterface ||
                                (((JCClassDecl) t).mods.flags & (Flags.PROTECTED|Flags.PUBLIC)) != 0 ||
                                (((JCClassDecl) t).mods.flags & (Flags.PRIVATE)) == 0 && ((JCClassDecl) t).sym.packge().getQualifiedName() == names.java_lang)
                                newdefs.append(t);
                            break;
                        case METHODDEF:
                            if (isInterface ||
                                (((JCMethodDecl) t).mods.flags & (Flags.PROTECTED|Flags.PUBLIC)) != 0 ||
                                ((JCMethodDecl) t).sym.name == names.init ||
                                (((JCMethodDecl) t).mods.flags & (Flags.PRIVATE)) == 0 && ((JCMethodDecl) t).sym.packge().getQualifiedName() == names.java_lang)
                                newdefs.append(t);
                            break;
                        case VARDEF:
                            if (isInterface || (((JCVariableDecl) t).mods.flags & (Flags.PROTECTED|Flags.PUBLIC)) != 0 ||
                                (((JCVariableDecl) t).mods.flags & (Flags.PRIVATE)) == 0 && ((JCVariableDecl) t).sym.packge().getQualifiedName() == names.java_lang)
                                newdefs.append(t);
                            break;
                        default:
                            break;
                        }
                    }
                    tree.defs = newdefs.toList();
                    super.visitClassDef(tree);
                }
            }
            MethodBodyRemover r = new MethodBodyRemover();
            return r.translate(cdef);
        }

    public void reportDeferredDiagnostics() {
        if (errorCount() == 0
                && annotationProcessingOccurred
                && implicitSourceFilesRead
                && implicitSourcePolicy == ImplicitSourcePolicy.UNSET) {
            if (explicitAnnotationProcessingRequested())
                log.warning("proc.use.implicit");
            else
                log.warning("proc.use.proc.or.implicit");
        }
        chk.reportDeferredDiagnostics();
        if (log.compressedOutput) {
            log.mandatoryNote(null, "compressed.diags");
        }
    }

    /** Close the compiler, flushing the logs
     */
    public void close() {
        close(true);
    }

    public void close(boolean disposeNames) {
        rootClasses = null;
        reader = null;
        make = null;
        writer = null;
        enter = null;
        if (todo != null)
            todo.clear();
        todo = null;
        parserFactory = null;
        syms = null;
        source = null;
        attr = null;
        chk = null;
        gen = null;
        flow = null;
        transTypes = null;
        lower = null;
        annotate = null;
        types = null;

        log.flush();
        try {
            fileManager.flush();
        } catch (IOException e) {
            throw new Abort(e);
        } finally {
            if (names != null && disposeNames)
                names.dispose();
            names = null;

            for (Closeable c: closeables) {
                try {
                    c.close();
                } catch (IOException e) {
                    // When javac uses JDK 7 as a baseline, this code would be
                    // better written to set any/all exceptions from all the
                    // Closeables as suppressed exceptions on the FatalError
                    // that is thrown.
                    JCDiagnostic msg = diagFactory.fragment("fatal.err.cant.close");
                    throw new FatalError(msg, e);
                }
            }
            closeables = List.nil();
        }
    }

    protected void printNote(String lines) {
        log.printRawLines(Log.WriterKind.NOTICE, lines);
    }

    /** Print numbers of errors and warnings.
     */
    public void printCount(String kind, int count) {
        if (count != 0) {
            String key;
            if (count == 1)
                key = "count." + kind;
            else
                key = "count." + kind + ".plural";
            log.printLines(WriterKind.ERROR, key, String.valueOf(count));
            log.flush(Log.WriterKind.ERROR);
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long elapsed(long then) {
        return now() - then;
    }

    public void initRound(JavaCompiler prev) {
        genEndPos = prev.genEndPos;
        keepComments = prev.keepComments;
        start_msec = prev.start_msec;
        hasBeenUsed = true;
        closeables = prev.closeables;
        prev.closeables = List.nil();
        shouldStopPolicyIfError = prev.shouldStopPolicyIfError;
        shouldStopPolicyIfNoError = prev.shouldStopPolicyIfNoError;
    }
}
