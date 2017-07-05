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

package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.ws.processor.util.ClassNameCollector;
import com.sun.tools.internal.xjc.api.ClassNameAllocator;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Vivek Pandey
 *         <p/>
 *         Implementation of Callback interface that allows the driver of the XJC API to rename JAXB-generated classes/interfaces/enums.
 */
public class ClassNameAllocatorImpl implements ClassNameAllocator {
    public ClassNameAllocatorImpl(ClassNameCollector classNameCollector) {
        this.classNameCollector = classNameCollector;
        this.jaxbClasses = new HashSet<String>();
    }

    public String assignClassName(String packageName, String className) {
        if(packageName== null || className == null){
            //TODO: throw Exception
            return className;
        }

        //if either of the values are empty string return the default className
        if(packageName.equals("") || className.equals(""))
            return className;

        String fullClassName = packageName+"."+className;

        // Check if there is any conflict with jaxws generated classes
        Set<String> seiClassNames = classNameCollector.getSeiClassNames();
        if(seiClassNames != null && seiClassNames.contains(fullClassName)){
            className += TYPE_SUFFIX;
        }

        jaxbClasses.add(packageName+"."+className);
        return className;
    }

    /**
     *
     * @return jaxbGenerated classNames
     */
    public Set<String> getJaxbGeneratedClasses() {
        return jaxbClasses;
    }

    private final static String TYPE_SUFFIX = "_Type";
    private ClassNameCollector classNameCollector;
    private Set<String> jaxbClasses;
}
