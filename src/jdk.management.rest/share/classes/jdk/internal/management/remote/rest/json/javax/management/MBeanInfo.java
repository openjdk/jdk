/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.management.remote.rest.json.javax.management;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Objects;

import javax.management.*;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONObject;

/**
 */
public class MBeanInfo extends javax.management.MBeanInfo {

    /* Serial version */
    static final long serialVersionUID = -6451021435135161911L;

    public MBeanInfo(String className,
                     String description,
                     MBeanAttributeInfo[] attributes,
                     MBeanConstructorInfo[] constructors,
                     MBeanOperationInfo[] operations,
                     MBeanNotificationInfo[] notifications)
            throws IllegalArgumentException {

        super(className, description, attributes, constructors, operations,
             notifications, null);
    }

    public MBeanInfo(String className,
                     String description,
                     MBeanAttributeInfo[] attributes,
                     MBeanConstructorInfo[] constructors,
                     MBeanOperationInfo[] operations,
                     MBeanNotificationInfo[] notifications,
                     Descriptor descriptor)
            throws IllegalArgumentException {

        super(className, description, attributes, constructors, operations,
             notifications, null);
    }

    public static MBeanInfo from(JSONObject json) {
        System.err.println("MBeanInfo FROM: " + json.toJsonString());

        String        name = JSONObject.getObjectFieldString(json, "name");
        String   className = JSONObject.getObjectFieldString(json, "className");
        String description = JSONObject.getObjectFieldString(json, "description");

        // MBeanAttributeInfo[]
        // Create directly using standard constructor.
        MBeanAttributeInfo[] attributes = null;
        JSONArray ai = JSONObject.getObjectFieldArray(json, "attributeInfo");
        if (ai !=null && ai.size() > 0) {
        attributes = new MBeanAttributeInfo[ai.size()];
        for (int i = 0; i < ai.size(); i++) {
            JSONObject j = (JSONObject) ai.get(i);
            String aName = JSONObject.getObjectFieldString(j, "name");
            String aType = JSONObject.getObjectFieldString(j, "type");
            String aAccess = JSONObject.getObjectFieldString(j, "access");
            String aDescription = JSONObject.getObjectFieldString(j, "description");
            String aDescriptor = JSONObject.getObjectFieldString(j, "descriptor");

            attributes[i] = new MBeanAttributeInfo(aName, aType, aDescription,
                                           aAccess.contains("read"), aAccess.contains("write"),
                                           name.startsWith("is"));
        }
        }

        MBeanConstructorInfo[] constructors = null;

        // MBeanOperationInfo[]
        MBeanOperationInfo[] operations = null;
        JSONArray oi = JSONObject.getObjectFieldArray(json, "operationInfo");
        if (oi != null && oi.size() > 0) {
        operations = new MBeanOperationInfo[oi.size()];
        for (int i = 0; i < oi.size(); i++) {
            JSONObject j = (JSONObject) oi.get(i);
            String oName = JSONObject.getObjectFieldString(j, "name");
            String oDescription = JSONObject.getObjectFieldString(j, "description");
            MBeanParameterInfo[] oSignature = createOperationSignature(JSONObject.getObjectFieldObject(j, "signature"));
            String oType = JSONObject.getObjectFieldString(j, "type");
            int oImpact = parseImpact(JSONObject.getObjectFieldString(j, "impact"));
            String oDescriptor = JSONObject.getObjectFieldString(j, "descriptor");

            operations[i] = new MBeanOperationInfo(oName, oDescription, oSignature, oType, oImpact);
        }
        }

        MBeanNotificationInfo[] notifications = null;

        return new MBeanInfo(className, description, attributes, constructors, operations, notifications);
    }

    protected static int parseImpact(String s) {
        return 1;
    }
    
    protected static MBeanParameterInfo[] createOperationSignature(JSONObject json) {
        if (json == null) {
            return null;
        }
 
        MBeanParameterInfo[] mbpi = new MBeanParameterInfo[0];

        return mbpi;
    }
}

