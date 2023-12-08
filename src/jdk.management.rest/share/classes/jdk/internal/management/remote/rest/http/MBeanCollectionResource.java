/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.management.remote.rest.http;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.JSONPrimitive;
import jdk.internal.management.remote.rest.json.parser.JSONParser;
import jdk.internal.management.remote.rest.json.parser.ParseException;
import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingException;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;
import com.sun.net.httpserver.HttpExchange;

import javax.management.*;
import jdk.internal.management.remote.rest.PlatformRestAdapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MBeanCollectionResource implements RestResource, NotificationListener {

    private List<ObjectName> allowedMbeans;
    private final MBeanServer mBeanServer;
    private final Map<ObjectName, MBeanResource> mBeanResourceMap = new ConcurrentHashMap<>();
    private static final int pageSize = 10;
    private static final String pathPrefix = "^/?jmx/servers/[a-zA-Z0-9\\-\\.]+/mbeans";

    // Only MXBean or any other MBean that uses types
    // that have a valid mapper functions
    private boolean isMBeanAllowed(ObjectName objName) {
        try {
            MBeanInfo mInfo = mBeanServer.getMBeanInfo(objName);

            // Return true for MXbean
            Descriptor desc = mInfo.getDescriptor();
            String isMxBean = (String) desc.getFieldValue("mxbean");
            if (isMxBean != null && isMxBean.equalsIgnoreCase("true"))
                return true;

            // Check attribute types
            MBeanAttributeInfo[] attrsInfo = mInfo.getAttributes();
            for (MBeanAttributeInfo attrInfo : attrsInfo) {
                String type = attrInfo.getType();
                if (!JSONMappingFactory.INSTANCE.isTypeMapped(type)) {
                    return false;
                }
            }

            // Check operation parameters and return types
            MBeanOperationInfo[] operations = mInfo.getOperations();
            for (MBeanOperationInfo opInfo : operations) {
                MBeanParameterInfo[] signature = opInfo.getSignature();
                for (MBeanParameterInfo sig : signature) {
                    if (!JSONMappingFactory.INSTANCE.isTypeMapped(sig.getType())) {
                        return false;
                    }
                }
                if (!JSONMappingFactory.INSTANCE.isTypeMapped(opInfo.getReturnType())) {
                    return false;
                }
            }
            return true;
        } catch (InstanceNotFoundException | IntrospectionException |
                ReflectionException | ClassNotFoundException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void introspectMBeanTypes(MBeanServer server) {
        if (allowedMbeans.isEmpty()) {
            Set<ObjectInstance> allMBeans = server.queryMBeans(null, null); // get all Mbeans
            allMBeans.stream().filter((objIns) -> (isMBeanAllowed(objIns.getObjectName())))
                    .forEachOrdered(objIns -> allowedMbeans.add(objIns.getObjectName()));
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        try {
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(mbs.getType())) {
                ObjectName mBeanName = mbs.getMBeanName();
                if (isMBeanAllowed(mBeanName)) {
                    allowedMbeans.add(mBeanName);
                }
            } else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(mbs.getType())) {
                if (allowedMbeans.contains(mbs.getMBeanName())) {
                    allowedMbeans.remove(mbs.getMBeanName());
                }
            }
        } catch (Exception e) {
        }
    }

    public MBeanCollectionResource(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
        allowedMbeans = new ArrayList<>();
        introspectMBeanTypes(mBeanServer);
        allowedMbeans = new CopyOnWriteArrayList<>(allowedMbeans);

        // Create a REST handler for each MBean
        allowedMbeans.forEach(objectName -> mBeanResourceMap.put(objectName,
                new MBeanResource(mBeanServer, objectName)));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8.name());

        if (path.matches(pathPrefix + "/?$")) {
            RestResource.super.handle(exchange);
        } else if (path.matches(pathPrefix + "/[^/]+/?.*")) {
            // Extract mbean name
            // Forward the request to its corresponding rest resource
            Pattern mbeans = Pattern.compile(pathPrefix + "/");
            Matcher matcher = mbeans.matcher(path);

            if (matcher.find()) {
                String ss = path.substring(matcher.end());
                String mBeanName = ss;
                if (ss.indexOf('/') != -1) {
                    mBeanName = ss.substring(0, ss.indexOf('/'));
                }
                try {
                    MBeanResource mBeanResource = mBeanResourceMap.get(new ObjectName(mBeanName));
                    if (mBeanResource == null) {
                        HttpUtil.sendResponse(exchange, HttpResponse.REQUEST_NOT_FOUND);
                        return;
                    }
                    mBeanResource.handle(exchange);
                } catch (MalformedObjectNameException e) {
                    HttpUtil.sendResponse(exchange, HttpResponse.BAD_REQUEST);
                }

            }
        }
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        try {
            final String path = PlatformRestAdapter.getDomain()
                    + URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8.displayName())
                    .replaceAll("/$", "");
            List<ObjectName> filteredMBeans = allowedMbeans;
            Map<String, String> queryMap = HttpUtil.getGetRequestQueryMap(exchange);
            String query = exchange.getRequestURI().getQuery();
            if (query != null && queryMap.isEmpty()) {
                return new HttpResponse(HttpResponse.BAD_REQUEST,
                        "Invalid query params : Allowed query keys [objectname,page]");
            } else if (query != null && !queryMap.isEmpty()) {
                Map<String, String> newMap = new HashMap<>(queryMap);
                newMap.remove("objectname");
                newMap.remove("page");
                if (!newMap.isEmpty()) { // Invalid query params
                    return new HttpResponse(HttpResponse.BAD_REQUEST,
                            "Invalid query params : Allowed query keys [objectname,page]");
                }
            }
            if (queryMap.containsKey("objectname")) {        // Filter based on ObjectName query
                Set<ObjectName> queryMBeans = mBeanServer
                        .queryNames(new ObjectName(queryMap.get("objectname")), null);
                queryMBeans.retainAll(allowedMbeans);   // Intersection of two lists
                filteredMBeans = new ArrayList<>(queryMBeans);
            }

            JSONObject _links = HttpUtil.getPaginationLinks(exchange, filteredMBeans, pageSize);
            List<ObjectName> mbeanPage = HttpUtil.filterByPage(exchange, filteredMBeans, pageSize);

            List<Map<String, String>> items = new ArrayList<>(filteredMBeans.size());
            for (ObjectName objectName : mbeanPage) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("name", objectName.getCanonicalName());
                MBeanResource mBeanResource = mBeanResourceMap.get(objectName);
                try {
                    JSONObject mBeanInfo = mBeanResource.getMBeanInfo(mBeanServer, objectName);
                    JSONElement element = mBeanInfo.get("descriptor");
                    if (element != null) {
                        JSONElement element1 = ((JSONObject) element).get("interfaceClassName");
                        if (element1 != null) {
                            String intfName = (String) ((JSONPrimitive) element1).getValue();
                            item.put("interfaceClassName", intfName);
                        }
                    }
                    element = mBeanInfo.get("className");
                    if (element != null) {
                        String className = (String) ((JSONPrimitive) element).getValue();
                        item.put("className", className);
                    }
                    element = mBeanInfo.get("description");
                    if (element != null) {
                        String description = (String) ((JSONPrimitive) element).getValue();
                        item.put("description", description);
                    }
                    element = mBeanInfo.get("attributeInfo");
                    if (element != null) {
                        item.put("attributeCount", ((JSONArray) element).size() + "");
                    }
                    element = mBeanInfo.get("operationInfo");
                    if (element != null) {
                        item.put("operationCount", ((JSONArray) element).size() + "");
                    }

                } catch (InstanceNotFoundException | IntrospectionException | ReflectionException e) {
                }

                String href = path + "/" + objectName.toString();
                href = HttpUtil.escapeUrl(href);
                item.put("href", href);
                String info = HttpUtil.escapeUrl(path + "/" + objectName.toString() + "/info");
                item.put("info", info);
                items.add(item);
            }

            Map<String, String> properties = new HashMap<>();

            properties.put("mbeanCount", Integer.toString(filteredMBeans.size()));

            JSONMapper typeMapper1 = JSONMappingFactory.INSTANCE.getTypeMapper(items);
            JSONMapper typeMapper2 = JSONMappingFactory.INSTANCE.getTypeMapper(properties);

            JSONElement linkElem = typeMapper1.toJsonValue(items);
            JSONElement propElem = typeMapper2.toJsonValue(properties);
            JSONObject jobj = new JSONObject();

            jobj.putAll((JSONObject) propElem);
            jobj.put("mbeans", linkElem);

            if (_links != null && !_links.isEmpty()) {
                jobj.put("_links", _links);
            }
            return new HttpResponse(jobj.toJsonString());
        } catch (JSONMappingException e) {
            return HttpResponse.SERVER_ERROR;
        } catch (UnsupportedEncodingException e) {
            return HttpResponse.BAD_REQUEST;
        } catch (MalformedObjectNameException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Invalid query string");
        }
    }

    @Override
    public HttpResponse doPost(HttpExchange exchange) {
        try {
            String path = URLDecoder.decode(exchange.getRequestURI().getPath(),StandardCharsets.UTF_8.displayName());
            String reqBody = null;
            if (path.matches(pathPrefix + "/?$")) { // POST to current URL
                reqBody = HttpUtil.readRequestBody(exchange);
                if (reqBody == null || reqBody.isEmpty()) { // No Parameters
                    return HttpResponse.BAD_REQUEST;
                }

                JSONParser parser = new JSONParser(reqBody);
                JSONElement jsonElement = parser.parse();
                if (!(jsonElement instanceof JSONObject)) {
                    return new HttpResponse(HttpResponse.BAD_REQUEST,
                            "Invalid parameters : [" + reqBody + "]");
                }

                JSONObject jsonObject = (JSONObject) jsonElement;
                JSONObject normalizedObject = new JSONObject(jsonObject);

                // Normalize the input MBean names
                for (String objectNameString : jsonObject.keySet()) {
                    if (!objectNameString.startsWith("?")) { // Ignore object name patterns
                        JSONElement element = jsonObject.get(objectNameString);
                        normalizedObject.remove(objectNameString);
                        normalizedObject.put(new ObjectName(objectNameString).getCanonicalName(), element);
                    }
                }

                jsonObject.clear();
                jsonObject = normalizedObject;

                Set<String> objectNamePatterns = jsonObject.keySet()
                        .stream()
                        .filter(a -> a.startsWith("?"))
                        .collect(Collectors.toSet());

                if (!objectNamePatterns.isEmpty()) {
                    for (String pattern : objectNamePatterns) {
                        Set<ObjectName> queryMBeans = mBeanServer
                                .queryNames(new ObjectName(pattern.substring(1)), null);
                        queryMBeans.retainAll(allowedMbeans);
                        JSONElement patternNode = jsonObject.get(pattern);
                        jsonObject.remove(pattern);
                        for (ObjectName queryMBean : queryMBeans) {
                            String name = queryMBean.getCanonicalName();
                            if (jsonObject.containsKey(name)) {
                                JSONObject obj = new JSONObject();
                                obj.put(name, patternNode);
                                deepMerge(jsonObject, obj);
                            } else {
                                jsonObject.put(name, patternNode);
                            }
                        }
                    }
                }

                JSONObject result = new JSONObject();
                for (String mBeanName : jsonObject.keySet()) {
                    MBeanResource mBeanResource = mBeanResourceMap.get(new ObjectName(mBeanName));
                    if (mBeanResource != null) {
                        JSONElement element = jsonObject.get(mBeanName);
                        if (element instanceof JSONObject) {
                            JSONElement res = mBeanResource.handleBulkRequest
                                    ((JSONObject) element);
                            result.put(mBeanName, res);
                        } else {
                            result.put(mBeanName, "Invalid input");
                        }
                    } else {
                        result.put(mBeanName, "Invalid MBean");
                    }
                }
                return new HttpResponse(result.toJsonString());
            } else {
                return HttpResponse.METHOD_NOT_ALLOWED;
            }
        } catch (ParseException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Invalid JSON String for request body");
        } catch (IOException e) {
            return HttpResponse.BAD_REQUEST;
        } catch (MalformedObjectNameException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Invalid query string");
        }
    }

    private JSONObject deepMerge(JSONObject jsonObject1, JSONObject jsonObject2) {
        for (String key : jsonObject2.keySet()) {
            if (jsonObject1.containsKey(key)) {
                if (jsonObject2.get(key) instanceof JSONObject && jsonObject1.get(key) instanceof JSONObject) {
                    JSONObject jobj1 = (JSONObject) jsonObject1.get(key);
                    JSONObject jobj2 = (JSONObject) jsonObject2.get(key);
                    jsonObject1.put(key, deepMerge(jobj1, jobj2));
                } else if (jsonObject2.get(key) instanceof JSONArray && jsonObject1.get(key) instanceof JSONArray) {
                    JSONArray array1 = (JSONArray) jsonObject1.get(key);
                    JSONArray array2 = (JSONArray) jsonObject2.get(key);
                    for (JSONElement each : array2) {
                        if (!array1.contains(each)) {
                            array1.add(each);
                        }
                    }
                } else {
                    JSONArray array = new JSONArray();
                    array.add(jsonObject1.get(key));
                    array.add(jsonObject2.get(key));
                    jsonObject1.put(key, array);
                }
            } else {
                jsonObject1.put(key, jsonObject2.get(key));
            }
        }
        return jsonObject1;
    }
}
