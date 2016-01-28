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


import java.lang.reflect.Modifier;
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor9;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.ClassFinder;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds.Kind;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
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
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;

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
public class DocEnv {
    protected static final Context.Key<DocEnv> docEnvKey = new Context.Key<>();

    public static DocEnv instance(Context context) {
        DocEnv instance = context.get(docEnvKey);
        if (instance == null)
            instance = new DocEnv(context);
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

    /** The encoding name. */
    private String encoding;

    final Symbol externalizableSym;

    /** Access filter (public, protected, ...).  */
    protected ModifierFilter filter;

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

    protected RootDocImpl root;

    public final DocTrees docTrees;

    public final Map<Element, TreePath> elementToTreePath;

    /**
     * Constructor
     *
     * @param context      Context for this javadoc instance.
     */
    protected DocEnv(Context context) {
        context.put(docEnvKey, this);
        this.context = context;

        messager = Messager.instance0(context);
        syms = Symtab.instance(context);
        finder = JavadocClassFinder.instance(context);
        enter = JavadocEnter.instance(context);
        names = Names.instance(context);
        externalizableSym = syms.enterClass(names.fromString("java.io.Externalizable"));
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

    public void intialize(String encoding,
            String showAccess,
            String overviewpath,
            List<String> javaNames,
            Iterable<? extends JavaFileObject> fileObjects,
            List<String> subPackages,
            List<String> excludedPackages,
            boolean docClasses,
            boolean quiet) {
        this.filter = ModifierFilter.getModifierFilter(showAccess);
        this.quiet = quiet;

        this.setEncoding(encoding);
        this.docClasses = docClasses;
    }

    /**
     * Load a class by qualified name.
     */
    public TypeElement loadClass(String name) {
        try {
            ClassSymbol c = finder.loadClass(names.fromString(name));
            return c;
        } catch (CompletionFailure ex) {
            chk.completionError(null, ex);
            return null;
        }
    }

    private boolean isSynthetic(long flags) {
        return (flags & Flags.SYNTHETIC) != 0;
    }

    private boolean isSynthetic(Symbol sym) {
        return isSynthetic(sym.flags_field);
    }

    SimpleElementVisitor9<Boolean, Void> shouldDocumentVisitor = null;
    public boolean shouldDocument(Element e) {
        if (shouldDocumentVisitor == null) {
            shouldDocumentVisitor = new SimpleElementVisitor9<Boolean, Void>() {

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public Boolean visitType(TypeElement e, Void p) {
                return shouldDocument((ClassSymbol)e);
            }

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public Boolean visitVariable(VariableElement e, Void p) {
                return shouldDocument((VarSymbol)e);
            }

            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public Boolean visitExecutable(ExecutableElement e, Void p) {
                return shouldDocument((MethodSymbol)e);
            }
        };
        }
        return shouldDocumentVisitor.visit(e);
    }

    /** Check whether this member should be documented. */
    public boolean shouldDocument(VarSymbol sym) {
        long mod = sym.flags();
        if (isSynthetic(mod)) {
            return false;
        }
        return filter.checkModifier(translateModifiers(mod));
    }

    /** Check whether this member should be documented. */
    public boolean shouldDocument(MethodSymbol sym) {
        long mod = sym.flags();
        if (isSynthetic(mod)) {
            return false;
        }
        return filter.checkModifier(translateModifiers(mod));
    }

    void setElementToTreePath(Element e, TreePath tree) {
        if (e == null || tree == null)
            return;
        elementToTreePath.put(e, tree);
    }

    private boolean hasLeaf(ClassSymbol sym) {
        TreePath path = elementToTreePath.get(sym);
        if (path == null)
            return false;
        return path.getLeaf() != null;
    }

    /** check whether this class should be documented. */
    public boolean shouldDocument(ClassSymbol sym) {
        return
            !isSynthetic(sym.flags_field) && // no synthetics
            (docClasses || hasLeaf(sym)) &&
            isVisible(sym);
    }

    //### Comment below is inaccurate wrt modifier filter testing
    /**
     * Check the visibility if this is an nested class.
     * if this is not a nested class, return true.
     * if this is an static visible nested class,
     *    return true.
     * if this is an visible nested class
     *    if the outer class is visible return true.
     *    else return false.
     * IMPORTANT: This also allows, static nested classes
     * to be defined inside an nested class, which is not
     * allowed by the compiler. So such an test case will
     * not reach upto this method itself, but if compiler
     * allows it, then that will go through.
     */
    public boolean isVisible(ClassSymbol sym) {
        long mod = sym.flags_field;
        if (!filter.checkModifier(translateModifiers(mod))) {
            return false;
        }
        ClassSymbol encl = sym.owner.enclClass();
        return (encl == null || (mod & Flags.STATIC) != 0 || isVisible(encl));
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
        messager.exit();
    }

    /**
     * Adds all inner classes of this class, and their inner classes recursively, to the list
     */
    void addAllClasses(Collection<TypeElement> list, TypeElement typeElement, boolean filtered) {
        ClassSymbol klass = (ClassSymbol)typeElement;
        try {
            if (isSynthetic(klass.flags())) return;
            // sometimes synthetic classes are not marked synthetic
            if (!JavadocTool.isValidClassName(klass.name.toString())) return;
            if (filtered && !shouldDocument(klass)) return;
            if (list.contains(klass)) return;
            list.add(klass);
            for (Symbol sym : klass.members().getSymbols(NON_RECURSIVE)) {
                if (sym != null && sym.kind == Kind.TYP) {
                    ClassSymbol s = (ClassSymbol)sym;
                    if (!isSynthetic(s.flags())) {
                        addAllClasses(list, s, filtered);
                    }
                }
            }
        } catch (CompletionFailure e) {
            // quietly ignore completion failures
        }
    }

    /**
     * Return a list of all classes contained in this package, including
     * member classes of those classes, and their member classes, etc.
     */
    void addAllClasses(Collection<TypeElement> list, PackageElement pkg) {
        boolean filtered = true;
        PackageSymbol sym = (PackageSymbol)pkg;
        for (Symbol isym : sym.members().getSymbols(NON_RECURSIVE)) {
            if (isym != null) {
                ClassSymbol s = (ClassSymbol)isym;
                if (!isSynthetic(s)) {
                    addAllClasses(list, s, filtered);
                }
            }
        }
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

    /**
     * Set the encoding.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Env<AttrContext> getEnv(ClassSymbol tsym) {
        return enter.getEnv(tsym);
    }

    /**
     * Get the encoding.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Convert modifier bits from private coding used by
     * the compiler to that of java.lang.reflect.Modifier.
     */
    static int translateModifiers(long flags) {
        int result = 0;
        if ((flags & Flags.ABSTRACT) != 0)
            result |= Modifier.ABSTRACT;
        if ((flags & Flags.FINAL) != 0)
            result |= Modifier.FINAL;
        if ((flags & Flags.INTERFACE) != 0)
            result |= Modifier.INTERFACE;
        if ((flags & Flags.NATIVE) != 0)
            result |= Modifier.NATIVE;
        if ((flags & Flags.PRIVATE) != 0)
            result |= Modifier.PRIVATE;
        if ((flags & Flags.PROTECTED) != 0)
            result |= Modifier.PROTECTED;
        if ((flags & Flags.PUBLIC) != 0)
            result |= Modifier.PUBLIC;
        if ((flags & Flags.STATIC) != 0)
            result |= Modifier.STATIC;
        if ((flags & Flags.SYNCHRONIZED) != 0)
            result |= Modifier.SYNCHRONIZED;
        if ((flags & Flags.TRANSIENT) != 0)
            result |= Modifier.TRANSIENT;
        if ((flags & Flags.VOLATILE) != 0)
            result |= Modifier.VOLATILE;
        return result;
    }

    private final Set<Element> includedSet = new HashSet<>();

    public void setIncluded(Element element) {
        includedSet.add(element);
    }

    private SimpleElementVisitor9<Boolean, Void> includedVisitor = null;

    public boolean isIncluded(Element e) {
        if (e == null) {
            return false;
        }
        if (includedVisitor == null) {
            includedVisitor = new SimpleElementVisitor9<Boolean, Void>() {
                @Override @DefinedBy(Api.LANGUAGE_MODEL)
                public Boolean visitType(TypeElement e, Void p) {
                    if (includedSet.contains(e)) {
                        return true;
                    }
                    if (shouldDocument(e)) {
                        // Class is nameable from top-level and
                        // the class and all enclosing classes
                        // pass the modifier filter.
                        PackageElement pkg = elements.getPackageOf(e);
                        if (includedSet.contains(pkg)) {
                            setIncluded(e);
                            return true;
                        }
                        Element enclosing = e.getEnclosingElement();
                        if (enclosing != null && includedSet.contains(enclosing)) {
                            setIncluded(e);
                            return true;
                        }
                    }
                    return false;
                }

                @Override @DefinedBy(Api.LANGUAGE_MODEL)
                public Boolean defaultAction(Element e, Void p) {
                    if (includedSet.contains(e) || shouldDocument(e)) {
                        return true;
                    }
                    return false;
                }

                @Override @DefinedBy(Api.LANGUAGE_MODEL)
                public Boolean visitPackage(PackageElement e, Void p) {
                    return includedSet.contains(e);
                }

                @Override @DefinedBy(Api.LANGUAGE_MODEL)
                public Boolean visitUnknown(Element e, Void p) {
                    throw new AssertionError("got element: " + e);
                }
            };
        }
        return includedVisitor.visit(e);
    }

    public boolean isQuiet() {
        return quiet;
    }

    /**
     * A class which filters the access flags on classes, fields, methods, etc.
     *
     * <p>
     * <b>This is NOT part of any supported API. If you write code that depends on this, you do so
     * at your own risk. This code and its internal interfaces are subject to change or deletion
     * without notice.</b>
     *
     * @see javax.lang.model.element.Modifier
     * @author Robert Field
     */

    private static class ModifierFilter {

        static enum FilterFlag {
            PACKAGE,
            PRIVATE,
            PROTECTED,
            PUBLIC
        }

        private Set<FilterFlag> oneOf;

        /**
         * Constructor - Specify a filter.
         *
         * @param oneOf a set containing desired flags to be matched.
         */
        ModifierFilter(Set<FilterFlag> oneOf) {
            this.oneOf = oneOf;
        }

        /**
         * Constructor - Specify a filter.
         *
         * @param oneOf an array containing desired flags to be matched.
         */
        ModifierFilter(FilterFlag... oneOf) {
            this.oneOf = new HashSet<>();
            this.oneOf.addAll(Arrays.asList(oneOf));
        }

        static ModifierFilter getModifierFilter(String showAccess) {
            switch (showAccess) {
                case "public":
                    return new ModifierFilter(FilterFlag.PUBLIC);
                case "package":
                    return new ModifierFilter(FilterFlag.PUBLIC, FilterFlag.PROTECTED,
                                              FilterFlag.PACKAGE);
                case "private":
                    return new ModifierFilter(FilterFlag.PRIVATE);
                default:
                    return new ModifierFilter(FilterFlag.PUBLIC, FilterFlag.PROTECTED);
            }
        }

        private boolean hasFlag(long flag, long modifierBits) {
            return (flag & modifierBits) != 0;
        }

        private List<FilterFlag> flagsToModifiers(long modifierBits) {
            List<FilterFlag> list = new ArrayList<>();
            boolean isPackage = true;
            if (hasFlag(com.sun.tools.javac.code.Flags.PRIVATE, modifierBits)) {
                list.add(FilterFlag.PRIVATE);
                isPackage = false;
            }
            if (hasFlag(com.sun.tools.javac.code.Flags.PROTECTED, modifierBits)) {
                list.add(FilterFlag.PROTECTED);
                isPackage = false;
            }
            if (hasFlag(com.sun.tools.javac.code.Flags.PUBLIC, modifierBits)) {
                list.add(FilterFlag.PUBLIC);
                isPackage = false;
            }
            if (isPackage) {
                list.add(FilterFlag.PACKAGE);
            }
            return list;
        }

        /**
         * Filter on modifier bits.
         *
         * @param modifierBits Bits as specified in the Modifier class
         *
         * @return Whether the modifierBits pass this filter.
         */
        public boolean checkModifier(int modifierBits) {
            return checkModifier(flagsToModifiers(modifierBits));
        }

        /**
         * Filter on Filter flags
         *
         * @param modifiers Flags as specified in the FilterFlags enumeration.
         *
         * @return if the modifier is contained.
         */
        public boolean checkModifier(List<FilterFlag> modifiers) {
            if (oneOf.contains(FilterFlag.PRIVATE)) {
                return true;
            }
            for (FilterFlag mod : modifiers) {
                if (oneOf.contains(mod)) {
                    return true;
                }
            }
            return false;
        }

    } // end ModifierFilter
}
