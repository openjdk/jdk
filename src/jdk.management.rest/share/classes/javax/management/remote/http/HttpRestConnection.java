/* * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.management.remote.rest.mapper.JSONDataException;
import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingException;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;

/**
 */
public class HttpRestConnection implements MBeanServerConnection {

    protected String baseURL;
    protected Map<String,?> env;

    protected String defaultDomain;
    protected String[] domains;

    protected HashMap<ObjectName,String> objectMap;
    protected HashMap<ObjectName,String> objectInfoMap;

    private static final String CHARSET = "UTF-8";
    
    public HttpRestConnection(String baseURL, Map<String,?> env) {
        this.env = env;
        // URL should end /jmx/servers/servername/ e.g. /jmx/servers/platform/
        this.baseURL = baseURL; // "http://" + host + ":" + port + "/jmx/servers/platform/";
        System.err.println("HttpRestConnection: baseURL = " + baseURL);
    }

    public void setup() throws IOException { 
        // Fetch /jmx/servers/platform and populate basic info.
        String str = executeHttpGetRequest(url(baseURL, ""));
    
//        System.err.println("XXX setup: raw = " + str);

        try {
        JSONParser parser = new JSONParser(str);
        JSONElement json = parser.parse();
        JSONObject jo = (JSONObject) json;
        JSONElement value = jo.get("defaultDomain");
        // System.err.println("defaultDomain json value = " + value);
        defaultDomain = (String) ((JSONPrimitive) value).getValue();

        // domains:  "[JMImplementation, java.util.logging, jdk.management.jfr, java.lang, com.sun.management, java.nio]"
        // populated in MBeanServerResource with Arrays.toString(mbeanServer.getDomains())
        value = jo.get("domains");

//        System.err.println("domains json value = " + value);
//        System.err.println("domains json class = " + value.getClass());
        if (value instanceof JSONArray) {
            // JSONArray extends ArrayList<JSONElement>. 
            ArrayList<String> a = new ArrayList<>();
            for (JSONElement e : (JSONArray) value) {
                String d = (String) ((JSONPrimitive) e).getValue();
                a.add(d);
            }
            domains = a.toArray(new String[0]);
        } else {
            throw new RuntimeException("JSONArray expected, got: " + value.getClass());
        }
//        System.err.println("domains array = " + domains);

        readMBeans();

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    protected void readMBeans() throws IOException {
        // Fetch jmx/servers/platform/mbeans/ and populate map:
        objectMap = new HashMap<ObjectName,String>();
        objectInfoMap = new HashMap<ObjectName,String>();
        // { "mbeans": [ { name, interfaceClassName, className, desription, attributeCout, operationCount, href, info }, ...
        String str = executeHttpGetRequest(url(baseURL, "mbeans"));
//        System.err.println("XXX readMBeans: str = " + str);

        try {
        JSONParser parser = new JSONParser(str);
        JSONObject jo = (JSONObject) parser.parse();
//        System.err.println("XXX readMBeans: jo = " + jo);
        JSONElement count = jo.get("mbeanCount");

        JSONElement mbeans = jo.get("mbeans");
        if (mbeans instanceof JSONArray) {
            for (JSONElement b : (JSONArray) mbeans) {
//                System.err.println("XXX MBEAN: " + b );
                JSONObject bean = (JSONObject) b;
                // Extract info to populate objectMap...
                JSONElement value = bean.get("name");
                String name = (String) ((JSONPrimitive) value).getValue();
   //             System.err.println("XXX name = " + name);
                value = bean.get("href");
                String href = (String) ((JSONPrimitive) value).getValue();
//                System.err.println("XXX href = " + href);
                if (name != null) {
                    objectMap.put(new ObjectName(name), href);
                    objectInfoMap.put(new ObjectName(name), href + "/info");
                }
            }
        } else {
            throw new RuntimeException("JSONArray expected, got: " + mbeans.getClass());
        }

        } catch (Exception e) {
            throw new IOException(e);
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

    public ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException, IOException {


        return null;
    }

    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query)
            throws IOException {

        String str = executeHttpGetRequest(url(baseURL, "mbeans/"));
        System.err.println("queryMBeans: " + str);

        return null;
    }

    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {
        return null;
    }

    public boolean isRegistered(ObjectName name) throws IOException {
        return false;
    }


    public Integer getMBeanCount() throws IOException {
        // /jmx/servers/platform/mbeans/
        // { "mbeanCount": "27", ...
        // RMI uses com/sun/jmx/mbeanserver/Repository.java which holds a count.
        String str = executeHttpGetRequest(url(baseURL, "mbeans/"));
        // System.err.println("getMBeanCount: raw = " + str);
        JSONParser parser = new JSONParser(str);
        try {
            JSONElement json = parser.parse();
//            System.err.println("getMBeanCount: JSONElement " + json);
//            System.err.println("json class = " + json.getClass());
            JSONObject jo = (JSONObject) json;
//            System.err.println("json Object = " + jo);

//            jo.keySet().forEach((s) -> { System.out.println("XX = '" + s + "'"); });
            JSONElement value = jo.get("mbeanCount");
//            System.err.println("json value = " + value);
            if (value == null) {
                throw new IOException("no field");
            }
            String v = (String) ((JSONPrimitive) value).getValue();
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException nfe) {
                throw new IOException("bad field");
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException,
                   IOException {

        // Covert name to MBean.
        // Get MBean Info.
        JSONObject o = objectForName(name);
        if (o == null) {
            throw new InstanceNotFoundException("no object for '" + name + "'");
        }
        // Will need mbean URL/info for Attribute Type information.
        JSONObject oi = objectInfoForName(name);
        if (oi == null) {
            throw new InstanceNotFoundException("no object info for '" + name + "'");
        }
//        System.err.println("XXX getAttribute gets " + o);
        // Get attribute.
        JSONObject attributes = (JSONObject) o.get("attributes");
        if (attributes == null) {
            throw new AttributeNotFoundException("no Attributes in '" + name + "' (internal error)");
        }
        JSONElement a = attributes.get(attribute);
        if (a == null) {
            throw new AttributeNotFoundException("no attribute '" + attribute + "' in object '" + name + "'");
        }
        System.err.println("XXX -> gets " + a);
        System.err.println("XXX -> gets " + a.toJsonString());
        System.err.println("XXX -> gets " + a.getClass()); // e.g. jdk.internal.management.remote.rest.json.JSONPrimitive
        // String s = (String) ((JSONPrimitive) value).getValue();
//        return ((JSONPrimitive)a).getValue();

        // Attribute info from .../info
        JSONArray attributeInfo = (JSONArray) oi.get("attributeInfo");
        if (attributeInfo == null) {
            throw new AttributeNotFoundException("no attribute '" + attribute + "' in object '" + name + "'");
        }
        // That is a JSONArray
        JSONObject thisAttrInfo = null;
        for (JSONElement ai : attributeInfo) {
            JSONElement n = ((JSONObject) ai).get("name");
            JSONPrimitive nn = (JSONPrimitive) n;
            // System.err.println("AI: " + nn.getValue());
            if (attribute.equals(nn.getValue())) {
                System.err.println("match");
                thisAttrInfo =  (JSONObject) ai;
                break;
            }
        }
        if (thisAttrInfo == null) {
            throw new AttributeNotFoundException("no attribute info for '" + attribute + "' in object '" + name + "'");
        }

        // We can get a simple typename line "long" or "[J", or we can get a longer opentype name
        // like ""javax.management.openmbean.SimpleType(name=java.lang.Integer)" or
        // "javax.management.openmbean.ArrayType(name=[J,dimension=1,elementType=javax.management.openmbean.SimpleType(name=java.lang.Long),primitiveArray=true)"

        JSONElement type = thisAttrInfo.get("type");
        String typeName = (String) ((JSONPrimitive) type).getValue();
        System.err.println("TYPE 1a: '" + typeName + "'");
        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(typeName);
        System.err.println("TYPE 1b: " + typeMapper);

        if (typeMapper == null) {
            JSONObject dd = (JSONObject) thisAttrInfo.get("descriptor");
            //System.err.println("TYPE 2: " + dd.toJsonString());
            JSONElement openType = dd.get("openType");
            //System.err.println("TYPE 3: " + openType);
            String ot = (String) ((JSONPrimitive)openType).getValue();
            System.err.println("TYPE 4: " + ot);
            typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(ot);

            if (typeMapper == null) {
                System.err.println("No JSONMapper for: " + dd.toJsonString());
                throw new InstanceNotFoundException("No JSONMapper for: " + dd.toJsonString());
            }
        }

        try {
            return typeMapper.toJavaObject(a);
        } catch (JSONDataException jde) {
            jde.printStackTrace(System.err);
            throw new InstanceNotFoundException("Mapper " + typeMapper + " gives: " + jde);
        }
    }

    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException,
                   IOException {

        return null;
    }

    public void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException, AttributeNotFoundException,
                   InvalidAttributeValueException, MBeanException,
                   ReflectionException, IOException {

    }

    public AttributeList setAttributes(ObjectName name,
                                       AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException, IOException {

        return null;
    }

    /**
      * Generic helper
      */
    public static List<JSONElement> getFromJSONArrayByFieldValue(JSONArray a, String fieldName, String fieldValue) {
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
    public static JSONElement findMBeanNamedOperationWithSignature(JSONArray a, String fieldName, String fieldValue, String [] signature) {
        // May be multiple operations of the same name.
        List<JSONElement> ops =  getFromJSONArrayByFieldValue(a, fieldName, fieldValue);
        for (JSONElement e : ops) {
            JSONObject o = (JSONObject) e;
            // Attempt to match signature.
            // "arguments" is an array of objects containing name and type.
            JSONArray array = (JSONArray) o.get("arguments");
            if (array.size() == signature.length) {
                // check signature types
                int i = 0;
                for (JSONElement item : array) {
                    JSONObject obj = (JSONObject) item;
                    JSONPrimitive type = (JSONPrimitive) obj.get("type");
                    System.err.println("PARAMS: " + type.getValue()  + " vs signature = " + signature[i]);
                    if (!type.getValue().equals(signature[i])) {
                        break;
                    }
                    i++;
                }
                if (i == signature.length) {
                    System.err.println("XXX sig MATCH");
                    return e;
                }
            }
        }
        return null;
    }

    public static JSONElement findMBeanInfoNamedOperationWithSignature(JSONArray a, String fieldName, String fieldValue, String [] signature) {
        // May be multiple operations of the same name.
        // Here in Mbean/info, the operationInfo array is of objects with
        // name/signature/returnType/descriptor (which contains openType, originalType).
        List<JSONElement> ops =  getFromJSONArrayByFieldValue(a, fieldName, fieldValue);
        for (JSONElement e : ops) {
            JSONObject o = (JSONObject) e;
            // Attempt to match signature.
            // "signature" is an array of objects containing name and type.
            JSONArray array = (JSONArray) o.get("signature");
            if (array.size() == signature.length) {
                // check signature types
                int i = 0;
                for (JSONElement item : array) {
                    JSONObject obj = (JSONObject) item;
                    JSONPrimitive type = (JSONPrimitive) obj.get("type");
                    System.err.println("PARAMS: " + type.getValue()  + " vs signature = " + signature[i]);
                    if (!type.getValue().equals(signature[i])) {
                        break;
                    }
                    i++;
                }
                if (i == signature.length) {
                    System.err.println("XXX sig MATCH");
                    // OK what is returnType?
                    JSONElement je = ((JSONObject)e).get("descriptor");
                    System.err.println("\n\n\nZZZ descriptor = " + je.toJsonString());
                    return e;
                }
            }
        }
        return null;
    }

    public String buildJSONForParams(JSONObject op, Object params[], String signature[]) throws JSONMappingException {
        // Create a JSONObject with name:value entries.
        JSONObject o = new JSONObject();
        for (int i = 0; i < params.length; i++) {
            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(params[i]);
            JSONElement je = typeMapper.toJsonValue(params[i]);
            System.err.println("p" + i + " = " + params[i] + " aka " + signature[i] + " = " + je);
            // XXXXXXX
            o.put("p" + i, je);
        }
        return o.toJsonString();
    }


    public static Object parseType(String originalType, String openType) {

        return null;
    }

    public Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException, IOException {

        System.err.println("XXX HttpRestConnection.invoke name = " + name + " op = " + operationName
                           + " params: " + params + " signature: " + signature);

        // Get info for named object,
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

        JSONElement operations = o.get("operations");
        if (operations == null) {
            throw new IOException("no operations in '" + name + "' (internal error)");
        }
        JSONElement operationInfo = oi.get("operationInfo");
        if (operationInfo == null) {
            throw new IOException("no operationInfo in '" + name + "/info' (internal error)");
        }
        // Find the named operation, which matches the signature.
        //JSONElement a = getFromJSONArrayByFieldValue((JSONArray) operations, "name", operationName);
        JSONElement a  = findMBeanNamedOperationWithSignature((JSONArray) operations, "name", operationName, signature);
        if (a == null) {
            throw new InstanceNotFoundException("no operation '" + operationName + "' in object '" + name + "'");
        }
        JSONElement opInfo  = findMBeanInfoNamedOperationWithSignature((JSONArray) operationInfo, "name", operationName, signature);
        if (opInfo == null) {
            throw new InstanceNotFoundException("no operation '" + operationName + "' in object '" + name + "'");
        }
        JSONObject op = (JSONObject) a;
        System.err.println("XXX invoke -> found 1 operation: " + a);
        System.err.println("XXX invoke -> found 2 operation: " + opInfo);
        JSONElement descriptor = ((JSONObject) opInfo).get("descriptor");
        System.err.println("XXX invoke -> found 2 desciptor: " + descriptor.toJsonString());
        
 
        // Form the HTTP/REST request, with given params.
        String href = (String) ((JSONPrimitive) op.get("href")).getValue(); // includes base and operation name
        String method = (String) ((JSONPrimitive) op.get("method")).getValue();
        String returnType = (String) ((JSONPrimitive) op.get("returnType")).getValue();
//        System.err.println("XX invoke href " + href + " " + method + " ret: " + returnType);

        // Need a JSON object of the params to send: { "p0": param1value }
        try {
        String postBody = buildJSONForParams(op, params, signature);
        System.out.println("POSTBODY: " + postBody);
        // Call.
        String s = executeHttpPostRequest(url(href, ""), postBody);

        // Parse result.
        JSONParser parser = new JSONParser(s);
        JSONElement json = parser.parse();
        System.err.println("INVOKE gets: " + s);

        // Return type.
        // descriptor has members openType, originalType.
        // e.g. originalType java.lang.management.ThreadInfo, returnType javax.management.openmbean.CompositeData
        String originalType = ((JSONElement) ((JSONObject) descriptor).get("originalType")).getValue();
        String openType     = ((JSONElement) ((JSONObject) descriptor).get("openType")).getValue();
        System.err.println("RETTYPE openType = " + openType + "\noriginalType " + originalType + ", returnType " + returnType ); 
        Object type = parseType(originalType, openType);

        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(originalType);
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

        } catch (Exception e) {
            // JSOMappingException, ParseException
            throw new IOException(e);
        }
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

        throw new UnsupportedOperationException("addNotificationListener not supported");
    }

    public void addNotificationListener(ObjectName name,
                                        ObjectName listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException, IOException {

        throw new UnsupportedOperationException("addNotificationListener not supported");
    }

    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException,
               IOException {

        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public void removeNotificationListener(ObjectName name,
                                           ObjectName listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException,
                   IOException {

        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException,
                   IOException {

        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public void removeNotificationListener(ObjectName name,
                                           NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException,
                   IOException {

        throw new UnsupportedOperationException("removeNotificationListener not supported");
    }

    public MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException,
                   ReflectionException, IOException {

        return null;
    }

    public boolean isInstanceOf(ObjectName name, String className)
            throws InstanceNotFoundException, IOException {

        return true;
    }

    protected static URL url(String baseURL, String s) {
        // Return a URL by adding given String to baseURL.
        URL url = null;
        if (s != null) {
            try {
                url = new URI(baseURL + s).toURL();
            } catch (URISyntaxException | MalformedURLException e) {
                // ignored
            }
        }
        return url;
    }

    protected JSONObject objectInfoForName(ObjectName name) {
        // jmx/servers/platform/mbeans/NAME/info
        try {
            String ref = objectInfoMap.get(name);
            String text = executeHttpGetRequest(url("", ref));
            JSONParser parser = new JSONParser(text);
            JSONElement json = parser.parse();
            return (JSONObject) json;
        } catch (Exception e) {
            return null;
        }
    }


    protected JSONObject objectForName(ObjectName name) {
        // jmx/servers/platform/mbeans/
        try {
            String ref = objectMap.get(name);
            String text = executeHttpGetRequest(url("", ref));
            JSONParser parser = new JSONParser(text);
            JSONElement json = parser.parse();
            return (JSONObject) json;
        } catch (Exception e) {
            return null;
        }
    }

    protected static String executeHttpGetRequest(URL url) throws MalformedURLException, IOException {
            // HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(false);
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
//            try {
                int status = con.getResponseCode();
                if (status == 200) {

                    StringBuilder sbuf;
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream()))) {
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
                            new InputStreamReader(con.getErrorStream()))) {
                        sbuf = new StringBuilder();
                        String input;
                        while ((input = br.readLine()) != null) {
                            sbuf.append(URLDecoder.decode(input, CHARSET));
                        }
                    }
                    return sbuf.toString();
                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        return null;
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
//            try {
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
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        }
        return null;
    }


}
