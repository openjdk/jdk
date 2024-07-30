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
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;

import javax.management.remote.http.JMXServersInfoCompositeData;

public class HttpRestConnection implements MBeanServerConnection {

    protected String baseURL;
    protected Map<String,?> env;

    protected String defaultDomain;
    protected String[] domains;

    protected HashMap<ObjectName,String> objectMap;

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
                System.err.println("XXX MBEAN: " + b );
                JSONObject bean = (JSONObject) b;
                // Extract info to populate objectMap...
                JSONElement value = bean.get("name");
                String name = (String) ((JSONPrimitive) value).getValue();
                System.err.println("XXX name = " + name);
                value = bean.get("href");
                String href = (String) ((JSONPrimitive) value).getValue();
                System.err.println("XXX href = " + href);
                if (name != null) {
                    objectMap.put(new ObjectName(name), href);
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
//        System.err.println("XXX getAttribute gets " + o);
        // Get attribute.
        JSONObject attributes = (JSONObject) o.get("attributes");
        JSONElement a = attributes.get(attribute);
        System.err.println("XXX -> gets " + a);
        System.err.println("XXX -> gets " + a.toJsonString());
        System.err.println("XXX -> gets " + a.getClass()); // e.g. jdk.internal.management.remote.rest.json.JSONPrimitive
        // String s = (String) ((JSONPrimitive) value).getValue();
//        return ((JSONPrimitive)a).getValue();

        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(a);
        if (typeMapper == null) {
            System.err.println("No JSONMapper for: " + a);
            throw new InstanceNotFoundException("No JSONMapper for: " + a);
        } else {
            try {
                return typeMapper.toJavaObject(a);
            } catch (JSONDataException jde) {
                throw new InstanceNotFoundException("Mapper " + typeMapper + " gives: " + jde);
            }
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

    public Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException, IOException {

        System.err.println("XXX HttpRestConnection.invoke name = " + name + " op = " + operationName
                           + " params: " + params + " signature: " + signature);
        return new Object();
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

    private String executeHttpPostRequest(String postBody) throws MalformedURLException, IOException {
        URL url = url(baseURL, "");
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
