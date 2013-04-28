/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.webservices.internal.api.databinding;

import java.io.File;

/**
 * WSDLGenerator is used to generate the WSDL representation of the service
 * endpoint interface of the parent Databinding object.
 */
public interface WSDLGenerator {

        /**
         * Sets the inlineSchema boolean. When the inlineSchema is true, the
         * generated schema documents are embedded within the type element of
         * the generated WSDL. When the inlineSchema is false, the generated
         * schema documents are generated as standalone schema documents and
         * imported into the generated WSDL.
         *
         * @param inline the inlineSchema boolean.
         * @return
         */
        WSDLGenerator inlineSchema(boolean inline);

        /**
         * Sets A property of the WSDLGenerator
         *
         * @param name The name of the property
         * @param value The value of the property
         *
     * @return this WSDLGenerator instance
         */
        WSDLGenerator property(String name, Object value);

        /**
         * Generates the WSDL using the wsdlResolver to output the generated
         * documents.
         *
         * @param wsdlResolver The WSDLResolver
         */
        void generate(com.oracle.webservices.internal.api.databinding.WSDLResolver wsdlResolver);

        /**
         * Generates the WSDL into the file directory
         *
         * @param outputDir The output file directory
         * @param name The file name of the main WSDL document
         */
        void generate(File outputDir, String name);
}
