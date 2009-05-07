/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.wsdl.parser;

import com.sun.istack.internal.NotNull;

import javax.xml.transform.Source;
import java.util.List;

/**
 * Abstraction over WSDL and Schema metadata
 *
 * @author Vivek Pandey
 */
public abstract class ServiceDescriptor {
    /**
     * Gives list of wsdls
     * @return List of WSDL documents as {@link Source}.
     * {@link javax.xml.transform.Source#getSystemId()} must be Non-null
     */
    public abstract @NotNull List<? extends Source> getWSDLs();

    /**
     * Gives list of schemas.
     * @return List of XML schema documents as {@link Source}. {@link javax.xml.transform.Source#getSystemId()} must be Non-null.
     *
     */
    public abstract @NotNull List<? extends Source> getSchemas();
}
