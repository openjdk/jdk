/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.jdi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sun.jvm.hotspot.oops.ArrayKlass;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.ObjArrayKlass;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.TypeArrayKlass;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.Method;
import com.sun.jdi.PrimitiveType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import com.sun.jdi.VirtualMachine;

public class ArrayTypeImpl extends ReferenceTypeImpl implements ArrayType {
  protected ArrayTypeImpl(VirtualMachine aVm, ArrayKlass aRef) {
        super(aVm, aRef);
    }

    public ArrayReference newInstance(int length) {
        vm.throwNotReadOnlyException("ArrayType.newInstance(int)");
        return null;
    }

    public String componentSignature() {
        return signature().substring(1); // Just skip the leading '['
    }

    public String componentTypeName() {
        JNITypeParser parser = new JNITypeParser(componentSignature());
        return parser.typeName();
    }

    public ClassLoaderReference classLoader() {
        if (ref() instanceof TypeArrayKlass) {
            // primitive array klasses are loaded by bootstrap loader
            return null;
        } else {
            Klass bottomKlass = ((ObjArrayKlass)ref()).getBottomKlass();
            if (bottomKlass instanceof TypeArrayKlass) {
                // multidimensional primitive array klasses are loaded by bootstrap loader
                return null;
            } else {
                // class loader of any other obj array klass is same as the loader
                // that loaded the bottom InstanceKlass
                Instance xx = (Instance)(((InstanceKlass) bottomKlass).getClassLoader());
                return vm.classLoaderMirror(xx);
            }
        }
    }

    @Override
    void addVisibleMethods(Map<String, Method> methodMap, Set<InterfaceType> handledInterfaces) {
        // arrays don't have methods
    }

    List getAllMethods() {
        // arrays don't have methods
        // JLS says arrays have methods of java.lang.Object. But
        // JVMDI-JDI returns zero size list. We do the same here
        // for consistency.
        return new ArrayList(0);
    }

    /*
     * Find the type object, if any, of a component type of this array.
     * The component type does not have to be immediate; e.g. this method
     * can be used to find the component Foo of Foo[][].
     */
    public Type componentType() throws ClassNotLoadedException {
        ArrayKlass k = (ArrayKlass) ref();
        if (k instanceof ObjArrayKlass) {
            Klass elementKlass = ((ObjArrayKlass)k).getElementKlass();
            if (elementKlass == null) {
                throw new ClassNotLoadedException(componentSignature());
            } else {
                return vm.referenceType(elementKlass);
            }
        } else {
            // It's a primitive type
            return vm.primitiveTypeMirror(signature().charAt(1));
        }
    }

    static boolean isComponentAssignable(Type destination, Type source) {
        if (source instanceof PrimitiveType) {
            // Assignment of primitive arrays requires identical
            // component types.
            return source.equals(destination);
        } else {
           if (destination instanceof PrimitiveType) {
                return false;
            }

            ReferenceTypeImpl refSource = (ReferenceTypeImpl)source;
            ReferenceTypeImpl refDestination = (ReferenceTypeImpl)destination;
            // Assignment of object arrays requires availability
            // of widening conversion of component types
            return refSource.isAssignableTo(refDestination);
        }
    }


    /*
    * Return true if an instance of the  given reference type
    * can be assigned to a variable of this type
    */
    boolean isAssignableTo(ReferenceType destType) {
        if (destType instanceof ArrayType) {
            try {
                Type destComponentType = ((ArrayType)destType).componentType();
                return isComponentAssignable(destComponentType, componentType());
            } catch (ClassNotLoadedException e) {
                // One or both component types has not yet been
                // loaded => can't assign
                return false;
            }
        } else {
            Symbol typeName = ((ReferenceTypeImpl)destType).typeNameAsSymbol();
            if (destType instanceof InterfaceType) {
                // Every array type implements java.io.Serializable and
                // java.lang.Cloneable. fixme in JVMDI-JDI, includes only
                // Cloneable but not Serializable.
                return typeName.equals(vm.javaLangCloneable()) ||
                       typeName.equals(vm.javaIoSerializable());
            } else {
                // Only valid ClassType assignee is Object
                return typeName.equals(vm.javaLangObject());
            }
        }
    }

    List inheritedTypes() {
        // arrays are derived from java.lang.Object and
        // B[] is derived from A[] if B is derived from A.
        // But JVMDI-JDI returns zero sized list and we do the
        // same for consistency.
        return new ArrayList(0);
    }

    int getModifiers() {
        /*
         * For object arrays, the return values for Interface
         * Accessible.isPrivate(), Accessible.isProtected(),
         * etc... are the same as would be returned for the
         * component type.  Fetch the modifier bits from the
         * component type and use those.
         *
         * For primitive arrays, the modifiers are always
         *   VMModifiers.FINAL | VMModifiers.PUBLIC
         *
         * Reference com.sun.jdi.Accessible.java.
         */
        try {
            Type t = componentType();
            if (t instanceof PrimitiveType) {
                return VMModifiers.FINAL | VMModifiers.PUBLIC;
            } else {
                ReferenceType rt = (ReferenceType)t;
                return rt.modifiers();
            }
        } catch (ClassNotLoadedException cnle) {
            cnle.printStackTrace();
        }
        return -1;
    }

    public String toString() {
       return "array class " + name() + " (" + loaderString() + ")";
    }

    /*
     * Save a pointless trip over the wire for these methods
     * which have undefined results for arrays.
     */
    public boolean isPrepared() { return true; }
    public boolean isVerified() { return true; }
    public boolean isInitialized() { return true; }
    public boolean failedToInitialize() { return false; }
    public boolean isAbstract() { return false; }

    /*
     * Defined always to be true for arrays
     */
    public boolean isFinal() { return true; }
}
