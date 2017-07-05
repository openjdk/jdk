/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 *
 * @author Thomas Wuerthinger
 */
public class Properties implements Serializable, Iterable<Property> {

    public static final long serialVersionUID = 1L;
    private String[] map = new String[4];

    public Properties() {
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (!(o instanceof Properties)) {
            return false;
        }

        Properties p = (Properties) o;

        for (Property prop : this) {
            String value = p.get(prop.getName());
            if (value == null || !value.equals(prop.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + (this.map != null ? this.map.hashCode() : 0);
        return hash;
    }

    public Properties(String name, String value) {
        this();
        this.setProperty(name, value);
    }

    public Properties(String name, String value, String name1, String value1) {
        this(name, value);
        this.setProperty(name1, value1);
    }

    public Properties(String name, String value, String name1, String value1, String name2, String value2) {
        this(name, value, name1, value1);
        this.setProperty(name2, value2);
    }

    public Properties(Properties p) {
        map = new String[p.map.length];
        System.arraycopy(map, 0, p.map, 0, p.map.length);
    }

    public static class Entity implements Provider {

        private Properties properties;

        public Entity() {
            properties = new Properties();
        }

        public Entity(Properties.Entity object) {
            properties = new Properties(object.getProperties());
        }

        public Properties getProperties() {
            return properties;
        }
    }

    private String getProperty(String key) {
        for (int i = 0; i < map.length; i += 2)
            if (map[i] != null && map[i].equals(key)) {
                return map[i + 1];
            }
        return null;
    }

    public interface PropertyMatcher {

        String getName();

        boolean match(String value);
    }

    public static class InvertPropertyMatcher implements PropertyMatcher {

        private PropertyMatcher matcher;

        public InvertPropertyMatcher(PropertyMatcher matcher) {
            this.matcher = matcher;
        }

        public String getName() {
            return matcher.getName();
        }

        public boolean match(String p) {
            return !matcher.match(p);
        }
    }

    public static class StringPropertyMatcher implements PropertyMatcher {

        private String name;
        private String value;

        public StringPropertyMatcher(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public boolean match(String p) {
            return p.equals(value);
        }
    }

    public static class RegexpPropertyMatcher implements PropertyMatcher {

        private String name;
        private Pattern valuePattern;

        public RegexpPropertyMatcher(String name, String value) {
            this.name = name;
            valuePattern = Pattern.compile(value);
        }

        public String getName() {
            return name;
        }

        public boolean match(String p) {
            Matcher m = valuePattern.matcher(p);
            return m.matches();
        }
    }

    public Property selectSingle(PropertyMatcher matcher) {
        String value = null;
        for (int i = 0; i < map.length; i += 2) {
            if (map[i] != null && matcher.getName().equals(map[i]))  {
                value = map[i + 1];
                break;
            }
        }
        if (value != null && matcher.match(value)) {
            return new Property(matcher.getName(), value);
        } else {
            return null;
        }
    }

    public interface Provider {

        public Properties getProperties();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < map.length; i += 2) {
            if (map[i + 1] != null) {
                String p = map[i + 1];
                sb.append(map[i] + " = " + map[i + 1] + "; ");
            }
        }
        return sb.append("]").toString();
    }

    public static class PropertySelector<T extends Properties.Provider> {

        private Collection<T> objects;

        public PropertySelector(Collection<T> objects) {
            this.objects = objects;
        }

        public T selectSingle(final String name, final String value) {
            return selectSingle(new StringPropertyMatcher(name, value));
        }

        public T selectSingle(PropertyMatcher matcher) {

            for (T t : objects) {
                Property p = t.getProperties().selectSingle(matcher);
                if (p != null) {
                    return t;
                }
            }

            return null;
        }

        public List<T> selectMultiple(final String name, final String value) {
            return selectMultiple(new StringPropertyMatcher(name, value));
        }

        public List<T> selectMultiple(PropertyMatcher matcher) {
            List<T> result = new ArrayList<T>();
            for (T t : objects) {
                Property p = t.getProperties().selectSingle(matcher);
                if (p != null) {
                    result.add(t);
                }
            }
            return result;
        }
    }

    public String get(String key) {
        for (int i = 0; i < map.length; i += 2) {
            if (map[i] != null && map[i].equals(key)) {
                return map[i + 1];
            }
        }
        return null;
    }

    public void setProperty(String name, String value) {
        for (int i = 0; i < map.length; i += 2) {
            if (map[i] != null && map[i].equals(name)) {
                String p = map[i + 1];
                if (value == null) {
                    // remove this property
                    map[i] = null;
                    map[i + 1] = null;
                } else {
                    map[i + 1] = value;
                }
                return;
            }
        }
        if (value == null) {
            return;
        }
        for (int i = 0; i < map.length; i += 2) {
            if (map[i] == null) {
                map[i] = name;
                map[i + 1] = value;
                return;
            }
        }
        String[] newMap = new String[map.length + 4];
        System.arraycopy(map, 0, newMap, 0, map.length);
        newMap[map.length] = name;
        newMap[map.length + 1] = value;
        map = newMap;
    }

    public  Iterator<Property> getProperties() {
        return iterator();
    }

    public void add(Properties properties) {
        for (Property p : properties) {
            add(p);
        }
    }

    public void add(Property property) {
        assert property.getName() != null;
        assert property.getValue() != null;
        setProperty(property.getName(), property.getValue());
    }
    class PropertiesIterator implements Iterator<Property>, Iterable<Property> {
        public Iterator<Property> iterator() {
                return this;
        }

        int index;

        public boolean hasNext() {
            while (index < map.length && map[index + 1] == null)
                index += 2;
            return index < map.length;
        }

        public Property next() {
            if (index < map.length) {
                index += 2;
                return new Property(map[index - 2], map[index - 1]);
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
    public Iterator<Property> iterator() {
        return new PropertiesIterator();
    }
}
