/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Names;

import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.tool.DocEnv;
import jdk.javadoc.internal.tool.RootDocImpl;

import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;

import static javax.lang.model.element.ElementKind.*;

/**
 * A quarantine class to isolate all the workarounds and bridges to
 * a locality. This class should eventually disappear once all the
 * standard APIs support the needed interfaces.
 *
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class WorkArounds {

    public final Configuration configuration;
    public final DocEnv env;
    public final Utils utils;

    private DocLint doclint;

    public WorkArounds(Configuration configuration) {
        this.configuration = configuration;
        this.utils = this.configuration.utils;
        this.env = ((RootDocImpl)this.configuration.root).env;
    }

    Map<CompilationUnitTree, Boolean> shouldCheck = new HashMap<>();
    // TODO: fix this up correctly
    public void runDocLint(TreePath path) {
        CompilationUnitTree unit = path.getCompilationUnit();
        if (doclint != null && shouldCheck.computeIfAbsent(unit, doclint::shouldCheck)) {
            doclint.scan(path);
        }
    }

    // TODO: fix this up correctly
    public void initDocLint(Collection<String> opts, Collection<String> customTagNames, String htmlVersion) {
        ArrayList<String> doclintOpts = new ArrayList<>();
        boolean msgOptionSeen = false;

        for (String opt : opts) {
            if (opt.startsWith(DocLint.XMSGS_OPTION)) {
                if (opt.equals(DocLint.XMSGS_CUSTOM_PREFIX + "none"))
                    return;
                msgOptionSeen = true;
            }
            doclintOpts.add(opt);
        }

        if (!msgOptionSeen) {
            doclintOpts.add(DocLint.XMSGS_OPTION);
        }

        String sep = "";
        StringBuilder customTags = new StringBuilder();
        for (String customTag : customTagNames) {
            customTags.append(sep);
            customTags.append(customTag);
            sep = DocLint.SEPARATOR;
        }
        doclintOpts.add(DocLint.XCUSTOM_TAGS_PREFIX + customTags.toString());
        doclintOpts.add(DocLint.XHTML_VERSION_PREFIX + htmlVersion);

        JavacTask t = BasicJavacTask.instance(env.context);
        doclint = new DocLint();
        // standard doclet normally generates H1, H2
        doclintOpts.add(DocLint.XIMPLICIT_HEADERS + "2");
        doclint.init(t, doclintOpts.toArray(new String[doclintOpts.size()]), false);
    }

    // TODO: fix this up correctly
    public boolean haveDocLint() {
        return (doclint == null);
    }

    // TODO: jx.l.m directSuperTypes don't work for things like Enum,
    // so we use javac directly, investigate why jx.l.m is not cutting it.
    public List<TypeMirror> interfaceTypesOf(TypeMirror type) {
        com.sun.tools.javac.util.List<com.sun.tools.javac.code.Type> interfaces =
                ((RootDocImpl)configuration.root).env.getTypes().interfaces((com.sun.tools.javac.code.Type)type);
        if (interfaces.isEmpty()) {
            return Collections.emptyList();
        }
        List<TypeMirror> list = new ArrayList<>(interfaces.size());
        for (com.sun.tools.javac.code.Type t : interfaces) {
            list.add((TypeMirror)t);
        }
        return list;
    }

    /*
     * TODO: This method exists because of a bug in javac which does not
     * handle "@deprecated tag in package-info.java", when this issue
     * is fixed this method and its uses must be jettisoned.
     */
    public boolean isDeprecated0(Element e) {
        if (!utils.getDeprecatedTrees(e).isEmpty()) {
            return true;
        }
        JavacTypes jctypes = ((RootDocImpl)configuration.root).env.typeutils;
        TypeMirror deprecatedType = utils.getDeprecatedType();
        for (AnnotationMirror anno : e.getAnnotationMirrors()) {
            if (jctypes.isSameType(anno.getAnnotationType().asElement().asType(), deprecatedType))
                return true;
        }
        return false;
    }

    // TODO: fix jx.l.m add this method.
    public boolean isSynthesized(AnnotationMirror aDesc) {
        return ((Attribute)aDesc).isSynthesized();
    }

    // TODO: implement using jx.l.model
    public boolean isVisible(TypeElement te) {
        return env.isVisible((ClassSymbol)te);
    }

    // TODO: fix the caller
    public Object getConstValue(VariableElement ve) {
        return ((VarSymbol)ve).getConstValue();
    }

    //TODO: DocTrees: Trees.getPath(Element e) is slow a factor 4-5 times.
    public Map<Element, TreePath> getElementToTreePath() {
        return env.elementToTreePath;
    }

    // TODO: needs to ported to jx.l.m.
    public TypeElement searchClass(TypeElement klass, String className) {
        // search by qualified name first
        TypeElement te = configuration.root.getElementUtils().getTypeElement(className);
        if (te != null) {
            return te;
        }

        // search inner classes
        for (TypeElement ite : utils.getClasses(klass)) {
            TypeElement innerClass = searchClass(ite, className);
            if (innerClass != null) {
                return innerClass;
            }
        }

        // check in this package
        te = utils.findClassInPackageElement(utils.containingPackage(klass), className);
        if (te != null) {
            return te;
        }

        ClassSymbol tsym = (ClassSymbol)klass;
        // make sure that this symbol has been completed
        // TODO: do we need this anymore ?
        if (tsym.completer != null) {
            tsym.complete();
        }

        // search imports
        if (tsym.sourcefile != null) {

            //### This information is available only for source classes.
            Env<AttrContext> compenv = env.getEnv(tsym);
            if (compenv == null) {
                return null;
            }
            Names names = tsym.name.table.names;
            Scope s = compenv.toplevel.namedImportScope;
            for (Symbol sym : s.getSymbolsByName(names.fromString(className))) {
                if (sym.kind == TYP) {
                    return (TypeElement)sym;
                }
            }

            s = compenv.toplevel.starImportScope;
            for (Symbol sym : s.getSymbolsByName(names.fromString(className))) {
                if (sym.kind == TYP) {
                    return (TypeElement)sym;
                }
            }
        }

        return null; // not found
    }

    // TODO:  need to re-implement this using j.l.m. correctly!, this has
    // implications on testInterface, the note here is that javac's supertype
    // does the right thing returning Parameters in scope.
    /**
     * Return the type containing the method that this method overrides.
     * It may be a <code>TypeElement</code> or a <code>TypeParameterElement</code>.
     * @param method target
     * @return a type
     */
    public TypeMirror overriddenType(ExecutableElement method) {
        if (utils.isStatic(method)) {
            return null;
        }
        MethodSymbol sym = (MethodSymbol)method;
        ClassSymbol origin = (ClassSymbol) sym.owner;
        for (com.sun.tools.javac.code.Type t = env.getTypes().supertype(origin.type);
                t.hasTag(com.sun.tools.javac.code.TypeTag.CLASS);
                t = env.getTypes().supertype(t)) {
            ClassSymbol c = (ClassSymbol) t.tsym;
            for (com.sun.tools.javac.code.Symbol sym2 : c.members().getSymbolsByName(sym.name)) {
                if (sym.overrides(sym2, origin, env.getTypes(), true)) {
                    return t;
                }
            }
        }
        return null;
    }

    // TODO: investigate and reimplement without javac dependencies.
    public boolean shouldDocument(Element e) {
        return env.shouldDocument(e);
    }

    //------------------Start of Serializable Implementation---------------------//
    private final static Map<TypeElement, NewSerializedForm> serializedForms = new HashMap<>();

    public SortedSet<VariableElement> getSerializableFields(Utils utils, TypeElement klass) {
        NewSerializedForm sf = serializedForms.get(klass);
        if (sf == null) {
            sf = new NewSerializedForm(utils, configuration.root.getElementUtils(), klass);
            serializedForms.put(klass, sf);
        }
        return sf.fields;
    }

    public SortedSet<ExecutableElement>  getSerializationMethods(Utils utils, TypeElement klass) {
        NewSerializedForm sf = serializedForms.get(klass);
        if (sf == null) {
            sf = new NewSerializedForm(utils, configuration.root.getElementUtils(), klass);
            serializedForms.put(klass, sf);
        }
        return sf.methods;
    }

    public boolean definesSerializableFields(Utils utils, TypeElement klass) {
        if (!utils.isSerializable(klass) || utils.isExternalizable(klass)) {
            return false;
        } else {
            NewSerializedForm sf = serializedForms.get(klass);
            if (sf == null) {
                sf = new NewSerializedForm(utils, configuration.root.getElementUtils(), klass);
                serializedForms.put(klass, sf);
            }
            return sf.definesSerializableFields;
        }
    }

    /* TODO we need a clean port to jx.l.m
     * The serialized form is the specification of a class' serialization state.
     * <p>
     *
     * It consists of the following information:
     * <p>
     *
     * <pre>
     * 1. Whether class is Serializable or Externalizable.
     * 2. Javadoc for serialization methods.
     *    a. For Serializable, the optional readObject, writeObject,
     *       readResolve and writeReplace.
     *       serialData tag describes, in prose, the sequence and type
     *       of optional data written by writeObject.
     *    b. For Externalizable, writeExternal and readExternal.
     *       serialData tag describes, in prose, the sequence and type
     *       of optional data written by writeExternal.
     * 3. Javadoc for serialization data layout.
     *    a. For Serializable, the name,type and description
     *       of each Serializable fields.
     *    b. For Externalizable, data layout is described by 2(b).
     * </pre>
     *
     */
    static class NewSerializedForm {

        final Utils utils;
        final Elements elements;

        final SortedSet<ExecutableElement> methods;

        /* List of FieldDocImpl - Serializable fields.
         * Singleton list if class defines Serializable fields explicitly.
         * Otherwise, list of default serializable fields.
         * 0 length list for Externalizable.
         */
        final SortedSet<VariableElement> fields;

        /* True if class specifies serializable fields explicitly.
         * using special static member, serialPersistentFields.
         */
        boolean definesSerializableFields = false;

        // Specially treated field/method names defined by Serialization.
        private static final String SERIALIZABLE_FIELDS = "serialPersistentFields";
        private static final String READOBJECT = "readObject";
        private static final String WRITEOBJECT = "writeObject";
        private static final String READRESOLVE = "readResolve";
        private static final String WRITEREPLACE = "writeReplace";
        private static final String READOBJECTNODATA = "readObjectNoData";

        NewSerializedForm(Utils utils, Elements elements, TypeElement te) {
            this.utils = utils;
            this.elements = elements;
            methods = new TreeSet<>(utils.makeGeneralPurposeComparator());
            fields = new TreeSet<>(utils.makeGeneralPurposeComparator());
            if (utils.isExternalizable(te)) {
                /* look up required public accessible methods,
                 *   writeExternal and readExternal.
                 */
                String[] readExternalParamArr = {"java.io.ObjectInput"};
                String[] writeExternalParamArr = {"java.io.ObjectOutput"};

                ExecutableElement md = findMethod(te, "readExternal", Arrays.asList(readExternalParamArr));
                if (md != null) {
                    methods.add(md);
                }
                md = findMethod((ClassSymbol) te, "writeExternal", Arrays.asList(writeExternalParamArr));
                if (md != null) {
                    methods.add(md);
                }
            } else if (utils.isSerializable(te)) {
                VarSymbol dsf = getDefinedSerializableFields((ClassSymbol) te);
                if (dsf != null) {
                    /* Define serializable fields with array of ObjectStreamField.
                     * Each ObjectStreamField should be documented by a
                     * serialField tag.
                     */
                    definesSerializableFields = true;
                    fields.add((VariableElement) dsf);
                } else {

                    /* Calculate default Serializable fields as all
                     * non-transient, non-static fields.
                     * Fields should be documented by serial tag.
                     */
                    computeDefaultSerializableFields((ClassSymbol) te);
                }

                /* Check for optional customized readObject, writeObject,
                 * readResolve and writeReplace, which can all contain
                 * the serialData tag.        */
                addMethodIfExist((ClassSymbol) te, READOBJECT);
                addMethodIfExist((ClassSymbol) te, WRITEOBJECT);
                addMethodIfExist((ClassSymbol) te, READRESOLVE);
                addMethodIfExist((ClassSymbol) te, WRITEREPLACE);
                addMethodIfExist((ClassSymbol) te, READOBJECTNODATA);
            }
        }

        private VarSymbol getDefinedSerializableFields(ClassSymbol def) {
            Names names = def.name.table.names;

            /* SERIALIZABLE_FIELDS can be private,
             */
            for (Symbol sym : def.members().getSymbolsByName(names.fromString(SERIALIZABLE_FIELDS))) {
                if (sym.kind == VAR) {
                    VarSymbol f = (VarSymbol) sym;
                    if ((f.flags() & Flags.STATIC) != 0
                            && (f.flags() & Flags.PRIVATE) != 0) {
                        return f;
                    }
                }
            }
            return null;
        }

        /*
         * Catalog Serializable method if it exists in current ClassSymbol.
         * Do not look for method in superclasses.
         *
         * Serialization requires these methods to be non-static.
         *
         * @param method should be an unqualified Serializable method
         *               name either READOBJECT, WRITEOBJECT, READRESOLVE
         *               or WRITEREPLACE.
         * @param visibility the visibility flag for the given method.
         */
        private void addMethodIfExist(ClassSymbol def, String methodName) {
            Names names = def.name.table.names;

            for (Symbol sym : def.members().getSymbolsByName(names.fromString(methodName))) {
                if (sym.kind == MTH) {
                    MethodSymbol md = (MethodSymbol) sym;
                    if ((md.flags() & Flags.STATIC) == 0) {
                        /*
                         * WARNING: not robust if unqualifiedMethodName is overloaded
                         *          method. Signature checking could make more robust.
                         * READOBJECT takes a single parameter, java.io.ObjectInputStream.
                         * WRITEOBJECT takes a single parameter, java.io.ObjectOutputStream.
                         */
                        methods.add(md);
                    }
                }
            }
        }

        /*
         * Compute default Serializable fields from all members of ClassSymbol.
         *
         * must walk over all members of ClassSymbol.
         */
        private void computeDefaultSerializableFields(ClassSymbol te) {
            for (Symbol sym : te.members().getSymbols(NON_RECURSIVE)) {
                if (sym != null && sym.kind == VAR) {
                    VarSymbol f = (VarSymbol) sym;
                    if ((f.flags() & Flags.STATIC) == 0
                            && (f.flags() & Flags.TRANSIENT) == 0) {
                        //### No modifier filtering applied here.
                        //### Add to beginning.
                        //### Preserve order used by old 'javadoc'.
                        fields.add(f);
                    }
                }
            }
        }

        /**
         * Find a method in this class scope. Search order: this class, interfaces, superclasses,
         * outerclasses. Note that this is not necessarily what the compiler would do!
         *
         * @param methodName the unqualified name to search for.
         * @param paramTypes the array of Strings for method parameter types.
         * @return the first MethodDocImpl which matches, null if not found.
         */
        public ExecutableElement findMethod(TypeElement te, String methodName,
                List<String> paramTypes) {
            List<? extends Element> allMembers = this.elements.getAllMembers(te);
            loop:
            for (Element e : allMembers) {
                if (e.getKind() != METHOD) {
                    continue;
                }
                ExecutableElement ee = (ExecutableElement) e;
                if (!ee.getSimpleName().contentEquals(methodName)) {
                    continue;
                }
                List<? extends VariableElement> parameters = ee.getParameters();
                if (paramTypes.size() != parameters.size()) {
                    continue;
                }
                for (int i = 0; i < parameters.size(); i++) {
                    VariableElement ve = parameters.get(i);
                    if (!ve.asType().toString().equals(paramTypes.get(i))) {
                        break loop;
                    }
                }
                return ee;
            }
            TypeElement encl = utils.getEnclosingTypeElement(te);
            if (encl == null) {
                return null;
            }
            return findMethod(encl, methodName, paramTypes);
        }
    }

    // TODO: this is a fast way to get the JavaFileObject for
    // a package.html file, however we need to eliminate this.
    public JavaFileObject getJavaFileObject(PackageElement pe) {
        return env.pkgToJavaFOMap.get(pe);
    }

    // TODO: we need to eliminate this, as it is hacky.
    /**
     * Returns a representation of the package truncated to two levels.
     * For instance if the given package represents foo.bar.baz will return
     * a representation of foo.bar
     * @param pkg the PackageElement
     * @return an abbreviated PackageElement
     */
    public PackageElement getAbbreviatedPackageElement(PackageElement pkg) {
        String parsedPackageName = utils.parsePackageName(pkg);
        ModuleElement encl = (ModuleElement) pkg.getEnclosingElement();
        PackageElement abbrevPkg = encl == null
                ? utils.elementUtils.getPackageElement(parsedPackageName)
                : ((JavacElements) utils.elementUtils).getPackageElement(encl, parsedPackageName);
        return abbrevPkg;
    }
}
