/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.security.AccessController;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.reflect.ReflectionFactory;

/**
 * The AccessibleObject class is the base class for Field, Method and
 * Constructor objects.  It provides the ability to flag a reflected
 * object as suppressing default Java language access control checks
 * when it is used. The access checks -- <em>module boundaries</em>,
 * public, default (package) access, protected, and private members --
 * are performed when Fields, Methods or Constructors are used to set
 * or get fields, to invoke methods or to create and initialize new
 * instances of classes, respectively. Unlike access control specified
 * in the <cite>The Java&trade; Language Specification</cite> and
 * <cite>The Java Virtual Machine Specification</cite>, access checks
 * with reflected objects assume {@link Module#canRead readability}.
 *
 * <p>Setting the {@code accessible} flag in a reflected object
 * permits sophisticated applications with sufficient privilege, such
 * as Java Object Serialization or other persistence mechanisms, to
 * manipulate objects in a manner that would normally be prohibited.
 *
 * <p>By default, a reflected object is <em>not</em> accessible.
 *
 * @see Field
 * @see Method
 * @see Constructor
 * @see ReflectPermission
 *
 * @since 1.2
 */
public class AccessibleObject implements AnnotatedElement {

    /**
     * The Permission object that is used to check whether a client
     * has sufficient privilege to defeat Java language access
     * control checks.
     */
    private static final java.security.Permission ACCESS_PERMISSION =
        new ReflectPermission("suppressAccessChecks");

    static void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(ACCESS_PERMISSION);
    }

    /**
     * Convenience method to set the {@code accessible} flag for an
     * array of objects with a single security check (for efficiency).
     *
     * <p>This method cannot be used to enable access to an object that is a
     * {@link Member member} of a class in a different module to the caller and
     * where the class is in a package that is not exported to the caller's
     * module. Additionally, if the member is non-public or its declaring
     * class is non-public, then this method can only be used to enable access
     * if the package is {@link Module#isOpen(String,Module) open} to at least
     * the caller's module.
     *
     * <p>If there is a security manager, its
     * {@code checkPermission} method is first called with a
     * {@code ReflectPermission("suppressAccessChecks")} permission.
     *
     * <p>A {@code SecurityException} is also thrown if any of the elements of
     * the input {@code array} is a {@link java.lang.reflect.Constructor}
     * object for the class {@code java.lang.Class} and {@code flag} is true.
     *
     * @param array the array of AccessibleObjects
     * @param flag  the new value for the {@code accessible} flag
     *              in each object
     * @throws InaccessibleObjectException if access cannot be enabled
     * @throws SecurityException if the request is denied.
     * @see SecurityManager#checkPermission
     * @see ReflectPermission
     */
    @CallerSensitive
    public static void setAccessible(AccessibleObject[] array, boolean flag) {
        checkPermission();
        if (flag) {
            Class<?> caller = Reflection.getCallerClass();
            array = array.clone();
            for (AccessibleObject ao : array) {
                ao.checkCanSetAccessible(caller);
            }
        }
        for (AccessibleObject ao : array) {
            ao.setAccessible0(flag);
        }
    }

    /**
     * Set the {@code accessible} flag for this object to
     * the indicated boolean value.  A value of {@code true} indicates that
     * the reflected object should suppress Java language access
     * checking when it is used.  A value of {@code false} indicates
     * that the reflected object should enforce Java language access checks
     * while assuming readability (as noted in the class description).
     *
     * <p>This method cannot be used to enable access to an object that is a
     * {@link Member member} of a class in a different module to the caller and
     * where the class is in a package that is not exported to the caller's
     * module. Additionally, if the member is non-public or its declaring
     * class is non-public, then this method can only be used to enable access
     * if the package is {@link Module#isOpen(String,Module) open} to at least
     * the caller's module.
     *
     * <p>If there is a security manager, its
     * {@code checkPermission} method is first called with a
     * {@code ReflectPermission("suppressAccessChecks")} permission.
     *
     * @param flag the new value for the {@code accessible} flag
     * @throws InaccessibleObjectException if access cannot be enabled
     * @throws SecurityException if the request is denied
     * @see SecurityManager#checkPermission
     * @see ReflectPermission
     * @see java.lang.invoke.MethodHandles#privateLookupIn
     */
    public void setAccessible(boolean flag) {
        AccessibleObject.checkPermission();
        setAccessible0(flag);
    }

    void setAccessible0(boolean flag) {
        this.override = flag;
    }

   /**
    * If the given AccessibleObject is a {@code Constructor}, {@code Method}
    * or {@code Field} then checks that its declaring class is in a package
    * that can be accessed by the given caller of setAccessible.
    */
    void checkCanSetAccessible(Class<?> caller) {
        // do nothing, needs to be overridden by Constructor, Method, Field
    }

    void checkCanSetAccessible(Class<?> caller, Class<?> declaringClass) {
        Module callerModule = caller.getModule();
        Module declaringModule = declaringClass.getModule();

        if (callerModule == declaringModule) return;
        if (callerModule == Object.class.getModule()) return;
        if (!declaringModule.isNamed()) return;

        // package is open to caller
        String pn = packageName(declaringClass);
        if (declaringModule.isOpen(pn, callerModule))
            return;

        // package is exported to caller and class/member is public
        boolean isExported = declaringModule.isExported(pn, callerModule);
        boolean isClassPublic = Modifier.isPublic(declaringClass.getModifiers());
        int modifiers;
        if (this instanceof Executable) {
            modifiers = ((Executable) this).getModifiers();
        } else {
            modifiers = ((Field) this).getModifiers();
        }
        boolean isMemberPublic = Modifier.isPublic(modifiers);
        if (isExported && isClassPublic && isMemberPublic)
            return;

        // not accessible
        String msg = "Unable to make ";
        if (this instanceof Field)
            msg += "field ";
        msg += this + " accessible: " + declaringModule + " does not \"";
        if (isClassPublic && isMemberPublic)
            msg += "exports";
        else
            msg += "opens";
        msg += " " + pn + "\" to " + callerModule;
        Reflection.throwInaccessibleObjectException(msg);
    }

    /**
     * Returns the package name of the given class.
     */
    private static String packageName(Class<?> c) {
        while (c.isArray()) {
            c = c.getComponentType();
        }
        String pn = c.getPackageName();
        return (pn != null) ? pn : "";
    }

    /**
     * Get the value of the {@code accessible} flag for this object.
     *
     * @return the value of the object's {@code accessible} flag
     */
    public boolean isAccessible() {
        return override;
    }

    /**
     * Constructor: only used by the Java Virtual Machine.
     */
    protected AccessibleObject() {}

    // Indicates whether language-level access checks are overridden
    // by this object. Initializes to "false". This field is used by
    // Field, Method, and Constructor.
    //
    // NOTE: for security purposes, this field must not be visible
    // outside this package.
    boolean override;

    // Reflection factory used by subclasses for creating field,
    // method, and constructor accessors. Note that this is called
    // very early in the bootstrapping process.
    static final ReflectionFactory reflectionFactory =
        AccessController.doPrivileged(
            new ReflectionFactory.GetReflectionFactoryAction());

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw new AssertionError("All subclasses should override this method");
    }

    /**
     * {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return AnnotatedElement.super.isAnnotationPresent(annotationClass);
    }

   /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        throw new AssertionError("All subclasses should override this method");
    }

    /**
     * @since 1.5
     */
    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        // Only annotations on classes are inherited, for all other
        // objects getDeclaredAnnotation is the same as
        // getAnnotation.
        return getAnnotation(annotationClass);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        // Only annotations on classes are inherited, for all other
        // objects getDeclaredAnnotationsByType is the same as
        // getAnnotationsByType.
        return getAnnotationsByType(annotationClass);
    }

    /**
     * @since 1.5
     */
    public Annotation[] getDeclaredAnnotations()  {
        throw new AssertionError("All subclasses should override this method");
    }


    // Shared access checking logic.

    // For non-public members or members in package-private classes,
    // it is necessary to perform somewhat expensive security checks.
    // If the security check succeeds for a given class, it will
    // always succeed (it is not affected by the granting or revoking
    // of permissions); we speed up the check in the common case by
    // remembering the last Class for which the check succeeded.
    //
    // The simple security check for Constructor is to see if
    // the caller has already been seen, verified, and cached.
    // (See also Class.newInstance(), which uses a similar method.)
    //
    // A more complicated security check cache is needed for Method and Field
    // The cache can be either null (empty cache), a 2-array of {caller,targetClass},
    // or a caller (with targetClass implicitly equal to memberClass).
    // In the 2-array case, the targetClass is always different from the memberClass.
    volatile Object securityCheckCache;

    final void checkAccess(Class<?> caller, Class<?> memberClass,
                           Class<?> targetClass, int modifiers)
        throws IllegalAccessException
    {
        if (caller == memberClass) {  // quick check
            return;             // ACCESS IS OK
        }
        Object cache = securityCheckCache;  // read volatile
        if (targetClass != null // instance member or constructor
            && Modifier.isProtected(modifiers)
            && targetClass != memberClass) {
            // Must match a 2-list of { caller, targetClass }.
            if (cache instanceof Class[]) {
                Class<?>[] cache2 = (Class<?>[]) cache;
                if (cache2[1] == targetClass &&
                    cache2[0] == caller) {
                    return;     // ACCESS IS OK
                }
                // (Test cache[1] first since range check for [1]
                // subsumes range check for [0].)
            }
        } else if (cache == caller) {
            // Non-protected case (or targetClass == memberClass or static member).
            return;             // ACCESS IS OK
        }

        // If no return, fall through to the slow path.
        slowCheckMemberAccess(caller, memberClass, targetClass, modifiers);
    }

    // Keep all this slow stuff out of line:
    void slowCheckMemberAccess(Class<?> caller, Class<?> memberClass,
                               Class<?> targetClass, int modifiers)
        throws IllegalAccessException
    {
        Reflection.ensureMemberAccess(caller, memberClass, targetClass, modifiers);

        // Success: Update the cache.
        Object cache = (targetClass != null
                        && Modifier.isProtected(modifiers)
                        && targetClass != memberClass)
                        ? new Class<?>[] { caller, targetClass }
                        : caller;

        // Note:  The two cache elements are not volatile,
        // but they are effectively final.  The Java memory model
        // guarantees that the initializing stores for the cache
        // elements will occur before the volatile write.
        securityCheckCache = cache;         // write volatile
    }
}
