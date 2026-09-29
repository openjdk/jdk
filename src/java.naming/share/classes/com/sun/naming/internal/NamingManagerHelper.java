/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.naming.internal;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.directory.Attributes;
import javax.naming.spi.DirObjectFactory;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.ObjectFactoryBuilder;
import java.net.MalformedURLException;
import java.util.Hashtable;
import java.util.function.Predicate;

public class NamingManagerHelper {

    public static Object getObjectInstance(Object refInfo, Name name, Context nameCtx,
                                           Hashtable<?,?> environment,
                                           Predicate<Class<?>> factoryFilter) throws Exception {
        ObjectFactory factory;

        // Use builder if installed
        ObjectFactoryBuilder builder = getObjectFactoryBuilder();
        if (builder != null) {
            // builder must return non-null factory
            factory = builder.createObjectFactory(refInfo, environment);
            return factory.getObjectInstance(refInfo, name, nameCtx,
                    environment);
        }

        // Use reference if possible
        Reference ref = null;
        if (refInfo instanceof Reference) {
            ref = (Reference) refInfo;
        } else if (refInfo instanceof Referenceable) {
            ref = ((Referenceable)(refInfo)).getReference();
        }

        Object answer;

        if (ref != null) {
            String f = ref.getFactoryClassName();
            if (f != null) {
                // if reference identifies a factory, use exclusively

                factory = getObjectFactoryFromReference(ref, f, factoryFilter);
                if (factory != null) {
                    return factory.getObjectInstance(ref, name, nameCtx,
                            environment);
                }
                // No factory found, so return original refInfo.
                // That could happen if:
                //  - a factory class is not in a class path and reference does
                //    not contain a URL for it
                //  - a factory class is available but object factory filters
                //    disallow its usage
                return refInfo;

            } else {
                // if reference has no factory, check for addresses
                // containing URLs

                answer = processURLAddrs(ref, name, nameCtx, environment);
                if (answer != null) {
                    return answer;
                }
            }
        }

        // try using any specified factories
        answer =
                createObjectFromFactories(refInfo, name, nameCtx, environment);
        return (answer != null) ? answer : refInfo;
    }


    public static Object getDirObjectInstance(Object refInfo, Name name, Context nameCtx,
                      Hashtable<?,?> environment, Attributes attrs,
                      Predicate<Class<?>> factoryFilter) throws Exception {
        ObjectFactory factory;

        ObjectFactoryBuilder builder = getObjectFactoryBuilder();
        if (builder != null) {
            // builder must return non-null factory
            factory = builder.createObjectFactory(refInfo, environment);
            if (factory instanceof DirObjectFactory) {
                return ((DirObjectFactory)factory).getObjectInstance(
                        refInfo, name, nameCtx, environment, attrs);
            } else {
                return factory.getObjectInstance(refInfo, name, nameCtx,
                        environment);
            }
        }

        // use reference if possible
        Reference ref = null;
        if (refInfo instanceof Reference) {
            ref = (Reference) refInfo;
        } else if (refInfo instanceof Referenceable) {
            ref = ((Referenceable)(refInfo)).getReference();
        }

        Object answer;

        if (ref != null) {
            String f = ref.getFactoryClassName();
            if (f != null) {
                // if reference identifies a factory, use exclusively

                factory = getObjectFactoryFromReference(ref, f, factoryFilter);
                if (factory instanceof DirObjectFactory) {
                    return ((DirObjectFactory)factory).getObjectInstance(
                            ref, name, nameCtx, environment, attrs);
                } else if (factory != null) {
                    return factory.getObjectInstance(ref, name, nameCtx,
                            environment);
                }
                // No factory found, so return original refInfo.
                // That could happen if:
                //  - a factory class is not in a class path and reference does
                //    not contain a URL for it
                //  - a factory class is available but object factory filters
                //    disallow its usage
                return refInfo;

            } else {
                // if reference has no factory, check for addresses
                // containing URLs
                // ignore name & attrs params; not used in URL factory
                // RMI references from '
                answer = processURLAddrs(ref, name, nameCtx, environment);
                if (answer != null) {
                    return answer;
                }
            }
        }

        // try using any specified factories
        answer = createObjectFromFactories(refInfo, name, nameCtx,
                environment, attrs);
        return (answer != null) ? answer : refInfo;
    }

    /**
     * Retrieves the ObjectFactory for the object identified by a reference,
     * using the reference's factory class name and factory codebase
     * to load in the factory's class.
     * @param ref The non-null reference to use.
     * @param factoryName The non-null class name of the factory.
     * @return The object factory for the object identified by ref; null
     * if unable to load the factory.
     */
    static ObjectFactory getObjectFactoryFromReference(
            Reference ref, String factoryName, Predicate<Class<?>> filter)
            throws IllegalAccessException,
            InstantiationException {
        Class<?> clas;

        // Try to use current class loader
        try {
            clas = helper.loadClassWithoutInit(factoryName);
            // Validate factory's class with the objects factory serial filter
            if (!filter.test(clas)) {
                return null;
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
        assert clas != null;
        @SuppressWarnings("deprecation") // Class.newInstance
        ObjectFactory result = (ObjectFactory) clas.newInstance();
        return result;
    }

    /**
     * Creates an object using the factories specified in the
     * {@code Context.OBJECT_FACTORIES} property of the environment
     * or of the provider resource file associated with {@code nameCtx}.
     *
     * @return factory created; null if cannot create
     */
    private static Object createObjectFromFactories(Object obj, Name name,
                                                    Context nameCtx, Hashtable<?,?> environment, Attributes attrs)
            throws Exception {

        FactoryEnumeration factories = ResourceManager.getFactories(
                Context.OBJECT_FACTORIES, environment, nameCtx);

        if (factories == null)
            return null;

        ObjectFactory factory;
        Object answer = null;
        // Try each factory until one succeeds
        while (answer == null && factories.hasMore()) {
            factory = (ObjectFactory)factories.next();
            if (factory instanceof DirObjectFactory) {
                answer = ((DirObjectFactory)factory).
                        getObjectInstance(obj, name, nameCtx, environment, attrs);
            } else {
                answer =
                        factory.getObjectInstance(obj, name, nameCtx, environment);
            }
        }
        return answer;
    }

    /*
     * Ref has no factory.  For each address of type "URL", try its URL
     * context factory.  Returns null if unsuccessful in creating and
     * invoking a factory.
     */
    static Object processURLAddrs(Reference ref, Name name, Context nameCtx,
                                  Hashtable<?,?> environment)
            throws NamingException {

        for (int i = 0; i < ref.size(); i++) {
            RefAddr addr = ref.get(i);
            if (addr instanceof StringRefAddr &&
                    addr.getType().equalsIgnoreCase("URL")) {

                String url = (String)addr.getContent();
                Object answer = processURL(url, name, nameCtx, environment);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return null;
    }

    private static Object processURL(Object refInfo, Name name,
                                     Context nameCtx, Hashtable<?,?> environment)
            throws NamingException {
        Object answer;

        // If refInfo is a URL string, try to use its URL context factory
        // If no context found, continue to try object factories.
        if (refInfo instanceof String) {
            String url = (String)refInfo;
            String scheme = getURLScheme(url);
            if (scheme != null) {
                answer = getURLObject(scheme, refInfo, name, nameCtx,
                        environment);
                if (answer != null) {
                    return answer;
                }
            }
        }

        // If refInfo is an array of URL strings,
        // try to find a context factory for any one of its URLs.
        // If no context found, continue to try object factories.
        if (refInfo instanceof String[]) {
            String[] urls = (String[])refInfo;
            for (int i = 0; i <urls.length; i++) {
                String scheme = getURLScheme(urls[i]);
                if (scheme != null) {
                    answer = getURLObject(scheme, refInfo, name, nameCtx,
                            environment);
                    if (answer != null)
                        return answer;
                }
            }
        }
        return null;
    }

    private static String getURLScheme(String str) {
        int colon_posn = str.indexOf(':');
        int slash_posn = str.indexOf('/');

        if (colon_posn > 0 && (slash_posn == -1 || colon_posn < slash_posn))
            return str.substring(0, colon_posn);
        return null;
    }

    /**
     * Creates an object for the given URL scheme id using
     * the supplied urlInfo.
     * <p>
     * If urlInfo is null, the result is a context for resolving URLs
     * with the scheme id 'scheme'.
     * If urlInfo is a URL, the result is a context named by the URL.
     * Names passed to this context is assumed to be relative to this
     * context (i.e. not a URL). For example, if urlInfo is
     * "ldap://ldap.wiz.com/o=Wiz,c=us", the resulting context will
     * be that pointed to by "o=Wiz,c=us" on the server 'ldap.wiz.com'.
     * Subsequent names that can be passed to this context will be
     * LDAP names relative to this context (e.g. cn="Barbs Jensen").
     * If urlInfo is an array of URLs, the URLs are assumed
     * to be equivalent in terms of the context to which they refer.
     * The resulting context is like that of the single URL case.
     * If urlInfo is of any other type, that is handled by the
     * context factory for the URL scheme.
     * @param scheme the URL scheme id for the context
     * @param urlInfo information used to create the context
     * @param name name of this object relative to {@code nameCtx}
     * @param nameCtx Context whose provider resource file will be searched
     *          for package prefix values (or null if none)
     * @param environment Environment properties for creating the context
     * @see javax.naming.InitialContext
     */
    private static Object getURLObject(String scheme, Object urlInfo,
                                       Name name, Context nameCtx,
                                       Hashtable<?,?> environment)
            throws NamingException {

        // e.g. "ftpURLContextFactory"
        ObjectFactory factory = (ObjectFactory)ResourceManager.getFactory(
                Context.URL_PKG_PREFIXES, environment, nameCtx,
                "." + scheme + "." + scheme + "URLContextFactory", DEFAULT_PKG_PREFIX);

        if (factory == null)
            return null;

        // Found object factory
        try {
            return factory.getObjectInstance(urlInfo, name, nameCtx, environment);
        } catch (NamingException e) {
            throw e;
        } catch (Exception e) {
            NamingException ne = new NamingException();
            ne.setRootCause(e);
            throw ne;
        }
    }

    /**
     * Creates an object using the factories specified in the
     * {@code Context.OBJECT_FACTORIES} property of the environment
     * or of the provider resource file associated with {@code nameCtx}.
     *
     * @return factory created; null if cannot create
     */
    private static Object createObjectFromFactories(Object obj, Name name,
                                                    Context nameCtx, Hashtable<?,?> environment) throws Exception {

        FactoryEnumeration factories = ResourceManager.getFactories(
                Context.OBJECT_FACTORIES, environment, nameCtx);

        if (factories == null)
            return null;

        // Try each factory until one succeeds
        ObjectFactory factory;
        Object answer = null;
        while (answer == null && factories.hasMore()) {
            factory = (ObjectFactory)factories.next();
            answer = factory.getObjectInstance(obj, name, nameCtx, environment);
        }
        return answer;
    }

    public static synchronized void setObjectFactoryBuilder(
            ObjectFactoryBuilder builder) throws NamingException {
        if (object_factory_builder != null)
            throw new IllegalStateException("ObjectFactoryBuilder already set");
        object_factory_builder = builder;
    }

    public static synchronized ObjectFactoryBuilder getObjectFactoryBuilder() {
        return object_factory_builder;
    }

    private static final String DEFAULT_PKG_PREFIX = "com.sun.jndi.url";
    static final VersionHelper helper = VersionHelper.getVersionHelper();

    private static ObjectFactoryBuilder object_factory_builder = null;

}
