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

package com.sun.xml.internal.ws.api;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.util.ReadOnlyPropertyException;

import javax.xml.ws.handler.MessageContext;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
public abstract class PropertySet {

    /**
     * Creates a new instance of TypedMap.
     */
    protected PropertySet() {}

    /**
     * Marks a field on {@link PropertySet} as a
     * property of {@link MessageContext}.
     *
     * <p>
     * To make the runtime processing easy, this annotation
     * must be on a public field (since the property name
     * can be set through {@link Map} anyway, you won't be
     * losing abstraction by doing so.)
     *
     * <p>
     * For similar reason, this annotation can be only placed
     * on a reference type, not primitive type.
     *
     * @see Packet
     * @author Kohsuke Kawaguchi
     */
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD,ElementType.METHOD})
    public @interface Property {
        /**
         * Name of the property.
         */
        String[] value();
    }

    /**
     * Represents the list of strongly-typed known propertyies
     * (keyed by property names.)
     *
     * <p>
     * Just giving it an alias to make the use of this class more fool-proof.
     */
    protected static final class PropertyMap extends HashMap<String,Accessor> {}

    /**
     * Map representing the Fields and Methods annotated with {@link Property}.
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

    // maybe we can use this some time
    ///**
    // * If all the properties defined on this {@link PropertySet} has a certain prefix
    // * (such as, say, "javax.xml.ws.http."), then return it.
    // *
    // * <p>
    // * Returning a non-null name from this method allows methods like
    // * {@link #get(Object)} and {@link #put(String, Object)} to work faster.
    // * This is especially so with {@link DistributedPropertySet}, so implementations
    // * are encouraged to set a common prefix, as much as possible.
    // *
    // * <p>
    // * Currently, this is used only by {@link DistributedPropertySet}.
    // *
    // * @return
    // *      Null if no such common prefix exists. Otherwise string like
    // *      "javax.xml.ws.http." (the dot at the last is usually preferrable,
    // *      so that properties like "javax.xml.ws.https.something" won't match.
    // */
    //protected abstract String getPropertyPrefix();

    /**
     * This method parses a class for fields and methods with {@link Property}.
     */
    protected static PropertyMap parse(final Class clazz) {
        // make all relevant fields and methods accessible.
        // this allows runtime to skip the security check, so they runs faster.
        return AccessController.doPrivileged(new PrivilegedAction<PropertyMap>() {
            public PropertyMap run() {
                PropertyMap props = new PropertyMap();
                for( Class c=clazz; c!=null; c=c.getSuperclass()) {
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
                            assert name.startsWith("get");

                            String setName = 's'+name.substring(1);   // getFoo -> setFoo
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

        public String getName() {
            return name;
        }

        public boolean hasValue(PropertySet props) {
            return get(props)!=null;
        }

        public Object get(PropertySet props) {
            try {
                return f.get(props);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }

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
            if(setter!=null)
                setter.setAccessible(true);
        }

        public String getName() {
            return name;
        }

        public boolean hasValue(PropertySet props) {
            return get(props)!=null;
        }

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

        public void set(PropertySet props, Object value) {
            if(setter==null)
                throw new ReadOnlyPropertyException(getName());
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
            if(t instanceof Error)
                throw (Error)t;
            if(t instanceof RuntimeException)
                throw (RuntimeException)t;
            throw new Error(e);
        }
    }


    public final boolean containsKey(Object key) {
        return get(key)!=null;
    }

    /**
     * Gets the name of the property.
     *
     * @param key
     *      This field is typed as {@link Object} to follow the {@link Map#get(Object)}
     *      convention, but if anything but {@link String} is passed, this method
     *      just returns null.
     */
    public Object get(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null)
            return sp.get(this);
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
    public boolean supports(Object key) {
        return getPropertyMap().containsKey(key);
    }

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
     * Lazily created view of {@link Property}s that
     * forms the core of {@link #createMapView()}.
     */
    /*package*/ Set<Entry<String,Object>> mapViewCore;

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
     * @return
     *      always non-null valid instance.
     */
    public final Map<String,Object> createMapView() {
        final Set<Entry<String,Object>> core = new HashSet<Entry<String,Object>>();
        createEntrySet(core);

        return new AbstractMap<String, Object>() {
            public Set<Entry<String,Object>> entrySet() {
                return core;
            }
        };
    }

    /*package*/ void createEntrySet(Set<Entry<String,Object>> core) {
        for (final Entry<String, Accessor> e : getPropertyMap().entrySet()) {
            core.add(new Entry<String, Object>() {
                public String getKey() {
                    return e.getKey();
                }

                public Object getValue() {
                    return e.getValue().get(PropertySet.this);
                }

                public Object setValue(Object value) {
                    Accessor acc = e.getValue();
                    Object old = acc.get(PropertySet.this);
                    acc.set(PropertySet.this,value);
                    return old;
                }
            });
        }
    }
}
