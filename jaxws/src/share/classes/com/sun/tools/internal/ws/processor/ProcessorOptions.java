/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor;

/**
 * Property names used by ProcessorActions
 *
 * @author WS Development Team
 */
public class ProcessorOptions {

    public final static String SOURCE_DIRECTORY_PROPERTY = "sourceDirectory";
    public final static String DESTINATION_DIRECTORY_PROPERTY =
        "destinationDirectory";
    public final static String NONCLASS_DESTINATION_DIRECTORY_PROPERTY =
        "nonclassDestinationDirectory";
    public final static String VALIDATE_WSDL_PROPERTY = "validationWSDL";
    public final static String EXPLICIT_SERVICE_CONTEXT_PROPERTY =
        "explicitServiceContext";
    public final static String PRINT_STACK_TRACE_PROPERTY = "printStackTrace";
    public final static String DONOT_OVERWRITE_CLASSES = "doNotOverWrite";
    public final static String NO_DATA_BINDING_PROPERTY = "noDataBinding";
    public final static String USE_WSI_BASIC_PROFILE = "useWSIBasicProfile";
    public final static String STRICT_COMPLIANCE = "strictCompliance";
    public final static String JAXWS_SOURCE_VERSION = "sourceVersion";
    public final static String UNWRAP_DOC_LITERAL_WRAPPERS =
        "unwrapDocLitWrappers";
    public final static String BINDING_FILES = "bindingFiles";
    public final static String EXTENSION = "extension";
    public final static String PROTOCOL = "protocol";
    public final static String TRANSPORT = "transport";
    public final static String WSDL_LOCATION = "wsdllocation";
    public final static String DEFAULT_PACKAGE = "defaultpackage";
}
