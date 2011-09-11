/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/*
 * This is a collection of utilities for Monitoring
 * and management API. 
 *
 * File dependency:
 *    conc.js -> for concurrency utilities
 */

// At any time, we maintain atmost one MBeanServer
// connection. And so, we store the same as a global
// variable.
var mmConnection = null;

function jmxConnect(hostport) {
    if (mmConnection != null) {
        // close the existing connection
        try {
            mmConnection.close();
        } catch (e) {
        }
    }

    var JMXServiceURL = javax.management.remote.JMXServiceURL;
    var JMXConnectorFactory = javax.management.remote.JMXConnectorFactory;

    var urlPath = "/jndi/rmi://" + hostport + "/jmxrmi";
    var url = new JMXServiceURL("rmi", "", 0, urlPath);
    var jmxc = JMXConnectorFactory.connect(url);
    // note that the "mmConnection" is a global variable!
    mmConnection = jmxc.getMBeanServerConnection();
}
jmxConnect.docString = "connects to the given host, port (specified as name:port)";

function mbeanConnection() {    
    if (mmConnection == null) {        
        throw "Not connected to MBeanServer yet!";
    }

    return mmConnection;
}
mbeanConnection.docString = "returns the current MBeanServer connection"

/**
 * Returns a platform MXBean proxy for given MXBean name and interface class
 */
function newPlatformMXBeanProxy(name, intf) {
    var factory = java.lang.management.ManagementFactory;
    return factory.newPlatformMXBeanProxy(mbeanConnection(), name, intf);
}
newPlatformMXBeanProxy.docString = "returns a proxy for a platform MXBean";

/**
 * Wraps a string to ObjectName if needed.
 */
function objectName(objName) {
    var ObjectName = Packages.javax.management.ObjectName;
    if (objName instanceof ObjectName) {
        return objName;
    } else {
        return new ObjectName(objName);
    }
}
objectName.docString = "creates JMX ObjectName for a given String";


/**
 * Creates a new (M&M) Attribute object
 *
 * @param name name of the attribute
 * @param value value of the attribute
 */
function attribute(name, value) {
    var Attribute = Packages.javax.management.Attribute;
    return new Attribute(name, value);
}
attribute.docString = "returns a new JMX Attribute using name and value given";

/**
 * Returns MBeanInfo for given ObjectName. Strings are accepted.
 */
function mbeanInfo(objName) {
    objName = objectName(objName);
    return mbeanConnection().getMBeanInfo(objName);
}
mbeanInfo.docString = "returns MBeanInfo of a given ObjectName";

/**
 * Returns ObjectInstance for a given ObjectName.
 */
function objectInstance(objName) {
    objName = objectName(objName);
    return mbeanConnection().objectInstance(objectName);
}
objectInstance.docString = "returns ObjectInstance for a given ObjectName";

/**
 * Queries with given ObjectName and QueryExp.
 * QueryExp may be null.
 *
 * @return set of ObjectNames.
 */
function queryNames(objName, query) {
    objName = objectName(objName);
    if (query == undefined) query = null;
    return mbeanConnection().queryNames(objName, query);
}
queryNames.docString = "returns QueryNames using given ObjectName and optional query";


/**
 * Queries with given ObjectName and QueryExp.
 * QueryExp may be null.
 *
 * @return set of ObjectInstances.
 */
function queryMBeans(objName, query) {
    objName = objectName(objName);
    if (query == undefined) query = null;
    return mbeanConnection().queryMBeans(objName, query);
}
queryMBeans.docString = "return MBeans using given ObjectName and optional query";

// wraps a script array as java.lang.Object[]
function objectArray(array) {
    var len = array.length;
    var res = java.lang.reflect.Array.newInstance(java.lang.Object, len);
    for (var i = 0; i < array.length; i++) {
        res[i] = array[i];
    }
    return res;
}

// wraps a script (string) array as java.lang.String[]
function stringArray(array) {
    var len = array.length;
    var res = java.lang.reflect.Array.newInstance(java.lang.String, len);
    for (var i = 0; i < array.length; i++) {
        res[i] = String(array[i]);
    }
    return res;
}

// script array to Java List
function toAttrList(array) {
    var AttributeList = Packages.javax.management.AttributeList;
    if (array instanceof AttributeList) {
        return array;
    }
    var list = new AttributeList(array.length);
    for (var index = 0; index < array.length; index++) {
        list.add(array[index]);
    }
    return list;
}

// Java Collection (Iterable) to script array
function toArray(collection) {
    if (collection instanceof Array) {
        return collection;
    }
    var itr = collection.iterator();
    var array = new Array();
    while (itr.hasNext()) {
        array[array.length] = itr.next();
    }
    return array;
}

// gets MBean attributes
function getMBeanAttributes(objName, attributeNames) {
    objName = objectName(objName);
    return mbeanConnection().getAttributes(objName,stringArray(attributeNames));
}
getMBeanAttributes.docString = "returns specified Attributes of given ObjectName";

// gets MBean attribute
function getMBeanAttribute(objName, attrName) {
    objName = objectName(objName);
    return mbeanConnection().getAttribute(objName, attrName);
}
getMBeanAttribute.docString = "returns a single Attribute of given ObjectName";


// sets MBean attributes
function setMBeanAttributes(objName, attrList) {
    objName = objectName(objName);
    attrList = toAttrList(attrList);
    return mbeanConnection().setAttributes(objName, attrList);
}
setMBeanAttributes.docString = "sets specified Attributes of given ObjectName";

// sets MBean attribute
function setMBeanAttribute(objName, attrName, attrValue) {
    var Attribute = Packages.javax.management.Attribute;
    objName = objectName(objName);
    mbeanConnection().setAttribute(objName, new Attribute(attrName, attrValue));
}
setMBeanAttribute.docString = "sets a single Attribute of given ObjectName";


// invokes an operation on given MBean
function invokeMBean(objName, operation, params, signature) {
    objName = objectName(objName);
    params = objectArray(params);
    signature = stringArray(signature);
    return mbeanConnection().invoke(objName, operation, params, signature);
}
invokeMBean.docString = "invokes MBean operation on given ObjectName";

/**
 * Wraps a MBean specified by ObjectName as a convenient
 * script object -- so that setting/getting MBean attributes
 * and invoking MBean method can be done with natural syntax.
 *
 * @param objName ObjectName of the MBean
 * @param async asynchornous mode [optional, default is false]
 * @return script wrapper for MBean
 *
 * With async mode, all field, operation access is async. Results
 * will be of type FutureTask. When you need value, call 'get' on it.
 */
function mbean(objName, async) {
    objName = objectName(objName);
    var info = mbeanInfo(objName);    
    var attrs = info.attributes;
    var attrMap = new Object;
    for (var index in attrs) {
        attrMap[attrs[index].name] = attrs[index];
    }
    var opers = info.operations;
    var operMap = new Object;
    for (var index in opers) {
        operMap[opers[index].name] = opers[index];
    }

    function isAttribute(name) {
        return name in attrMap;
    }

    function isOperation(name) {
        return name in operMap;
    }

    return new JSAdapter() {
        __has__: function (name) {
            return isAttribute(name) || isOperation(name);
        },
        __get__: function (name) {
            if (isAttribute(name)) {
                if (async) {
                    return getMBeanAttribute.future(objName, name); 
                } else {
                    return getMBeanAttribute(objName, name); 
                }
            } else if (isOperation(name)) {
                var oper = operMap[name];
                return function() {
                    var params = objectArray(arguments);
                    var sigs = oper.signature;
                    var sigNames = new Array(sigs.length);
                    for (var index in sigs) {
                        sigNames[index] = sigs[index].getType();
                    }
                    if (async) {
                        return invokeMBean.future(objName, name, 
                                                  params, sigNames);
                    } else {
                        return invokeMBean(objName, name, params, sigNames);
                    }
                }
            } else {
                return undefined;
            }
        },
        __put__: function (name, value) {
            if (isAttribute(name)) {
                if (async) {
                    setMBeanAttribute.future(objName, name, value);
                } else {
                    setMBeanAttribute(objName, name, value);
                }
            } else {
                return undefined;
            }
        }
    };
}
mbean.docString = "returns a conveninent script wrapper for a MBean of given ObjectName";

if (this.application != undefined) {    
    this.application.addTool("JMX Connect", 
        // connect to a JMX MBean Server 
        function () {
            var url = prompt("Connect to JMX server (host:port)");
            if (url != null) {
                try {
                    jmxConnect(url);
                    alert("connected!");
                } catch (e) {
                    error(e, "Can not connect to " + url);
                }
            }
        });
}
