/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
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

import sun.tools.tree.Node;
import sun.tools.tree.Vset;
import sun.tools.tree.Expression;
import sun.tools.tree.Statement;
import sun.tools.tree.Context;
import sun.tools.asm.Assembler;
import java.io.PrintStream;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

/**
 * This class defines a member of a Java class:
 * a variable, a method, or an inner class.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class MemberDefinition implements Constants {
    protected long where;
    protected int modifiers;
    protected Type type;
    protected String documentation;
    protected IdentifierToken expIds[];
    protected ClassDeclaration exp[];
    protected Node value;
    protected ClassDefinition clazz;
    protected Identifier name;
    protected ClassDefinition innerClass;
    protected MemberDefinition nextMember;
    protected MemberDefinition nextMatch;
    protected MemberDefinition accessPeer;
    protected boolean superAccessMethod;

    /**
     * Constructor
     */
    public MemberDefinition(long where, ClassDefinition clazz, int modifiers,
                            Type type, Identifier name,
                            IdentifierToken expIds[], Node value) {
        if (expIds == null) {
            expIds = new IdentifierToken[0];
        }
        this.where = where;
        this.clazz = clazz;
        this.modifiers = modifiers;
        this.type = type;
        this.name = name;
        this.expIds = expIds;
        this.value = value;
    }

    /**
     * Constructor for an inner class.
     * Inner classes are represented as fields right along with
     * variables and methods for simplicity of data structure,
     * and to reflect properly the textual declaration order.
     * <p>
     * This constructor calls the generic constructor for this
     * class, extracting all necessary values from the innerClass.
     */
    public MemberDefinition(ClassDefinition innerClass) {
        this(innerClass.getWhere(),
             innerClass.getOuterClass(),
             innerClass.getModifiers(),
             innerClass.getType(),
             innerClass.getName().getFlatName().getName(),
             null, null);
        this.innerClass = innerClass;
    }

    /**
     * A cache of previously created proxy members.  Used to ensure
     * uniqueness of proxy objects.  See the makeProxyMember method
     * defined below.
     */
    static private Map proxyCache;

    /**
     * Create a member which is externally the same as `field' but
     * is defined in class `classDef'.  This is used by code
     * in sun.tools.tree.(MethodExpression,FieldExpression) as
     * part of the fix for bug 4135692.
     *
     * Proxy members should not be added, ala addMember(), to classes.
     * They are merely "stand-ins" to produce modified MethodRef
     * constant pool entries during code generation.
     *
     * We keep a cache of previously created proxy members not to
     * save time or space, but to ensure uniqueness of the proxy
     * member for any (field,classDef) pair.  If these are not made
     * unique then we can end up generating duplicate MethodRef
     * constant pool entries during code generation.
     */
    public static MemberDefinition makeProxyMember(MemberDefinition field,
                                                   ClassDefinition classDef,
                                                   Environment env) {

        if (proxyCache == null) {
            proxyCache = new HashMap();
        }

        String key = field.toString() + "@" + classDef.toString();
        // System.out.println("Key is : " + key);
        MemberDefinition proxy = (MemberDefinition)proxyCache.get(key);

        if (proxy != null)
            return proxy;

        proxy = new MemberDefinition(field.getWhere(), classDef,
                                     field.getModifiers(), field.getType(),
                                     field.getName(), field.getExceptionIds(),
                                     null);
        proxy.exp = field.getExceptions(env);
        proxyCache.put(key, proxy);

        return proxy;
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
        return clazz.getClassDeclaration();
    }

    /**
     * A stub.  Subclasses can do more checking.
     */
    public void resolveTypeStructure(Environment env) {
    }

    /**
     * Get the class declaration in which the field is actually defined
     */
    public ClassDeclaration getDefiningClassDeclaration() {
        return getClassDeclaration();
    }

    /**
     * Get the class definition
     */
    public final ClassDefinition getClassDefinition() {
        return clazz;
    }

    /**
     * Get the field's top-level enclosing class
     */
    public final ClassDefinition getTopClass() {
        return clazz.getTopClass();
    }

    /**
     * Get the field's modifiers
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

    /**
     * Get the field's type
     */
    public final Type getType() {
        return type;
    }

    /**
     * Get the field's name
     */
    public final Identifier getName() {
        return name;
    }

    /**
     * Get arguments (a vector of LocalMember)
     */
    public Vector getArguments() {
        return isMethod() ? new Vector() : null;
    }

    /**
     * Get the exceptions that are thrown by this method.
     */
    public ClassDeclaration[] getExceptions(Environment env) {
        if (expIds != null && exp == null) {
            if (expIds.length == 0)
                exp = new ClassDeclaration[0];
            else
                // we should have translated this already!
                throw new CompilerError("getExceptions "+this);
        }
        return exp;
    }

    public final IdentifierToken[] getExceptionIds() {
        return expIds;
    }

    /**
     * Get an inner class.
     */
    public ClassDefinition getInnerClass() {
        return innerClass;
    }

    /**
     * Is this a synthetic field which holds a copy of,
     * or reference to, a local variable or enclosing instance?
     */
    public boolean isUplevelValue() {
        if (!isSynthetic() || !isVariable() || isStatic()) {
            return false;
        }
        String name = this.name.toString();
        return name.startsWith(prefixVal)
            || name.startsWith(prefixLoc)
            || name.startsWith(prefixThis);
    }

    public boolean isAccessMethod() {
        // This no longer works, because access methods
        // for constructors do not use the standard naming
        // scheme.
        //    return isSynthetic() && isMethod()
        //        && name.toString().startsWith(prefixAccess);
        // Assume that a method is an access method if it has
        // an access peer.  NOTE: An access method will not be
        // recognized as such until 'setAccessMethodTarget' has
        // been called on it.
        return isSynthetic() && isMethod() && (accessPeer != null);
    }

    /**
     * Is this a synthetic method which provides access to a
     * visible private member?
     */
    public MemberDefinition getAccessMethodTarget() {
        if (isAccessMethod()) {
            for (MemberDefinition f = accessPeer; f != null; f = f.accessPeer) {
                // perhaps skip over another access for the same field
                if (!f.isAccessMethod()) {
                    return f;
                }
            }
        }
        return null;
    }


    public void setAccessMethodTarget(MemberDefinition target) {
        if (getAccessMethodTarget() != target) {
            /*-------------------*
            if (!isAccessMethod() || accessPeer != null ||
                    target.accessPeer != null) {
                throw new CompilerError("accessPeer");
            }
            *-------------------*/
            if (accessPeer != null || target.accessPeer != null) {
                throw new CompilerError("accessPeer");
            }
            accessPeer = target;
        }
    }

    /**
     * If this method is a getter for a private field, return the setter.
     */
    public MemberDefinition getAccessUpdateMember() {
        if (isAccessMethod()) {
            for (MemberDefinition f = accessPeer; f != null; f = f.accessPeer) {
                if (f.isAccessMethod()) {
                    return f;
                }
            }
        }
        return null;
    }

    public void setAccessUpdateMember(MemberDefinition updater) {
        if (getAccessUpdateMember() != updater) {
            if (!isAccessMethod() ||
                    updater.getAccessMethodTarget() != getAccessMethodTarget()) {
                throw new CompilerError("accessPeer");
            }
            updater.accessPeer = accessPeer;
            accessPeer = updater;
        }
    }

    /**
     * Is this an access method for a field selection or method call
     * of the form '...super.foo' or '...super.foo()'?
     */
    public final boolean isSuperAccessMethod() {
        return superAccessMethod;
    }

    /**
     * Mark this member as an access method for a field selection
     * or method call via the 'super' keyword.
     */
    public final void setIsSuperAccessMethod(boolean b) {
        superAccessMethod = b;
    }

    /**
     * Tell if this is a final variable without an initializer.
     * Such variables are subject to definite single assignment.
     */
    public final boolean isBlankFinal() {
        return isFinal() && !isSynthetic() && getValue() == null;
    }

    public boolean isNeverNull() {
        if (isUplevelValue()) {
            // loc$x and this$C are never null
            return !name.toString().startsWith(prefixVal);
        }
        return false;
    }

    /**
     * Get the field's final value (may return null)
     */
    public Node getValue(Environment env) throws ClassNotFound {
        return value;
    }
    public final Node getValue() {
        return value;
    }
    public final void setValue(Node value) {
        this.value = value;
    }
    public Object getInitialValue() {
        return null;
    }

    /**
     * Get the next field or the next match
     */
    public final MemberDefinition getNextMember() {
        return nextMember;
    }
    public final MemberDefinition getNextMatch() {
        return nextMatch;
    }

    /**
     * Get the field's documentation
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Request a check of the field definition.
     */
    public void check(Environment env) throws ClassNotFound {
    }

    /**
     * Really check the field definition.
     */
    public Vset check(Environment env, Context ctx, Vset vset) throws ClassNotFound {
        return vset;
    }

    /**
     * Generate code
     */
    public void code(Environment env, Assembler asm) throws ClassNotFound {
        throw new CompilerError("code");
    }
    public void codeInit(Environment env, Context ctx, Assembler asm) throws ClassNotFound {
        throw new CompilerError("codeInit");
    }

    /**
     * Tells whether to report a deprecation error for this field.
     */
    public boolean reportDeprecated(Environment env) {
        return (isDeprecated() || clazz.reportDeprecated(env));
    }

    /**
     * Check if a field can reach another field (only considers
     * forward references, not the access modifiers).
     */
    public final boolean canReach(Environment env, MemberDefinition f) {
        if (f.isLocal() || !f.isVariable() || !(isVariable() || isInitializer()))
            return true;
        if ((getClassDeclaration().equals(f.getClassDeclaration())) &&
            (isStatic() == f.isStatic())) {
            // They are located in the same class, and are either both
            // static or both non-static.  Check the initialization order.
            while (((f = f.getNextMember()) != null) && (f != this));
            return f != null;
        }
        return true;
    }

    //-----------------------------------------------------------------
    // The code in this section is intended to test certain kinds of
    // compatibility between methods.  There are two kinds of compatibility
    // that the compiler may need to test.  The first is whether one
    // method can legally override another.  The second is whether two
    // method definitions can legally coexist.  We use the word `meet'
    // to mean the intersection of two legally coexisting methods.
    // For more information on these kinds of compatibility, see the
    // comments/code for checkOverride() and checkMeet() below.

    /**
     * Constants used by getAccessLevel() to represent the access
     * modifiers as numbers.
     */
    static final int PUBLIC_ACCESS = 1;
    static final int PROTECTED_ACCESS = 2;
    static final int PACKAGE_ACCESS = 3;
    static final int PRIVATE_ACCESS = 4;

    /**
     * Return the access modifier of this member as a number.  The idea
     * is that this number may be used to check properties like "the
     * access modifier of x is more restrictive than the access
     * modifier of y" with a simple inequality test:
     * "x.getAccessLevel() > y.getAccessLevel.
     *
     * This is an internal utility method.
     */
    private int getAccessLevel() {
        // Could just compute this once instead of recomputing.
        // Check to see if this is worth it.
        if (isPublic()) {
            return PUBLIC_ACCESS;
        } else if (isProtected()) {
            return PROTECTED_ACCESS;
        } else if (isPackagePrivate()) {
            return PACKAGE_ACCESS;
        } else if (isPrivate()) {
            return PRIVATE_ACCESS;
        } else {
            throw new CompilerError("getAccessLevel()");
        }
    }

    /**
     * Munge our error message to report whether the override conflict
     * came from an inherited method or a declared method.
     */
    private void reportError(Environment env, String errorString,
                             ClassDeclaration clazz,
                             MemberDefinition method) {

        if (clazz == null) {
            // For example:
            // "Instance method BLAH inherited from CLASSBLAH1 cannot be
            //  overridden by the static method declared in CLASSBLAH2."
            env.error(getWhere(), errorString,
                      this, getClassDeclaration(),
                      method.getClassDeclaration());
        } else {
            // For example:
            // "In CLASSBLAH1, instance method BLAH inherited from CLASSBLAH2
            //  cannot be overridden by the static method inherited from
            //  CLASSBLAH3."
            env.error(clazz.getClassDefinition().getWhere(),
                      //"inherit." + errorString,
                      errorString,
                      //clazz,
                      this, getClassDeclaration(),
                      method.getClassDeclaration());
        }
    }

    /**
     * Convenience method to see if two methods return the same type
     */
    public boolean sameReturnType(MemberDefinition method) {
        // Make sure both are methods.
        if (!isMethod() || !method.isMethod()) {
            throw new CompilerError("sameReturnType: not method");
        }

        Type myReturnType = getType().getReturnType();
        Type yourReturnType = method.getType().getReturnType();

        return (myReturnType == yourReturnType);
    }

    /**
     * Check to see if `this' can override/hide `method'.  Caller is
     * responsible for verifying that `method' has the same signature
     * as `this'.  Caller is also responsible for verifying that
     * `method' is visible to the class where this override is occurring.
     * This method is called for the case when class B extends A and both
     * A and B define some method.
     * <pre>
     *       A - void foo() throws e1
     *       |
     *       |
     *       B - void foo() throws e2
     * </pre>
     */
    public boolean checkOverride(Environment env, MemberDefinition method) {
        return checkOverride(env, method, null);
    }

    /**
     * Checks whether `this' can override `method'.  It `clazz' is
     * null, it reports the errors in the class where `this' is
     * declared.  If `clazz' is not null, it reports the error in `clazz'.
     */
    private boolean checkOverride(Environment env,
                                  MemberDefinition method,
                                  ClassDeclaration clazz) {
        // This section of code is largely based on section 8.4.6.3
        // of the JLS.

        boolean success = true;

        // Sanity
        if (!isMethod()) {
            throw new CompilerError("checkOverride(), expected method");
        }

        // Suppress checks for synthetic methods, as the compiler presumably
        // knows what it is doing, e.g., access methods.
        if (isSynthetic()) {
            // Sanity check: We generally do not intend for one synthetic
            // method to override another, though hiding of static members
            // is expected.  This check may need to be changed if new uses
            // of synthetic methods are devised.
            //
            // Query: this code was copied from elsewhere.  What
            // exactly is the role of the !isStatic() in the test?
            if (method.isFinal() ||
                (!method.isConstructor() &&
                 !method.isStatic() && !isStatic())) {
                ////////////////////////////////////////////////////////////
                // NMG 2003-01-28 removed the following test because it is
                // invalidated by bridge methods inserted by the "generic"
                // (1.5) Java compiler.  In 1.5, this code is used,
                // indirectly, by rmic
                ////////////////////////////////////////////////////////////
                // throw new CompilerError("checkOverride() synthetic");
                ////////////////////////////////////////////////////////////
            }

            // We trust the compiler.  (Ha!)  We're done checking.
            return true;
        }

        // Our caller should have verified that the method had the
        // same signature.
        if (getName() != method.getName() ||
            !getType().equalArguments(method.getType())) {

            throw new CompilerError("checkOverride(), signature mismatch");
        }

        // It is forbidden to `override' a static method with an instance
        // method.
        if (method.isStatic() && !isStatic()) {
            reportError(env, "override.static.with.instance", clazz, method);
            success = false;
        }

        // It is forbidden to `hide' an instance method with a static
        // method.
        if (!method.isStatic() && isStatic()) {
            reportError(env, "hide.instance.with.static", clazz, method);
            success = false;
        }

        // We cannot override a final method.
        if (method.isFinal()) {
            reportError(env, "override.final.method", clazz, method);
            success = false;
        }

        // Give a warning when we override a deprecated method with
        // a non-deprecated one.
        //
        // We bend over backwards to suppress this warning if
        // the `method' has not been already compiled or
        // `this' has been already compiled.
        if (method.reportDeprecated(env) && !isDeprecated()
               && this instanceof sun.tools.javac.SourceMember) {
            reportError(env, "warn.override.is.deprecated",
                        clazz, method);
        }

        // Visibility may not be more restrictive
        if (getAccessLevel() > method.getAccessLevel()) {
            reportError(env, "override.more.restrictive", clazz, method);
            success = false;
        }

        // Return type equality
        if (!sameReturnType(method)) {
            ////////////////////////////////////////////////////////////
            // PCJ 2003-07-30 removed the following error because it is
            // invalidated by the covariant return type feature of the
            // 1.5 compiler.  The resulting check is now much looser
            // than the actual 1.5 language spec, but that should be OK
            // because this code is only still used by rmic.  See 4892308.
            ////////////////////////////////////////////////////////////
            // reportError(env, "override.different.return", clazz, method);
            // success = false;
            ////////////////////////////////////////////////////////////
        }

        // Exception agreeement
        if (!exceptionsFit(env, method)) {
            reportError(env, "override.incompatible.exceptions",
                        clazz, method);
            success = false;
        }

        return success;
    }

    /**
     * Check to see if two method definitions are compatible, that is
     * do they have a `meet'.  The meet of two methods is essentially
     * and `intersection' of
     * two methods.  This method is called when some class C inherits
     * declarations for some method foo from two parents (superclass,
     * interfaces) but it does not, itself, have a declaration of foo.
     * Caller is responsible for making sure that both methods are
     * indeed visible in clazz.
     * <pre>
     *     A - void foo() throws e1
     *      \
     *       \     B void foo() throws e2
     *        \   /
     *         \ /
     *          C
     * </pre>
     */
    public boolean checkMeet(Environment env,
                             MemberDefinition method,
                             ClassDeclaration clazz) {
        // This section of code is largely based on Section 8.4.6
        // and 9.4.1 of the JLS.

        // Sanity
        if (!isMethod()) {
            throw new CompilerError("checkMeet(), expected method");
        }

        // Check for both non-abstract.
        if (!isAbstract() && !method.isAbstract()) {
            throw new CompilerError("checkMeet(), no abstract method");
        }

        // If either method is non-abstract, then we need to check that
        // the abstract method can be properly overridden.  We call
        // the checkOverride method to check this and generate any errors.
        // This test must follow the previous test.
        else if (!isAbstract()) {
            return checkOverride(env, method, clazz);
        } else if (!method.isAbstract()) {
            return method.checkOverride(env, this, clazz);
        }

        // Both methods are abstract.

        // Our caller should have verified that the method has the
        // same signature.
        if (getName() != method.getName() ||
            !getType().equalArguments(method.getType())) {

            throw new CompilerError("checkMeet(), signature mismatch");
        }

        // Check for return type equality
        if (!sameReturnType(method)) {
            // More args?
            env.error(clazz.getClassDefinition().getWhere(),
                      "meet.different.return",
                      this, this.getClassDeclaration(),
                      method.getClassDeclaration());
            return false;
        }

        // We don't have to check visibility -- there always
        // potentially exists a meet.  Similarly with exceptions.

        // There does exist a meet.
        return true;
    }

    /**
     * This method is meant to be used to determine if one of two inherited
     * methods could override the other.  Unlike checkOverride(), failure
     * is not an error.  This method is only meant to be called after
     * checkMeet() has succeeded on the two methods.
     *
     * If you call couldOverride() without doing a checkMeet() first, then
     * you are on your own.
     */
    public boolean couldOverride(Environment env,
                                 MemberDefinition method) {

        // Sanity
        if (!isMethod()) {
            throw new CompilerError("coulcOverride(), expected method");
        }

        // couldOverride() is only called with `this' and `method' both
        // being inherited methods.  Neither of them is defined in the
        // class which we are currently working on.  Even though an
        // abstract method defined *in* a class can override a non-abstract
        // method defined in a superclass, an abstract method inherited
        // from an interface *never* can override a non-abstract method.
        // This comment may sound odd, but that's the way inheritance is.
        // The following check makes sure we aren't trying to override
        // an inherited non-abstract definition with an abstract definition
        // from an interface.
        if (!method.isAbstract()) {
            return false;
        }

        // Visibility should be less restrictive
        if (getAccessLevel() > method.getAccessLevel()) {
            return false;
        }

        // Exceptions
        if (!exceptionsFit(env, method)) {
            return false;
        }

        // Potentially some deprecation warnings could be given here
        // when we merge two abstract methods, one of which is deprecated.
        // This is not currently reported.

        return true;
    }

    /**
     * Check to see if the exceptions of `this' fit within the
     * exceptions of `method'.
     */
    private boolean exceptionsFit(Environment env,
                                  MemberDefinition method) {
        ClassDeclaration e1[] = getExceptions(env);        // my exceptions
        ClassDeclaration e2[] = method.getExceptions(env); // parent's

        // This code is taken nearly verbatim from the old implementation
        // of checkOverride() in SourceClass.
    outer:
        for (int i = 0 ; i < e1.length ; i++) {
            try {
                ClassDefinition c1 = e1[i].getClassDefinition(env);
                for (int j = 0 ; j < e2.length ; j++) {
                    if (c1.subClassOf(env, e2[j])) {
                        continue outer;
                    }
                }
                if (c1.subClassOf(env,
                                  env.getClassDeclaration(idJavaLangError)))
                    continue outer;
                if (c1.subClassOf(env,
                                  env.getClassDeclaration(idJavaLangRuntimeException)))
                    continue outer;

                // the throws was neither something declared by a parent,
                // nor one of the ignorables.
                return false;

            } catch (ClassNotFound ee) {
                // We were unable to find one of the exceptions.
                env.error(getWhere(), "class.not.found",
                          ee.name, method.getClassDeclaration());
            }
        }

        // All of the exceptions `fit'.
        return true;
    }

    //-----------------------------------------------------------------

    /**
     * Checks
     */
    public final boolean isPublic() {
        return (modifiers & M_PUBLIC) != 0;
    }
    public final boolean isPrivate() {
        return (modifiers & M_PRIVATE) != 0;
    }
    public final boolean isProtected() {
        return (modifiers & M_PROTECTED) != 0;
    }
    public final boolean isPackagePrivate() {
        return (modifiers & (M_PUBLIC | M_PRIVATE | M_PROTECTED)) == 0;
    }
    public final boolean isFinal() {
        return (modifiers & M_FINAL) != 0;
    }
    public final boolean isStatic() {
        return (modifiers & M_STATIC) != 0;
    }
    public final boolean isSynchronized() {
        return (modifiers & M_SYNCHRONIZED) != 0;
    }
    public final boolean isAbstract() {
        return (modifiers & M_ABSTRACT) != 0;
    }
    public final boolean isNative() {
        return (modifiers & M_NATIVE) != 0;
    }
    public final boolean isVolatile() {
        return (modifiers & M_VOLATILE) != 0;
    }
    public final boolean isTransient() {
        return (modifiers & M_TRANSIENT) != 0;
    }
    public final boolean isMethod() {
        return type.isType(TC_METHOD);
    }
    public final boolean isVariable() {
        return !type.isType(TC_METHOD) && innerClass == null;
    }
    public final boolean isSynthetic() {
        return (modifiers & M_SYNTHETIC) != 0;
    }
    public final boolean isDeprecated() {
        return (modifiers & M_DEPRECATED) != 0;
    }
    public final boolean isStrict() {
        return (modifiers & M_STRICTFP) != 0;
    }
    public final boolean isInnerClass() {
        return innerClass != null;
    }
    public final boolean isInitializer() {
        return getName().equals(idClassInit);
    }
    public final boolean isConstructor() {
        return getName().equals(idInit);
    }
    public boolean isLocal() {
        return false;
    }
    public boolean isInlineable(Environment env, boolean fromFinal) throws ClassNotFound {
        return (isStatic() || isPrivate() || isFinal() || isConstructor() || fromFinal) &&
            !(isSynchronized() || isNative());
    }

    /**
     * Check if constant:  Will it inline away to a constant?
     */
    public boolean isConstant() {
        if (isFinal() && isVariable() && value != null) {
            try {
                // If an infinite regress requeries this name,
                // deny that it is a constant.
                modifiers &= ~M_FINAL;
                return ((Expression)value).isConstant();
            } finally {
                modifiers |= M_FINAL;
            }
        }
        return false;
    }

    /**
     * toString
     */
    public String toString() {
        Identifier name = getClassDefinition().getName();
        if (isInitializer()) {
            return isStatic() ? "static {}" : "instance {}";
        } else if (isConstructor()) {
            StringBuffer buf = new StringBuffer();
            buf.append(name);
            buf.append('(');
            Type argTypes[] = getType().getArgumentTypes();
            for (int i = 0 ; i < argTypes.length ; i++) {
                if (i > 0) {
                    buf.append(',');
                }
                buf.append(argTypes[i].toString());
            }
            buf.append(')');
            return buf.toString();
        } else if (isInnerClass()) {
            return getInnerClass().toString();
        }
        return type.typeString(getName().toString());
    }

    /**
     * Print for debugging
     */
    public void print(PrintStream out) {
        if (isPublic()) {
            out.print("public ");
        }
        if (isPrivate()) {
            out.print("private ");
        }
        if (isProtected()) {
            out.print("protected ");
        }
        if (isFinal()) {
            out.print("final ");
        }
        if (isStatic()) {
            out.print("static ");
        }
        if (isSynchronized()) {
            out.print("synchronized ");
        }
        if (isAbstract()) {
            out.print("abstract ");
        }
        if (isNative()) {
            out.print("native ");
        }
        if (isVolatile()) {
            out.print("volatile ");
        }
        if (isTransient()) {
            out.print("transient ");
        }
        out.println(toString() + ";");
    }

    public void cleanup(Environment env) {
        documentation = null;
        if (isMethod() && value != null) {
            int cost = 0;
            if (isPrivate() || isInitializer()) {
                value = Statement.empty;
            } else if ((cost =
                        ((Statement)value)
                       .costInline(Statement.MAXINLINECOST, null, null))
                                >= Statement.MAXINLINECOST) {
                // will never be inlined
                value = Statement.empty;
            } else {
                try {
                    if (!isInlineable(null, true)) {
                        value = Statement.empty;
                    }
                }
                catch (ClassNotFound ee) { }
            }
            if (value != Statement.empty && env.dump()) {
                env.output("[after cleanup of " + getName() + ", " +
                           cost + " expression cost units remain]");
            }
        } else if (isVariable()) {
            if (isPrivate() || !isFinal() || type.isType(TC_ARRAY)) {
                value = null;
            }
        }
    }
}
