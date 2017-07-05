/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.hotspot.igv.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Properties implements Serializable {

    public static final long serialVersionUID = 1L;
    private Map<String, Property> map;

    public Properties() {
        map = new HashMap<String, Property>(5);
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (!(o instanceof Properties)) {
            return false;
        }

        Properties p = (Properties) o;

        if (getProperties().size() != p.getProperties().size()) {
            return false;
        }
        for (Property prop : getProperties()) {
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
        this.add(new Property(name, value));
    }

    public Properties(String name, String value, String name1, String value1) {
        this(name, value);
        this.add(new Property(name1, value1));
    }

    public Properties(String name, String value, String name1, String value1, String name2, String value2) {
        this(name, value, name1, value1);
        this.add(new Property(name2, value2));
    }

    public Properties(Properties p) {
        map = new HashMap<String, Property>(p.map);
    }

    public static class Object implements Provider {

        private Properties properties;

        public Object() {
            properties = new Properties();
        }

        public Object(Properties.Object object) {
            properties = new Properties(object.getProperties());
        }

        public Properties getProperties() {
            return properties;
        }
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

        Property p = this.map.get(matcher.getName());
        if (p == null) {
            return null;
        }
        if (matcher.match(p.getValue())) {
            return p;
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
        for (Property p : map.values()) {
            sb.append(p.toString());
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
        Property p = map.get(key);
        if (p == null) {
            return null;
        } else {
            return p.getValue();
        }
    }

    public String getProperty(String string) {
        return get(string);
    }

    public Property setProperty(String name, String value) {

        if (value == null) {
            // remove this property
            return map.remove(name);
        } else {
            Property p = map.get(name);
            if (p == null) {
                p = new Property(name, value);
                map.put(name, p);
            } else {
                p.setValue(value);
            }
            return p;
        }
    }

    public Collection<Property> getProperties() {
        return Collections.unmodifiableCollection(map.values());
    }

    public void add(Properties properties) {
        for (Property p : properties.getProperties()) {
            add(p);
        }
    }

    public void add(Property property) {
        assert property.getName() != null;
        assert property.getValue() != null;
        map.put(property.getName(), property);
    }
}
