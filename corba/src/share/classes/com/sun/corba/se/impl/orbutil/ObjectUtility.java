/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

import java.security.PrivilegedAction;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Properties;
import java.util.IdentityHashMap;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger ;
import java.math.BigDecimal ;

public final class ObjectUtility {
    private boolean useToString ;
    private boolean isIndenting ;
    private int initialLevel ;
    private int increment ;
    private ClassMap classToPrinter = new ClassMap() ;

    private static ObjectUtility standard = new ObjectUtility( false, true,
        0, 4 ) ;
    private static ObjectUtility compact = new ObjectUtility( true, false,
        0, 4 ) ;

    private ObjectUtility( boolean useToString, boolean isIndenting,
        int initialLevel, int increment )
    {
        this.useToString = useToString ;
        this.isIndenting = isIndenting ;
        this.initialLevel = initialLevel ;
        this.increment = increment ;
        classToPrinter.put( Properties.class, propertiesPrinter ) ;
        classToPrinter.put( Collection.class, collectionPrinter ) ;
        classToPrinter.put( Map.class, mapPrinter ) ;
    }

    /** Construct an Utility instance with the desired objectToString
    * behavior.
    */
    public static ObjectUtility make( boolean useToString, boolean isIndenting,
        int initialLevel, int increment )
    {
        return new ObjectUtility( useToString, isIndenting, initialLevel,
            increment ) ;
    }

    /** Construct an Utility instance with the desired objectToString
    * behavior.
    */
    public static ObjectUtility make( boolean useToString, boolean isIndenting )
    {
        return new ObjectUtility( useToString, isIndenting, 0, 4 ) ;
    }

    /** Get the standard Utility object that supports objectToString with
    * indented display and no use of toString() methods.
    */
    public static ObjectUtility make()
    {
        return standard ;
    }

    /** A convenience method that gives the default behavior: use indenting
    * to display the object's structure and do not use built-in toString
    * methods.
    */
    public static String defaultObjectToString( java.lang.Object object )
    {
        return standard.objectToString( object ) ;
    }

    public static String compactObjectToString( java.lang.Object object )
    {
        return compact.objectToString( object ) ;
    }

    /** objectToString handles display of arbitrary objects.  It correctly
    * handles objects whose elements form an arbitrary graph.  It uses
    * reflection to display the contents of any kind of object.
    * An object's toString() method may optionally be used, but the default
    * is to ignore all toString() methods except for those defined for
    * primitive types, primitive type wrappers, and strings.
    */
    public String objectToString(java.lang.Object obj)
    {
        IdentityHashMap printed = new IdentityHashMap() ;
        ObjectWriter result = ObjectWriter.make( isIndenting, initialLevel,
            increment ) ;
        objectToStringHelper( printed, result, obj ) ;
        return result.toString() ;
    }

    // Perform a deep structural equality comparison of the two objects.
    // This handles all arrays, maps, and sets specially, otherwise
    // it just calls the object's equals() method.
    public static boolean equals( java.lang.Object obj1, java.lang.Object obj2 )
    {
        // Set of pairs of objects that have been (or are being) considered for
        // equality.  Such pairs are presumed to be equals.  If they are not,
        // this will be detected eventually and the equals method will return
        // false.
        Set considered = new HashSet() ;

        // Map that gives the corresponding component of obj2 for a component
        // of obj1.  This is used to check for the same aliasing and use of
        // equal objects in both objects.
        Map counterpart = new IdentityHashMap() ;

        return equalsHelper( counterpart, considered, obj1, obj2 ) ;
    }

    /** If arr1 and arr2 are both arrays of the same component type,
     * return an array of that component type that consists of the
     * elements of arr1 followed by the elements of arr2.
     * Throws IllegalArgumentException otherwise.
     */
    public static Object concatenateArrays( Object arr1, Object arr2 )
    {
        Class comp1 = arr1.getClass().getComponentType() ;
        Class comp2 = arr2.getClass().getComponentType() ;
        int len1 = Array.getLength( arr1 ) ;
        int len2 = Array.getLength( arr2 ) ;

        if ((comp1 == null) || (comp2 == null))
            throw new IllegalStateException( "Arguments must be arrays" ) ;
        if (!comp1.equals( comp2 ))
            throw new IllegalStateException(
                "Arguments must be arrays with the same component type" ) ;

        Object result = Array.newInstance( comp1, len1 + len2 ) ;

        int index = 0 ;

        for (int ctr=0; ctr<len1; ctr++)
            Array.set( result, index++, Array.get( arr1, ctr ) ) ;

        for (int ctr=0; ctr<len2; ctr++)
            Array.set( result, index++, Array.get( arr2, ctr ) ) ;

        return result ;
    }

//===========================================================================
//  Implementation
//===========================================================================

    private void objectToStringHelper( IdentityHashMap printed,
        ObjectWriter result, java.lang.Object obj)
    {
        if (obj==null) {
            result.append( "null" ) ;
            result.endElement() ;
        } else {
            Class cls = obj.getClass() ;
            result.startObject( obj ) ;

            if (printed.keySet().contains( obj )) {
                result.endObject( "*VISITED*" ) ;
            } else {
                printed.put( obj, null ) ;

                if (mustUseToString(cls)) {
                    result.endObject( obj.toString() ) ;
                } else {
                    // First, handle any classes that have special printer
                    // methods defined.  This is useful when the class
                    // overrides toString with something that
                    // is not sufficiently detailed.
                    ObjectPrinter printer = (ObjectPrinter)(classToPrinter.get(
                        cls )) ;
                    if (printer != null) {
                        printer.print( printed, result, obj ) ;
                        result.endObject() ;
                    } else {
                        Class compClass = cls.getComponentType() ;

                        if (compClass == null)
                            // handleObject always calls endObject
                            handleObject( printed, result, obj ) ;
                        else {
                            handleArray( printed, result, obj ) ;
                            result.endObject() ;
                        }
                    }
                }
            }
        }
    }

    private static interface ObjectPrinter {
        void print( IdentityHashMap printed, ObjectWriter buff,
            java.lang.Object obj ) ;
    }

    private ObjectPrinter propertiesPrinter = new ObjectPrinter() {
        public void print( IdentityHashMap printed, ObjectWriter buff,
            java.lang.Object obj )
        {
            if (!(obj instanceof Properties))
                throw new Error() ;

            Properties props = (Properties)obj ;
            Enumeration keys = props.propertyNames() ;
            while (keys.hasMoreElements()) {
                String key = (String)(keys.nextElement()) ;
                String value = props.getProperty( key ) ;
                buff.startElement() ;
                buff.append( key ) ;
                buff.append( "=" ) ;
                buff.append( value ) ;
                buff.endElement() ;
            }
        }
    } ;

    private ObjectPrinter collectionPrinter = new ObjectPrinter() {
        public void print( IdentityHashMap printed, ObjectWriter buff,
            java.lang.Object obj )
        {
            if (!(obj instanceof Collection))
                throw new Error() ;

            Collection coll = (Collection)obj ;
            Iterator iter = coll.iterator() ;
            while (iter.hasNext()) {
                java.lang.Object element = iter.next() ;
                buff.startElement() ;
                objectToStringHelper( printed, buff, element ) ;
                buff.endElement() ;
            }
        }
    } ;

    private ObjectPrinter mapPrinter = new ObjectPrinter() {
        public void print( IdentityHashMap printed, ObjectWriter buff,
            java.lang.Object obj )
        {
            if (!(obj instanceof Map))
                throw new Error() ;

            Map map = (Map)obj ;
            Iterator iter = map.entrySet().iterator() ;
            while (iter.hasNext()) {
                Entry entry = (Entry)(iter.next()) ;
                buff.startElement() ;
                objectToStringHelper( printed, buff, entry.getKey() ) ;
                buff.append( "=>" ) ;
                objectToStringHelper( printed, buff, entry.getValue() ) ;
                buff.endElement() ;
            }
        }
    } ;

    private static class ClassMap {
        ArrayList data ;

        public ClassMap()
        {
            data = new ArrayList() ;
        }

        /** Return the first element of the ClassMap that is assignable to cls.
        * The order is determined by the order in which the put method was
        * called.  Returns null if there is no match.
        */
        public java.lang.Object get( Class cls )
        {
            Iterator iter = data.iterator() ;
            while (iter.hasNext()) {
                java.lang.Object[] arr = (java.lang.Object[])(iter.next()) ;
                Class key = (Class)(arr[0]) ;
                if (key.isAssignableFrom( cls ))
                    return arr[1] ;
            }

            return null ;
        }

        /** Add obj to the map with key cls.  Note that order matters,
         * as the first match is returned.
         */
        public void put( Class cls, java.lang.Object obj )
        {
            java.lang.Object[] pair = { cls, obj } ;
            data.add( pair ) ;
        }
    }

    private boolean mustUseToString( Class cls )
    {
        // These probably never occur
        if (cls.isPrimitive())
            return true ;

        // We must use toString for all primitive wrappers, since
        // otherwise the code recurses endlessly (access value field
        // inside Integer, returns another Integer through reflection).
        if ((cls == Integer.class) ||
            (cls == BigInteger.class) ||
            (cls == BigDecimal.class) ||
            (cls == String.class) ||
            (cls == StringBuffer.class) ||
            (cls == Long.class) ||
            (cls == Short.class) ||
            (cls == Byte.class) ||
            (cls == Character.class) ||
            (cls == Float.class) ||
            (cls == Double.class) ||
            (cls == Boolean.class))
            return true ;

        if (useToString) {
            try {
                cls.getDeclaredMethod( "toString", (Class[])null ) ;
                return true ;
            } catch (Exception exc) {
                return false ;
            }
        }

        return false ;
    }

    private void handleObject( IdentityHashMap printed, ObjectWriter result,
        java.lang.Object obj )
    {
        Class cls = obj.getClass() ;

        try {
            Field[] fields;
            SecurityManager security = System.getSecurityManager();
            if (security != null && !Modifier.isPublic(cls.getModifiers())) {
                fields = new Field[0];
            } else {
                fields = cls.getDeclaredFields();
            }

            for (int ctr=0; ctr<fields.length; ctr++ ) {
                final Field fld = fields[ctr] ;
                int modifiers = fld.getModifiers() ;

                // Do not display field if it is static, since these fields
                // are always the same for every instances.  This could
                // be made configurable, but I don't think it is
                // useful to do so.
                if (!Modifier.isStatic( modifiers )) {
                    if (security != null) {
                        if (!Modifier.isPublic(modifiers))
                            continue;
                    }
                    result.startElement() ;
                    result.append( fld.getName() ) ;
                    result.append( ":" ) ;

                    try {
                        // Make sure that we can read the field if it is
                        // not public
                        AccessController.doPrivileged( new PrivilegedAction() {
                            public Object run() {
                                fld.setAccessible( true ) ;
                                return null ;
                            }
                        } ) ;

                        java.lang.Object value = fld.get( obj ) ;
                        objectToStringHelper( printed, result, value ) ;
                    } catch (Exception exc2) {
                        result.append( "???" ) ;
                    }

                    result.endElement() ;
                }
            }

            result.endObject() ;
        } catch (Exception exc2) {
            result.endObject( obj.toString() ) ;
        }
    }

    private void handleArray( IdentityHashMap printed, ObjectWriter result,
        java.lang.Object obj )
    {
        Class compClass = obj.getClass().getComponentType() ;
        if (compClass == boolean.class) {
            boolean[] arr = (boolean[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == byte.class) {
            byte[] arr = (byte[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == short.class) {
            short[] arr = (short[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == int.class) {
            int[] arr = (int[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == long.class) {
            long[] arr = (long[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == char.class) {
            char[] arr = (char[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == float.class) {
            float[] arr = (float[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else if (compClass == double.class) {
            double[] arr = (double[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                result.append( arr[ctr] ) ;
                result.endElement() ;
            }
        } else { // array of object
            java.lang.Object[] arr = (java.lang.Object[])obj ;
            for (int ctr=0; ctr<arr.length; ctr++) {
                result.startElement() ;
                objectToStringHelper( printed, result, arr[ctr] ) ;
                result.endElement() ;
            }
        }
    }

    private static class Pair
    {
        private java.lang.Object obj1 ;
        private java.lang.Object obj2 ;

        Pair( java.lang.Object obj1, java.lang.Object obj2 )
        {
            this.obj1 = obj1 ;
            this.obj2 = obj2 ;
        }

        public boolean equals( java.lang.Object obj )
        {
            if (!(obj instanceof Pair))
                return false ;

            Pair other = (Pair)obj ;
            return other.obj1 == obj1 && other.obj2 == obj2 ;
        }

        public int hashCode()
        {
            return System.identityHashCode( obj1 ) ^
                System.identityHashCode( obj2 ) ;
        }
    }

    private static boolean equalsHelper( Map counterpart, Set considered,
        java.lang.Object obj1, java.lang.Object obj2 )
    {
        if ((obj1 == null) || (obj2 == null))
            return obj1 == obj2 ;

        java.lang.Object other2 = counterpart.get( obj1 ) ;
        if (other2 == null) {
            other2 = obj2 ;
            counterpart.put( obj1, other2 ) ;
        }

        if (obj1 == other2)
            return true ;

        if (obj2 != other2)
            return false ;

        Pair pair = new Pair( obj1, obj2 ) ;
        if (considered.contains( pair ))
            return true ;
        else
            considered.add( pair ) ;

        if (obj1 instanceof java.lang.Object[] &&
            obj2 instanceof java.lang.Object[])
            return equalArrays( counterpart, considered,
                (java.lang.Object[])obj1, (java.lang.Object[])obj2 ) ;
        else if (obj1 instanceof Map && obj2 instanceof Map)
            return equalMaps( counterpart, considered,
                (Map)obj1, (Map)obj2 ) ;
        else if (obj1 instanceof Set && obj2 instanceof Set)
            return equalSets( counterpart, considered,
                (Set)obj1, (Set)obj2 ) ;
        else if (obj1 instanceof List && obj2 instanceof List)
            return equalLists( counterpart, considered,
                (List)obj1, (List)obj2 ) ;
        else if (obj1 instanceof boolean[] && obj2 instanceof boolean[])
            return Arrays.equals( (boolean[])obj1, (boolean[])obj2 ) ;
        else if (obj1 instanceof byte[] && obj2 instanceof byte[])
            return Arrays.equals( (byte[])obj1, (byte[])obj2 ) ;
        else if (obj1 instanceof char[] && obj2 instanceof char[])
            return Arrays.equals( (char[])obj1, (char[])obj2 ) ;
        else if (obj1 instanceof double[] && obj2 instanceof double[])
            return Arrays.equals( (double[])obj1, (double[])obj2 ) ;
        else if (obj1 instanceof float[] && obj2 instanceof float[])
            return Arrays.equals( (float[])obj1, (float[])obj2 ) ;
        else if (obj1 instanceof int[] && obj2 instanceof int[])
            return Arrays.equals( (int[])obj1, (int[])obj2 ) ;
        else if (obj1 instanceof long[] && obj2 instanceof long[])
            return Arrays.equals( (long[])obj1, (long[])obj2 ) ;
        else {
            Class cls = obj1.getClass() ;
            if (cls != obj2.getClass())
                return obj1.equals( obj2 ) ;
            else
                return equalsObject( counterpart, considered, cls, obj1, obj2 ) ;
        }
    }

    private static boolean equalsObject( Map counterpart, Set considered,
        Class cls, java.lang.Object obj1, java.lang.Object obj2 )
    {
        Class objectClass = java.lang.Object.class ;
        if (cls == objectClass)
            return true ;

        Class[] equalsTypes = { objectClass } ;
        try {
            Method equalsMethod = cls.getDeclaredMethod( "equals",
                equalsTypes ) ;
            return obj1.equals( obj2 ) ;
        } catch (Exception exc) {
            if (equalsObjectFields( counterpart, considered,
                    cls, obj1, obj2 ))
                return equalsObject( counterpart, considered,
                    cls.getSuperclass(), obj1, obj2 ) ;
            else
                return false ;
        }
    }

    private static boolean equalsObjectFields( Map counterpart, Set considered,
        Class cls, java.lang.Object obj1, java.lang.Object obj2 )
    {
        Field[] fields = cls.getDeclaredFields() ;
        for (int ctr=0; ctr<fields.length; ctr++) {
            try {
                final Field field = fields[ctr] ;
                // Ignore static fields
                if (!Modifier.isStatic( field.getModifiers())) {
                    AccessController.doPrivileged(new PrivilegedAction() {
                        public Object run() {
                            field.setAccessible( true ) ;
                            return null ;
                        }
                    } ) ;

                    java.lang.Object value1 = field.get( obj1 ) ;
                    java.lang.Object value2 = field.get( obj2 ) ;
                    if (!equalsHelper( counterpart, considered, value1,
                        value2 ))
                        return false ;
                }
            } catch (IllegalAccessException exc) {
                return false ;
            }
        }

        return true ;
    }

    private static boolean equalArrays( Map counterpart, Set considered,
        java.lang.Object[] arr1, java.lang.Object[] arr2 )
    {
        int len = arr1.length ;
        if (len != arr2.length)
            return false ;

        for (int ctr = 0; ctr<len; ctr++ )
            if (!equalsHelper( counterpart, considered, arr1[ctr], arr2[ctr] ))
                return false ;

        return true ;
    }

    private static boolean equalMaps( Map counterpart, Set considered,
        Map map1, Map map2 )
    {
        if (map2.size() != map1.size())
            return false;

        try {
            Iterator i = map1.entrySet().iterator();
            while (i.hasNext()) {
                Entry e = (Entry) i.next();
                java.lang.Object key = e.getKey();
                java.lang.Object value = e.getValue();
                if (value == null) {
                    if (!(map2.get(key)==null && map2.containsKey(key)))
                        return false;
                } else {
                    if (!equalsHelper( counterpart, considered,
                        value, map2.get(key)))
                        return false;
                }
            }
        } catch(ClassCastException unused)   {
            return false;
        } catch(NullPointerException unused) {
            return false;
        }

        return true;
    }

    // Obviously this is an inefficient quadratic algorithm.
    // This is taken pretty directly from AbstractSet and AbstractCollection
    // in the JDK.
    // For HashSet, an O(n) (with a good hash function) algorithm
    // is possible, and likewise TreeSet, since it is
    // ordered, is O(n).  But this is not worth the effort here.
    // Note that the inner loop uses equals, not equalsHelper.
    // This is needed because of the searching behavior of this test.
    // However, note that this will NOT correctly handle sets that
    // contain themselves as members, or that have members that reference
    // themselves.  These cases will cause infinite regress!
    private static boolean equalSets( Map counterpart, Set considered,
        Set set1, Set set2 )
    {
        if (set1.size() != set2.size())
            return false ;

        Iterator e1 = set1.iterator() ;
        while (e1.hasNext()) {
            java.lang.Object obj1 = e1.next() ;

            boolean found = false ;
            Iterator e2 = set2.iterator() ;
            while (e2.hasNext() && !found) {
                java.lang.Object obj2 = e2.next() ;
                found = equals( obj1, obj2 ) ;
            }

            if (!found)
                return false ;
        }

        return true ;
    }

    private static boolean equalLists( Map counterpart, Set considered,
        List list1, List list2 )
    {
        ListIterator e1 = list1.listIterator();
        ListIterator e2 = list2.listIterator();
        while(e1.hasNext() && e2.hasNext()) {
            java.lang.Object o1 = e1.next();
            java.lang.Object o2 = e2.next();
            if (!(o1==null ? o2==null : equalsHelper(
                counterpart, considered, o1, o2)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }
}
