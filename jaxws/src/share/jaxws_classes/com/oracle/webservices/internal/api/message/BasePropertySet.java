/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.webservices.internal.api.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * A set of "properties" that can be accessed via strongly-typed fields
 * as well as reflexibly through the property name.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("SuspiciousMethodCalls")
public abstract class BasePropertySet implements PropertySet {

    /**
     * Creates a new instance of TypedMap.
     */
    protected BasePropertySet() {
    }

    private Map<String,Object> mapView;

    /**
     * Represents the list of strongly-typed known properties
     * (keyed by property names.)
     *
     * <p>
     * Just giving it an alias to make the use of this class more fool-proof.
     */
    protected static class PropertyMap extends HashMap<String,Accessor> {

        // the entries are often being iterated through so performance can be improved
        // by their caching instead of iterating through the original (immutable) map each time
        transient PropertyMapEntry[] cachedEntries = null;

        PropertyMapEntry[] getPropertyMapEntries() {
            if (cachedEntries == null) {
                cachedEntries = createPropertyMapEntries();
            }
            return cachedEntries;
        }

        private PropertyMapEntry[] createPropertyMapEntries() {
            final PropertyMapEntry[] modelEntries = new PropertyMapEntry[size()];
            int i = 0;
            for (final Entry<String, Accessor> e : entrySet()) {
                modelEntries[i++] = new PropertyMapEntry(e.getKey(), e.getValue());
            }
            return modelEntries;
        }

    }

    /**
     * PropertyMapEntry represents a Map.Entry in the PropertyMap with more efficient access.
     */
    static public class PropertyMapEntry {
        public PropertyMapEntry(String k, Accessor v) {
            key = k; value = v;
        }
        String key;
        Accessor value;
    }

    /**
     * Map representing the Fields and Methods annotated with {@link PropertySet.Property}.
     * Model of {@link PropertySet} class.
     *
     * <p>
     * At the end of the derivation chain this method just needs to be implemented
     * as:
     *
     * <pre>
     * private static final PropertyMap model;
     * static {
     *   model = parse(MyDerivedClass.class);
     * }
     * protected PropertyMap getPropertyMap() {
     *   return model;
     * }
     * </pre>
     */
    protected abstract PropertyMap getPropertyMap();

    /**
     * This method parses a class for fields and methods with {@link PropertySet.Property}.
     */
    protected static PropertyMap parse(final Class clazz) {
        // make all relevant fields and methods accessible.
        // this allows runtime to skip the security check, so they runs faster.
        return AccessController.doPrivileged(new PrivilegedAction<PropertyMap>() {
            @Override
            public PropertyMap run() {
                PropertyMap props = new PropertyMap();
                for (Class c=clazz; c!=null; c=c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        Property cp = f.getAnnotation(Property.class);
                        if(cp!=null) {
                            for(String value : cp.value()) {
                                props.put(value, new FieldAccessor(f, value));
                            }
                        }
                    }
                    for (Method m : c.getDeclaredMethods()) {
                        Property cp = m.getAnnotation(Property.class);
                        if(cp!=null) {
                            String name = m.getName();
                            assert name.startsWith("get") || name.startsWith("is");

                            String setName = name.startsWith("is") ? "set"+name.substring(2) : // isFoo -> setFoo
                                                                     's'  +name.substring(1);  // getFoo -> setFoo
                            Method setter;
                            try {
                                setter = clazz.getMethod(setName,m.getReturnType());
                            } catch (NoSuchMethodException e) {
                                setter = null; // no setter
                            }
                            for(String value : cp.value()) {
                                props.put(value, new MethodAccessor(m, setter, value));
                            }
                        }
                    }
                }

                return props;
            }
        });
    }

    /**
     * Represents a typed property defined on a {@link PropertySet}.
     */
    protected interface Accessor {
        String getName();
        boolean hasValue(PropertySet props);
        Object get(PropertySet props);
        void set(PropertySet props, Object value);
    }

    static final class FieldAccessor implements Accessor {
        /**
         * Field with the annotation.
         */
        private final Field f;

        /**
         * One of the values in {@link Property} annotation on {@link #f}.
         */
        private final String name;

        protected FieldAccessor(Field f, String name) {
            this.f = f;
            f.setAccessible(true);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasValue(PropertySet props) {
            return get(props)!=null;
        }

        @Override
        public Object get(PropertySet props) {
            try {
                return f.get(props);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }

        @Override
        public void set(PropertySet props, Object value) {
            try {
                f.set(props,value);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }
    }

    static final class MethodAccessor implements Accessor {
        /**
         * Getter method.
         */
        private final @NotNull Method getter;
        /**
         * Setter method.
         * Some property is read-only.
         */
        private final @Nullable Method setter;

        /**
         * One of the values in {@link Property} annotation on {@link #getter}.
         */
        private final String name;

        protected MethodAccessor(Method getter, Method setter, String value) {
            this.getter = getter;
            this.setter = setter;
            this.name = value;
            getter.setAccessible(true);
            if (setter!=null) {
                setter.setAccessible(true);
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasValue(PropertySet props) {
            return get(props)!=null;
        }

        @Override
        public Object get(PropertySet props) {
            try {
                return getter.invoke(props);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                handle(e);
                return 0;   // never reach here
            }
        }

        @Override
        public void set(PropertySet props, Object value) {
            if(setter==null) {
                throw new ReadOnlyPropertyException(getName());
            }
            try {
                setter.invoke(props,value);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                handle(e);
            }
        }

        /**
         * Since we don't expect the getter/setter to throw a checked exception,
         * it should be possible to make the exception propagation transparent.
         * That's what we are trying to do here.
         */
        private Exception handle(InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t instanceof Error) {
                throw (Error)t;
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            throw new Error(e);
        }
    }


    /**
     * Class allowing to work with PropertySet object as with a Map; it doesn't only allow to read properties from
     * the map but also to modify the map in a way it is in sync with original strongly typed fields. It also allows
     * (if necessary) to store additional properties those can't be found in strongly typed fields.
     *
     * @see com.sun.xml.internal.ws.api.PropertySet#asMap() method
     */
    final class MapView extends HashMap<String, Object> {

        // flag if it should allow store also different properties
        // than the from strongly typed fields
        boolean extensible;

        MapView(boolean extensible) {
                super(getPropertyMap().getPropertyMapEntries().length);
            this.extensible = extensible;
            initialize();
        }

        public void initialize() {
            // iterate (cached) array instead of map to speed things up ...
            PropertyMapEntry[] entries = getPropertyMap().getPropertyMapEntries();
            for (PropertyMapEntry entry : entries) {
                super.put(entry.key, entry.value);
            }
        }

        @Override
        public Object get(Object key) {
            Object o = super.get(key);
            if (o instanceof Accessor) {
                return ((Accessor) o).get(BasePropertySet.this);
            } else {
                return o;
            }
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> entries = new HashSet<Entry<String, Object>>();
            for (String key : keySet()) {
                entries.add(new SimpleImmutableEntry<String, Object>(key, get(key)));
            }
            return entries;
        }

        @Override
        public Object put(String key, Object value) {

            Object o = super.get(key);
            if (o != null && o instanceof Accessor) {

                Object oldValue = ((Accessor) o).get(BasePropertySet.this);
                ((Accessor) o).set(BasePropertySet.this, value);
                return oldValue;

            } else {

                if (extensible) {
                    return super.put(key, value);
                } else {
                    throw new IllegalStateException("Unknown property [" + key + "] for PropertySet [" +
                            BasePropertySet.this.getClass().getName() + "]");
                }
            }
        }

        @Override
        public void clear() {
            for (String key : keySet()) {
                remove(key);
            }
        }

        @Override
        public Object remove(Object key) {
            Object o;
            o = super.get(key);
            if (o instanceof Accessor) {
                ((Accessor)o).set(BasePropertySet.this, null);
            }
            return super.remove(key);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if (sp != null) {
            return sp.get(this) != null;
        }
        return false;
    }

    /**
     * Gets the name of the property.
     *
     * @param key
     *      This field is typed as {@link Object} to follow the {@link Map#get(Object)}
     *      convention, but if anything but {@link String} is passed, this method
     *      just returns null.
     */
    @Override
    public Object get(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if (sp != null) {
            return sp.get(this);
        }
        throw new IllegalArgumentException("Undefined property "+key);
    }

    /**
     * Sets a property.
     *
     * <h3>Implementation Note</h3>
     * This method is slow. Code inside JAX-WS should define strongly-typed
     * fields in this class and access them directly, instead of using this.
     *
     * @throws ReadOnlyPropertyException
     *      if the given key is an alias of a strongly-typed field,
     *      and if the name object given is not assignable to the field.
     *
     * @see Property
     */
    @Override
    public Object put(String key, Object value) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null) {
            Object old = sp.get(this);
            sp.set(this,value);
            return old;
        } else {
            throw new IllegalArgumentException("Undefined property "+key);
        }
    }

    /**
     * Checks if this {@link PropertySet} supports a property of the given name.
     */
    @Override
    public boolean supports(Object key) {
        return getPropertyMap().containsKey(key);
    }

    @Override
    public Object remove(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null) {
            Object old = sp.get(this);
            sp.set(this,null);
            return old;
        } else {
            throw new IllegalArgumentException("Undefined property "+key);
        }
    }

    /**
     * Creates a {@link Map} view of this {@link PropertySet}.
     *
     * <p>
     * This map is partially live, in the sense that values you set to it
     * will be reflected to {@link PropertySet}.
     *
     * <p>
     * However, this map may not pick up changes made
     * to {@link PropertySet} after the view is created.
     *
     * @deprecated use newer implementation {@link PropertySet#asMap()} which produces
     * readwrite {@link Map}
     *
     * @return
     *      always non-null valid instance.
     */
    @Deprecated
    @Override
    public final Map<String,Object> createMapView() {
        final Set<Entry<String,Object>> core = new HashSet<Entry<String,Object>>();
        createEntrySet(core);

        return new AbstractMap<String, Object>() {
            @Override
            public Set<Entry<String,Object>> entrySet() {
                return core;
            }
        };
    }

    /**
     * Creates a modifiable {@link Map} view of this {@link PropertySet}.
     * <p/>
     * Changes done on this {@link Map} or on {@link PropertySet} object work in both directions - values made to
     * {@link Map} are reflected to {@link PropertySet} and changes done using getters/setters on {@link PropertySet}
     * object are automatically reflected in this {@link Map}.
     * <p/>
     * If necessary, it also can hold other values (not present on {@link PropertySet}) -
     * {@see PropertySet#mapAllowsAdditionalProperties}
     *
     * @return always non-null valid instance.
     */
    @Override
    public Map<String, Object> asMap() {
        if (mapView == null) {
            mapView = createView();
        }
        return mapView;
    }

    protected Map<String, Object> createView() {
        return new MapView(mapAllowsAdditionalProperties());
    }

    /**
     * Used when constructing the {@link MapView} for this object - it controls if the {@link MapView} servers only to
     * access strongly typed values or allows also different values
     *
     * @return true if {@link Map} should allow also properties not defined as strongly typed fields
     */
    protected boolean mapAllowsAdditionalProperties() {
        return false;
    }

    protected void createEntrySet(Set<Entry<String,Object>> core) {
        for (final Entry<String, Accessor> e : getPropertyMap().entrySet()) {
            core.add(new Entry<String, Object>() {
                @Override
                public String getKey() {
                    return e.getKey();
                }

                @Override
                public Object getValue() {
                    return e.getValue().get(BasePropertySet.this);
                }

                @Override
                public Object setValue(Object value) {
                    Accessor acc = e.getValue();
                    Object old = acc.get(BasePropertySet.this);
                    acc.set(BasePropertySet.this,value);
                    return old;
                }
            });
        }
    }
}
