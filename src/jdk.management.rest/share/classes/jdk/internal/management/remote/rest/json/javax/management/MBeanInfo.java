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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.Set;

import javax.management.*;
import javax.management.modelmbean.DescriptorSupport;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.JSONPrimitive;

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
//        System.err.println("MBeanInfo FROM: " + json.toJsonString());

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
            // Descriptor can be null, but can contain openType, etc...
            JSONObject aDescriptorJSON = JSONObject.getObjectFieldObject(j, "descriptor");
            Descriptor aDescriptor = null;
            if (aDescriptorJSON != null) {
                aDescriptor = createDescriptor(aDescriptorJSON);
                // XXX do we use type info from aDescriptor?

                /*String originalType = (String) aDescriptor.getFieldValue("originalType");
                if (originalType != null) {
                    aType = originalType;
                    System.err.println("XXXX using originalType '" + aType + "' from: " + aDescriptorJSON.toJsonString());
                } */
               /* String openType = (String) aDescriptor.getFieldValue("openType");
                if (openType != null) {
                    aType = openType;
                    System.err.println("XXXX using openType '" + aType + "' from: " + aDescriptorJSON.toJsonString());
                } */
            }

            // Attribute type may have been CompositeData, but Descriptor has the full type definition.
            attributes[i] = new MBeanAttributeInfo(aName, aType, aDescription,
                                           aAccess.contains("read"), aAccess.contains("write"),
                                           name.startsWith("is"), aDescriptor);
//            System.err.println("XXXX MBAI " + i + " = " + attributes[i]);
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
                MBeanParameterInfo[] oSignature = createOperationSignature(JSONObject.getObjectFieldArray(j, "signature"));
                String oType = JSONObject.getObjectFieldString(j, "returnType");
                int oImpact = parseImpact(JSONObject.getObjectFieldString(j, "impact"));
                        JSONObject oDescriptorJSON = JSONObject.getObjectFieldObject(j, "descriptor");
                Descriptor oDescriptor = null;
                if (oDescriptorJSON != null) {
                    oDescriptor = createDescriptor(oDescriptorJSON);
                }
                operations[i] = new MBeanOperationInfo(oName, oDescription, oSignature, oType, oImpact, oDescriptor);
//            System.err.println("XXXX MBOI " + i + " = " + operations[i]);
            }
        }

        MBeanNotificationInfo[] notifications = null;
        JSONArray ni = JSONObject.getObjectFieldArray(json, "operationInfo");
        if (ni != null && ni.size() > 0) {
            notifications = new MBeanNotificationInfo[ni.size()];
            for (int i = 0; i < ni.size(); i++) {
                JSONObject j = (JSONObject) ni.get(i);
                String nName = JSONObject.getObjectFieldString(j, "name");
                String [] nNotifTypes = null;
                JSONArray n = JSONObject.getObjectFieldArray(j, "notifTypes");
                if (n != null && n.size() > 0) {
                    nNotifTypes = new String[n.size()];
                    for (int k = 0; k < n.size(); k++) {
                        nNotifTypes[k] = (String) ((JSONPrimitive) n.get(k)).getValue();
                    }
                }
                String nDescription = JSONObject.getObjectFieldString(j, "description");
                String nDescriptor = JSONObject.getObjectFieldString(j, "descriptor");
                notifications[i] = new MBeanNotificationInfo(nNotifTypes, nName, nDescription); // XXXX , nDescriptor);
            }
        }
        return new MBeanInfo(className, description, attributes, constructors, operations, notifications);
    }

    protected static int parseImpact(String s) {
        return 1; // XXXX
    }

    protected static Descriptor createDescriptor(JSONObject j) {
        if (j == null) {
            return null;
        }
        Set<String> keys = j.keySet();
        String [] k = new String[keys.size()];
        String [] v = new String[keys.size()];
        int i = 0;
        for (String key : keys) {
            k[i] = key;
            v[i] = JSONObject.getObjectFieldString(j, key);
            i++;
        }
        DescriptorSupport d = new DescriptorSupport(k,v);
        return d;
    }

/*
    protected static Descriptor parseDescriptor(String s) {
        if (s == null) {
            return null;
        }
        System.err.println("parseDescriptor: " + s);
        // https://docs.oracle.com/en/java/javase/23/docs/api/java.management/javax/management/Descriptor.html
        // DescriptorSupport can be constructed from arrays if names and values.
        List<String> n = new ArrayList<>();
        List<String> v = new ArrayList<>();
        // Descriptor string is comma-separated, quoted name:value pairs.
        int pos = 0;
        while (true) {
            String name = parseDescriptorComponent(s, pos);
            if (name == null) {
                break;
            }
            pos += name.length() + 2;
            if (s.charAt(pos++) != ':') {
                break;
            }
            if (s.charAt(pos++) != ' ') {
                break;
            }
            String value = parseDescriptorComponent(s, pos);
            if (value == null) {
                break;
            }
            n.add(name);
            n.add(value);
            System.err.println("DDDDD " + name +", " + value);
            pos += name.length() + 2;
            if (s.charAt(pos) != ',') {
                break;
            }
        } 
         
        DescriptorSupport d = new DescriptorSupport((String []) n.toArray(), (String []) v.toArray());
        return d;
    } 

    protected static String parseDescriptorComponent(String s, int pos) {
        // Parse one quoted value from the String.
        if (s.charAt(pos) != '\"') {
            return null;
        }
        pos = s.indexOf('\"', pos+1);
        if (pos < 0) {
            return null;
        }
        return s.substring(1, pos);
    } */
    
    protected static MBeanParameterInfo[] createOperationSignature(JSONArray json) {
        if (json == null) {
            return null; // Valid empty signature.
        }
        // XXXX 
        MBeanParameterInfo[] mbpi = new MBeanParameterInfo[json.size()];
        for (int i = 0; i<json.size(); i++) {
            JSONObject j = (JSONObject) json.get(i);
            String name = JSONObject.getObjectFieldString(j, "name");
            String type = JSONObject.getObjectFieldString(j, "type");
            String description = JSONObject.getObjectFieldString(j, "description");
            Descriptor descriptor = createDescriptor(JSONObject.getObjectFieldObject(j, "descriptor"));

            MBeanParameterInfo mbp = new MBeanParameterInfo(name, type, description, descriptor);
//            System.err.println("XXXX MBParamInfo: " + mbp);
            mbpi[i] = mbp;
        }
        return mbpi;
    }
}

