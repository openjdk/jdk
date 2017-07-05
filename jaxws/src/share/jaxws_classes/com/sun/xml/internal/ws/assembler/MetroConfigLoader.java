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

package com.sun.xml.internal.ws.assembler;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.logging.Logger;
import com.sun.xml.internal.ws.api.ResourceLoader;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.resources.TubelineassemblyMessages;
import com.sun.xml.internal.ws.runtime.config.MetroConfig;
import com.sun.xml.internal.ws.runtime.config.TubeFactoryList;
import com.sun.xml.internal.ws.runtime.config.TubelineDefinition;
import com.sun.xml.internal.ws.runtime.config.TubelineMapping;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.ws.WebServiceException;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectPermission;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.*;
import java.util.PropertyPermission;
import java.util.logging.Level;

/**
 * This class is responsible for locating and loading Metro configuration files
 * (both application jaxws-tubes.xml and default jaxws-tubes-default.xml).
 * <p/>
 * Once the configuration is loaded the class is able to resolve which tubeline
 * configuration belongs to each endpoint or endpoint client. This information is
 * then used in {@link TubelineAssemblyController} to construct the list of
 * {@link TubeCreator} objects that are used in the actual tubeline construction.
 *
 * @author Marek Potociar <marek.potociar at sun.com>
 */
// TODO Move the logic of this class directly into MetroConfig class.
class MetroConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(MetroConfigLoader.class);

    private MetroConfigName defaultTubesConfigNames;

    private static interface TubeFactoryListResolver {

        TubeFactoryList getFactories(TubelineDefinition td);
    }

    private static final TubeFactoryListResolver ENDPOINT_SIDE_RESOLVER = new TubeFactoryListResolver() {

        public TubeFactoryList getFactories(TubelineDefinition td) {
            return (td != null) ? td.getEndpointSide() : null;
        }
    };
    private static final TubeFactoryListResolver CLIENT_SIDE_RESOLVER = new TubeFactoryListResolver() {

        public TubeFactoryList getFactories(TubelineDefinition td) {
            return (td != null) ? td.getClientSide() : null;
        }
    };
    //
    private MetroConfig defaultConfig;
    private URL defaultConfigUrl;
    private MetroConfig appConfig;
    private URL appConfigUrl;

    MetroConfigLoader(Container container, MetroConfigName defaultTubesConfigNames) {
        this.defaultTubesConfigNames = defaultTubesConfigNames;
        ResourceLoader spiResourceLoader = null;
        if (container != null) {
            spiResourceLoader = container.getSPI(ResourceLoader.class);
        }
        // if spi resource can't load resource, default (MetroConfigUrlLoader) is used;
        // it searches the classpath, so it would be most probably used
        // when using jaxws- or metro-defaults from jaxws libraries
        init(container, spiResourceLoader, new MetroConfigUrlLoader(container));
    }

    private void init(Container container, ResourceLoader... loaders) {

        String appFileName = null;
        String defaultFileName = null;
        if (container != null) {
            MetroConfigName mcn = container.getSPI(MetroConfigName.class);
            if (mcn != null) {
                appFileName = mcn.getAppFileName();
                defaultFileName = mcn.getDefaultFileName();
            }
        }
        if (appFileName == null) {
            appFileName = defaultTubesConfigNames.getAppFileName();
        }

        if (defaultFileName == null) {
            defaultFileName = defaultTubesConfigNames.getDefaultFileName();
        }
        this.defaultConfigUrl = locateResource(defaultFileName, loaders);
        if (defaultConfigUrl == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0001_DEFAULT_CFG_FILE_NOT_FOUND(defaultFileName)));
        }

        LOGGER.config(TubelineassemblyMessages.MASM_0002_DEFAULT_CFG_FILE_LOCATED(defaultFileName, defaultConfigUrl));
        this.defaultConfig = MetroConfigLoader.loadMetroConfig(defaultConfigUrl);
        if (defaultConfig == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0003_DEFAULT_CFG_FILE_NOT_LOADED(defaultFileName)));
        }
        if (defaultConfig.getTubelines() == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0004_NO_TUBELINES_SECTION_IN_DEFAULT_CFG_FILE(defaultFileName)));
        }
        if (defaultConfig.getTubelines().getDefault() == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0005_NO_DEFAULT_TUBELINE_IN_DEFAULT_CFG_FILE(defaultFileName)));
        }

        this.appConfigUrl = locateResource(appFileName, loaders);
        if (appConfigUrl != null) {
            LOGGER.config(TubelineassemblyMessages.MASM_0006_APP_CFG_FILE_LOCATED(appConfigUrl));
            this.appConfig = MetroConfigLoader.loadMetroConfig(appConfigUrl);
        } else {
            LOGGER.config(TubelineassemblyMessages.MASM_0007_APP_CFG_FILE_NOT_FOUND());
            this.appConfig = null;
        }
    }

    TubeFactoryList getEndpointSideTubeFactories(URI endpointReference) {
        return getTubeFactories(endpointReference, ENDPOINT_SIDE_RESOLVER);
    }

    TubeFactoryList getClientSideTubeFactories(URI endpointReference) {
        return getTubeFactories(endpointReference, CLIENT_SIDE_RESOLVER);
    }

    private TubeFactoryList getTubeFactories(URI endpointReference, TubeFactoryListResolver resolver) {
        if (appConfig != null && appConfig.getTubelines() != null) {
            for (TubelineMapping mapping : appConfig.getTubelines().getTubelineMappings()) {
                if (mapping.getEndpointRef().equals(endpointReference.toString())) {
                    TubeFactoryList list = resolver.getFactories(getTubeline(appConfig, resolveReference(mapping.getTubelineRef())));
                    if (list != null) {
                        return list;
                    } else {
                        break;
                    }
                }
            }

            if (appConfig.getTubelines().getDefault() != null) {
                TubeFactoryList list = resolver.getFactories(getTubeline(appConfig, resolveReference(appConfig.getTubelines().getDefault())));
                if (list != null) {
                    return list;
                }
            }
        }

        for (TubelineMapping mapping : defaultConfig.getTubelines().getTubelineMappings()) {
            if (mapping.getEndpointRef().equals(endpointReference.toString())) {
                TubeFactoryList list = resolver.getFactories(getTubeline(defaultConfig, resolveReference(mapping.getTubelineRef())));
                if (list != null) {
                    return list;
                } else {
                    break;
                }
            }
        }

        return resolver.getFactories(getTubeline(defaultConfig, resolveReference(defaultConfig.getTubelines().getDefault())));
    }

    TubelineDefinition getTubeline(MetroConfig config, URI tubelineDefinitionUri) {
        if (config != null && config.getTubelines() != null) {
            for (TubelineDefinition td : config.getTubelines().getTubelineDefinitions()) {
                if (td.getName().equals(tubelineDefinitionUri.getFragment())) {
                    return td;
                }
            }
        }

        return null;
    }

    private static URI resolveReference(String reference) {
        try {
            return new URI(reference);
        } catch (URISyntaxException ex) {
            throw LOGGER.logSevereException(new WebServiceException(TubelineassemblyMessages.MASM_0008_INVALID_URI_REFERENCE(reference), ex));
        }
    }


    private static URL locateResource(String resource, ResourceLoader loader) {
        if (loader == null) return null;

        try {
            return loader.getResource(resource);
        } catch (MalformedURLException ex) {
            LOGGER.severe(TubelineassemblyMessages.MASM_0009_CANNOT_FORM_VALID_URL(resource), ex);
        }
        return null;
    }

    private static URL locateResource(String resource, ResourceLoader[] loaders) {

        for (ResourceLoader loader : loaders) {
            URL url = locateResource(resource, loader);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static MetroConfig loadMetroConfig(@NotNull URL resourceUrl) {
        MetroConfig result = null;
        try {
            JAXBContext jaxbContext = createJAXBContext();
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            XMLInputFactory factory = XmlUtil.newXMLInputFactory(true);
            final JAXBElement<MetroConfig> configElement = unmarshaller.unmarshal(factory.createXMLStreamReader(resourceUrl.openStream()), MetroConfig.class);
            result = configElement.getValue();
        } catch (Exception e) {
            LOGGER.warning(TubelineassemblyMessages.MASM_0010_ERROR_READING_CFG_FILE_FROM_LOCATION(resourceUrl.toString()), e);
        }
        return result;
    }

    private static JAXBContext createJAXBContext() throws Exception {
        if (isJDKInternal()) {
            // since jdk classes are repackaged, extra privilege is necessary to create JAXBContext
            return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<JAXBContext>() {
                        @Override
                        public JAXBContext run() throws Exception {
                            return JAXBContext.newInstance(MetroConfig.class.getPackage().getName());
                        }
                    }, createSecurityContext()
            );
        } else {
            // usage from JAX-WS/Metro/Glassfish
            return JAXBContext.newInstance(MetroConfig.class.getPackage().getName());
        }
    }

    private static AccessControlContext createSecurityContext() {
        PermissionCollection perms = new Permissions();
        perms.add(new RuntimePermission("accessClassInPackage.com" + ".sun.xml.internal.ws.runtime.config")); // avoid repackaging
        perms.add(new ReflectPermission("suppressAccessChecks"));
        return new AccessControlContext(
                new ProtectionDomain[]{
                        new ProtectionDomain(null, perms),
                });
    }

    private static boolean isJDKInternal() {
        // avoid "string repackaging"
        return MetroConfigLoader.class.getName().startsWith("com." + "sun.xml.internal.ws");
    }

    private static class MetroConfigUrlLoader extends ResourceLoader {

        Container container; // TODO remove the field together with the code path using it (see below)
        ResourceLoader parentLoader;

        MetroConfigUrlLoader(ResourceLoader parentLoader) {
            this.parentLoader = parentLoader;
        }

        MetroConfigUrlLoader(Container container) {
            this((container != null) ? container.getSPI(ResourceLoader.class) : null);
            this.container = container;
        }

        @Override
        public URL getResource(String resource) throws MalformedURLException {
            LOGGER.entering(resource);
            URL resourceUrl = null;
            try {
                if (parentLoader != null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(TubelineassemblyMessages.MASM_0011_LOADING_RESOURCE(resource, parentLoader));
                    }

                    resourceUrl = parentLoader.getResource(resource);
                }

                if (resourceUrl == null) {
                    resourceUrl = loadViaClassLoaders("com/sun/xml/internal/ws/assembler/" + resource);
                }

                if (resourceUrl == null && container != null) {
                    // TODO: we should remove this code path, the config file should be loaded using ResourceLoader only
                    resourceUrl = loadFromServletContext(resource);
                }

                return resourceUrl;
            } finally {
                LOGGER.exiting(resourceUrl);
            }
        }

        private static URL loadViaClassLoaders(final String resource) {
            URL resourceUrl = tryLoadFromClassLoader(resource, Thread.currentThread().getContextClassLoader());
            if (resourceUrl == null) {
                resourceUrl = tryLoadFromClassLoader(resource, MetroConfigLoader.class.getClassLoader());
                if (resourceUrl == null) {
                    return ClassLoader.getSystemResource(resource);
                }
            }

            return resourceUrl;
        }

        private static URL tryLoadFromClassLoader(final String resource, final ClassLoader loader) {
            return (loader != null) ? loader.getResource(resource) : null;
        }

        private URL loadFromServletContext(String resource) throws RuntimeException {
            Object context = null;
            try {
                final Class<?> contextClass = Class.forName("javax.servlet.ServletContext");
                context = container.getSPI(contextClass);
                if (context != null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(TubelineassemblyMessages.MASM_0012_LOADING_VIA_SERVLET_CONTEXT(resource, context));
                    }
                    try {
                        final Method method = context.getClass().getMethod("getResource", String.class);
                        final Object result = method.invoke(context, "/WEB-INF/" + resource);
                        return URL.class.cast(result);
                    } catch (Exception e) {
                        throw LOGGER.logSevereException(new RuntimeException(TubelineassemblyMessages.MASM_0013_ERROR_INVOKING_SERVLET_CONTEXT_METHOD("getResource()")), e);
                    }
                }
            } catch (ClassNotFoundException e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(TubelineassemblyMessages.MASM_0014_UNABLE_TO_LOAD_CLASS("javax.servlet.ServletContext"));
                }
            }
            return null;
        }
    }

}
