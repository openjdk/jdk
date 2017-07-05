/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Control block for publishing Pack200 options to the other classes.
 */

final class PropMap implements SortedMap<String, String>  {
    private final TreeMap<String, String> theMap = new TreeMap<>();;

    // type is erased, elements are of type java.beans.PropertyChangeListener
    private final List<Object> listenerList = new ArrayList<>(1);

    void addListener(Object listener) {
        assert Beans.isPropertyChangeListener(listener);
        listenerList.add(listener);
    }

    void removeListener(Object listener) {
        assert Beans.isPropertyChangeListener(listener);
        listenerList.remove(listener);
    }

    // Override:
    public String put(String key, String value) {
        String oldValue = theMap.put(key, value);
        if (value != oldValue && !listenerList.isEmpty()) {
            assert Beans.isBeansPresent();
            // Post the property change event.
            Object event = Beans.newPropertyChangeEvent(this, key, oldValue, value);
            for (Object listener : listenerList) {
                Beans.invokePropertyChange(listener, event);
            }
        }
        return oldValue;
    }

    // All this other stuff is private to the current package.
    // Outide clients of Pack200 do not need to use it; they can
    // get by with generic SortedMap functionality.
    private static Map<String, String> defaultProps;
    static {
        Properties props = new Properties();

        // Allow implementation selected via -Dpack.disable.native=true
        props.put(Utils.DEBUG_DISABLE_NATIVE,
                  String.valueOf(Boolean.getBoolean(Utils.DEBUG_DISABLE_NATIVE)));

        // Set the DEBUG_VERBOSE from system
        props.put(Utils.DEBUG_VERBOSE,
                  String.valueOf(Integer.getInteger(Utils.DEBUG_VERBOSE,0)));

        // Set the PACK_TIMEZONE_NO_UTC
        props.put(Utils.PACK_DEFAULT_TIMEZONE,
                  String.valueOf(Boolean.getBoolean(Utils.PACK_DEFAULT_TIMEZONE)));

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
                System.getProperty(Utils.CLASS_FORMAT_ERROR, Pack200.Packer.PASS));

        // Default effort is 5, midway between 1 and 9.
        props.put(Pack200.Packer.EFFORT, "5");

        // Define certain attribute layouts by default.
        // Do this after the previous props are put in place,
        // to allow override if necessary.
        String propFile = "intrinsic.properties";

        try (InputStream propStr = PackerImpl.class.getResourceAsStream(propFile)) {
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

    /**
     * A class that provides access to the java.beans.PropertyChangeListener
     * and java.beans.PropertyChangeEvent without creating a static dependency
     * on java.beans. This class can be removed once the addPropertyChangeListener
     * and removePropertyChangeListener methods are removed from Packer and
     * Unpacker.
     */
    private static class Beans {
        private static final Class<?> propertyChangeListenerClass =
            getClass("java.beans.PropertyChangeListener");

        private static final Class<?> propertyChangeEventClass =
            getClass("java.beans.PropertyChangeEvent");

        private static final Method propertyChangeMethod =
            getMethod(propertyChangeListenerClass,
                      "propertyChange",
                      propertyChangeEventClass);

        private static final Constructor<?> propertyEventCtor =
            getConstructor(propertyChangeEventClass,
                           Object.class,
                           String.class,
                           Object.class,
                           Object.class);

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name, true, Beans.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        private static Constructor<?> getConstructor(Class<?> c, Class<?>... types) {
            try {
                return (c == null) ? null : c.getDeclaredConstructor(types);
            } catch (NoSuchMethodException x) {
                throw new AssertionError(x);
            }
        }

        private static Method getMethod(Class<?> c, String name, Class<?>... types) {
            try {
                return (c == null) ? null : c.getMethod(name, types);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Returns {@code true} if java.beans is present.
         */
        static boolean isBeansPresent() {
            return propertyChangeListenerClass != null &&
                   propertyChangeEventClass != null;
        }

        /**
         * Returns {@code true} if the given object is a PropertyChangeListener
         */
        static boolean isPropertyChangeListener(Object obj) {
            if (propertyChangeListenerClass == null) {
                return false;
            } else {
                return propertyChangeListenerClass.isInstance(obj);
            }
        }

        /**
         * Returns a new PropertyChangeEvent with the given source, property
         * name, old and new values.
         */
        static Object newPropertyChangeEvent(Object source, String prop,
                                             Object oldValue, Object newValue)
        {
            try {
                return propertyEventCtor.newInstance(source, prop, oldValue, newValue);
            } catch (InstantiationException | IllegalAccessException x) {
                throw new AssertionError(x);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause instanceof Error)
                    throw (Error)cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                throw new AssertionError(x);
            }
        }

        /**
         * Invokes the given PropertyChangeListener's propertyChange method
         * with the given event.
         */
        static void invokePropertyChange(Object listener, Object ev) {
            try {
                propertyChangeMethod.invoke(listener, ev);
            } catch (IllegalAccessException x) {
                throw new AssertionError(x);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause instanceof Error)
                    throw (Error)cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                throw new AssertionError(x);
            }
        }
    }
}
