/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;
import java.util.List;
import java.util.Collections;
import java.io.IOException;

/**
 * This class describes the classes and packages imported
 * from a source file. A Hashtable called bindings is maintained
 * to quickly map symbol names to classes. This table is flushed
 * everytime a new import is added.
 *
 * A class name is resolved as follows:
 *  - if it is a qualified name then return the corresponding class
 *  - if the name corresponds to an individually imported class then return that class
 *  - check if the class is defined in any of the imported packages,
 *    if it is then return it, make sure it is defined in only one package
 *  - assume that the class is defined in the current package
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */

public
class Imports implements Constants {
    /**
     * The current package, which is implicitly imported,
     * and has precedence over other imported packages.
     */
    Identifier currentPackage = idNull;

    /**
     * A location for the current package declaration.  Used to
     * report errors against the current package.
     */
    long currentPackageWhere = 0;

    /**
     * The imported classes, including memoized imports from packages.
     */
    Hashtable classes = new Hashtable();

    /**
     * The imported package identifiers.  This will not contain duplicate
     * imports for the same package.  It will also not contain the
     * current package.
     */
    Vector packages = new Vector();

    /**
     * The (originally) imported classes.
     * A vector of IdentifierToken.
     */
    Vector singles = new Vector();

    /**
     * Are the import names checked yet?
     */
    protected int checked;

    /**
     * Constructor, always import java.lang.
     */
    public Imports(Environment env) {
        addPackage(idJavaLang);
    }

    /**
     * Check the names of the imports.
     */
    public synchronized void resolve(Environment env) {
        if (checked != 0) {
            return;
        }
        checked = -1;

        // After all class information has been read, now we can
        // safely inspect import information for errors.
        // If we did this before all parsing was finished,
        // we could get vicious circularities, since files can
        // import each others' classes.

        // A note: the resolution of the package java.lang takes place
        // in the sun.tools.javac.BatchEnvironment#setExemptPackages().

        // Make sure that the current package's name does not collide
        // with the name of an existing class. (bug 4101529)
        //
        // This change has been backed out because, on WIN32, it
        // failed to distinguish between java.awt.event and
        // java.awt.Event when looking for a directory.  We will
        // add this back in later.
        //
        // if (currentPackage != idNull) {
        //    Identifier resolvedName =
        //      env.resolvePackageQualifiedName(currentPackage);
        //
        //   Identifier className = resolvedName.getTopName();
        //
        //   if (importable(className, env)) {
        //      // The name of the current package is also the name
        //      // of a class.
        //      env.error(currentPackageWhere, "package.class.conflict",
        //                currentPackage, className);
        //     }
        // }

        Vector resolvedPackages = new Vector();
        for (Enumeration e = packages.elements() ; e.hasMoreElements() ;) {
            IdentifierToken t = (IdentifierToken)e.nextElement();
            Identifier nm = t.getName();
            long where = t.getWhere();

            // Check to see if this package is exempt from the "exists"
            // check.  See the note in
            // sun.tools.javac.BatchEnvironment#setExemptPackages()
            // for more information.
            if (env.isExemptPackage(nm)) {
                resolvedPackages.addElement(t);
                continue;
            }

            // (Note: This code is moved from BatchParser.importPackage().)
            try {
                Identifier rnm = env.resolvePackageQualifiedName(nm);
                if (importable(rnm, env)) {
                    // This name is a real class; better not be a package too.
                    if (env.getPackage(rnm.getTopName()).exists()) {
                        env.error(where, "class.and.package",
                                  rnm.getTopName());
                    }
                    // Pass an "inner" name to the imports.
                    if (!rnm.isInner())
                        rnm = Identifier.lookupInner(rnm, idNull);
                    nm = rnm;
                } else if (!env.getPackage(nm).exists()) {
                    env.error(where, "package.not.found", nm, "import");
                } else if (rnm.isInner()) {
                    // nm exists, and rnm.getTopName() is a parent package
                    env.error(where, "class.and.package", rnm.getTopName());
                }
                resolvedPackages.addElement(new IdentifierToken(where, nm));
            } catch (IOException ee) {
                env.error(where, "io.exception", "import");
            }
        }
        packages = resolvedPackages;

        for (Enumeration e = singles.elements() ; e.hasMoreElements() ;) {
            IdentifierToken t = (IdentifierToken)e.nextElement();
            Identifier nm = t.getName();
            long where = t.getWhere();
            Identifier pkg = nm.getQualifier();

            // (Note: This code is moved from BatchParser.importClass().)
            nm = env.resolvePackageQualifiedName(nm);
            if (!env.classExists(nm.getTopName())) {
                env.error(where, "class.not.found", nm, "import");
            }

            // (Note: This code is moved from Imports.addClass().)
            Identifier snm = nm.getFlatName().getName();

            // make sure it isn't already imported explicitly
            Identifier className = (Identifier)classes.get(snm);
            if (className != null) {
                Identifier f1 = Identifier.lookup(className.getQualifier(),
                                                  className.getFlatName());
                Identifier f2 = Identifier.lookup(nm.getQualifier(),
                                                  nm.getFlatName());
                if (!f1.equals(f2)) {
                    env.error(where, "ambig.class", nm, className);
                }
            }
            classes.put(snm, nm);


            // The code here needs to check to see, if we
            // are importing an inner class, that all of its
            // enclosing classes are visible to us.  To check this,
            // we need to construct a definition for the class.
            // The code here used to call...
            //
            //     ClassDefinition def = env.getClassDefinition(nm);
            //
            // ...but that interfered with the basicCheck()'ing of
            // interfaces in certain cases (bug no. 4086139).  Never
            // fear.  Instead we load the class with a call to the
            // new getClassDefinitionNoCheck() which does no basicCheck() and
            // lets us answer the questions we are interested in w/o
            // interfering with the demand-driven nature of basicCheck().

            try {
                // Get a declaration
                ClassDeclaration decl = env.getClassDeclaration(nm);

                // Get the definition (no env argument)
                ClassDefinition def = decl.getClassDefinitionNoCheck(env);

                // Get the true name of the package containing this class.
                // `pkg' from above is insufficient.  It includes the
                // names of our enclosing classes.  Fix for 4086815.
                Identifier importedPackage = def.getName().getQualifier();

                // Walk out the outerClass chain, ensuring that each level
                // is visible from our perspective.
                for (; def != null; def = def.getOuterClass()) {
                    if (def.isPrivate()
                        || !(def.isPublic()
                             || importedPackage.equals(currentPackage))) {
                        env.error(where, "cant.access.class", def);
                        break;
                    }
                }
            } catch (AmbiguousClass ee) {
                env.error(where, "ambig.class", ee.name1, ee.name2);
            } catch (ClassNotFound ee) {
                env.error(where, "class.not.found", ee.name, "import");
            }
        }
        checked = 1;
    }

    /**
     * Lookup a class, given the current set of imports,
     * AmbiguousClass exception is thrown if the name can be
     * resolved in more than one way. A ClassNotFound exception
     * is thrown if the class is not found in the imported classes
     * and packages.
     */
    public synchronized Identifier resolve(Environment env, Identifier nm) throws ClassNotFound {
        if (tracing) env.dtEnter("Imports.resolve: " + nm);

        // If the class has the special ambiguous prefix, then we will
        // get the original AmbiguousClass exception by removing the
        // prefix and proceeding in the normal fashion.
        // (part of solution for 4059855)
        if (nm.hasAmbigPrefix()) {
            nm = nm.removeAmbigPrefix();
        }

        if (nm.isQualified()) {
            // Don't bother it is already qualified
            if (tracing) env.dtExit("Imports.resolve: QUALIFIED " + nm);
            return nm;
        }

        if (checked <= 0) {
            checked = 0;
            resolve(env);
        }

        // Check if it was imported before
        Identifier className = (Identifier)classes.get(nm);
        if (className != null) {
            if (tracing) env.dtExit("Imports.resolve: PREVIOUSLY IMPORTED " + nm);
            return className;
        }

        // Note: the section below has changed a bit during the fix
        // for bug 4093217.  The current package is no longer grouped
        // with the rest of the import-on-demands; it is now checked
        // separately.  Also, the list of import-on-demands is now
        // guarranteed to be duplicate-free, so the code below can afford
        // to be a bit simpler.

        // First we look in the current package.  The current package
        // is given precedence over the rest of the import-on-demands,
        // which means, among other things, that a class in the current
        // package cannot be ambiguous.
        Identifier id = Identifier.lookup(currentPackage, nm);
        if (importable(id, env)) {
            className = id;
        } else {
            // If it isn't in the current package, try to find it in
            // our import-on-demands.
            Enumeration e = packages.elements();
            while (e.hasMoreElements()) {
                IdentifierToken t = (IdentifierToken)e.nextElement();
                id = Identifier.lookup(t.getName(), nm);

                if (importable(id, env)) {
                    if (className == null) {
                        // We haven't found any other matching classes yet.
                        // Set className to what we've found and continue
                        // looking for an ambiguity.
                        className = id;
                    } else {
                        if (tracing)
                            env.dtExit("Imports.resolve: AMBIGUOUS " + nm);

                        // We've found an ambiguity.
                        throw new AmbiguousClass(className, id);
                    }
                }
            }
        }

        // Make sure a class was found
        if (className == null) {
            if (tracing) env.dtExit("Imports.resolve: NOT FOUND " + nm);
            throw new ClassNotFound(nm);
        }

        // Remember the binding
        classes.put(nm, className);
        if (tracing) env.dtExit("Imports.resolve: FIRST IMPORT " + nm);
        return className;
    }

    /**
     * Check to see if 'id' names an importable class in `env'.
     * This method was made public and static for utility.
     */
    static public boolean importable(Identifier id, Environment env) {
        if (!id.isInner()) {
            return env.classExists(id);
        } else if (!env.classExists(id.getTopName())) {
            return false;
        } else {
            // load the top class and look inside it
            try {
                // There used to be a call to...
                //    env.getClassDeclaration(id.getTopName());
                // ...here.  It has been replaced with the
                // two statements below.  These should be functionally
                // the same except for the fact that
                // getClassDefinitionNoCheck() does not call
                // basicCheck().  This allows us to avoid a circular
                // need to do basicChecking that can arise with
                // certain patterns of importing and inheritance.
                // This is a fix for a variant of bug 4086139.
                //
                // Note: the special case code in env.getClassDefinition()
                // which handles inner class names is not replicated below.
                // This should be okay, as we are looking up id.getTopName(),
                // not id.
                ClassDeclaration decl =
                    env.getClassDeclaration(id.getTopName());
                ClassDefinition c =
                    decl.getClassDefinitionNoCheck(env);

                return c.innerClassExists(id.getFlatName().getTail());
            } catch (ClassNotFound ee) {
                return false;
            }
        }
    }

    /**
     * Suppose a resolve() call has failed.
     * This routine can be used silently to give a reasonable
     * default qualification (the current package) to the identifier.
     * This decision is recorded for future reference.
     */
    public synchronized Identifier forceResolve(Environment env, Identifier nm) {
        if (nm.isQualified())
            return nm;

        Identifier className = (Identifier)classes.get(nm);
        if (className != null) {
            return className;
        }

        className = Identifier.lookup(currentPackage, nm);

        classes.put(nm, className);
        return className;
    }

    /**
     * Add a class import
     */
    public synchronized void addClass(IdentifierToken t) {
        singles.addElement(t);
    }
    // for compatibility
    public void addClass(Identifier nm) throws AmbiguousClass {
        addClass(new IdentifierToken(nm));
    }

    /**
     * Add a package import, or perhaps an inner class scope.
     * Ignore any duplicate imports.
     */
    public synchronized void addPackage(IdentifierToken t) {
        final Identifier name = t.getName();

        // If this is a duplicate import for the current package,
        // ignore it.
        if (name == currentPackage) {
            return;
        }

        // If this is a duplicate of a package which has already been
        // added to the list, ignore it.
        final int size = packages.size();
        for (int i = 0; i < size; i++) {
            if (name == ((IdentifierToken)packages.elementAt(i)).getName()) {
                return;
            }
        }

        // Add the package to the list.
        packages.addElement(t);
    }
    // for compatibility
    public void addPackage(Identifier id) {
        addPackage(new IdentifierToken(id));
    }

    /**
     * Specify the current package with an IdentifierToken.
     */
    public synchronized void setCurrentPackage(IdentifierToken t) {
        currentPackage = t.getName();
        currentPackageWhere = t.getWhere();
    }

    /**
     * Specify the current package
     */
    public synchronized void setCurrentPackage(Identifier id) {
        currentPackage = id;
    }

    /**
     * Report the current package
     */
    public Identifier getCurrentPackage() {
        return currentPackage;
    }

    /**
     * Return an unmodifiable list of IdentifierToken representing
     * packages specified as imports.
     */
    public List getImportedPackages() {
        return Collections.unmodifiableList(packages);
    }

    /**
     * Return an unmodifiable list of IdentifierToken representing
     * classes specified as imports.
     */
    public List getImportedClasses() {
        return Collections.unmodifiableList(singles);
    }

    /**
     * Extend an environment with my resolve() method.
     */
    public Environment newEnvironment(Environment env) {
        return new ImportEnvironment(env, this);
    }
}

final
class ImportEnvironment extends Environment {
    Imports imports;

    ImportEnvironment(Environment env, Imports imports) {
        super(env, env.getSource());
        this.imports = imports;
    }

    public Identifier resolve(Identifier nm) throws ClassNotFound {
        return imports.resolve(this, nm);
    }

    public Imports getImports() {
        return imports;
    }
}
