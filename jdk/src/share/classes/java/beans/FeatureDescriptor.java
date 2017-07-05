/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.beans;

import com.sun.beans.TypeResolver;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.ref.SoftReference;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * The FeatureDescriptor class is the common baseclass for PropertyDescriptor,
 * EventSetDescriptor, and MethodDescriptor, etc.
 * <p>
 * It supports some common information that can be set and retrieved for
 * any of the introspection descriptors.
 * <p>
 * In addition it provides an extension mechanism so that arbitrary
 * attribute/value pairs can be associated with a design feature.
 */

public class FeatureDescriptor {

    private Reference<Class> classRef;

    /**
     * Constructs a <code>FeatureDescriptor</code>.
     */
    public FeatureDescriptor() {
    }

    /**
     * Gets the programmatic name of this feature.
     *
     * @return The programmatic name of the property/method/event
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the programmatic name of this feature.
     *
     * @param name  The programmatic name of the property/method/event
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the localized display name of this feature.
     *
     * @return The localized display name for the property/method/event.
     *  This defaults to the same as its programmatic name from getName.
     */
    public String getDisplayName() {
        if (displayName == null) {
            return getName();
        }
        return displayName;
    }

    /**
     * Sets the localized display name of this feature.
     *
     * @param displayName  The localized display name for the
     *          property/method/event.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * The "expert" flag is used to distinguish between those features that are
     * intended for expert users from those that are intended for normal users.
     *
     * @return True if this feature is intended for use by experts only.
     */
    public boolean isExpert() {
        return expert;
    }

    /**
     * The "expert" flag is used to distinguish between features that are
     * intended for expert users from those that are intended for normal users.
     *
     * @param expert True if this feature is intended for use by experts only.
     */
    public void setExpert(boolean expert) {
        this.expert = expert;
    }

    /**
     * The "hidden" flag is used to identify features that are intended only
     * for tool use, and which should not be exposed to humans.
     *
     * @return True if this feature should be hidden from human users.
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * The "hidden" flag is used to identify features that are intended only
     * for tool use, and which should not be exposed to humans.
     *
     * @param hidden  True if this feature should be hidden from human users.
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * The "preferred" flag is used to identify features that are particularly
     * important for presenting to humans.
     *
     * @return True if this feature should be preferentially shown to human users.
     */
    public boolean isPreferred() {
        return preferred;
    }

    /**
     * The "preferred" flag is used to identify features that are particularly
     * important for presenting to humans.
     *
     * @param preferred  True if this feature should be preferentially shown
     *                   to human users.
     */
    public void setPreferred(boolean preferred) {
        this.preferred = preferred;
    }

    /**
     * Gets the short description of this feature.
     *
     * @return  A localized short description associated with this
     *   property/method/event.  This defaults to be the display name.
     */
    public String getShortDescription() {
        if (shortDescription == null) {
            return getDisplayName();
        }
        return shortDescription;
    }

    /**
     * You can associate a short descriptive string with a feature.  Normally
     * these descriptive strings should be less than about 40 characters.
     * @param text  A (localized) short description to be associated with
     * this property/method/event.
     */
    public void setShortDescription(String text) {
        shortDescription = text;
    }

    /**
     * Associate a named attribute with this feature.
     *
     * @param attributeName  The locale-independent name of the attribute
     * @param value  The value.
     */
    public void setValue(String attributeName, Object value) {
        if (table == null) {
            table = new java.util.Hashtable();
        }
        table.put(attributeName, value);
    }

    /**
     * Retrieve a named attribute with this feature.
     *
     * @param attributeName  The locale-independent name of the attribute
     * @return  The value of the attribute.  May be null if
     *     the attribute is unknown.
     */
    public Object getValue(String attributeName) {
        if (table == null) {
           return null;
        }
        return table.get(attributeName);
    }

    /**
     * Gets an enumeration of the locale-independent names of this
     * feature.
     *
     * @return  An enumeration of the locale-independent names of any
     *    attributes that have been registered with setValue.
     */
    public java.util.Enumeration<String> attributeNames() {
        if (table == null) {
            table = new java.util.Hashtable();
        }
        return table.keys();
    }

    /**
     * Package-private constructor,
     * Merge information from two FeatureDescriptors.
     * The merged hidden and expert flags are formed by or-ing the values.
     * In the event of other conflicts, the second argument (y) is
     * given priority over the first argument (x).
     *
     * @param x  The first (lower priority) MethodDescriptor
     * @param y  The second (higher priority) MethodDescriptor
     */
    FeatureDescriptor(FeatureDescriptor x, FeatureDescriptor y) {
        expert = x.expert | y.expert;
        hidden = x.hidden | y.hidden;
        preferred = x.preferred | y.preferred;
        name = y.name;
        shortDescription = x.shortDescription;
        if (y.shortDescription != null) {
            shortDescription = y.shortDescription;
        }
        displayName = x.displayName;
        if (y.displayName != null) {
            displayName = y.displayName;
        }
        classRef = x.classRef;
        if (y.classRef != null) {
            classRef = y.classRef;
        }
        addTable(x.table);
        addTable(y.table);
    }

    /*
     * Package-private dup constructor
     * This must isolate the new object from any changes to the old object.
     */
    FeatureDescriptor(FeatureDescriptor old) {
        expert = old.expert;
        hidden = old.hidden;
        preferred = old.preferred;
        name = old.name;
        shortDescription = old.shortDescription;
        displayName = old.displayName;
        classRef = old.classRef;

        addTable(old.table);
    }

    private void addTable(java.util.Hashtable t) {
        if (t == null) {
            return;
        }
        java.util.Enumeration keys = t.keys();
        while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            Object value = t.get(key);
            setValue(key, value);
        }
    }

    // Package private methods for recreating the weak/soft referent

    void setClass0(Class cls) {
        this.classRef = getWeakReference(cls);
    }

    Class getClass0() {
        return (this.classRef != null)
                ? this.classRef.get()
                : null;
    }

    /**
     * Create a Reference wrapper for the object.
     *
     * @param obj object that will be wrapped
     * @param soft true if a SoftReference should be created; otherwise Soft
     * @return a Reference or null if obj is null.
     */
    static Reference createReference(Object obj, boolean soft) {
        Reference ref = null;
        if (obj != null) {
            if (soft) {
                ref = new SoftReference(obj);
            } else {
                ref = new WeakReference(obj);
            }
        }
        return ref;
    }

    // Convenience method which creates a WeakReference.
    static Reference createReference(Object obj) {
        return createReference(obj, false);
    }

    /**
     * Returns an object from a Reference wrapper.
     *
     * @return the Object in a wrapper or null.
     */
    static Object getObject(Reference ref) {
        return (ref == null) ? null : (Object)ref.get();
    }

    /**
     * Creates a new soft reference that refers to the given object.
     *
     * @return a new soft reference or <code>null</code> if object is <code>null</code>
     *
     * @see SoftReference
     */
    static <T> Reference<T> getSoftReference(T object) {
        return (object != null)
                ? new SoftReference<T>(object)
                : null;
    }

    /**
     * Creates a new weak reference that refers to the given object.
     *
     * @return a new weak reference or <code>null</code> if object is <code>null</code>
     *
     * @see WeakReference
     */
    static <T> Reference<T> getWeakReference(T object) {
        return (object != null)
                ? new WeakReference<T>(object)
                : null;
    }

    /**
     * Resolves the return type of the method.
     *
     * @param base    the class that contains the method in the hierarchy
     * @param method  the object that represents the method
     * @return a class identifying the return type of the method
     *
     * @see Method#getGenericReturnType
     * @see Method#getReturnType
     */
    static Class getReturnType(Class base, Method method) {
        if (base == null) {
            base = method.getDeclaringClass();
        }
        return TypeResolver.erase(TypeResolver.resolveInClass(base, method.getGenericReturnType()));
    }

    /**
     * Resolves the parameter types of the method.
     *
     * @param base    the class that contains the method in the hierarchy
     * @param method  the object that represents the method
     * @return an array of classes identifying the parameter types of the method
     *
     * @see Method#getGenericParameterTypes
     * @see Method#getParameterTypes
     */
    static Class[] getParameterTypes(Class base, Method method) {
        if (base == null) {
            base = method.getDeclaringClass();
        }
        return TypeResolver.erase(TypeResolver.resolveInClass(base, method.getGenericParameterTypes()));
    }

    private boolean expert;
    private boolean hidden;
    private boolean preferred;
    private String shortDescription;
    private String name;
    private String displayName;
    private java.util.Hashtable table;
}
