/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import java.lang.reflect.Modifier;
import java.util.*;

import javax.tools.JavaFileManager;

import com.sun.javadoc.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;
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
 * @since 1.4
 * @author Robert Field
 * @author Neal Gafter (rewrite)
 * @author Scott Seligman (generics)
 */
public class DocEnv {
    protected static final Context.Key<DocEnv> docEnvKey =
        new Context.Key<DocEnv>();

    public static DocEnv instance(Context context) {
        DocEnv instance = context.get(docEnvKey);
        if (instance == null)
            instance = new DocEnv(context);
        return instance;
    }

    private Messager messager;

    DocLocale doclocale;

    /** Predefined symbols known to the compiler. */
    Symtab syms;

    /** Referenced directly in RootDocImpl. */
    JavadocClassReader reader;

    /** Javadoc's own version of the compiler's enter phase. */
    JavadocEnter enter;

    /** The name table. */
    Names names;

    /** The encoding name. */
    private String encoding;

    final Symbol externalizableSym;

    /** Access filter (public, protected, ...).  */
    protected ModifierFilter showAccess;

    /** True if we are using a sentence BreakIterator. */
    boolean breakiterator;

    /**
     * True if we do not want to print any notifications at all.
     */
    boolean quiet = false;

    Check chk;
    Types types;
    JavaFileManager fileManager;
    Context context;
    DocLint doclint;

    WeakHashMap<JCTree, TreePath> treePaths = new WeakHashMap<JCTree, TreePath>();

    /** Allow documenting from class files? */
    boolean docClasses = false;

    /** Does the doclet only expect pre-1.5 doclet API? */
    protected boolean legacyDoclet = true;

    /**
     * Set this to true if you would like to not emit any errors, warnings and
     * notices.
     */
    private boolean silent = false;

    /**
     * The source language version.
     */
    protected Source source;

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
        reader = JavadocClassReader.instance0(context);
        enter = JavadocEnter.instance0(context);
        names = Names.instance(context);
        externalizableSym = reader.enterClass(names.fromString("java.io.Externalizable"));
        chk = Check.instance(context);
        types = Types.instance(context);
        fileManager = context.get(JavaFileManager.class);

        // Default.  Should normally be reset with setLocale.
        this.doclocale = new DocLocale(this, "", breakiterator);
        source = Source.instance(context);
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /**
     * Look up ClassDoc by qualified name.
     */
    public ClassDocImpl lookupClass(String name) {
        ClassSymbol c = getClassSymbol(name);
        if (c != null) {
            return getClassDoc(c);
        } else {
            return null;
        }
    }

    /**
     * Load ClassDoc by qualified name.
     */
    public ClassDocImpl loadClass(String name) {
        try {
            ClassSymbol c = reader.loadClass(names.fromString(name));
            return getClassDoc(c);
        } catch (CompletionFailure ex) {
            chk.completionError(null, ex);
            return null;
        }
    }

    /**
     * Look up PackageDoc by qualified name.
     */
    public PackageDocImpl lookupPackage(String name) {
        //### Jing alleges that class check is needed
        //### to avoid a compiler bug.  Most likely
        //### instead a dummy created for error recovery.
        //### Should investigate this.
        PackageSymbol p = syms.packages.get(names.fromString(name));
        ClassSymbol c = getClassSymbol(name);
        if (p != null && c == null) {
            return getPackageDoc(p);
        } else {
            return null;
        }
    }
        // where
        /** Retrieve class symbol by fully-qualified name.
         */
        ClassSymbol getClassSymbol(String name) {
            // Name may contain nested class qualification.
            // Generate candidate flatnames with successively shorter
            // package qualifiers and longer nested class qualifiers.
            int nameLen = name.length();
            char[] nameChars = name.toCharArray();
            int idx = name.length();
            for (;;) {
                ClassSymbol s = syms.classes.get(names.fromChars(nameChars, 0, nameLen));
                if (s != null)
                    return s; // found it!
                idx = name.substring(0, idx).lastIndexOf('.');
                if (idx < 0) break;
                nameChars[idx] = '$';
            }
            return null;
        }

    /**
     * Set the locale.
     */
    public void setLocale(String localeName) {
        // create locale specifics
        doclocale = new DocLocale(this, localeName, breakiterator);
        // update Messager if locale has changed.
        messager.setLocale(doclocale.locale);
    }

    /** Check whether this member should be documented. */
    public boolean shouldDocument(VarSymbol sym) {
        long mod = sym.flags();

        if ((mod & Flags.SYNTHETIC) != 0) {
            return false;
        }

        return showAccess.checkModifier(translateModifiers(mod));
    }

    /** Check whether this member should be documented. */
    public boolean shouldDocument(MethodSymbol sym) {
        long mod = sym.flags();

        if ((mod & Flags.SYNTHETIC) != 0) {
            return false;
        }

        return showAccess.checkModifier(translateModifiers(mod));
    }

    /** check whether this class should be documented. */
    public boolean shouldDocument(ClassSymbol sym) {
        return
            (sym.flags_field&Flags.SYNTHETIC) == 0 && // no synthetics
            (docClasses || getClassDoc(sym).tree != null) &&
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
    protected boolean isVisible(ClassSymbol sym) {
        long mod = sym.flags_field;
        if (!showAccess.checkModifier(translateModifiers(mod))) {
            return false;
        }
        ClassSymbol encl = sym.owner.enclClass();
        return (encl == null || (mod & Flags.STATIC) != 0 || isVisible(encl));
    }

    //---------------- print forwarders ----------------//

    /**
     * Print error message, increment error count.
     *
     * @param msg message to print.
     */
    public void printError(String msg) {
        if (silent)
            return;
        messager.printError(msg);
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     */
    public void error(DocImpl doc, String key) {
        if (silent)
            return;
        messager.error(doc==null ? null : doc.position(), key);
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     */
    public void error(SourcePosition pos, String key) {
        if (silent)
            return;
        messager.error(pos, key);
    }

    /**
     * Print error message, increment error count.
     *
     * @param msg message to print.
     */
    public void printError(SourcePosition pos, String msg) {
        if (silent)
            return;
        messager.printError(pos, msg);
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     */
    public void error(DocImpl doc, String key, String a1) {
        if (silent)
            return;
        messager.error(doc==null ? null : doc.position(), key, a1);
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     */
    public void error(DocImpl doc, String key, String a1, String a2) {
        if (silent)
            return;
        messager.error(doc==null ? null : doc.position(), key, a1, a2);
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     * @param a3 third argument
     */
    public void error(DocImpl doc, String key, String a1, String a2, String a3) {
        if (silent)
            return;
        messager.error(doc==null ? null : doc.position(), key, a1, a2, a3);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param msg message to print.
     */
    public void printWarning(String msg) {
        if (silent)
            return;
        messager.printWarning(msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     */
    public void warning(DocImpl doc, String key) {
        if (silent)
            return;
        messager.warning(doc==null ? null : doc.position(), key);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param msg message to print.
     */
    public void printWarning(SourcePosition pos, String msg) {
        if (silent)
            return;
        messager.printWarning(pos, msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     */
    public void warning(DocImpl doc, String key, String a1) {
        if (silent)
            return;
        // suppress messages that have (probably) been covered by doclint
        if (doclint != null && doc != null && key.startsWith("tag"))
            return;
        messager.warning(doc==null ? null : doc.position(), key, a1);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     */
    public void warning(DocImpl doc, String key, String a1, String a2) {
        if (silent)
            return;
        messager.warning(doc==null ? null : doc.position(), key, a1, a2);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     * @param a3 third argument
     */
    public void warning(DocImpl doc, String key, String a1, String a2, String a3) {
        if (silent)
            return;
        messager.warning(doc==null ? null : doc.position(), key, a1, a2, a3);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     * @param a3 third argument
     */
    public void warning(DocImpl doc, String key, String a1, String a2, String a3,
                        String a4) {
        if (silent)
            return;
        messager.warning(doc==null ? null : doc.position(), key, a1, a2, a3, a4);
    }

    /**
     * Print a message.
     *
     * @param msg message to print.
     */
    public void printNotice(String msg) {
        if (silent || quiet)
            return;
        messager.printNotice(msg);
    }


    /**
     * Print a message.
     *
     * @param key selects message from resource
     */
    public void notice(String key) {
        if (silent || quiet)
            return;
        messager.notice(key);
    }

    /**
     * Print a message.
     *
     * @param msg message to print.
     */
    public void printNotice(SourcePosition pos, String msg) {
        if (silent || quiet)
            return;
        messager.printNotice(pos, msg);
    }

    /**
     * Print a message.
     *
     * @param key selects message from resource
     * @param a1 first argument
     */
    public void notice(String key, String a1) {
        if (silent || quiet)
            return;
        messager.notice(key, a1);
    }

    /**
     * Print a message.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     */
    public void notice(String key, String a1, String a2) {
        if (silent || quiet)
            return;
        messager.notice(key, a1, a2);
    }

    /**
     * Print a message.
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     * @param a3 third argument
     */
    public void notice(String key, String a1, String a2, String a3) {
        if (silent || quiet)
            return;
        messager.notice(key, a1, a2, a3);
    }

    /**
     * Exit, reporting errors and warnings.
     */
    public void exit() {
        // Messager should be replaced by a more general
        // compilation environment.  This can probably
        // subsume DocEnv as well.
        messager.exit();
    }

    protected Map<PackageSymbol, PackageDocImpl> packageMap =
            new HashMap<PackageSymbol, PackageDocImpl>();
    /**
     * Return the PackageDoc of this package symbol.
     */
    public PackageDocImpl getPackageDoc(PackageSymbol pack) {
        PackageDocImpl result = packageMap.get(pack);
        if (result != null) return result;
        result = new PackageDocImpl(this, pack);
        packageMap.put(pack, result);
        return result;
    }

    /**
     * Create the PackageDoc (or a subtype) for a package symbol.
     */
    void makePackageDoc(PackageSymbol pack, TreePath treePath) {
        PackageDocImpl result = packageMap.get(pack);
        if (result != null) {
            if (treePath != null) result.setTreePath(treePath);
        } else {
            result = new PackageDocImpl(this, pack, treePath);
            packageMap.put(pack, result);
        }
    }


    protected Map<ClassSymbol, ClassDocImpl> classMap =
            new HashMap<ClassSymbol, ClassDocImpl>();
    /**
     * Return the ClassDoc (or a subtype) of this class symbol.
     */
    public ClassDocImpl getClassDoc(ClassSymbol clazz) {
        ClassDocImpl result = classMap.get(clazz);
        if (result != null) return result;
        if (isAnnotationType(clazz)) {
            result = new AnnotationTypeDocImpl(this, clazz);
        } else {
            result = new ClassDocImpl(this, clazz);
        }
        classMap.put(clazz, result);
        return result;
    }

    /**
     * Create the ClassDoc (or a subtype) for a class symbol.
     */
    protected void makeClassDoc(ClassSymbol clazz, TreePath treePath) {
        ClassDocImpl result = classMap.get(clazz);
        if (result != null) {
            if (treePath != null) result.setTreePath(treePath);
            return;
        }
        if (isAnnotationType((JCClassDecl) treePath.getLeaf())) {   // flags of clazz may not yet be set
            result = new AnnotationTypeDocImpl(this, clazz, treePath);
        } else {
            result = new ClassDocImpl(this, clazz, treePath);
        }
        classMap.put(clazz, result);
    }

    protected static boolean isAnnotationType(ClassSymbol clazz) {
        return ClassDocImpl.isAnnotationType(clazz);
    }

    protected static boolean isAnnotationType(JCClassDecl tree) {
        return (tree.mods.flags & Flags.ANNOTATION) != 0;
    }

    protected Map<VarSymbol, FieldDocImpl> fieldMap =
            new HashMap<VarSymbol, FieldDocImpl>();
    /**
     * Return the FieldDoc of this var symbol.
     */
    public FieldDocImpl getFieldDoc(VarSymbol var) {
        FieldDocImpl result = fieldMap.get(var);
        if (result != null) return result;
        result = new FieldDocImpl(this, var);
        fieldMap.put(var, result);
        return result;
    }
    /**
     * Create a FieldDoc for a var symbol.
     */
    protected void makeFieldDoc(VarSymbol var, TreePath treePath) {
        FieldDocImpl result = fieldMap.get(var);
        if (result != null) {
            if (treePath != null) result.setTreePath(treePath);
        } else {
            result = new FieldDocImpl(this, var, treePath);
            fieldMap.put(var, result);
        }
    }

    protected Map<MethodSymbol, ExecutableMemberDocImpl> methodMap =
            new HashMap<MethodSymbol, ExecutableMemberDocImpl>();
    /**
     * Create a MethodDoc for this MethodSymbol.
     * Should be called only on symbols representing methods.
     */
    protected void makeMethodDoc(MethodSymbol meth, TreePath treePath) {
        MethodDocImpl result = (MethodDocImpl)methodMap.get(meth);
        if (result != null) {
            if (treePath != null) result.setTreePath(treePath);
        } else {
            result = new MethodDocImpl(this, meth, treePath);
            methodMap.put(meth, result);
        }
    }

    /**
     * Return the MethodDoc for a MethodSymbol.
     * Should be called only on symbols representing methods.
     */
    public MethodDocImpl getMethodDoc(MethodSymbol meth) {
        assert !meth.isConstructor() : "not expecting a constructor symbol";
        MethodDocImpl result = (MethodDocImpl)methodMap.get(meth);
        if (result != null) return result;
        result = new MethodDocImpl(this, meth);
        methodMap.put(meth, result);
        return result;
    }

    /**
     * Create the ConstructorDoc for a MethodSymbol.
     * Should be called only on symbols representing constructors.
     */
    protected void makeConstructorDoc(MethodSymbol meth, TreePath treePath) {
        ConstructorDocImpl result = (ConstructorDocImpl)methodMap.get(meth);
        if (result != null) {
            if (treePath != null) result.setTreePath(treePath);
        } else {
            result = new ConstructorDocImpl(this, meth, treePath);
            methodMap.put(meth, result);
        }
    }

    /**
     * Return the ConstructorDoc for a MethodSymbol.
     * Should be called only on symbols representing constructors.
     */
    public ConstructorDocImpl getConstructorDoc(MethodSymbol meth) {
        assert meth.isConstructor() : "expecting a constructor symbol";
        ConstructorDocImpl result = (ConstructorDocImpl)methodMap.get(meth);
        if (result != null) return result;
        result = new ConstructorDocImpl(this, meth);
        methodMap.put(meth, result);
        return result;
    }

    /**
     * Create the AnnotationTypeElementDoc for a MethodSymbol.
     * Should be called only on symbols representing annotation type elements.
     */
    protected void makeAnnotationTypeElementDoc(MethodSymbol meth, TreePath treePath) {
        AnnotationTypeElementDocImpl result =
            (AnnotationTypeElementDocImpl)methodMap.get(meth);
        if (result != null) {
            if (treePath != null) result.setTreePath(treePath);
        } else {
            result =
                new AnnotationTypeElementDocImpl(this, meth, treePath);
            methodMap.put(meth, result);
        }
    }

    /**
     * Return the AnnotationTypeElementDoc for a MethodSymbol.
     * Should be called only on symbols representing annotation type elements.
     */
    public AnnotationTypeElementDocImpl getAnnotationTypeElementDoc(
            MethodSymbol meth) {

        AnnotationTypeElementDocImpl result =
            (AnnotationTypeElementDocImpl)methodMap.get(meth);
        if (result != null) return result;
        result = new AnnotationTypeElementDocImpl(this, meth);
        methodMap.put(meth, result);
        return result;
    }

//  private Map<ClassType, ParameterizedTypeImpl> parameterizedTypeMap =
//          new HashMap<ClassType, ParameterizedTypeImpl>();
    /**
     * Return the ParameterizedType of this instantiation.
//   * ### Could use Type.sameTypeAs() instead of equality matching in hashmap
//   * ### to avoid some duplication.
     */
    ParameterizedTypeImpl getParameterizedType(ClassType t) {
        return new ParameterizedTypeImpl(this, t);
//      ParameterizedTypeImpl result = parameterizedTypeMap.get(t);
//      if (result != null) return result;
//      result = new ParameterizedTypeImpl(this, t);
//      parameterizedTypeMap.put(t, result);
//      return result;
    }

    TreePath getTreePath(JCCompilationUnit tree) {
        TreePath p = treePaths.get(tree);
        if (p == null)
            treePaths.put(tree, p = new TreePath(tree));
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

    /**
     * Set the encoding.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
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

    void initDoclint(Collection<String> opts) {
        ArrayList<String> doclintOpts = new ArrayList<String>();

        for (String opt: opts) {
            doclintOpts.add(opt == null ? DocLint.XMSGS_OPTION : DocLint.XMSGS_CUSTOM_PREFIX + opt);
        }

        if (doclintOpts.isEmpty()) {
            doclintOpts.add(DocLint.XMSGS_OPTION);
        } else if (doclintOpts.size() == 1
                && doclintOpts.get(0).equals(DocLint.XMSGS_CUSTOM_PREFIX + "none")) {
            return;
        }

        JavacTask t = BasicJavacTask.instance(context);
        doclint = new DocLint();
        // standard doclet normally generates H1, H2
        doclintOpts.add(DocLint.XIMPLICIT_HEADERS + "2");
        doclint.init(t, doclintOpts.toArray(new String[doclintOpts.size()]), false);
    }

    boolean showTagMessages() {
        return (doclint == null);
    }
}
