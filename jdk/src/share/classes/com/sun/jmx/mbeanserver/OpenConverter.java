/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jmx.mbeanserver;

import static com.sun.jmx.mbeanserver.Util.*;

import static javax.management.openmbean.SimpleType.*;

import com.sun.jmx.remote.util.EnvHelp;

import java.beans.ConstructorProperties;
import java.io.InvalidObjectException;
import java.lang.annotation.ElementType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import javax.management.JMX;
import javax.management.ObjectName;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataInvocationHandler;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeDataView;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

/**
   <p>A converter between Java types and the limited set of classes
   defined by Open MBeans.</p>

   <p>A Java type is an instance of java.lang.reflect.Type.  For our
   purposes, it is either a Class, such as String.class or int.class;
   or a ParameterizedType, such as List<String> or Map<Integer,
   String[]>.  On J2SE 1.4 and earlier, it can only be a Class.</p>

   <p>Each Type is associated with an OpenConverter.  The
   OpenConverter defines an OpenType corresponding to the Type, plus a
   Java class corresponding to the OpenType.  For example:</p>

   <pre>
   Type                     Open class     OpenType
   ----                     ----------     --------
   Integer                  Integer        SimpleType.INTEGER
   int                      int            SimpleType.INTEGER
   Integer[]                Integer[]      ArrayType(1, SimpleType.INTEGER)
   int[]                    Integer[]      ArrayType(SimpleType.INTEGER, true)
   String[][]               String[][]     ArrayType(2, SimpleType.STRING)
   List<String>             String[]       ArrayType(1, SimpleType.STRING)
   ThreadState (an Enum)    String         SimpleType.STRING
   Map<Integer, String[]>   TabularData    TabularType(
                                             CompositeType(
                                               {"key", SimpleType.INTEGER},
                                               {"value",
                                                 ArrayType(1,
                                                  SimpleType.STRING)}),
                                             indexNames={"key"})
   </pre>

   <p>Apart from simple types, arrays, and collections, Java types are
   converted through introspection into CompositeType.  The Java type
   must have at least one getter (method such as "int getSize()" or
   "boolean isBig()"), and we must be able to deduce how to
   reconstruct an instance of the Java class from the values of the
   getters using one of various heuristics.</p>

   @since 1.6
 */
public abstract class OpenConverter {
    private OpenConverter(Type targetType, OpenType openType,
                          Class openClass) {
        this.targetType = targetType;
        this.openType = openType;
        this.openClass = openClass;
    }

    /** <p>Convert an instance of openClass into an instance of targetType. */
    public final Object fromOpenValue(MXBeanLookup lookup, Object value)
            throws InvalidObjectException {
        if (value == null)
            return null;
        else
            return fromNonNullOpenValue(lookup, value);
    }

    abstract Object fromNonNullOpenValue(MXBeanLookup lookup, Object value)
            throws InvalidObjectException;

    /** <p>Throw an appropriate InvalidObjectException if we will not be able
        to convert back from the open data to the original Java object.</p> */
    void checkReconstructible() throws InvalidObjectException {
        // subclasses override if action necessary
    }

    /** <p>Convert an instance of targetType into an instance of openClass. */
    final Object toOpenValue(MXBeanLookup lookup, Object value)
            throws OpenDataException {
        if (value == null)
            return null;
        else
            return toNonNullOpenValue(lookup, value);
    }

    abstract Object toNonNullOpenValue(MXBeanLookup lookup, Object value)
            throws OpenDataException;

    /** <p>True if and only if this OpenConverter's toOpenValue and fromOpenValue
        methods are the identity function.</p> */
    boolean isIdentity() {
        return false;
    }

    /** <p>True if and only if isIdentity() and even an array of the underlying type
       is transformed as the identity.  This is true for Integer and
       ObjectName, for instance, but not for int.</p> */
    final Type getTargetType() {
        return targetType;
    }

    final OpenType getOpenType() {
        return openType;
    }

    /* The Java class corresponding to getOpenType().  This is the class
       named by getOpenType().getClassName(), except that it may be a
       primitive type or an array of primitive type.  */
    final Class getOpenClass() {
        return openClass;
    }

    private final Type targetType;
    private final OpenType openType;
    private final Class openClass;

    private static final class ConverterMap
        extends WeakHashMap<Type, WeakReference<OpenConverter>> {}

    private static final ConverterMap converterMap = new ConverterMap();

    /** Following List simply serves to keep a reference to predefined
        OpenConverters so they don't get garbage collected. */
    private static final List<OpenConverter> permanentConverters = newList();

    private static synchronized OpenConverter getConverter(Type type) {
        WeakReference<OpenConverter> wr = converterMap.get(type);
        return (wr == null) ? null : wr.get();
    }

    private static synchronized void putConverter(Type type,
                                                  OpenConverter conv) {
        WeakReference<OpenConverter> wr =
            new WeakReference<OpenConverter>(conv);
        converterMap.put(type, wr);
    }

    private static synchronized void putPermanentConverter(Type type,
                                                           OpenConverter conv) {
        putConverter(type, conv);
        permanentConverters.add(conv);
    }

    static {
        /* Set up the mappings for Java types that map to SimpleType.  */

        final OpenType[] simpleTypes = {
            BIGDECIMAL, BIGINTEGER, BOOLEAN, BYTE, CHARACTER, DATE,
            DOUBLE, FLOAT, INTEGER, LONG, OBJECTNAME, SHORT, STRING,
            VOID,
        };

        for (int i = 0; i < simpleTypes.length; i++) {
            final OpenType t = simpleTypes[i];
            Class c;
            try {
                c = Class.forName(t.getClassName(), false,
                                  ObjectName.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                // the classes that these predefined types declare must exist!
                throw new Error(e);
            }
            final OpenConverter conv = new IdentityConverter(c, t, c);
            putPermanentConverter(c, conv);

            if (c.getName().startsWith("java.lang.")) {
                try {
                    final Field typeField = c.getField("TYPE");
                    final Class primitiveType = (Class) typeField.get(null);
                    final OpenConverter primitiveConv =
                        new IdentityConverter(primitiveType, t, primitiveType);
                    putPermanentConverter(primitiveType,
                                          primitiveConv);
                    if (primitiveType != void.class) {
                        final Class<?> primitiveArrayType =
                            Array.newInstance(primitiveType, 0).getClass();
                        final OpenType primitiveArrayOpenType =
                            ArrayType.getPrimitiveArrayType(primitiveArrayType);
                        final OpenConverter primitiveArrayConv =
                            new IdentityConverter(primitiveArrayType,
                                                  primitiveArrayOpenType,
                                                  primitiveArrayType);
                        putPermanentConverter(primitiveArrayType,
                                              primitiveArrayConv);
                    }
                } catch (NoSuchFieldException e) {
                    // OK: must not be a primitive wrapper
                } catch (IllegalAccessException e) {
                    // Should not reach here
                    assert(false);
                }
            }
        }
    }

    /** Get the converter for the given Java type, creating it if necessary. */
    public static synchronized OpenConverter toConverter(Type objType)
            throws OpenDataException {

        if (inProgress.containsKey(objType))
            throw new OpenDataException("Recursive data structure");

        OpenConverter conv;

        conv = getConverter(objType);
        if (conv != null)
            return conv;

        inProgress.put(objType, objType);
        try {
            conv = makeConverter(objType);
        } finally {
            inProgress.remove(objType);
        }

        putConverter(objType, conv);
        return conv;
    }

    private static OpenConverter makeConverter(Type objType)
            throws OpenDataException {

        /* It's not yet worth formalizing these tests by having for example
           an array of factory classes, each of which says whether it
           recognizes the Type (Chain of Responsibility pattern).  */
        if (objType instanceof GenericArrayType) {
            Type componentType =
                ((GenericArrayType) objType).getGenericComponentType();
            return makeArrayOrCollectionConverter(objType, componentType);
        } else if (objType instanceof Class) {
            Class<?> objClass = (Class<?>) objType;
            if (objClass.isEnum()) {
                // Huge hack to avoid compiler warnings here.  The ElementType
                // parameter is ignored but allows us to obtain a type variable
                // T that matches <T extends Enum<T>>.
                return makeEnumConverter(objClass, ElementType.class);
            } else if (objClass.isArray()) {
                Type componentType = objClass.getComponentType();
                return makeArrayOrCollectionConverter(objClass, componentType);
            } else if (JMX.isMXBeanInterface(objClass)) {
                return makeMXBeanConverter(objClass);
            } else {
                return makeCompositeConverter(objClass);
            }
        } else if (objType instanceof ParameterizedType) {
            return makeParameterizedConverter((ParameterizedType) objType);
        } else
            throw new OpenDataException("Cannot map type: " + objType);
    }

    private static <T extends Enum<T>> OpenConverter
            makeEnumConverter(Class<?> enumClass, Class<T> fake) {
        Class<T> enumClassT = Util.cast(enumClass);
        return new EnumConverter<T>(enumClassT);
    }

    /* Make the converter for an array type, or a collection such as
     * List<String> or Set<Integer>.  We never see one-dimensional
     * primitive arrays (e.g. int[]) here because they use the identity
     * converter and are registered as such in the static initializer.
     */
    private static OpenConverter
        makeArrayOrCollectionConverter(Type collectionType, Type elementType)
            throws OpenDataException {

        final OpenConverter elementConverter = toConverter(elementType);
        final OpenType<?> elementOpenType = elementConverter.getOpenType();
        final ArrayType<?> openType = ArrayType.getArrayType(elementOpenType);
        final Class<?> elementOpenClass = elementConverter.getOpenClass();

        final Class<?> openArrayClass;
        final String openArrayClassName;
        if (elementOpenClass.isArray())
            openArrayClassName = "[" + elementOpenClass.getName();
        else
            openArrayClassName = "[L" + elementOpenClass.getName() + ";";
        try {
            openArrayClass = Class.forName(openArrayClassName);
        } catch (ClassNotFoundException e) {
            throw openDataException("Cannot obtain array class", e);
        }

        if (collectionType instanceof ParameterizedType) {
            return new CollectionConverter(collectionType,
                                           openType, openArrayClass,
                                           elementConverter);
        } else {
            if (elementConverter.isIdentity()) {
                return new IdentityConverter(collectionType,
                                             openType,
                                             openArrayClass);
            } else {
                return new ArrayConverter(collectionType,
                                          openType,
                                          openArrayClass,
                                          elementConverter);
            }
        }
    }

    private static final String[] keyArray = {"key"};
    private static final String[] keyValueArray = {"key", "value"};

    private static OpenConverter
        makeTabularConverter(Type objType, boolean sortedMap,
                             Type keyType, Type valueType)
            throws OpenDataException {

        final String objTypeName = objType.toString();
        final OpenConverter keyConverter = toConverter(keyType);
        final OpenConverter valueConverter = toConverter(valueType);
        final OpenType keyOpenType = keyConverter.getOpenType();
        final OpenType valueOpenType = valueConverter.getOpenType();
        final CompositeType rowType =
            new CompositeType(objTypeName,
                              objTypeName,
                              keyValueArray,
                              keyValueArray,
                              new OpenType[] {keyOpenType, valueOpenType});
        final TabularType tabularType =
            new TabularType(objTypeName, objTypeName, rowType, keyArray);
        return new TabularConverter(objType, sortedMap, tabularType,
                                    keyConverter, valueConverter);
    }

    /* We know how to translate List<E>, Set<E>, SortedSet<E>,
       Map<K,V>, SortedMap<K,V>, and that's it.  We don't accept
       subtypes of those because we wouldn't know how to deserialize
       them.  We don't accept Queue<E> because it is unlikely people
       would use that as a parameter or return type in an MBean.  */
    private static OpenConverter
        makeParameterizedConverter(ParameterizedType objType) throws OpenDataException {

        final Type rawType = objType.getRawType();

        if (rawType instanceof Class) {
            Class c = (Class<?>) rawType;
            if (c == List.class || c == Set.class || c == SortedSet.class) {
                Type[] actuals = objType.getActualTypeArguments();
                assert(actuals.length == 1);
                if (c == SortedSet.class)
                    mustBeComparable(c, actuals[0]);
                return makeArrayOrCollectionConverter(objType, actuals[0]);
            } else {
                boolean sortedMap = (c == SortedMap.class);
                if (c == Map.class || sortedMap) {
                    Type[] actuals = objType.getActualTypeArguments();
                    assert(actuals.length == 2);
                    if (sortedMap)
                        mustBeComparable(c, actuals[0]);
                    return makeTabularConverter(objType, sortedMap,
                            actuals[0], actuals[1]);
                }
            }
        }
        throw new OpenDataException("Cannot convert type: " + objType);
    }

    private static OpenConverter makeMXBeanConverter(Type t)
            throws OpenDataException {
        return new MXBeanConverter(t);
    }

    private static OpenConverter makeCompositeConverter(Class c)
            throws OpenDataException {

        // For historical reasons GcInfo implements CompositeData but we
        // shouldn't count its CompositeData.getCompositeType() field as
        // an item in the computed CompositeType.
        final boolean gcInfoHack =
            (c.getName().equals("com.sun.management.GcInfo") &&
                c.getClassLoader() == null);

        final List<Method> methods =
                MBeanAnalyzer.eliminateCovariantMethods(c.getMethods());
        final SortedMap<String,Method> getterMap = newSortedMap();

        /* Select public methods that look like "T getX()" or "boolean
           isX()", where T is not void and X is not the empty
           string.  Exclude "Class getClass()" inherited from Object.  */
        for (Method method : methods) {
            final String propertyName = propertyName(method);

            if (propertyName == null)
                continue;
            if (gcInfoHack && propertyName.equals("CompositeType"))
                continue;

            Method old =
                getterMap.put(decapitalize(propertyName),
                            method);
            if (old != null) {
                final String msg =
                    "Class " + c.getName() + " has method name clash: " +
                    old.getName() + ", " + method.getName();
                throw new OpenDataException(msg);
            }
        }

        final int nitems = getterMap.size();

        if (nitems == 0) {
            throw new OpenDataException("Can't map " + c.getName() +
                                        " to an open data type");
        }

        final Method[] getters = new Method[nitems];
        final String[] itemNames = new String[nitems];
        final OpenType[] openTypes = new OpenType[nitems];
        int i = 0;
        for (Map.Entry<String,Method> entry : getterMap.entrySet()) {
            itemNames[i] = entry.getKey();
            final Method getter = entry.getValue();
            getters[i] = getter;
            final Type retType = getter.getGenericReturnType();
            openTypes[i] = toConverter(retType).getOpenType();
            i++;
        }

        CompositeType compositeType =
            new CompositeType(c.getName(),
                              c.getName(),
                              itemNames, // field names
                              itemNames, // field descriptions
                              openTypes);

        return new CompositeConverter(c,
                                      compositeType,
                                      itemNames,
                                      getters);
    }

    /* Converter for classes where the open data is identical to the
       original data.  This is true for any of the SimpleType types,
       and for an any-dimension array of those.  It is also true for
       primitive types as of JMX 1.3, since an int[] needs to
       can be directly represented by an ArrayType, and an int needs no mapping
       because reflection takes care of it.  */
    private static final class IdentityConverter extends OpenConverter {
        IdentityConverter(Type targetType, OpenType openType,
                          Class openClass) {
            super(targetType, openType, openClass);
        }

        boolean isIdentity() {
            return true;
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value) {
            return value;
        }

        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object value) {
            return value;
        }
    }

    private static final class EnumConverter<T extends Enum<T>>
            extends OpenConverter {

        EnumConverter(Class<T> enumClass) {
            super(enumClass, SimpleType.STRING, String.class);
            this.enumClass = enumClass;
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value) {
            return ((Enum) value).name();
        }

        // return type could be T, but after erasure that would be
        // java.lang.Enum, which doesn't exist on J2SE 1.4
        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws InvalidObjectException {
            try {
                return Enum.valueOf(enumClass, (String) value);
            } catch (Exception e) {
                throw invalidObjectException("Cannot convert to enum: " +
                                             value, e);
            }
        }

        private final Class<T> enumClass;
    }

    private static final class ArrayConverter extends OpenConverter {
        ArrayConverter(Type targetType,
                       ArrayType openArrayType, Class openArrayClass,
                       OpenConverter elementConverter) {
            super(targetType, openArrayType, openArrayClass);
            this.elementConverter = elementConverter;
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws OpenDataException {
            Object[] valueArray = (Object[]) value;
            final int len = valueArray.length;
            final Object[] openArray = (Object[])
                Array.newInstance(getOpenClass().getComponentType(), len);
            for (int i = 0; i < len; i++) {
                openArray[i] =
                    elementConverter.toOpenValue(lookup, valueArray[i]);
            }
            return openArray;
        }

        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object openValue)
                throws InvalidObjectException {
            final Object[] openArray = (Object[]) openValue;
            final Type targetType = getTargetType();
            final Object[] valueArray;
            final Type componentType;
            if (targetType instanceof GenericArrayType) {
                componentType =
                    ((GenericArrayType) targetType).getGenericComponentType();
            } else if (targetType instanceof Class &&
                       ((Class<?>) targetType).isArray()) {
                componentType = ((Class<?>) targetType).getComponentType();
            } else {
                throw new IllegalArgumentException("Not an array: " +
                                                   targetType);
            }
            valueArray = (Object[]) Array.newInstance((Class<?>) componentType,
                                                      openArray.length);
            for (int i = 0; i < openArray.length; i++) {
                valueArray[i] =
                    elementConverter.fromOpenValue(lookup, openArray[i]);
            }
            return valueArray;
        }

        void checkReconstructible() throws InvalidObjectException {
            elementConverter.checkReconstructible();
        }

        /** OpenConverter for the elements of this array.  If this is an
            array of arrays, the converter converts the second-level arrays,
            not the deepest elements.  */
        private final OpenConverter elementConverter;
    }

    private static final class CollectionConverter extends OpenConverter {
        CollectionConverter(Type targetType,
                            ArrayType openArrayType,
                            Class openArrayClass,
                            OpenConverter elementConverter) {
            super(targetType, openArrayType, openArrayClass);
            this.elementConverter = elementConverter;

            /* Determine the concrete class to be used when converting
               back to this Java type.  We convert all Lists to ArrayList
               and all Sets to TreeSet.  (TreeSet because it is a SortedSet,
               so works for both Set and SortedSet.)  */
            Type raw = ((ParameterizedType) targetType).getRawType();
            Class c = (Class<?>) raw;
            if (c == List.class)
                collectionClass = ArrayList.class;
            else if (c == Set.class)
                collectionClass = HashSet.class;
            else if (c == SortedSet.class)
                collectionClass = TreeSet.class;
            else { // can't happen
                assert(false);
                collectionClass = null;
            }
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws OpenDataException {
            final Collection valueCollection = (Collection) value;
            if (valueCollection instanceof SortedSet) {
                Comparator comparator =
                    ((SortedSet) valueCollection).comparator();
                if (comparator != null) {
                    final String msg =
                        "Cannot convert SortedSet with non-null comparator: " +
                        comparator;
                    throw new OpenDataException(msg);
                }
            }
            final Object[] openArray = (Object[])
                Array.newInstance(getOpenClass().getComponentType(),
                                  valueCollection.size());
            int i = 0;
            for (Object o : valueCollection)
                openArray[i++] = elementConverter.toOpenValue(lookup, o);
            return openArray;
        }

        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object openValue)
                throws InvalidObjectException {
            final Object[] openArray = (Object[]) openValue;
            final Collection<Object> valueCollection;
            try {
                valueCollection = Util.cast(collectionClass.newInstance());
            } catch (Exception e) {
                throw invalidObjectException("Cannot create collection", e);
            }
            for (Object o : openArray) {
                Object value = elementConverter.fromOpenValue(lookup, o);
                if (!valueCollection.add(value)) {
                    final String msg =
                        "Could not add " + o + " to " +
                        collectionClass.getName() +
                        " (duplicate set element?)";
                    throw new InvalidObjectException(msg);
                }
            }
            return valueCollection;
        }

        void checkReconstructible() throws InvalidObjectException {
            elementConverter.checkReconstructible();
        }

        private final Class<? extends Collection> collectionClass;
        private final OpenConverter elementConverter;
    }

    private static final class MXBeanConverter extends OpenConverter {
        MXBeanConverter(Type intf) {
            super(intf, SimpleType.OBJECTNAME, ObjectName.class);
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws OpenDataException {
            lookupNotNull(lookup, OpenDataException.class);
            ObjectName name = lookup.mxbeanToObjectName(value);
            if (name == null)
                throw new OpenDataException("No name for object: " + value);
            return name;
        }

        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws InvalidObjectException {
            lookupNotNull(lookup, InvalidObjectException.class);
            ObjectName name = (ObjectName) value;
            Object mxbean =
                lookup.objectNameToMXBean(name, (Class<?>) getTargetType());
            if (mxbean == null) {
                final String msg =
                    "No MXBean for name: " + name;
                throw new InvalidObjectException(msg);
            }
            return mxbean;
        }

        private <T extends Exception> void
            lookupNotNull(MXBeanLookup lookup, Class<T> excClass)
                throws T {
            if (lookup == null) {
                final String msg =
                    "Cannot convert MXBean interface in this context";
                T exc;
                try {
                    Constructor<T> con = excClass.getConstructor(String.class);
                    exc = con.newInstance(msg);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                throw exc;
            }
        }
    }

    private static final class TabularConverter extends OpenConverter {
        TabularConverter(Type targetType,
                         boolean sortedMap,
                         TabularType tabularType,
                         OpenConverter keyConverter,
                         OpenConverter valueConverter) {
            super(targetType, tabularType, TabularData.class);
            this.sortedMap = sortedMap;
            this.keyConverter = keyConverter;
            this.valueConverter = valueConverter;
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws OpenDataException {
            final Map<Object, Object> valueMap = Util.cast(value);
            if (valueMap instanceof SortedMap) {
                Comparator comparator = ((SortedMap) valueMap).comparator();
                if (comparator != null) {
                    final String msg =
                        "Cannot convert SortedMap with non-null comparator: " +
                        comparator;
                    throw new OpenDataException(msg);
                }
            }
            final TabularType tabularType = (TabularType) getOpenType();
            final TabularData table = new TabularDataSupport(tabularType);
            final CompositeType rowType = tabularType.getRowType();
            for (Map.Entry entry : valueMap.entrySet()) {
                final Object openKey =
                    keyConverter.toOpenValue(lookup, entry.getKey());
                final Object openValue =
                    valueConverter.toOpenValue(lookup, entry.getValue());
                final CompositeData row;
                row =
                    new CompositeDataSupport(rowType, keyValueArray,
                                             new Object[] {openKey,
                                                           openValue});
                table.put(row);
            }
            return table;
        }

        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object openValue)
                throws InvalidObjectException {
            final TabularData table = (TabularData) openValue;
            final Collection<CompositeData> rows = Util.cast(table.values());
            final Map<Object, Object> valueMap =
                sortedMap ? newSortedMap() : newMap();
            for (CompositeData row : rows) {
                final Object key =
                    keyConverter.fromOpenValue(lookup, row.get("key"));
                final Object value =
                    valueConverter.fromOpenValue(lookup, row.get("value"));
                if (valueMap.put(key, value) != null) {
                    final String msg =
                        "Duplicate entry in TabularData: key=" + key;
                    throw new InvalidObjectException(msg);
                }
            }
            return valueMap;
        }

        void checkReconstructible() throws InvalidObjectException {
            keyConverter.checkReconstructible();
            valueConverter.checkReconstructible();
        }

        private final boolean sortedMap;
        private final OpenConverter keyConverter;
        private final OpenConverter valueConverter;
    }

    private static final class CompositeConverter extends OpenConverter {
        CompositeConverter(Class targetClass,
                           CompositeType compositeType,
                           String[] itemNames,
                           Method[] getters) throws OpenDataException {
            super(targetClass, compositeType, CompositeData.class);

            assert(itemNames.length == getters.length);

            this.itemNames = itemNames;
            this.getters = getters;
            this.getterConverters = new OpenConverter[getters.length];
            for (int i = 0; i < getters.length; i++) {
                Type retType = getters[i].getGenericReturnType();
                getterConverters[i] = OpenConverter.toConverter(retType);
            }
        }

        final Object toNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws OpenDataException {
            CompositeType ct = (CompositeType) getOpenType();
            if (value instanceof CompositeDataView)
                return ((CompositeDataView) value).toCompositeData(ct);
            if (value == null)
                return null;

            Object[] values = new Object[getters.length];
            for (int i = 0; i < getters.length; i++) {
                try {
                    Object got = getters[i].invoke(value, (Object[]) null);
                    values[i] = getterConverters[i].toOpenValue(lookup, got);
                } catch (Exception e) {
                    throw openDataException("Error calling getter for " +
                                            itemNames[i] + ": " + e, e);
                }
            }
            return new CompositeDataSupport(ct, itemNames, values);
        }

        /** Determine how to convert back from the CompositeData into
            the original Java type.  For a type that is not reconstructible,
            this method will fail every time, and will throw the right
            exception. */
        private synchronized void makeCompositeBuilder()
                throws InvalidObjectException {
            if (compositeBuilder != null)
                return;

            Class targetClass = (Class<?>) getTargetType();
            /* In this 2D array, each subarray is a set of builders where
               there is no point in consulting the ones after the first if
               the first refuses.  */
            CompositeBuilder[][] builders = {
                {
                    new CompositeBuilderViaFrom(targetClass, itemNames),
                },
                {
                    new CompositeBuilderViaConstructor(targetClass, itemNames),
                },
                {
                    new CompositeBuilderCheckGetters(targetClass, itemNames,
                                                     getterConverters),
                    new CompositeBuilderViaSetters(targetClass, itemNames),
                    new CompositeBuilderViaProxy(targetClass, itemNames),
                },
            };
            CompositeBuilder foundBuilder = null;
            /* We try to make a meaningful exception message by
               concatenating each Builder's explanation of why it
               isn't applicable.  */
            final StringBuilder whyNots = new StringBuilder();
        find:
            for (CompositeBuilder[] relatedBuilders : builders) {
                for (int i = 0; i < relatedBuilders.length; i++) {
                    CompositeBuilder builder = relatedBuilders[i];
                    String whyNot = builder.applicable(getters);
                    if (whyNot == null) {
                        foundBuilder = builder;
                        break find;
                    }
                    if (whyNot.length() > 0) {
                        if (whyNots.length() > 0)
                            whyNots.append("; ");
                        whyNots.append(whyNot);
                        if (i == 0)
                           break; // skip other builders in this group
                    }
                }
            }
            if (foundBuilder == null) {
                final String msg =
                    "Do not know how to make a " + targetClass.getName() +
                    " from a CompositeData: " + whyNots;
                throw new InvalidObjectException(msg);
            }
            compositeBuilder = foundBuilder;
        }

        void checkReconstructible() throws InvalidObjectException {
            makeCompositeBuilder();
        }

        public final Object fromNonNullOpenValue(MXBeanLookup lookup, Object value)
                throws InvalidObjectException {
            makeCompositeBuilder();
            return compositeBuilder.fromCompositeData(lookup,
                                                      (CompositeData) value,
                                                      itemNames,
                                                      getterConverters);
        }

        private final String[] itemNames;
        private final Method[] getters;
        private final OpenConverter[] getterConverters;
        private CompositeBuilder compositeBuilder;
    }

    /** Converts from a CompositeData to an instance of the targetClass.  */
    private static abstract class CompositeBuilder {
        CompositeBuilder(Class targetClass, String[] itemNames) {
            this.targetClass = targetClass;
            this.itemNames = itemNames;
        }

        Class<?> getTargetClass() {
            return targetClass;
        }

        String[] getItemNames() {
            return itemNames;
        }

        /** If the subclass is appropriate for targetClass, then the
            method returns null.  If the subclass is not appropriate,
            then the method returns an explanation of why not.  If the
            subclass should be appropriate but there is a problem,
            then the method throws InvalidObjectException.  */
        abstract String applicable(Method[] getters)
                throws InvalidObjectException;

        abstract Object fromCompositeData(MXBeanLookup lookup, CompositeData cd,
                                          String[] itemNames,
                                          OpenConverter[] converters)
                throws InvalidObjectException;

        private final Class<?> targetClass;
        private final String[] itemNames;
    }

    /** Builder for when the target class has a method "public static
        from(CompositeData)".  */
    private static final class CompositeBuilderViaFrom
            extends CompositeBuilder {

        CompositeBuilderViaFrom(Class targetClass, String[] itemNames) {
            super(targetClass, itemNames);
        }

        String applicable(Method[] getters) throws InvalidObjectException {
            // See if it has a method "T from(CompositeData)"
            // as is conventional for a CompositeDataView
            Class<?> targetClass = getTargetClass();
            try {
                Method fromMethod =
                    targetClass.getMethod("from",
                                          new Class[] {CompositeData.class});

                if (!Modifier.isStatic(fromMethod.getModifiers())) {
                    final String msg =
                        "Method from(CompositeData) is not static";
                    throw new InvalidObjectException(msg);
                }

                if (fromMethod.getReturnType() != getTargetClass()) {
                    final String msg =
                        "Method from(CompositeData) returns " +
                        fromMethod.getReturnType().getName() +
                        " not " + targetClass.getName();
                    throw new InvalidObjectException(msg);
                }

                this.fromMethod = fromMethod;
                return null; // success!
            } catch (InvalidObjectException e) {
                throw e;
            } catch (Exception e) {
                // OK: it doesn't have the method
                return "no method from(CompositeData)";
            }
        }

        final Object fromCompositeData(MXBeanLookup lookup, CompositeData cd,
                                 String[] itemNames,
                                 OpenConverter[] converters)
                throws InvalidObjectException {
            try {
                return fromMethod.invoke(null, cd);
            } catch (Exception e) {
                final String msg = "Failed to invoke from(CompositeData)";
                throw invalidObjectException(msg, e);
            }
        }

        private Method fromMethod;
    }

    /** This builder never actually returns success.  It simply serves
        to check whether the other builders in the same group have any
        chance of success.  If any getter in the targetClass returns
        a type that we don't know how to reconstruct, then we will
        not be able to make a builder, and there is no point in repeating
        the error about the problematic getter as many times as there are
        candidate builders.  Instead, the "applicable" method will return
        an explanatory string, and the other builders will be skipped.
        If all the getters are OK, then the "applicable" method will return
        an empty string and the other builders will be tried.  */
    private static class CompositeBuilderCheckGetters extends CompositeBuilder {
        CompositeBuilderCheckGetters(Class targetClass, String[] itemNames,
                                     OpenConverter[] getterConverters) {
            super(targetClass, itemNames);
            this.getterConverters = getterConverters;
        }

        String applicable(Method[] getters) {
            for (int i = 0; i < getters.length; i++) {
                try {
                    getterConverters[i].checkReconstructible();
                } catch (InvalidObjectException e) {
                    return "method " + getters[i].getName() + " returns type " +
                        "that cannot be mapped back from OpenData";
                }
            }
            return "";
        }

        final Object fromCompositeData(MXBeanLookup lookup, CompositeData cd,
                                       String[] itemNames,
                                       OpenConverter[] converters) {
            throw new Error();
        }

        private final OpenConverter[] getterConverters;
    }

    /** Builder for when the target class has a setter for every getter. */
    private static class CompositeBuilderViaSetters extends CompositeBuilder {

        CompositeBuilderViaSetters(Class targetClass, String[] itemNames) {
            super(targetClass, itemNames);
        }

        String applicable(Method[] getters) {
            try {
                Constructor<?> c = getTargetClass().getConstructor((Class[]) null);
            } catch (Exception e) {
                return "does not have a public no-arg constructor";
            }

            Method[] setters = new Method[getters.length];
            for (int i = 0; i < getters.length; i++) {
                Method getter = getters[i];
                Class returnType = getter.getReturnType();
                String name = propertyName(getter);
                String setterName = "set" + name;
                Method setter;
                try {
                    setter = getTargetClass().getMethod(setterName, returnType);
                    if (setter.getReturnType() != void.class)
                        throw new Exception();
                } catch (Exception e) {
                    return "not all getters have corresponding setters " +
                           "(" + getter + ")";
                }
                setters[i] = setter;
            }
            this.setters = setters;
            return null;
        }

        Object fromCompositeData(MXBeanLookup lookup, CompositeData cd,
                                 String[] itemNames,
                                 OpenConverter[] converters)
                throws InvalidObjectException {
            Object o;
            try {
                o = getTargetClass().newInstance();
                for (int i = 0; i < itemNames.length; i++) {
                    if (cd.containsKey(itemNames[i])) {
                        Object openItem = cd.get(itemNames[i]);
                        Object javaItem =
                            converters[i].fromOpenValue(lookup, openItem);
                        setters[i].invoke(o, javaItem);
                    }
                }
            } catch (Exception e) {
                throw invalidObjectException(e);
            }
            return o;
        }

        private Method[] setters;
    }

    /** Builder for when the target class has a constructor that is
        annotated with @ConstructorProperties so we can see the correspondence
        to getters.  */
    private static final class CompositeBuilderViaConstructor
            extends CompositeBuilder {

        CompositeBuilderViaConstructor(Class targetClass, String[] itemNames) {
            super(targetClass, itemNames);
        }

        String applicable(Method[] getters) throws InvalidObjectException {

            final Class<ConstructorProperties> propertyNamesClass = ConstructorProperties.class;

            Class targetClass = getTargetClass();
            Constructor<?>[] constrs = targetClass.getConstructors();

            // Applicable if and only if there are any annotated constructors
            List<Constructor<?>> annotatedConstrList = newList();
            for (Constructor<?> constr : constrs) {
                if (Modifier.isPublic(constr.getModifiers())
                        && constr.getAnnotation(propertyNamesClass) != null)
                    annotatedConstrList.add(constr);
            }

            if (annotatedConstrList.isEmpty())
                return "no constructor has @ConstructorProperties annotation";

            annotatedConstructors = newList();

            // Now check that all the annotated constructors are valid
            // and throw an exception if not.

            // First link the itemNames to their getter indexes.
            Map<String, Integer> getterMap = newMap();
            String[] itemNames = getItemNames();
            for (int i = 0; i < itemNames.length; i++)
                getterMap.put(itemNames[i], i);

            // Run through the constructors making the checks in the spec.
            // For each constructor, remember the correspondence between its
            // parameters and the items.  The int[] for a constructor says
            // what parameter index should get what item.  For example,
            // if element 0 is 2 then that means that item 0 in the
            // CompositeData goes to parameter 2 of the constructor.  If an
            // element is -1, that item isn't given to the constructor.
            // Also remember the set of properties in that constructor
            // so we can test unambiguity.
            Set<BitSet> getterIndexSets = newSet();
            for (Constructor<?> constr : annotatedConstrList) {
                String[] propertyNames =
                    constr.getAnnotation(propertyNamesClass).value();

                Type[] paramTypes = constr.getGenericParameterTypes();
                if (paramTypes.length != propertyNames.length) {
                    final String msg =
                        "Number of constructor params does not match " +
                        "@ConstructorProperties annotation: " + constr;
                    throw new InvalidObjectException(msg);
                }

                int[] paramIndexes = new int[getters.length];
                for (int i = 0; i < getters.length; i++)
                    paramIndexes[i] = -1;
                BitSet present = new BitSet();

                for (int i = 0; i < propertyNames.length; i++) {
                    String propertyName = propertyNames[i];
                    if (!getterMap.containsKey(propertyName)) {
                        final String msg =
                            "@ConstructorProperties includes name " + propertyName +
                            " which does not correspond to a property: " +
                            constr;
                        throw new InvalidObjectException(msg);
                    }
                    int getterIndex = getterMap.get(propertyName);
                    paramIndexes[getterIndex] = i;
                    if (present.get(getterIndex)) {
                        final String msg =
                            "@ConstructorProperties contains property " +
                            propertyName + " more than once: " + constr;
                        throw new InvalidObjectException(msg);
                    }
                    present.set(getterIndex);
                    Method getter = getters[getterIndex];
                    Type propertyType = getter.getGenericReturnType();
                    if (!propertyType.equals(paramTypes[i])) {
                        final String msg =
                            "@ConstructorProperties gives property " + propertyName +
                            " of type " + propertyType + " for parameter " +
                            " of type " + paramTypes[i] + ": " + constr;
                        throw new InvalidObjectException(msg);
                    }
                }

                if (!getterIndexSets.add(present)) {
                    final String msg =
                        "More than one constructor has a @ConstructorProperties " +
                        "annotation with this set of names: " +
                        Arrays.toString(propertyNames);
                    throw new InvalidObjectException(msg);
                }

                Constr c = new Constr(constr, paramIndexes, present);
                annotatedConstructors.add(c);
            }

            /* Check that no possible set of items could lead to an ambiguous
             * choice of constructor (spec requires this check).  For any
             * pair of constructors, their union would be the minimal
             * ambiguous set.  If this set itself corresponds to a constructor,
             * there is no ambiguity for that pair.  In the usual case, one
             * of the constructors is a superset of the other so the union is
             * just the bigger constuctor.
             *
             * The algorithm here is quadratic in the number of constructors
             * with a @ConstructorProperties annotation.  Typically this corresponds
             * to the number of versions of the class there have been.  Ten
             * would already be a large number, so although it's probably
             * possible to have an O(n lg n) algorithm it wouldn't be
             * worth the complexity.
             */
            for (BitSet a : getterIndexSets) {
                boolean seen = false;
                for (BitSet b : getterIndexSets) {
                    if (a == b)
                        seen = true;
                    else if (seen) {
                        BitSet u = new BitSet();
                        u.or(a); u.or(b);
                        if (!getterIndexSets.contains(u)) {
                            Set<String> names = new TreeSet<String>();
                            for (int i = u.nextSetBit(0); i >= 0;
                                 i = u.nextSetBit(i+1))
                                names.add(itemNames[i]);
                            final String msg =
                                "Constructors with @ConstructorProperties annotation " +
                                " would be ambiguous for these items: " +
                                names;
                            throw new InvalidObjectException(msg);
                        }
                    }
                }
            }

            return null; // success!
        }

        Object fromCompositeData(MXBeanLookup lookup, CompositeData cd,
                                 String[] itemNames,
                                 OpenConverter[] converters)
                throws InvalidObjectException {
            // The CompositeData might come from an earlier version where
            // not all the items were present.  We look for a constructor
            // that accepts just the items that are present.  Because of
            // the ambiguity check in applicable(), we know there must be
            // at most one maximally applicable constructor.
            CompositeType ct = cd.getCompositeType();
            BitSet present = new BitSet();
            for (int i = 0; i < itemNames.length; i++) {
                if (ct.getType(itemNames[i]) != null)
                    present.set(i);
            }

            Constr max = null;
            for (Constr constr : annotatedConstructors) {
                if (subset(constr.presentParams, present) &&
                        (max == null ||
                         subset(max.presentParams, constr.presentParams)))
                    max = constr;
            }

            if (max == null) {
                final String msg =
                    "No constructor has a @ConstructorProperties for this set of " +
                    "items: " + ct.keySet();
                throw new InvalidObjectException(msg);
            }

            Object[] params = new Object[max.presentParams.cardinality()];
            for (int i = 0; i < itemNames.length; i++) {
                if (!max.presentParams.get(i))
                    continue;
                Object openItem = cd.get(itemNames[i]);
                Object javaItem = converters[i].fromOpenValue(lookup, openItem);
                int index = max.paramIndexes[i];
                if (index >= 0)
                    params[index] = javaItem;
            }

            try {
                return max.constructor.newInstance(params);
            } catch (Exception e) {
                final String msg =
                    "Exception constructing " + getTargetClass().getName();
                throw invalidObjectException(msg, e);
            }
        }

        private static boolean subset(BitSet sub, BitSet sup) {
            BitSet subcopy = (BitSet) sub.clone();
            subcopy.andNot(sup);
            return subcopy.isEmpty();
        }

        private static class Constr {
            final Constructor<?> constructor;
            final int[] paramIndexes;
            final BitSet presentParams;
            Constr(Constructor<?> constructor, int[] paramIndexes,
                   BitSet presentParams) {
                this.constructor = constructor;
                this.paramIndexes = paramIndexes;
                this.presentParams = presentParams;
            }
        }

        private List<Constr> annotatedConstructors;
    }

    /** Builder for when the target class is an interface and contains
        no methods other than getters.  Then we can make an instance
        using a dynamic proxy that forwards the getters to the source
        CompositeData.  */
    private static final class CompositeBuilderViaProxy
            extends CompositeBuilder {

        CompositeBuilderViaProxy(Class targetClass, String[] itemNames) {
            super(targetClass, itemNames);
        }

        String applicable(Method[] getters) {
            Class targetClass = getTargetClass();
            if (!targetClass.isInterface())
                return "not an interface";
            Set<Method> methods =
                newSet(Arrays.asList(targetClass.getMethods()));
            methods.removeAll(Arrays.asList(getters));
            /* If the interface has any methods left over, they better be
             * public methods that are already present in java.lang.Object.
             */
            String bad = null;
            for (Method m : methods) {
                String mname = m.getName();
                Class[] mparams = m.getParameterTypes();
                try {
                    Method om = Object.class.getMethod(mname, mparams);
                    if (!Modifier.isPublic(om.getModifiers()))
                        bad = mname;
                } catch (NoSuchMethodException e) {
                    bad = mname;
                }
                /* We don't catch SecurityException since it shouldn't
                 * happen for a method in Object and if it does we would
                 * like to know about it rather than mysteriously complaining.
                 */
            }
            if (bad != null)
                return "contains methods other than getters (" + bad + ")";
            return null; // success!
        }

        final Object fromCompositeData(MXBeanLookup lookup, CompositeData cd,
                                 String[] itemNames,
                                 OpenConverter[] converters) {
            final Class targetClass = getTargetClass();
            return
                Proxy.newProxyInstance(targetClass.getClassLoader(),
                                       new Class[] {targetClass},
                                       new CompositeDataInvocationHandler(cd));
        }
    }

    static InvalidObjectException invalidObjectException(String msg,
                                                         Throwable cause) {
        return EnvHelp.initCause(new InvalidObjectException(msg), cause);
    }

    static InvalidObjectException invalidObjectException(Throwable cause) {
        return invalidObjectException(cause.getMessage(), cause);
    }

    static OpenDataException openDataException(String msg, Throwable cause) {
        return EnvHelp.initCause(new OpenDataException(msg), cause);
    }

    static OpenDataException openDataException(Throwable cause) {
        return openDataException(cause.getMessage(), cause);
    }

    static void mustBeComparable(Class collection, Type element)
            throws OpenDataException {
        if (!(element instanceof Class)
            || !Comparable.class.isAssignableFrom((Class<?>) element)) {
            final String msg =
                "Parameter class " + element + " of " +
                collection.getName() + " does not implement " +
                Comparable.class.getName();
            throw new OpenDataException(msg);
        }
    }

    /**
     * Utility method to take a string and convert it to normal Java variable
     * name capitalization.  This normally means converting the first
     * character from upper case to lower case, but in the (unusual) special
     * case when there is more than one character and both the first and
     * second characters are upper case, we leave it alone.
     * <p>
     * Thus "FooBah" becomes "fooBah" and "X" becomes "x", but "URL" stays
     * as "URL".
     *
     * @param  name The string to be decapitalized.
     * @return  The decapitalized version of the string.
     */
    public static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        int offset1 = Character.offsetByCodePoints(name, 0, 1);
        // Should be name.offsetByCodePoints but 6242664 makes this fail
        if (offset1 < name.length() &&
                Character.isUpperCase(name.codePointAt(offset1)))
            return name;
        return name.substring(0, offset1).toLowerCase() +
               name.substring(offset1);
    }

    /**
     * Reverse operation for java.beans.Introspector.decapitalize.  For any s,
     * capitalize(decapitalize(s)).equals(s).  The reverse is not true:
     * e.g. capitalize("uRL") produces "URL" which is unchanged by
     * decapitalize.
     */
    static String capitalize(String name) {
        if (name == null || name.length() == 0)
            return name;
        int offset1 = name.offsetByCodePoints(0, 1);
        return name.substring(0, offset1).toUpperCase() +
               name.substring(offset1);
    }

    public static String propertyName(Method m) {
        String rest = null;
        String name = m.getName();
        if (name.startsWith("get"))
            rest = name.substring(3);
        else if (name.startsWith("is") && m.getReturnType() == boolean.class)
            rest = name.substring(2);
        if (rest == null || rest.length() == 0
            || m.getParameterTypes().length > 0
            || m.getReturnType() == void.class
            || name.equals("getClass"))
            return null;
        return rest;
    }

    private final static Map<Type, Type> inProgress = newIdentityHashMap();
    // really an IdentityHashSet but that doesn't exist
}
