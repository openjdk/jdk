/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package combo;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.util.HashSet;
import java.util.Set;

/**
 * A reusable context is a context that can be used safely across multiple compilation rounds
 * arising from execution of a combo test. It achieves reuse by replacing some components
 * (most notably JavaCompiler and Log) with reusable counterparts, and by exposing a method
 * to cleanup leftovers from previous compilation.
 * <p>
 * There are, however, situations in which reusing the context is not safe: (i) when different
 * compilations are using different sets of compiler options (as most option values are cached
 * inside components themselves) and (ii) when the compilation unit happens to redefine classes
 * in the java.* packages.
 */
class ReusableContext extends Context implements TaskListener {

    Set<CompilationUnitTree> roots = new HashSet<>();

    String opts;
    boolean polluted = false;

    ReusableContext() {
        super();
        put(Log.logKey, ReusableLog.factory);
        put(JavaCompiler.compilerKey, ReusableJavaCompiler.factory);
    }

    void clear() {
        drop(Arguments.argsKey);
        drop(DiagnosticListener.class);
        drop(Log.outKey);
        drop(JavaFileManager.class);
        drop(JavacTask.class);

        if (ht.get(Log.logKey) instanceof ReusableLog) {
            //log already inited - not first round
            ((ReusableLog)Log.instance(this)).clear();
            Enter.instance(this).newRound();
            ((ReusableJavaCompiler)ReusableJavaCompiler.instance(this)).clear();
            Types.instance(this).newRound();
            Check.instance(this).newRound();
            CompileStates.instance(this).clear();
            MultiTaskListener.instance(this).clear();

            //find if any of the roots have redefined java.* classes
            Symtab syms = Symtab.instance(this);
            pollutionScanner.scan(roots, syms);
            roots.clear();
        }
    }

    /**
     * This scanner detects as to whether the shared context has been polluted. This happens
     * whenever a compiled program redefines a core class (in 'java.*' package) or when
     * (typically because of cyclic inheritance) the symbol kind of a core class has been touched.
     */
    TreeScanner<Void, Symtab> pollutionScanner = new TreeScanner<Void, Symtab>() {
        @Override
        public Void visitClass(ClassTree node, Symtab syms) {
            Symbol sym = ((JCClassDecl)node).sym;
            if (sym != null) {
                syms.classes.remove(sym.flatName());
                Type sup = supertype(sym);
                if (isCoreClass(sym) ||
                        (sup != null && isCoreClass(sup.tsym) && sup.tsym.kind != Kinds.Kind.TYP)) {
                    polluted = true;
                }
            }
            return super.visitClass(node, syms);
        }

        private boolean isCoreClass(Symbol s) {
            return s.flatName().toString().startsWith("java.");
        }

        private Type supertype(Symbol s) {
            if (s.type == null ||
                    !s.type.hasTag(TypeTag.CLASS)) {
                return null;
            } else {
                ClassType ct = (ClassType)s.type;
                return ct.supertype_field;
            }
        }
    };

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() == Kind.PARSE) {
            roots.add(e.getCompilationUnit());
        }
    }

    @Override
    public void started(TaskEvent e) {
        //do nothing
    }

    <T> void drop(Key<T> k) {
        ht.remove(k);
    }

    <T> void drop(Class<T> c) {
        ht.remove(key(c));
    }

    /**
     * Reusable JavaCompiler; exposes a method to clean up the component from leftovers associated with
     * previous compilations.
     */
    static class ReusableJavaCompiler extends JavaCompiler {

        static Factory<JavaCompiler> factory = ReusableJavaCompiler::new;

        ReusableJavaCompiler(Context context) {
            super(context);
        }

        @Override
        public void close() {
            //do nothing
        }

        void clear() {
            newRound();
        }

        @Override
        protected void checkReusable() {
            //do nothing - it's ok to reuse the compiler
        }
    }

    /**
     * Reusable Log; exposes a method to clean up the component from leftovers associated with
     * previous compilations.
     */
    static class ReusableLog extends Log {

        static Factory<Log> factory = ReusableLog::new;

        Context context;

        ReusableLog(Context context) {
            super(context);
            this.context = context;
        }

        void clear() {
            recorded.clear();
            sourceMap.clear();
            nerrors = 0;
            nwarnings = 0;
            //Set a fake listener that will lazily lookup the context for the 'real' listener. Since
            //this field is never updated when a new task is created, we cannot simply reset the field
            //or keep old value. This is a hack to workaround the limitations in the current infrastructure.
            diagListener = new DiagnosticListener<JavaFileObject>() {
                DiagnosticListener<JavaFileObject> cachedListener;

                @Override
                @SuppressWarnings("unchecked")
                public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                    if (cachedListener == null) {
                        cachedListener = context.get(DiagnosticListener.class);
                    }
                    cachedListener.report(diagnostic);
                }
            };
        }
    }
}
