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


package com.sun.xml.internal.ws.transport;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.*;

/**
 * HTTP request and response headers are represented by this class which implements
 * the interface {@link java.util.Map}&lt;
 * {@link java.lang.String},{@link java.util.List}&lt;{@link java.lang.String}&gt;&gt;.
 * The keys are case-insensitive Strings representing the header names and
 * the value associated with each key is a {@link List}&lt;{@link String}&gt; with one
 * element for each occurence of the header name in the request or response.
 * <p>
 * For example, if a response header instance contains one key "HeaderName" with two values "value1 and value2"
 * then this object is output as two header lines:
 * <blockquote><pre>
 * HeaderName: value1
 * HeaderName: value2
 * </blockquote></pre>
 * <p>
 * All the normal {@link java.util.Map} methods are provided, but the following
 * additional convenience methods are most likely to be used:
 * <ul>
 * <li>{@link #getFirst(String)} returns a single valued header or the first value of
 * a multi-valued header.</li>
 * <li>{@link #add(String,String)} adds the given header value to the list for the given key</li>
 * <li>{@link #set(String,String)} sets the given header field to the single value given
 * overwriting any existing values in the value list.
 * </ul><p>
 * All methods in this class accept <code>null</code> values for keys and values. However, null
 * keys will never will be present in HTTP request headers, and will not be output/sent in response headers.
 * Null values can be represented as either a null entry for the key (i.e. the list is null) or
 * where the key has a list, but one (or more) of the list's values is null. Null values are output
 * as a header line containing the key but no associated value.
 * @since 1.6
 */
public class Headers implements Map<String,List<String>> {

    HashMap<String,List<String>> map;

    public Headers () {
        map = new HashMap<String,List<String>>(32);
    }

    /* Normalize the key by converting to following form.
     * First char upper case, rest lower case.
     * key is presumed to be ASCII
     */
    private String normalize (String key) {
        if (key == null) {
            return null;
        }
        int len = key.length();
        if (len == 0) {
            return key;
        }
        char[] b = new char [len];
        String s = null;
            b = key.toCharArray();
            if (b[0] >= 'a' && b[0] <= 'z') {
                b[0] = (char)(b[0] - ('a' - 'A'));
            }
            for (int i=1; i<len; i++) {
                if (b[i] >= 'A' && b[i] <= 'Z') {
                    b[i] = (char) (b[i] + ('a' - 'A'));
                }
            }
            s = new String (b);
        return s;
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object key) {
        if (key == null) {
            return false;
        }
        if (!(key instanceof String)) {
            return false;
        }
        return map.containsKey (normalize((String)key));
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public List<String> get(Object key) {
        return map.get(normalize((String)key));
    }

    /**
     * returns the first value from the List of String values
     * for the given key (if at least one exists).
     * @param key the key to search for
     * @return the first string value associated with the key
     */
    public String getFirst (String key) {
        List<String> l = map.get(normalize((String)key));
        if (l == null) {
            return null;
        }
        return l.get(0);
    }

    public List<String> put(String key, List<String> value) {
        return map.put (normalize(key), value);
    }

    /**
     * adds the given value to the list of headers
     * for the given key. If the mapping does not
     * already exist, then it is created
     * @param key the header name
     * @param value the header value to add to the header
     */
    public void add (String key, String value) {
        String k = normalize(key);
        List<String> l = map.get(k);
        if (l == null) {
            l = new LinkedList<String>();
            map.put(k,l);
        }
        l.add (value);
    }

    /**
     * sets the given value as the sole header value
     * for the given key. If the mapping does not
     * already exist, then it is created
     * @param key the header name
     * @param value the header value to set.
     */
    public void set (String key, String value) {
        LinkedList<String> l = new LinkedList<String>();
        l.add (value);
        put (key, l);
    }


    public List<String> remove(Object key) {
        return map.remove(normalize((String)key));
    }

    public void putAll(Map<? extends String,? extends List<String>> t)  {
        for(Map.Entry<? extends String, ? extends List<String>> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public void clear() {
        map.clear();
    }

    public Set<String> keySet() {
        return map.keySet();
    }

    public Collection<List<String>> values() {
        return map.values();
    }

    public Set<Map.Entry<String, List<String>>> entrySet() {
        return map.entrySet();
    }

    public boolean equals(Object o) {
        return map.equals(o);
    }

    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
