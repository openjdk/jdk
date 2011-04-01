/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.beans;

import com.sun.beans.WeakCache;
import com.sun.beans.finder.BeanInfoFinder;
import com.sun.beans.finder.ClassFinder;

import java.awt.Component;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.EventListener;
import java.util.EventObject;
import java.util.List;
import java.util.TreeMap;
import java.util.WeakHashMap;

import sun.awt.AppContext;
import sun.reflect.misc.ReflectUtil;

/**
 * The Introspector class provides a standard way for tools to learn about
 * the properties, events, and methods supported by a target Java Bean.
 * <p>
 * For each of those three kinds of information, the Introspector will
 * separately analyze the bean's class and superclasses looking for
 * either explicit or implicit information and use that information to
 * build a BeanInfo object that comprehensively describes the target bean.
 * <p>
 * For each class "Foo", explicit information may be available if there exists
 * a corresponding "FooBeanInfo" class that provides a non-null value when
 * queried for the information.   We first look for the BeanInfo class by
 * taking the full package-qualified name of the target bean class and
 * appending "BeanInfo" to form a new class name.  If this fails, then
 * we take the final classname component of this name, and look for that
 * class in each of the packages specified in the BeanInfo package search
 * path.
 * <p>
 * Thus for a class such as "sun.xyz.OurButton" we would first look for a
 * BeanInfo class called "sun.xyz.OurButtonBeanInfo" and if that failed we'd
 * look in each package in the BeanInfo search path for an OurButtonBeanInfo
 * class.  With the default search path, this would mean looking for
 * "sun.beans.infos.OurButtonBeanInfo".
 * <p>
 * If a class provides explicit BeanInfo about itself then we add that to
 * the BeanInfo information we obtained from analyzing any derived classes,
 * but we regard the explicit information as being definitive for the current
 * class and its base classes, and do not proceed any further up the superclass
 * chain.
 * <p>
 * If we don't find explicit BeanInfo on a class, we use low-level
 * reflection to study the methods of the class and apply standard design
 * patterns to identify property accessors, event sources, or public
 * methods.  We then proceed to analyze the class's superclass and add
 * in the information from it (and possibly on up the superclass chain).
 * <p>
 * For more information about introspection and design patterns, please
 * consult the
 *  <a href="http://java.sun.com/products/javabeans/docs/index.html">JavaBeans&trade; specification</a>.
 */

public class Introspector {

    // Flags that can be used to control getBeanInfo:
    public final static int USE_ALL_BEANINFO           = 1;
    public final static int IGNORE_IMMEDIATE_BEANINFO  = 2;
    public final static int IGNORE_ALL_BEANINFO        = 3;

    // Static Caches to speed up introspection.
    private static WeakCache<Class<?>, Method[]> declaredMethodCache =
            new WeakCache<Class<?>, Method[]>();

    private static final Object BEANINFO_CACHE = new Object();

    private Class beanClass;
    private BeanInfo explicitBeanInfo;
    private BeanInfo superBeanInfo;
    private BeanInfo additionalBeanInfo[];

    private boolean propertyChangeSource = false;
    private static Class eventListenerType = EventListener.class;

    // These should be removed.
    private String defaultEventName;
    private String defaultPropertyName;
    private int defaultEventIndex = -1;
    private int defaultPropertyIndex = -1;

    // Methods maps from Method objects to MethodDescriptors
    private Map methods;

    // properties maps from String names to PropertyDescriptors
    private Map properties;

    // events maps from String names to EventSetDescriptors
    private Map events;

    private final static EventSetDescriptor[] EMPTY_EVENTSETDESCRIPTORS = new EventSetDescriptor[0];

    static final String ADD_PREFIX = "add";
    static final String REMOVE_PREFIX = "remove";
    static final String GET_PREFIX = "get";
    static final String SET_PREFIX = "set";
    static final String IS_PREFIX = "is";

    private static final Object FINDER_KEY = new Object();

    //======================================================================
    //                          Public methods
    //======================================================================

    /**
     * Introspect on a Java Bean and learn about all its properties, exposed
     * methods, and events.
     * <p>
     * If the BeanInfo class for a Java Bean has been previously Introspected
     * then the BeanInfo class is retrieved from the BeanInfo cache.
     *
     * @param beanClass  The bean class to be analyzed.
     * @return  A BeanInfo object describing the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     * @see #flushCaches
     * @see #flushFromCaches
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass)
        throws IntrospectionException
    {
        if (!ReflectUtil.isPackageAccessible(beanClass)) {
            return (new Introspector(beanClass, null, USE_ALL_BEANINFO)).getBeanInfo();
        }
        Map<Class<?>, BeanInfo> beanInfoCache;
        BeanInfo beanInfo;
        synchronized (BEANINFO_CACHE) {
            beanInfoCache = (Map<Class<?>, BeanInfo>) AppContext.getAppContext().get(BEANINFO_CACHE);
            if (beanInfoCache == null) {
                beanInfoCache = new WeakHashMap<Class<?>, BeanInfo>();
                AppContext.getAppContext().put(BEANINFO_CACHE, beanInfoCache);
            }
            beanInfo = beanInfoCache.get(beanClass);
        }
        if (beanInfo == null) {
            beanInfo = new Introspector(beanClass, null, USE_ALL_BEANINFO).getBeanInfo();
            synchronized (BEANINFO_CACHE) {
                beanInfoCache.put(beanClass, beanInfo);
            }
        }
        return beanInfo;
    }

    /**
     * Introspect on a Java bean and learn about all its properties, exposed
     * methods, and events, subject to some control flags.
     * <p>
     * If the BeanInfo class for a Java Bean has been previously Introspected
     * based on the same arguments then the BeanInfo class is retrieved
     * from the BeanInfo cache.
     *
     * @param beanClass  The bean class to be analyzed.
     * @param flags  Flags to control the introspection.
     *     If flags == USE_ALL_BEANINFO then we use all of the BeanInfo
     *          classes we can discover.
     *     If flags == IGNORE_IMMEDIATE_BEANINFO then we ignore any
     *           BeanInfo associated with the specified beanClass.
     *     If flags == IGNORE_ALL_BEANINFO then we ignore all BeanInfo
     *           associated with the specified beanClass or any of its
     *           parent classes.
     * @return  A BeanInfo object describing the target bean.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass, int flags)
                                                throws IntrospectionException {
        return getBeanInfo(beanClass, null, flags);
    }

    /**
     * Introspect on a Java bean and learn all about its properties, exposed
     * methods, below a given "stop" point.
     * <p>
     * If the BeanInfo class for a Java Bean has been previously Introspected
     * based on the same arguments, then the BeanInfo class is retrieved
     * from the BeanInfo cache.
     *
     * @param beanClass The bean class to be analyzed.
     * @param stopClass The baseclass at which to stop the analysis.  Any
     *    methods/properties/events in the stopClass or in its baseclasses
     *    will be ignored in the analysis.
     * @exception IntrospectionException if an exception occurs during
     *              introspection.
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass, Class<?> stopClass)
                                                throws IntrospectionException {
        return getBeanInfo(beanClass, stopClass, USE_ALL_BEANINFO);
    }

    /**
     * Introspect on a Java Bean and learn about all its properties,
     * exposed methods and events, below a given {@code stopClass} point
     * subject to some control {@code flags}.
     * <dl>
     *  <dt>USE_ALL_BEANINFO</dt>
     *  <dd>Any BeanInfo that can be discovered will be used.</dd>
     *  <dt>IGNORE_IMMEDIATE_BEANINFO</dt>
     *  <dd>Any BeanInfo associated with the specified {@code beanClass} will be ignored.</dd>
     *  <dt>IGNORE_ALL_BEANINFO</dt>
     *  <dd>Any BeanInfo associated with the specified {@code beanClass}
     *      or any of its parent classes will be ignored.</dd>
     * </dl>
     * Any methods/properties/events in the {@code stopClass}
     * or in its parent classes will be ignored in the analysis.
     * <p>
     * If the BeanInfo class for a Java Bean has been
     * previously introspected based on the same arguments then
     * the BeanInfo class is retrieved from the BeanInfo cache.
     *
     * @param beanClass  the bean class to be analyzed
     * @param stopClass  the parent class at which to stop the analysis
     * @param flags      flags to control the introspection
     * @return a BeanInfo object describing the target bean
     * @exception IntrospectionException if an exception occurs during introspection
     *
     * @since 1.7
     */
    public static BeanInfo getBeanInfo(Class<?> beanClass, Class<?> stopClass,
                                        int flags) throws IntrospectionException {
        BeanInfo bi;
        if (stopClass == null && flags == USE_ALL_BEANINFO) {
            // Same parameters to take advantage of caching.
            bi = getBeanInfo(beanClass);
        } else {
            bi = (new Introspector(beanClass, stopClass, flags)).getBeanInfo();
        }
        return bi;

        // Old behaviour: Make an independent copy of the BeanInfo.
        //return new GenericBeanInfo(bi);
    }


    /**
     * Utility method to take a string and convert it to normal Java variable
     * name capitalization.  This normally means converting the first
     * character from upper case to lower case, but in the (unusual) special
     * case when there is more than one character and both the first and
     * second characters are upper case, we leave it alone.
     * <p>
     * Thus "FooBah" becomes "fooBah" and "X" becomes "x", but "URL" stays
     * as "URL".
     *
     * @param  name The string to be decapitalized.
     * @return  The decapitalized version of the string.
     */
    public static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
                        Character.isUpperCase(name.charAt(0))){
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Gets the list of package names that will be used for
     *          finding BeanInfo classes.
     *
     * @return  The array of package names that will be searched in
     *          order to find BeanInfo classes. The default value
     *          for this array is implementation-dependent; e.g.
     *          Sun implementation initially sets to {"sun.beans.infos"}.
     */

    public static String[] getBeanInfoSearchPath() {
        return getFinder().getPackages();
    }

    /**
     * Change the list of package names that will be used for
     *          finding BeanInfo classes.  The behaviour of
     *          this method is undefined if parameter path
     *          is null.
     *
     * <p>First, if there is a security manager, its <code>checkPropertiesAccess</code>
     * method is called. This could result in a SecurityException.
     *
     * @param path  Array of package names.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertiesAccess</code> method doesn't allow setting
     *              of system properties.
     * @see SecurityManager#checkPropertiesAccess
     */

    public static void setBeanInfoSearchPath(String[] path) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPropertiesAccess();
        }
        getFinder().setPackages(path);
    }


    /**
     * Flush all of the Introspector's internal caches.  This method is
     * not normally required.  It is normally only needed by advanced
     * tools that update existing "Class" objects in-place and need
     * to make the Introspector re-analyze existing Class objects.
     */

    public static void flushCaches() {
        synchronized (BEANINFO_CACHE) {
            Map beanInfoCache = (Map) AppContext.getAppContext().get(BEANINFO_CACHE);
            if (beanInfoCache != null) {
                beanInfoCache.clear();
            }
            declaredMethodCache.clear();
        }
    }

    /**
     * Flush the Introspector's internal cached information for a given class.
     * This method is not normally required.  It is normally only needed
     * by advanced tools that update existing "Class" objects in-place
     * and need to make the Introspector re-analyze an existing Class object.
     *
     * Note that only the direct state associated with the target Class
     * object is flushed.  We do not flush state for other Class objects
     * with the same name, nor do we flush state for any related Class
     * objects (such as subclasses), even though their state may include
     * information indirectly obtained from the target Class object.
     *
     * @param clz  Class object to be flushed.
     * @throws NullPointerException If the Class object is null.
     */
    public static void flushFromCaches(Class<?> clz) {
        if (clz == null) {
            throw new NullPointerException();
        }
        synchronized (BEANINFO_CACHE) {
            Map beanInfoCache = (Map) AppContext.getAppContext().get(BEANINFO_CACHE);
            if (beanInfoCache != null) {
                beanInfoCache.put(clz, null);
            }
            declaredMethodCache.put(clz, null);
        }
    }

    //======================================================================
    //                  Private implementation methods
    //======================================================================

    private Introspector(Class beanClass, Class stopClass, int flags)
                                            throws IntrospectionException {
        this.beanClass = beanClass;

        // Check stopClass is a superClass of startClass.
        if (stopClass != null) {
            boolean isSuper = false;
            for (Class c = beanClass.getSuperclass(); c != null; c = c.getSuperclass()) {
                if (c == stopClass) {
                    isSuper = true;
                }
            }
            if (!isSuper) {
                throw new IntrospectionException(stopClass.getName() + " not superclass of " +
                                        beanClass.getName());
            }
        }

        if (flags == USE_ALL_BEANINFO) {
            explicitBeanInfo = findExplicitBeanInfo(beanClass);
        }

        Class superClass = beanClass.getSuperclass();
        if (superClass != stopClass) {
            int newFlags = flags;
            if (newFlags == IGNORE_IMMEDIATE_BEANINFO) {
                newFlags = USE_ALL_BEANINFO;
            }
            superBeanInfo = getBeanInfo(superClass, stopClass, newFlags);
        }
        if (explicitBeanInfo != null) {
            additionalBeanInfo = explicitBeanInfo.getAdditionalBeanInfo();
        }
        if (additionalBeanInfo == null) {
            additionalBeanInfo = new BeanInfo[0];
        }
    }

    /**
     * Constructs a GenericBeanInfo class from the state of the Introspector
     */
    private BeanInfo getBeanInfo() throws IntrospectionException {

        // the evaluation order here is import, as we evaluate the
        // event sets and locate PropertyChangeListeners before we
        // look for properties.
        BeanDescriptor bd = getTargetBeanDescriptor();
        MethodDescriptor mds[] = getTargetMethodInfo();
        EventSetDescriptor esds[] = getTargetEventInfo();
        PropertyDescriptor pds[] = getTargetPropertyInfo();

        int defaultEvent = getTargetDefaultEventIndex();
        int defaultProperty = getTargetDefaultPropertyIndex();

        return new GenericBeanInfo(bd, esds, defaultEvent, pds,
                        defaultProperty, mds, explicitBeanInfo);

    }

    /**
     * Looks for an explicit BeanInfo class that corresponds to the Class.
     * First it looks in the existing package that the Class is defined in,
     * then it checks to see if the class is its own BeanInfo. Finally,
     * the BeanInfo search path is prepended to the class and searched.
     *
     * @param beanClass  the class type of the bean
     * @return Instance of an explicit BeanInfo class or null if one isn't found.
     */
    private static BeanInfo findExplicitBeanInfo(Class beanClass) {
        return getFinder().find(beanClass);
    }

    /**
     * @return An array of PropertyDescriptors describing the editable
     * properties supported by the target bean.
     */

    private PropertyDescriptor[] getTargetPropertyInfo() {

        // Check if the bean has its own BeanInfo that will provide
        // explicit information.
        PropertyDescriptor[] explicitProperties = null;
        if (explicitBeanInfo != null) {
            explicitProperties = getPropertyDescriptors(this.explicitBeanInfo);
        }

        if (explicitProperties == null && superBeanInfo != null) {
            // We have no explicit BeanInfo properties.  Check with our parent.
            addPropertyDescriptors(getPropertyDescriptors(this.superBeanInfo));
        }

        for (int i = 0; i < additionalBeanInfo.length; i++) {
            addPropertyDescriptors(additionalBeanInfo[i].getPropertyDescriptors());
        }

        if (explicitProperties != null) {
            // Add the explicit BeanInfo data to our results.
            addPropertyDescriptors(explicitProperties);

        } else {

            // Apply some reflection to the current class.

            // First get an array of all the public methods at this level
            Method methodList[] = getPublicDeclaredMethods(beanClass);

            // Now analyze each method.
            for (int i = 0; i < methodList.length; i++) {
                Method method = methodList[i];
                if (method == null || method.isSynthetic()) {
                    continue;
                }
                // skip static methods.
                int mods = method.getModifiers();
                if (Modifier.isStatic(mods)) {
                    continue;
                }
                String name = method.getName();
                Class argTypes[] = method.getParameterTypes();
                Class resultType = method.getReturnType();
                int argCount = argTypes.length;
                PropertyDescriptor pd = null;

                if (name.length() <= 3 && !name.startsWith(IS_PREFIX)) {
                    // Optimization. Don't bother with invalid propertyNames.
                    continue;
                }

                try {

                    if (argCount == 0) {
                        if (name.startsWith(GET_PREFIX)) {
                            // Simple getter
                            pd = new PropertyDescriptor(this.beanClass, name.substring(3), method, null);
                        } else if (resultType == boolean.class && name.startsWith(IS_PREFIX)) {
                            // Boolean getter
                            pd = new PropertyDescriptor(this.beanClass, name.substring(2), method, null);
                        }
                    } else if (argCount == 1) {
                        if (int.class.equals(argTypes[0]) && name.startsWith(GET_PREFIX)) {
                            pd = new IndexedPropertyDescriptor(this.beanClass, name.substring(3), null, null, method, null);
                        } else if (void.class.equals(resultType) && name.startsWith(SET_PREFIX)) {
                            // Simple setter
                            pd = new PropertyDescriptor(this.beanClass, name.substring(3), null, method);
                            if (throwsException(method, PropertyVetoException.class)) {
                                pd.setConstrained(true);
                            }
                        }
                    } else if (argCount == 2) {
                            if (void.class.equals(resultType) && int.class.equals(argTypes[0]) && name.startsWith(SET_PREFIX)) {
                            pd = new IndexedPropertyDescriptor(this.beanClass, name.substring(3), null, null, null, method);
                            if (throwsException(method, PropertyVetoException.class)) {
                                pd.setConstrained(true);
                            }
                        }
                    }
                } catch (IntrospectionException ex) {
                    // This happens if a PropertyDescriptor or IndexedPropertyDescriptor
                    // constructor fins that the method violates details of the deisgn
                    // pattern, e.g. by having an empty name, or a getter returning
                    // void , or whatever.
                    pd = null;
                }

                if (pd != null) {
                    // If this class or one of its base classes is a PropertyChange
                    // source, then we assume that any properties we discover are "bound".
                    if (propertyChangeSource) {
                        pd.setBound(true);
                    }
                    addPropertyDescriptor(pd);
                }
            }
        }
        processPropertyDescriptors();

        // Allocate and populate the result array.
        PropertyDescriptor result[] = new PropertyDescriptor[properties.size()];
        result = (PropertyDescriptor[])properties.values().toArray(result);

        // Set the default index.
        if (defaultPropertyName != null) {
            for (int i = 0; i < result.length; i++) {
                if (defaultPropertyName.equals(result[i].getName())) {
                    defaultPropertyIndex = i;
                }
            }
        }

        return result;
    }

    private HashMap pdStore = new HashMap();

    /**
     * Adds the property descriptor to the list store.
     */
    private void addPropertyDescriptor(PropertyDescriptor pd) {
        String propName = pd.getName();
        List list = (List)pdStore.get(propName);
        if (list == null) {
            list = new ArrayList();
            pdStore.put(propName, list);
        }
        if (this.beanClass != pd.getClass0()) {
            // replace existing property descriptor
            // only if we have types to resolve
            // in the context of this.beanClass
            try {
                String name = pd.getName();
                Method read = pd.getReadMethod();
                Method write = pd.getWriteMethod();
                boolean cls = true;
                if (read != null) cls = cls && read.getGenericReturnType() instanceof Class;
                if (write != null) cls = cls && write.getGenericParameterTypes()[0] instanceof Class;
                if (pd instanceof IndexedPropertyDescriptor) {
                    IndexedPropertyDescriptor ipd = (IndexedPropertyDescriptor)pd;
                    Method readI = ipd.getIndexedReadMethod();
                    Method writeI = ipd.getIndexedWriteMethod();
                    if (readI != null) cls = cls && readI.getGenericReturnType() instanceof Class;
                    if (writeI != null) cls = cls && writeI.getGenericParameterTypes()[1] instanceof Class;
                    if (!cls) {
                        pd = new IndexedPropertyDescriptor(this.beanClass, name, read, write, readI, writeI);
                    }
                } else if (!cls) {
                    pd = new PropertyDescriptor(this.beanClass, name, read, write);
                }
            } catch ( IntrospectionException e ) {
            }
        }
        list.add(pd);
    }

    private void addPropertyDescriptors(PropertyDescriptor[] descriptors) {
        if (descriptors != null) {
            for (PropertyDescriptor descriptor : descriptors) {
                addPropertyDescriptor(descriptor);
            }
        }
    }

    private PropertyDescriptor[] getPropertyDescriptors(BeanInfo info) {
        PropertyDescriptor[] descriptors = info.getPropertyDescriptors();
        int index = info.getDefaultPropertyIndex();
        if ((0 <= index) && (index < descriptors.length)) {
            this.defaultPropertyName = descriptors[index].getName();
        }
        return descriptors;
    }

    /**
     * Populates the property descriptor table by merging the
     * lists of Property descriptors.
     */
    private void processPropertyDescriptors() {
        if (properties == null) {
            properties = new TreeMap();
        }

        List list;

        PropertyDescriptor pd, gpd, spd;
        IndexedPropertyDescriptor ipd, igpd, ispd;

        Iterator it = pdStore.values().iterator();
        while (it.hasNext()) {
            pd = null; gpd = null; spd = null;
            ipd = null; igpd = null; ispd = null;

            list = (List)it.next();

            // First pass. Find the latest getter method. Merge properties
            // of previous getter methods.
            for (int i = 0; i < list.size(); i++) {
                pd = (PropertyDescriptor)list.get(i);
                if (pd instanceof IndexedPropertyDescriptor) {
                    ipd = (IndexedPropertyDescriptor)pd;
                    if (ipd.getIndexedReadMethod() != null) {
                        if (igpd != null) {
                            igpd = new IndexedPropertyDescriptor(igpd, ipd);
                        } else {
                            igpd = ipd;
                        }
                    }
                } else {
                    if (pd.getReadMethod() != null) {
                        if (gpd != null) {
                            // Don't replace the existing read
                            // method if it starts with "is"
                            Method method = gpd.getReadMethod();
                            if (!method.getName().startsWith(IS_PREFIX)) {
                                gpd = new PropertyDescriptor(gpd, pd);
                            }
                        } else {
                            gpd = pd;
                        }
                    }
                }
            }

            // Second pass. Find the latest setter method which
            // has the same type as the getter method.
            for (int i = 0; i < list.size(); i++) {
                pd = (PropertyDescriptor)list.get(i);
                if (pd instanceof IndexedPropertyDescriptor) {
                    ipd = (IndexedPropertyDescriptor)pd;
                    if (ipd.getIndexedWriteMethod() != null) {
                        if (igpd != null) {
                            if (igpd.getIndexedPropertyType()
                                == ipd.getIndexedPropertyType()) {
                                if (ispd != null) {
                                    ispd = new IndexedPropertyDescriptor(ispd, ipd);
                                } else {
                                    ispd = ipd;
                                }
                            }
                        } else {
                            if (ispd != null) {
                                ispd = new IndexedPropertyDescriptor(ispd, ipd);
                            } else {
                                ispd = ipd;
                            }
                        }
                    }
                } else {
                    if (pd.getWriteMethod() != null) {
                        if (gpd != null) {
                            if (gpd.getPropertyType() == pd.getPropertyType()) {
                                if (spd != null) {
                                    spd = new PropertyDescriptor(spd, pd);
                                } else {
                                    spd = pd;
                                }
                            }
                        } else {
                            if (spd != null) {
                                spd = new PropertyDescriptor(spd, pd);
                            } else {
                                spd = pd;
                            }
                        }
                    }
                }
            }

            // At this stage we should have either PDs or IPDs for the
            // representative getters and setters. The order at which the
            // property descriptors are determined represent the
            // precedence of the property ordering.
            pd = null; ipd = null;

            if (igpd != null && ispd != null) {
                // Complete indexed properties set
                // Merge any classic property descriptors
                if (gpd != null) {
                    PropertyDescriptor tpd = mergePropertyDescriptor(igpd, gpd);
                    if (tpd instanceof IndexedPropertyDescriptor) {
                        igpd = (IndexedPropertyDescriptor)tpd;
                    }
                }
                if (spd != null) {
                    PropertyDescriptor tpd = mergePropertyDescriptor(ispd, spd);
                    if (tpd instanceof IndexedPropertyDescriptor) {
                        ispd = (IndexedPropertyDescriptor)tpd;
                    }
                }
                if (igpd == ispd) {
                    pd = igpd;
                } else {
                    pd = mergePropertyDescriptor(igpd, ispd);
                }
            } else if (gpd != null && spd != null) {
                // Complete simple properties set
                if (gpd == spd) {
                    pd = gpd;
                } else {
                    pd = mergePropertyDescriptor(gpd, spd);
                }
            } else if (ispd != null) {
                // indexed setter
                pd = ispd;
                // Merge any classic property descriptors
                if (spd != null) {
                    pd = mergePropertyDescriptor(ispd, spd);
                }
                if (gpd != null) {
                    pd = mergePropertyDescriptor(ispd, gpd);
                }
            } else if (igpd != null) {
                // indexed getter
                pd = igpd;
                // Merge any classic property descriptors
                if (gpd != null) {
                    pd = mergePropertyDescriptor(igpd, gpd);
                }
                if (spd != null) {
                    pd = mergePropertyDescriptor(igpd, spd);
                }
            } else if (spd != null) {
                // simple setter
                pd = spd;
            } else if (gpd != null) {
                // simple getter
                pd = gpd;
            }

            // Very special case to ensure that an IndexedPropertyDescriptor
            // doesn't contain less information than the enclosed
            // PropertyDescriptor. If it does, then recreate as a
            // PropertyDescriptor. See 4168833
            if (pd instanceof IndexedPropertyDescriptor) {
                ipd = (IndexedPropertyDescriptor)pd;
                if (ipd.getIndexedReadMethod() == null && ipd.getIndexedWriteMethod() == null) {
                    pd = new PropertyDescriptor(ipd);
                }
            }

            // Find the first property descriptor
            // which does not have getter and setter methods.
            // See regression bug 4984912.
            if ( (pd == null) && (list.size() > 0) ) {
                pd = (PropertyDescriptor) list.get(0);
            }

            if (pd != null) {
                properties.put(pd.getName(), pd);
            }
        }
    }

    /**
     * Adds the property descriptor to the indexedproperty descriptor only if the
     * types are the same.
     *
     * The most specific property descriptor will take precedence.
     */
    private PropertyDescriptor mergePropertyDescriptor(IndexedPropertyDescriptor ipd,
                                                       PropertyDescriptor pd) {
        PropertyDescriptor result = null;

        Class propType = pd.getPropertyType();
        Class ipropType = ipd.getIndexedPropertyType();

        if (propType.isArray() && propType.getComponentType() == ipropType) {
            if (pd.getClass0().isAssignableFrom(ipd.getClass0())) {
                result = new IndexedPropertyDescriptor(pd, ipd);
            } else {
                result = new IndexedPropertyDescriptor(ipd, pd);
            }
        } else {
            // Cannot merge the pd because of type mismatch
            // Return the most specific pd
            if (pd.getClass0().isAssignableFrom(ipd.getClass0())) {
                result = ipd;
            } else {
                result = pd;
                // Try to add methods which may have been lost in the type change
                // See 4168833
                Method write = result.getWriteMethod();
                Method read = result.getReadMethod();

                if (read == null && write != null) {
                    read = findMethod(result.getClass0(),
                                      GET_PREFIX + NameGenerator.capitalize(result.getName()), 0);
                    if (read != null) {
                        try {
                            result.setReadMethod(read);
                        } catch (IntrospectionException ex) {
                            // no consequences for failure.
                        }
                    }
                }
                if (write == null && read != null) {
                    write = findMethod(result.getClass0(),
                                       SET_PREFIX + NameGenerator.capitalize(result.getName()), 1,
                                       new Class[] { FeatureDescriptor.getReturnType(result.getClass0(), read) });
                    if (write != null) {
                        try {
                            result.setWriteMethod(write);
                        } catch (IntrospectionException ex) {
                            // no consequences for failure.
                        }
                    }
                }
            }
        }
        return result;
    }

    // Handle regular pd merge
    private PropertyDescriptor mergePropertyDescriptor(PropertyDescriptor pd1,
                                                       PropertyDescriptor pd2) {
        if (pd1.getClass0().isAssignableFrom(pd2.getClass0())) {
            return new PropertyDescriptor(pd1, pd2);
        } else {
            return new PropertyDescriptor(pd2, pd1);
        }
    }

    // Handle regular ipd merge
    private PropertyDescriptor mergePropertyDescriptor(IndexedPropertyDescriptor ipd1,
                                                       IndexedPropertyDescriptor ipd2) {
        if (ipd1.getClass0().isAssignableFrom(ipd2.getClass0())) {
            return new IndexedPropertyDescriptor(ipd1, ipd2);
        } else {
            return new IndexedPropertyDescriptor(ipd2, ipd1);
        }
    }

    /**
     * @return An array of EventSetDescriptors describing the kinds of
     * events fired by the target bean.
     */
    private EventSetDescriptor[] getTargetEventInfo() throws IntrospectionException {
        if (events == null) {
            events = new HashMap();
        }

        // Check if the bean has its own BeanInfo that will provide
        // explicit information.
        EventSetDescriptor[] explicitEvents = null;
        if (explicitBeanInfo != null) {
            explicitEvents = explicitBeanInfo.getEventSetDescriptors();
            int ix = explicitBeanInfo.getDefaultEventIndex();
            if (ix >= 0 && ix < explicitEvents.length) {
                defaultEventName = explicitEvents[ix].getName();
            }
        }

        if (explicitEvents == null && superBeanInfo != null) {
            // We have no explicit BeanInfo events.  Check with our parent.
            EventSetDescriptor supers[] = superBeanInfo.getEventSetDescriptors();
            for (int i = 0 ; i < supers.length; i++) {
                addEvent(supers[i]);
            }
            int ix = superBeanInfo.getDefaultEventIndex();
            if (ix >= 0 && ix < supers.length) {
                defaultEventName = supers[ix].getName();
            }
        }

        for (int i = 0; i < additionalBeanInfo.length; i++) {
            EventSetDescriptor additional[] = additionalBeanInfo[i].getEventSetDescriptors();
            if (additional != null) {
                for (int j = 0 ; j < additional.length; j++) {
                    addEvent(additional[j]);
                }
            }
        }

        if (explicitEvents != null) {
            // Add the explicit explicitBeanInfo data to our results.
            for (int i = 0 ; i < explicitEvents.length; i++) {
                addEvent(explicitEvents[i]);
            }

        } else {

            // Apply some reflection to the current class.

            // Get an array of all the public beans methods at this level
            Method methodList[] = getPublicDeclaredMethods(beanClass);

            // Find all suitable "add", "remove" and "get" Listener methods
            // The name of the listener type is the key for these hashtables
            // i.e, ActionListener
            Map adds = null;
            Map removes = null;
            Map gets = null;

            for (int i = 0; i < methodList.length; i++) {
                Method method = methodList[i];
                if (method == null) {
                    continue;
                }
                // skip static methods.
                int mods = method.getModifiers();
                if (Modifier.isStatic(mods)) {
                    continue;
                }
                String name = method.getName();
                // Optimization avoid getParameterTypes
                if (!name.startsWith(ADD_PREFIX) && !name.startsWith(REMOVE_PREFIX)
                    && !name.startsWith(GET_PREFIX)) {
                    continue;
                }

                Class argTypes[] = FeatureDescriptor.getParameterTypes(beanClass, method);
                Class resultType = FeatureDescriptor.getReturnType(beanClass, method);

                if (name.startsWith(ADD_PREFIX) && argTypes.length == 1 &&
                    resultType == Void.TYPE &&
                    Introspector.isSubclass(argTypes[0], eventListenerType)) {
                    String listenerName = name.substring(3);
                    if (listenerName.length() > 0 &&
                        argTypes[0].getName().endsWith(listenerName)) {
                        if (adds == null) {
                            adds = new HashMap();
                        }
                        adds.put(listenerName, method);
                    }
                }
                else if (name.startsWith(REMOVE_PREFIX) && argTypes.length == 1 &&
                         resultType == Void.TYPE &&
                         Introspector.isSubclass(argTypes[0], eventListenerType)) {
                    String listenerName = name.substring(6);
                    if (listenerName.length() > 0 &&
                        argTypes[0].getName().endsWith(listenerName)) {
                        if (removes == null) {
                            removes = new HashMap();
                        }
                        removes.put(listenerName, method);
                    }
                }
                else if (name.startsWith(GET_PREFIX) && argTypes.length == 0 &&
                         resultType.isArray() &&
                         Introspector.isSubclass(resultType.getComponentType(),
                                                 eventListenerType)) {
                    String listenerName  = name.substring(3, name.length() - 1);
                    if (listenerName.length() > 0 &&
                        resultType.getComponentType().getName().endsWith(listenerName)) {
                        if (gets == null) {
                            gets = new HashMap();
                        }
                        gets.put(listenerName, method);
                    }
                }
            }

            if (adds != null && removes != null) {
                // Now look for matching addFooListener+removeFooListener pairs.
                // Bonus if there is a matching getFooListeners method as well.
                Iterator keys = adds.keySet().iterator();
                while (keys.hasNext()) {
                    String listenerName = (String) keys.next();
                    // Skip any "add" which doesn't have a matching "remove" or
                    // a listener name that doesn't end with Listener
                    if (removes.get(listenerName) == null || !listenerName.endsWith("Listener")) {
                        continue;
                    }
                    String eventName = decapitalize(listenerName.substring(0, listenerName.length()-8));
                    Method addMethod = (Method)adds.get(listenerName);
                    Method removeMethod = (Method)removes.get(listenerName);
                    Method getMethod = null;
                    if (gets != null) {
                        getMethod = (Method)gets.get(listenerName);
                    }
                    Class argType = FeatureDescriptor.getParameterTypes(beanClass, addMethod)[0];

                    // generate a list of Method objects for each of the target methods:
                    Method allMethods[] = getPublicDeclaredMethods(argType);
                    List validMethods = new ArrayList(allMethods.length);
                    for (int i = 0; i < allMethods.length; i++) {
                        if (allMethods[i] == null) {
                            continue;
                        }

                        if (isEventHandler(allMethods[i])) {
                            validMethods.add(allMethods[i]);
                        }
                    }
                    Method[] methods = (Method[])validMethods.toArray(new Method[validMethods.size()]);

                    EventSetDescriptor esd = new EventSetDescriptor(eventName, argType,
                                                                    methods, addMethod,
                                                                    removeMethod,
                                                                    getMethod);

                    // If the adder method throws the TooManyListenersException then it
                    // is a Unicast event source.
                    if (throwsException(addMethod,
                                        java.util.TooManyListenersException.class)) {
                        esd.setUnicast(true);
                    }
                    addEvent(esd);
                }
            } // if (adds != null ...
        }
        EventSetDescriptor[] result;
        if (events.size() == 0) {
            result = EMPTY_EVENTSETDESCRIPTORS;
        } else {
            // Allocate and populate the result array.
            result = new EventSetDescriptor[events.size()];
            result = (EventSetDescriptor[])events.values().toArray(result);

            // Set the default index.
            if (defaultEventName != null) {
                for (int i = 0; i < result.length; i++) {
                    if (defaultEventName.equals(result[i].getName())) {
                        defaultEventIndex = i;
                    }
                }
            }
        }
        return result;
    }

    private void addEvent(EventSetDescriptor esd) {
        String key = esd.getName();
        if (esd.getName().equals("propertyChange")) {
            propertyChangeSource = true;
        }
        EventSetDescriptor old = (EventSetDescriptor)events.get(key);
        if (old == null) {
            events.put(key, esd);
            return;
        }
        EventSetDescriptor composite = new EventSetDescriptor(old, esd);
        events.put(key, composite);
    }

    /**
     * @return An array of MethodDescriptors describing the private
     * methods supported by the target bean.
     */
    private MethodDescriptor[] getTargetMethodInfo() {
        if (methods == null) {
            methods = new HashMap(100);
        }

        // Check if the bean has its own BeanInfo that will provide
        // explicit information.
        MethodDescriptor[] explicitMethods = null;
        if (explicitBeanInfo != null) {
            explicitMethods = explicitBeanInfo.getMethodDescriptors();
        }

        if (explicitMethods == null && superBeanInfo != null) {
            // We have no explicit BeanInfo methods.  Check with our parent.
            MethodDescriptor supers[] = superBeanInfo.getMethodDescriptors();
            for (int i = 0 ; i < supers.length; i++) {
                addMethod(supers[i]);
            }
        }

        for (int i = 0; i < additionalBeanInfo.length; i++) {
            MethodDescriptor additional[] = additionalBeanInfo[i].getMethodDescriptors();
            if (additional != null) {
                for (int j = 0 ; j < additional.length; j++) {
                    addMethod(additional[j]);
                }
            }
        }

        if (explicitMethods != null) {
            // Add the explicit explicitBeanInfo data to our results.
            for (int i = 0 ; i < explicitMethods.length; i++) {
                addMethod(explicitMethods[i]);
            }

        } else {

            // Apply some reflection to the current class.

            // First get an array of all the beans methods at this level
            Method methodList[] = getPublicDeclaredMethods(beanClass);

            // Now analyze each method.
            for (int i = 0; i < methodList.length; i++) {
                Method method = methodList[i];
                if (method == null) {
                    continue;
                }
                MethodDescriptor md = new MethodDescriptor(method);
                addMethod(md);
            }
        }

        // Allocate and populate the result array.
        MethodDescriptor result[] = new MethodDescriptor[methods.size()];
        result = (MethodDescriptor[])methods.values().toArray(result);

        return result;
    }

    private void addMethod(MethodDescriptor md) {
        // We have to be careful here to distinguish method by both name
        // and argument lists.
        // This method gets called a *lot, so we try to be efficient.
        String name = md.getName();

        MethodDescriptor old = (MethodDescriptor)methods.get(name);
        if (old == null) {
            // This is the common case.
            methods.put(name, md);
            return;
        }

        // We have a collision on method names.  This is rare.

        // Check if old and md have the same type.
        String[] p1 = md.getParamNames();
        String[] p2 = old.getParamNames();

        boolean match = false;
        if (p1.length == p2.length) {
            match = true;
            for (int i = 0; i < p1.length; i++) {
                if (p1[i] != p2[i]) {
                    match = false;
                    break;
                }
            }
        }
        if (match) {
            MethodDescriptor composite = new MethodDescriptor(old, md);
            methods.put(name, composite);
            return;
        }

        // We have a collision on method names with different type signatures.
        // This is very rare.

        String longKey = makeQualifiedMethodName(name, p1);
        old = (MethodDescriptor)methods.get(longKey);
        if (old == null) {
            methods.put(longKey, md);
            return;
        }
        MethodDescriptor composite = new MethodDescriptor(old, md);
        methods.put(longKey, composite);
    }

    /**
     * Creates a key for a method in a method cache.
     */
    private static String makeQualifiedMethodName(String name, String[] params) {
        StringBuffer sb = new StringBuffer(name);
        sb.append('=');
        for (int i = 0; i < params.length; i++) {
            sb.append(':');
            sb.append(params[i]);
        }
        return sb.toString();
    }

    private int getTargetDefaultEventIndex() {
        return defaultEventIndex;
    }

    private int getTargetDefaultPropertyIndex() {
        return defaultPropertyIndex;
    }

    private BeanDescriptor getTargetBeanDescriptor() {
        // Use explicit info, if available,
        if (explicitBeanInfo != null) {
            BeanDescriptor bd = explicitBeanInfo.getBeanDescriptor();
            if (bd != null) {
                return (bd);
            }
        }
        // OK, fabricate a default BeanDescriptor.
        return new BeanDescriptor(this.beanClass, findCustomizerClass(this.beanClass));
    }

    private static Class<?> findCustomizerClass(Class<?> type) {
        String name = type.getName() + "Customizer";
        try {
            type = ClassFinder.findClass(name, type.getClassLoader());
            // Each customizer should inherit java.awt.Component and implement java.beans.Customizer
            // according to the section 9.3 of JavaBeans&trade; specification
            if (Component.class.isAssignableFrom(type) && Customizer.class.isAssignableFrom(type)) {
                return type;
            }
        }
        catch (Exception exception) {
            // ignore any exceptions
        }
        return null;
    }

    private boolean isEventHandler(Method m) {
        // We assume that a method is an event handler if it has a single
        // argument, whose type inherit from java.util.Event.
        Class argTypes[] = FeatureDescriptor.getParameterTypes(beanClass, m);
        if (argTypes.length != 1) {
            return false;
        }
        return isSubclass(argTypes[0], EventObject.class);
    }

    /*
     * Internal method to return *public* methods within a class.
     */
    private static Method[] getPublicDeclaredMethods(Class clz) {
        // Looking up Class.getDeclaredMethods is relatively expensive,
        // so we cache the results.
        if (!ReflectUtil.isPackageAccessible(clz)) {
            return new Method[0];
        }
        synchronized (BEANINFO_CACHE) {
            Method[] result = declaredMethodCache.get(clz);
            if (result == null) {
                result = clz.getMethods();
                for (int i = 0; i < result.length; i++) {
                    Method method = result[i];
                    if (!method.getDeclaringClass().equals(clz)) {
                        result[i] = null;
                    }
                }
                declaredMethodCache.put(clz, result);
            }
            return result;
        }
    }

    //======================================================================
    // Package private support methods.
    //======================================================================

    /**
     * Internal support for finding a target methodName with a given
     * parameter list on a given class.
     */
    private static Method internalFindMethod(Class start, String methodName,
                                                 int argCount, Class args[]) {
        // For overriden methods we need to find the most derived version.
        // So we start with the given class and walk up the superclass chain.

        Method method = null;

        for (Class cl = start; cl != null; cl = cl.getSuperclass()) {
            Method methods[] = getPublicDeclaredMethods(cl);
            for (int i = 0; i < methods.length; i++) {
                method = methods[i];
                if (method == null) {
                    continue;
                }

                // make sure method signature matches.
                Class params[] = FeatureDescriptor.getParameterTypes(start, method);
                if (method.getName().equals(methodName) &&
                    params.length == argCount) {
                    if (args != null) {
                        boolean different = false;
                        if (argCount > 0) {
                            for (int j = 0; j < argCount; j++) {
                                if (params[j] != args[j]) {
                                    different = true;
                                    continue;
                                }
                            }
                            if (different) {
                                continue;
                            }
                        }
                    }
                    return method;
                }
            }
        }
        method = null;

        // Now check any inherited interfaces.  This is necessary both when
        // the argument class is itself an interface, and when the argument
        // class is an abstract class.
        Class ifcs[] = start.getInterfaces();
        for (int i = 0 ; i < ifcs.length; i++) {
            // Note: The original implementation had both methods calling
            // the 3 arg method. This is preserved but perhaps it should
            // pass the args array instead of null.
            method = internalFindMethod(ifcs[i], methodName, argCount, null);
            if (method != null) {
                break;
            }
        }
        return method;
    }

    /**
     * Find a target methodName on a given class.
     */
    static Method findMethod(Class cls, String methodName, int argCount) {
        return findMethod(cls, methodName, argCount, null);
    }

    /**
     * Find a target methodName with specific parameter list on a given class.
     * <p>
     * Used in the contructors of the EventSetDescriptor,
     * PropertyDescriptor and the IndexedPropertyDescriptor.
     * <p>
     * @param cls The Class object on which to retrieve the method.
     * @param methodName Name of the method.
     * @param argCount Number of arguments for the desired method.
     * @param args Array of argument types for the method.
     * @return the method or null if not found
     */
    static Method findMethod(Class cls, String methodName, int argCount,
                             Class args[]) {
        if (methodName == null) {
            return null;
        }
        return internalFindMethod(cls, methodName, argCount, args);
    }

    /**
     * Return true if class a is either equivalent to class b, or
     * if class a is a subclass of class b, i.e. if a either "extends"
     * or "implements" b.
     * Note tht either or both "Class" objects may represent interfaces.
     */
    static  boolean isSubclass(Class a, Class b) {
        // We rely on the fact that for any given java class or
        // primtitive type there is a unqiue Class object, so
        // we can use object equivalence in the comparisons.
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        for (Class x = a; x != null; x = x.getSuperclass()) {
            if (x == b) {
                return true;
            }
            if (b.isInterface()) {
                Class interfaces[] = x.getInterfaces();
                for (int i = 0; i < interfaces.length; i++) {
                    if (isSubclass(interfaces[i], b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Return true iff the given method throws the given exception.
     */
    private boolean throwsException(Method method, Class exception) {
        Class exs[] = method.getExceptionTypes();
        for (int i = 0; i < exs.length; i++) {
            if (exs[i] == exception) {
                return true;
            }
        }
        return false;
    }

    private static BeanInfoFinder getFinder() {
        AppContext context = AppContext.getAppContext();
        Object object = context.get(FINDER_KEY);
        if (object instanceof BeanInfoFinder) {
            return (BeanInfoFinder) object;
        }
        BeanInfoFinder finder = new BeanInfoFinder();
        context.put(FINDER_KEY, finder);
        return finder;
    }

    /**
     * Try to create an instance of a named class.
     * First try the classloader of "sibling", then try the system
     * classloader then the class loader of the current Thread.
     */
    static Object instantiate(Class sibling, String className)
                 throws InstantiationException, IllegalAccessException,
                                                ClassNotFoundException {
        // First check with sibling's classloader (if any).
        ClassLoader cl = sibling.getClassLoader();
        Class cls = ClassFinder.findClass(className, cl);
        return cls.newInstance();
    }

} // end class Introspector

//===========================================================================

/**
 * Package private implementation support class for Introspector's
 * internal use.
 * <p>
 * Mostly this is used as a placeholder for the descriptors.
 */

class GenericBeanInfo extends SimpleBeanInfo {

    private BeanDescriptor beanDescriptor;
    private EventSetDescriptor[] events;
    private int defaultEvent;
    private PropertyDescriptor[] properties;
    private int defaultProperty;
    private MethodDescriptor[] methods;
    private final Reference<BeanInfo> targetBeanInfoRef;

    public GenericBeanInfo(BeanDescriptor beanDescriptor,
                EventSetDescriptor[] events, int defaultEvent,
                PropertyDescriptor[] properties, int defaultProperty,
                MethodDescriptor[] methods, BeanInfo targetBeanInfo) {
        this.beanDescriptor = beanDescriptor;
        this.events = events;
        this.defaultEvent = defaultEvent;
        this.properties = properties;
        this.defaultProperty = defaultProperty;
        this.methods = methods;
        this.targetBeanInfoRef = new SoftReference<BeanInfo>(targetBeanInfo);
    }

    /**
     * Package-private dup constructor
     * This must isolate the new object from any changes to the old object.
     */
    GenericBeanInfo(GenericBeanInfo old) {

        beanDescriptor = new BeanDescriptor(old.beanDescriptor);
        if (old.events != null) {
            int len = old.events.length;
            events = new EventSetDescriptor[len];
            for (int i = 0; i < len; i++) {
                events[i] = new EventSetDescriptor(old.events[i]);
            }
        }
        defaultEvent = old.defaultEvent;
        if (old.properties != null) {
            int len = old.properties.length;
            properties = new PropertyDescriptor[len];
            for (int i = 0; i < len; i++) {
                PropertyDescriptor oldp = old.properties[i];
                if (oldp instanceof IndexedPropertyDescriptor) {
                    properties[i] = new IndexedPropertyDescriptor(
                                        (IndexedPropertyDescriptor) oldp);
                } else {
                    properties[i] = new PropertyDescriptor(oldp);
                }
            }
        }
        defaultProperty = old.defaultProperty;
        if (old.methods != null) {
            int len = old.methods.length;
            methods = new MethodDescriptor[len];
            for (int i = 0; i < len; i++) {
                methods[i] = new MethodDescriptor(old.methods[i]);
            }
        }
        this.targetBeanInfoRef = old.targetBeanInfoRef;
    }

    public PropertyDescriptor[] getPropertyDescriptors() {
        return properties;
    }

    public int getDefaultPropertyIndex() {
        return defaultProperty;
    }

    public EventSetDescriptor[] getEventSetDescriptors() {
        return events;
    }

    public int getDefaultEventIndex() {
        return defaultEvent;
    }

    public MethodDescriptor[] getMethodDescriptors() {
        return methods;
    }

    public BeanDescriptor getBeanDescriptor() {
        return beanDescriptor;
    }

    public java.awt.Image getIcon(int iconKind) {
        BeanInfo targetBeanInfo = this.targetBeanInfoRef.get();
        if (targetBeanInfo != null) {
            return targetBeanInfo.getIcon(iconKind);
        }
        return super.getIcon(iconKind);
    }
}
