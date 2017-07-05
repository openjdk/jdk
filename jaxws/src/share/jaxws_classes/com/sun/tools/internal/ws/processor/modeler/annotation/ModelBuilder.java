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

package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.tools.internal.ws.processor.modeler.ModelerException;
import com.sun.tools.internal.ws.wscompile.WsgenOptions;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.File;

/**
 * @author WS Development Team
 */
public interface ModelBuilder {

    ProcessingEnvironment getProcessingEnvironment();

    String getOperationName(Name methodName);

    TypeMirror getHolderValueType(TypeMirror type);

    boolean checkAndSetProcessed(TypeElement typeElement);

    /**
     * Checks if type is a service specific exception
     *
     * @param typeMirror the given element's type
     * @return true if is not a service specific exception as defined by JAX-WS specification
     */
    boolean isServiceException(TypeMirror typeMirror);

    boolean isRemote(TypeElement typeElement);

    boolean canOverWriteClass(String className);

    WsgenOptions getOptions();

    File getSourceDir();

    void log(String msg);

    void processWarning(String message);

    void processError(String message);

    void processError(String message, Element element) throws ModelerException;
}
