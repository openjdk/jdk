/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.Pack200;

/**
 * Control block for publishing Pack200 options to the other classes.
 */

final class PropMap implements SortedMap<String, String>  {
    private final TreeMap<String, String> theMap = new TreeMap<>();;

    // Override:
    public String put(String key, String value) {
        String oldValue = theMap.put(key, value);
        return oldValue;
    }

    // All this other stuff is private to the current package.
    // Outide clients of Pack200 do not need to use it; they can
    // get by with generic SortedMap functionality.
    private static Map<String, String> defaultProps;
    static {
        Properties props = new Properties();

        // Allow implementation selected via -Dpack.disable.native=true
        String propValue = getPropertyValue(Utils.DEBUG_DISABLE_NATIVE, "false");
        props.put(Utils.DEBUG_DISABLE_NATIVE,
                  String.valueOf(Boolean.parseBoolean(propValue)));

        // Set the DEBUG_VERBOSE from system
        int verbose = 0;
        try {
            verbose = Integer.decode(getPropertyValue(Utils.DEBUG_VERBOSE, "0"));
        } catch (NumberFormatException e) {
        }
        props.put(Utils.DEBUG_VERBOSE, String.valueOf(verbose));

        // The segment size is unlimited
        props.put(Pack200.Packer.SEGMENT_LIMIT, "-1");

        // Preserve file ordering by default.
        props.put(Pack200.Packer.KEEP_FILE_ORDER, Pack200.Packer.TRUE);

        // Preserve all modification times by default.
        props.put(Pack200.Packer.MODIFICATION_TIME, Pack200.Packer.KEEP);

        // Preserve deflation hints by default.
        props.put(Pack200.Packer.DEFLATE_HINT, Pack200.Packer.KEEP);

        // Pass through files with unrecognized attributes by default.
        props.put(Pack200.Packer.UNKNOWN_ATTRIBUTE, Pack200.Packer.PASS);

        // Pass through files with unrecognized format by default, also
        // allow system property to be set
        props.put(Utils.CLASS_FORMAT_ERROR,
                  getPropertyValue(Utils.CLASS_FORMAT_ERROR, Pack200.Packer.PASS));

        // Default effort is 5, midway between 1 and 9.
        props.put(Pack200.Packer.EFFORT, "5");

        // Define certain attribute layouts by default.
        // Do this after the previous props are put in place,
        // to allow override if necessary.
        String propFile = "intrinsic.properties";

        PrivilegedAction<InputStream> pa =
            () -> PackerImpl.class.getResourceAsStream(propFile);
        try (InputStream propStr = AccessController.doPrivileged(pa)) {
            if (propStr == null) {
                throw new RuntimeException(propFile + " cannot be loaded");
            }
            props.load(propStr);
        } catch (IOException ee) {
            throw new RuntimeException(ee);
        }

        for (Map.Entry<Object, Object> e : props.entrySet()) {
            String key = (String) e.getKey();
            String val = (String) e.getValue();
            if (key.startsWith("attribute.")) {
                e.setValue(Attribute.normalizeLayoutString(val));
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        HashMap<String, String> temp = new HashMap(props);  // shrink to fit
        defaultProps = temp;
    }

    private static String getPropertyValue(String key, String defaultValue) {
        PrivilegedAction<String> pa = () -> System.getProperty(key);
        String s = AccessController.doPrivileged(pa);
        return s != null ? s : defaultValue;
    }

    PropMap() {
        theMap.putAll(defaultProps);
    }

    // Return a view of this map which includes only properties
    // that begin with the given prefix.  This is easy because
    // the map is sorted, and has a subMap accessor.
    SortedMap<String, String> prefixMap(String prefix) {
        int len = prefix.length();
        if (len == 0)
            return this;
        char nextch = (char)(prefix.charAt(len-1) + 1);
        String limit = prefix.substring(0, len-1)+nextch;
        //System.out.println(prefix+" => "+subMap(prefix, limit));
        return subMap(prefix, limit);
    }

    String getProperty(String s) {
        return get(s);
    }
    String getProperty(String s, String defaultVal) {
        String val = getProperty(s);
        if (val == null)
            return defaultVal;
        return val;
    }
    String setProperty(String s, String val) {
        return put(s, val);
    }

    // Get sequence of props for "prefix", and "prefix.*".
    List<String> getProperties(String prefix) {
        Collection<String> values = prefixMap(prefix).values();
        List<String> res = new ArrayList<>(values.size());
        res.addAll(values);
        while (res.remove(null));
        return res;
    }

    private boolean toBoolean(String val) {
        return Boolean.valueOf(val).booleanValue();
    }
    boolean getBoolean(String s) {
        return toBoolean(getProperty(s));
    }
    boolean setBoolean(String s, boolean val) {
        return toBoolean(setProperty(s, String.valueOf(val)));
    }
    int toInteger(String val) {
        return toInteger(val, 0);
    }
    int toInteger(String val, int def) {
        if (val == null)  return def;
        if (Pack200.Packer.TRUE.equals(val))   return 1;
        if (Pack200.Packer.FALSE.equals(val))  return 0;
        return Integer.parseInt(val);
    }
    int getInteger(String s, int def) {
        return toInteger(getProperty(s), def);
    }
    int getInteger(String s) {
        return toInteger(getProperty(s));
    }
    int setInteger(String s, int val) {
        return toInteger(setProperty(s, String.valueOf(val)));
    }

    long toLong(String val) {
        try {
            return val == null ? 0 : Long.parseLong(val);
        } catch (java.lang.NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid value");
        }
    }
    long getLong(String s) {
        return toLong(getProperty(s));
    }
    long setLong(String s, long val) {
        return toLong(setProperty(s, String.valueOf(val)));
    }

    int getTime(String s) {
        String sval = getProperty(s, "0");
        if (Utils.NOW.equals(sval)) {
            return (int)((System.currentTimeMillis()+500)/1000);
        }
        long lval = toLong(sval);
        final long recentSecondCount = 1000000000;

        if (lval < recentSecondCount*10 && !"0".equals(sval))
            Utils.log.warning("Supplied modtime appears to be seconds rather than milliseconds: "+sval);

        return (int)((lval+500)/1000);
    }

    void list(PrintStream out) {
        PrintWriter outw = new PrintWriter(out);
        list(outw);
        outw.flush();
    }
    void list(PrintWriter out) {
        out.println("#"+Utils.PACK_ZIP_ARCHIVE_MARKER_COMMENT+"[");
        Set<Map.Entry<String, String>> defaults = defaultProps.entrySet();
        for (Map.Entry<String, String> e : theMap.entrySet()) {
            if (defaults.contains(e))  continue;
            out.println("  " + e.getKey() + " = " + e.getValue());
        }
        out.println("#]");
    }

    @Override
    public int size() {
        return theMap.size();
    }

    @Override
    public boolean isEmpty() {
        return theMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return theMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return theMap.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return theMap.get(key);
    }

    @Override
    public String remove(Object key) {
       return theMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
       theMap.putAll(m);
    }

    @Override
    public void clear() {
        theMap.clear();
    }

    @Override
    public Set<String> keySet() {
       return theMap.keySet();
    }

    @Override
    public Collection<String> values() {
       return theMap.values();
    }

    @Override
    public Set<Map.Entry<String, String>> entrySet() {
        return theMap.entrySet();
    }

    @Override
    public Comparator<? super String> comparator() {
        return theMap.comparator();
    }

    @Override
    public SortedMap<String, String> subMap(String fromKey, String toKey) {
        return theMap.subMap(fromKey, toKey);
    }

    @Override
    public SortedMap<String, String> headMap(String toKey) {
        return theMap.headMap(toKey);
    }

    @Override
    public SortedMap<String, String> tailMap(String fromKey) {
        return theMap.tailMap(fromKey);
    }

    @Override
    public String firstKey() {
        return theMap.firstKey();
    }

    @Override
    public String lastKey() {
       return theMap.lastKey();
    }
}
