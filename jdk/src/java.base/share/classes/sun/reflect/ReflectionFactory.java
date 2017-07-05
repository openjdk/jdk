/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import sun.reflect.misc.ReflectUtil;

/** <P> The master factory for all reflective objects, both those in
    java.lang.reflect (Fields, Methods, Constructors) as well as their
    delegates (FieldAccessors, MethodAccessors, ConstructorAccessors).
    </P>

    <P> The methods in this class are extremely unsafe and can cause
    subversion of both the language and the verifier. For this reason,
    they are all instance methods, and access to the constructor of
    this factory is guarded by a security check, in similar style to
    {@link sun.misc.Unsafe}. </P>
*/

public class ReflectionFactory {

    private static boolean initted = false;
    private static final Permission reflectionFactoryAccessPerm
        = new RuntimePermission("reflectionFactoryAccess");
    private static final ReflectionFactory soleInstance = new ReflectionFactory();
    // Provides access to package-private mechanisms in java.lang.reflect
    private static volatile LangReflectAccess langReflectAccess;

    //
    // "Inflation" mechanism. Loading bytecodes to implement
    // Method.invoke() and Constructor.newInstance() currently costs
    // 3-4x more than an invocation via native code for the first
    // invocation (though subsequent invocations have been benchmarked
    // to be over 20x faster). Unfortunately this cost increases
    // startup time for certain applications that use reflection
    // intensively (but only once per class) to bootstrap themselves.
    // To avoid this penalty we reuse the existing JVM entry points
    // for the first few invocations of Methods and Constructors and
    // then switch to the bytecode-based implementations.
    //
    // Package-private to be accessible to NativeMethodAccessorImpl
    // and NativeConstructorAccessorImpl
    private static boolean noInflation        = false;
    private static int     inflationThreshold = 15;

    private ReflectionFactory() {
    }

    /**
     * A convenience class for acquiring the capability to instantiate
     * reflective objects.  Use this instead of a raw call to {@link
     * #getReflectionFactory} in order to avoid being limited by the
     * permissions of your callers.
     *
     * <p>An instance of this class can be used as the argument of
     * <code>AccessController.doPrivileged</code>.
     */
    public static final class GetReflectionFactoryAction
        implements PrivilegedAction<ReflectionFactory> {
        public ReflectionFactory run() {
            return getReflectionFactory();
        }
    }

    /**
     * Provides the caller with the capability to instantiate reflective
     * objects.
     *
     * <p> First, if there is a security manager, its
     * <code>checkPermission</code> method is called with a {@link
     * java.lang.RuntimePermission} with target
     * <code>"reflectionFactoryAccess"</code>.  This may result in a
     * security exception.
     *
     * <p> The returned <code>ReflectionFactory</code> object should be
     * carefully guarded by the caller, since it can be used to read and
     * write private data and invoke private methods, as well as to load
     * unverified bytecodes.  It must never be passed to untrusted code.
     *
     * @exception SecurityException if a security manager exists and its
     *             <code>checkPermission</code> method doesn't allow
     *             access to the RuntimePermission "reflectionFactoryAccess".  */
    public static ReflectionFactory getReflectionFactory() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            // TO DO: security.checkReflectionFactoryAccess();
            security.checkPermission(reflectionFactoryAccessPerm);
        }
        return soleInstance;
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by java.lang.reflect
    //
    //

    /** Called only by java.lang.reflect.Modifier's static initializer */
    public void setLangReflectAccess(LangReflectAccess access) {
        langReflectAccess = access;
    }

    /**
     * Note: this routine can cause the declaring class for the field
     * be initialized and therefore must not be called until the
     * first get/set of this field.
     * @param field the field
     * @param override true if caller has overridden accessibility
     */
    public FieldAccessor newFieldAccessor(Field field, boolean override) {
        checkInitted();
        return UnsafeFieldAccessorFactory.newFieldAccessor(field, override);
    }

    public MethodAccessor newMethodAccessor(Method method) {
        checkInitted();

        if (noInflation && !ReflectUtil.isVMAnonymousClass(method.getDeclaringClass())) {
            return new MethodAccessorGenerator().
                generateMethod(method.getDeclaringClass(),
                               method.getName(),
                               method.getParameterTypes(),
                               method.getReturnType(),
                               method.getExceptionTypes(),
                               method.getModifiers());
        } else {
            NativeMethodAccessorImpl acc =
                new NativeMethodAccessorImpl(method);
            DelegatingMethodAccessorImpl res =
                new DelegatingMethodAccessorImpl(acc);
            acc.setParent(res);
            return res;
        }
    }

    public ConstructorAccessor newConstructorAccessor(Constructor<?> c) {
        checkInitted();

        Class<?> declaringClass = c.getDeclaringClass();
        if (Modifier.isAbstract(declaringClass.getModifiers())) {
            return new InstantiationExceptionConstructorAccessorImpl(null);
        }
        if (declaringClass == Class.class) {
            return new InstantiationExceptionConstructorAccessorImpl
                ("Can not instantiate java.lang.Class");
        }
        // Bootstrapping issue: since we use Class.newInstance() in
        // the ConstructorAccessor generation process, we have to
        // break the cycle here.
        if (Reflection.isSubclassOf(declaringClass,
                                    ConstructorAccessorImpl.class)) {
            return new BootstrapConstructorAccessorImpl(c);
        }

        if (noInflation && !ReflectUtil.isVMAnonymousClass(c.getDeclaringClass())) {
            return new MethodAccessorGenerator().
                generateConstructor(c.getDeclaringClass(),
                                    c.getParameterTypes(),
                                    c.getExceptionTypes(),
                                    c.getModifiers());
        } else {
            NativeConstructorAccessorImpl acc =
                new NativeConstructorAccessorImpl(c);
            DelegatingConstructorAccessorImpl res =
                new DelegatingConstructorAccessorImpl(acc);
            acc.setParent(res);
            return res;
        }
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by java.lang
    //
    //

    /** Creates a new java.lang.reflect.Field. Access checks as per
        java.lang.reflect.AccessibleObject are not overridden. */
    public Field newField(Class<?> declaringClass,
                          String name,
                          Class<?> type,
                          int modifiers,
                          int slot,
                          String signature,
                          byte[] annotations)
    {
        return langReflectAccess().newField(declaringClass,
                                            name,
                                            type,
                                            modifiers,
                                            slot,
                                            signature,
                                            annotations);
    }

    /** Creates a new java.lang.reflect.Method. Access checks as per
        java.lang.reflect.AccessibleObject are not overridden. */
    public Method newMethod(Class<?> declaringClass,
                            String name,
                            Class<?>[] parameterTypes,
                            Class<?> returnType,
                            Class<?>[] checkedExceptions,
                            int modifiers,
                            int slot,
                            String signature,
                            byte[] annotations,
                            byte[] parameterAnnotations,
                            byte[] annotationDefault)
    {
        return langReflectAccess().newMethod(declaringClass,
                                             name,
                                             parameterTypes,
                                             returnType,
                                             checkedExceptions,
                                             modifiers,
                                             slot,
                                             signature,
                                             annotations,
                                             parameterAnnotations,
                                             annotationDefault);
    }

    /** Creates a new java.lang.reflect.Constructor. Access checks as
        per java.lang.reflect.AccessibleObject are not overridden. */
    public Constructor<?> newConstructor(Class<?> declaringClass,
                                         Class<?>[] parameterTypes,
                                         Class<?>[] checkedExceptions,
                                         int modifiers,
                                         int slot,
                                         String signature,
                                         byte[] annotations,
                                         byte[] parameterAnnotations)
    {
        return langReflectAccess().newConstructor(declaringClass,
                                                  parameterTypes,
                                                  checkedExceptions,
                                                  modifiers,
                                                  slot,
                                                  signature,
                                                  annotations,
                                                  parameterAnnotations);
    }

    /** Gets the MethodAccessor object for a java.lang.reflect.Method */
    public MethodAccessor getMethodAccessor(Method m) {
        return langReflectAccess().getMethodAccessor(m);
    }

    /** Sets the MethodAccessor object for a java.lang.reflect.Method */
    public void setMethodAccessor(Method m, MethodAccessor accessor) {
        langReflectAccess().setMethodAccessor(m, accessor);
    }

    /** Gets the ConstructorAccessor object for a
        java.lang.reflect.Constructor */
    public ConstructorAccessor getConstructorAccessor(Constructor<?> c) {
        return langReflectAccess().getConstructorAccessor(c);
    }

    /** Sets the ConstructorAccessor object for a
        java.lang.reflect.Constructor */
    public void setConstructorAccessor(Constructor<?> c,
                                       ConstructorAccessor accessor)
    {
        langReflectAccess().setConstructorAccessor(c, accessor);
    }

    /** Makes a copy of the passed method. The returned method is a
        "child" of the passed one; see the comments in Method.java for
        details. */
    public Method copyMethod(Method arg) {
        return langReflectAccess().copyMethod(arg);
    }

    /** Makes a copy of the passed field. The returned field is a
        "child" of the passed one; see the comments in Field.java for
        details. */
    public Field copyField(Field arg) {
        return langReflectAccess().copyField(arg);
    }

    /** Makes a copy of the passed constructor. The returned
        constructor is a "child" of the passed one; see the comments
        in Constructor.java for details. */
    public <T> Constructor<T> copyConstructor(Constructor<T> arg) {
        return langReflectAccess().copyConstructor(arg);
    }

    /** Gets the byte[] that encodes TypeAnnotations on an executable.
     */
    public byte[] getExecutableTypeAnnotationBytes(Executable ex) {
        return langReflectAccess().getExecutableTypeAnnotationBytes(ex);
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by serialization
    //
    //

    public Constructor<?> newConstructorForSerialization
        (Class<?> classToInstantiate, Constructor<?> constructorToCall)
    {
        // Fast path
        if (constructorToCall.getDeclaringClass() == classToInstantiate) {
            return constructorToCall;
        }

        ConstructorAccessor acc = new MethodAccessorGenerator().
            generateSerializationConstructor(classToInstantiate,
                                             constructorToCall.getParameterTypes(),
                                             constructorToCall.getExceptionTypes(),
                                             constructorToCall.getModifiers(),
                                             constructorToCall.getDeclaringClass());
        Constructor<?> c = newConstructor(constructorToCall.getDeclaringClass(),
                                          constructorToCall.getParameterTypes(),
                                          constructorToCall.getExceptionTypes(),
                                          constructorToCall.getModifiers(),
                                          langReflectAccess().
                                          getConstructorSlot(constructorToCall),
                                          langReflectAccess().
                                          getConstructorSignature(constructorToCall),
                                          langReflectAccess().
                                          getConstructorAnnotations(constructorToCall),
                                          langReflectAccess().
                                          getConstructorParameterAnnotations(constructorToCall));
        setConstructorAccessor(c, acc);
        return c;
    }

    //--------------------------------------------------------------------------
    //
    // Internals only below this point
    //

    static int inflationThreshold() {
        return inflationThreshold;
    }

    /** We have to defer full initialization of this class until after
        the static initializer is run since java.lang.reflect.Method's
        static initializer (more properly, that for
        java.lang.reflect.AccessibleObject) causes this class's to be
        run, before the system properties are set up. */
    private static void checkInitted() {
        if (initted) return;
        AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                public Void run() {
                    // Tests to ensure the system properties table is fully
                    // initialized. This is needed because reflection code is
                    // called very early in the initialization process (before
                    // command-line arguments have been parsed and therefore
                    // these user-settable properties installed.) We assume that
                    // if System.out is non-null then the System class has been
                    // fully initialized and that the bulk of the startup code
                    // has been run.

                    if (System.out == null) {
                        // java.lang.System not yet fully initialized
                        return null;
                    }

                    String val = System.getProperty("sun.reflect.noInflation");
                    if (val != null && val.equals("true")) {
                        noInflation = true;
                    }

                    val = System.getProperty("sun.reflect.inflationThreshold");
                    if (val != null) {
                        try {
                            inflationThreshold = Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            throw new RuntimeException("Unable to parse property sun.reflect.inflationThreshold", e);
                        }
                    }

                    initted = true;
                    return null;
                }
            });
    }

    private static LangReflectAccess langReflectAccess() {
        if (langReflectAccess == null) {
            // Call a static method to get class java.lang.reflect.Modifier
            // initialized. Its static initializer will cause
            // setLangReflectAccess() to be called from the context of the
            // java.lang.reflect package.
            Modifier.isPublic(Modifier.PUBLIC);
        }
        return langReflectAccess;
    }
}
