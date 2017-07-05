/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.beans;

import java.lang.ref.Reference;
import java.lang.reflect.Method;

/**
 * An IndexedPropertyDescriptor describes a property that acts like an
 * array and has an indexed read and/or indexed write method to access
 * specific elements of the array.
 * <p>
 * An indexed property may also provide simple non-indexed read and write
 * methods.  If these are present, they read and write arrays of the type
 * returned by the indexed read method.
 */

public class IndexedPropertyDescriptor extends PropertyDescriptor {

    private Reference<Class> indexedPropertyTypeRef;
    private Reference<Method> indexedReadMethodRef;
    private Reference<Method> indexedWriteMethodRef;

    private String indexedReadMethodName;
    private String indexedWriteMethodName;

    /**
     * This constructor constructs an IndexedPropertyDescriptor for a property
     * that follows the standard Java conventions by having getFoo and setFoo
     * accessor methods, for both indexed access and array access.
     * <p>
     * Thus if the argument name is "fred", it will assume that there
     * is an indexed reader method "getFred", a non-indexed (array) reader
     * method also called "getFred", an indexed writer method "setFred",
     * and finally a non-indexed writer method "setFred".
     *
     * @param propertyName The programmatic name of the property.
     * @param beanClass The Class object for the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public IndexedPropertyDescriptor(String propertyName, Class<?> beanClass)
                throws IntrospectionException {
        this(propertyName, beanClass,
             Introspector.GET_PREFIX + NameGenerator.capitalize(propertyName),
             Introspector.SET_PREFIX + NameGenerator.capitalize(propertyName),
             Introspector.GET_PREFIX + NameGenerator.capitalize(propertyName),
             Introspector.SET_PREFIX + NameGenerator.capitalize(propertyName));
    }

    /**
     * This constructor takes the name of a simple property, and method
     * names for reading and writing the property, both indexed
     * and non-indexed.
     *
     * @param propertyName The programmatic name of the property.
     * @param beanClass  The Class object for the target bean.
     * @param readMethodName The name of the method used for reading the property
     *           values as an array.  May be null if the property is write-only
     *           or must be indexed.
     * @param writeMethodName The name of the method used for writing the property
     *           values as an array.  May be null if the property is read-only
     *           or must be indexed.
     * @param indexedReadMethodName The name of the method used for reading
     *          an indexed property value.
     *          May be null if the property is write-only.
     * @param indexedWriteMethodName The name of the method used for writing
     *          an indexed property value.
     *          May be null if the property is read-only.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public IndexedPropertyDescriptor(String propertyName, Class<?> beanClass,
                String readMethodName, String writeMethodName,
                String indexedReadMethodName, String indexedWriteMethodName)
                throws IntrospectionException {
        super(propertyName, beanClass, readMethodName, writeMethodName);

        this.indexedReadMethodName = indexedReadMethodName;
        if (indexedReadMethodName != null && getIndexedReadMethod() == null) {
            throw new IntrospectionException("Method not found: " + indexedReadMethodName);
        }

        this.indexedWriteMethodName = indexedWriteMethodName;
        if (indexedWriteMethodName != null && getIndexedWriteMethod() == null) {
            throw new IntrospectionException("Method not found: " + indexedWriteMethodName);
        }
        // Implemented only for type checking.
        findIndexedPropertyType(getIndexedReadMethod(), getIndexedWriteMethod());
    }

    /**
     * This constructor takes the name of a simple property, and Method
     * objects for reading and writing the property.
     *
     * @param propertyName The programmatic name of the property.
     * @param readMethod The method used for reading the property values as an array.
     *          May be null if the property is write-only or must be indexed.
     * @param writeMethod The method used for writing the property values as an array.
     *          May be null if the property is read-only or must be indexed.
     * @param indexedReadMethod The method used for reading an indexed property value.
     *          May be null if the property is write-only.
     * @param indexedWriteMethod The method used for writing an indexed property value.
     *          May be null if the property is read-only.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public IndexedPropertyDescriptor(String propertyName, Method readMethod, Method writeMethod,
                                            Method indexedReadMethod, Method indexedWriteMethod)
                throws IntrospectionException {
        super(propertyName, readMethod, writeMethod);

        setIndexedReadMethod0(indexedReadMethod);
        setIndexedWriteMethod0(indexedWriteMethod);

        // Type checking
        setIndexedPropertyType(findIndexedPropertyType(indexedReadMethod, indexedWriteMethod));
    }

    /**
     * Creates <code>PropertyDescriptor</code> for the specified bean
     * with the specified name and methods to read/write the property value.
     *
     * @param bean          the type of the target bean
     * @param base          the base name of the property (the rest of the method name)
     * @param read          the method used for reading the property value
     * @param write         the method used for writing the property value
     * @param readIndexed   the method used for reading an indexed property value
     * @param writeIndexed  the method used for writing an indexed property value
     * @exception IntrospectionException if an exception occurs during introspection
     *
     * @since 1.7
     */
    IndexedPropertyDescriptor(Class<?> bean, String base, Method read, Method write, Method readIndexed, Method writeIndexed) throws IntrospectionException {
        super(bean, base, read, write);

        setIndexedReadMethod0(readIndexed);
        setIndexedWriteMethod0(writeIndexed);

        // Type checking
        setIndexedPropertyType(findIndexedPropertyType(readIndexed, writeIndexed));
    }

    /**
     * Gets the method that should be used to read an indexed
     * property value.
     *
     * @return The method that should be used to read an indexed
     * property value.
     * May return null if the property isn't indexed or is write-only.
     */
    public synchronized Method getIndexedReadMethod() {
        Method indexedReadMethod = getIndexedReadMethod0();
        if (indexedReadMethod == null) {
            Class cls = getClass0();
            if (cls == null ||
                (indexedReadMethodName == null && indexedReadMethodRef == null)) {
                // the Indexed readMethod was explicitly set to null.
                return null;
            }
            if (indexedReadMethodName == null) {
                Class type = getIndexedPropertyType0();
                if (type == boolean.class || type == null) {
                    indexedReadMethodName = Introspector.IS_PREFIX + getBaseName();
                } else {
                    indexedReadMethodName = Introspector.GET_PREFIX + getBaseName();
                }
            }

            Class[] args = { int.class };

            indexedReadMethod = Introspector.findMethod(cls, indexedReadMethodName,
                                                        1, args);
            if (indexedReadMethod == null) {
                // no "is" method, so look for a "get" method.
                indexedReadMethodName = Introspector.GET_PREFIX + getBaseName();
                indexedReadMethod = Introspector.findMethod(cls, indexedReadMethodName,
                                                            1, args);
            }
            setIndexedReadMethod0(indexedReadMethod);
        }
        return indexedReadMethod;
    }

    /**
     * Sets the method that should be used to read an indexed property value.
     *
     * @param readMethod The new indexed read method.
     */
    public synchronized void setIndexedReadMethod(Method readMethod)
        throws IntrospectionException {

        // the indexed property type is set by the reader.
        setIndexedPropertyType(findIndexedPropertyType(readMethod,
                                                       getIndexedWriteMethod0()));
        setIndexedReadMethod0(readMethod);
    }

    private void setIndexedReadMethod0(Method readMethod) {
        if (readMethod == null) {
            indexedReadMethodName = null;
            indexedReadMethodRef = null;
            return;
        }
        setClass0(readMethod.getDeclaringClass());

        indexedReadMethodName = readMethod.getName();
        this.indexedReadMethodRef = getSoftReference(readMethod);
        setTransient(readMethod.getAnnotation(Transient.class));
    }


    /**
     * Gets the method that should be used to write an indexed property value.
     *
     * @return The method that should be used to write an indexed
     * property value.
     * May return null if the property isn't indexed or is read-only.
     */
    public synchronized Method getIndexedWriteMethod() {
        Method indexedWriteMethod = getIndexedWriteMethod0();
        if (indexedWriteMethod == null) {
            Class cls = getClass0();
            if (cls == null ||
                (indexedWriteMethodName == null && indexedWriteMethodRef == null)) {
                // the Indexed writeMethod was explicitly set to null.
                return null;
            }

            // We need the indexed type to ensure that we get the correct method.
            // Cannot use the getIndexedPropertyType method since that could
            // result in an infinite loop.
            Class type = getIndexedPropertyType0();
            if (type == null) {
                try {
                    type = findIndexedPropertyType(getIndexedReadMethod(), null);
                    setIndexedPropertyType(type);
                } catch (IntrospectionException ex) {
                    // Set iprop type to be the classic type
                    Class propType = getPropertyType();
                    if (propType.isArray()) {
                        type = propType.getComponentType();
                    }
                }
            }

            if (indexedWriteMethodName == null) {
                indexedWriteMethodName = Introspector.SET_PREFIX + getBaseName();
            }
            indexedWriteMethod = Introspector.findMethod(cls, indexedWriteMethodName,
                         2, (type == null) ? null : new Class[] { int.class, type });
            if (indexedWriteMethod != null) {
                if (!indexedWriteMethod.getReturnType().equals(void.class)) {
                    indexedWriteMethod = null;
                }
            }
            setIndexedWriteMethod0(indexedWriteMethod);
        }
        return indexedWriteMethod;
    }

    /**
     * Sets the method that should be used to write an indexed property value.
     *
     * @param writeMethod The new indexed write method.
     */
    public synchronized void setIndexedWriteMethod(Method writeMethod)
        throws IntrospectionException {

        // If the indexed property type has not been set, then set it.
        Class type = findIndexedPropertyType(getIndexedReadMethod(),
                                             writeMethod);
        setIndexedPropertyType(type);
        setIndexedWriteMethod0(writeMethod);
    }

    private void setIndexedWriteMethod0(Method writeMethod) {
        if (writeMethod == null) {
            indexedWriteMethodName = null;
            indexedWriteMethodRef = null;
            return;
        }
        setClass0(writeMethod.getDeclaringClass());

        indexedWriteMethodName = writeMethod.getName();
        this.indexedWriteMethodRef = getSoftReference(writeMethod);
        setTransient(writeMethod.getAnnotation(Transient.class));
    }

    /**
     * Returns the Java type info for the indexed property.
     * Note that the {@code Class} object may describe
     * primitive Java types such as {@code int}.
     * This type is returned by the indexed read method
     * or is used as the parameter type of the indexed write method.
     *
     * @return the {@code Class} object that represents the Java type info,
     *         or {@code null} if the type cannot be determined
     */
    public synchronized Class<?> getIndexedPropertyType() {
        Class type = getIndexedPropertyType0();
        if (type == null) {
            try {
                type = findIndexedPropertyType(getIndexedReadMethod(),
                                               getIndexedWriteMethod());
                setIndexedPropertyType(type);
            } catch (IntrospectionException ex) {
                // fall
            }
        }
        return type;
    }

    // Private methods which set get/set the Reference objects

    private void setIndexedPropertyType(Class type) {
        this.indexedPropertyTypeRef = getWeakReference(type);
    }

    private Class getIndexedPropertyType0() {
        return (this.indexedPropertyTypeRef != null)
                ? this.indexedPropertyTypeRef.get()
                : null;
    }

    private Method getIndexedReadMethod0() {
        return (this.indexedReadMethodRef != null)
                ? this.indexedReadMethodRef.get()
                : null;
    }

    private Method getIndexedWriteMethod0() {
        return (this.indexedWriteMethodRef != null)
                ? this.indexedWriteMethodRef.get()
                : null;
    }

    private Class findIndexedPropertyType(Method indexedReadMethod,
                                          Method indexedWriteMethod)
        throws IntrospectionException {
        Class indexedPropertyType = null;

        if (indexedReadMethod != null) {
            Class params[] = getParameterTypes(getClass0(), indexedReadMethod);
            if (params.length != 1) {
                throw new IntrospectionException("bad indexed read method arg count");
            }
            if (params[0] != Integer.TYPE) {
                throw new IntrospectionException("non int index to indexed read method");
            }
            indexedPropertyType = getReturnType(getClass0(), indexedReadMethod);
            if (indexedPropertyType == Void.TYPE) {
                throw new IntrospectionException("indexed read method returns void");
            }
        }
        if (indexedWriteMethod != null) {
            Class params[] = getParameterTypes(getClass0(), indexedWriteMethod);
            if (params.length != 2) {
                throw new IntrospectionException("bad indexed write method arg count");
            }
            if (params[0] != Integer.TYPE) {
                throw new IntrospectionException("non int index to indexed write method");
            }
            if (indexedPropertyType != null && indexedPropertyType != params[1]) {
                throw new IntrospectionException(
                                                 "type mismatch between indexed read and indexed write methods: "
                                                 + getName());
            }
            indexedPropertyType = params[1];
        }
        Class propertyType = getPropertyType();
        if (propertyType != null && (!propertyType.isArray() ||
                                     propertyType.getComponentType() != indexedPropertyType)) {
            throw new IntrospectionException("type mismatch between indexed and non-indexed methods: "
                                             + getName());
        }
        return indexedPropertyType;
    }

    /**
     * Compares this <code>PropertyDescriptor</code> against the specified object.
     * Returns true if the objects are the same. Two <code>PropertyDescriptor</code>s
     * are the same if the read, write, property types, property editor and
     * flags  are equivalent.
     *
     * @since 1.4
     */
    public boolean equals(Object obj) {
        // Note: This would be identical to PropertyDescriptor but they don't
        // share the same fields.
        if (this == obj) {
            return true;
        }

        if (obj != null && obj instanceof IndexedPropertyDescriptor) {
            IndexedPropertyDescriptor other = (IndexedPropertyDescriptor)obj;
            Method otherIndexedReadMethod = other.getIndexedReadMethod();
            Method otherIndexedWriteMethod = other.getIndexedWriteMethod();

            if (!compareMethods(getIndexedReadMethod(), otherIndexedReadMethod)) {
                return false;
            }

            if (!compareMethods(getIndexedWriteMethod(), otherIndexedWriteMethod)) {
                return false;
            }

            if (getIndexedPropertyType() != other.getIndexedPropertyType()) {
                return false;
            }
            return super.equals(obj);
        }
        return false;
    }

    /**
     * Package-private constructor.
     * Merge two property descriptors.  Where they conflict, give the
     * second argument (y) priority over the first argumnnt (x).
     *
     * @param x  The first (lower priority) PropertyDescriptor
     * @param y  The second (higher priority) PropertyDescriptor
     */

    IndexedPropertyDescriptor(PropertyDescriptor x, PropertyDescriptor y) {
        super(x,y);
        if (x instanceof IndexedPropertyDescriptor) {
            IndexedPropertyDescriptor ix = (IndexedPropertyDescriptor)x;
            try {
                Method xr = ix.getIndexedReadMethod();
                if (xr != null) {
                    setIndexedReadMethod(xr);
                }

                Method xw = ix.getIndexedWriteMethod();
                if (xw != null) {
                    setIndexedWriteMethod(xw);
                }
            } catch (IntrospectionException ex) {
                // Should not happen
                throw new AssertionError(ex);
            }
        }
        if (y instanceof IndexedPropertyDescriptor) {
            IndexedPropertyDescriptor iy = (IndexedPropertyDescriptor)y;
            try {
                Method yr = iy.getIndexedReadMethod();
                if (yr != null && yr.getDeclaringClass() == getClass0()) {
                    setIndexedReadMethod(yr);
                }

                Method yw = iy.getIndexedWriteMethod();
                if (yw != null && yw.getDeclaringClass() == getClass0()) {
                    setIndexedWriteMethod(yw);
                }
            } catch (IntrospectionException ex) {
                // Should not happen
                throw new AssertionError(ex);
            }
        }
    }

    /*
     * Package-private dup constructor
     * This must isolate the new object from any changes to the old object.
     */
    IndexedPropertyDescriptor(IndexedPropertyDescriptor old) {
        super(old);
        indexedReadMethodRef = old.indexedReadMethodRef;
        indexedWriteMethodRef = old.indexedWriteMethodRef;
        indexedPropertyTypeRef = old.indexedPropertyTypeRef;
        indexedWriteMethodName = old.indexedWriteMethodName;
        indexedReadMethodName = old.indexedReadMethodName;
    }

    /**
     * Returns a hash code value for the object.
     * See {@link java.lang.Object#hashCode} for a complete description.
     *
     * @return a hash code value for this object.
     * @since 1.5
     */
    public int hashCode() {
        int result = super.hashCode();

        result = 37 * result + ((indexedWriteMethodName == null) ? 0 :
                                indexedWriteMethodName.hashCode());
        result = 37 * result + ((indexedReadMethodName == null) ? 0 :
                                indexedReadMethodName.hashCode());
        result = 37 * result + ((getIndexedPropertyType() == null) ? 0 :
                                getIndexedPropertyType().hashCode());

        return result;
    }

    void appendTo(StringBuilder sb) {
        super.appendTo(sb);
        appendTo(sb, "indexedPropertyType", this.indexedPropertyTypeRef);
        appendTo(sb, "indexedReadMethod", this.indexedReadMethodRef);
        appendTo(sb, "indexedWriteMethod", this.indexedWriteMethodRef);
    }
}
