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

package com.sun.xml.internal.ws.api.wsdl.parser;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import org.xml.sax.EntityResolver;

/**
 * Extension point for resolving metadata using wsimport.
 * <p/>
 * wsimport would get a {@link MetaDataResolver} using this factory and from it will resolve all the wsdl/schema
 * metadata.
 *
 * Implementor of this class must provide a zero argument constructor so that
 * it can be loaded during service lookup mechanism.
 *
 * @author Vivek Pandey
 * @see com.sun.xml.internal.ws.api.wsdl.parser.MetaDataResolver#resolve(java.net.URI)
 */
public abstract class MetadataResolverFactory {
    /**
     * Gets a {@link com.sun.xml.internal.ws.api.wsdl.parser.MetaDataResolver}
     *
     * @param resolver
     */
    public abstract
    @NotNull
    MetaDataResolver metadataResolver(@Nullable EntityResolver resolver);
}
