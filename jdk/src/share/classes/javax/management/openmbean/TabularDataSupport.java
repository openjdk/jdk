/*
 * Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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


package javax.management.openmbean;


// java import
//
import com.sun.jmx.mbeanserver.GetPropertyAction;
import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// jmx import
//


/**
 * The <tt>TabularDataSupport</tt> class is the <i>open data</i> class which implements the <tt>TabularData</tt>
 * and the <tt>Map</tt> interfaces, and which is internally based on a hash map data structure.
 *
 * @since 1.5
 */
/* It would make much more sense to implement
   Map<List<?>,CompositeData> here, but unfortunately we cannot for
   compatibility reasons.  If we did that, then we would have to
   define e.g.
   CompositeData remove(Object)
   instead of
   Object remove(Object).

   That would mean that if any existing code subclassed
   TabularDataSupport and overrode
   Object remove(Object),
   it would (a) no longer compile and (b) not actually override
   CompositeData remove(Object)
   in binaries compiled before the change.
*/
public class TabularDataSupport
    implements TabularData, Map<Object,Object>,
               Cloneable, Serializable {


    /* Serial version */
    static final long serialVersionUID = 5720150593236309827L;


    /**
     * @serial This tabular data instance's contents: a {@link HashMap}
     */
    // field cannot be final because of clone method
    private Map<Object,CompositeData> dataMap;

    /**
     * @serial This tabular data instance's tabular type
     */
    private final TabularType tabularType;

    /**
     * The array of item names that define the index used for rows (convenience field)
     */
    private transient String[] indexNamesArray;



    /* *** Constructors *** */


    /**
     * Creates an empty <tt>TabularDataSupport</tt> instance whose open-type is <var>tabularType</var>,
     * and whose underlying <tt>HashMap</tt> has a default initial capacity (101) and default load factor (0.75).
     * <p>
     * This constructor simply calls <tt>this(tabularType, 101, 0.75f);</tt>
     *
     * @param  tabularType               the <i>tabular type</i> describing this <tt>TabularData</tt> instance;
     *                                   cannot be null.
     *
     * @throws IllegalArgumentException  if the tabular type is null.
     */
    public TabularDataSupport(TabularType tabularType) {

        this(tabularType, 16, 0.75f);
    }

    /**
     * Creates an empty <tt>TabularDataSupport</tt> instance whose open-type is <var>tabularType</var>,
     * and whose underlying <tt>HashMap</tt> has the specified initial capacity and load factor.
     *
     * @param  tabularType               the <i>tabular type</i> describing this <tt>TabularData</tt> instance;
     *                           cannot be null.
     *
     * @param  initialCapacity   the initial capacity of the HashMap.
     *
     * @param  loadFactor        the load factor of the HashMap
     *
     * @throws IllegalArgumentException  if the initial capacity is less than zero,
     *                                   or the load factor is nonpositive,
     *                                   or the tabular type is null.
     */
    public TabularDataSupport(TabularType tabularType, int initialCapacity, float loadFactor) {

        // Check tabularType is not null
        //
        if (tabularType == null) {
            throw new IllegalArgumentException("Argument tabularType cannot be null.");
        }

        // Initialize this.tabularType (and indexNamesArray for convenience)
        //
        this.tabularType = tabularType;
        List<String> tmpNames = tabularType.getIndexNames();
        this.indexNamesArray = tmpNames.toArray(new String[tmpNames.size()]);

        // Since LinkedHashMap was introduced in SE 1.4, it's conceivable even
        // if very unlikely that we might be the server of a 1.3 client.  In
        // that case you'll need to set this property.  See CR 6334663.
        String useHashMapProp = AccessController.doPrivileged(
                new GetPropertyAction("jmx.tabular.data.hash.map"));
        boolean useHashMap = "true".equalsIgnoreCase(useHashMapProp);

        // Construct the empty contents HashMap
        //
        this.dataMap = useHashMap ?
            new HashMap<Object,CompositeData>(initialCapacity, loadFactor) :
            new LinkedHashMap<Object, CompositeData>(initialCapacity, loadFactor);
    }




    /* *** TabularData specific information methods *** */


    /**
     * Returns the <i>tabular type</i> describing this <tt>TabularData</tt> instance.
     */
    public TabularType getTabularType() {

        return tabularType;
    }

    /**
     * Calculates the index that would be used in this <tt>TabularData</tt> instance to refer to the specified
     * composite data <var>value</var> parameter if it were added to this instance.
     * This method checks for the type validity of the specified <var>value</var>,
     * but does not check if the calculated index is already used to refer to a value in this <tt>TabularData</tt> instance.
     *
     * @param  value                      the composite data value whose index in this
     *                                    <tt>TabularData</tt> instance is to be calculated;
     *                                    must be of the same composite type as this instance's row type;
     *                                    must not be null.
     *
     * @return the index that the specified <var>value</var> would have in this <tt>TabularData</tt> instance.
     *
     * @throws NullPointerException       if <var>value</var> is <tt>null</tt>.
     *
     * @throws InvalidOpenTypeException   if <var>value</var> does not conform to this <tt>TabularData</tt> instance's
     *                                    row type definition.
     */
    public Object[] calculateIndex(CompositeData value) {

        // Check value is valid
        //
        checkValueType(value);

        // Return its calculated index
        //
        return internalCalculateIndex(value).toArray();
    }




    /* *** Content information query methods *** */


    /**
     * Returns <tt>true</tt> if and only if this <tt>TabularData</tt> instance contains a <tt>CompositeData</tt> value
     * (ie a row) whose index is the specified <var>key</var>. If <var>key</var> cannot be cast to a one dimension array
     * of Object instances, this method simply returns <tt>false</tt>; otherwise it returns the the result of the call to
     * <tt>this.containsKey((Object[]) key)</tt>.
     *
     * @param  key  the index value whose presence in this <tt>TabularData</tt> instance is to be tested.
     *
     * @return  <tt>true</tt> if this <tt>TabularData</tt> indexes a row value with the specified key.
     */
    public boolean containsKey(Object key) {

        // if key is not an array of Object instances, return false
        //
        Object[] k;
        try {
            k = (Object[]) key;
        } catch (ClassCastException e) {
            return false;
        }

        return  this.containsKey(k);
    }

    /**
     * Returns <tt>true</tt> if and only if this <tt>TabularData</tt> instance contains a <tt>CompositeData</tt> value
     * (ie a row) whose index is the specified <var>key</var>. If <var>key</var> is <tt>null</tt> or does not conform to
     * this <tt>TabularData</tt> instance's <tt>TabularType</tt> definition, this method simply returns <tt>false</tt>.
     *
     * @param  key  the index value whose presence in this <tt>TabularData</tt> instance is to be tested.
     *
     * @return  <tt>true</tt> if this <tt>TabularData</tt> indexes a row value with the specified key.
     */
    public boolean containsKey(Object[] key) {

        return  ( key == null ? false : dataMap.containsKey(Arrays.asList(key)));
    }

    /**
     * Returns <tt>true</tt> if and only if this <tt>TabularData</tt> instance contains the specified
     * <tt>CompositeData</tt> value. If <var>value</var> is <tt>null</tt> or does not conform to
     * this <tt>TabularData</tt> instance's row type definition, this method simply returns <tt>false</tt>.
     *
     * @param  value  the row value whose presence in this <tt>TabularData</tt> instance is to be tested.
     *
     * @return  <tt>true</tt> if this <tt>TabularData</tt> instance contains the specified row value.
     */
    public boolean containsValue(CompositeData value) {

        return dataMap.containsValue(value);
    }

    /**
     * Returns <tt>true</tt> if and only if this <tt>TabularData</tt> instance contains the specified
     * value.
     *
     * @param  value  the row value whose presence in this <tt>TabularData</tt> instance is to be tested.
     *
     * @return  <tt>true</tt> if this <tt>TabularData</tt> instance contains the specified row value.
     */
    public boolean containsValue(Object value) {

        return dataMap.containsValue(value);
    }

    /**
     * This method simply calls <tt>get((Object[]) key)</tt>.
     *
     * @throws NullPointerException  if the <var>key</var> is <tt>null</tt>
     * @throws ClassCastException    if the <var>key</var> is not of the type <tt>Object[]</tt>
     * @throws InvalidKeyException   if the <var>key</var> does not conform to this <tt>TabularData</tt> instance's
     *                               <tt>TabularType</tt> definition
     */
    public Object get(Object key) {

        return get((Object[]) key);
    }

    /**
     * Returns the <tt>CompositeData</tt> value whose index is
     * <var>key</var>, or <tt>null</tt> if there is no value mapping
     * to <var>key</var>, in this <tt>TabularData</tt> instance.
     *
     * @param key the index of the value to get in this
     * <tt>TabularData</tt> instance; * must be valid with this
     * <tt>TabularData</tt> instance's row type definition; * must not
     * be null.
     *
     * @return the value corresponding to <var>key</var>.
     *
     * @throws NullPointerException  if the <var>key</var> is <tt>null</tt>
     * @throws InvalidKeyException   if the <var>key</var> does not conform to this <tt>TabularData</tt> instance's
     *                               <tt>TabularType</tt> type definition.
     */
    public CompositeData get(Object[] key) {

        // Check key is not null and valid with tabularType
        // (throws NullPointerException, InvalidKeyException)
        //
        checkKeyType(key);

        // Return the mapping stored in the parent HashMap
        //
        return dataMap.get(Arrays.asList(key));
    }




    /* *** Content modification operations (one element at a time) *** */


    /**
     * This method simply calls <tt>put((CompositeData) value)</tt> and
     * therefore ignores its <var>key</var> parameter which can be <tt>null</tt>.
     *
     * @param key an ignored parameter.
     * @param value the {@link CompositeData} to put.
     *
     * @return the value which is put
     *
     * @throws NullPointerException  if the <var>value</var> is <tt>null</tt>
     * @throws ClassCastException if the <var>value</var> is not of
     * the type <tt>CompositeData</tt>
     * @throws InvalidOpenTypeException if the <var>value</var> does
     * not conform to this <tt>TabularData</tt> instance's
     * <tt>TabularType</tt> definition
     * @throws KeyAlreadyExistsException if the key for the
     * <var>value</var> parameter, calculated according to this
     * <tt>TabularData</tt> instance's <tt>TabularType</tt> definition
     * already maps to an existing value
     */
    public Object put(Object key, Object value) {
        internalPut((CompositeData) value);
        return value; // should be return internalPut(...); (5090566)
    }

    public void put(CompositeData value) {
        internalPut(value);
    }

    private CompositeData internalPut(CompositeData value) {
        // Check value is not null, value's type is the same as this instance's row type,
        // and calculate the value's index according to this instance's tabularType and
        // check it is not already used for a mapping in the parent HashMap
        //
        List<?> index = checkValueAndIndex(value);

        // store the (key, value) mapping in the dataMap HashMap
        //
        return dataMap.put(index, value);
    }

    /**
     * This method simply calls <tt>remove((Object[]) key)</tt>.
     *
     * @param key an <tt>Object[]</tt> representing the key to remove.
     *
     * @return previous value associated with specified key, or <tt>null</tt>
     *         if there was no mapping for key.
     *
     * @throws NullPointerException  if the <var>key</var> is <tt>null</tt>
     * @throws ClassCastException    if the <var>key</var> is not of the type <tt>Object[]</tt>
     * @throws InvalidKeyException   if the <var>key</var> does not conform to this <tt>TabularData</tt> instance's
     *                               <tt>TabularType</tt> definition
     */
    public Object remove(Object key) {

        return remove((Object[]) key);
    }

    /**
     * Removes the <tt>CompositeData</tt> value whose index is <var>key</var> from this <tt>TabularData</tt> instance,
     * and returns the removed value, or returns <tt>null</tt> if there is no value whose index is <var>key</var>.
     *
     * @param  key  the index of the value to get in this <tt>TabularData</tt> instance;
     *              must be valid with this <tt>TabularData</tt> instance's row type definition;
     *              must not be null.
     *
     * @return previous value associated with specified key, or <tt>null</tt>
     *         if there was no mapping for key.
     *
     * @throws NullPointerException  if the <var>key</var> is <tt>null</tt>
     * @throws InvalidKeyException   if the <var>key</var> does not conform to this <tt>TabularData</tt> instance's
     *                               <tt>TabularType</tt> definition
     */
    public CompositeData remove(Object[] key) {

        // Check key is not null and valid with tabularType
        // (throws NullPointerException, InvalidKeyException)
        //
        checkKeyType(key);

        // Removes the (key, value) mapping in the parent HashMap
        //
        return dataMap.remove(Arrays.asList(key));
    }



    /* ***   Content modification bulk operations   *** */


    /**
     * Add all the values contained in the specified map <var>t</var>
     * to this <tt>TabularData</tt> instance.  This method converts
     * the collection of values contained in this map into an array of
     * <tt>CompositeData</tt> values, if possible, and then call the
     * method <tt>putAll(CompositeData[])</tt>. Note that the keys
     * used in the specified map <var>t</var> are ignored. This method
     * allows, for example to add the content of another
     * <tt>TabularData</tt> instance with the same row type (but
     * possibly different index names) into this instance.
     *
     * @param t the map whose values are to be added as new rows to
     * this <tt>TabularData</tt> instance; if <var>t</var> is
     * <tt>null</tt> or empty, this method returns without doing
     * anything.
     *
     * @throws NullPointerException if a value in <var>t</var> is
     * <tt>null</tt>.
     * @throws ClassCastException if a value in <var>t</var> is not an
     * instance of <tt>CompositeData</tt>.
     * @throws InvalidOpenTypeException if a value in <var>t</var>
     * does not conform to this <tt>TabularData</tt> instance's row
     * type definition.
     * @throws KeyAlreadyExistsException if the index for a value in
     * <var>t</var>, calculated according to this
     * <tt>TabularData</tt> instance's <tt>TabularType</tt> definition
     * already maps to an existing value in this instance, or two
     * values in <var>t</var> have the same index.
     */
    public void putAll(Map<?,?> t) {

        // if t is null or empty, just return
        //
        if ( (t == null) || (t.size() == 0) ) {
            return;
        }

        // Convert the values in t into an array of <tt>CompositeData</tt>
        //
        CompositeData[] values;
        try {
            values =
                t.values().toArray(new CompositeData[t.size()]);
        } catch (java.lang.ArrayStoreException e) {
            throw new ClassCastException("Map argument t contains values which are not instances of <tt>CompositeData</tt>");
        }

        // Add the array of values
        //
        putAll(values);
    }

    /**
     * Add all the elements in <var>values</var> to this
     * <tt>TabularData</tt> instance.  If any element in
     * <var>values</var> does not satisfy the constraints defined in
     * {@link #put(CompositeData) <tt>put</tt>}, or if any two
     * elements in <var>values</var> have the same index calculated
     * according to this <tt>TabularData</tt> instance's
     * <tt>TabularType</tt> definition, then an exception describing
     * the failure is thrown and no element of <var>values</var> is
     * added, thus leaving this <tt>TabularData</tt> instance
     * unchanged.
     *
     * @param values the array of composite data values to be added as
     * new rows to this <tt>TabularData</tt> instance; if
     * <var>values</var> is <tt>null</tt> or empty, this method
     * returns without doing anything.
     *
     * @throws NullPointerException if an element of <var>values</var>
     * is <tt>null</tt>
     * @throws InvalidOpenTypeException if an element of
     * <var>values</var> does not conform to this
     * <tt>TabularData</tt> instance's row type definition (ie its
     * <tt>TabularType</tt> definition)
     * @throws KeyAlreadyExistsException if the index for an element
     * of <var>values</var>, calculated according to this
     * <tt>TabularData</tt> instance's <tt>TabularType</tt> definition
     * already maps to an existing value in this instance, or two
     * elements of <var>values</var> have the same index
     */
    public void putAll(CompositeData[] values) {

        // if values is null or empty, just return
        //
        if ( (values == null) || (values.length == 0) ) {
            return;
        }

        // create the list of indexes corresponding to each value
        List<List<?>> indexes =
            new ArrayList<List<?>>(values.length + 1);

        // Check all elements in values and build index list
        //
        List<?> index;
        for (int i=0; i<values.length; i++) {
            // check value and calculate index
            index = checkValueAndIndex(values[i]);
            // check index is different of those previously calculated
            if (indexes.contains(index)) {
                throw new KeyAlreadyExistsException("Argument elements values["+ i +"] and values["+ indexes.indexOf(index) +
                                                    "] have the same indexes, "+
                                                    "calculated according to this TabularData instance's tabularType.");
            }
            // add to index list
            indexes.add(index);
        }

        // store all (index, value) mappings in the dataMap HashMap
        //
        for (int i=0; i<values.length; i++) {
            dataMap.put(indexes.get(i), values[i]);
        }
    }

    /**
     * Removes all rows from this <code>TabularDataSupport</code> instance.
     */
    public void clear() {

        dataMap.clear();
    }



    /* ***  Informational methods from java.util.Map  *** */

    /**
     * Returns the number of rows in this <code>TabularDataSupport</code> instance.
     *
     * @return the number of rows in this <code>TabularDataSupport</code> instance.
     */
    public int size() {

        return dataMap.size();
    }

    /**
     * Returns <tt>true</tt> if this <code>TabularDataSupport</code> instance contains no rows.
     *
     * @return <tt>true</tt> if this <code>TabularDataSupport</code> instance contains no rows.
     */
    public boolean isEmpty() {

        return (this.size() == 0);
    }



    /* ***  Collection views from java.util.Map  *** */

    /**
     * Returns a set view of the keys contained in the underlying map of this
     * {@code TabularDataSupport} instance used to index the rows.
     * Each key contained in this {@code Set} is an unmodifiable {@code List<?>}
     * so the returned set view is a {@code Set<List<?>>} but is declared as a
     * {@code Set<Object>} for compatibility reasons.
     * The set is backed by the underlying map of this
     * {@code TabularDataSupport} instance, so changes to the
     * {@code TabularDataSupport} instance are reflected in the
     * set, and vice-versa.
     *
     * The set supports element removal, which removes the corresponding
     * row from this {@code TabularDataSupport} instance, via the
     * {@link Iterator#remove}, {@link Set#remove}, {@link Set#removeAll},
     * {@link Set#retainAll}, and {@link Set#clear} operations. It does
     *  not support the {@link Set#add} or {@link Set#addAll} operations.
     *
     * @return a set view ({@code Set<List<?>>}) of the keys used to index
     * the rows of this {@code TabularDataSupport} instance.
     */
    public Set<Object> keySet() {

        return dataMap.keySet() ;
    }

    /**
     * Returns a collection view of the rows contained in this
     * {@code TabularDataSupport} instance. The returned {@code Collection}
     * is a {@code Collection<CompositeData>} but is declared as a
     * {@code Collection<Object>} for compatibility reasons.
     * The returned collection can be used to iterate over the values.
     * The collection is backed by the underlying map, so changes to the
     * {@code TabularDataSupport} instance are reflected in the collection,
     * and vice-versa.
     *
     * The collection supports element removal, which removes the corresponding
     * index to row mapping from this {@code TabularDataSupport} instance, via
     * the {@link Iterator#remove}, {@link Collection#remove},
     * {@link Collection#removeAll}, {@link Collection#retainAll},
     * and {@link Collection#clear} operations. It does not support
     * the {@link Collection#add} or {@link Collection#addAll} operations.
     *
     * @return a collection view ({@code Collection<CompositeData>}) of
     * the values contained in this {@code TabularDataSupport} instance.
     */
    @SuppressWarnings("unchecked")  // historical confusion about the return type
    public Collection<Object> values() {

        return Util.cast(dataMap.values());
    }


    /**
     * Returns a collection view of the index to row mappings
     * contained in this {@code TabularDataSupport} instance.
     * Each element in the returned collection is
     * a {@code Map.Entry<List<?>,CompositeData>} but
     * is declared as a {@code Map.Entry<Object,Object>}
     * for compatibility reasons. Each of the map entry
     * keys is an unmodifiable {@code List<?>}.
     * The collection is backed by the underlying map of this
     * {@code TabularDataSupport} instance, so changes to the
     * {@code TabularDataSupport} instance are reflected in
     * the collection, and vice-versa.
     * The collection supports element removal, which removes
     * the corresponding mapping from the map, via the
     * {@link Iterator#remove}, {@link Collection#remove},
     * {@link Collection#removeAll}, {@link Collection#retainAll},
     * and {@link Collection#clear} operations. It does not support
     * the {@link Collection#add} or {@link Collection#addAll}
     * operations.
     * <p>
     * <b>IMPORTANT NOTICE</b>: Do not use the {@code setValue} method of the
     * {@code Map.Entry} elements contained in the returned collection view.
     * Doing so would corrupt the index to row mappings contained in this
     * {@code TabularDataSupport} instance.
     *
     * @return a collection view ({@code Set<Map.Entry<List<?>,CompositeData>>})
     * of the mappings contained in this map.
     * @see java.util.Map.Entry
     */
    @SuppressWarnings("unchecked")  // historical confusion about the return type
    public Set<Map.Entry<Object,Object>> entrySet() {

        return Util.cast(dataMap.entrySet());
    }


    /* ***  Commodity methods from java.lang.Object  *** */


    /**
     * Returns a clone of this <code>TabularDataSupport</code> instance:
     * the clone is obtained by calling <tt>super.clone()</tt>, and then cloning the underlying map.
     * Only a shallow clone of the underlying map is made, i.e. no cloning of the indexes and row values is made as they are immutable.
     */
    /* We cannot use covariance here and return TabularDataSupport
       because this would fail with existing code that subclassed
       TabularDataSupport and overrode Object clone().  It would not
       override the new clone().  */
    public Object clone() {
        try {
            TabularDataSupport c = (TabularDataSupport) super.clone();
            c.dataMap = new HashMap<Object,CompositeData>(c.dataMap);
            return c;
        }
        catch (CloneNotSupportedException e) {
            throw new InternalError(e.toString());
        }
    }


    /**
     * Compares the specified <var>obj</var> parameter with this <code>TabularDataSupport</code> instance for equality.
     * <p>
     * Returns <tt>true</tt> if and only if all of the following statements are true:
     * <ul>
     * <li><var>obj</var> is non null,</li>
     * <li><var>obj</var> also implements the <code>TabularData</code> interface,</li>
     * <li>their tabular types are equal</li>
     * <li>their contents (ie all CompositeData values) are equal.</li>
     * </ul>
     * This ensures that this <tt>equals</tt> method works properly for <var>obj</var> parameters which are
     * different implementations of the <code>TabularData</code> interface.
     * <br>&nbsp;
     * @param  obj  the object to be compared for equality with this <code>TabularDataSupport</code> instance;
     *
     * @return  <code>true</code> if the specified object is equal to this <code>TabularDataSupport</code> instance.
     */
    public boolean equals(Object obj) {

        // if obj is null, return false
        //
        if (obj == null) {
            return false;
        }

        // if obj is not a TabularData, return false
        //
        TabularData other;
        try {
            other = (TabularData) obj;
        } catch (ClassCastException e) {
            return false;
        }

        // Now, really test for equality between this TabularData implementation and the other:
        //

        // their tabularType should be equal
        if ( ! this.getTabularType().equals(other.getTabularType()) ) {
            return false;
        }

        // their contents should be equal:
        // . same size
        // . values in this instance are in the other (we know there are no duplicate elements possible)
        // (row values comparison is enough, because keys are calculated according to tabularType)

        if (this.size() != other.size()) {
            return false;
        }
        for (CompositeData value : dataMap.values()) {
            if ( ! other.containsValue(value) ) {
                return false;
            }
        }

        // All tests for equality were successfull
        //
        return true;
    }

    /**
     * Returns the hash code value for this <code>TabularDataSupport</code> instance.
     * <p>
     * The hash code of a <code>TabularDataSupport</code> instance is the sum of the hash codes
     * of all elements of information used in <code>equals</code> comparisons
     * (ie: its <i>tabular type</i> and its content, where the content is defined as all the CompositeData values).
     * <p>
     * This ensures that <code> t1.equals(t2) </code> implies that <code> t1.hashCode()==t2.hashCode() </code>
     * for any two <code>TabularDataSupport</code> instances <code>t1</code> and <code>t2</code>,
     * as required by the general contract of the method
     * {@link Object#hashCode() Object.hashCode()}.
     * <p>
     * However, note that another instance of a class implementing the <code>TabularData</code> interface
     * may be equal to this <code>TabularDataSupport</code> instance as defined by {@link #equals},
     * but may have a different hash code if it is calculated differently.
     *
     * @return  the hash code value for this <code>TabularDataSupport</code> instance
     */
   public int hashCode() {

        int result = 0;

        result += this.tabularType.hashCode();
        for (Object value : values())
            result += value.hashCode();

        return result;

    }

    /**
     * Returns a string representation of this <code>TabularDataSupport</code> instance.
     * <p>
     * The string representation consists of the name of this class (ie <code>javax.management.openmbean.TabularDataSupport</code>),
     * the string representation of the tabular type of this instance, and the string representation of the contents
     * (ie list the key=value mappings as returned by a call to
     * <tt>dataMap.</tt>{@link java.util.HashMap#toString() toString()}).
     *
     * @return  a string representation of this <code>TabularDataSupport</code> instance
     */
    public String toString() {

        return new StringBuilder()
            .append(this.getClass().getName())
            .append("(tabularType=")
            .append(tabularType.toString())
            .append(",contents=")
            .append(dataMap.toString())
            .append(")")
            .toString();
    }




    /* *** TabularDataSupport internal utility methods *** */


    /**
     * Returns the index for value, assuming value is valid for this <tt>TabularData</tt> instance
     * (ie value is not null, and its composite type is equal to row type).
     *
     * The index is a List, and not an array, so that an index.equals(otherIndex) call will actually compare contents,
     * not just the objects references as is done for an array object.
     *
     * The returned List is unmodifiable so that once a row has been put into the dataMap, its index cannot be modified,
     * for example by a user that would attempt to modify an index contained in the Set returned by keySet().
     */
    private List<?> internalCalculateIndex(CompositeData value) {

        return Collections.unmodifiableList(Arrays.asList(value.getAll(this.indexNamesArray)));
    }

    /**
     * Checks if the specified key is valid for this <tt>TabularData</tt> instance.
     *
     * @throws  NullPointerException
     * @throws  InvalidOpenTypeException
     */
    private void checkKeyType(Object[] key) {

        // Check key is neither null nor empty
        //
        if ( (key == null) || (key.length == 0) ) {
            throw new NullPointerException("Argument key cannot be null or empty.");
        }

        /* Now check key is valid with tabularType index and row type definitions: */

        // key[] should have the size expected for an index
        //
        if (key.length != this.indexNamesArray.length) {
            throw new InvalidKeyException("Argument key's length="+ key.length +
                                          " is different from the number of item values, which is "+ indexNamesArray.length +
                                          ", specified for the indexing rows in this TabularData instance.");
        }

        // each element in key[] should be a value for its corresponding open type specified in rowType
        //
        OpenType<?> keyElementType;
        for (int i=0; i<key.length; i++) {
            keyElementType = tabularType.getRowType().getType(this.indexNamesArray[i]);
            if ( (key[i] != null) && (! keyElementType.isValue(key[i])) ) {
                throw new InvalidKeyException("Argument element key["+ i +"] is not a value for the open type expected for "+
                                              "this element of the index, whose name is \""+ indexNamesArray[i] +
                                              "\" and whose open type is "+ keyElementType);
            }
        }
    }

    /**
     * Checks the specified value's type is valid for this <tt>TabularData</tt> instance
     * (ie value is not null, and its composite type is equal to row type).
     *
     * @throws  NullPointerException
     * @throws  InvalidOpenTypeException
     */
    private void checkValueType(CompositeData value) {

        // Check value is not null
        //
        if (value == null) {
            throw new NullPointerException("Argument value cannot be null.");
        }

        // if value's type is not the same as this instance's row type, throw InvalidOpenTypeException
        //
        if (!tabularType.getRowType().isValue(value)) {
            throw new InvalidOpenTypeException("Argument value's composite type ["+ value.getCompositeType() +
                                               "] is not assignable to "+
                                               "this TabularData instance's row type ["+ tabularType.getRowType() +"].");
        }
    }

    /**
     * Checks if the specified value can be put (ie added) in this <tt>TabularData</tt> instance
     * (ie value is not null, its composite type is equal to row type, and its index is not already used),
     * and returns the index calculated for this value.
     *
     * The index is a List, and not an array, so that an index.equals(otherIndex) call will actually compare contents,
     * not just the objects references as is done for an array object.
     *
     * @throws  NullPointerException
     * @throws  InvalidOpenTypeException
     * @throws  KeyAlreadyExistsException
     */
    private List<?> checkValueAndIndex(CompositeData value) {

        // Check value is valid
        //
        checkValueType(value);

        // Calculate value's index according to this instance's tabularType
        // and check it is not already used for a mapping in the parent HashMap
        //
        List<?> index = internalCalculateIndex(value);

        if (dataMap.containsKey(index)) {
            throw new KeyAlreadyExistsException("Argument value's index, calculated according to this TabularData "+
                                                "instance's tabularType, already refers to a value in this table.");
        }

        // The check is OK, so return the index
        //
        return index;
    }

    /**
     * Deserializes a {@link TabularDataSupport} from an {@link ObjectInputStream}.
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      List<String> tmpNames = tabularType.getIndexNames();
      indexNamesArray = tmpNames.toArray(new String[tmpNames.size()]);
    }
}
