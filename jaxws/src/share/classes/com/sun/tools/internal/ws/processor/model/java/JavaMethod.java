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

package com.sun.tools.internal.ws.processor.model.java;

import com.sun.tools.internal.ws.resources.ModelMessages;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.processor.model.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * @author WS Development Team
 */
public class JavaMethod {

    private final ErrorReceiver errorReceiver;
    private final String name;
    private final List<JavaParameter> parameters = new ArrayList<JavaParameter>();
    private final List<String> exceptions = new ArrayList<String>();
    private final WsimportOptions options;
    private JavaType returnType;

    public JavaMethod(String name, WsimportOptions options, ErrorReceiver receiver) {
        this.name = name;
        this.returnType = null;
        this.errorReceiver = receiver;
        this.options = options;
    }

    public String getName() {
        return name;
    }

    public JavaType getReturnType() {
        return returnType;
    }

    public void setReturnType(JavaType returnType) {
        this.returnType = returnType;
    }

    private boolean hasParameter(String paramName) {
        for (JavaParameter parameter : parameters) {
            if (paramName.equals(parameter.getName())) {
                return true;
            }
        }
        return false;
    }

    private Parameter getParameter(String paramName){
        for (JavaParameter parameter : parameters) {
            if (paramName.equals(parameter.getName())) {
                return parameter.getParameter();
            }
        }
        return null;
    }

    public void addParameter(JavaParameter param) {
        // verify that this member does not already exist
        if (hasParameter(param.getName())) {
            if(options.isExtensionMode()){
                param.setName(getUniqueName(param.getName()));
            }else{
                Parameter duplicParam = getParameter(param.getName());
                if(param.getParameter().isEmbedded()){
                    errorReceiver.error(param.getParameter().getLocator(), ModelMessages.MODEL_PARAMETER_NOTUNIQUE_WRAPPER(param.getName(), param.getParameter().getEntityName()));
                    errorReceiver.error(duplicParam.getLocator(), ModelMessages.MODEL_PARAMETER_NOTUNIQUE_WRAPPER(param.getName(), duplicParam.getEntityName()));
                }else{
                    errorReceiver.error(param.getParameter().getLocator(), ModelMessages.MODEL_PARAMETER_NOTUNIQUE(param.getName(), param.getParameter().getEntityName()));
                    errorReceiver.error(duplicParam.getLocator(), ModelMessages.MODEL_PARAMETER_NOTUNIQUE(param.getName(), duplicParam.getEntityName()));
                }
                return;
            }
        }
        parameters.add(param);
    }

    public List<JavaParameter> getParametersList() {
        return parameters;
    }

    public void addException(String exception) {
        // verify that this exception does not already exist
        if (!exceptions.contains(exception)) {
            exceptions.add(exception);
        }
    }

    /** TODO: NB uses it, remove it once we expose it thru some API **/
    public Iterator<String> getExceptions() {
        return exceptions.iterator();
    }

    private String getUniqueName(String param){
        int parmNum = 0;
        while(hasParameter(param)){
            param = param + Integer.toString(parmNum++);
        }
        return param;
    }
}
