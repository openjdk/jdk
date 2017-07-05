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
package com.sun.org.apache.xml.internal.security.utils.resolver.implementations;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URISyntaxException;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverContext;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverException;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;

/**
 * A simple ResourceResolver for requests into the local filesystem.
 */
public class ResolverLocalFilesystem extends ResourceResolverSpi {

    private static final int FILE_URI_LENGTH = "file:/".length();

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(ResolverLocalFilesystem.class.getName());

    @Override
    public boolean engineIsThreadSafe() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public XMLSignatureInput engineResolveURI(ResourceResolverContext context)
        throws ResourceResolverException {
        try {
            // calculate new URI
            URI uriNew = getNewURI(context.uriToResolve, context.baseUri);

            String fileName =
                ResolverLocalFilesystem.translateUriToFilename(uriNew.toString());
            FileInputStream inputStream = new FileInputStream(fileName);
            XMLSignatureInput result = new XMLSignatureInput(inputStream);

            result.setSourceURI(uriNew.toString());

            return result;
        } catch (Exception e) {
            throw new ResourceResolverException("generic.EmptyMessage", e, context.attr, context.baseUri);
        }
    }

    /**
     * Method translateUriToFilename
     *
     * @param uri
     * @return the string of the filename
     */
    private static String translateUriToFilename(String uri) {

        String subStr = uri.substring(FILE_URI_LENGTH);

        if (subStr.indexOf("%20") > -1) {
            int offset = 0;
            int index = 0;
            StringBuilder temp = new StringBuilder(subStr.length());
            do {
                index = subStr.indexOf("%20",offset);
                if (index == -1) {
                    temp.append(subStr.substring(offset));
                } else {
                    temp.append(subStr.substring(offset, index));
                    temp.append(' ');
                    offset = index + 3;
                }
            } while(index != -1);
            subStr = temp.toString();
        }

        if (subStr.charAt(1) == ':') {
            // we're running M$ Windows, so this works fine
            return subStr;
        }
        // we're running some UNIX, so we have to prepend a slash
        return "/" + subStr;
    }

    /**
     * @inheritDoc
     */
    public boolean engineCanResolveURI(ResourceResolverContext context) {
        if (context.uriToResolve == null) {
            return false;
        }

        if (context.uriToResolve.equals("") || (context.uriToResolve.charAt(0)=='#') ||
            context.uriToResolve.startsWith("http:")) {
            return false;
        }

        try {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "I was asked whether I can resolve " + context.uriToResolve);
            }

            if (context.uriToResolve.startsWith("file:") || context.baseUri.startsWith("file:")) {
                if (log.isLoggable(java.util.logging.Level.FINE)) {
                    log.log(java.util.logging.Level.FINE, "I state that I can resolve " + context.uriToResolve);
                }
                return true;
            }
        } catch (Exception e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, e.getMessage(), e);
            }
        }

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "But I can't");
        }

        return false;
    }

    private static URI getNewURI(String uri, String baseURI) throws URISyntaxException {
        URI newUri = null;
        if (baseURI == null || "".equals(baseURI)) {
            newUri = new URI(uri);
        } else {
            newUri = new URI(baseURI).resolve(uri);
        }

        // if the URI contains a fragment, ignore it
        if (newUri.getFragment() != null) {
            URI uriNewNoFrag =
                new URI(newUri.getScheme(), newUri.getSchemeSpecificPart(), null);
            return uriNewNoFrag;
        }
        return newUri;
    }
}
