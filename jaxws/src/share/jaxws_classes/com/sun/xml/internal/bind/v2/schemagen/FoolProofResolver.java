/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.schemagen;

import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;

import com.sun.xml.internal.bind.Util;

/**
 * {@link SchemaOutputResolver} that wraps the user-specified resolver
 * and makes sure that it's following the contract.
 *
 * <p>
 * This protects the rest of the {@link XmlSchemaGenerator} from client programming
 * error.
 */
final class FoolProofResolver extends SchemaOutputResolver {
    private static final Logger logger = Util.getClassLogger();
    private final SchemaOutputResolver resolver;

    public FoolProofResolver(SchemaOutputResolver resolver) {
        assert resolver!=null;
        this.resolver = resolver;
    }

    public Result createOutput(String namespaceUri, String suggestedFileName) throws IOException {
        logger.entering(getClass().getName(),"createOutput",new Object[]{namespaceUri,suggestedFileName});
        Result r = resolver.createOutput(namespaceUri,suggestedFileName);
        if(r!=null) {
            String sysId = r.getSystemId();
            logger.finer("system ID = "+sysId);
            if(sysId!=null) {
                // TODO: make sure that the system Id is absolute

                // don't use java.net.URI, because it doesn't allow some characters (like SP)
                // which can legally used as file names.

                // but don't use java.net.URL either, because it doesn't allow a made-up URI
                // like kohsuke://foo/bar/zot
            } else
                throw new AssertionError("system ID cannot be null");
        }
        logger.exiting(getClass().getName(),"createOutput",r);
        return r;
    }
}
