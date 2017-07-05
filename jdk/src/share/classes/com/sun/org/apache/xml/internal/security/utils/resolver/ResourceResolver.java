/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.utils.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.resolver.implementations.ResolverDirectHTTP;
import com.sun.org.apache.xml.internal.security.utils.resolver.implementations.ResolverFragment;
import com.sun.org.apache.xml.internal.security.utils.resolver.implementations.ResolverLocalFilesystem;
import com.sun.org.apache.xml.internal.security.utils.resolver.implementations.ResolverXPointer;
import org.w3c.dom.Attr;

/**
 * During reference validation, we have to retrieve resources from somewhere.
 * This is done by retrieving a Resolver. The resolver needs two arguments: The
 * URI in which the link to the new resource is defined and the baseURI of the
 * file/entity in which the URI occurs (the baseURI is the same as the SystemId).
 */
public class ResourceResolver {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(ResourceResolver.class.getName());

    /** these are the system-wide resolvers */
    private static List<ResourceResolver> resolverList = new ArrayList<ResourceResolver>();

    /** Field resolverSpi */
    private final ResourceResolverSpi resolverSpi;

    /**
     * Constructor ResourceResolver
     *
     * @param resourceResolver
     */
    public ResourceResolver(ResourceResolverSpi resourceResolver) {
        this.resolverSpi = resourceResolver;
    }

    /**
     * Method getInstance
     *
     * @param uri
     * @param baseURI
     * @return the instance
     *
     * @throws ResourceResolverException
     */
    public static final ResourceResolver getInstance(Attr uri, String baseURI)
        throws ResourceResolverException {
        return getInstance(uri, baseURI, false);
    }

    /**
     * Method getInstance
     *
     * @param uri
     * @param baseURI
     * @param secureValidation
     * @return the instance
     *
     * @throws ResourceResolverException
     */
    public static final ResourceResolver getInstance(
        Attr uri, String baseURI, boolean secureValidation
    ) throws ResourceResolverException {
        synchronized (resolverList) {
            for (ResourceResolver resolver : resolverList) {
                ResourceResolver resolverTmp = resolver;
                if (!resolver.resolverSpi.engineIsThreadSafe()) {
                    try {
                        resolverTmp =
                            new ResourceResolver(resolver.resolverSpi.getClass().newInstance());
                    } catch (InstantiationException e) {
                        throw new ResourceResolverException("", e, uri, baseURI);
                    } catch (IllegalAccessException e) {
                        throw new ResourceResolverException("", e, uri, baseURI);
                    }
                }

                if (log.isLoggable(java.util.logging.Level.FINE)) {
                    log.log(java.util.logging.Level.FINE,
                        "check resolvability by class " + resolverTmp.getClass().getName()
                    );
                }

                resolverTmp.resolverSpi.secureValidation = secureValidation;
                if ((resolverTmp != null) && resolverTmp.canResolve(uri, baseURI)) {
                    // Check to see whether the Resolver is allowed
                    if (secureValidation
                        && (resolverTmp.resolverSpi instanceof ResolverLocalFilesystem
                            || resolverTmp.resolverSpi instanceof ResolverDirectHTTP)) {
                        Object exArgs[] = { resolverTmp.resolverSpi.getClass().getName() };
                        throw new ResourceResolverException(
                            "signature.Reference.ForbiddenResolver", exArgs, uri, baseURI
                        );
                    }
                    return resolverTmp;
                }
            }
        }

        Object exArgs[] = { ((uri != null) ? uri.getNodeValue() : "null"), baseURI };

        throw new ResourceResolverException("utils.resolver.noClass", exArgs, uri, baseURI);
    }

    /**
     * Method getInstance
     *
     * @param uri
     * @param baseURI
     * @param individualResolvers
     * @return the instance
     *
     * @throws ResourceResolverException
     */
    public static ResourceResolver getInstance(
        Attr uri, String baseURI, List<ResourceResolver> individualResolvers
    ) throws ResourceResolverException {
        return getInstance(uri, baseURI, individualResolvers, false);
    }

    /**
     * Method getInstance
     *
     * @param uri
     * @param baseURI
     * @param individualResolvers
     * @param secureValidation
     * @return the instance
     *
     * @throws ResourceResolverException
     */
    public static ResourceResolver getInstance(
        Attr uri, String baseURI, List<ResourceResolver> individualResolvers, boolean secureValidation
    ) throws ResourceResolverException {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE,
                "I was asked to create a ResourceResolver and got "
                + (individualResolvers == null ? 0 : individualResolvers.size())
            );
        }

        // first check the individual Resolvers
        if (individualResolvers != null) {
            for (int i = 0; i < individualResolvers.size(); i++) {
                ResourceResolver resolver = individualResolvers.get(i);

                if (resolver != null) {
                    if (log.isLoggable(java.util.logging.Level.FINE)) {
                        String currentClass = resolver.resolverSpi.getClass().getName();
                        log.log(java.util.logging.Level.FINE, "check resolvability by class " + currentClass);
                    }

                    resolver.resolverSpi.secureValidation = secureValidation;
                    if (resolver.canResolve(uri, baseURI)) {
                        return resolver;
                    }
                }
            }
        }

        return getInstance(uri, baseURI, secureValidation);
    }

    /**
     * Registers a ResourceResolverSpi class. This method logs a warning if
     * the class cannot be registered.
     *
     * @param className the name of the ResourceResolverSpi class to be registered
     */
    @SuppressWarnings("unchecked")
    public static void register(String className) {
        try {
            Class<ResourceResolverSpi> resourceResolverClass =
                (Class<ResourceResolverSpi>) Class.forName(className);
            register(resourceResolverClass, false);
        } catch (ClassNotFoundException e) {
            log.log(java.util.logging.Level.WARNING, "Error loading resolver " + className + " disabling it");
        }
    }

    /**
     * Registers a ResourceResolverSpi class at the beginning of the provider
     * list. This method logs a warning if the class cannot be registered.
     *
     * @param className the name of the ResourceResolverSpi class to be registered
     */
    @SuppressWarnings("unchecked")
    public static void registerAtStart(String className) {
        try {
            Class<ResourceResolverSpi> resourceResolverClass =
                (Class<ResourceResolverSpi>) Class.forName(className);
            register(resourceResolverClass, true);
        } catch (ClassNotFoundException e) {
            log.log(java.util.logging.Level.WARNING, "Error loading resolver " + className + " disabling it");
        }
    }

    /**
     * Registers a ResourceResolverSpi class. This method logs a warning if the class
     * cannot be registered.
     * @param className
     * @param start
     */
    public static void register(Class<? extends ResourceResolverSpi> className, boolean start) {
        try {
            ResourceResolverSpi resourceResolverSpi = className.newInstance();
            register(resourceResolverSpi, start);
        } catch (IllegalAccessException e) {
            log.log(java.util.logging.Level.WARNING, "Error loading resolver " + className + " disabling it");
        } catch (InstantiationException e) {
            log.log(java.util.logging.Level.WARNING, "Error loading resolver " + className + " disabling it");
        }
    }

    /**
     * Registers a ResourceResolverSpi instance. This method logs a warning if the class
     * cannot be registered.
     * @param resourceResolverSpi
     * @param start
     */
    public static void register(ResourceResolverSpi resourceResolverSpi, boolean start) {
        synchronized(resolverList) {
            if (start) {
                resolverList.add(0, new ResourceResolver(resourceResolverSpi));
            } else {
                resolverList.add(new ResourceResolver(resourceResolverSpi));
            }
        }
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Registered resolver: " + resourceResolverSpi.toString());
        }
    }

    /**
     * This method registers the default resolvers.
     */
    public static void registerDefaultResolvers() {
        synchronized(resolverList) {
            resolverList.add(new ResourceResolver(new ResolverFragment()));
            resolverList.add(new ResourceResolver(new ResolverLocalFilesystem()));
            resolverList.add(new ResourceResolver(new ResolverXPointer()));
            resolverList.add(new ResourceResolver(new ResolverDirectHTTP()));
        }
    }

    /**
     * Method resolve
     *
     * @param uri
     * @param baseURI
     * @return the resource
     *
     * @throws ResourceResolverException
     */
    public XMLSignatureInput resolve(Attr uri, String baseURI)
        throws ResourceResolverException {
        return resolverSpi.engineResolve(uri, baseURI);
    }

    /**
     * Method setProperty
     *
     * @param key
     * @param value
     */
    public void setProperty(String key, String value) {
        resolverSpi.engineSetProperty(key, value);
    }

    /**
     * Method getProperty
     *
     * @param key
     * @return the value of the property
     */
    public String getProperty(String key) {
        return resolverSpi.engineGetProperty(key);
    }

    /**
     * Method addProperties
     *
     * @param properties
     */
    public void addProperties(Map<String, String> properties) {
        resolverSpi.engineAddProperies(properties);
    }

    /**
     * Method getPropertyKeys
     *
     * @return all property keys.
     */
    public String[] getPropertyKeys() {
        return resolverSpi.engineGetPropertyKeys();
    }

    /**
     * Method understandsProperty
     *
     * @param propertyToTest
     * @return true if the resolver understands the property
     */
    public boolean understandsProperty(String propertyToTest) {
        return resolverSpi.understandsProperty(propertyToTest);
    }

    /**
     * Method canResolve
     *
     * @param uri
     * @param baseURI
     * @return true if it can resolve the uri
     */
    private boolean canResolve(Attr uri, String baseURI) {
        return resolverSpi.engineCanResolve(uri, baseURI);
    }
}
