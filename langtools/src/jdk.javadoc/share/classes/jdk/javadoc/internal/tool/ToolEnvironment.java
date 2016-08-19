/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;


import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.ClassFinder;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCPackageDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

/**
 * Holds the environment for a run of javadoc.
 * Holds only the information needed throughout the
 * run and not the compiler info that could be GC'ed
 * or ported.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Neal Gafter (rewrite)
 * @author Scott Seligman (generics)
 */
public class ToolEnvironment {
    protected static final Context.Key<ToolEnvironment> ToolEnvKey = new Context.Key<>();

    public static ToolEnvironment instance(Context context) {
        ToolEnvironment instance = context.get(ToolEnvKey);
        if (instance == null)
            instance = new ToolEnvironment(context);
        return instance;
    }

    private final Messager messager;

    /** Predefined symbols known to the compiler. */
    public final Symtab syms;

    /** Referenced directly in RootDocImpl. */
    private final ClassFinder finder;

    /** Javadoc's own version of the compiler's enter phase. */
    final Enter enter;

    /** The name table. */
    private Names names;

    final Symbol externalizableSym;

    /**
     * True if we do not want to print any notifications at all.
     */
    boolean quiet = false;

    Check chk;
    com.sun.tools.javac.code.Types types;
    JavaFileManager fileManager;
    public final Context context;

    WeakHashMap<JCTree, TreePath> treePaths = new WeakHashMap<>();

    public final HashMap<PackageElement, JavaFileObject> pkgToJavaFOMap = new HashMap<>();

    /** Allow documenting from class files? */
    boolean docClasses = false;

    /**
     * The source language version.
     */
    public final Source source;

    public final Elements elements;

    public final JavacTypes typeutils;

    protected DocEnvImpl docEnv;

    public final DocTrees docTrees;

    public final Map<Element, TreePath> elementToTreePath;

    /**
     * Constructor
     *
     * @param context      Context for this javadoc instance.
     */
    protected ToolEnvironment(Context context) {
        context.put(ToolEnvKey, this);
        this.context = context;

        messager = Messager.instance0(context);
        syms = Symtab.instance(context);
        finder = JavadocClassFinder.instance(context);
        enter = JavadocEnter.instance(context);
        names = Names.instance(context);
        externalizableSym = syms.enterClass(syms.java_base, names.fromString("java.io.Externalizable"));
        chk = Check.instance(context);
        types = com.sun.tools.javac.code.Types.instance(context);
        fileManager = context.get(JavaFileManager.class);
        if (fileManager instanceof JavacFileManager) {
            ((JavacFileManager)fileManager).setSymbolFileEnabled(false);
        }
        docTrees = JavacTrees.instance(context);
        source = Source.instance(context);
        elements =  JavacElements.instance(context);
        typeutils = JavacTypes.instance(context);
        elementToTreePath = new HashMap<>();
    }

    public void initialize(Map<ToolOption, Object> toolOpts) {
        this.quiet = (boolean)toolOpts.getOrDefault(ToolOption.QUIET, false);
    }

    /**
     * Load a class by qualified name.
     */
    public TypeElement loadClass(String name) {
        try {
            Name nameImpl = names.fromString(name);
            ModuleSymbol mod = syms.inferModule(Convert.packagePart(nameImpl));
            ClassSymbol c = finder.loadClass(mod != null ? mod : syms.errModule, nameImpl);
            return c;
        } catch (CompletionFailure ex) {
            chk.completionError(null, ex);
            return null;
        }
    }

    boolean isSynthetic(Symbol sym) {
        return (sym.flags() & Flags.SYNTHETIC) != 0;
    }

    void setElementToTreePath(Element e, TreePath tree) {
        if (e == null || tree == null)
            return;
        elementToTreePath.put(e, tree);
    }

    /**
     * Returns true if the symbol has a tree path associated with it.
     * Primarily used to disambiguate a symbol associated with a source
     * file versus a class file.
     * @param sym the symbol to be checked
     * @return true if the symbol has a tree path
     */
    boolean hasPath(ClassSymbol sym) {
        TreePath path = elementToTreePath.get(sym);
        return path != null;
    }

    //---------------- print forwarders ----------------//

    // ERRORS
    /**
     * Print error message, increment error count.
     *
     * @param msg message to print.
     */
    public void printError(String msg) {
        messager.printError(msg);
    }

//    /**
//     * Print error message, increment error count.
//     *
//     * @param key selects message from resource
//     */
//    public void error(Element element, String key) {
//        if (element == null)
//            messager.error(key);
//        else
//            messager.error(element, key);
//    }
//
//    public void error(String prefix, String key) {
//        printError(prefix + ":" + messager.getText(key));
//    }
//
//    /**
//     * Print error message, increment error count.
//     *
//     * @param path the path to the source
//     * @param key selects message from resource
//     */
//    public void error(DocTreePath path, String key) {
//        messager.error(path, key);
//    }
//
//    /**
//     * Print error message, increment error count.
//     *
//     * @param path the path to the source
//     * @param msg message to print.
//     */
//    public void printError(DocTreePath path, String msg) {
//        messager.printError(path, msg);
//    }
//
//    /**
//     * Print error message, increment error count.
//     * @param e the target element
//     * @param msg message to print.
//     */
//    public void printError(Element e, String msg) {
//        messager.printError(e, msg);
//    }

    /**
     * Print error message, increment error count.
     * @param key selects message from resource
     * @param args replacement arguments
     */
    public void error(String key, String... args) {
        error(null, key, args);
    }

    /**
     * Print error message, increment error count.
     *
     * @param element the source element
     * @param key selects message from resource
     * @param args replacement arguments
     */
    public void error(Element element, String key, String... args) {
        if (element == null)
            messager.error(key, (Object[]) args);
        else
            messager.error(element, key, (Object[]) args);
    }

    // WARNINGS

//    /**
//     * Print warning message, increment warning count.
//     *
//     * @param msg message to print.
//     */
//    public void printWarning(String msg) {
//        messager.printWarning(msg);
//    }
//
//    public void warning(String key) {
//        warning((Element)null, key);
//    }

    public void warning(String key, String... args) {
        warning((Element)null, key, args);
    }

//    /**
//     * Print warning message, increment warning count.
//     *
//     * @param element the source element
//     * @param key selects message from resource
//     */
//    public void warning(Element element, String key) {
//        if (element == null)
//            messager.warning(key);
//        else
//            messager.warning(element, key);
//    }
//
//    /**
//     * Print warning message, increment warning count.
//     *
//     * @param path the path to the source
//     * @param msg message to print.
//     */
//    public void printWarning(DocTreePath path, String msg) {
//        messager.printWarning(path, msg);
//    }
//
//    /**
//     * Print warning message, increment warning count.
//     *
//     * @param e  the source element
//     * @param msg message to print.
//     */
//    public void printWarning(Element e, String msg) {
//        messager.printWarning(e, msg);
//    }

    /**
     * Print warning message, increment warning count.
     *
     * @param e    the source element
     * @param key  selects message from resource
     * @param args the replace arguments
     */
    public void warning(Element e, String key, String... args) {
        if (e == null)
            messager.warning(key, (Object[]) args);
        else
            messager.warning(e, key, (Object[]) args);
    }

//    Note: no longer required
//    /**
//     * Print a message.
//     *
//     * @param msg message to print.
//     */
//    public void printNotice(String msg) {
//        if (quiet) {
//            return;
//        }
//        messager.printNotice(msg);
//    }

//  Note: no longer required
//    /**
//     * Print a message.
//     *
//     * @param e the source element
//     * @param msg message to print.
//     */
//    public void printNotice(Element e, String msg) {
//        if (quiet) {
//            return;
//        }
//        messager.printNotice(e, msg);
//    }

    //  NOTICES
    /**
     * Print a message.
     *
     * @param key selects message from resource
     */
    public void notice(String key) {
        if (quiet) {
            return;
        }
        messager.notice(key);
    }

//    Note: not used anymore
//    /**
//     * Print a message.
//     *
//     * @param path the path to the source
//     * @param msg message to print.
//     */
//    public void printNotice(DocTreePath path, String msg) {
//        if (quiet) {
//            return;
//        }
//        messager.printNotice(path, msg);
//    }

    /**
     * Print a message.
     *
     * @param key selects message from resource
     * @param a1 first argument
     */
    public void notice(String key, String a1) {
        if (quiet) {
            return;
        }
        messager.notice(key, a1);
    }

//    Note: not used anymore
//    /**
//     * Print a message.
//     *
//     * @param key selects message from resource
//     * @param a1 first argument
//     * @param a2 second argument
//     */
//    public void notice(String key, String a1, String a2) {
//        if (quiet) {
//            return;
//        }
//        messager.notice(key, a1, a2);
//    }
//

//    Note: not used anymore
//    /**
//     * Print a message.
//     *
//     * @param key selects message from resource
//     * @param a1 first argument
//     * @param a2 second argument
//     * @param a3 third argument
//     */
//    public void notice(String key, String a1, String a2, String a3) {
//        if (quiet) {
//            return;
//        }
//        messager.notice(key, a1, a2, a3);
//    }

    /**
     * Exit, reporting errors and warnings.
     */
    public void exit() {
        // Messager should be replaced by a more general
        // compilation environment.  This can probably
        // subsume DocEnv as well.
        throw new Messager.ExitJavadoc();
    }

    TreePath getTreePath(JCCompilationUnit tree) {
        TreePath p = treePaths.get(tree);
        if (p == null)
            treePaths.put(tree, p = new TreePath(tree));
        return p;
    }

    TreePath getTreePath(JCCompilationUnit toplevel, JCPackageDecl tree) {
        TreePath p = treePaths.get(tree);
        if (p == null)
            treePaths.put(tree, p = new TreePath(getTreePath(toplevel), tree));
        return p;
    }

    TreePath getTreePath(JCCompilationUnit toplevel, JCClassDecl tree) {
        TreePath p = treePaths.get(tree);
        if (p == null)
            treePaths.put(tree, p = new TreePath(getTreePath(toplevel), tree));
        return p;
    }

    TreePath getTreePath(JCCompilationUnit toplevel, JCClassDecl cdecl, JCTree tree) {
        return new TreePath(getTreePath(toplevel, cdecl), tree);
    }

    public com.sun.tools.javac.code.Types getTypes() {
        return types;
    }

    public Env<AttrContext> getEnv(ClassSymbol tsym) {
        return enter.getEnv(tsym);
    }

    public boolean isQuiet() {
        return quiet;
    }
}
