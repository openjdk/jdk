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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverContext;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;

/**
 */
public class ResolverAnonymous extends ResourceResolverSpi {

    private InputStream inStream;

    @Override
    public boolean engineIsThreadSafe() {
        return true;
    }

    /**
     * @param filename
     * @throws FileNotFoundException
     * @throws IOException
     */
    public ResolverAnonymous(String filename) throws FileNotFoundException, IOException {
        inStream = Files.newInputStream(Paths.get(filename));
    }

    /**
     * @param is
     */
    public ResolverAnonymous(InputStream is) {
        inStream = is;
    }

    /** {@inheritDoc} */
    @Override
    public XMLSignatureInput engineResolveURI(ResourceResolverContext context) {
        XMLSignatureInput input = new XMLSignatureInput(inStream);
        input.setSecureValidation(context.secureValidation);
        return input;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean engineCanResolveURI(ResourceResolverContext context) {
        if (context.uriToResolve == null) {
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    public String[] engineGetPropertyKeys() {
        return new String[0];
    }
}
