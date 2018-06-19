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

import java.util.HashMap;
import java.util.Map;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;

/**
 * During reference validation, we have to retrieve resources from somewhere.
 *
 */
public abstract class ResourceResolverSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(ResourceResolverSpi.class);

    /** Field properties */
    protected Map<String, String> properties;

    /**
     * This is the workhorse method used to resolve resources.
     * @param context Context to use to resolve resources.
     *
     * @return the resource wrapped around a XMLSignatureInput
     *
     * @throws ResourceResolverException
     */
    public abstract XMLSignatureInput engineResolveURI(ResourceResolverContext context)
        throws ResourceResolverException;

    /**
     * Method engineSetProperty
     *
     * @param key
     * @param value
     */
    public void engineSetProperty(String key, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }

    /**
     * Method engineGetProperty
     *
     * @param key
     * @return the value of the property
     */
    public String engineGetProperty(String key) {
        if (properties == null) {
            return null;
        }
        return properties.get(key);
    }

    /**
     *
     * @param newProperties
     */
    public void engineAddProperies(Map<String, String> newProperties) {
        if (newProperties != null && !newProperties.isEmpty()) {
            if (properties == null) {
                properties = new HashMap<>();
            }
            properties.putAll(newProperties);
        }
    }

    /**
     * Tells if the implementation does can be reused by several threads safely.
     * It normally means that the implementation does not have any member, or there is
     * member change between engineCanResolve and engineResolve invocations. Or it maintains all
     * member info in ThreadLocal methods.
     */
    public boolean engineIsThreadSafe() {
        return false;
    }

    /**
     * This method helps the {@link ResourceResolver} to decide whether a
     * {@link ResourceResolverSpi} is able to perform the requested action.
     *
     * @param context Context in which to do resolution.
     * @return true if the engine can resolve the uri
     */
    public abstract boolean engineCanResolveURI(ResourceResolverContext context);

    /**
     * Method engineGetPropertyKeys
     *
     * @return the property keys
     */
    public String[] engineGetPropertyKeys() {
        return new String[0];
    }

    /**
     * Method understandsProperty
     *
     * @param propertyToTest
     * @return true if understands the property
     */
    public boolean understandsProperty(String propertyToTest) {
        String[] understood = this.engineGetPropertyKeys();

        if (understood != null) {
            for (String str : understood) {
                if (str.equals(propertyToTest)) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Fixes a platform dependent filename to standard URI form.
     *
     * @param str The string to fix.
     *
     * @return Returns the fixed URI string.
     */
    public static String fixURI(String str) {

        // handle platform dependent strings
        str = str.replace(java.io.File.separatorChar, '/');

        if (str.length() >= 4) {

            // str =~ /^\W:\/([^/])/ # to speak perl ;-))
            char ch0 = Character.toUpperCase(str.charAt(0));
            char ch1 = str.charAt(1);
            char ch2 = str.charAt(2);
            char ch3 = str.charAt(3);
            boolean isDosFilename = 'A' <= ch0 && ch0 <= 'Z'
                && ch1 == ':' && ch2 == '/'
                && ch3 != '/';

            if (isDosFilename) {
                LOG.debug("Found DOS filename: {}", str);
            }
        }

        // Windows fix
        if (str.length() >= 2) {
            char ch1 = str.charAt(1);

            if (ch1 == ':') {
                char ch0 = Character.toUpperCase(str.charAt(0));

                if ('A' <= ch0 && ch0 <= 'Z') {
                    str = "/" + str;
                }
            }
        }

        // done
        return str;
    }
}
