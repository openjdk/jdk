/*
 * Copyright (c) 1994, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.io.OutputStream;
import java.io.PrintStream;
import sun.tools.tree.Context;
import sun.tools.tree.Vset;
import sun.tools.tree.Expression;
import sun.tools.tree.LocalMember;
import sun.tools.tree.UplevelReference;

/**
 * This class is a Java class definition
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
@SuppressWarnings("deprecation")
public
class ClassDefinition implements Constants {

    protected Object source;
    protected long where;
    protected int modifiers;
    protected Identifier localName; // for local classes
    protected ClassDeclaration declaration;
    protected IdentifierToken superClassId;
    protected IdentifierToken interfaceIds[];
    protected ClassDeclaration superClass;
    protected ClassDeclaration interfaces[];
    protected ClassDefinition outerClass;
    protected MemberDefinition outerMember;
    protected MemberDefinition innerClassMember;        // field for me in outerClass
    protected MemberDefinition firstMember;
    protected MemberDefinition lastMember;
    protected boolean resolved;
    protected String documentation;
    protected boolean error;
    protected boolean nestError;
    protected UplevelReference references;
    protected boolean referencesFrozen;
    private Hashtable<Identifier, MemberDefinition> fieldHash = new Hashtable<>(31);
    private int abstr;

    // Table of local and anonymous classes whose internal names are constructed
    // using the current class as a prefix.  This is part of a fix for
    // bugid 4054523 and 4030421.  See also 'Environment.getClassDefinition'
    // and 'BatchEnvironment.makeClassDefinition'.  Allocated on demand.
    private Hashtable<String, ClassDefinition> localClasses = null;
    private final int LOCAL_CLASSES_SIZE = 31;

    // The immediately surrounding context in which the class appears.
    // Set at the beginning of checking, upon entry to 'SourceClass.checkInternal'.
    // Null for classes that are not local or inside a local class.
    // At present, this field exists only for the benefit of 'resolveName' as part
    // of the fix for 4095716.
    protected Context classContext;

    // The saved class context is now also used in 'SourceClass.getAccessMember'.
    // Provide read-only access via this method.  Part of fix for 4098093.
    public Context getClassContext() {
        return classContext;
    }


    /**
     * Constructor
     */
    protected ClassDefinition(Object source, long where, ClassDeclaration declaration,
                              int modifiers, IdentifierToken superClass, IdentifierToken interfaces[]) {
        this.source = source;
        this.where = where;
        this.declaration = declaration;
        this.modifiers = modifiers;
        this.superClassId = superClass;
        this.interfaceIds = interfaces;
    }

    /**
     * Get the source of the class
     */
    public final Object getSource() {
        return source;
    }

    /**
     * Check if there were any errors in this class.
     */
    public final boolean getError() {
        return error;
    }

    /**
     * Mark this class to be erroneous.
     */
    public final void setError() {
        this.error = true;
        setNestError();
    }

    /**
     * Check if there were any errors in our class nest.
     */
    public final boolean getNestError() {
        // Check to see if our error value is set, or if any of our
        // outer classes' error values are set.  This will work in
        // conjunction with setError(), which sets the error value
        // of its outer class, to yield true is any of our nest
        // siblings has an error.  This addresses bug 4111488: either
        // code should be generated for all classes in a nest, or
        // none of them.
        return nestError || ((outerClass != null) ? outerClass.getNestError() : false);
    }

    /**
     * Mark this class, and all siblings in its class nest, to be
     * erroneous.
     */
    public final void setNestError() {
        this.nestError = true;
        if (outerClass != null) {
            // If we have an outer class, set it to be erroneous as well.
            // This will work in conjunction with getError(), which checks
            // the error value of its outer class, to set the whole class
            // nest to be erroneous.  This address bug 4111488: either
            // code should be generated for all classes in a nest, or
            // none of them.
            outerClass.setNestError();
        }
    }

    /**
     * Get the position in the input
     */
    public final long getWhere() {
        return where;
    }

    /**
     * Get the class declaration
     */
    public final ClassDeclaration getClassDeclaration() {
        return declaration;
    }

    /**
     * Get the class' modifiers
     */
    public final int getModifiers() {
        return modifiers;
    }
    public final void subModifiers(int mod) {
        modifiers &= ~mod;
    }
    public final void addModifiers(int mod) {
        modifiers |= mod;
    }

    // *** DEBUG ***
    protected boolean supersCheckStarted = !(this instanceof sun.tools.javac.SourceClass);

    /**
     * Get the class' super class
     */
    public final ClassDeclaration getSuperClass() {
        /*---
        if (superClass == null && superClassId != null)
            throw new CompilerError("getSuperClass "+superClassId);
        // There are obscure cases where null is the right answer,
        // in order to enable some error reporting later on.
        // For example:  class T extends T.N { class N { } }
        ---*/

        // *** DEBUG ***
        // This method should not be called if the superclass has not been resolved.
        if (!supersCheckStarted) throw new CompilerError("unresolved super");

        return superClass;
    }

    /**
     * Get the super class, and resolve names now if necessary.
     *
     * It is only possible to resolve names at this point if we are
     * a source class.  The provision of this method at this level
     * in the class hierarchy is dubious, but see 'getInnerClass' below.
     * All other calls to 'getSuperClass(env)' appear in 'SourceClass'.
     * NOTE: An older definition of this method has been moved to
     * 'SourceClass', where it overrides this one.
     *
     * @see #resolveTypeStructure
     */

    public ClassDeclaration getSuperClass(Environment env) {
        return getSuperClass();
    }

    /**
     * Get the class' interfaces
     */
    public final ClassDeclaration getInterfaces()[] {
        if (interfaces == null)  throw new CompilerError("getInterfaces");
        return interfaces;
    }

    /**
     * Get the class' enclosing class (or null if not inner)
     */
    public final ClassDefinition getOuterClass() {
        return outerClass;
    }

    /**
     * Set the class' enclosing class.  Must be done at most once.
     */
    protected final void setOuterClass(ClassDefinition outerClass) {
        if (this.outerClass != null)  throw new CompilerError("setOuterClass");
        this.outerClass = outerClass;
    }

    /**
     * Set the class' enclosing current instance pointer.
     * Must be done at most once.
     */
    protected final void setOuterMember(MemberDefinition outerMember) {

        if (isStatic() || !isInnerClass())  throw new CompilerError("setOuterField");
        if (this.outerMember != null)  throw new CompilerError("setOuterField");
        this.outerMember = outerMember;
    }

    /**
     * Tell if the class is inner.
     * This predicate also returns true for top-level nested types.
     * To test for a true inner class as seen by the programmer,
     * use <tt>!isTopLevel()</tt>.
     */
    public final boolean isInnerClass() {
        return outerClass != null;
    }

    /**
     * Tell if the class is a member of another class.
     * This is false for package members and for block-local classes.
     */
    public final boolean isMember() {
        return outerClass != null && !isLocal();
    }

    /**
     * Tell if the class is "top-level", which is either a package member,
     * or a static member of another top-level class.
     */
    public final boolean isTopLevel() {
        return outerClass == null || isStatic() || isInterface();
    }

    /**
     * Tell if the class is local or inside a local class,
     * which means it cannot be mentioned outside of its file.
     */

    // The comment above is true only because M_LOCAL is set
    // whenever M_ANONYMOUS is.  I think it is risky to assume that
    // isAnonymous(x) => isLocal(x).

    public final boolean isInsideLocal() {
        return isLocal() ||
            (outerClass != null && outerClass.isInsideLocal());
    }

    /**
     * Tell if the class is local or anonymous class, or inside
     * such a class, which means it cannot be mentioned outside of
     * its file.
     */
    public final boolean isInsideLocalOrAnonymous() {
        return isLocal() || isAnonymous () ||
            (outerClass != null && outerClass.isInsideLocalOrAnonymous());
    }

    /**
     * Return a simple identifier for this class (idNull if anonymous).
     */
    public Identifier getLocalName() {
        if (localName != null) {
            return localName;
        }
        // This is also the name of the innerClassMember, if any:
        return getName().getFlatName().getName();
    }

    /**
     * Set the local name of a class.  Must be a local class.
     */
    public void setLocalName(Identifier name) {
        if (isLocal()) {
            localName = name;
        }
    }

    /**
     * If inner, get the field for this class in the enclosing class
     */
    public final MemberDefinition getInnerClassMember() {
        if (outerClass == null)
            return null;
        if (innerClassMember == null) {
            // We must find the field in the outer class.
            Identifier nm = getName().getFlatName().getName();
            for (MemberDefinition field = outerClass.getFirstMatch(nm);
                 field != null; field = field.getNextMatch()) {
                if (field.isInnerClass()) {
                    innerClassMember = field;
                    break;
                }
            }
            if (innerClassMember == null)
                throw new CompilerError("getInnerClassField");
        }
        return innerClassMember;
    }

    /**
     * If inner, return an innermost uplevel self pointer, if any exists.
     * Otherwise, return null.
     */
    public final MemberDefinition findOuterMember() {
        return outerMember;
    }

    /**
     * See if this is a (nested) static class.
     */
    public final boolean isStatic() {
        return (modifiers & ACC_STATIC) != 0;
    }

    /**
     * Get the class' top-level enclosing class
     */
    public final ClassDefinition getTopClass() {
        ClassDefinition p, q;
        for (p = this; (q = p.outerClass) != null; p = q)
            ;
        return p;
    }

    /**
     * Get the class' first field or first match
     */
    public final MemberDefinition getFirstMember() {
        return firstMember;
    }
    public final MemberDefinition getFirstMatch(Identifier name) {
        return fieldHash.get(name);
    }

    /**
     * Get the class' name
     */
    public final Identifier getName() {
        return declaration.getName();
    }

    /**
     * Get the class' type
     */
    public final Type getType() {
        return declaration.getType();
    }

    /**
     * Get the class' documentation
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Return true if the given documentation string contains a deprecation
     * paragraph.  This is true if the string contains the tag @deprecated
     * is the first word in a line.
     */
    public static boolean containsDeprecated(String documentation) {
        if (documentation == null) {
            return false;
        }
    doScan:
        for (int scan = 0;
             (scan = documentation.indexOf(paraDeprecated, scan)) >= 0;
             scan += paraDeprecated.length()) {
            // make sure there is only whitespace between this word
            // and the beginning of the line
            for (int beg = scan-1; beg >= 0; beg--) {
                char ch = documentation.charAt(beg);
                if (ch == '\n' || ch == '\r') {
                    break;      // OK
                }
                if (!Character.isSpace(ch)) {
                    continue doScan;
                }
            }
            // make sure the char after the word is space or end of line
            int end = scan+paraDeprecated.length();
            if (end < documentation.length()) {
                char ch = documentation.charAt(end);
                if (!(ch == '\n' || ch == '\r') && !Character.isSpace(ch)) {
                    continue doScan;
                }
            }
            return true;
        }
        return false;
    }

    public final boolean inSamePackage(ClassDeclaration c) {
        // find out if the class stored in c is defined in the same
        // package as the current class.
        return inSamePackage(c.getName().getQualifier());
    }

    public final boolean inSamePackage(ClassDefinition c) {
        // find out if the class stored in c is defined in the same
        // package as the current class.
        return inSamePackage(c.getName().getQualifier());
    }

    public final boolean inSamePackage(Identifier packageName) {
        return (getName().getQualifier().equals(packageName));
    }

    /**
     * Checks
     */
    public final boolean isInterface() {
        return (getModifiers() & M_INTERFACE) != 0;
    }
    public final boolean isClass() {
        return (getModifiers() & M_INTERFACE) == 0;
    }
    public final boolean isPublic() {
        return (getModifiers() & M_PUBLIC) != 0;
    }
    public final boolean isPrivate() {
        return (getModifiers() & M_PRIVATE) != 0;
    }
    public final boolean isProtected() {
        return (getModifiers() & M_PROTECTED) != 0;
    }
    public final boolean isPackagePrivate() {
        return (modifiers & (M_PUBLIC | M_PRIVATE | M_PROTECTED)) == 0;
    }
    public final boolean isFinal() {
        return (getModifiers() & M_FINAL) != 0;
    }
    public final boolean isAbstract() {
        return (getModifiers() & M_ABSTRACT) != 0;
    }
    public final boolean isSynthetic() {
        return (getModifiers() & M_SYNTHETIC) != 0;
    }
    public final boolean isDeprecated() {
        return (getModifiers() & M_DEPRECATED) != 0;
    }
    public final boolean isAnonymous() {
        return (getModifiers() & M_ANONYMOUS) != 0;
    }
    public final boolean isLocal() {
        return (getModifiers() & M_LOCAL) != 0;
    }
    public final boolean hasConstructor() {
        return getFirstMatch(idInit) != null;
    }


    /**
     * Check to see if a class must be abstract.  This method replaces
     * isAbstract(env)
     */
    public final boolean mustBeAbstract(Environment env) {
        // If it is declared abstract, return true.
        // (Fix for 4110534.)
        if (isAbstract()) {
            return true;
        }

        // Check to see if the class should have been declared to be
        // abstract.

        // We make sure that the inherited method collection has been
        // performed.
        collectInheritedMethods(env);

        // We check for any abstract methods inherited or declared
        // by this class.
        Iterator<MemberDefinition> methods = getMethods();
        while (methods.hasNext()) {
            MemberDefinition method = methods.next();

            if (method.isAbstract()) {
                return true;
            }
        }

        // We check for hidden "permanently abstract" methods in
        // our superclasses.
        return getPermanentlyAbstractMethods().hasNext();
    }

    /**
     * Check if this is a super class of another class
     */
    public boolean superClassOf(Environment env, ClassDeclaration otherClass)
                                                                throws ClassNotFound {
        while (otherClass != null) {
            if (getClassDeclaration().equals(otherClass)) {
                return true;
            }
            otherClass = otherClass.getClassDefinition(env).getSuperClass();
        }
        return false;
    }

    /**
     * Check if this is an enclosing class of another class
     */
    public boolean enclosingClassOf(ClassDefinition otherClass) {
        while ((otherClass = otherClass.getOuterClass()) != null) {
            if (this == otherClass) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this is a sub class of another class
     */
    public boolean subClassOf(Environment env, ClassDeclaration otherClass) throws ClassNotFound {
        ClassDeclaration c = getClassDeclaration();
        while (c != null) {
            if (c.equals(otherClass)) {
                return true;
            }
            c = c.getClassDefinition(env).getSuperClass();
        }
        return false;
    }

    /**
     * Check if this class is implemented by another class
     */
    public boolean implementedBy(Environment env, ClassDeclaration c) throws ClassNotFound {
        for (; c != null ; c = c.getClassDefinition(env).getSuperClass()) {
            if (getClassDeclaration().equals(c)) {
                return true;
            }
            ClassDeclaration intf[] = c.getClassDefinition(env).getInterfaces();
            for (int i = 0 ; i < intf.length ; i++) {
                if (implementedBy(env, intf[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check to see if a class which implements interface `this' could
     * possibly implement the interface `intDef'.  Note that the only
     * way that this can fail is if `this' and `intDef' have methods
     * which are of the same signature and different return types.  This
     * method is used by Environment.explicitCast() to determine if a
     * cast between two interfaces is legal.
     *
     * This method should only be called on a class after it has been
     * basicCheck()'ed.
     */
    public boolean couldImplement(ClassDefinition intDef) {
        // Check to see if we could have done the necessary checks.
        if (!doInheritanceChecks) {
            throw new CompilerError("couldImplement: no checks");
        }

        // This method should only be called for interfaces.
        if (!isInterface() || !intDef.isInterface()) {
            throw new CompilerError("couldImplement: not interface");
        }

        // Make sure we are not called before we have collected our
        // inheritance information.
        if (allMethods == null) {
            throw new CompilerError("couldImplement: called early");
        }

        // Get the other classes' methods.  getMethods() in
        // general can return methods which are not visible to the
        // current package.  We need to make sure that these do not
        // prevent this class from being implemented.
        Iterator<MemberDefinition> otherMethods = intDef.getMethods();

        while (otherMethods.hasNext()) {
            // Get one of the methods from intDef...
            MemberDefinition method = otherMethods.next();

            Identifier name = method.getName();
            Type type = method.getType();

            // See if we implement a method of the same signature...
            MemberDefinition myMethod = allMethods.lookupSig(name, type);

            //System.out.println("Comparing\n\t" + myMethod +
            //                   "\nand\n\t" + method);

            if (myMethod != null) {
                // We do.  Make sure the methods have the same return type.
                if (!myMethod.sameReturnType(method)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Check if another class can be accessed from the 'extends' or 'implements'
     * clause of this class.
     */
    public boolean extendsCanAccess(Environment env, ClassDeclaration c) throws ClassNotFound {

        // Names in the 'extends' or 'implements' clause of an inner class
        // are checked as if they appeared in the body of the surrounding class.
        if (outerClass != null) {
            return outerClass.canAccess(env, c);
        }

        // We are a package member.

        ClassDefinition cdef = c.getClassDefinition(env);

        if (cdef.isLocal()) {
            // No locals should be in scope in the 'extends' or
            // 'implements' clause of a package member.
            throw new CompilerError("top local");
        }

        if (cdef.isInnerClass()) {
            MemberDefinition f = cdef.getInnerClassMember();

            // Access to public member is always allowed.
            if (f.isPublic()) {
                return true;
            }

            // Private access is ok only from the same class nest.  This can
            // happen only if the class represented by 'this' encloses the inner
            // class represented by 'f'.
            if (f.isPrivate()) {
                return getClassDeclaration().equals(f.getTopClass().getClassDeclaration());
            }

            // Protected or default access -- allow access if in same package.
            return getName().getQualifier().equals(f.getClassDeclaration().getName().getQualifier());
        }

        // Access to public member is always allowed.
        if (cdef.isPublic()) {
            return true;
        }

        // Default access -- allow access if in same package.
        return getName().getQualifier().equals(c.getName().getQualifier());
    }

    /**
     * Check if another class can be accessed from within the body of this class.
     */
    public boolean canAccess(Environment env, ClassDeclaration c) throws ClassNotFound {
        ClassDefinition cdef = c.getClassDefinition(env);

        if (cdef.isLocal()) {
            // if it's in scope, it's accessible
            return true;
        }

        if (cdef.isInnerClass()) {
            return canAccess(env, cdef.getInnerClassMember());
        }

        // Public access is always ok
        if (cdef.isPublic()) {
            return true;
        }

        // It must be in the same package
        return getName().getQualifier().equals(c.getName().getQualifier());
    }

    /**
     * Check if a field can be accessed from a class
     */

    public boolean canAccess(Environment env, MemberDefinition f)
                throws ClassNotFound {

        // Public access is always ok
        if (f.isPublic()) {
            return true;
        }
        // Protected access is ok from a subclass
        if (f.isProtected() && subClassOf(env, f.getClassDeclaration())) {
            return true;
        }
        // Private access is ok only from the same class nest
        if (f.isPrivate()) {
            return getTopClass().getClassDeclaration()
                .equals(f.getTopClass().getClassDeclaration());
        }
        // It must be in the same package
        return getName().getQualifier().equals(f.getClassDeclaration().getName().getQualifier());
    }

    /**
     * Check if a class is entitled to inline access to a class from
     * another class.
     */
    public boolean permitInlinedAccess(Environment env, ClassDeclaration c)
                       throws ClassNotFound {

        return (env.opt() && c.equals(declaration)) ||
               (env.opt_interclass() && canAccess(env, c));
    }

    /**
     * Check if a class is entitled to inline access to a method from
     * another class.
     */
    public boolean permitInlinedAccess(Environment env, MemberDefinition f)
                       throws ClassNotFound {
        return (env.opt()
                    && (f.clazz.getClassDeclaration().equals(declaration))) ||
               (env.opt_interclass() && canAccess(env, f));
    }

    /**
     * We know the field is marked protected (and not public) and that
     * the field is visible (as per canAccess).  Can we access the field as
     * <accessor>.<field>, where <accessor> has the type <accessorType>?
     *
     * Protected fields can only be accessed when the accessorType is a
     * subclass of the current class
     */
    public boolean protectedAccess(Environment env, MemberDefinition f,
                                   Type accessorType)
        throws ClassNotFound
    {

        return
               // static protected fields are accessible
               f.isStatic()
            || // allow array.clone()
               (accessorType.isType(TC_ARRAY) && (f.getName() == idClone)
                 && (f.getType().getArgumentTypes().length == 0))
            || // <accessorType> is a subtype of the current class
               (accessorType.isType(TC_CLASS)
                 && env.getClassDefinition(accessorType.getClassName())
                         .subClassOf(env, getClassDeclaration()))
            || // we are accessing the field from a friendly class (same package)
               (getName().getQualifier()
                   .equals(f.getClassDeclaration().getName().getQualifier()));
    }


    /**
     * Find or create an access method for a private member,
     * or return null if this is not possible.
     */
    public MemberDefinition getAccessMember(Environment env, Context ctx,
                                          MemberDefinition field, boolean isSuper) {
        throw new CompilerError("binary getAccessMember");
    }

    /**
     * Find or create an update method for a private member,
     * or return null if this is not possible.
     */
    public MemberDefinition getUpdateMember(Environment env, Context ctx,
                                            MemberDefinition field, boolean isSuper) {
        throw new CompilerError("binary getUpdateMember");
    }

    /**
     * Get a field from this class.  Report ambiguous fields.
     * If no accessible field is found, this method may return an
     * inaccessible field to allow a useful error message.
     *
     * getVariable now takes the source class `source' as an argument.
     * This allows getVariable to check whether a field is inaccessible
     * before it signals that a field is ambiguous.  The compiler used to
     * signal an ambiguity even when one of the fields involved was not
     * accessible.  (bug 4053724)
     */
    public MemberDefinition getVariable(Environment env,
                                        Identifier nm,
                                        ClassDefinition source)
        throws AmbiguousMember, ClassNotFound {

        return getVariable0(env, nm, source, true, true);
    }

    /*
     * private fields are never inherited.  package-private fields are
     * not inherited across package boundaries.  To capture this, we
     * take two booleans as parameters: showPrivate indicates whether
     * we have passed a class boundary, and showPackage indicates whether
     * we have crossed a package boundary.
     */
    private MemberDefinition getVariable0(Environment env,
                                          Identifier nm,
                                          ClassDefinition source,
                                          boolean showPrivate,
                                          boolean showPackage)
        throws AmbiguousMember, ClassNotFound {

        // Check to see if this field is defined in the current class
        for (MemberDefinition member = getFirstMatch(nm);
             member != null;
             member = member.getNextMatch()) {
            if (member.isVariable()) {
                if ((showPrivate || !member.isPrivate()) &&
                    (showPackage || !member.isPackagePrivate())) {
                    // It is defined in this class.
                    return member;
                } else {
                    // Even though this definition is not inherited,
                    // it hides all definitions in supertypes.
                    return null;
                }
            }
        }

        // Find the field in our superclass.
        ClassDeclaration sup = getSuperClass();
        MemberDefinition field = null;
        if (sup != null) {
            field =
                sup.getClassDefinition(env)
                  .getVariable0(env, nm, source,
                                false,
                                showPackage && inSamePackage(sup));
        }

        // Find the field in our superinterfaces.
        for (int i = 0 ; i < interfaces.length ; i++) {
            // Try to look up the field in an interface.  Since interfaces
            // only have public fields, the values of the two boolean
            // arguments are not important.
            MemberDefinition field2 =
                interfaces[i].getClassDefinition(env)
                  .getVariable0(env, nm, source, true, true);

            if (field2 != null) {
                // If we have two different, accessible fields, then
                // we've found an ambiguity.
                if (field != null &&
                    source.canAccess(env, field) &&
                    field2 != field) {

                    throw new AmbiguousMember(field2, field);
                }
                field = field2;
            }
        }
        return field;
    }

    /**
     * Tells whether to report a deprecation error for this class.
     */
    public boolean reportDeprecated(Environment env) {
        return (isDeprecated()
                || (outerClass != null && outerClass.reportDeprecated(env)));
    }

    /**
     * Note that this class is being used somehow by <tt>ref</tt>.
     * Report deprecation errors, etc.
     */
    public void noteUsedBy(ClassDefinition ref, long where, Environment env) {
        // (Have this deal with canAccess() checks, too?)
        if (reportDeprecated(env)) {
            env.error(where, "warn.class.is.deprecated", this);
        }
    }

   /**
     * Get an inner class.
     * Look in supers but not outers.
     * (This is used directly to resolve expressions like "site.K", and
     * inside a loop to resolve lone names like "K" or the "K" in "K.L".)
     *
     * Called from 'Context' and 'FieldExpression' as well as this class.
     *
     * @see FieldExpression.checkCommon
     * @see resolveName
     */
    public MemberDefinition getInnerClass(Environment env, Identifier nm)
                                                        throws ClassNotFound {
        // Note:  AmbiguousClass will not be thrown unless and until
        // inner classes can be defined inside interfaces.

        // Check if it is defined in the current class
        for (MemberDefinition field = getFirstMatch(nm);
                field != null ; field = field.getNextMatch()) {
            if (field.isInnerClass()) {
                if (field.getInnerClass().isLocal()) {
                    continue;   // ignore this name; it is internally generated
                }
                return field;
            }
        }

        // Get it from the super class
        // It is likely that 'getSuperClass()' could be made to work here
        // but we would have to assure somehow that 'resolveTypeStructure'
        // has been called on the current class nest.  Since we can get
        // here from 'resolveName', which is called from 'resolveSupers',
        // it is possible that the first attempt to resolve the superclass
        // will originate here, instead of in the call to 'getSuperClass'
        // in 'checkSupers'.  See 'resolveTypeStructure', in which a call
        // to 'resolveSupers' precedes the call to 'checkSupers'.  Why is
        // name resolution done twice, first in 'resolveName'?
        // NOTE: 'SourceMember.resolveTypeStructure' may initiate type
        // structure resolution for an inner class.  Normally, this
        // occurs during the resolution of the outer class, but fields
        // added after the resolution of their containing class will
        // be resolved late -- see 'addMember(env,field)' below.
        // This should only happen for synthetic members, which should
        // never be an inner class.
        ClassDeclaration sup = getSuperClass(env);
        if (sup != null)
            return sup.getClassDefinition(env).getInnerClass(env, nm);

        return null;
    }

    /**
     * Lookup a method.  This code implements the method lookup
     * mechanism specified in JLS 15.11.2.
     *
     * This mechanism cannot be used to lookup synthetic methods.
     */
    private MemberDefinition matchMethod(Environment env,
                                         ClassDefinition accessor,
                                         Identifier methodName,
                                         Type[] argumentTypes,
                                         boolean isAnonConstCall,
                                         Identifier accessPackage)
        throws AmbiguousMember, ClassNotFound {

        if (allMethods == null || !allMethods.isFrozen()) {
            // This may be too restrictive.
            throw new CompilerError("matchMethod called early");
            // collectInheritedMethods(env);
        }

        // A tentative maximally specific method.
        MemberDefinition tentative = null;

        // A list of other methods which may be maximally specific too.
        List<MemberDefinition> candidateList = null;

        // Get all the methods inherited by this class which
        // have the name `methodName'.
        Iterator<MemberDefinition> methods = allMethods.lookupName(methodName);

        while (methods.hasNext()) {
            MemberDefinition method = methods.next();

            // See if this method is applicable.
            if (!env.isApplicable(method, argumentTypes)) {
                continue;
            }

            // See if this method is accessible.
            if (accessor != null) {
                if (!accessor.canAccess(env, method)) {
                    continue;
                }
            } else if (isAnonConstCall) {
                if (method.isPrivate() ||
                    (method.isPackagePrivate() &&
                     accessPackage != null &&
                     !inSamePackage(accessPackage))) {
                    // For anonymous constructor accesses, we
                    // haven't yet built an accessing class.
                    // We disallow anonymous classes from seeing
                    // private/package-private inaccessible
                    // constructors in their superclass.
                    continue;
                }
            } else {
                // If accessor is null, we assume that the access
                // is allowed.  Query: is this option used?
            }

            if (tentative == null) {
                // `method' becomes our tentative maximally specific match.
                tentative = method;
            } else {
                if (env.isMoreSpecific(method, tentative)) {
                    // We have found a method which is a strictly better
                    // match than `tentative'.  Replace it.
                    tentative = method;
                } else {
                    // If this method could possibly be another
                    // maximally specific method, add it to our
                    // list of other candidates.
                    if (!env.isMoreSpecific(tentative,method)) {
                        if (candidateList == null) {
                            candidateList = new ArrayList<>();
                        }
                        candidateList.add(method);
                    }
                }
            }
        }

        if (tentative != null && candidateList != null) {
            // Find out if our `tentative' match is a uniquely
            // maximally specific.
            Iterator<MemberDefinition> candidates = candidateList.iterator();
            while (candidates.hasNext()) {
                MemberDefinition method = candidates.next();
                if (!env.isMoreSpecific(tentative, method)) {
                    throw new AmbiguousMember(tentative, method);
                }
            }
        }

        return tentative;
    }

    /**
     * Lookup a method.  This code implements the method lookup
     * mechanism specified in JLS 15.11.2.
     *
     * This mechanism cannot be used to lookup synthetic methods.
     */
    public MemberDefinition matchMethod(Environment env,
                                        ClassDefinition accessor,
                                        Identifier methodName,
                                        Type[] argumentTypes)
        throws AmbiguousMember, ClassNotFound {

        return matchMethod(env, accessor, methodName,
                           argumentTypes, false, null);
    }

    /**
     * Lookup a method.  This code implements the method lookup
     * mechanism specified in JLS 15.11.2.
     *
     * This mechanism cannot be used to lookup synthetic methods.
     */
    public MemberDefinition matchMethod(Environment env,
                                        ClassDefinition accessor,
                                        Identifier methodName)
        throws AmbiguousMember, ClassNotFound {

        return matchMethod(env, accessor, methodName,
                           Type.noArgs, false, null);
    }

    /**
     * A version of matchMethod to be used only for constructors
     * when we cannot pass in a sourceClass argument.  We just assert
     * our package name.
     *
     * This is used only for anonymous classes, where we have to look up
     * a (potentially) protected constructor with no valid sourceClass
     * parameter available.
     */
    public MemberDefinition matchAnonConstructor(Environment env,
                                                 Identifier accessPackage,
                                                 Type argumentTypes[])
        throws AmbiguousMember, ClassNotFound {

        return matchMethod(env, null, idInit, argumentTypes,
                           true, accessPackage);
    }

    /**
     * Find a method, ie: exact match in this class or any of the super
     * classes.
     *
     * Only called by javadoc.  For now I am holding off rewriting this
     * code to rely on collectInheritedMethods(), as that code has
     * not gotten along with javadoc in the past.
     */
    public MemberDefinition findMethod(Environment env, Identifier nm, Type t)
    throws ClassNotFound {
        // look in the current class
        MemberDefinition f;
        for (f = getFirstMatch(nm) ; f != null ; f = f.getNextMatch()) {
            // Note that non-method types return false for equalArguments().
            if (f.getType().equalArguments(t)) {
                return f;
            }
        }

        // constructors are not inherited
        if (nm.equals(idInit)) {
            return null;
        }

        // look in the super class
        ClassDeclaration sup = getSuperClass();
        if (sup == null)
            return null;

        return sup.getClassDefinition(env).findMethod(env, nm, t);
    }

    // We create a stub for this.  Source classes do more work.
    protected void basicCheck(Environment env) throws ClassNotFound {
        // Do the outer class first.
        if (outerClass != null)
            outerClass.basicCheck(env);
    }

    /**
     * Check this class.
     */
    public void check(Environment env) throws ClassNotFound {
    }

    public Vset checkLocalClass(Environment env, Context ctx,
                                Vset vset, ClassDefinition sup,
                                Expression args[], Type argTypes[]
                                ) throws ClassNotFound {
        throw new CompilerError("checkLocalClass");
    }

    //---------------------------------------------------------------
    // The non-synthetic methods defined in this class or in any
    // of its parents (class or interface).  This member is used
    // to cache work done in collectInheritedMethods for use by
    // getMethods() and matchMethod().  It should be accessed by
    // no other method without forethought.
    MethodSet allMethods = null;

    // One of our superclasses may contain an abstract method which
    // we are unable to ever implement.  This happens when there is
    // a package-private abstract method in our parent and we are in
    // a different package than our parent.  In these cases, we
    // keep a list of the "permanently abstract" or "unimplementable"
    // methods so that we can correctly detect that this class is
    // indeed abstract and so that we can give somewhat comprehensible
    // error messages.
    private List<MemberDefinition> permanentlyAbstractMethods = new ArrayList<>();

    /**
     * This method returns an Iterator of all abstract methods
     * in our superclasses which we are unable to implement.
     */
    protected Iterator<MemberDefinition> getPermanentlyAbstractMethods() {
        // This method can only be called after collectInheritedMethods.
        if (allMethods == null) {
            throw new CompilerError("isPermanentlyAbstract() called early");
        }

        return permanentlyAbstractMethods.iterator();
    }

    /**
     * A flag used by turnOffInheritanceChecks() to indicate if
     * inheritance checks are on or off.
     */
    protected static boolean doInheritanceChecks = true;

    /**
     * This is a workaround to allow javadoc to turn off certain
     * inheritance/override checks which interfere with javadoc
     * badly.  In the future it might be good to eliminate the
     * shared sources of javadoc and javac to avoid the need for this
     * sort of workaround.
     */
    public static void turnOffInheritanceChecks() {
        doInheritanceChecks = false;
    }

    /**
     * Add all of the methods declared in or above `parent' to
     * `allMethods', the set of methods in the current class.
     * `myMethods' is the set of all methods declared in this
     * class, and `mirandaMethods' is a repository for Miranda methods.
     * If mirandaMethods is null, no mirandaMethods will be
     * generated.
     *
     * For a definition of Miranda methods, see the comment above the
     * method addMirandaMethods() which occurs later in this file.
     */
    private void collectOneClass(Environment env,
                                 ClassDeclaration parent,
                                 MethodSet myMethods,
                                 MethodSet allMethods,
                                 MethodSet mirandaMethods) {

        // System.out.println("Inheriting methods from " + parent);

        try {
            ClassDefinition pClass = parent.getClassDefinition(env);
            Iterator<MemberDefinition> methods = pClass.getMethods(env);
            while (methods.hasNext()) {
                MemberDefinition method =
                    methods.next();

                // Private methods are not inherited.
                //
                // Constructors are not inherited.
                //
                // Any non-abstract methods in an interface come
                // from java.lang.Object.  This means that they
                // should have already been added to allMethods
                // when we walked our superclass lineage.
                if (method.isPrivate() ||
                    method.isConstructor() ||
                    (pClass.isInterface() && !method.isAbstract())) {

                    continue;
                }

                // Get the components of the methods' signature.
                Identifier name = method.getName();
                Type type = method.getType();

                // Check for a method of the same signature which
                // was locally declared.
                MemberDefinition override =
                    myMethods.lookupSig(name, type);

                // Is this method inaccessible due to package-private
                // visibility?
                if (method.isPackagePrivate() &&
                    !inSamePackage(method.getClassDeclaration())) {

                    if (override != null && this instanceof
                        sun.tools.javac.SourceClass) {
                        // We give a warning when a class shadows an
                        // inaccessible package-private method from
                        // its superclass.  This warning is meant
                        // to prevent people from relying on overriding
                        // when it does not happen.  This warning should
                        // probably be removed to be consistent with the
                        // general "no warnings" policy of this
                        // compiler.
                        //
                        // The `instanceof' above is a hack so that only
                        // SourceClass generates this warning, not a
                        // BinaryClass, for example.
                        env.error(method.getWhere(),
                                  "warn.no.override.access",
                                  override,
                                  override.getClassDeclaration(),
                                  method.getClassDeclaration());
                    }

                    // If our superclass has a package-private abstract
                    // method that we have no access to, then we add
                    // this method to our list of permanently abstract
                    // methods.  The idea is, since we cannot override
                    // the method, we can never make this class
                    // non-abstract.
                    if (method.isAbstract()) {
                        permanentlyAbstractMethods.add(method);
                    }

                    // `method' is inaccessible.  We do not inherit it.
                    continue;
                }

                if (override != null) {
                    // `method' and `override' have the same signature.
                    // We are required to check that `override' is a
                    // legal override of `method'

                    //System.out.println ("About to check override of " +
                    //              method);

                    override.checkOverride(env, method);
                } else {
                    // In the absence of a definition in the class
                    // itself, we check to see if this definition
                    // can be successfully merged with any other
                    // inherited definitions.

                    // Have we added a member of the same signature
                    // to `allMethods' already?
                    MemberDefinition formerMethod =
                        allMethods.lookupSig(name, type);

                    // If the previous definition is nonexistent or
                    // ignorable, replace it.
                    if (formerMethod == null) {
                        //System.out.println("Added " + method + " to " +
                        //             this);

                        if (mirandaMethods != null &&
                            pClass.isInterface() && !isInterface()) {
                            // Whenever a class inherits a method
                            // from an interface, that method is
                            // one of our "miranda" methods.  Early
                            // VMs require that these methods be
                            // added as true members to the class
                            // to enable method lookup to work in the
                            // VM.
                            method =
                                new sun.tools.javac.SourceMember(method,this,
                                                                 env);
                            mirandaMethods.add(method);

                            //System.out.println("Added " + method +
                            // " to " + this + " as a Miranda");
                        }

                        // There is no previous inherited definition.
                        // Add `method' to `allMethods'.
                        allMethods.add(method);
                    } else if (isInterface() &&
                               !formerMethod.isAbstract() &&
                               method.isAbstract()) {
                        // If we are in an interface and we have inherited
                        // both an abstract method and a non-abstract method
                        // then we know that the non-abstract method is
                        // a placeholder from Object put in for type checking
                        // and the abstract method was already checked to
                        // be proper by our superinterface.
                        allMethods.replace(method);

                    } else {
                        // Okay, `formerMethod' and `method' both have the
                        // same signature.  See if they are compatible.

                        //System.out.println ("About to check meet of " +
                        //              method);

                        if (!formerMethod.checkMeet(env,
                                           method,
                                           this.getClassDeclaration())) {
                                // The methods are incompatible.  Skip to
                                // next method.
                            continue;
                        }

                        if (formerMethod.couldOverride(env, method)) {
                                // Do nothing.  The current definition
                                // is specific enough.

                                //System.out.println("trivial meet of " +
                                //                 method);
                            continue;
                        }

                        if (method.couldOverride(env, formerMethod)) {
                                // `method' is more specific than
                                // `formerMethod'.  replace `formerMethod'.

                                //System.out.println("new def of " + method);
                            if (mirandaMethods != null &&
                                pClass.isInterface() && !isInterface()) {
                                // Whenever a class inherits a method
                                // from an interface, that method is
                                // one of our "miranda" methods.  Early
                                // VMs require that these methods be
                                // added as true members to the class
                                // to enable method lookup to work in the
                                // VM.
                                method =
                                    new sun.tools.javac.SourceMember(method,
                                                                     this,env);

                                mirandaMethods.replace(method);

                                //System.out.println("Added " + method +
                                // " to " + this + " as a Miranda");
                            }

                            allMethods.replace(method);

                            continue;
                        }

                        // Neither method is more specific than the other.
                        // Oh well.  We need to construct a nontrivial
                        // meet of the two methods.
                        //
                        // This is not yet implemented, so we give
                        // a message with a helpful workaround.
                        env.error(this.where,
                                  "nontrivial.meet", method,
                                  formerMethod.getClassDefinition(),
                                  method.getClassDeclaration()
                                  );
                    }
                }
            }
        } catch (ClassNotFound ee) {
            env.error(getWhere(), "class.not.found", ee.name, this);
        }
    }

    /**
     * <p>Collect all methods defined in this class or inherited from
     * any of our superclasses or interfaces.  Look for any
     * incompatible definitions.
     *
     * <p>This function is also responsible for collecting the
     * <em>Miranda</em> methods for a class.  For a definition of
     * Miranda methods, see the comment in addMirandaMethods()
     * below.
     */
    protected void collectInheritedMethods(Environment env) {
        // The methods defined in this class.
        MethodSet myMethods;
        MethodSet mirandaMethods;

        //System.out.println("Called collectInheritedMethods() for " +
        //                 this);

        if (allMethods != null) {
            if (allMethods.isFrozen()) {
                // We have already done the collection.  No need to
                // do it again.
                return;
            } else {
                // We have run into a circular need to collect our methods.
                // This should not happen at this stage.
                throw new CompilerError("collectInheritedMethods()");
            }
        }

        myMethods = new MethodSet();
        allMethods = new MethodSet();

        // For testing, do not generate miranda methods.
        if (env.version12()) {
            mirandaMethods = null;
        } else {
            mirandaMethods = new MethodSet();
        }

        // Any methods defined in the current class get added
        // to both the myMethods and the allMethods MethodSets.

        for (MemberDefinition member = getFirstMember();
             member != null;
             member = member.nextMember) {

            // We only collect methods.  Initializers are not relevant.
            if (member.isMethod() &&
                !member.isInitializer()) {

                //System.out.println("Declared in " + this + ", " + member);

                ////////////////////////////////////////////////////////////
                // PCJ 2003-07-30 modified the following code because with
                // the covariant return type feature of the 1.5 compiler,
                // there might be multiple methods with the same signature
                // but different return types, and MethodSet doesn't
                // support that.  We use a new utility method that attempts
                // to ensure that the appropriate method winds up in the
                // MethodSet.  See 4892308.
                ////////////////////////////////////////////////////////////
                // myMethods.add(member);
                // allMethods.add(member);
                ////////////////////////////////////////////////////////////
                methodSetAdd(env, myMethods, member);
                methodSetAdd(env, allMethods, member);
                ////////////////////////////////////////////////////////////
            }
        }

        // We're ready to start adding inherited methods.  First add
        // the methods from our superclass.

        //System.out.println("About to start superclasses for " + this);

        ClassDeclaration scDecl = getSuperClass(env);
        if (scDecl != null) {
            collectOneClass(env, scDecl,
                            myMethods, allMethods, mirandaMethods);

            // Make sure that we add all unimplementable methods from our
            // superclass to our list of unimplementable methods.
            ClassDefinition sc = scDecl.getClassDefinition();
            Iterator<MemberDefinition> supIter = sc.getPermanentlyAbstractMethods();
            while (supIter.hasNext()) {
                permanentlyAbstractMethods.add(supIter.next());
            }
        }

        // Now we inherit all of the methods from our interfaces.

        //System.out.println("About to start interfaces for " + this);

        for (int i = 0; i < interfaces.length; i++) {
            collectOneClass(env, interfaces[i],
                            myMethods, allMethods, mirandaMethods);
        }
        allMethods.freeze();

        // Now we have collected all of our methods from our superclasses
        // and interfaces into our `allMethods' member.  Good.  As a last
        // task, we add our collected miranda methods to this class.
        //
        // If we do not add the mirandas to the class explicitly, there
        // will be no code generated for them.
        if (mirandaMethods != null && mirandaMethods.size() > 0) {
            addMirandaMethods(env, mirandaMethods.iterator());
        }
    }

    ////////////////////////////////////////////////////////////
    // PCJ 2003-07-30 added this utility method to insulate
    // MethodSet additions from the covariant return type
    // feature of the 1.5 compiler.  When there are multiple
    // methods with the same signature and different return
    // types to be added, we try to ensure that the one with
    // the most specific return type winds up in the MethodSet.
    // This logic was not put into MethodSet itself because it
    // requires access to an Environment for type relationship
    // checking.  No error checking is performed here, but that
    // should be OK because this code is only still used by
    // rmic.  See 4892308.
    ////////////////////////////////////////////////////////////
    private static void methodSetAdd(Environment env,
                                     MethodSet methodSet,
                                     MemberDefinition newMethod)
    {
        MemberDefinition oldMethod = methodSet.lookupSig(newMethod.getName(),
                                                         newMethod.getType());
        if (oldMethod != null) {
            Type oldReturnType = oldMethod.getType().getReturnType();
            Type newReturnType = newMethod.getType().getReturnType();
            try {
                if (env.isMoreSpecific(newReturnType, oldReturnType)) {
                    methodSet.replace(newMethod);
                }
            } catch (ClassNotFound ignore) {
            }
        } else {
            methodSet.add(newMethod);
        }
    }
    ////////////////////////////////////////////////////////////

    /**
     * Get an Iterator of all methods which could be accessed in an
     * instance of this class.
     */
    public Iterator<MemberDefinition> getMethods(Environment env) {
        if (allMethods == null) {
            collectInheritedMethods(env);
        }
        return getMethods();
    }

    /**
     * Get an Iterator of all methods which could be accessed in an
     * instance of this class.  Throw a compiler error if we haven't
     * generated this information yet.
     */
    public Iterator<MemberDefinition> getMethods() {
        if (allMethods == null) {
            throw new CompilerError("getMethods: too early");
        }
        return allMethods.iterator();
    }

    // In early VM's there was a bug -- the VM didn't walk the interfaces
    // of a class looking for a method, they only walked the superclass
    // chain.  This meant that abstract methods defined only in interfaces
    // were not being found.  To fix this bug, a counter-bug was introduced
    // in the compiler -- the so-called Miranda methods.  If a class
    // does not provide a definition for an abstract method in one of
    // its interfaces then the compiler inserts one in the class artificially.
    // That way the VM didn't have to bother looking at the interfaces.
    //
    // This is a problem.  Miranda methods are not part of the specification.
    // But they continue to be inserted so that old VM's can run new code.
    // Someday, when the old VM's are gone, perhaps classes can be compiled
    // without Miranda methods.  Towards this end, the compiler has a
    // flag, -nomiranda, which can turn off the creation of these methods.
    // Eventually that behavior should become the default.
    //
    // Why are they called Miranda methods?  Well the sentence "If the
    // class is not able to provide a method, then one will be provided
    // by the compiler" is very similar to the sentence "If you cannot
    // afford an attorney, one will be provided by the court," -- one
    // of the so-called "Miranda" rights in the United States.

    /**
     * Add a list of methods to this class as miranda methods.  This
     * gets overridden with a meaningful implementation in SourceClass.
     * BinaryClass should not need to do anything -- it should already
     * have its miranda methods and, if it doesn't, then that doesn't
     * affect our compilation.
     */
    protected void addMirandaMethods(Environment env,
                                     Iterator<MemberDefinition> mirandas) {
        // do nothing.
    }

    //---------------------------------------------------------------

    public void inlineLocalClass(Environment env) {
    }

    /**
     * We create a stub for this.  Source classes do more work.
     * Some calls from 'SourceClass.checkSupers' execute this method.
     * @see sun.tools.javac.SourceClass#resolveTypeStructure
     */

    public void resolveTypeStructure(Environment env) {
    }

    /**
     * Look up an inner class name, from somewhere inside this class.
     * Since supers and outers are in scope, search them too.
     * <p>
     * If no inner class is found, env.resolveName() is then called,
     * to interpret the ambient package and import directives.
     * <p>
     * This routine operates on a "best-efforts" basis.  If
     * at some point a class is not found, the partially-resolved
     * identifier is returned.  Eventually, someone else has to
     * try to get the ClassDefinition and diagnose the ClassNotFound.
     * <p>
     * resolveName() looks at surrounding scopes, and hence
     * pulling in both inherited and uplevel types.  By contrast,
     * resolveInnerClass() is intended only for interpreting
     * explicitly qualified names, and so look only at inherited
     * types.  Also, resolveName() looks for package prefixes,
     * which appear similar to "very uplevel" outer classes.
     * <p>
     * A similar (but more complex) name-lookup process happens
     * when field and identifier expressions denoting qualified names
     * are type-checked.  The added complexity comes from the fact
     * that variables may occur in such names, and take precedence
     * over class and package names.
     * <p>
     * In the expression type-checker, resolveInnerClass() is paralleled
     * by code in FieldExpression.checkAmbigName(), which also calls
     * ClassDefinition.getInnerClass() to interpret names of the form
     * "OuterClass.Inner" (and also outerObject.Inner).  The checking
     * of an identifier expression that fails to be a variable is referred
     * directly to resolveName().
     */
    public Identifier resolveName(Environment env, Identifier name) {
        if (tracing) env.dtEvent("ClassDefinition.resolveName: " + name);
        // This logic is pretty much exactly parallel to that of
        // Environment.resolveName().
        if (name.isQualified()) {
            // Try to resolve the first identifier component,
            // because inner class names take precedence over
            // package prefixes.  (Cf. Environment.resolveName.)
            Identifier rhead = resolveName(env, name.getHead());

            if (rhead.hasAmbigPrefix()) {
                // The first identifier component refers to an
                // ambiguous class.  Limp on.  We throw away the
                // rest of the classname as it is irrelevant.
                // (part of solution for 4059855).
                return rhead;
            }

            if (!env.classExists(rhead)) {
                return env.resolvePackageQualifiedName(name);
            }
            try {
                return env.getClassDefinition(rhead).
                    resolveInnerClass(env, name.getTail());
            } catch (ClassNotFound ee) {
                // return partially-resolved name someone else can fail on
                return Identifier.lookupInner(rhead, name.getTail());
            }
        }

        // This method used to fail to look for local classes, thus a
        // reference to a local class within, e.g., the type of a member
        // declaration, would fail to resolve if the immediately enclosing
        // context was an inner class.  The code added below is ugly, but
        // it works, and is lifted from existing code in 'Context.resolveName'
        // and 'Context.getClassCommon'. See the comments there about the design.
        // Fixes 4095716.

        int ls = -2;
        LocalMember lf = null;
        if (classContext != null) {
            lf = classContext.getLocalClass(name);
            if (lf != null) {
                ls = lf.getScopeNumber();
            }
        }

        // Look for an unqualified name in enclosing scopes.
        for (ClassDefinition c = this; c != null; c = c.outerClass) {
            try {
                MemberDefinition f = c.getInnerClass(env, name);
                if (f != null &&
                    (lf == null || classContext.getScopeNumber(c) > ls)) {
                    // An uplevel member was found, and was nested more deeply than
                    // any enclosing local of the same name.
                    return f.getInnerClass().getName();
                }
            } catch (ClassNotFound ee) {
                // a missing superclass, or something catastrophic
            }
        }

        // No uplevel member found, so use the enclosing local if one was found.
        if (lf != null) {
           return lf.getInnerClass().getName();
        }

        // look in imports, etc.
        return env.resolveName(name);
    }

    /**
     * Interpret a qualified class name, which may have further subcomponents..
     * Follow inheritance links, as in:
     *  class C { class N { } }  class D extends C { }  ... new D.N() ...
     * Ignore outer scopes and packages.
     * @see resolveName
     */
    public Identifier resolveInnerClass(Environment env, Identifier nm) {
        if (nm.isInner())  throw new CompilerError("inner");
        if (nm.isQualified()) {
            Identifier rhead = resolveInnerClass(env, nm.getHead());
            try {
                return env.getClassDefinition(rhead).
                    resolveInnerClass(env, nm.getTail());
            } catch (ClassNotFound ee) {
                // return partially-resolved name someone else can fail on
                return Identifier.lookupInner(rhead, nm.getTail());
            }
        } else {
            try {
                MemberDefinition f = getInnerClass(env, nm);
                if (f != null) {
                    return f.getInnerClass().getName();
                }
            } catch (ClassNotFound ee) {
                // a missing superclass, or something catastrophic
            }
            // Fake a good name for a diagnostic.
            return Identifier.lookupInner(this.getName(), nm);
        }
    }

    /**
     * While resolving import directives, the question has arisen:
     * does a given inner class exist?  If the top-level class exists,
     * we ask it about an inner class via this method.
     * This method looks only at the literal name of the class,
     * and does not attempt to follow inheritance links.
     * This is necessary, since at the time imports are being
     * processed, inheritance links have not been resolved yet.
     * (Thus, an import directive must always spell a class
     * name exactly.)
     */
    public boolean innerClassExists(Identifier nm) {
        for (MemberDefinition field = getFirstMatch(nm.getHead()) ; field != null ; field = field.getNextMatch()) {
            if (field.isInnerClass()) {
                if (field.getInnerClass().isLocal()) {
                    continue;   // ignore this name; it is internally generated
                }
                return !nm.isQualified() ||
                    field.getInnerClass().innerClassExists(nm.getTail());
            }
        }
        return false;
    }

   /**
     * Find any method with a given name.
     */
    public MemberDefinition findAnyMethod(Environment env, Identifier nm) throws ClassNotFound {
        MemberDefinition f;
        for (f = getFirstMatch(nm) ; f != null ; f = f.getNextMatch()) {
            if (f.isMethod()) {
                return f;
            }
        }

        // look in the super class
        ClassDeclaration sup = getSuperClass();
        if (sup == null)
            return null;
        return sup.getClassDefinition(env).findAnyMethod(env, nm);
    }

    /**
      * Given the fact that this class has no method "nm" matching "argTypes",
      * find out if the mismatch can be blamed on a particular actual argument
      * which disagrees with all of the overloadings.
      * If so, return the code (i<<2)+(castOK<<1)+ambig, where
      * "i" is the number of the offending argument, and
      * "castOK" is 1 if a cast could fix the problem.
      * The target type for the argument is returned in margTypeResult[0].
      * If not all methods agree on this type, "ambig" is 1.
      * If there is more than one method, the choice of target type is
      * arbitrary.<p>
      * Return -1 if every argument is acceptable to at least one method.
      * Return -2 if there are no methods of the required arity.
      * The value "start" gives the index of the first argument to begin
      * checking.
      */
    public int diagnoseMismatch(Environment env, Identifier nm, Type argTypes[],
                                int start, Type margTypeResult[]) throws ClassNotFound {
        int haveMatch[] = new int[argTypes.length];
        Type margType[] = new Type[argTypes.length];
        if (!diagnoseMismatch(env, nm, argTypes, start, haveMatch, margType))
            return -2;
        for (int i = start; i < argTypes.length; i++) {
            if (haveMatch[i] < 4) {
                margTypeResult[0] = margType[i];
                return (i<<2) | haveMatch[i];
            }
        }
        return -1;
    }

    private boolean diagnoseMismatch(Environment env, Identifier nm, Type argTypes[], int start,
                                     int haveMatch[], Type margType[]) throws ClassNotFound {
        // look in the current class
        boolean haveOne = false;
        MemberDefinition f;
        for (f = getFirstMatch(nm) ; f != null ; f = f.getNextMatch()) {
            if (!f.isMethod()) {
                continue;
            }
            Type fArgTypes[] = f.getType().getArgumentTypes();
            if (fArgTypes.length == argTypes.length) {
                haveOne = true;
                for (int i = start; i < argTypes.length; i++) {
                    Type at = argTypes[i];
                    Type ft = fArgTypes[i];
                    if (env.implicitCast(at, ft)) {
                        haveMatch[i] = 4;
                        continue;
                    } else if (haveMatch[i] <= 2 && env.explicitCast(at, ft)) {
                        if (haveMatch[i] < 2)  margType[i] = null;
                        haveMatch[i] = 2;
                    } else if (haveMatch[i] > 0) {
                        continue;
                    }
                    if (margType[i] == null)
                        margType[i] = ft;
                    else if (margType[i] != ft)
                        haveMatch[i] |= 1;
                }
            }
        }

        // constructors are not inherited
        if (nm.equals(idInit)) {
            return haveOne;
        }

        // look in the super class
        ClassDeclaration sup = getSuperClass();
        if (sup != null) {
            if (sup.getClassDefinition(env).diagnoseMismatch(env, nm, argTypes, start,
                                                             haveMatch, margType))
                haveOne = true;
        }
        return haveOne;
    }

    /**
     * Add a field (no checks)
     */
    public void addMember(MemberDefinition field) {
        //System.out.println("ADD = " + field);
        if (firstMember == null) {
            firstMember = lastMember = field;
        } else if (field.isSynthetic() && field.isFinal()
                                       && field.isVariable()) {
            // insert this at the front, because of initialization order
            field.nextMember = firstMember;
            firstMember = field;
            field.nextMatch = fieldHash.get(field.name);
        } else {
            lastMember.nextMember = field;
            lastMember = field;
            field.nextMatch = fieldHash.get(field.name);
        }
        fieldHash.put(field.name, field);
    }

    /**
     * Add a field (subclasses make checks)
     */
    public void addMember(Environment env, MemberDefinition field) {
        addMember(field);
        if (resolved) {
            // a late addition
            field.resolveTypeStructure(env);
        }
    }

    /**
     * Find or create an uplevel reference for the given target.
     */
    public UplevelReference getReference(LocalMember target) {
        for (UplevelReference r = references; r != null; r = r.getNext()) {
            if (r.getTarget() == target) {
                return r;
            }
        }
        return addReference(target);
    }

    protected UplevelReference addReference(LocalMember target) {
        if (target.getClassDefinition() == this) {
            throw new CompilerError("addReference "+target);
        }
        referencesMustNotBeFrozen();
        UplevelReference r = new UplevelReference(this, target);
        references = r.insertInto(references);
        return r;
    }

    /**
     * Return the list of all uplevel references.
     */
    public UplevelReference getReferences() {
        return references;
    }

    /**
     * Return the same value as getReferences.
     * Also, mark the set of references frozen.
     * After that, it is an error to add new references.
     */
    public UplevelReference getReferencesFrozen() {
        referencesFrozen = true;
        return references;
    }

    /**
     * assertion check
     */
    public final void referencesMustNotBeFrozen() {
        if (referencesFrozen) {
            throw new CompilerError("referencesMustNotBeFrozen "+this);
        }
    }

    /**
     * Get helper method for class literal lookup.
     */
    public MemberDefinition getClassLiteralLookup(long fwhere) {
        throw new CompilerError("binary class");
    }

    /**
     * Add a dependency
     */
    public void addDependency(ClassDeclaration c) {
        throw new CompilerError("addDependency");
    }

    /**
     * Maintain a hash table of local and anonymous classes
     * whose internal names are prefixed by the current class.
     * The key is the simple internal name, less the prefix.
     */

    public ClassDefinition getLocalClass(String name) {
        if (localClasses == null) {
            return null;
        } else {
            return localClasses.get(name);
        }
    }

    public void addLocalClass(ClassDefinition c, String name) {
        if (localClasses == null) {
            localClasses = new Hashtable<>(LOCAL_CLASSES_SIZE);
        }
        localClasses.put(name, c);
    }


    /**
     * Print for debugging
     */
    public void print(PrintStream out) {
        if (isPublic()) {
            out.print("public ");
        }
        if (isInterface()) {
            out.print("interface ");
        } else {
            out.print("class ");
        }
        out.print(getName() + " ");
        if (getSuperClass() != null) {
            out.print("extends " + getSuperClass().getName() + " ");
        }
        if (interfaces.length > 0) {
            out.print("implements ");
            for (int i = 0 ; i < interfaces.length ; i++) {
                if (i > 0) {
                    out.print(", ");
                }
                out.print(interfaces[i].getName());
                out.print(" ");
            }
        }
        out.println("{");

        for (MemberDefinition f = getFirstMember() ; f != null ; f = f.getNextMember()) {
            out.print("    ");
            f.print(out);
        }

        out.println("}");
    }

    /**
     * Convert to String
     */
    public String toString() {
        return getClassDeclaration().toString();
    }

    /**
     * After the class has been written to disk, try to free up
     * some storage.
     */
    public void cleanup(Environment env) {
        if (env.dump()) {
            env.output("[cleanup " + getName() + "]");
        }
        for (MemberDefinition f = getFirstMember() ; f != null ; f = f.getNextMember()) {
            f.cleanup(env);
        }
        // keep "references" around, for the sake of local subclasses
        documentation = null;
    }
}
