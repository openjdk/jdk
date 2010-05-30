/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.javac;

import sun.tools.java.*;
import sun.tools.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.Enumeration;

/**
 * Batch file parser, this needs more work.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
@Deprecated
public
class BatchParser extends Parser {
    /**
     * The current package
     */
    protected Identifier pkg;

    /**
     * The current imports
     */
    protected Imports imports;

    /**
     * The classes defined in this file
     */
    protected Vector classes;


    /**
     * The current class
     */
    protected SourceClass sourceClass;

    /**
     * The toplevel environment
     */
    protected Environment toplevelEnv;

    /**
     * Create a batch file parser
     */
    public BatchParser(Environment env, InputStream in) throws IOException {
        super(env, in);

        imports = new Imports(env);
        classes = new Vector();
        toplevelEnv = imports.newEnvironment(env);
    }

    /**
     * Package declaration
     */
    public void packageDeclaration(long where, IdentifierToken t) {
        Identifier nm = t.getName();
        //System.out.println("package " + nm);
        if (pkg == null) {
            // This code has been changed to pass an IdentifierToken,
            // rather than an Identifier, to setCurrentPackage().  Imports
            // now needs the location of the token.
            pkg = t.getName();
            imports.setCurrentPackage(t);
        } else {
            env.error(where, "package.repeated");
        }
    }

    /**
     * Import class
     */
    public void importClass(long pos, IdentifierToken t) {
        //System.out.println("import class " + t);
        imports.addClass(t);
    }

    /**
     * Import package
     */
    public void importPackage(long pos, IdentifierToken t) {
        //System.out.println("import package " + t);
        imports.addPackage(t);
    }

    /**
     * Define class
     */
    public ClassDefinition beginClass(long where, String doc, int mod,
                                      IdentifierToken t,
                                      IdentifierToken sup,
                                      IdentifierToken interfaces[]) {

        // If this class is nested, the modifier bits set here will
        // be copied into the 'SourceMember' object for the inner class
        // created during the call to 'makeClassDefinition' below.
        // When writing the class file, we will look there for the
        // 'untransformed' modifiers.  The modifiers in the ClassDefinition
        // object will end up as the 'transformed' modifiers.  Note that
        // there are some bits set here that are not legal class modifiers
        // according to the JVMS, e.g., M_PRIVATE and M_STATIC.  These are
        // masked off while writing the class file, but are preserved in
        // the InnerClasses attributes.

        if (tracing) toplevelEnv.dtEnter("beginClass: " + sourceClass);

        SourceClass outerClass = sourceClass;

        if (outerClass == null && pkg != null) {
            t = new IdentifierToken(t.getWhere(),
                                    Identifier.lookup(pkg, t.getName()));
        }

        // The defaults for anonymous and local classes should be documented!

        if ((mod & M_ANONYMOUS) != 0) {
            mod |= (M_FINAL | M_PRIVATE);
        }
        if ((mod & M_LOCAL) != 0) {
            mod |= M_PRIVATE;
        }

        // Certain modifiers are implied as follows:
        //
        // 1.  Any interface (nested or not) is implicitly deemed to be abstract,
        //     whether it is explicitly marked so or not.  (Java 1.0.)
        // 2.  A interface which is a member of a type is implicitly deemed to
        //     be static, whether it is explicitly marked so or not.  (InnerClasses)
        // 3a. A type which is a member of an interface is implicitly deemed
        //     to be public, whether it is explicitly marked so or not. (InnerClasses)
        // 3b. A type which is a member of an interface is implicitly deemed
        //     to be static, whether it is explicitly marked so or not. (InnerClasses)

        if ((mod & M_INTERFACE) != 0) {
            // Rule 1.
            mod |= M_ABSTRACT;
            if (outerClass != null) {
                // Rule 2.
                mod |= M_STATIC;
            }
        }

        if (outerClass != null && outerClass.isInterface()) {
            // Rule 3a.
            // For interface members, neither 'private' nor 'protected'
            // are legal modifiers.  We avoid setting M_PUBLIC in some
            // cases in order to avoid interfering with error detection
            // and reporting.  This is patched up, after reporting an
            // error, by 'SourceClass.addMember'.
            if ((mod & (M_PRIVATE | M_PROTECTED)) == 0)
                mod |= M_PUBLIC;
            // Rule 3b.
            mod |= M_STATIC;
        }

        // For nested classes, we must transform 'protected' to 'public'
        // and 'private' to package scope.  This must be done later,
        // because any modifiers set here will be copied into the
        // 'MemberDefinition' for the nested class, which must represent
        // the original untransformed modifiers.  Also, compile-time
        // checks should be performed against the actual, untransformed
        // modifiers.  This is in contrast to transformations that implement
        // implicit modifiers, such as M_STATIC and M_FINAL for fields
        // of interfaces.

        sourceClass = (SourceClass)
            toplevelEnv.makeClassDefinition(toplevelEnv, where, t,
                                            doc, mod, sup,
                                            interfaces, outerClass);

        sourceClass.getClassDeclaration().setDefinition(sourceClass, CS_PARSED);
        env = new Environment(toplevelEnv, sourceClass);

        if (tracing) toplevelEnv.dtEvent("beginClass: SETTING UP DEPENDENCIES");

        // The code which adds artificial dependencies between
        // classes in the same source file has been moved to
        // BatchEnvironment#parseFile().

        if (tracing) toplevelEnv.dtEvent("beginClass: ADDING TO CLASS LIST");

        classes.addElement(sourceClass);

        if (tracing) toplevelEnv.dtExit("beginClass: " + sourceClass);

        return sourceClass;
    }

    /**
     * Report the current class under construction.
     */
    public ClassDefinition getCurrentClass() {
        return sourceClass;
    }

    /**
     * End class
     */
    public void endClass(long where, ClassDefinition c) {

        if (tracing) toplevelEnv.dtEnter("endClass: " + sourceClass);

        // c == sourceClass; don't bother to check
        sourceClass.setEndPosition(where);
        SourceClass outerClass = (SourceClass) sourceClass.getOuterClass();
        sourceClass = outerClass;
        env = toplevelEnv;
        if (sourceClass != null)
            env = new Environment(env, sourceClass);

        if (tracing) toplevelEnv.dtExit("endClass: " + sourceClass);
    }

    /**
     * Define a method
     */
    public void defineField(long where, ClassDefinition c,
                            String doc, int mod, Type t,
                            IdentifierToken name, IdentifierToken args[],
                            IdentifierToken exp[], Node val) {
        // c == sourceClass; don't bother to check
        Identifier nm = name.getName();
        // Members that are nested classes are not created with 'defineField',
        // so these transformations do not apply to them.  See 'beginClass' above.
        if (sourceClass.isInterface()) {
            // Members of interfaces are implicitly public.
            if ((mod & (M_PRIVATE | M_PROTECTED)) == 0)
                // For interface members, neither 'private' nor 'protected'
                // are legal modifiers.  Avoid setting M_PUBLIC in some cases
                // to avoid interfering with later error detection.  This will
                // be fixed up after the error is reported.
                mod |= M_PUBLIC;
            // Methods of interfaces are implicitly abstract.
            // Fields of interfaces are implicitly static and final.
            if (t.isType(TC_METHOD)) {
                mod |= M_ABSTRACT;
            } else {
                mod |= M_STATIC | M_FINAL;
            }
        }
        if (nm.equals(idInit)) {
            // The parser reports "idInit" when in reality it has found
            // that there is no method name at all present.
            // So, decide if it's really a constructor, or a syntax error.
            Type rt = t.getReturnType();
            Identifier retname = !rt.isType(TC_CLASS) ? idStar /*no match*/
                                                      : rt.getClassName();
            Identifier clsname = sourceClass.getLocalName();
            if (clsname.equals(retname)) {
                t = Type.tMethod(Type.tVoid, t.getArgumentTypes());
            } else if (clsname.equals(retname.getFlatName().getName())) {
                // It appears to be a constructor with spurious qualification.
                t = Type.tMethod(Type.tVoid, t.getArgumentTypes());
                env.error(where, "invalid.method.decl.qual");
            } else if (retname.isQualified() || retname.equals(idStar)) {
                // It appears to be a type name with no method name.
                env.error(where, "invalid.method.decl.name");
                return;
            } else {
                // We assume the type name is missing, even though the
                // simple name that's present might have been intended
                // to be a type:  "String (){}" vs. "toString(){}".
                env.error(where, "invalid.method.decl");
                return;
            }
        }

        if (args == null && t.isType(TC_METHOD)) {
            args = new IdentifierToken[0];
        }

        if (exp == null && t.isType(TC_METHOD)) {
            exp = new IdentifierToken[0];
        }

        MemberDefinition f = env.makeMemberDefinition(env, where, sourceClass,
                                                    doc, mod, t, nm,
                                                    args, exp, val);
        if (env.dump()) {
            f.print(System.out);
        }
    }
}
