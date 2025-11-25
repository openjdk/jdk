/*
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

package java.util.concurrent.atomic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import jdk.internal.reflect.Reflection;

/**
 * Utility class containing common field validation and access checking logic
 * shared by AtomicIntegerFieldUpdater, AtomicLongFieldUpdater, and
 * AtomicReferenceFieldUpdater implementations.
 *
 * @since 26
 */
final class FieldUpdaterUtil {

    private FieldUpdaterUtil() {}

    /**
     * Finds a field and validates it meets the requirements for atomic field updaters.
     * Ensures the caller has access, the field is volatile and not static, and for
     * primitive updaters, that the field type matches the expected type.
     *
     * @param tclass the class holding the field
     * @param fieldName the name of the field
     * @param caller the class constructing the updater
     * @param expectedType the expected field type (int.class, long.class),
     *                     or null for reference type updaters
     * @param <T> the type of instances of tclass
     * @return the validated field
     * @throws RuntimeException if the field cannot be found or accessed
     * @throws IllegalArgumentException if the field is not volatile, is static,
     *                                  or does not match the expected type
     */
    static <T> Field findValidatedField(Class<T> tclass, String fieldName,
                                        Class<?> caller, Class<?> expectedType) {
        Field field;
        int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
            Reflection.ensureMemberAccess(caller, tclass, null, modifiers);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Class<?> fieldType = field.getType();

        if (expectedType == int.class && fieldType != int.class) {
            throw new IllegalArgumentException("Must be integer type");
        } else if (expectedType == long.class && fieldType != long.class) {
            throw new IllegalArgumentException("Must be long type");
        } else if (expectedType == null && fieldType.isPrimitive()) {
            throw new IllegalArgumentException("Must be reference type");
        }

        if (!Modifier.isVolatile(modifiers)) {
            throw new IllegalArgumentException("Must be volatile type");
        }

        if (Modifier.isStatic(modifiers)) {
            throw new IllegalArgumentException("Must not be a static field");
        }

        return field;
    }

    /**
     * Computes the appropriate class for access checking based on
     * protected field access rules.
     * <p>
     * Access to protected field members is restricted to receivers only
     * of the accessing class, or one of its subclasses, and the
     * accessing class must in turn be a subclass (or package sibling)
     * of the protected member's defining class.
     * If the updater refers to a protected field of a declaring class
     * outside the current package, the receiver argument will be
     * narrowed to the type of the accessing class.
     *
     * @param tclass the class holding the field
     * @param caller the class constructing the updater
     * @param modifiers the field modifiers
     * @param <T> the type of instances of tclass
     * @return the class to use for access checks (either caller or tclass)
     */
    static <T> Class<?> computeAccessClass(Class<T> tclass, Class<?> caller, int modifiers) {
        return (Modifier.isProtected(modifiers) &&
                tclass.isAssignableFrom(caller) &&
                !isSamePackage(tclass, caller))
               ? caller : tclass;
    }

    /**
     * Returns true if the two classes have the same class loader and
     * package qualifier.
     *
     * @param class1 the first class
     * @param class2 the second class
     * @return true if both classes are in the same package
     */
    static boolean isSamePackage(Class<?> class1, Class<?> class2) {
        return class1.getClassLoader() == class2.getClassLoader()
               && class1.getPackageName() == class2.getPackageName();
    }

    /**
     * Returns true if the second classloader can be found in the first
     * classloader's delegation chain.
     * Equivalent to the inaccessible: first.isAncestor(second).
     *
     * @param first the first classloader
     * @param second the second classloader
     * @return true if second is an ancestor of first
     */
    static boolean isAncestor(ClassLoader first, ClassLoader second) {
        ClassLoader acl = first;
        do {
            acl = acl.getParent();
            if (second == acl) {
                return true;
            }
        } while (acl != null);
        return false;
    }
}
