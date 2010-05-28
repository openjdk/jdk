/*
 * Copyright (c) 2004, 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.main;

import java.io.*;
import java.util.Map;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import com.sun.tools.apt.comp.*;
import com.sun.tools.apt.util.Bark;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.tools.javac.parser.DocCommentScanner;

/**
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 */
@SuppressWarnings("deprecation")
public class JavaCompiler extends com.sun.tools.javac.main.JavaCompiler {
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


    java.util.Set<String> genSourceFileNames;
    java.util.Set<String> genClassFileNames;

    public java.util.Set<String> getSourceFileNames() {
        return genSourceFileNames;
    }

    /** List of names of generated class files.
     */
    public java.util.Set<String> getClassFileNames() {
        return genClassFileNames;
    }

    java.util.Set<java.io.File> aggregateGenFiles = java.util.Collections.emptySet();

    public java.util.Set<java.io.File> getAggregateGenFiles() {
        return aggregateGenFiles;
    }

    /** The bark to be used for error reporting.
     */
    Bark bark;

    /** The log to be used for error reporting.
     */
    Log log;

    /** The annotation framework
     */
    Apt apt;

    private static Context preRegister(Context context) {
        Bark.preRegister(context);

        // force the use of the scanner that captures Javadoc comments
        DocCommentScanner.Factory.preRegister(context);

        if (context.get(JavaFileManager.class) == null)
            JavacFileManager.preRegister(context);

        return context;
    }

    /** Construct a new compiler from a shared context.
     */
    public JavaCompiler(Context context) {
        super(preRegister(context));

        context.put(compilerKey, this);
        apt = Apt.instance(context);

        ClassReader classReader = ClassReader.instance(context);
        classReader.preferSource = true;

        // TEMPORARY NOTE: bark==log, but while refactoring, we maintain their
        // original identities, to remember the original intent.
        log = Log.instance(context);
        bark = Bark.instance(context);

        Options options = Options.instance(context);
        classOutput   = options.get("-retrofit")      == null;
        nocompile     = options.get("-nocompile")     != null;
        print         = options.get("-print")         != null;
        classesAsDecls= options.get("-XclassesAsDecls") != null;

        genSourceFileNames = new java.util.LinkedHashSet<String>();
        genClassFileNames  = new java.util.LinkedHashSet<String>();

        // this forces a copy of the line map to be kept in the tree,
        // for use by com.sun.mirror.util.SourcePosition.
        lineDebugInfo = true;
    }

    /* Switches:
     */

    /** Emit class files. This switch is always set, except for the first
     *  phase of retrofitting, where signatures are parsed.
     */
    public boolean classOutput;

    /** The internal printing annotation processor should be used.
     */
    public boolean print;

    /** Compilation should not be done after annotation processing.
     */
    public boolean nocompile;

    /** Are class files being treated as declarations
     */
    public boolean classesAsDecls;

    /** Try to open input stream with given name.
     *  Report an error if this fails.
     *  @param filename   The file name of the input stream to be opened.
     */
    // PROVIDED FOR EXTREME BACKWARDS COMPATIBILITY
    // There are some very obscure errors that can arise while translating
    // the contents of a file from bytes to characters. In Tiger, these
    // diagnostics were ignored. This method provides compatibility with
    // that behavior. It would be better to honor those diagnostics, in which
    // case, this method can be deleted.
    @Override
    public CharSequence readSource(JavaFileObject filename) {
        try {
            inputFiles.add(filename);
            boolean prev = bark.setDiagnosticsIgnored(true);
            try {
                return filename.getCharContent(false);
            }
            finally {
                bark.setDiagnosticsIgnored(prev);
            }
        } catch (IOException e) {
            bark.error(Position.NOPOS, "cant.read.file", filename);
            return null;
        }
    }

    /** Parse contents of input stream.
     *  @param filename     The name of the file from which input stream comes.
     *  @param input        The input stream to be parsed.
     */
    // PROVIDED FOR BACKWARDS COMPATIBILITY
    // In Tiger, diagnostics from the scanner and parser were ignored.
    // This method provides compatibility with that behavior.
    // It would be better to honor those diagnostics, in which
    // case, this method can be deleted.
    @Override
    protected JCCompilationUnit parse(JavaFileObject filename, CharSequence content) {
        boolean prev = bark.setDiagnosticsIgnored(true);
        try {
            return super.parse(filename, content);
        }
        finally {
            bark.setDiagnosticsIgnored(prev);
        }
    }

    @Override
    protected boolean keepComments() {
        return true;  // make doc comments available to mirror API impl.
    }

    /** Track when the JavaCompiler has been used to compile something. */
    private boolean hasBeenUsed = false;

    /** Main method: compile a list of files, return all compiled classes
     *  @param filenames     The names of all files to be compiled.
     */
    public List<ClassSymbol> compile(List<String> filenames,
                                     Map<String, String> origOptions,
                                     ClassLoader aptCL,
                                     AnnotationProcessorFactory providedFactory,
                                     java.util.Set<Class<? extends AnnotationProcessorFactory> > productiveFactories,
                                     java.util.Set<java.io.File> aggregateGenFiles)
        throws Throwable {
        // as a JavaCompiler can only be used once, throw an exception if
        // it has been used before.
        assert !hasBeenUsed : "attempt to reuse JavaCompiler";
        hasBeenUsed = true;

        this.aggregateGenFiles = aggregateGenFiles;

        long msec = System.currentTimeMillis();

        ListBuffer<ClassSymbol> classes = new ListBuffer<ClassSymbol>();
        try {
            JavacFileManager fm = (JavacFileManager)fileManager;
            //parse all files
            ListBuffer<JCCompilationUnit> trees = new ListBuffer<JCCompilationUnit>();
            for (List<String> l = filenames; l.nonEmpty(); l = l.tail) {
                if (classesAsDecls) {
                    if (! l.head.endsWith(".java") ) { // process as class file
                        ClassSymbol cs = reader.enterClass(names.fromString(l.head));
                        try {
                            cs.complete();
                        } catch(Symbol.CompletionFailure cf) {
                            bark.aptError("CantFindClass", l);
                            continue;
                        }

                        classes.append(cs); // add to list of classes
                        continue;
                    }
                }
                JavaFileObject fo = fm.getJavaFileObjectsFromStrings(List.of(l.head)).iterator().next();
                trees.append(parse(fo));
            }

            //enter symbols for all files
            List<JCCompilationUnit> roots = trees.toList();

            if (errorCount() == 0) {
                boolean prev = bark.setDiagnosticsIgnored(true);
                try {
                    enter.main(roots);
                }
                finally {
                    bark.setDiagnosticsIgnored(prev);
                }
            }

            if (errorCount() == 0) {
                apt.main(roots,
                         classes,
                         origOptions, aptCL,
                         providedFactory,
                         productiveFactories);
                genSourceFileNames.addAll(apt.getSourceFileNames());
                genClassFileNames.addAll(apt.getClassFileNames());
            }

        } catch (Abort ex) {
        }

        if (verbose)
            printVerbose("total", Long.toString(System.currentTimeMillis() - msec));

        chk.reportDeferredDiagnostics();

        printCount("error", errorCount());
        printCount("warn", warningCount());

        return classes.toList();
    }
}
