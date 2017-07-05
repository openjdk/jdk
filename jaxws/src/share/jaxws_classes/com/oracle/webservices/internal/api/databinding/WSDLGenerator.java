/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
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
