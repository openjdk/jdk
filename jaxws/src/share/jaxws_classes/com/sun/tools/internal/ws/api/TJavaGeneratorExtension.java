/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.api;

import com.sun.codemodel.internal.JMethod;
import com.sun.tools.internal.ws.api.wsdl.TWSDLOperation;
import com.sun.tools.internal.ws.processor.generator.JavaGeneratorExtensionFacade;

/**
 * Provides Java SEI Code generation Extensiblity mechanism.
 *
 * @see JavaGeneratorExtensionFacade
 * @author Vivek Pandey
 * @deprecated This class is deprecated, will be removed in JAX-WS 2.2 RI.
 */
public abstract class TJavaGeneratorExtension {
    /**
     * This method should be used to write annotations on {@link JMethod}.
     *
     * @param wsdlOperation non-null wsdl extensiblity element -  wsdl:portType/wsdl:operation.
     * @param jMethod non-null {@link JMethod}
     */
     public abstract void writeMethodAnnotations(TWSDLOperation wsdlOperation, JMethod jMethod);
}
