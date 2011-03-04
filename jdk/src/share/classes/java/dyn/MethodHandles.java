/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

import java.lang.reflect.*;
import sun.dyn.Access;
import sun.dyn.MemberName;
import sun.dyn.MethodHandleImpl;
import sun.dyn.WrapperInstance;
import sun.dyn.util.ValueConversions;
import sun.dyn.util.VerifyAccess;
import sun.dyn.util.Wrapper;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import sun.dyn.Invokers;
import sun.dyn.MethodTypeImpl;
import sun.reflect.Reflection;
import static sun.dyn.MemberName.newIllegalArgumentException;
import static sun.dyn.MemberName.newNoAccessException;

/**
 * This class consists exclusively of static methods that operate on or return
 * method handles. They fall into several categories:
 * <ul>
 * <li>Lookup methods which help create method handles for methods and fields.
 * <li>Combinator methods, which combine or transform pre-existing method handles into new ones.
 * <li>Other factory methods to create method handles that emulate other common JVM operations or control flow patterns.
 * <li>Wrapper methods which can convert between method handles and other function-like "SAM types".
 * </ul>
 * <p>
 * @author John Rose, JSR 292 EG
 */
public class MethodHandles {

    private MethodHandles() { }  // do not instantiate

    private static final Access IMPL_TOKEN = Access.getToken();
    private static final MemberName.Factory IMPL_NAMES = MemberName.getFactory(IMPL_TOKEN);
    static { MethodHandleImpl.initStatics(); }
    // See IMPL_LOOKUP below.

    //// Method handle creation from ordinary methods.

    /**
     * Return a {@link Lookup lookup object} on the caller,
     * which has the capability to access any method handle that the caller has access to,
     * including direct method handles to private fields and methods.
     * This lookup object is a <em>capability</em> which may be delegated to trusted agents.
     * Do not store it in place where untrusted code can access it.
     */
    public static Lookup lookup() {
        return new Lookup();
    }

    /**
     * Return a {@link Lookup lookup object} which is trusted minimally.
     * It can only be used to create method handles to
     * publicly accessible fields and methods.
     * <p>
     * As a matter of pure convention, the {@linkplain Lookup#lookupClass lookup class}
     * of this lookup object will be {@link java.lang.Object}.
     * <p>
     * The lookup class can be changed to any other class {@code C} using an expression of the form
     * {@linkplain Lookup#in <code>publicLookup().in(C.class)</code>}.
     * Since all classes have equal access to public names,
     * such a change would confer no new access rights.
     */
    public static Lookup publicLookup() {
        return Lookup.PUBLIC_LOOKUP;
    }

    /**
     * A <em>lookup object</em> is a factory for creating method handles,
     * when the creation requires access checking.
     * Method handles do not perform
     * access checks when they are called, but rather when they are created.
     * Therefore, method handle access
     * restrictions must be enforced when a method handle is created.
     * The caller class against which those restrictions are enforced
     * is known as the {@linkplain #lookupClass lookup class}.
     * <p>
     * A lookup class which needs to create method handles will call
     * {@link MethodHandles#lookup MethodHandles.lookup} to create a factory for itself.
     * When the {@code Lookup} factory object is created, the identity of the lookup class is
     * determined, and securely stored in the {@code Lookup} object.
     * The lookup class (or its delegates) may then use factory methods
     * on the {@code Lookup} object to create method handles for access-checked members.
     * This includes all methods, constructors, and fields which are allowed to the lookup class,
     * even private ones.
     * <p>
     * The factory methods on a {@code Lookup} object correspond to all major
     * use cases for methods, constructors, and fields.
     * Here is a summary of the correspondence between these factory methods and
     * the behavior the resulting method handles:
     * <code>
     * <table border=1 cellpadding=5 summary="lookup method behaviors">
     * <tr><th>lookup expression</th><th>member</th><th>behavior</th></tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findGetter lookup.findGetter(C.class,"f",FT.class)}</td>
     *     <td>FT f;</td><td>(T) this.f;</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findStaticGetter lookup.findStaticGetter(C.class,"f",FT.class)}</td>
     *     <td>static<br>FT f;</td><td>(T) C.f;</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findSetter lookup.findSetter(C.class,"f",FT.class)}</td>
     *     <td>FT f;</td><td>this.f = x;</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findStaticSetter lookup.findStaticSetter(C.class,"f",FT.class)}</td>
     *     <td>static<br>FT f;</td><td>C.f = arg;</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findVirtual lookup.findVirtual(C.class,"m",MT)}</td>
     *     <td>T m(A*);</td><td>(T) this.m(arg*);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findStatic lookup.findStatic(C.class,"m",MT)}</td>
     *     <td>static<br>T m(A*);</td><td>(T) C.m(arg*);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findSpecial lookup.findSpecial(C.class,"m",MT,this.class)}</td>
     *     <td>T m(A*);</td><td>(T) super.m(arg*);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#findConstructor lookup.findConstructor(C.class,MT)}</td>
     *     <td>C(A*);</td><td>(T) new C(arg*);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#unreflectGetter lookup.unreflectGetter(aField)}</td>
     *     <td>(static)?<br>FT f;</td><td>(FT) aField.get(thisOrNull);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#unreflectSetter lookup.unreflectSetter(aField)}</td>
     *     <td>(static)?<br>FT f;</td><td>aField.set(thisOrNull, arg);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#unreflect lookup.unreflect(aMethod)}</td>
     *     <td>(static)?<br>T m(A*);</td><td>(T) aMethod.invoke(thisOrNull, arg*);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#unreflectConstructor lookup.unreflectConstructor(aConstructor)}</td>
     *     <td>C(A*);</td><td>(C) aConstructor.newInstance(arg*);</td>
     * </tr>
     * <tr>
     *     <td>{@linkplain java.dyn.MethodHandles.Lookup#unreflect lookup.unreflect(aMethod)}</td>
     *     <td>(static)?<br>T m(A*);</td><td>(T) aMethod.invoke(thisOrNull, arg*);</td>
     * </tr>
     * </table>
     * </code>
     * Here, the type {@code C} is the class or interface being searched for a member,
     * documented as a parameter named {@code refc} in the lookup methods.
     * The method or constructor type {@code MT} is composed from the return type {@code T}
     * and the sequence of argument types {@code A*}.
     * Both {@code MT} and the field type {@code FT} are documented as a parameter named {@code type}.
     * The formal parameter {@code this} stands for the self-reference of type {@code C};
     * if it is present, it is always the leading argument to the method handle invocation.
     * The name {@code arg} stands for all the other method handle arguments.
     * In the code examples for the Core Reflection API, the name {@code thisOrNull}
     * stands for a null reference if the accessed method or field is static,
     * and {@code this} otherwise.
     * The names {@code aMethod}, {@code aField}, and {@code aConstructor} stand
     * for reflective objects corresponding to the given members.
     * <p>
     * The equivalence between looked-up method handles and underlying
     * class members can break down in a few ways:
     * <ul>
     * <li>If {@code C} is not symbolically accessible from the lookup class's loader,
     * the lookup can still succeed, even when there is no equivalent
     * Java expression or bytecoded constant.
     * <li>Likewise, if {@code T} or {@code MT}
     * is not symbolically accessible from the lookup class's loader,
     * the lookup can still succeed.
     * For example, lookups for {@code MethodHandle.invokeExact} and
     * {@code MethodHandle.invokeGeneric} will always succeed, regardless of requested type.
     * <li>If there is a security manager installed, it can forbid the lookup
     * on various grounds (<a href="#secmgr">see below</a>).
     * By contrast, the {@code ldc} instruction is not subject to
     * security manager checks.
     * </ul>
     *
     * <h3><a name="access"></a>Access checking</h3>
     * Access checks are applied in the factory methods of {@code Lookup},
     * when a method handle is created.
     * This is a key difference from the Core Reflection API, since
     * {@link java.lang.reflect.Method#invoke Method.invoke}
     * performs access checking against every caller, on every call.
     * <p>
     * All access checks start from a {@code Lookup} object, which
     * compares its recorded lookup class against all requests to
     * create method handles.
     * A single {@code Lookup} object can be used to create any number
     * of access-checked method handles, all checked against a single
     * lookup class.
     * <p>
     * A {@code Lookup} object can be shared with other trusted code,
     * such as a metaobject protocol.
     * A shared {@code Lookup} object delegates the capability
     * to create method handles on private members of the lookup class.
     * Even if privileged code uses the {@code Lookup} object,
     * the access checking is confined to the privileges of the
     * original lookup class.
     * <p>
     * A lookup can fail, because
     * the containing class is not accessible to the lookup class, or
     * because the desired class member is missing, or because the
     * desired class member is not accessible to the lookup class.
     * In any of these cases, a {@code ReflectiveOperationException} will be
     * thrown from the attempted lookup.  The exact class will be one of
     * the following:
     * <ul>
     * <li>NoSuchMethodException &mdash; if a method is requested but does not exist
     * <li>NoSuchFieldException &mdash; if a field is requested but does not exist
     * <li>IllegalAccessException &mdash; if the member exists but an access check fails
     * </ul>
     * <p>
     * In general, the conditions under which a method handle may be
     * looked up for a method {@code M} are exactly equivalent to the conditions
     * under which the lookup class could have compiled and resolved a call to {@code M}.
     * And the effect of invoking the method handle resulting from the lookup
     * is exactly equivalent to executing the compiled and resolved call to {@code M}.
     * The same point is true of fields and constructors.
     * <p>
     * In some cases, access between nested classes is obtained by the Java compiler by creating
     * an wrapper method to access a private method of another class
     * in the same top-level declaration.
     * For example, a nested class {@code C.D}
     * can access private members within other related classes such as
     * {@code C}, {@code C.D.E}, or {@code C.B},
     * but the Java compiler may need to generate wrapper methods in
     * those related classes.  In such cases, a {@code Lookup} object on
     * {@code C.E} would be unable to those private members.
     * A workaround for this limitation is the {@link Lookup#in Lookup.in} method,
     * which can transform a lookup on {@code C.E} into one on any of those other
     * classes, without special elevation of privilege.
     * <p>
     * Although bytecode instructions can only refer to classes in
     * a related class loader, this API can search for methods in any
     * class, as long as a reference to its {@code Class} object is
     * available.  Such cross-loader references are also possible with the
     * Core Reflection API, and are impossible to bytecode instructions
     * such as {@code invokestatic} or {@code getfield}.
     * There is a {@linkplain java.lang.SecurityManager security manager API}
     * to allow applications to check such cross-loader references.
     * These checks apply to both the {@code MethodHandles.Lookup} API
     * and the Core Reflection API
     * (as found on {@link java.lang.Class Class}).
     * <p>
     * Access checks only apply to named and reflected methods,
     * constructors, and fields.
     * Other method handle creation methods, such as
     * {@link #convertArguments MethodHandles.convertArguments},
     * do not require any access checks, and are done
     * with static methods of {@link MethodHandles},
     * independently of any {@code Lookup} object.
     *
     * <h3>Security manager interactions</h3>
     * <a name="secmgr"></a>
     * If a security manager is present, member lookups are subject to
     * additional checks.
     * From one to four calls are made to the security manager.
     * Any of these calls can refuse access by throwing a
     * {@link java.lang.SecurityException SecurityException}.
     * Define {@code smgr} as the security manager,
     * {@code refc} as the containing class in which the member
     * is being sought, and {@code defc} as the class in which the
     * member is actually defined.
     * The calls are made according to the following rules:
     * <ul>
     * <li>In all cases, {@link SecurityManager#checkMemberAccess
     *     smgr.checkMemberAccess(refc, Member.PUBLIC)} is called.
     * <li>If the class loader of the lookup class is not
     *     the same as or an ancestor of the class loader of {@code refc},
     *     then {@link SecurityManager#checkPackageAccess
     *     smgr.checkPackageAccess(refcPkg)} is called,
     *     where {@code refcPkg} is the package of {@code refc}.
     * <li>If the retrieved member is not public,
     *     {@link SecurityManager#checkMemberAccess
     *     smgr.checkMemberAccess(defc, Member.DECLARED)} is called.
     *     (Note that {@code defc} might be the same as {@code refc}.)
     * <li>If the retrieved member is not public,
     *     and if {@code defc} and {@code refc} are in different class loaders,
     *     and if the class loader of the lookup class is not
     *     the same as or an ancestor of the class loader of {@code defc},
     *     then {@link SecurityManager#checkPackageAccess
     *     smgr.checkPackageAccess(defcPkg)} is called,
     *     where {@code defcPkg} is the package of {@code defc}.
     * </ul>
     * In all cases, the requesting class presented to the security
     * manager will be the lookup class from the current {@code Lookup} object.
     */
    public static final
    class Lookup {
        /** The class on behalf of whom the lookup is being performed. */
        private final Class<?> lookupClass;

        /** The allowed sorts of members which may be looked up (PUBLIC, etc.). */
        private final int allowedModes;

        /** A single-bit mask representing {@code public} access,
         *  which may contribute to the result of {@link #lookupModes lookupModes}.
         *  The value, {@code 0x01}, happens to be the same as the value of the
         *  {@code public} {@linkplain java.lang.reflect.Modifier#PUBLIC modifier bit}.
         */
        public static final int PUBLIC = Modifier.PUBLIC;

        /** A single-bit mask representing {@code private} access,
         *  which may contribute to the result of {@link #lookupModes lookupModes}.
         *  The value, {@code 0x02}, happens to be the same as the value of the
         *  {@code private} {@linkplain java.lang.reflect.Modifier#PRIVATE modifier bit}.
         */
        public static final int PRIVATE = Modifier.PRIVATE;

        /** A single-bit mask representing {@code protected} access,
         *  which may contribute to the result of {@link #lookupModes lookupModes}.
         *  The value, {@code 0x04}, happens to be the same as the value of the
         *  {@code protected} {@linkplain java.lang.reflect.Modifier#PROTECTED modifier bit}.
         */
        public static final int PROTECTED = Modifier.PROTECTED;

        /** A single-bit mask representing {@code package} access (default access),
         *  which may contribute to the result of {@link #lookupModes lookupModes}.
         *  The value is {@code 0x08}, which does not correspond meaningfully to
         *  any particular {@linkplain java.lang.reflect.Modifier modifier bit}.
         */
        public static final int PACKAGE = Modifier.STATIC;

        private static final int ALL_MODES = (PUBLIC | PRIVATE | PROTECTED | PACKAGE);
        private static final int TRUSTED   = -1;

        private static int fixmods(int mods) {
            mods &= (ALL_MODES - PACKAGE);
            return (mods != 0) ? mods : PACKAGE;
        }

        /** Tells which class is performing the lookup.  It is this class against
         *  which checks are performed for visibility and access permissions.
         *  <p>
         *  The class implies a maximum level of access permission,
         *  but the permissions may be additionally limited by the bitmask
         *  {@link #lookupModes lookupModes}, which controls whether non-public members
         *  can be accessed.
         */
        public Class<?> lookupClass() {
            return lookupClass;
        }

        // This is just for calling out to MethodHandleImpl.
        private Class<?> lookupClassOrNull() {
            return (allowedModes == TRUSTED) ? null : lookupClass;
        }

        /** Tells which access-protection classes of members this lookup object can produce.
         *  The result is a bit-mask of the bits
         *  {@linkplain #PUBLIC PUBLIC (0x01)},
         *  {@linkplain #PRIVATE PRIVATE (0x02)},
         *  {@linkplain #PROTECTED PROTECTED (0x04)},
         *  and {@linkplain #PACKAGE PACKAGE (0x08)}.
         *  <p>
         *  A freshly-created lookup object
         *  on the {@linkplain java.dyn.MethodHandles#lookup() caller's class}
         *  has all possible bits set, since the caller class can access all its own members.
         *  A lookup object on a new lookup class
         *  {@linkplain java.dyn.MethodHandles.Lookup#in created from a previous lookup object}
         *  may have some mode bits set to zero.
         *  The purpose of this is to restrict access via the new lookup object,
         *  so that it can access only names which can be reached by the original
         *  lookup object, and also by the new lookup class.
         */
        public int lookupModes() {
            return allowedModes & ALL_MODES;
        }

        /** Embody the current class (the lookupClass) as a lookup class
         * for method handle creation.
         * Must be called by from a method in this package,
         * which in turn is called by a method not in this package.
         * <p>
         * Also, don't make it private, lest javac interpose
         * an access$N method.
         */
        Lookup() {
            this(getCallerClassAtEntryPoint(), ALL_MODES);
            // make sure we haven't accidentally picked up a privileged class:
            checkUnprivilegedlookupClass(lookupClass);
        }

        Lookup(Access token, Class<?> lookupClass) {
            this(lookupClass, ALL_MODES);
            Access.check(token);
        }

        private Lookup(Class<?> lookupClass, int allowedModes) {
            this.lookupClass = lookupClass;
            this.allowedModes = allowedModes;
        }

        /**
         * Creates a lookup on the specified new lookup class.
         * The resulting object will report the specified
         * class as its own {@link #lookupClass lookupClass}.
         * <p>
         * However, the resulting {@code Lookup} object is guaranteed
         * to have no more access capabilities than the original.
         * In particular, access capabilities can be lost as follows:<ul>
         * <li>If the new lookup class differs from the old one,
         * protected members will not be accessible by virtue of inheritance.
         * (Protected members may continue to be accessible because of package sharing.)
         * <li>If the new lookup class is in a different package
         * than the old one, protected and default (package) members will not be accessible.
         * <li>If the new lookup class is not within the same package member
         * as the old one, private members will not be accessible.
         * <li>If the new lookup class is not accessible to the old lookup class,
         * then no members, not even public members, will be accessible.
         * (In all other cases, public members will continue to be accessible.)
         * </ul>
         *
         * @param requestedLookupClass the desired lookup class for the new lookup object
         * @return a lookup object which reports the desired lookup class
         * @throws NullPointerException if the argument is null
         */
        public Lookup in(Class<?> requestedLookupClass) {
            requestedLookupClass.getClass();  // null check
            if (allowedModes == TRUSTED)  // IMPL_LOOKUP can make any lookup at all
                return new Lookup(requestedLookupClass, ALL_MODES);
            if (requestedLookupClass == this.lookupClass)
                return this;  // keep same capabilities
            int newModes = (allowedModes & (ALL_MODES & ~PROTECTED));
            if ((newModes & PACKAGE) != 0
                && !VerifyAccess.isSamePackage(this.lookupClass, requestedLookupClass)) {
                newModes &= ~(PACKAGE|PRIVATE);
            }
            // Allow nestmate lookups to be created without special privilege:
            if ((newModes & PRIVATE) != 0
                && !VerifyAccess.isSamePackageMember(this.lookupClass, requestedLookupClass)) {
                newModes &= ~PRIVATE;
            }
            if (newModes == PUBLIC
                && !VerifyAccess.isClassAccessible(requestedLookupClass, this.lookupClass)) {
                // The requested class it not accessible from the lookup class.
                // No permissions.
                newModes = 0;
            }
            checkUnprivilegedlookupClass(requestedLookupClass);
            return new Lookup(requestedLookupClass, newModes);
        }

        // Make sure outer class is initialized first.
        static { IMPL_TOKEN.getClass(); }

        /** Version of lookup which is trusted minimally.
         *  It can only be used to create method handles to
         *  publicly accessible members.
         */
        static final Lookup PUBLIC_LOOKUP = new Lookup(Object.class, PUBLIC);

        /** Package-private version of lookup which is trusted. */
        static final Lookup IMPL_LOOKUP = new Lookup(Object.class, TRUSTED);
        static { MethodHandleImpl.initLookup(IMPL_TOKEN, IMPL_LOOKUP); }

        private static void checkUnprivilegedlookupClass(Class<?> lookupClass) {
            String name = lookupClass.getName();
            if (name.startsWith("java.dyn.") || name.startsWith("sun.dyn."))
                throw newIllegalArgumentException("illegal lookupClass: "+lookupClass);
        }

        /**
         * Displays the name of the class from which lookups are to be made.
         * (The name is the one reported by {@link java.lang.Class#getName() Class.getName}.)
         * If there are restrictions on the access permitted to this lookup,
         * this is indicated by adding a suffix to the class name, consisting
         * of a slash and a keyword.  The keyword represents the strongest
         * allowed access, and is chosen as follows:
         * <ul>
         * <li>If no access is allowed, the suffix is "/noaccess".
         * <li>If only public access is allowed, the suffix is "/public".
         * <li>If only public and package access are allowed, the suffix is "/package".
         * <li>If only public, package, and private access are allowed, the suffix is "/private".
         * </ul>
         * If none of the above cases apply, it is the case that full
         * access (public, package, private, and protected) is allowed.
         * In this case, no suffix is added.
         * This is true only of an object obtained originally from
         * {@link java.dyn.MethodHandles#lookup MethodHandles.lookup}.
         * Objects created by {@link java.dyn.MethodHandles.Lookup#in Lookup.in}
         * always have restricted access, and will display a suffix.
         * <p>
         * (It may seem strange that protected access should be
         * stronger than private access.  Viewed independently from
         * package access, protected access is the first to be lost,
         * because it requires a direct subclass relationship between
         * caller and callee.)
         * @see #in
         */
        @Override
        public String toString() {
            String cname = lookupClass.getName();
            switch (allowedModes) {
            case 0:  // no privileges
                return cname + "/noaccess";
            case PUBLIC:
                return cname + "/public";
            case PUBLIC|PACKAGE:
                return cname + "/package";
            case ALL_MODES & ~PROTECTED:
                return cname + "/private";
            case ALL_MODES:
                return cname;
            case TRUSTED:
                return "/trusted";  // internal only; not exported
            default:  // Should not happen, but it's a bitfield...
                cname = cname + "/" + Integer.toHexString(allowedModes);
                assert(false) : cname;
                return cname;
            }
        }

        // call this from an entry point method in Lookup with extraFrames=0.
        private static Class<?> getCallerClassAtEntryPoint() {
            final int CALLER_DEPTH = 4;
            // 0: Reflection.getCC, 1: getCallerClassAtEntryPoint,
            // 2: Lookup.<init>, 3: MethodHandles.*, 4: caller
            // Note:  This should be the only use of getCallerClass in this file.
            assert(Reflection.getCallerClass(CALLER_DEPTH-1) == MethodHandles.class);
            return Reflection.getCallerClass(CALLER_DEPTH);
        }

        /**
         * Produces a method handle for a static method.
         * The type of the method handle will be that of the method.
         * (Since static methods do not take receivers, there is no
         * additional receiver argument inserted into the method handle type,
         * as there would be with {@link #findVirtual findVirtual} or {@link #findSpecial findSpecial}.)
         * The method and all its argument types must be accessible to the lookup class.
         * If the method's class has not yet been initialized, that is done
         * immediately, before the method handle is returned.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set.
         * @param refc the class from which the method is accessed
         * @param name the name of the method
         * @param type the type of the method
         * @return the desired method handle
         * @throws NoSuchMethodException if the method does not exist
         * @throws IllegalAccessException if access checking fails, or if the method is not {@code static}
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public
        MethodHandle findStatic(Class<?> refc, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
            MemberName method = resolveOrFail(refc, name, type, true);
            checkMethod(refc, method, true);
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, lookupClassOrNull());
        }

        /**
         * Produces a method handle for a virtual method.
         * The type of the method handle will be that of the method,
         * with the receiver type (usually {@code refc}) prepended.
         * The method and all its argument types must be accessible to the lookup class.
         * <p>
         * When called, the handle will treat the first argument as a receiver
         * and dispatch on the receiver's type to determine which method
         * implementation to enter.
         * (The dispatching action is identical with that performed by an
         * {@code invokevirtual} or {@code invokeinterface} instruction.)
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set.
         * <p>
         * Because of the general equivalence between {@code invokevirtual}
         * instructions and method handles produced by {@code findVirtual},
         * if the class is {@code MethodHandle} and the name string is
         * {@code invokeExact} or {@code invokeGeneric}, the resulting
         * method handle is equivalent to one produced by
         * {@link java.dyn.MethodHandles#exactInvoker MethodHandles.exactInvoker} or
         * {@link java.dyn.MethodHandles#genericInvoker MethodHandles.genericInvoker}
         * with the same {@code type} argument.
         *
         * @param refc the class or interface from which the method is accessed
         * @param name the name of the method
         * @param type the type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @throws NoSuchMethodException if the method does not exist
         * @throws IllegalAccessException if access checking fails, or if the method is {@code static}
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findVirtual(Class<?> refc, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
            MemberName method = resolveOrFail(refc, name, type, false);
            checkMethod(refc, method, false);
            MethodHandle mh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClassOrNull());
            return restrictProtectedReceiver(method, mh);
        }

        /**
         * Produces a method handle which creates an object and initializes it, using
         * the constructor of the specified type.
         * The parameter types of the method handle will be those of the constructor,
         * while the return type will be a reference to the constructor's class.
         * The constructor and all its argument types must be accessible to the lookup class.
         * If the constructor's class has not yet been initialized, that is done
         * immediately, before the method handle is returned.
         * <p>
         * Note:  The requested type must have a return type of {@code void}.
         * This is consistent with the JVM's treatment of constructor type descriptors.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the constructor's variable arity modifier bit ({@code 0x0080}) is set.
         * @param refc the class or interface from which the method is accessed
         * @param type the type of the method, with the receiver argument omitted, and a void return type
         * @return the desired method handle
         * @throws NoSuchMethodException if the constructor does not exist
         * @throws IllegalAccessException if access checking fails
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findConstructor(Class<?> refc, MethodType type) throws NoSuchMethodException, IllegalAccessException {
            String name = "<init>";
            MemberName ctor = resolveOrFail(refc, name, type, false, false, lookupClassOrNull());
            assert(ctor.isConstructor());
            checkAccess(refc, ctor);
            MethodHandle rawMH = MethodHandleImpl.findMethod(IMPL_TOKEN, ctor, false, lookupClassOrNull());
            MethodHandle allocMH = MethodHandleImpl.makeAllocator(IMPL_TOKEN, rawMH);
            return fixVarargs(allocMH, rawMH);
        }

        /** Return a version of MH which matches matchMH w.r.t. isVarargsCollector. */
        private static MethodHandle fixVarargs(MethodHandle mh, MethodHandle matchMH) {
            boolean va1 = mh.isVarargsCollector();
            boolean va2 = matchMH.isVarargsCollector();
            if (va1 == va2) {
                return mh;
            } else if (va2) {
                MethodType type = mh.type();
                int arity = type.parameterCount();
                return mh.asVarargsCollector(type.parameterType(arity-1));
            } else {
                throw new InternalError("already varargs, but template is not: "+mh);
            }
        }

        /**
         * Produces an early-bound method handle for a virtual method,
         * as if called from an {@code invokespecial}
         * instruction from {@code caller}.
         * The type of the method handle will be that of the method,
         * with a suitably restricted receiver type (such as {@code caller}) prepended.
         * The method and all its argument types must be accessible
         * to the caller.
         * <p>
         * When called, the handle will treat the first argument as a receiver,
         * but will not dispatch on the receiver's type.
         * (This direct invocation action is identical with that performed by an
         * {@code invokespecial} instruction.)
         * <p>
         * If the explicitly specified caller class is not identical with the
         * lookup class, or if this lookup object does not have private access
         * privileges, the access fails.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set.
         * @param refc the class or interface from which the method is accessed
         * @param name the name of the method (which must not be "&lt;init&gt;")
         * @param type the type of the method, with the receiver argument omitted
         * @param specialCaller the proposed calling class to perform the {@code invokespecial}
         * @return the desired method handle
         * @throws NoSuchMethodException if the method does not exist
         * @throws IllegalAccessException if access checking fails
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findSpecial(Class<?> refc, String name, MethodType type,
                                        Class<?> specialCaller) throws NoSuchMethodException, IllegalAccessException {
            checkSpecialCaller(specialCaller);
            MemberName method = resolveOrFail(refc, name, type, false, false, specialCaller);
            checkMethod(refc, method, false);
            MethodHandle mh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, specialCaller);
            return restrictReceiver(method, mh, specialCaller);
        }

        /**
         * Produces a method handle giving read access to a non-static field.
         * The type of the method handle will have a return type of the field's
         * value type.
         * The method handle's single argument will be the instance containing
         * the field.
         * Access checking is performed immediately on behalf of the lookup class.
         * @param refc the class or interface from which the method is accessed
         * @param name the field's name
         * @param type the field's type
         * @return a method handle which can load values from the field
         * @throws NoSuchFieldException if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is {@code static}
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findGetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
            return makeAccessor(refc, name, type, false, false);
        }

        /**
         * Produces a method handle giving write access to a non-static field.
         * The type of the method handle will have a void return type.
         * The method handle will take two arguments, the instance containing
         * the field, and the value to be stored.
         * The second argument will be of the field's value type.
         * Access checking is performed immediately on behalf of the lookup class.
         * @param refc the class or interface from which the method is accessed
         * @param name the field's name
         * @param type the field's type
         * @return a method handle which can store values into the field
         * @throws NoSuchFieldException if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is {@code static}
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findSetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
            return makeAccessor(refc, name, type, false, true);
        }

        /**
         * Produces a method handle giving read access to a static field.
         * The type of the method handle will have a return type of the field's
         * value type.
         * The method handle will take no arguments.
         * Access checking is performed immediately on behalf of the lookup class.
         * @param refc the class or interface from which the method is accessed
         * @param name the field's name
         * @param type the field's type
         * @return a method handle which can load values from the field
         * @throws NoSuchFieldException if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is not {@code static}
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findStaticGetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
            return makeAccessor(refc, name, type, true, false);
        }

        /**
         * Produces a method handle giving write access to a static field.
         * The type of the method handle will have a void return type.
         * The method handle will take a single
         * argument, of the field's value type, the value to be stored.
         * Access checking is performed immediately on behalf of the lookup class.
         * @param refc the class or interface from which the method is accessed
         * @param name the field's name
         * @param type the field's type
         * @return a method handle which can store values into the field
         * @throws NoSuchFieldException if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is not {@code static}
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle findStaticSetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
            return makeAccessor(refc, name, type, true, true);
        }

        /**
         * Produces an early-bound method handle for a non-static method.
         * The receiver must have a supertype {@code defc} in which a method
         * of the given name and type is accessible to the lookup class.
         * The method and all its argument types must be accessible to the lookup class.
         * The type of the method handle will be that of the method,
         * without any insertion of an additional receiver parameter.
         * The given receiver will be bound into the method handle,
         * so that every call to the method handle will invoke the
         * requested method on the given receiver.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set
         * <em>and</em> the trailing array argument is not the only argument.
         * (If the trailing array argument is the only argument,
         * the given receiver value will be bound to it.)
         * <p>
         * This is equivalent to the following code:
         * <blockquote><pre>
MethodHandle mh0 = {@link #findVirtual findVirtual}(defc, name, type);
MethodHandle mh1 = mh0.{@link MethodHandle#bindTo bindTo}(receiver);
MethodType mt1 = mh1.type();
if (mh0.isVarargsCollector() && mt1.parameterCount() > 0) {
  mh1 = mh1.asVarargsCollector(mt1.parameterType(mt1.parameterCount()-1));
return mh1;
         * </pre></blockquote>
         * where {@code defc} is either {@code receiver.getClass()} or a super
         * type of that class, in which the requested method is accessible
         * to the lookup class.
         * (Note that {@code bindTo} does not preserve variable arity.)
         * @param receiver the object from which the method is accessed
         * @param name the name of the method
         * @param type the type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @throws NoSuchMethodException if the method does not exist
         * @throws IllegalAccessException if access checking fails
         * @exception SecurityException if a security manager is present and it
         *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle bind(Object receiver, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
            Class<? extends Object> refc = receiver.getClass(); // may get NPE
            MemberName method = resolveOrFail(refc, name, type, false);
            checkMethod(refc, method, false);
            MethodHandle dmh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClassOrNull());
            MethodHandle bmh = MethodHandleImpl.bindReceiver(IMPL_TOKEN, dmh, receiver);
            if (bmh == null)
                throw newNoAccessException(method, this);
            if (dmh.type().parameterCount() == 0)
                return dmh;  // bound the trailing parameter; no varargs possible
            return fixVarargs(bmh, dmh);
        }

        /**
         * Make a direct method handle to <i>m</i>, if the lookup class has permission.
         * If <i>m</i> is non-static, the receiver argument is treated as an initial argument.
         * If <i>m</i> is virtual, overriding is respected on every call.
         * Unlike the Core Reflection API, exceptions are <em>not</em> wrapped.
         * The type of the method handle will be that of the method,
         * with the receiver type prepended (but only if it is non-static).
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * If <i>m</i> is not public, do not share the resulting handle with untrusted parties.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set.
         * @param m the reflected method
         * @return a method handle which can invoke the reflected method
         * @throws IllegalAccessException if access checking fails
         * @throws NullPointerException if the argument is null
         */
        public MethodHandle unreflect(Method m) throws IllegalAccessException {
            MemberName method = new MemberName(m);
            assert(method.isMethod());
            if (!m.isAccessible())  checkMethod(method.getDeclaringClass(), method, method.isStatic());
            MethodHandle mh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClassOrNull());
            if (!m.isAccessible())  mh = restrictProtectedReceiver(method, mh);
            return mh;
        }

        /**
         * Produces a method handle for a reflected method.
         * It will bypass checks for overriding methods on the receiver,
         * as if by a {@code invokespecial} instruction from within the {@code specialCaller}.
         * The type of the method handle will be that of the method,
         * with the special caller type prepended (and <em>not</em> the receiver of the method).
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class,
         * as if {@code invokespecial} instruction were being linked.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set.
         * @param m the reflected method
         * @param specialCaller the class nominally calling the method
         * @return a method handle which can invoke the reflected method
         * @throws IllegalAccessException if access checking fails
         * @throws NullPointerException if any argument is null
         */
        public MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws IllegalAccessException {
            checkSpecialCaller(specialCaller);
            MemberName method = new MemberName(m);
            assert(method.isMethod());
            // ignore m.isAccessible:  this is a new kind of access
            checkMethod(m.getDeclaringClass(), method, false);
            MethodHandle mh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, lookupClassOrNull());
            return restrictReceiver(method, mh, specialCaller);
        }

        /**
         * Produces a method handle for a reflected constructor.
         * The type of the method handle will be that of the constructor,
         * with the return type changed to the declaring class.
         * The method handle will perform a {@code newInstance} operation,
         * creating a new instance of the constructor's class on the
         * arguments passed to the method handle.
         * <p>
         * If the constructor's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the constructor's variable arity modifier bit ({@code 0x0080}) is set.
         * @param c the reflected constructor
         * @return a method handle which can invoke the reflected constructor
         * @throws IllegalAccessException if access checking fails
         * @throws NullPointerException if the argument is null
         */
        public MethodHandle unreflectConstructor(Constructor c) throws IllegalAccessException {
            MemberName ctor = new MemberName(c);
            assert(ctor.isConstructor());
            if (!c.isAccessible())  checkAccess(c.getDeclaringClass(), ctor);
            MethodHandle rawCtor = MethodHandleImpl.findMethod(IMPL_TOKEN, ctor, false, lookupClassOrNull());
            MethodHandle allocator = MethodHandleImpl.makeAllocator(IMPL_TOKEN, rawCtor);
            return fixVarargs(allocator, rawCtor);
        }

        /**
         * Produces a method handle giving read access to a reflected field.
         * The type of the method handle will have a return type of the field's
         * value type.
         * If the field is static, the method handle will take no arguments.
         * Otherwise, its single argument will be the instance containing
         * the field.
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param f the reflected field
         * @return a method handle which can load values from the reflected field
         * @throws IllegalAccessException if access checking fails
         * @throws NullPointerException if the argument is null
         */
        public MethodHandle unreflectGetter(Field f) throws IllegalAccessException {
            return makeAccessor(f.getDeclaringClass(), new MemberName(f), f.isAccessible(), false);
        }

        /**
         * Produces a method handle giving write access to a reflected field.
         * The type of the method handle will have a void return type.
         * If the field is static, the method handle will take a single
         * argument, of the field's value type, the value to be stored.
         * Otherwise, the two arguments will be the instance containing
         * the field, and the value to be stored.
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param f the reflected field
         * @return a method handle which can store values into the reflected field
         * @throws IllegalAccessException if access checking fails
         * @throws NullPointerException if the argument is null
         */
        public MethodHandle unreflectSetter(Field f) throws IllegalAccessException {
            return makeAccessor(f.getDeclaringClass(), new MemberName(f), f.isAccessible(), true);
        }

        /// Helper methods, all package-private.

        MemberName resolveOrFail(Class<?> refc, String name, Class<?> type, boolean isStatic) throws NoSuchFieldException, IllegalAccessException {
            checkSymbolicClass(refc);  // do this before attempting to resolve
            name.getClass(); type.getClass();  // NPE
            int mods = (isStatic ? Modifier.STATIC : 0);
            return IMPL_NAMES.resolveOrFail(new MemberName(refc, name, type, mods), true, lookupClassOrNull(),
                                            NoSuchFieldException.class);
        }

        MemberName resolveOrFail(Class<?> refc, String name, MethodType type, boolean isStatic) throws NoSuchMethodException, IllegalAccessException {
            checkSymbolicClass(refc);  // do this before attempting to resolve
            name.getClass(); type.getClass();  // NPE
            int mods = (isStatic ? Modifier.STATIC : 0);
            return IMPL_NAMES.resolveOrFail(new MemberName(refc, name, type, mods), true, lookupClassOrNull(),
                                            NoSuchMethodException.class);
        }

        MemberName resolveOrFail(Class<?> refc, String name, MethodType type, boolean isStatic,
                                 boolean searchSupers, Class<?> specialCaller) throws NoSuchMethodException, IllegalAccessException {
            checkSymbolicClass(refc);  // do this before attempting to resolve
            name.getClass(); type.getClass();  // NPE
            int mods = (isStatic ? Modifier.STATIC : 0);
            return IMPL_NAMES.resolveOrFail(new MemberName(refc, name, type, mods), searchSupers, specialCaller,
                                            NoSuchMethodException.class);
        }

        void checkSymbolicClass(Class<?> refc) throws IllegalAccessException {
            Class<?> caller = lookupClassOrNull();
            if (caller != null && !VerifyAccess.isClassAccessible(refc, caller))
                throw newNoAccessException("symbolic reference class is not public", new MemberName(refc), this);
        }

        void checkMethod(Class<?> refc, MemberName m, boolean wantStatic) throws IllegalAccessException {
            String message;
            if (m.isConstructor())
                message = "expected a method, not a constructor";
            else if (!m.isMethod())
                message = "expected a method";
            else if (wantStatic != m.isStatic())
                message = wantStatic ? "expected a static method" : "expected a non-static method";
            else
                { checkAccess(refc, m); return; }
            throw newNoAccessException(message, m, this);
        }

        void checkAccess(Class<?> refc, MemberName m) throws IllegalAccessException {
            int allowedModes = this.allowedModes;
            if (allowedModes == TRUSTED)  return;
            int mods = m.getModifiers();
            if (Modifier.isPublic(mods) && Modifier.isPublic(refc.getModifiers()) && allowedModes != 0)
                return;  // common case
            int requestedModes = fixmods(mods);  // adjust 0 => PACKAGE
            if ((requestedModes & allowedModes) != 0
                && VerifyAccess.isMemberAccessible(refc, m.getDeclaringClass(),
                                                   mods, lookupClass()))
                return;
            if (((requestedModes & ~allowedModes) & PROTECTED) != 0
                && VerifyAccess.isSamePackage(m.getDeclaringClass(), lookupClass()))
                // Protected members can also be checked as if they were package-private.
                return;
            throw newNoAccessException(accessFailedMessage(refc, m), m, this);
        }

        String accessFailedMessage(Class<?> refc, MemberName m) {
            Class<?> defc = m.getDeclaringClass();
            int mods = m.getModifiers();
            // check the class first:
            boolean classOK = (Modifier.isPublic(defc.getModifiers()) &&
                               (defc == refc ||
                                Modifier.isPublic(refc.getModifiers())));
            if (!classOK && (allowedModes & PACKAGE) != 0) {
                classOK = (VerifyAccess.isClassAccessible(defc, lookupClass()) &&
                           (defc == refc ||
                            VerifyAccess.isClassAccessible(refc, lookupClass())));
            }
            if (!classOK)
                return "class is not public";
            if (Modifier.isPublic(mods))
                return "access to public member failed";  // (how?)
            if (Modifier.isPrivate(mods))
                return "member is private";
            if (Modifier.isProtected(mods))
                return "member is protected";
            return "member is private to package";
        }

        private static final boolean ALLOW_NESTMATE_ACCESS = false;

        void checkSpecialCaller(Class<?> specialCaller) throws IllegalAccessException {
            if (allowedModes == TRUSTED)  return;
            if ((allowedModes & PRIVATE) == 0
                || (specialCaller != lookupClass()
                    && !(ALLOW_NESTMATE_ACCESS &&
                         VerifyAccess.isSamePackageMember(specialCaller, lookupClass()))))
                throw newNoAccessException("no private access for invokespecial",
                                           new MemberName(specialCaller), this);
        }

        MethodHandle restrictProtectedReceiver(MemberName method, MethodHandle mh) throws IllegalAccessException {
            // The accessing class only has the right to use a protected member
            // on itself or a subclass.  Enforce that restriction, from JVMS 5.4.4, etc.
            if (!method.isProtected() || method.isStatic()
                || allowedModes == TRUSTED
                || method.getDeclaringClass() == lookupClass()
                || (ALLOW_NESTMATE_ACCESS &&
                    VerifyAccess.isSamePackageMember(method.getDeclaringClass(), lookupClass())))
                return mh;
            else
                return restrictReceiver(method, mh, lookupClass());
        }
        MethodHandle restrictReceiver(MemberName method, MethodHandle mh, Class<?> caller) throws IllegalAccessException {
            assert(!method.isStatic());
            Class<?> defc = method.getDeclaringClass();  // receiver type of mh is too wide
            if (defc.isInterface() || !defc.isAssignableFrom(caller)) {
                throw newNoAccessException("caller class must be a subclass below the method", method, caller);
            }
            MethodType rawType = mh.type();
            if (rawType.parameterType(0) == caller)  return mh;
            MethodType narrowType = rawType.changeParameterType(0, caller);
            MethodHandle narrowMH = MethodHandleImpl.convertArguments(IMPL_TOKEN, mh, narrowType, rawType, null);
            return fixVarargs(narrowMH, mh);
        }

        MethodHandle makeAccessor(Class<?> refc, String name, Class<?> type,
                                  boolean isStatic, boolean isSetter) throws NoSuchFieldException, IllegalAccessException {
            MemberName field = resolveOrFail(refc, name, type, isStatic);
            if (isStatic != field.isStatic())
                throw newNoAccessException(isStatic
                                           ? "expected a static field"
                                           : "expected a non-static field",
                                           field, this);
            return makeAccessor(refc, field, false, isSetter);
        }

        MethodHandle makeAccessor(Class<?> refc, MemberName field,
                                  boolean trusted, boolean isSetter) throws IllegalAccessException {
            assert(field.isField());
            if (trusted)
                return MethodHandleImpl.accessField(IMPL_TOKEN, field, isSetter, lookupClassOrNull());
            checkAccess(refc, field);
            MethodHandle mh = MethodHandleImpl.accessField(IMPL_TOKEN, field, isSetter, lookupClassOrNull());
            return restrictProtectedReceiver(field, mh);
        }
    }

    /**
     * Produces a method handle giving read access to elements of an array.
     * The type of the method handle will have a return type of the array's
     * element type.  Its first argument will be the array type,
     * and the second will be {@code int}.
     * @param arrayClass an array type
     * @return a method handle which can load values from the given array type
     * @throws NullPointerException if the argument is null
     * @throws  IllegalArgumentException if arrayClass is not an array type
     */
    public static
    MethodHandle arrayElementGetter(Class<?> arrayClass) throws IllegalArgumentException {
        return MethodHandleImpl.accessArrayElement(IMPL_TOKEN, arrayClass, false);
    }

    /**
     * Produces a method handle giving write access to elements of an array.
     * The type of the method handle will have a void return type.
     * Its last argument will be the array's element type.
     * The first and second arguments will be the array type and int.
     * @return a method handle which can store values into the array type
     * @throws NullPointerException if the argument is null
     * @throws IllegalArgumentException if arrayClass is not an array type
     */
    public static
    MethodHandle arrayElementSetter(Class<?> arrayClass) throws IllegalArgumentException {
        return MethodHandleImpl.accessArrayElement(IMPL_TOKEN, arrayClass, true);
    }

    /// method handle invocation (reflective style)

    /**
     * Produces a method handle which will invoke any method handle of the
     * given {@code type} on a standard set of {@code Object} type arguments
     * and a single trailing {@code Object[]} array.
     * The resulting invoker will be a method handle with the following
     * arguments:
     * <ul>
     * <li>a single {@code MethodHandle} target
     * <li>zero or more {@code Object} values (counted by {@code objectArgCount})
     * <li>an {@code Object[]} array containing more arguments
     * </ul>
     * <p>
     * The invoker will behave like a call to {@link MethodHandle#invokeGeneric invokeGeneric} with
     * the indicated {@code type}.
     * That is, if the target is exactly of the given {@code type}, it will behave
     * like {@code invokeExact}; otherwise it behave as if {@link MethodHandle#asType asType}
     * is used to convert the target to the required {@code type}.
     * <p>
     * The type of the returned invoker will not be the given {@code type}, but rather
     * will have all parameter and return types replaced by {@code Object}, except for
     * the last parameter type, which will be the array type {@code Object[]}.
     * <p>
     * Before invoking its target, the invoker will spread the varargs array, apply
     * reference casts as necessary, and unbox and widen primitive arguments.
     * The return value of the invoker will be an {@code Object} reference,
     * boxing a primitive value if the original type returns a primitive,
     * and always null if the original type returns void.
     * <p>
     * This method is equivalent to the following code (though it may be more efficient):
     * <p><blockquote><pre>
MethodHandle invoker = MethodHandles.genericInvoker(type);
int spreadArgCount = type.parameterCount - objectArgCount;
invoker = invoker.asSpreader(Object[].class, spreadArgCount);
return invoker;
     * </pre></blockquote>
     * <p>
     * This method throws no reflective or security exceptions.
     * @param type the desired target type
     * @param objectArgCount number of fixed (non-varargs) {@code Object} arguments
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle spreadInvoker(MethodType type, int objectArgCount) {
        if (objectArgCount < 0 || objectArgCount > type.parameterCount())
            throw new IllegalArgumentException("bad argument count "+objectArgCount);
        return invokers(type).spreadInvoker(objectArgCount);
    }

    /**
     * Produces a special <em>invoker method handle</em> which can be used to
     * invoke any method handle of the given type, as if by {@code invokeExact}.
     * The resulting invoker will have a type which is
     * exactly equal to the desired type, except that it will accept
     * an additional leading argument of type {@code MethodHandle}.
     * <p>
     * This method is equivalent to the following code (though it may be more efficient):
     * <p><blockquote><pre>
publicLookup().findVirtual(MethodHandle.class, "invokeExact", type)
     * </pre></blockquote>
     *
     * <p style="font-size:smaller;">
     * <em>Discussion:</em>
     * Invoker method handles can be useful when working with variable method handles
     * of unknown types.
     * For example, to emulate an {@code invokeExact} call to a variable method
     * handle {@code M}, extract its type {@code T},
     * look up the invoker method {@code X} for {@code T},
     * and call the invoker method, as {@code X.invokeGeneric(T, A...)}.
     * (It would not work to call {@code X.invokeExact}, since the type {@code T}
     * is unknown.)
     * If spreading, collecting, or other argument transformations are required,
     * they can be applied once to the invoker {@code X} and reused on many {@code M}
     * method handle values, as long as they are compatible with the type of {@code X}.
     * <p>
     * <em>(Note:  The invoker method is not available via the Core Reflection API.
     * An attempt to call {@linkplain java.lang.reflect.Method#invoke Method.invoke}
     * on the declared {@code invokeExact} or {@code invokeGeneric} method will raise an
     * {@link java.lang.UnsupportedOperationException UnsupportedOperationException}.)</em>
     * <p>
     * This method throws no reflective or security exceptions.
     * @param type the desired target type
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle exactInvoker(MethodType type) {
        return invokers(type).exactInvoker();
    }

    /**
     * Produces a special <em>invoker method handle</em> which can be used to
     * invoke any method handle of the given type, as if by {@code invokeGeneric}.
     * The resulting invoker will have a type which is
     * exactly equal to the desired type, except that it will accept
     * an additional leading argument of type {@code MethodHandle}.
     * <p>
     * Before invoking its target, the invoker will apply reference casts as
     * necessary and unbox and widen primitive arguments, as if by {@link #convertArguments convertArguments}.
     * The return value of the invoker will be an {@code Object} reference,
     * boxing a primitive value if the original type returns a primitive,
     * and always null if the original type returns void.
     * <p>
     * This method is equivalent to the following code (though it may be more efficient):
     * <p><blockquote><pre>
publicLookup().findVirtual(MethodHandle.class, "invokeGeneric", type)
     * </pre></blockquote>
     * <p>
     * This method throws no reflective or security exceptions.
     * @param type the desired target type
     * @return a method handle suitable for invoking any method handle convertible to the given type
     */
    static public
    MethodHandle genericInvoker(MethodType type) {
        return invokers(type).genericInvoker();
    }

    static Invokers invokers(MethodType type) {
        return MethodTypeImpl.invokers(IMPL_TOKEN, type);
    }

    /**
     * Perform value checking, exactly as if for an adapted method handle.
     * It is assumed that the given value is either null, of type T0,
     * or (if T0 is primitive) of the wrapper type corresponding to T0.
     * The following checks and conversions are made:
     * <ul>
     * <li>If T0 and T1 are references, then a cast to T1 is applied.
     *     (The types do not need to be related in any particular way.)
     * <li>If T0 and T1 are primitives, then a widening or narrowing
     *     conversion is applied, if one exists.
     * <li>If T0 is a primitive and T1 a reference, and
     *     T0 has a wrapper type TW, a boxing conversion to TW is applied,
     *     possibly followed by a reference conversion.
     *     T1 must be TW or a supertype.
     * <li>If T0 is a reference and T1 a primitive, and
     *     T1 has a wrapper type TW, an unboxing conversion is applied,
     *     possibly preceded by a reference conversion.
     *     T0 must be TW or a supertype.
     * <li>If T1 is void, the return value is discarded
     * <li>If T0 is void and T1 a reference, a null value is introduced.
     * <li>If T0 is void and T1 a primitive, a zero value is introduced.
     * </ul>
     * If the value is discarded, null will be returned.
     * @param valueType
     * @param value
     * @return the value, converted if necessary
     * @throws java.lang.ClassCastException if a cast fails
     */
    static
    <T0, T1> T1 checkValue(Class<T0> t0, Class<T1> t1, Object value)
       throws ClassCastException
    {
        if (t0 == t1) {
            // no conversion needed; just reassert the same type
            if (t0.isPrimitive())
                return Wrapper.asPrimitiveType(t1).cast(value);
            else
                return Wrapper.OBJECT.convert(value, t1);
        }
        boolean prim0 = t0.isPrimitive(), prim1 = t1.isPrimitive();
        if (!prim0) {
            // check contract with caller
            Wrapper.OBJECT.convert(value, t0);
            if (!prim1) {
                return Wrapper.OBJECT.convert(value, t1);
            }
            // convert reference to primitive by unboxing
            Wrapper w1 = Wrapper.forPrimitiveType(t1);
            return w1.convert(value, t1);
        }
        // check contract with caller:
        Wrapper.asWrapperType(t0).cast(value);
        Wrapper w1 = Wrapper.forPrimitiveType(t1);
        return w1.convert(value, t1);
    }

    static
    Object checkValue(Class<?> T1, Object value)
       throws ClassCastException
    {
        Class<?> T0;
        if (value == null)
            T0 = Object.class;
        else
            T0 = value.getClass();
        return checkValue(T0, T1, value);
    }

    /// method handle modification (creation from other method handles)

    /**
     * Produces a method handle which adapts the type of the
     * given method handle to a new type by pairwise argument conversion.
     * The original type and new type must have the same number of arguments.
     * The resulting method handle is guaranteed to report a type
     * which is equal to the desired new type.
     * <p>
     * If the original type and new type are equal, returns target.
     * <p>
     * The following conversions are applied as needed both to
     * arguments and return types.  Let T0 and T1 be the differing
     * new and old parameter types (or old and new return types)
     * for corresponding values passed by the new and old method types.
     * Given those types T0, T1, one of the following conversions is applied
     * if possible:
     * <ul>
     * <li>If T0 and T1 are references, then a cast to T1 is applied.
     *     (The types do not need to be related in any particular way.)
     * <li>If T0 and T1 are primitives, then a Java method invocation
     *     conversion (JLS 5.3) is applied, if one exists.
     * <li>If T0 is a primitive and T1 a reference, a boxing
     *     conversion is applied if one exists, possibly followed by
     *     a reference conversion to a superclass.
     *     T1 must be a wrapper class or a supertype of one.
     * <li>If T0 is a reference and T1 a primitive, an unboxing
     *     conversion will be applied at runtime, possibly followed
     *     by a Java method invocation conversion (JLS 5.3)
     *     on the primitive value.  (These are the widening conversions.)
     *     T0 must be a wrapper class or a supertype of one.
     *     (In the case where T0 is Object, these are the conversions
     *     allowed by java.lang.reflect.Method.invoke.)
     * <li>If the return type T1 is void, any returned value is discarded
     * <li>If the return type T0 is void and T1 a reference, a null value is introduced.
     * <li>If the return type T0 is void and T1 a primitive, a zero value is introduced.
     * </ul>
     * @param target the method handle to invoke after arguments are retyped
     * @param newType the expected type of the new method handle
     * @return a method handle which delegates to {@code target} after performing
     *           any necessary argument conversions, and arranges for any
     *           necessary return value conversions
     * @throws NullPointerException if either argument is null
     * @throws WrongMethodTypeException if the conversion cannot be made
     * @see MethodHandle#asType
     * @see MethodHandles#explicitCastArguments
     */
    public static
    MethodHandle convertArguments(MethodHandle target, MethodType newType) {
        MethodType oldType = target.type();
        if (oldType.equals(newType))
            return target;
        MethodHandle res = null;
        try {
            res = MethodHandleImpl.convertArguments(IMPL_TOKEN, target,
                                                    newType, oldType, null);
        } catch (IllegalArgumentException ex) {
        }
        if (res == null)
            throw new WrongMethodTypeException("cannot convert to "+newType+": "+target);
        return res;
    }

    /**
     * Produces a method handle which adapts the type of the
     * given method handle to a new type by pairwise argument conversion.
     * The original type and new type must have the same number of arguments.
     * The resulting method handle is guaranteed to report a type
     * which is equal to the desired new type.
     * <p>
     * If the original type and new type are equal, returns target.
     * <p>
     * The same conversions are allowed as for {@link #convertArguments convertArguments},
     * and some additional conversions are also applied if those conversions fail.
     * Given types T0, T1, one of the following conversions is applied
     * in addition, if the conversions specified for {@code convertArguments}
     * would be insufficient:
     * <ul>
     * <li>If T0 and T1 are references, and T1 is an interface type,
     *     then the value of type T0 is passed as a T1 without a cast.
     *     (This treatment of interfaces follows the usage of the bytecode verifier.)
     * <li>If T0 and T1 are primitives and one is boolean,
     *     the boolean is treated as a one-bit unsigned integer.
     *     (This treatment follows the usage of the bytecode verifier.)
     *     A conversion from another primitive type behaves as if
     *     it first converts to byte, and then masks all but the low bit.
     * <li>If a primitive value would be converted by {@code convertArguments}
     *     using Java method invocation conversion (JLS 5.3),
     *     Java casting conversion (JLS 5.5) may be used also.
     *     This allows primitives to be narrowed as well as widened.
     * </ul>
     * @param target the method handle to invoke after arguments are retyped
     * @param newType the expected type of the new method handle
     * @return a method handle which delegates to {@code target} after performing
     *           any necessary argument conversions, and arranges for any
     *           necessary return value conversions
     * @throws NullPointerException if either argument is null
     * @throws WrongMethodTypeException if the conversion cannot be made
     * @see MethodHandle#asType
     * @see MethodHandles#convertArguments
     */
    public static
    MethodHandle explicitCastArguments(MethodHandle target, MethodType newType) {
        return convertArguments(target, newType);  // FIXME!
    }

    /*
      FIXME: Reconcile javadoc with 10/22/2010 EG notes on conversion:

      Both converters arrange for their method handles to convert arguments
      and return values.  The conversion rules are the same for arguments
      and return values, and depend only on source and target types, S and
      T.  The conversions allowed by castConvertArguments are a strict
      superset of those performed by convertArguments.

      In all cases, if S and T are references, a simple checkcast is done.
      If neither S nor T is a primitive, no attempt is made to unbox and
      box.  A failed conversion throws ClassCastException.

      If T is void, the value is dropped.

      For compatibility with reflection, if S is void and T is a reference,
      a null value is produced.

      For compatibility with reflection, if S is a reference and T is a
      primitive, S is first unboxed and then undergoes primitive conversion.
      In the case of 'convertArguments', only assignment conversion is
      performed (no narrowing primitive conversion).

      If S is a primitive, S is boxed, and then the above rules are applied.
      If S and T are both primitives, the boxing will be undetectable; only
      the primitive conversions will be apparent to the user.  The key point
      is that if S is a primitive type, the implementation may box it and
      treat is as Object, without loss of information, or it may use a "fast
      path" which does not use boxing.

      Notwithstanding the rules above, for compatibility with the verifier,
      if T is an interface, it is treated as if it were Object.  [KEEP THIS?]

      Also, for compatibility with the verifier, a boolean may be undergo
      widening or narrowing conversion to any other primitive type.  [KEEP THIS?]
    */

    /**
     * Produces a method handle which adapts the calling sequence of the
     * given method handle to a new type, by reordering the arguments.
     * The resulting method handle is guaranteed to report a type
     * which is equal to the desired new type.
     * <p>
     * The given array controls the reordering.
     * Call {@code #I} the number of incoming parameters (the value
     * {@code newType.parameterCount()}, and call {@code #O} the number
     * of outgoing parameters (the value {@code target.type().parameterCount()}).
     * Then the length of the reordering array must be {@code #O},
     * and each element must be a non-negative number less than {@code #I}.
     * For every {@code N} less than {@code #O}, the {@code N}-th
     * outgoing argument will be taken from the {@code I}-th incoming
     * argument, where {@code I} is {@code reorder[N]}.
     * <p>
     * No argument or return value conversions are applied.
     * The type of each incoming argument, as determined by {@code newType},
     * must be identical to the type of the corresponding outgoing argument
     * or arguments in the target method handle.
     * The return type of {@code newType} must be identical to the return
     * type of the original target.
     * <p>
     * The reordering array need not specify an actual permutation.
     * An incoming argument will be duplicated if its index appears
     * more than once in the array, and an incoming argument will be dropped
     * if its index does not appear in the array.
     * As in the case of {@link #dropArguments(MethodHandle,int,List) dropArguments},
     * incoming arguments which are not mentioned in the reordering array
     * are may be any type, as determined only by {@code newType}.
     * <blockquote><pre>
MethodType intfn1 = MethodType.methodType(int.class, int.class);
MethodType intfn2 = MethodType.methodType(int.class, int.class, int.class);
MethodHandle sub = ... {int x, int y => x-y} ...;
assert(sub.type().equals(intfn2));
MethodHandle sub1 = MethodHandles.permuteArguments(sub, intfn2, 0, 1);
MethodHandle rsub = MethodHandles.permuteArguments(sub, intfn2, 1, 0);
assert((int)rsub.invokeExact(1, 100) == 99);
MethodHandle add = ... {int x, int y => x+y} ...;
assert(add.type().equals(intfn2));
MethodHandle twice = MethodHandles.permuteArguments(add, intfn1, 0, 0);
assert(twice.type().equals(intfn1));
assert((int)twice.invokeExact(21) == 42);
     * </pre></blockquote>
     * @param target the method handle to invoke after arguments are reordered
     * @param newType the expected type of the new method handle
     * @param reorder a string which controls the reordering
     * @return a method handle which delegates to {@code target} after it
     *           drops unused arguments and moves and/or duplicates the other arguments
     * @throws NullPointerException if any argument is null
     */
    public static
    MethodHandle permuteArguments(MethodHandle target, MethodType newType, int... reorder) {
        MethodType oldType = target.type();
        checkReorder(reorder, newType, oldType);
        return MethodHandleImpl.convertArguments(IMPL_TOKEN, target,
                                                 newType, oldType,
                                                 reorder);
    }

    private static void checkReorder(int[] reorder, MethodType newType, MethodType oldType) {
        if (reorder.length == oldType.parameterCount()) {
            int limit = newType.parameterCount();
            boolean bad = false;
            for (int i : reorder) {
                if (i < 0 || i >= limit) {
                    bad = true; break;
                }
            }
            if (!bad)  return;
        }
        throw newIllegalArgumentException("bad reorder array");
    }

    /**
     * Equivalent to the following code:
     * <p><blockquote><pre>
     * int spreadPos = newType.parameterCount() - 1;
     * Class&lt;?&gt; spreadType = newType.parameterType(spreadPos);
     * int spreadCount = target.type().parameterCount() - spreadPos;
     * MethodHandle adapter = target.asSpreader(spreadType, spreadCount);
     * adapter = adapter.asType(newType);
     * return adapter;
     * </pre></blockquote>
     * @param target the method handle to invoke after argument spreading
     * @param newType the expected type of the new method handle
     * @return a method handle which spreads its final argument,
     *         before calling the original method handle
     */
    /*non-public*/ static
    MethodHandle spreadArguments(MethodHandle target, MethodType newType) {
        MethodType oldType = target.type();
        int inargs  = newType.parameterCount();
        int outargs = oldType.parameterCount();
        int spreadPos = inargs - 1;
        int numSpread = (outargs - spreadPos);
        MethodHandle res = null;
        if (spreadPos >= 0 && numSpread >= 0) {
            res = MethodHandleImpl.spreadArguments(IMPL_TOKEN, target, newType, spreadPos);
        }
        if (res == null) {
            throw newIllegalArgumentException("cannot spread "+newType+" to " +oldType);
        }
        return res;
    }

    /**
     * Equivalent to the following code:
     * <p><blockquote><pre>
     * int collectPos = target.type().parameterCount() - 1;
     * Class&lt;?&gt; collectType = target.type().parameterType(collectPos);
     * if (!collectType.isArray())  collectType = Object[].class;
     * int collectCount = newType.parameterCount() - collectPos;
     * MethodHandle adapter = target.asCollector(collectType, collectCount);
     * adapter = adapter.asType(newType);
     * return adapter;
     * </pre></blockquote>
     * @param target the method handle to invoke after argument collection
     * @param newType the expected type of the new method handle
     * @return a method handle which collects some trailing argument
     *         into an array, before calling the original method handle
     */
    /*non-public*/ static
    MethodHandle collectArguments(MethodHandle target, MethodType newType) {
        MethodType oldType = target.type();
        int inargs  = newType.parameterCount();
        int outargs = oldType.parameterCount();
        int collectPos = outargs - 1;
        int numCollect = (inargs - collectPos);
        if (collectPos < 0 || numCollect < 0)
            throw newIllegalArgumentException("wrong number of arguments");
        MethodHandle res = MethodHandleImpl.collectArguments(IMPL_TOKEN, target, newType, collectPos, null);
        if (res == null) {
            throw newIllegalArgumentException("cannot collect from "+newType+" to " +oldType);
        }
        return res;
    }

    /**
     * Produces a method handle of the requested return type which returns the given
     * constant value every time it is invoked.
     * <p>
     * Before the method handle is returned, the passed-in value is converted to the requested type.
     * If the requested type is primitive, widening primitive conversions are attempted,
     * else reference conversions are attempted.
     * <p>The returned method handle is equivalent to {@code identity(type).bindTo(value)},
     * unless the type is {@code void}, in which case it is {@code identity(type)}.
     * @param type the return type of the desired method handle
     * @param value the value to return
     * @return a method handle of the given return type and no arguments, which always returns the given value
     * @throws NullPointerException if the {@code type} argument is null
     * @throws ClassCastException if the value cannot be converted to the required return type
     * @throws IllegalArgumentException if the given type is {@code void.class}
     */
    public static
    MethodHandle constant(Class<?> type, Object value) {
        if (type.isPrimitive()) {
            if (type == void.class)
                throw newIllegalArgumentException("void type");
            Wrapper w = Wrapper.forPrimitiveType(type);
            return identity(type).bindTo(w.convert(value, type));
        } else {
            return identity(type).bindTo(type.cast(value));
        }
    }

    /**
     * Produces a method handle which returns its sole argument when invoked.
     * <p>The identity function for {@code void} takes no arguments and returns no values.
     * @param type the type of the sole parameter and return value of the desired method handle
     * @return a unary method handle which accepts and returns the given type
     * @throws NullPointerException if the argument is null
     * @throws IllegalArgumentException if the given type is {@code void.class}
     */
    public static
    MethodHandle identity(Class<?> type) {
        if (type == void.class)
            throw newIllegalArgumentException("void type");
        return ValueConversions.identity(type);
    }

    /**
     * Produces a method handle which calls the original method handle {@code target},
     * after inserting the given argument(s) at the given position.
     * The formal parameters to {@code target} which will be supplied by those
     * arguments are called <em>bound parameters</em>, because the new method
     * will contain bindings for those parameters take from {@code values}.
     * The type of the new method handle will drop the types for the bound
     * parameters from the original target type, since the new method handle
     * will no longer require those arguments to be supplied by its callers.
     * <p>
     * Each given argument object must match the corresponding bound parameter type.
     * If a bound parameter type is a primitive, the argument object
     * must be a wrapper, and will be unboxed to produce the primitive value.
     * <p>
     * The  <i>pos</i> may range between zero and <i>N</i> (inclusively),
     * where <i>N</i> is the number of argument types in resulting method handle
     * (after bound parameter types are dropped).
     * @param target the method handle to invoke after the argument is inserted
     * @param pos where to insert the argument (zero for the first)
     * @param values the series of arguments to insert
     * @return a method handle which inserts an additional argument,
     *         before calling the original method handle
     * @throws NullPointerException if the {@code target} argument or the {@code values} array is null
     * @see MethodHandle#bindTo
     */
    public static
    MethodHandle insertArguments(MethodHandle target, int pos, Object... values) {
        int insCount = values.length;
        MethodType oldType = target.type();
        ArrayList<Class<?>> ptypes =
                new ArrayList<Class<?>>(oldType.parameterList());
        int outargs = oldType.parameterCount();
        int inargs  = outargs - insCount;
        if (inargs < 0)
            throw newIllegalArgumentException("too many values to insert");
        if (pos < 0 || pos > inargs)
            throw newIllegalArgumentException("no argument type to append");
        MethodHandle result = target;
        for (int i = 0; i < insCount; i++) {
            Object value = values[i];
            Class<?> valueType = oldType.parameterType(pos+i);
            value = checkValue(valueType, value);
            if (pos == 0 && !valueType.isPrimitive()) {
                // At least for now, make bound method handles a special case.
                MethodHandle bmh = MethodHandleImpl.bindReceiver(IMPL_TOKEN, result, value);
                if (bmh != null) {
                    result = bmh;
                    continue;
                }
                // else fall through to general adapter machinery
            }
            result = MethodHandleImpl.bindArgument(IMPL_TOKEN, result, pos, value);
        }
        return result;
    }

    /**
     * Produces a method handle which calls the original method handle,
     * after dropping the given argument(s) at the given position.
     * The type of the new method handle will insert the given argument
     * type(s), at that position, into the original handle's type.
     * <p>
     * The <i>pos</i> may range between zero and <i>N</i>,
     * where <i>N</i> is the number of argument types in <i>target</i>,
     * meaning to drop the first or last argument (respectively),
     * or an argument somewhere in between.
     * <p>
     * <b>Example:</b>
     * <p><blockquote><pre>
import static java.dyn.MethodHandles.*;
import static java.dyn.MethodType.*;
...
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
assertEquals("xy", (String) cat.invokeExact("x", "y"));
MethodHandle d0 = dropArguments(cat, 0, String.class);
assertEquals("yz", (String) d0.invokeExact("x", "y", "z"));
MethodHandle d1 = dropArguments(cat, 1, String.class);
assertEquals("xz", (String) d1.invokeExact("x", "y", "z"));
MethodHandle d2 = dropArguments(cat, 2, String.class);
assertEquals("xy", (String) d2.invokeExact("x", "y", "z"));
MethodHandle d12 = dropArguments(cat, 1, int.class, boolean.class);
assertEquals("xz", (String) d12.invokeExact("x", 12, true, "z"));
     * </pre></blockquote>
     * @param target the method handle to invoke after the arguments are dropped
     * @param valueTypes the type(s) of the argument(s) to drop
     * @param pos position of first argument to drop (zero for the leftmost)
     * @return a method handle which drops arguments of the given types,
     *         before calling the original method handle
     * @throws NullPointerException if the {@code target} argument is null,
     *                              or if the {@code valueTypes} list or any of its elements is null
     * @throws IllegalArgumentException if any of the {@code valueTypes} is {@code void.class}
     */
    public static
    MethodHandle dropArguments(MethodHandle target, int pos, List<Class<?>> valueTypes) {
        if (valueTypes.size() == 0)  return target;
        MethodType oldType = target.type();
        int outargs = oldType.parameterCount();
        int inargs  = outargs + valueTypes.size();
        if (pos < 0 || pos >= inargs)
            throw newIllegalArgumentException("no argument type to remove");
        ArrayList<Class<?>> ptypes =
                new ArrayList<Class<?>>(oldType.parameterList());
        ptypes.addAll(pos, valueTypes);
        MethodType newType = MethodType.methodType(oldType.returnType(), ptypes);
        return MethodHandleImpl.dropArguments(IMPL_TOKEN, target, newType, pos);
    }

    /**
     * Produces a method handle which calls the original method handle,
     * after dropping the given argument(s) at the given position.
     * The type of the new method handle will insert the given argument
     * type(s), at that position, into the original handle's type.
     * This method is equivalent to the following code:
     * <code>
     * {@link #dropArguments(MethodHandle,int,List) dropArguments}(target, pos, Arrays.asList(valueTypes))
     * </code>
     * @param target the method handle to invoke after the arguments are dropped
     * @param valueTypes the type(s) of the argument(s) to drop
     * @param pos position of first argument to drop (zero for the leftmost)
     * @return a method handle which drops arguments of the given types,
     *         before calling the original method handle
     * @throws NullPointerException if the {@code target} argument is null,
     *                              or if the {@code valueTypes} array or any of its elements is null
     * @throws IllegalArgumentException if any of the {@code valueTypes} is {@code void.class}
     */
    public static
    MethodHandle dropArguments(MethodHandle target, int pos, Class<?>... valueTypes) {
        return dropArguments(target, pos, Arrays.asList(valueTypes));
    }

    /**
     * Adapt a target method handle {@code target} by pre-processing
     * one or more of its arguments, each with its own unary filter function,
     * and then calling the target with each pre-processed argument
     * replaced by the result of its corresponding filter function.
     * <p>
     * The pre-processing is performed by one or more method handles,
     * specified in the elements of the {@code filters} array.
     * Null arguments in the array are ignored, and the corresponding arguments left unchanged.
     * (If there are no non-null elements in the array, the original target is returned.)
     * Each filter is applied to the corresponding argument of the adapter.
     * <p>
     * If a filter {@code F} applies to the {@code N}th argument of
     * the method handle, then {@code F} must be a method handle which
     * takes exactly one argument.  The type of {@code F}'s sole argument
     * replaces the corresponding argument type of the target
     * in the resulting adapted method handle.
     * The return type of {@code F} must be identical to the corresponding
     * parameter type of the target.
     * <p>
     * It is an error if there are elements of {@code filters}
     * which do not correspond to argument positions in the target.
     * <b>Example:</b>
     * <p><blockquote><pre>
import static java.dyn.MethodHandles.*;
import static java.dyn.MethodType.*;
...
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
MethodHandle upcase = lookup().findVirtual(String.class,
  "toUpperCase", methodType(String.class));
assertEquals("xy", (String) cat.invokeExact("x", "y"));
MethodHandle f0 = filterArguments(cat, 0, upcase);
assertEquals("Xy", (String) f0.invokeExact("x", "y")); // Xy
MethodHandle f1 = filterArguments(cat, 1, upcase);
assertEquals("xY", (String) f1.invokeExact("x", "y")); // xY
MethodHandle f2 = filterArguments(cat, 0, upcase, upcase);
assertEquals("XY", (String) f2.invokeExact("x", "y")); // XY
     * </pre></blockquote>
     *
     * @param target the method handle to invoke after arguments are filtered
     * @param pos the position of the first argument to filter
     * @param filters method handles to call initially on filtered arguments
     * @return method handle which incorporates the specified argument filtering logic
     * @throws NullPointerException if the {@code target} argument is null
     *                              or if the {@code filters} array is null
     * @throws IllegalArgumentException if a non-null element of {@code filters}
     *          does not match a corresponding argument type of {@code target} as described above,
     *          or if the {@code pos+filters.length} is greater than {@code target.type().parameterCount()}
     */
    public static
    MethodHandle filterArguments(MethodHandle target, int pos, MethodHandle... filters) {
        MethodType targetType = target.type();
        MethodHandle adapter = target;
        MethodType adapterType = targetType;
        int maxPos = targetType.parameterCount();
        if (pos + filters.length > maxPos)
            throw newIllegalArgumentException("too many filters");
        int curPos = pos-1;  // pre-incremented
        for (MethodHandle filter : filters) {
            curPos += 1;
            if (filter == null)  continue;  // ignore null elements of filters
            MethodType filterType = filter.type();
            if (filterType.parameterCount() != 1
                || filterType.returnType() != targetType.parameterType(curPos))
                throw newIllegalArgumentException("target and filter types do not match");
            adapterType = adapterType.changeParameterType(curPos, filterType.parameterType(0));
            adapter = MethodHandleImpl.filterArgument(IMPL_TOKEN, adapter, curPos, filter);
        }
        MethodType midType = adapter.type();
        if (midType != adapterType)
            adapter = MethodHandleImpl.convertArguments(IMPL_TOKEN, adapter, adapterType, midType, null);
        return adapter;
    }

    /**
     * Adapt a target method handle {@code target} by post-processing
     * its return value with a unary filter function.
     * <p>
     * If a filter {@code F} applies to the return value of
     * the target method handle, then {@code F} must be a method handle which
     * takes exactly one argument.  The return type of {@code F}
     * replaces the return type of the target
     * in the resulting adapted method handle.
     * The argument type of {@code F} must be identical to the
     * return type of the target.
     * <b>Example:</b>
     * <p><blockquote><pre>
import static java.dyn.MethodHandles.*;
import static java.dyn.MethodType.*;
...
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
MethodHandle length = lookup().findVirtual(String.class,
  "length", methodType(int.class));
System.out.println((String) cat.invokeExact("x", "y")); // xy
MethodHandle f0 = filterReturnValue(cat, length);
System.out.println((int) f0.invokeExact("x", "y")); // 2
     * </pre></blockquote>
     * @param target the method handle to invoke before filtering the return value
     * @param filter method handle to call on the return value
     * @return method handle which incorporates the specified return value filtering logic
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if {@code filter}
     *          does not match the return type of {@code target} as described above
     */
    public static
    MethodHandle filterReturnValue(MethodHandle target, MethodHandle filter) {
        MethodType targetType = target.type();
        MethodType filterType = filter.type();
        if (filterType.parameterCount() != 1
            || filterType.parameterType(0) != targetType.returnType())
            throw newIllegalArgumentException("target and filter types do not match");
        // result = fold( lambda(retval, arg...) { filter(retval) },
        //                lambda(        arg...) { target(arg...) } )
        // FIXME: Too many nodes here.
        MethodHandle returner = dropArguments(filter, 1, targetType.parameterList());
        return foldArguments(returner, target);
    }

    /**
     * Adapt a target method handle {@code target} by pre-processing
     * some of its arguments, and then calling the target with
     * the result of the pre-processing, plus all original arguments.
     * <p>
     * The pre-processing is performed by a second method handle, the {@code combiner}.
     * The first {@code N} arguments passed to the adapter,
     * are copied to the combiner, which then produces a result.
     * (Here, {@code N} is defined as the parameter count of the adapter.)
     * After this, control passes to the {@code target}, with both the result
     * of the combiner, and all the original incoming arguments.
     * <p>
     * The first argument type of the target must be identical with the
     * return type of the combiner.
     * The resulting adapter is the same type as the target, except that the
     * initial argument type of the target is dropped.
     * <p>
     * (Note that {@link #dropArguments(MethodHandle,int,List) dropArguments} can be used to remove any arguments
     * that either the {@code combiner} or {@code target} does not wish to receive.
     * If some of the incoming arguments are destined only for the combiner,
     * consider using {@link MethodHandle#asCollector asCollector} instead, since those
     * arguments will not need to be live on the stack on entry to the
     * target.)
     * <p>
     * The first argument of the target must be identical with the
     * return value of the combiner.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * // there are N arguments in the A sequence
     * T target(V, A[N]..., B...);
     * V combiner(A...);
     * T adapter(A... a, B... b) {
     *   V v = combiner(a...);
     *   return target(v, a..., b...);
     * }
     * </pre></blockquote>
     * @param target the method handle to invoke after arguments are combined
     * @param combiner method handle to call initially on the incoming arguments
     * @return method handle which incorporates the specified argument folding logic
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if the first argument type of
     *          {@code target} is not the same as {@code combiner}'s return type,
     *          or if the following argument types of {@code target}
     *          are not identical with the argument types of {@code combiner}
     */
    public static
    MethodHandle foldArguments(MethodHandle target, MethodHandle combiner) {
        MethodType targetType = target.type();
        MethodType combinerType = combiner.type();
        int foldArgs = combinerType.parameterCount();
        boolean ok = (targetType.parameterCount() >= 1 + foldArgs);
        if (ok && !combinerType.parameterList().equals(targetType.parameterList().subList(1, foldArgs+1)))
            ok = false;
        if (ok && !combinerType.returnType().equals(targetType.parameterType(0)))
            ok = false;
        if (!ok)
            throw misMatchedTypes("target and combiner types", targetType, combinerType);
        MethodType newType = targetType.dropParameterTypes(0, 1);
        return MethodHandleImpl.foldArguments(IMPL_TOKEN, target, newType, combiner);
    }

    /**
     * Make a method handle which adapts a target method handle,
     * by guarding it with a test, a boolean-valued method handle.
     * If the guard fails, a fallback handle is called instead.
     * All three method handles must have the same corresponding
     * argument and return types, except that the return type
     * of the test must be boolean, and the test is allowed
     * to have fewer arguments than the other two method handles.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * boolean test(A...);
     * T target(A...,B...);
     * T fallback(A...,B...);
     * T adapter(A... a,B... b) {
     *   if (test(a...))
     *     return target(a..., b...);
     *   else
     *     return fallback(a..., b...);
     * }
     * </pre></blockquote>
     * Note that the test arguments ({@code a...} in the pseudocode) cannot
     * be modified by execution of the test, and so are passed unchanged
     * from the caller to the target or fallback as appropriate.
     * @param test method handle used for test, must return boolean
     * @param target method handle to call if test passes
     * @param fallback method handle to call if test fails
     * @return method handle which incorporates the specified if/then/else logic
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code test} does not return boolean,
     *          or if all three method types do not match (with the return
     *          type of {@code test} changed to match that of {@code target}).
     */
    public static
    MethodHandle guardWithTest(MethodHandle test,
                               MethodHandle target,
                               MethodHandle fallback) {
        MethodType gtype = test.type();
        MethodType ttype = target.type();
        MethodType ftype = fallback.type();
        if (!ttype.equals(ftype))
            throw misMatchedTypes("target and fallback types", ttype, ftype);
        if (gtype.returnType() != boolean.class)
            throw newIllegalArgumentException("guard type is not a predicate "+gtype);
        List<Class<?>> targs = ttype.parameterList();
        List<Class<?>> gargs = gtype.parameterList();
        if (!targs.equals(gargs)) {
            int gpc = gargs.size(), tpc = targs.size();
            if (gpc >= tpc || !targs.subList(0, gpc).equals(gargs))
                throw misMatchedTypes("target and test types", ttype, gtype);
            test = dropArguments(test, gpc, targs.subList(gpc, tpc));
            gtype = test.type();
        }
        return MethodHandleImpl.makeGuardWithTest(IMPL_TOKEN, test, target, fallback);
    }

    static RuntimeException misMatchedTypes(String what, MethodType t1, MethodType t2) {
        return newIllegalArgumentException(what + " must match: " + t1 + " != " + t2);
    }

    /**
     * Make a method handle which adapts a target method handle,
     * by running it inside an exception handler.
     * If the target returns normally, the adapter returns that value.
     * If an exception matching the specified type is thrown, the fallback
     * handle is called instead on the exception, plus the original arguments.
     * <p>
     * The target and handler must have the same corresponding
     * argument and return types, except that handler may omit trailing arguments
     * (similarly to the predicate in {@link #guardWithTest guardWithTest}).
     * Also, the handler must have an extra leading parameter of {@code exType} or a supertype.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * T target(A..., B...);
     * T handler(ExType, A...);
     * T adapter(A... a, B... b) {
     *   try {
     *     return target(a..., b...);
     *   } catch (ExType ex) {
     *     return handler(ex, a...);
     *   }
     * }
     * </pre></blockquote>
     * Note that the saved arguments ({@code a...} in the pseudocode) cannot
     * be modified by execution of the target, and so are passed unchanged
     * from the caller to the handler, if the handler is invoked.
     * <p>
     * The target and handler must return the same type, even if the handler
     * always throws.  (This might happen, for instance, because the handler
     * is simulating a {@code finally} clause).
     * To create such a throwing handler, compose the handler creation logic
     * with {@link #throwException throwException},
     * in order to create a method handle of the correct return type.
     * @param target method handle to call
     * @param exType the type of exception which the handler will catch
     * @param handler method handle to call if a matching exception is thrown
     * @return method handle which incorporates the specified try/catch logic
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code handler} does not accept
     *          the given exception type, or if the method handle types do
     *          not match in their return types and their
     *          corresponding parameters
     */
    public static
    MethodHandle catchException(MethodHandle target,
                                Class<? extends Throwable> exType,
                                MethodHandle handler) {
        MethodType ttype = target.type();
        MethodType htype = handler.type();
        if (htype.parameterCount() < 1 ||
            !htype.parameterType(0).isAssignableFrom(exType))
            throw newIllegalArgumentException("handler does not accept exception type "+exType);
        if (htype.returnType() != ttype.returnType())
            throw misMatchedTypes("target and handler return types", ttype, htype);
        List<Class<?>> targs = ttype.parameterList();
        List<Class<?>> hargs = htype.parameterList();
        hargs = hargs.subList(1, hargs.size());  // omit leading parameter from handler
        if (!targs.equals(hargs)) {
            int hpc = hargs.size(), tpc = targs.size();
            if (hpc >= tpc || !targs.subList(0, hpc).equals(hargs))
                throw misMatchedTypes("target and handler types", ttype, htype);
            handler = dropArguments(handler, hpc, hargs.subList(hpc, tpc));
            htype = handler.type();
        }
        return MethodHandleImpl.makeGuardWithCatch(IMPL_TOKEN, target, exType, handler);
    }

    /**
     * Produces a method handle which will throw exceptions of the given {@code exType}.
     * The method handle will accept a single argument of {@code exType},
     * and immediately throw it as an exception.
     * The method type will nominally specify a return of {@code returnType}.
     * The return type may be anything convenient:  It doesn't matter to the
     * method handle's behavior, since it will never return normally.
     * @return method handle which can throw the given exceptions
     * @throws NullPointerException if either argument is null
     */
    public static
    MethodHandle throwException(Class<?> returnType, Class<? extends Throwable> exType) {
        return MethodHandleImpl.throwException(IMPL_TOKEN, MethodType.methodType(returnType, exType));
    }

    /**
     * Produces an instance of the given "SAM" interface which redirects
     * its calls to the given method handle.
     * <p>
     * A SAM interface is an interface which declares a single abstract method.
     * When determining the unique abstract method of a SAM interface,
     * the public {@code Object} methods ({@code toString}, {@code equals}, {@code hashCode})
     * are disregarded.  For example, {@link java.util.Comparator} is a SAM interface,
     * even though it re-declares the {@code Object.equals} method.
     * Also, if the SAM interface has a supertype,
     * the SAM interface may override an inherited method.
     * Any such overrides are respected, and the method handle will be accessible
     * by either the inherited method or the SAM method.
     * In particular, a {@linkplain java.lang.reflect.Method#isBridge bridge method}
     * may be created if the methods have different return types.
     * <p>
     * The type must be public.  No additional access checks are performed.
     * <p>
     * The resulting instance of the required SAM type will respond to
     * invocation of the SAM type's single abstract method by calling
     * the given {@code target} on the incoming arguments,
     * and returning or throwing whatever the {@code target}
     * returns or throws.  The invocation will be as if by
     * {@code target.invokeGeneric}.
     * The target's type will be checked before the SAM
     * instance is created, as if by a call to {@code asType},
     * which may result in a {@code WrongMethodTypeException}.
     * <p>
     * The wrapper instance will implement the requested SAM interface
     * and its super-types, but no other SAM types.
     * This means that the SAM instance will not unexpectedly
     * pass an {@code instanceof} test for any unrequested type.
     * <p style="font-size:smaller;">
     * <em>Implementation Note:</em>
     * Therefore, each SAM instance must implement a unique SAM type.
     * Implementations may not bundle together
     * multiple SAM types onto single implementation classes
     * in the style of {@link java.awt.AWTEventMulticaster}.
     * <p>
     * The method handle may throw an <em>undeclared exception</em>,
     * which means any checked exception (or other checked throwable)
     * not declared by the SAM type's single abstract method.
     * If this happens, the throwable will be wrapped in an instance of
     * {@link java.lang.reflect.UndeclaredThrowableException UndeclaredThrowableException}
     * and thrown in that wrapped form.
     * <p>
     * Like {@link java.lang.Integer#valueOf Integer.valueOf},
     * {@code asInstance} is a factory method whose results are defined
     * by their behavior.
     * It is not guaranteed to return a new instance for every call.
     * <p>
     * Future versions of this API may accept additional types,
     * such as abstract classes with single abstract methods.
     * Future versions of this API may also equip wrapper instances
     * with one or more additional public "marker" interfaces.
     *
     * @param target the method handle to invoke from the wrapper
     * @param samType the desired type of the wrapper, a SAM type
     * @return a correctly-typed wrapper for the given {@code target}
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if the {@code samType} is not a
     *         valid argument to this method
     * @throws WrongMethodTypeException if the {@code target} cannot
     *         be converted to the type required by the SAM type
     */
    // Other notes to implementors:
    // <p>
    // No stable mapping is promised between the SAM type and
    // the implementation class C.  Over time, several implementation
    // classes might be used for the same SAM type.
    // <p>
    // If the implementation is able
    // to prove that a wrapper of the required SAM type
    // has already been created for a given
    // method handle, or for another method handle with the
    // same behavior, the implementation may return that wrapper in place of
    // a new wrapper.
    // <p>
    // This method is designed to apply to common use cases
    // where a single method handle must interoperate with
    // an interface that implements a function-like
    // API.  Additional variations, such as SAM classes with
    // private constructors, or interfaces with multiple but related
    // entry points, must be covered by hand-written or automatically
    // generated adapter classes.
    //
    public static
    <T> T asInstance(final MethodHandle target, final Class<T> samType) {
        // POC implementation only; violates the above contract several ways
        final Method sam = getSamMethod(samType);
        if (sam == null)
            throw new IllegalArgumentException("not a SAM type: "+samType.getName());
        MethodType samMT = MethodType.methodType(sam.getReturnType(), sam.getParameterTypes());
        MethodHandle checkTarget = target.asType(samMT);  // make throw WMT
        checkTarget = checkTarget.asType(checkTarget.type().changeReturnType(Object.class));
        final MethodHandle vaTarget = checkTarget.asSpreader(Object[].class, samMT.parameterCount());
        return samType.cast(Proxy.newProxyInstance(
                samType.getClassLoader(),
                new Class[]{ samType, WrapperInstance.class },
                new InvocationHandler() {
                    private Object getArg(String name) {
                        if ((Object)name == "getWrapperInstanceTarget")  return target;
                        if ((Object)name == "getWrapperInstanceType")    return samType;
                        throw new AssertionError();
                    }
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getDeclaringClass() == WrapperInstance.class)
                            return getArg(method.getName());
                        if (method.equals(sam))
                            return vaTarget.invokeExact(args);
                        if (isObjectMethod(method))
                            return callObjectMethod(this, method, args);
                        throw new InternalError();
                    }
                }));
    }

    /**
     * Determine if the given object was produced by a call to {@link #asInstance asInstance}.
     * @param x any reference
     * @return true if the reference is not null and points to an object produced by {@code asInstance}
     */
    public static
    boolean isWrapperInstance(Object x) {
        return x instanceof WrapperInstance;
    }

    private static WrapperInstance asWrapperInstance(Object x) {
        try {
            if (x != null)
                return (WrapperInstance) x;
        } catch (ClassCastException ex) {
        }
        throw new IllegalArgumentException("not a wrapper instance");
    }

    /**
     * Produces or recovers a target method handle which is behaviorally
     * equivalent to the SAM method of this wrapper instance.
     * The object {@code x} must have been produced by a call to {@link #asInstance asInstance}.
     * This requirement may be tested via {@link #isWrapperInstance isWrapperInstance}.
     * @param x any reference
     * @return a method handle implementing the SAM method
     * @throws IllegalArgumentException if the reference x is not to a wrapper instance
     */
    public static
    MethodHandle wrapperInstanceTarget(Object x) {
        return asWrapperInstance(x).getWrapperInstanceTarget();
    }

    /**
     * Recover the SAM type for which this wrapper instance was created.
     * The object {@code x} must have been produced by a call to {@link #asInstance asInstance}.
     * This requirement may be tested via {@link #isWrapperInstance isWrapperInstance}.
     * @param x any reference
     * @return the SAM type for which the wrapper was created
     * @throws IllegalArgumentException if the reference x is not to a wrapper instance
     */
    public static
    Class<?> wrapperInstanceType(Object x) {
        return asWrapperInstance(x).getWrapperInstanceType();
    }

    private static
    boolean isObjectMethod(Method m) {
        switch (m.getName()) {
        case "toString":
            return (m.getReturnType() == String.class
                    && m.getParameterTypes().length == 0);
        case "hashCode":
            return (m.getReturnType() == int.class
                    && m.getParameterTypes().length == 0);
        case "equals":
            return (m.getReturnType() == boolean.class
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == Object.class);
        }
        return false;
    }

    private static
    Object callObjectMethod(Object self, Method m, Object[] args) {
        assert(isObjectMethod(m)) : m;
        switch (m.getName()) {
        case "toString":
            return self.getClass().getName() + "@" + Integer.toHexString(self.hashCode());
        case "hashCode":
            return System.identityHashCode(self);
        case "equals":
            return (self == args[0]);
        }
        return null;
    }

    private static
    Method getSamMethod(Class<?> samType) {
        Method sam = null;
        for (Method m : samType.getMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isAbstract(mod)) {
                if (sam != null && !isObjectMethod(sam))
                    return null;  // too many abstract methods
                sam = m;
            }
        }
        if (!samType.isInterface() && getSamConstructor(samType) == null)
            return null;  // wrong kind of constructor
        return sam;
    }

    private static
    Constructor getSamConstructor(Class<?> samType) {
        for (Constructor c : samType.getDeclaredConstructors()) {
            if (c.getParameterTypes().length == 0) {
                int mod = c.getModifiers();
                if (Modifier.isPublic(mod) || Modifier.isProtected(mod))
                    return c;
            }
        }
        return null;
    }

    /*non-public*/
    static MethodHandle asVarargsCollector(MethodHandle target, Class<?> arrayType) {
        return MethodHandleImpl.asVarargsCollector(IMPL_TOKEN, target, arrayType);
    }
}
