/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.transport;

import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * HTTP request and response headers are represented by this class which implements
 * the interface {@link java.util.Map}&lt;{@link String},
 * {@link List}&lt;{@link String}&gt;&gt;.
 * The keys are case-insensitive Strings representing the header names and
 * the value associated with each key is a {@link List}&lt;{@link String}&gt; with one
 * element for each occurrence of the header name in the request or response.
 * <p>
 * For example, if the request has the the following headers:
 * <blockquote><pre>
 * HeaderName: value1
 * HeadernaMe: value2
 * </blockquote></pre>
 * Then get("hEaDeRnAmE") would give both "value1", and "value2" values in a list
 * <p>
 * All the normal {@link Map} methods are provided, but the following
 * additional convenience methods are most likely to be used:
 * <ul>
 * <li>{@link #getFirst(String)} returns a single valued header or the first
 * value of a multi-valued header.</li>
 * <li>{@link #add(String,String)} adds the given header value to the list
 * for the given key</li>
 * <li>{@link #set(String,String)} sets the given header field to the single
 * value given overwriting any existing values in the value list.
 * </ul><p>
 * All methods in this class accept <code>null</code> values for keys and values.
 * However, null keys will never will be present in HTTP request headers, and
 * will not be output/sent in response headers. Null values can be represented
 * as either a null entry for the key (i.e. the list is null) or where the key
 * has a list, but one (or more) of the list's values is null. Null values are
 * output as a header line containing the key but no associated value.
 *
 * @author Jitendra Kotamraju
 */
public class Headers extends TreeMap<String,List<String>> {

    public Headers() {
        super(INSTANCE);
    }

    private static final InsensitiveComparator INSTANCE = new InsensitiveComparator();

    // case-insensitive string comparison of HTTP header names.
    private static final class InsensitiveComparator implements Comparator<String>, Serializable {
        public int compare(String o1, String o2) {
            if (o1 == null && o2 == null)
                return 0;
            if (o1 == null)
                return -1;
            if (o2 == null)
                return 1;
            return o1.compareToIgnoreCase(o2);
        }
    }

    /**
     * Adds the given value to the list of headers for the given key. If the
     * mapping does not already exist, then it is created.
     *
     * @param key the header name
     * @param value the header value to add to the header
     */
    public void add (String key, String value) {
        List<String> list = this.get(key);
        if (list == null) {
            list = new LinkedList<String>();
            put(key,list);
        }
        list.add (value);
   }

    /**
     * Returns the first value from the List of String values for the given key
     * (if at least one exists).
     *
     * @param key the key to search for
     * @return the first string value associated with the key
     */
    public String getFirst (String key) {
        List<String> l = get(key);
        return (l == null) ? null : l.get(0);
    }

    /**
     * Sets the given value as the sole header value for the given key. If the
     * mapping does not already exist, then it is created.
     *
     * @param key the header name
     * @param value the header value to set.
     */
    public void set (String key, String value) {
        LinkedList<String> l = new LinkedList<String>();
        l.add (value);
        put(key, l);
    }

}
