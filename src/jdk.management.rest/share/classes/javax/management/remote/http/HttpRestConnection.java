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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import javax.management.*;
import javax.management.MBeanServerConnection;
import javax.management.openmbean.CompositeDataSupport;

import javax.net.ssl.HttpsURLConnection;

import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.parser.JSONParser;
import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;

public class HttpRestConnection implements MBeanServerConnection {

    protected String host;
    protected int port;
    protected Map<String,?> env;
    protected String baseURL;

    private static final String CHARSET = "UTF-8";
    
    public HttpRestConnection(String host, int port, Map<String,?> env) {
        this.host = host;
        this.port = port;
        this.env = env;
        this.baseURL = "http://" + host + ":" + port + "/jmx/servers/platform/";
        // Or baseURL could be a required parameter, and not assume "platform"...
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

        // jmx/servers/platform/mbeans/
        // { "mbeans": [ { name, interfaceClassName, className, desription, attributeCout, operationCount, href, info }, ...
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
        String str = executeHttpGetRequest("mbeans/");
        System.err.println("getMBeanCount: " + str);
        JSONParser parser = new JSONParser(str);
        try {
        JSONElement json = parser.parse();
        System.err.println("getMBeanCount: " + json);

        // JSON Mapper - say what we expect to get out:
        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(CompositeDataSupport.class);
        
        Object result = typeMapper.toJavaObject(json);
        System.err.println("getMBeanCount: " + result);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return 1;// result;
    }

    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException,
                   IOException {

        return null;
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

        return null;
    }

    public String getDefaultDomain() throws IOException {
        return null;

    }

    public String[] getDomains() throws IOException {
        return null;

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

    private URL url(String s) {
        // Return a URL by adding given String to baseURL.
        URL url = null;
        if (s != null) {
            try {
                url = new URI(baseURL + s).toURL();
            } catch (URISyntaxException | MalformedURLException e) {
            }
        }
        return url;
    }

    private String executeHttpGetRequest(String inputURL) throws MalformedURLException, IOException {
        URL url = url(inputURL);
        if (url != null) {
            // HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(false);
            String userCredentials = "username1:password1";
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
            try {
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
            } catch (IOException e) {
            }
        }
        return null;
    }

    private String executeHttpPostRequest(String postBody) throws MalformedURLException, IOException {
        URL url = url("");
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
            try {
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


}
