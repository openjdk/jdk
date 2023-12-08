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
import jdk.internal.management.remote.rest.json.parser.TokenMgrError;
import jdk.internal.management.remote.rest.mapper.JSONDataException;
import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingException;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;
import com.sun.net.httpserver.HttpExchange;

import javax.management.*;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenType;
import jdk.internal.management.remote.rest.PlatformRestAdapter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.management.MBeanOperationInfo.*;

public class MBeanResource implements RestResource {

    private final ObjectName objectName;
    private final MBeanServer mBeanServer;
    private static final String pathPrefix = "^/?jmx/servers/[^/]+/mbeans/[^/]+";

    private final static Map<String, Class<?>> primitiveToObject = new HashMap<>();

    static {
        primitiveToObject.put("int", Integer.TYPE);
        primitiveToObject.put("long", Long.TYPE);
        primitiveToObject.put("double", Double.TYPE);
        primitiveToObject.put("float", Float.TYPE);
        primitiveToObject.put("boolean", Boolean.TYPE);
        primitiveToObject.put("char", Character.TYPE);
        primitiveToObject.put("byte", Byte.TYPE);
        primitiveToObject.put("void", Void.TYPE);
        primitiveToObject.put("short", Short.TYPE);
    }

    MBeanResource(MBeanServer mBeanServer, ObjectName objectName) {
        this.mBeanServer = mBeanServer;
        this.objectName = objectName;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(),StandardCharsets.UTF_8.displayName());
        if (path.matches(pathPrefix + "/?$")) {
            RestResource.super.handle(exchange);
        } else if (path.matches(pathPrefix + "/info$")
                && exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            RestResource.super.handle(exchange);
        } else if (path.matches(pathPrefix + "/[^/]+/?$")
                && exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            RestResource.super.handle(exchange);
        } else {
            HttpUtil.sendResponse(exchange, HttpResponse.REQUEST_NOT_FOUND);
        }
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        String path = PlatformRestAdapter.getDomain() +
                exchange.getRequestURI().getPath().replaceAll("/$", "");

        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.displayName());
            if (path.endsWith("info")) {
                return doMBeanInfo();
            }

            String infoPath = path + "/info";

            Map<String, Object> allAttributes = getAllAttributes();
            Map<String, String> _links = new LinkedHashMap<>();
            //_links.put("info", HttpUtil.escapeUrl(infoPath));

            MBeanOperationInfo[] opInfo = mBeanServer.getMBeanInfo(objectName).getOperations();
            JSONArray jarr = new JSONArray();
            for (MBeanOperationInfo op : opInfo) {
                JSONObject jobj1 = new JSONObject();
                JSONArray jarr1 = new JSONArray();
                jobj1.put("name", op.getName());
                jobj1.put("href", HttpUtil.escapeUrl(path + "/" + op.getName()));
                jobj1.put("method", "POST");
                for (MBeanParameterInfo paramInfo : op.getSignature()) {
                    JSONObject jobj = new JSONObject();
                    jobj.put("name", paramInfo.getName());
                    jobj.put("type", paramInfo.getType());
                    jarr1.add(jobj);
                }
                jobj1.put("arguments", jarr1);
                jobj1.put("returnType", op.getReturnType());
                jarr.add(jobj1);
            }

            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(allAttributes);
            if (typeMapper != null) {
                JSONElement jsonElement1 = typeMapper.toJsonValue(allAttributes);
                JSONElement jsonElement2 = typeMapper.toJsonValue(_links);

                JSONObject jobj = new JSONObject();
                jobj.put("attributes", jsonElement1);
                jobj.put("operations", jarr);
                return new HttpResponse(jobj.toJsonString());
            } else {
                return HttpResponse.SERVER_ERROR;
            }
        } catch (RuntimeOperationsException | IntrospectionException | ReflectionException
                | JSONMappingException | MBeanException e) {
            return HttpResponse.SERVER_ERROR;
        } catch (InstanceNotFoundException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Specified MBean does not exist");
        } catch (Exception e) {
            return HttpResponse.SERVER_ERROR;
        }
    }

    /*
    HTTP POST for this MBean's URL allows setting of attributes and execution of operations.
    POST request body can follow one of the below formats
    1. { name : value}
    Set a single attribute
    2. { name1 : value1, name2 : value2 }
    Sets multiple attributes
    3. {attributes : {read : [name]} , {write : {name : value}}, operations : {op_name : {param_name:name, param_value:value}}}
    This bulk operation request sets multiple attributes and executes multiple
    operations on the MBean.
 */
    @Override
    public HttpResponse doPost(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String reqBody = null;
        try {
            if (path.matches(pathPrefix + "/?$")) { // POST to current URL
                reqBody = HttpUtil.readRequestBody(exchange);
                if (reqBody.isEmpty()) {                // No Parameters
                    return HttpResponse.BAD_REQUEST;
                }

                JSONParser parser = new JSONParser(reqBody);
                JSONElement jsonElement = parser.parse();
                if (!(jsonElement instanceof JSONObject)) {
                    return new HttpResponse(HttpResponse.BAD_REQUEST,
                            "Invalid parameters : [" + reqBody + "]");
                }

                JSONObject jsonObject = (JSONObject) jsonElement;

                // Handle bulk operation
                if (jsonObject.keySet().contains("attributes") | jsonObject.keySet().contains("operations")) {
                    return new HttpResponse(handleBulkRequest(jsonObject).toJsonString());
                } else {    // Handle attribute update
                    Map<String, Object> stringObjectMap = setAttributes(jsonObject);
                    JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(stringObjectMap);
                    if (typeMapper != null) {
                        return new HttpResponse(HttpURLConnection.HTTP_OK, typeMapper.toJsonValue(stringObjectMap).toJsonString());
                    } else {
                        return new HttpResponse(HttpResponse.SERVER_ERROR, "Unable to find JSON Mapper");
                    }
                }
            } else if (path.matches(pathPrefix + "/[^/]+/?$")) {  // POST to MBeanOperation
                Matcher matcher = Pattern.compile(pathPrefix + "/").matcher(path);
                String operation;
                if (matcher.find()) {
                    operation = path.substring(matcher.end());
                } else {
                    return HttpResponse.BAD_REQUEST;
                }

                reqBody = HttpUtil.readRequestBody(exchange);
                JSONElement result;
                if (reqBody.isEmpty()) { // No Parameters
                    result = execOperation(operation, null);
                } else {
                    JSONParser parser = new JSONParser(reqBody);
                    JSONElement jsonElement = parser.parse();
                    if (!(jsonElement instanceof JSONObject)) {
                        return new HttpResponse(HttpResponse.BAD_REQUEST,
                                "Invalid parameters : [" + reqBody + "] for operation - " + operation);
                    }
                    result = execOperation(operation, (JSONObject) jsonElement);
                }
                return new HttpResponse(HttpURLConnection.HTTP_OK, result.toJsonString());
            } else {
                return HttpResponse.REQUEST_NOT_FOUND;
            }
        } catch (InstanceNotFoundException e) {
            // Should never happen
        } catch (JSONDataException | ParseException | TokenMgrError e) {
            return new HttpResponse(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid JSON : " + reqBody, e.getMessage());
        } catch (IntrospectionException | JSONMappingException | MBeanException | ReflectionException | IOException e) {
            return HttpResponse.SERVER_ERROR;
        } catch (IllegalArgumentException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return HttpResponse.SERVER_ERROR;
        }
        return HttpResponse.REQUEST_NOT_FOUND;
    }

    private HttpResponse doMBeanInfo() {
        try {
            JSONObject mBeanInfo = getMBeanInfo(mBeanServer, objectName);
            return new HttpResponse(mBeanInfo.toJsonString());
        } catch (RuntimeOperationsException | IntrospectionException | ReflectionException e) {
            return HttpResponse.SERVER_ERROR;
        } catch (InstanceNotFoundException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Specified MBean does not exist");
        } catch (Exception e) {
            return HttpResponse.SERVER_ERROR;
        }
    }

    JSONElement handleBulkRequest(JSONObject reqObject) {
        JSONObject result = new JSONObject();

        // Handle attributes
        JSONElement element = reqObject.get("attributes");
        if (element != null && element instanceof JSONObject) {
            JSONObject attrInfo = (JSONObject) element;
            JSONObject attrNode = new JSONObject();

            // Read attributes
            JSONElement read = attrInfo.get("get");
            if (read != null) {
                if (read instanceof JSONArray) {
                    JSONArray jsonAttrMap = (JSONArray) read;
                    JSONElement resultJson;
                    Map<String, Object> attrRead;
                    try {
                        String[] attributes = getStrings(jsonAttrMap);
                        attrRead = getAttributes(attributes);
                        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(attrRead);
                        resultJson = typeMapper.toJsonValue(attrRead);
                    } catch (InstanceNotFoundException | ReflectionException | JSONMappingException | MBeanException e) {
                        resultJson = new JSONPrimitive("<ERROR: Unable to retrieve value>");
                    } catch (JSONDataException e) {
                        resultJson = new JSONPrimitive("Invalid JSON : " + e.getMessage());
                    }
                    attrNode.put("get", resultJson);
                } else {
                    attrInfo.put("get", new JSONPrimitive("Invalid JSON : " + read.toJsonString()));
                }
            }

            // Write attributes
            JSONElement write = attrInfo.get("set");

            if (write != null) {
                if (write instanceof JSONObject) {
                    JSONElement resultJson;
                    JSONObject jsonAttrMap = (JSONObject) write;
                    try {
                        Map<String, Object> attrMap = setAttributes(jsonAttrMap);
                        JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(attrMap);
                        resultJson = typeMapper.toJsonValue(attrMap);
                    } catch (JSONDataException ex) {
                        resultJson = new JSONPrimitive("Invalid JSON : " + write.toJsonString());
                    } catch (JSONMappingException | IntrospectionException | InstanceNotFoundException | ReflectionException e) {
                        resultJson = new JSONPrimitive("<ERROR: Unable to retrieve value>");
                    }
                    attrNode.put("set", resultJson);
                } else {
                    attrNode.put("set", new JSONPrimitive("Invalid JSON : " + write.toJsonString()));
                }
            }
            result.put("attributes", attrNode);
        }

        // Execute operations
        element = reqObject.get("operations");
        if (element != null) {
            JSONArray operationList;
            if (element instanceof JSONPrimitive             // Single no-arg operation
                    || element instanceof JSONObject) {     // single/mulitple operations
                operationList = new JSONArray();
                operationList.add(element);
            } else if (element instanceof JSONArray) {  // List of no-arg/with-arg operation
                operationList = (JSONArray) element;
            } else {
                operationList = new JSONArray();
            }
            JSONObject opResult = new JSONObject();
            operationList.forEach((elem) -> {
                if (elem instanceof JSONPrimitive
                        && ((JSONPrimitive) elem).getValue() instanceof String) { // no-arg operation
                    String opName = (String) ((JSONPrimitive) elem).getValue();
                    try {
                        JSONElement obj = execOperation(opName, null);
                        opResult.put(opName, obj);
                    } catch (IllegalArgumentException e) {
                        opResult.put(opName, e.getMessage());
                    } catch (IntrospectionException | InstanceNotFoundException
                            | MBeanException | ReflectionException e) {
                        opResult.put(opName, "<ERROR while executing operation>");
                    }
                } else if (elem instanceof JSONObject) {
                    ((JSONObject) elem).keySet().forEach((opName) -> {
                        try {
                            JSONElement jsonElement = ((JSONObject) elem).get(opName);
                            if (jsonElement instanceof JSONObject) {
                                JSONElement obj = execOperation(opName, (JSONObject) jsonElement);
                                opResult.put(opName, obj);
                            } else {
                                opResult.put(opName, new JSONPrimitive("Invalid parameter JSON"));
                            }
                        } catch (IllegalArgumentException e) {
                            opResult.put(opName, e.getMessage());
                        } catch (IntrospectionException | InstanceNotFoundException
                                | MBeanException | ReflectionException e) {
                            opResult.put(opName, "<ERROR while executing operation>");
                        }
                    });
                }
            });
            result.put("operations", opResult);
        }
        return result;
    }

    JSONObject getMBeanInfo(MBeanServer mbeanServer, ObjectName mbean)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException {

        JSONObject jobj = new JSONObject();
        MBeanInfo mBeanInfo = mbeanServer.getMBeanInfo(mbean);
        if (mBeanInfo == null) {
            return jobj;
        }
        jobj.put("name", mbean.getCanonicalName());
        jobj.put("className", mBeanInfo.getClassName());
        jobj.put("description", mBeanInfo.getDescription());

        // Populate Attribute Info
        MBeanAttributeInfo[] attributes = mBeanInfo.getAttributes();
        JSONArray jarr = new JSONArray();
        for (MBeanAttributeInfo attr : attributes) {
            String access;
            if (attr.isReadable()) {
                if (attr.isWritable()) {
                    access = "read/write";
                } else {
                    access = "read-only";
                }
            } else if (attr.isWritable()) {
                access = "write-only";
            } else {
                access = "no-access";
            }
            JSONObject jobj1 = new JSONObject();
            jobj1.put("name", attr.getName());
            jobj1.put("type", attr.getType());
            jobj1.put("access", access);
            jobj1.put("description", attr.getDescription());
            jobj1.put("descriptor", getDescriptorJSON(attr.getDescriptor()));
            jarr.add(jobj1);
        }
        jobj.put("attributeInfo", jarr);


        // Add constructor Info
        MBeanConstructorInfo[] constructorInfo = mBeanInfo.getConstructors();
        jarr = new JSONArray();
        for (MBeanConstructorInfo constructor : constructorInfo) {
            JSONObject jobj1 = new JSONObject();
            jobj1.put("name", constructor.getName());
            JSONArray jarr1 = new JSONArray();
            for (MBeanParameterInfo paramInfo : constructor.getSignature()) {
                jarr1.add(getParamJSON(paramInfo));
            }
            jobj1.put("signature", jarr1);
            jobj1.put("description", constructor.getDescription());
            if (constructor.getDescriptor().getFieldNames().length > 1) {
                jobj1.put("descriptor", getDescriptorJSON(constructor.getDescriptor()));
            }
            jarr.add(jobj1);
        }
        jobj.put("constructorInfo", jarr);

        MBeanOperationInfo[] opInfo = mBeanInfo.getOperations();
        jarr = new JSONArray();

        for (MBeanOperationInfo op : opInfo) {
            String impactString;
            switch (op.getImpact()) {
                case ACTION:
                    impactString = "action";
                    break;
                case ACTION_INFO:
                    impactString = "action/info";
                    break;
                case INFO:
                    impactString = "info";
                    break;
                case UNKNOWN:
                    impactString = "unknown";
                    break;
                default:
                    impactString = "(" + op.getImpact() + ")";
            }

            JSONObject jobj1 = new JSONObject();

            jobj1.put("name", op.getName());
            JSONArray jarr1 = new JSONArray();
            for (MBeanParameterInfo paramInfo : op.getSignature()) {
                jarr1.add(getParamJSON(paramInfo));
            }
            jobj1.put("signature", jarr1);
            jobj1.put("returnType", op.getReturnType());
            jobj1.put("impact", impactString);
            jobj1.put("description", op.getDescription());
            if (op.getDescriptor().getFieldNames().length > 1) {
                jobj1.put("descriptor", getDescriptorJSON(op.getDescriptor()));
            }
            jarr.add(jobj1);
        }
        jobj.put("operationInfo", jarr);

        MBeanNotificationInfo[] notifications = mBeanInfo.getNotifications();
        jarr = new JSONArray();

        for (MBeanNotificationInfo notification : notifications) {
            JSONObject jobj1 = new JSONObject();
            jobj1.put("name", notification.getName());
            JSONArray jarr1 = new JSONArray();
            for (String notifType : notification.getNotifTypes()) {
                jarr1.add(new JSONPrimitive(notifType));
            }
            jobj1.put("notifTypes", jarr1);
            jobj1.put("description", notification.getDescription());
            if (notification.getDescriptor().getFieldNames().length > 1) {
                jobj1.put("descriptor", getDescriptorJSON(notification.getDescriptor()));
            }
            jarr.add(jobj1);
        }
        jobj.put("notificationInfo", jarr);

        jobj.put("descriptor", getDescriptorJSON(mBeanInfo.getDescriptor()));
        return jobj;
    }

    private JSONObject getParamJSON(MBeanParameterInfo mParamInfo) {
        JSONObject jobj1 = new JSONObject();
        jobj1.put("name", mParamInfo.getName());
        jobj1.put("type", mParamInfo.getType());
        if (mParamInfo.getDescription() != null && !mParamInfo.getDescription().isEmpty()) {
            jobj1.put("description", mParamInfo.getDescription());
        }
        if (mParamInfo.getDescriptor() != null && mParamInfo.getDescriptor().getFieldNames().length > 1) {
            jobj1.put("descriptor", getDescriptorJSON(mParamInfo.getDescriptor()));
        }
        return jobj1;
    }

    private JSONObject getDescriptorJSON(Descriptor descriptor) {
        JSONObject jobj2 = new JSONObject();
        String[] descNames = descriptor.getFieldNames();
        for (String descName : descNames) {
            Object fieldValue = descriptor.getFieldValue(descName);
            jobj2.put(descName, fieldValue != null ? fieldValue.toString() : null);
        }
        return jobj2;
    }

    private Map<String, Object> getAttributes(String[] attrs) throws
            InstanceNotFoundException, ReflectionException, MBeanException {

        Map<String, Object> result = new LinkedHashMap<>();

        if (attrs == null || attrs.length == 0) {
            return result;
        }

        AttributeList attrVals = mBeanServer.getAttributes(objectName, attrs);
        List<String> missingAttrs = new ArrayList<>(Arrays.asList(attrs));
        attrVals.asList().forEach(a -> {
            missingAttrs.remove(a.getName());
            result.put(a.getName(), a.getValue());
        });

        for (String attr : missingAttrs) {
            try {
                mBeanServer.getAttribute(objectName, attr);
                result.put(attr, "< Error: No such attribute >");
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof UnsupportedOperationException) {
                    result.put(attr, "< Attribute not supported >");
                } else if (ex.getCause() instanceof IllegalArgumentException) {
                    result.put(attr, "< Invalid attributes >");
                }
            } catch (AttributeNotFoundException e) {
                result.put(attr, "< Attribute not found >");
            }
        }
        return result;
    }

    private Map<String, Object> getAllAttributes() throws InstanceNotFoundException,
            ReflectionException, MBeanException, IntrospectionException {

        MBeanInfo mInfo = mBeanServer.getMBeanInfo(objectName);
        String[] attrs = Stream.of(mInfo.getAttributes())
                .map(MBeanAttributeInfo::getName)
                .toArray(String[]::new);

        return getAttributes(attrs);
    }

    private Map<String, Object> setAttributes(JSONObject attrMap) throws JSONDataException,
            IntrospectionException, InstanceNotFoundException, ReflectionException {

        if (attrMap == null || attrMap.isEmpty()) {
            throw new JSONDataException("Null arguments for set attribute");
        }

        MBeanInfo mBeanInfo = mBeanServer.getMBeanInfo(objectName);
        Map<String, Object> result = new HashMap<>();

        for (String attrName : attrMap.keySet()) {
            MBeanAttributeInfo attrInfo = Arrays.stream(mBeanInfo.getAttributes()).
                    filter(a -> a.getName().equals(attrName)).findFirst().orElse(null);
            if (attrInfo == null) {
                result.put(attrName, "<Attribute not found>");
            } else if (!attrInfo.isWritable()) {
                result.put(attrName, "<Attribute is read-only>");
            } else {
                JSONMapper mapper;
                if (attrInfo instanceof OpenMBeanAttributeInfo) {
                    OpenType<?> type = ((OpenMBeanAttributeInfo) attrInfo).getOpenType();
                    mapper = JSONMappingFactory.INSTANCE.getTypeMapper(type);
                } else {
                    Class<?> inputCls = primitiveToObject.get(attrInfo.getType());
                    try {
                        if (inputCls == null) {
                            inputCls = Class.forName(attrInfo.getType());
                        }
                    } catch (ClassNotFoundException | ClassCastException ex) {
                        throw new IllegalArgumentException("Invalid parameters : "
                                + attrMap.get(attrName).toJsonString() + " cannot be mapped to : " + attrInfo.getType());
                    }
                    mapper = JSONMappingFactory.INSTANCE.getTypeMapper(inputCls);
                }
                try {
                    Object attrValue = mapper.toJavaObject(attrMap.get(attrName));
                    Attribute attrObj = new Attribute(attrName, attrValue);
                    mBeanServer.setAttribute(objectName, attrObj);
                    result.put(attrName, "success");
                } catch (InvalidAttributeValueException | JSONDataException e) {
                    result.put(attrName, "<Invalid value for the attribute>");
                } catch (AttributeNotFoundException e) {
                    result.put(attrName, "<Attribute not found>");
                } catch (ReflectionException | InstanceNotFoundException | MBeanException e) {
                    result.put(attrName, "<ERROR: Unable to retrieve value>");
                }
            }
        }
        return result;
    }

    private Object mapJsonElemToType(JSONElement jsonElement, MBeanParameterInfo type) {
        if (type instanceof OpenMBeanParameterInfo) {
            OpenType<?> openType = ((OpenMBeanParameterInfo) type).getOpenType();
            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(openType);
            try {
                return typeMapper.toJavaObject(jsonElement);
            } catch (JSONDataException ex) {
                throw new IllegalArgumentException("Invalid JSON String : " + jsonElement.toJsonString() + " for arguments");
            }
        } else {
            Class<?> inputCls = primitiveToObject.get(type.getType());
            try {
                if (inputCls == null) {
                    inputCls = Class.forName(type.getType());
                }
            } catch (ClassNotFoundException | ClassCastException ex) {
                throw new IllegalArgumentException("Invalid parameters : " + jsonElement.toJsonString() + " cannot be mapped to : " + type.getType());
            }
            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(inputCls);
            if (typeMapper == null) {
                throw new IllegalArgumentException("Invalid parameters : " + jsonElement.toJsonString() + " cannot be mapped to : " + type.getType());
            }
            try {
                return typeMapper.toJavaObject(jsonElement);
            } catch (JSONDataException ex) {
                throw new IllegalArgumentException("Invalid JSON String : " + jsonElement.toJsonString() + " for arguments");
            }
        }
    }

    private Map<String, Object> getOperationParameters(Map<String, JSONElement> jsonValues, Map<String, MBeanParameterInfo> typeMap) {
        if (jsonValues.size() != typeMap.size()) {
            throw new IllegalArgumentException("Invalid parameters : expected - " + typeMap.size() + " parameters, got - " + jsonValues.size());
        }
        if (!jsonValues.keySet().equals(typeMap.keySet())) {
            throw new IllegalArgumentException("Invalid parameters - expected : " + Arrays.toString(typeMap.keySet().toArray()));
        }
        Map<String, Object> parameters = new LinkedHashMap<>(); // Order of parameters should be same as typeMap
        if (typeMap.isEmpty() && jsonValues.isEmpty()) {
            return parameters;
        }
        typeMap.keySet().forEach((name) -> {
            MBeanParameterInfo type = typeMap.get(name);
            JSONElement jsonVal = jsonValues.get(name);
            Object obj = mapJsonElemToType(jsonVal, type);
            parameters.put(name, obj);
        });
        return parameters;
    }

    private JSONElement execOperation(String opstr, JSONObject params)
            throws MBeanException, IntrospectionException, ReflectionException, InstanceNotFoundException {
        if (params == null) {
            params = new JSONObject();
        }
        MBeanInfo mBeanInfo;
        try {
            mBeanInfo = mBeanServer.getMBeanInfo(objectName);
        } catch (InstanceNotFoundException ex) {
            throw new IllegalArgumentException("MBean does not exist");
        }

        List<MBeanOperationInfo> mBeanOperationInfos = Arrays.stream(mBeanInfo.getOperations()).
                filter(a -> a.getName().equals(opstr)).collect(Collectors.toList());

        if (mBeanOperationInfos.isEmpty()) {
            throw new IllegalArgumentException("Invalid Operation String");
        }

        String[] signature = null;
        Object[] parameters = null;

        IllegalArgumentException exception = null;
        for (MBeanOperationInfo mBeanOperationInfo : mBeanOperationInfos) {
            MBeanParameterInfo[] sig = mBeanOperationInfo.getSignature();
            try {
                Map<String, MBeanParameterInfo> typeMap = new LinkedHashMap<>();    // Order of parameters is important
                Arrays.stream(sig).forEach(e -> typeMap.put(e.getName(), e));
                parameters = getOperationParameters(params, typeMap).values().toArray();
                signature = Stream.of(sig).map(MBeanParameterInfo::getType).toArray(String[]::new);
                exception = null;
                break;
            } catch (IllegalArgumentException ex) {
                exception = ex;
            }
        }
        if (exception != null) {
            throw exception;
        }

        Object invoke;
        try {
            invoke = mBeanServer.invoke(objectName, opstr, parameters, signature);
            if (invoke != null) {
                JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(invoke);
                if (typeMapper != null) {
                    return typeMapper.toJsonValue(invoke);
                } else {
                    return new JSONPrimitive("<Unable to map result to JSON>");
                }
            } else {
                return new JSONPrimitive("void");
            }
        } catch (JSONMappingException e) {
            return new JSONPrimitive("<Unable to map result to JSON>");
        }
    }

    private String[] getStrings(JSONArray jsonArray) throws JSONDataException {
        List<String> attributes = new ArrayList<>();
        for (JSONElement element : jsonArray) {
            if (element instanceof JSONPrimitive && ((JSONPrimitive) element).getValue() instanceof String) {
                JSONPrimitive val = (JSONPrimitive) element;
                attributes.add((String) val.getValue());
            } else throw new JSONDataException("Expecting String, got " + element.toJsonString());
        }
        return attributes.toArray(new String[0]);
    }
}
