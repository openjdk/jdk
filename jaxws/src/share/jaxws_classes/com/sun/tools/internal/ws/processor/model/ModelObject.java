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

package com.sun.tools.internal.ws.processor.model;

import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.xml.sax.Locator;

/**
 *
 * @author WS Development Team
 */
public abstract class ModelObject {
    public abstract void accept(ModelVisitor visitor) throws Exception;

    private final Entity entity;
    protected ErrorReceiver errorReceiver;

    protected ModelObject(Entity entity) {
        this.entity = entity;
    }

    public void setErrorReceiver(ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    public Entity getEntity() {
        return entity;
    }

    public Object getProperty(String key) {
        if (_properties == null) {
            return null;
        }
        return _properties.get(key);
    }

    public void setProperty(String key, Object value) {
        if (value == null) {
            removeProperty(key);
            return;
        }

        if (_properties == null) {
            _properties = new HashMap();
        }
        _properties.put(key, value);
    }

    public void removeProperty(String key) {
        if (_properties != null) {
            _properties.remove(key);
        }
    }

    public Iterator getProperties() {
        if (_properties == null) {
            return Collections.emptyList().iterator();
        } else {
            return _properties.keySet().iterator();
        }
    }

    public Locator getLocator(){
        return entity.getLocator();
    }

    public Map getPropertiesMap() {
        return _properties;
    }

    public void setPropertiesMap(Map m) {
        _properties = m;
    }

    public String getJavaDoc() {
        return javaDoc;
    }

    public void setJavaDoc(String javaDoc) {
        this.javaDoc = javaDoc;
    }

    private String javaDoc;
    private Map _properties;
}
