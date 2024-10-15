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

package javax.management.remote.http;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.*;
import javax.management.MBeanServerConnection;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;

import javax.net.ssl.HttpsURLConnection;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.JSONPrimitive;
import jdk.internal.management.remote.rest.json.parser.JSONParser;
import jdk.internal.management.remote.rest.json.parser.ParseException;

import jdk.internal.management.remote.rest.mapper.JSONDataException;
import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingException;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;

/**
 * Implementation of MBeanServerConnection using HTTP as a transport.
 */
public class HttpRestConnection implements MBeanServerConnection {

    protected String baseURL;
    protected Map<String,?> env;

    protected String defaultDomain;
    protected String[] domains;
    protected int mBeanCount;

    // ObjectNames map to URLs and JSONObjects:
    protected HashMap<ObjectName,String> objectRefMap;
    protected HashMap<ObjectName,JSONObject> objectMap;
    protected HashMap<ObjectName,String> objectInfoRefMap;
    protected HashMap<ObjectName,JSONObject> objectInfoMap;

    private static final String CHARSET = "UTF-8";

    /**
     * Construct given a base URL and env.
     */    
    public HttpRestConnection(String baseURL, Map<String,?> env) {
        this.env = env;
        // URL should end /jmx/servers/servername/ e.g. /jmx/servers/platform/
        this.baseURL = baseURL; // "http://" + host + ":" + port + "/jmx/servers/platform/";
        objectRefMap = new HashMap<ObjectName,String>();
        objectMap = new HashMap<ObjectName, JSONObject>();
        objectInfoRefMap = new HashMap<ObjectName,String>();
        objectInfoMap = new HashMap<ObjectName, JSONObject>();
        System.err.println("XXXX HttpRestConnection created on baseURL " + baseURL);
    }

private static void stacks() {
    for (Map.Entry<Thread, StackTraceElement[]> s : Thread.getAllStackTraces().entrySet()) {
        printStack(s.getKey(), s.getValue());
    }   
}   

private static void printStack(Thread t, StackTraceElement[] stack) {
    System.out.println("\t" + t + " stack: (length = " + stack.length + ")");                                                                       
    if (t != null)      { 
        for (StackTraceElement stack1 : stack) {
            System.out.println("\t" + stack1);
        }                                                                                                                                        
        System.out.println();                                                                                                                   
    }                                                                                                                                        
}

    public void setup() throws IOException { 
        // Fetch /jmx/servers/platform, populate basic info and MBeans.
        System.err.println("XXXX HttpRestConnection setup " + url(baseURL)); 
        JSONObject jo = (JSONObject) getJSONForURL(url(baseURL));
        if (jo == null) {
            throw new IOException("cannot read JSON from base URL");
        }
        defaultDomain = JSONObject.getObjectFieldString(jo, "defaultDomain");

        // domains:  "[JMImplementation, java.util.logging, jdk.management.jfr, java.lang, com.sun.management, java.nio]"
        // populated in MBeanServerResource with Arrays.toString(mbeanServer.getDomains())
        JSONArray domainsArray = JSONObject.getObjectFieldArray(jo, "domains");
        if (domainsArray != null) {
            ArrayList<String> a = new ArrayList<>();
            for (JSONElement e : domainsArray) {
                String d = (String) ((JSONPrimitive) e).getValue();
                a.add(d);
            }
            domains = a.toArray(new String[0]);
        } else {
            domains = new String[0];
        }
        readMBeans();
    }

    protected void readMBeans() throws IOException {
        // Fetch jmx/servers/platform/mbeans
        // { "mbeans": [ { name, interfaceClassName, className, desription, attributeCout, operationCount, href, info }, ...
        // and populate map:

        JSONObject jo = (JSONObject) getJSONForURL(url(baseURL, "mbeans"));
        if (jo == null) {
            throw new IOException("cannot read mbeans at " + baseURL);
        }
        mBeanCount = JSONObject.getObjectFieldInt(jo, "mbeanCount");

        JSONArray mbeans = JSONObject.getObjectFieldArray(jo, "mbeans");
        if (mbeans != null) {
            for (JSONElement b : mbeans) {
                JSONObject bean = (JSONObject) b;
                // Extract info to populate objectMap...
                String name = JSONObject.getObjectFieldString(bean, "name");
                String href = JSONObject.getObjectFieldString(bean, "href");
                if (name != null) {
                    try {
                        objectRefMap.put(new ObjectName(name), href);
                        objectInfoRefMap.put(new ObjectName(name), href + "/info");
                        System.err.println("XXXX readMBeans: " + name);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }
            }
        } else {
            throw new IOException("cannot read mbeans");
        }

    }

    public ObjectInstance createMBean(String className, ObjectName name)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, IOException {

        throw new UnsupportedOperationException("createMBean not supported");
    }

    public ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException,
                   IOException {

        throw new UnsupportedOperationException("createMBean not supported");
    }

    public ObjectInstance createMBean(String className, ObjectName name,
                                      Object params[], String signature[])
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, IOException {

        throw new UnsupportedOperationException("createMBean not supported");
    }

    public ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName, Object params[],
                                      String signature[])
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException,
                   IOException {

        throw new UnsupportedOperationException("createMBean not supported");
    }

    public void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException, MBeanRegistrationException,
                   IOException {

        throw new UnsupportedOperationException("unregisterMBean not supported");
    }

    protected ObjectInstance objectInstanceForName(ObjectName objectName) throws InstanceNotFoundException, IOException {

        JSONObject o = objectInfoForName(objectName);
        if (o == null) {
            throw new InstanceNotFoundException("Not known: " + objectName);
        }
        return objectInstanceForJSON(o);
    }

    protected ObjectInstance objectInstanceForJSON(JSONObject o) throws InstanceNotFoundException, IOException {
        // JSONObject for MBean Info contains info needed to create an ObjectInstance.
        // System.err.println("oifj: " + o.toJsonString());
        String objectName = JSONObject.getObjectFieldString(o, "name");
        String className = JSONObject.getObjectFieldString(o, "className");
        if (className == null) {
            throw new InstanceNotFoundException("No className in MBean info: " + o);
        }
        try {
            ObjectInstance oi = new ObjectInstance(objectName, className);
            return oi;
        } catch (MalformedObjectNameException mone) {
            throw new InstanceNotFoundException("Not found due to: " + mone);
        }
    }

    public ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException, IOException {

        ObjectInstance i = objectInstanceForName(name);
        if (i == null) {
            throw new InstanceNotFoundException("Not found: " + name);
        }
        return i;
    }

    protected boolean query(ObjectName name, QueryExp query, JSONObject json) {
        // See com/sun/jmx/mbeanserver/Repository.java
        if (query == null) {
            return true;
        }
        // XXXXX TODO
        return true;
    }

    protected Set<JSONObject> queryMB(ObjectName name, QueryExp query)
        throws IOException {

        Set<JSONObject> results = new HashSet<>();

        // objectInfoRefMap.keySet is the list of all known ObjectNames.
        // Consider refreshing?

        for (ObjectName n : objectInfoRefMap.keySet()) {
            JSONObject json = objectInfoForName(n);
            if (json != null) {
                if (query(name, query, json)) {
                    results.add(json);
                }
            }
        }
        return results;
    }

    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query)
            throws IOException {

        // Query operations are a client-side convenience now, rather than a means for a
        // client to query a remote server.

        Set<JSONObject> q = queryMB(name, query);
        Set<ObjectInstance> results = new HashSet<>();
        for (JSONObject json : q) {
            try {
                results.add(objectInstanceForJSON(json));
            } catch (InstanceNotFoundException infe) {
                // ignore
            }
        }
        return results;
    }

    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {

        Set<JSONObject> q = queryMB(name, query);
        Set<ObjectName> results = new HashSet<ObjectName>();
        for (JSONObject json : q) {
            JSONElement value = json.get("name");
            String n = (String) ((JSONPrimitive) value).getValue();
            try {
                results.add(new ObjectName(n));
            } catch (MalformedObjectNameException mone) {
                mone.printStackTrace(System.err);
            }
        }
        return results;
    }

    public boolean isRegistered(ObjectName name) throws IOException {
        try {
            ObjectInstance i = objectInstanceForName(name);
            return (i != null);
        } catch (InstanceNotFoundException infe) {
            return false;
        }
    }


    public Integer getMBeanCount() throws IOException {
        // RMI uses com/sun/jmx/mbeanserver/Repository.java which holds a count.

        // Consider refreshing.
        return mBeanCount;
    }

    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException,
                   IOException {

        JSONObject o = objectForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("no object for '" + name + "'");
        }
        // Will need mbean URL/info for Attribute Type information.
        JSONObject oi = objectInfoForName(name);
        if (oi == null) {
            throw new InstanceNotFoundException("no object info for '" + name + "'");
        }
        JSONObject attributes = JSONObject.getObjectFieldObject(o, "attributes");
        if (attributes == null) {
            throw new AttributeNotFoundException("no Attributes in '" + name + "' (internal error)");
        }
        if (!attributes.keySet().contains(attribute)) {
            throw new AttributeNotFoundException("no attribute '" + attribute + "' in object '" + name + "' with JSON: " + attributes.toJsonString());

        }
        JSONElement a = attributes.get(attribute);
        if (a == null) {
            return null;
//            throw new AttributeNotFoundException("no attribute '" + attribute + "' in object '" + name + "' with JSON: " + attributes.toJsonString());
        }
//        System.err.println("XXX getAttribute -> gets Json " + a.toJsonString());

        // Attribute info from .../info
        JSONArray attributeInfo = JSONObject.getObjectFieldArray(oi, "attributeInfo");
        if (attributeInfo == null) {
            throw new AttributeNotFoundException("no attribute '" + attribute + "' in object '" + name + "'");
        }
//        System.err.println("XXX getAttribute -> gets Json " + attributeInfo.toJsonString());
        JSONObject thisAttrInfo = null;
        for (JSONElement ai : attributeInfo) {
            JSONElement n = ((JSONObject) ai).get("name");
            JSONPrimitive nn = (JSONPrimitive) n;
            // System.err.println("AI: " + nn.getValue());
            if (attribute.equals(nn.getValue())) {
                // System.err.println("match");
                thisAttrInfo =  (JSONObject) ai;
                break;
            }
        }
        if (thisAttrInfo == null) {
            throw new AttributeNotFoundException("no attribute info for '" + attribute + "' in object '" + name + "'");
        }

        // We can get a simple typename like "long" or "[J", or we can get a longer opentype name
        // like ""javax.management.openmbean.SimpleType(name=java.lang.Integer)" or
        // "javax.management.openmbean.ArrayType(name=[J,dimension=1,elementType=javax.management.openmbean.SimpleType(name=java.lang.Long),primitiveArray=true)"

        String typeName = JSONObject.getObjectFieldString(thisAttrInfo, "type");
        // System.err.println("getAttr TYPE 1a: '" + typeName + "'");
        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(typeName);
        // System.err.println("getAttr TYPE 1b: mapper = " + typeMapper);

        if (typeMapper == null) {
            JSONObject dd = JSONObject.getObjectFieldObject(thisAttrInfo, "descriptor");
            //System.err.println("TYPE 2: " + dd.toJsonString());
            //System.err.println("TYPE 3: " + openType);
            String ot = JSONObject.getObjectFieldString(dd, "openType");
//            System.err.println("TYPE 4: " + ot);
            typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(ot);

            if (typeMapper == null) {
//                System.err.println("No JSONMapper for: " + dd.toJsonString());
                throw new InstanceNotFoundException("No JSONMapper for: " + dd.toJsonString());
            }
        }

        try {
//            System.err.println("XXXXX a = '" + a.toJsonString() + "'");

            // MBeanResource may indicate errors or not supported with e.g.
            // "< Attribute not supported >"
            // "< Error: No such attribute >"
            // "< Invalid attributes >"
            if (a.toJsonString().startsWith("\"< Attribute not supported: ")) {
                // MmeoryPoolImpl: Usage threshold is not supported
                throw new RuntimeMBeanException(new UnsupportedOperationException(a.toJsonString()));
            }
            return typeMapper.toJavaObject(a);
            // MBeanResource may for error conditions populate the value with e.g.
        } catch (JSONDataException jde) {
//            jde.printStackTrace(System.err);
            throw new InstanceNotFoundException("Mapper " + typeMapper + " gives: " + jde);
        }
    }

    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException,
                   IOException {

        AttributeList results = new AttributeList(attributes.length);
        for (String a : attributes) {
            try {
                Object value = getAttribute(name, a);
                results.add(new Attribute(a, value));  
            } catch (Exception e) {
                // omit from results
            }
        }
        return results;
    }

    public void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException, AttributeNotFoundException,
                   InvalidAttributeValueException, MBeanException,
                   ReflectionException, IOException {

        JSONObject o = objectForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("no object for '" + name + "'");
        }
        String href = objectRefMap.get(name);

        JSONObject oi = objectInfoForName(name);
        if (oi == null) {
            throw new InstanceNotFoundException("no object info for '" + name + "'");
        }
        JSONObject attributes = JSONObject.getObjectFieldObject(o, "attributes");
        if (attributes == null) {
            throw new AttributeNotFoundException("no Attributes in '" + name + "' (internal error)");
        }
        JSONElement a = attributes.get(attribute.getName());
        if (a == null) {
            throw new AttributeNotFoundException("no attribute '" + attribute.getName() + "' in object '" + name + "'");
        }

        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(Attribute.class);
//         System.err.println("AAAAA Attribute = " + attribute + " typeMapper = " + typeMapper);
        try {
            String body = null;
            if (typeMapper != null) {
                body = typeMapper.toJsonValue(attribute).toJsonString();
            } else {
                body = "{ \"" + attribute.getName() +"\": " + attribute.getValue() + "}";
            }
//            System.err.println("AAAAA body = " + body);
            String s = executeHttpPostRequest(url(href), body);
//            System.err.println("AAAAA result = " + s);

        } catch (JSONMappingException jme) {
            jme.printStackTrace(System.err);
        }
    }

    public AttributeList setAttributes(ObjectName name,
                                       AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException, IOException {

        // XXX POST 
        return null;
    }

    /**
      * Generic helper
      */
    protected static List<JSONElement> getFromJSONArrayByFieldValue(JSONArray a, String fieldName, String fieldValue) {
        List<JSONElement> results = new ArrayList<>();

    for (JSONElement e : a) {
        if (e instanceof JSONObject) {
            JSONElement field = ((JSONObject) e).get(fieldName);
            if (field == null) {
                break; // Elements do not have named field.
            }
            if (field instanceof JSONPrimitive) {
                String value = (String) ((JSONPrimitive)field).getValue();
                // System.err.println("YYYY " + field + " = '" + value + "'");
                if (value.equals(fieldValue)) {
                    //  System.err.println("YYYY returning " + field + " = " + value);
                    results.add(e);
                }
            } else {
                break; // Fields called 'fieldName' are not primitives.
            }
        } else {
            break; // Elements are not objects.
        }
    }
    return results;
    }

    /**
     * Helper method for locating an operation.
     */
    protected static JSONElement findMBeanNamedOperationWithSignature(JSONArray a, String fieldName, String fieldValue, String [] signature) {
        // May be multiple operations of the same name.
        List<JSONElement> ops =  getFromJSONArrayByFieldValue(a, fieldName, fieldValue);
        for (JSONElement e : ops) {
            JSONObject o = (JSONObject) e;
            // Attempt to match signature.
            // "arguments" is an array of objects containing name and type.
            JSONArray array = JSONObject.getObjectFieldArray(o, "arguments");
            if (array == null) {
                // Need to match a no-arg operation?
                // System.err.println("ZZZ fmnows: null argumnets in op, signature = " + signature.length);
                if (signature.length == 0) {
                    return e;
                }
            } else if (array.size() == signature.length) {
                // check signature types
                int i = 0;
                for (JSONElement item : array) {
                    JSONObject obj = (JSONObject) item;
                    JSONPrimitive type = (JSONPrimitive) obj.get("type");
//                    System.err.println("PARAMS: " + type.getValue()  + " vs signature = " + signature[i]);
                    if (!type.getValue().equals(signature[i])) {
                        break;
                    }
                    i++;
                }
                if (i == signature.length) {
//                    System.err.println("XXX sig MATCH");
                    return e;
                }
            }
        }
        return null;
    }

    protected static JSONElement findMBeanInfoNamedOperationWithSignature(JSONArray a, String fieldName, String fieldValue, String [] signature) {
        // May be multiple operations of the same name.
        // Here in Mbean/info, the operationInfo array is of objects with
        // name/signature/returnType/descriptor (which contains openType, originalType).
        List<JSONElement> ops =  getFromJSONArrayByFieldValue(a, fieldName, fieldValue);
        for (JSONElement e : ops) {
            JSONObject o = (JSONObject) e;
            // Attempt to match signature.
            // "signature" is an array of objects containing name and type.
            JSONArray array = JSONObject.getObjectFieldArray(o, "signature");
            if (array == null) {
                // Need to match a no-arg operation?
                if (signature.length == 0) {
                    return e;
                }
            } else if (array.size() == signature.length) {
                // check signature types
                int i = 0;
                for (JSONElement item : array) {
                    JSONObject obj = (JSONObject) item;
                    JSONPrimitive type = (JSONPrimitive) obj.get("type");
//                    System.err.println("PARAMS: " + type.getValue()  + " vs signature = " + signature[i]);
                    if (!type.getValue().equals(signature[i])) {
                        break;
                    }
                    i++;
                }
                if (i == signature.length) {
//                    System.err.println("XXX sig MATCH");
                    // OK what is returnType?
                    JSONElement je = ((JSONObject)e).get("descriptor");
//                    System.err.println("\n\n\nZZZ descriptor = " + je.toJsonString());
                    return e;
                }
            }
        }
        return null;
    }

    protected JSONObject buildJSONForParams(JSONObject op, Object params[], String signature[]) {
        // Create a JSONObject with name:value entries.
        // Must have correct number of parameters, which may be zero.
        JSONObject o = new JSONObject();
        for (int i = 0; i < params.length; i++) {
            //JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(params[i]);
            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(signature[i]);
            if (typeMapper == null) {
//                System.err.println("buildJSONForParams: p" + i + ": no TypeMapper for  = " + params[i]
//                                   + " aka " + signature[i]);
//                throw new JSONMappingException("buildJSONForParams: p" + i + ": no TypeMapper for  = " + params[i]
//                                   + " aka " + signature[i]);
            }
            try {
                JSONElement je = typeMapper.toJsonValue(params[i]);
//                System.err.println("XXXX buildJSONForParams p" + i + " = " + params[i] + " aka " + signature[i] + " = " + je);
                o.put("p" + i, je);
            } catch (JSONMappingException je) {
                je.printStackTrace(System.err);
            }
        }
//        System.err.println("XXXX buildJSONForParams returning: " + o);
        return o;
    }

    protected static Object getObjectForType(String originalType, String openType, JSONElement json) {
    /*
     * Figure out a Java Object for type information and some JSON.
     * May use JSONMapper for a named object type, or possibly parse a CompositeType description.
     * e.g.
     * "openType": "javax.management.openmbean.SimpleType(name=java.lang.Boolean)",
     * "originalType": "boolean"
     * or:
     * "openType": "javax.management.openmbean.CompositeType(name=java.lang.management.MemoryUsage,items=((itemName=committed,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),(itemName=init,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),(itemName=max,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long)),(itemName=used,itemType=javax.management.openmbean.SimpleType(name=java.lang.Long))))",
     * "originalType": "java.lang.management.MemoryUsage"
     */
        try {
            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(originalType);
            if (typeMapper != null) {
                return typeMapper.toJavaObject(json);
            }

            // Do we have our private subclass of the wanted class, which enables creating directly from JSON,
            // e.g. jdk.internal.management.remote.rest.json.java.lang.management.ThreadInfo
            try {
                Class<?> c = Class.forName("jdk.internal.management.remote.rest.json." + originalType);
                Method m = c.getMethod("from", JSONObject.class);
                Object o = m.invoke(null, (JSONObject) json);
                return o;
            } catch (ClassNotFoundException cnfe) {
                System.err.println("ZZZZ No private impl for: " + originalType);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        // Parse openType:
//        typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(openType);
//        return typeMapper;
        return null;
    }

    public Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException, IOException {

//        System.err.println("XXX HttpRestConnection.invoke name = " + name + " op = " + operationName
//                           + " params: " + params + " (len=" + params.length + "), signature: " + signature);
/*        
        for (int i = 0; i<params.length; i++) {
            System.err.println("XXX " + i + " = param " + params[i] + ", sig " + signature[i]);
        }
        if (params.length != signature.length) {
            System.err.println("params length " + params.length + " != signature length " + signature.length);
        } */
        Object result = null;

        // Get info for named object.
        JSONObject o = objectForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("no object for '" + name + "'");
        }

        JSONObject oi = objectInfoForName(name);
        if (oi == null) {
            throw new InstanceNotFoundException("no object info for '" + name + "'");
        }
        // find the operation named operationName.

        // MBean's object contains "operations" array of operation objects with name/href/method/arguments/returnType members.
        // But return types are terse.
        // The MBean/info object contains "operationInfo" array 

        JSONArray operations = JSONObject.getObjectFieldArray(o, "operations");
        if (operations == null) {
            throw new IOException("no operations info in '" + name + "' (internal error)");
        }
        if (operations.size() == 0) {
            throw new IOException("no operations in '" + name + "'");
        }
        JSONArray operationInfo = JSONObject.getObjectFieldArray(oi, "operationInfo");
        if (operationInfo == null) {
            throw new IOException("no operationInfo in '" + name + "/info' (internal error)");
        }
        // Find the named operation, which matches the signature.
        //JSONElement a = getFromJSONArrayByFieldValue((JSONArray) operations, "name", operationName);
        JSONElement a  = findMBeanNamedOperationWithSignature(operations, "name", operationName, signature);
        if (a == null) {
            throw new InstanceNotFoundException("no matching operation '" + operationName + "' in object '" + name + "'");
        }
        JSONObject op = (JSONObject) a;

        JSONElement opInfo  = findMBeanInfoNamedOperationWithSignature(operationInfo, "name", operationName, signature);
        if (opInfo == null) {
            throw new InstanceNotFoundException("no operation '" + operationName + "' in object '" + name + "'");
        }

//        System.err.println("XXX invoke -> found operation: " + op.toJsonString());
//        System.err.println("XXX invoke -> found opInfo: " + opInfo.toJsonString());

        JSONObject descriptor = JSONObject.getObjectFieldObject((JSONObject) opInfo, "descriptor");
        // Contains "openType", "originalType", e.g.
        // {"openType": "javax.management.openmbean.SimpleType(name=java.lang.Long)","originalType": "long"}
        if (descriptor == null) {
            throw new IOException("No descriptor in operation info: " + op.toJsonString());
        }
//        System.err.println("XXX invoke -> found descriptor: " + descriptor.toJsonString());
        
        // From the HTTP/REST request, with given params.
        String href = JSONObject.getObjectFieldString(op, "href"); // includes base and operation name
        String method = JSONObject.getObjectFieldString(op, "method");
        String returnType = JSONObject.getObjectFieldString(op, "returnType");

//        System.err.println("XX invoke href " + href + " " + method + " ret: " + returnType);

        // Presumably a POST.
        if (!method.equals("POST")) {
            throw new IOException("Not a POST operation.");
        }
        // Need a JSON object of the params to send: { "p0": param1value }
        JSONObject postBody = buildJSONForParams(op, params, signature);
//        System.out.println("POSTBODY: '" + postBody.toJsonString() + "'");
        // Call.
        String s = executeHttpPostRequest(url(href), postBody.toJsonString());

        if (s == null) {
            throw new IOException("null resonse");
        } else {
        // Parse result.
        try {
        JSONParser parser = new JSONParser(s);
        JSONElement json = parser.parse();
//        System.err.println("INVOKE gets: " + s);
        // Result can be a simple result or a JSON object.

        // On failure:
        // INVOKE gets: {"status": 400,"message": "Invalid JSON : {}","details": "Encountered \" \"}\" \"} \"\" at line 1, column 2.

        // Return type.
        // descriptor has members openType, originalType.
        // e.g. originalType java.lang.management.ThreadInfo, returnType javax.management.openmbean.CompositeData
        String originalType = JSONObject.getObjectFieldString(descriptor, "originalType");
        String openType     = JSONObject.getObjectFieldString(descriptor, "openType");
//        System.err.println("RETTYPE openType = " + openType + "\nRETTYPE originalType " + originalType + "\nRETTYPE returnType " + returnType ); 

        result = getObjectForType(originalType, openType, json);
//        System.err.println("XX invoke result = " + result);

/*        JSONMapper typeMapper = typeMapperFor(originalType, openType);
        System.err.println("RETURNING: typeMapper: " + typeMapper);
        if (typeMapper == null) {
            typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(returnType);
            System.err.println("RETURNING: typeMapper: " + typeMapper);
        } 
        if (typeMapper == null) {
            throw new IOException("No type mapper for '" + originalType + "' for JSON: " + s);
        } 
        Object result = typeMapper.toJavaObject(json);
        System.err.println("RETURNING: " + result);
        return result;
 */
        } catch (Exception e) {
            // JSOMappingException, ParseException
            e.printStackTrace(System.err);
            throw new IOException(e);
        }
        }

        return result;
    }

    public String getDefaultDomain() throws IOException {
        return defaultDomain;

    }

    public String[] getDomains() throws IOException {
        return domains;

    }

    public void addNotificationListener(ObjectName name,
                                        NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException, IOException {

        // addNotification... methods are void, but the HTTP request can check response and throw if appropriate.
        // Throwing where not implemented causes a problem some apps, e.g. JMC.

        JSONObject o = objectInfoForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("Not known: " + name);
        }

        // Server will perform its own addNotificationListener, with a proxy listener to store results.
        JSONObject body = new JSONObject();
        body.put("addNotificationListener", "123");
        body.put("name", name.toString());
        // body.add("filter", xxxx);

        String href = objectRefMap.get(name);
        href += "/addNotificationListener";
        String s = executeHttpPostRequest(url(href), body.toJsonString());
        try {
            JSONParser parser = new JSONParser(s);
            JSONObject json = (JSONObject) parser.parse();
            // Repsonse should contain where to poll for Notifications.
            // handback is sent to the poller for later us.
            System.err.println("XXXX http client addNotifListener result: " + json.toJsonString());
            String ref = JSONObject.getObjectFieldString(json, "href");
            if (ref != null) {
                registerNotif(ref, listener, handback);
            } else {

            }
        } catch (ParseException pe) {
            pe.printStackTrace(System.err);
        }
    }

    public void addNotificationListener(ObjectName name,
                                        ObjectName listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException, IOException {

        // XXXX
        // listener is the name of another object which will listen?

        JSONObject o = objectInfoForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("Not known: " + name);
        }
        JSONObject l = objectInfoForName(listener);
        if (l == null) {
            throw new InstanceNotFoundException("Not known: " + listener);
        }
    }

    protected void registerNotif(String ref, NotificationListener listener, Object handback) {
        // Check Notification polling thread is active.
        if (notifPoller == null) {
            notifPoller = new NotifPoller();
            Thread thr = new Thread(notifPoller);
            thr.setDaemon(true);
            thr.start();
        }    
        // Add ref to locations to poll.
        notifPoller.add(ref, listener, handback);
    }

    // To poll notifs we need: a URL, a listener, a possible handback object.
    private NotifPoller notifPoller;
    protected int pollDelay = 10000;

    protected class NotifPoller implements Runnable {

        protected List<String> refs;
        protected List<NotificationListener> listeners;
        protected volatile boolean active;

        public NotifPoller() {
            refs = new ArrayList<>();
            listeners = new ArrayList<>();
            active = true; // add a stop method?
        }

        public void add(String r, NotificationListener listener, Object handback) {
            refs.add(r);
            listeners.add(listener);
        }

        public void run() {
            while (active) {
                try {
                    Thread.sleep(pollDelay);
                } catch (InterruptedException ie) {
                    // ignored
                }
                JSONObject body = new JSONObject();
                // Could consider a body that requests specific notifs, but for now
                // not poll fetches and clears all notifications.
                body.put("blah", "123");
                for (int i = 0; i < refs.size(); i++) {
                    String r = refs.get(i); 
                    try {
                        String s = executeHttpPostRequest(url(r), body.toJsonString());
                        if (s != null) {
                            JSONParser parser = new JSONParser(s);
                            try {
                            JSONArray j = (JSONArray) parser.parse();
                            System.err.println("XXXX Notification poll gets: " + j.toJsonString());
                            // Recognise any Notifications and invoke them here in the client...
                            NotificationListener listener = listeners.get(i); 
                            Object handback = null;
                            for (JSONElement json : j) {
                                if (json != null && json instanceof JSONObject) {
                                    Notification n = decodeNotification((JSONObject) json);
                                    System.err.println("GOT: " + n);
                                    listener.handleNotification(n, handback);
                                }
                            }
                            } catch (ParseException pe) {
                                pe.printStackTrace(System.err);
                            }
                        } else {
                            System.err.println("XXXX Notification poll post gets null.");
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace(System.err);
                        // should remove r
                    } catch (IOException e) {
                        // e.printStackTrace(System.err);
                    }
                }
            }
        }
    }
    protected static Notification decodeNotification(JSONObject json) {
        // This is manual deserialisation as Notif is not a Composite object or a type the
        // json type mapper handles...
        String type = JSONObject.getObjectFieldString(json, "type");
        String source = JSONObject.getObjectFieldString(json, "source");
        long sequenceNumber = JSONObject.getObjectFieldLong(json, "sequenceNumber");
        long timeStamp = JSONObject.getObjectFieldLong(json, "timeStamp");
        String message = JSONObject.getObjectFieldString(json, "message");
        Notification n = new Notification(type, source, sequenceNumber, timeStamp, message);
        return n;
    }

    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException,
               IOException {

        JSONObject o = objectInfoForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("Not known: " + name);
        }
        // XXXX
        // This is doable:
        // remove local poller
        // call server to remove its listener.
    }

    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException,
                   IOException {

//        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException,
                   IOException {

//        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException,
                   IOException {

//        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException,
                   ReflectionException, IOException {

        // Create an MBeanInfo from JSON:
        JSONObject json = objectInfoForName(name);
        MBeanInfo info = jdk.internal.management.remote.rest.json.javax.management.MBeanInfo.from(json);
        return info;
    }

    public boolean isInstanceOf(ObjectName name, String className)
            throws InstanceNotFoundException, IOException {

        // Used by ManagementFactory.
        JSONObject o = objectInfoForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("no object info for '" + name + "'");
        }
        String objectClassName = JSONObject.getObjectFieldString(o, "className");
        if (objectClassName == null) {
            throw new InstanceNotFoundException("No className in MBean (internal error): " + o);
        }

        if (objectClassName.equals("com.sun.management.internal.OperatingSystemImpl")
            && className.equals("java.lang.management.OperatingSystemMXBean")) {
            return true;
            // hack due to: java.lang.UnsatisfiedLinkError: 'void com.sun.management.internal.OperatingSystemImpl.initialize0()'
        }

        // e.g. java.lang:type=Threading, java.lang.management.ThreadMXBean
        // gets objectClassName: com.sun.management.internal.HotSpotThreadImpl
        //
        // HotSpotThreadImpl extends ThreadImpl implements ThreadMXBean, but
        // interface com.sun.management.ThreadMXBean extends java.lang.management.ThreadMXBean
        try {
            return classEqualsOrImplements(objectClassName, className);
        } catch(UnsatisfiedLinkError ule) {
            System.err.println("XXX isInstanceOf(" + name.toString() + ", " + className + "): " + ule);
        } catch (Throwable e) {
           e.printStackTrace(System.err);
        }
        return false;
    }

    protected boolean classEqualsOrImplements(String className, String wantedName) throws Throwable {
        if (className.equals(wantedName)) {
            return true;
        }
        Class<?> c = Class.forName(className);
        for (Class<?> i : c.getInterfaces()) {
            boolean good = classEqualsOrImplements(i.getName(), wantedName);
            if (good) {
                return good;
            }
        } 
        return false;
    }

    protected static URL url(String s) {
        // Return a URL where s is the complete URL text.
        s = s.replaceAll(" ", "%20");
        try {
            return new URI(s).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            e.printStackTrace(System.err);
        }
        return null;
    }


    protected static URL url(String baseURL, String s) {
        // Return a URL by adding given String to baseURL.
        return url(baseURL + 
                   ((!baseURL.endsWith("/") && !s.startsWith("/")) ? "/" : "")
                   + s);
    }

    protected JSONObject objectForName(ObjectName name) {
        // Info for attributes may be rapidly changing, so fetch eatch time.
        // Some info, e.g. Operations, is likely to be static.

        // ObjectName can be a full name, but also a pattern like: ":type=Type".
        synchronized(objectMap) {
        try {
            // Could check if Object is already known:
            // JSONObject o = objectMap.get(name);
            String ref = objectRefMap.get(name);
            if (ref == null) {
                return null;
            }
            JSONObject o = (JSONObject) getJSONForURL(url(ref));
            objectMap.put(name, o);
            return o;
        } catch (Exception e) {
            return null;
        }
        }
    }

    protected JSONObject objectInfoForName(ObjectName name) {
        // Object info such as name, class, description, attributeInfo, operationInfo
        // is generally static.  Use objectInfoMap as a cache.
        synchronized(objectInfoMap) {
        try {
            JSONObject o = objectInfoMap.get(name);
            if (o == null) {
                String ref = objectInfoRefMap.get(name);
                if (ref == null) {
                    return null;
                }
                o = (JSONObject) getJSONForURL(url(ref));
                objectInfoMap.put(name, o);
            }
            return  o;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
        }
    }

    protected static JSONElement getJSONForURL(URL url) throws IOException {
        // Utility to fetch from a URL, parse and return the JSONElement.
        String str = executeHttpGetRequest(url);
        JSONParser parser = new JSONParser(str);
        try {
            JSONElement json = parser.parse();
            return json;
        } catch (ParseException pe) {
            throw new IOException("Failed to parse JSON", pe);
        }
    }


    protected static String executeHttpGetRequest(URL url) throws MalformedURLException, IOException {
            // HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(false);
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
                int status = con.getResponseCode();
                if (status == 200) {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                        try {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        } catch (IllegalArgumentException iae) {
                            System.err.println("XXX input = " + input);
                            throw iae;
                        }
                        }
                    }
                    return sbuf.toString();
                } else {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getErrorStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                }
    }

    private String executeHttpPostRequest(URL url, String postBody) throws MalformedURLException, IOException {
        if (postBody != null && !postBody.isEmpty()) {
            //HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            connection.setRequestProperty("Authorization", basicAuth);
            try (OutputStreamWriter out = new OutputStreamWriter(
                    connection.getOutputStream(), CHARSET)) {
                out.write(URLEncoder.encode(postBody, CHARSET));
                out.flush();
            }
                int status = connection.getResponseCode();
                if (status == 200) {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                } else {
                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                }
        }
        return null;
    }
}
