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
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *
 * Neither the name of Oracle nor the names of contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any
 * nuclear facility.
 */

// This function depends on the pre-defined variable
// "plugin" of type com.sun.tools.jconsole.JConsolePlugin

function jcontext() {
    return plugin.getContext();
}
jcontext.docString = "returns JConsoleContext for the current jconsole plugin" 

function mbeanConnection() {
    return jcontext().getMBeanServerConnection();
}
mbeanConnection.docString = "returns current MBeanServer connection"

/**
 * Prints one liner help message for each function exposed here
 * Note that this function depends on docString meta-data for
 * each function
 */
function help() {
    var i;
    for (i in this) {
        var func = this[i];
        if (typeof(func) == "function" &&
           ("docString" in func)) {
            echo(i + " - " + func["docString"]);
        }
    }
}
help.docString = "prints help message for global functions";

function connectionState() {
    return jcontext().connectionState;
}
connectionState.docString = "return connection state of the current jcontext";

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

/**
 * load and evaluate script file. If no script file is
 * specified, file dialog is shown to choose the script.
 *
 * @param file script file name [optional]
 * @return value returned from evaluating script
 */
function load(file) {
    if (file == undefined || file == null) {
        // file not specified, show file dialog to choose
        file = fileDialog();
    }
    if (file == null) return;

    var reader = new java.io.FileReader(file);
    var oldFilename = engine.get(engine.FILENAME);
    engine.put(engine.FILENAME, file);
    try {
        engine.eval(reader);
    } finally {
        engine.put(engine.FILENAME, oldFilename);
    }
    reader.close();
}
load.docString = "loads a script file and evaluates it";

/**
 * Concurrency utilities for JavaScript. These are based on
 * java.lang and java.util.concurrent API. The following functions 
 * provide a simpler API for scripts. Instead of directly using java.lang
 * and java.util.concurrent classes, scripts can use functions and
 * objects exported from here. 
 */

/**
 * Wrapper for java.lang.Object.wait
 *
 * can be called only within a sync method
 */
function wait(object) {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var waitMethod = objClazz.getMethod('wait', null);
    waitMethod.invoke(object, null);
}
wait.docString = "convenient wrapper for java.lang.Object.wait method";


/**
 * Wrapper for java.lang.Object.notify
 *
 * can be called only within a sync method
 */
function notify(object) {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var notifyMethod = objClazz.getMethod('notify', null);
    notifyMethod.invoke(object, null);
}
notify.docString = "convenient wrapper for java.lang.Object.notify method";


/**
 * Wrapper for java.lang.Object.notifyAll
 *
 * can be called only within a sync method
 */
function notifyAll(object)  {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var notifyAllMethod = objClazz.getMethod('notifyAll', null);
    notifyAllMethod.invoke(object, null);
}
notifyAll.docString = "convenient wrapper for java.lang.Object.notifyAll method";


/**
 * Creates a java.lang.Runnable from a given script
 * function.
 */
Function.prototype.runnable = function() {
    var args = arguments;
    var func = this;
    return new java.lang.Runnable() {
        run: function() {
            func.apply(null, args);
        }
    }
}

/**
 * Executes the function on a new Java Thread.
 */
Function.prototype.thread = function() {
    var t = new java.lang.Thread(this.runnable.apply(this, arguments));
    t.start();
    return t;
}

/**
 * Executes the function on a new Java daemon Thread.
 */
Function.prototype.daemon = function() {
    var t = new java.lang.Thread(this.runnable.apply(this, arguments));
    t.setDaemon(true);
    t.start();
    return t;
}

/**
 * Creates a java.util.concurrent.Callable from a given script
 * function.
 */
Function.prototype.callable = function() {
    var args = arguments;
    var func = this;
    return new java.util.concurrent.Callable() {
          call: function() { return func.apply(null, args); }
    }
}

/**
 * Registers the script function so that it will be called exit.
 */
Function.prototype.atexit = function () {
    var args = arguments;
    java.lang.Runtime.getRuntime().addShutdownHook(
         new java.lang.Thread(this.runnable.apply(this, args)));
}

/**
 * Executes the function asynchronously.  
 *
 * @return a java.util.concurrent.FutureTask
 */
Function.prototype.future = (function() {
    // default executor for future
    var juc = java.util.concurrent;
    var theExecutor = juc.Executors.newSingleThreadExecutor();
    // clean-up the default executor at exit
    (function() { theExecutor.shutdown(); }).atexit();
    return function() {
        return theExecutor.submit(this.callable.apply(this, arguments));
    }
})();

// shortcut for j.u.c lock classes
var Lock = java.util.concurrent.locks.ReentrantLock;
var RWLock = java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Executes a function after acquiring given lock. On return,
 * (normal or exceptional), lock is released.
 *
 * @param lock lock that is locked and unlocked
 */
Function.prototype.sync = function (lock) {
    if (arguments.length == 0) {
        throw "lock is missing";
    }
    var res = new Array(arguments.length - 1);
    for (var i = 0; i < res.length; i++) {
        res[i] = arguments[i + 1];
    }
    lock.lock();
    try {
        this.apply(null, res);
    } finally {
        lock.unlock();
    }
}

/**
 * Causes current thread to sleep for specified
 * number of milliseconds
 *
 * @param interval in milliseconds
 */
function sleep(interval) {
    java.lang.Thread.sleep(interval);
}
sleep.docString = "wrapper for java.lang.Thread.sleep method";

/**
 * Schedules a task to be executed once in
 * every N milliseconds specified. 
 *
 * @param callback function or expression to evaluate
 * @param interval in milliseconds to sleep
 * @return timeout ID (which is nothing but Thread instance)
 */
function setTimeout(callback, interval) {
    if (! (callback instanceof Function)) {
        callback = new Function(callback);
    }

    // start a new thread that sleeps given time
    // and calls callback in an infinite loop
    return (function() {
         while (true) {
             sleep(interval);
             callback();
         }
    }).daemon();
}
setTimeout.docString = "calls given callback once after specified interval"

/** 
 * Cancels a timeout set earlier.
 * @param tid timeout ID returned from setTimeout
 */
function clearTimeout(tid) {
    // we just interrupt the timer thread
    tid.interrupt();
}

/**
 * Simple access to thread local storage. 
 *
 * Script sample:
 *
 *  __thread.x = 44;
 *  function f() { 
 *      __thread.x = 'hello'; 
 *      print(__thread.x); 
 *  }
 *  f.thread();       // prints 'hello'
 * print(__thread.x); // prints 44 in main thread
 */
var __thread = (function () {
    var map = new Object();
    return new JSAdapter() {
        __has__: function(name) {
            return map[name] != undefined;
        },
        __get__: function(name) {
            if (map[name] != undefined) {
                return map[name].get();
            } else {
                return undefined;
            }
        },
        __put__: sync(function(name, value) {
            if (map[name] == undefined) {
                var tmp = new java.lang.ThreadLocal();
                tmp.set(value);
                map[name] = tmp;
            } else {
                map[name].set(value);
            }
        }),
        __delete__: function(name) {
            if (map[name] != undefined) {
                map[name].set(null);
            }            
        }
    }
})();

// user interface utilities

/** 
 * Swing invokeLater - invokes given function in AWT event thread
 */
Function.prototype.invokeLater = function() {
    var SwingUtilities = Packages.javax.swing.SwingUtilities;
    SwingUtilities.invokeLater(this.runnable.apply(this, arguments));
}

/** 
 * Swing invokeAndWait - invokes given function in AWT event thread
 * and waits for it's completion
 */
Function.prototype.invokeAndWait = function() {
    var SwingUtilities = Packages.javax.swing.SwingUtilities;
    SwingUtilities.invokeAndWait(this.runnable.apply(this, arguments));
}

/**
 * Am I running in AWT event dispatcher thread?
 */
function isEventThread() {
    var SwingUtilities = Packages.javax.swing.SwingUtilities;
    return SwingUtilities.isEventDispatchThread();
}
isEventThread.docString = "returns whether the current thread is GUI thread";

/**
 * Opens a file dialog box 
 *
 * @param curDir current directory [optional]
 * @return absolute path if file selected or else null
 */
function fileDialog(curDir) {
    var result;
    function _fileDialog() {
        if (curDir == undefined) curDir = undefined;
        var JFileChooser = Packages.javax.swing.JFileChooser;
        var dialog = new JFileChooser(curDir);
        var res = dialog.showOpenDialog(null);
        if (res == JFileChooser.APPROVE_OPTION) {
            result = dialog.getSelectedFile().getAbsolutePath();
        } else {
           result = null;
        }
    }

    if (isEventThread()) {
        _fileDialog();
    } else {
        _fileDialog.invokeAndWait();
    }
    return result;
}
fileDialog.docString = "show a FileOpen dialog box";

/**
 * Shows a message box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 * @param msgType type of message box [constants in JOptionPane]
 */
function msgBox(msg, title, msgType) {
   
    function _msgBox() { 
        var JOptionPane = Packages.javax.swing.JOptionPane;
        if (msg === undefined) msg = "undefined";
        if (msg === null) msg = "null";
        if (title == undefined) title = msg;
        if (msgType == undefined) type = JOptionPane.INFORMATION_MESSAGE;
        JOptionPane.showMessageDialog(window, msg, title, msgType);
    }
    if (isEventThread()) {
        _msgBox();
    } else {
        _msgBox.invokeAndWait();
    }
}
msgBox.docString = "shows MessageBox to the user";
 
/**
 * Shows an information alert box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 */   
function alert(msg, title) {
    var JOptionPane = Packages.javax.swing.JOptionPane;
    msgBox(msg, title, JOptionPane.INFORMATION_MESSAGE);
}
alert.docString = "shows an alert message box to the user";

/**
 * Shows an error alert box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 */
function error(msg, title) {
    var JOptionPane = Packages.javax.swing.JOptionPane;
    msgBox(msg, title, JOptionPane.ERROR_MESSAGE);
}
error.docString = "shows an error message box to the user";


/**
 * Shows a warning alert box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 */
function warn(msg, title) {
    var JOptionPane = Packages.javax.swing.JOptionPane;
    msgBox(msg, title, JOptionPane.WARNING_MESSAGE);
}
warn.docString = "shows a warning message box to the user";


/**
 * Shows a prompt dialog box
 *
 * @param question question to be asked
 * @param answer default answer suggested [optional]
 * @return answer given by user
 */
function prompt(question, answer) {
    var result;
    function _prompt() {
        var JOptionPane = Packages.javax.swing.JOptionPane;
        if (answer == undefined) answer = "";
        result = JOptionPane.showInputDialog(window, question, answer);
    }
    if (isEventThread()) {
        _prompt();
    } else {
        _prompt.invokeAndWait();
    }
    return result;
}
prompt.docString = "shows a prompt box to the user and returns the answer";

/**
 * Shows a confirmation dialog box
 *
 * @param msg message to be shown
 * @param title title of message box [optional]
 * @return boolean (yes->true, no->false)
 */
function confirm(msg, title) {
    var result;
    var JOptionPane = Packages.javax.swing.JOptionPane;
    function _confirm() {
        if (title == undefined) title = msg;
        var optionType = JOptionPane.YES_NO_OPTION;
        result = JOptionPane.showConfirmDialog(null, msg, title, optionType);
    }
    if (isEventThread()) {
        _confirm();
    } else {
        _confirm.invokeAndWait();
    }     
    return result == JOptionPane.YES_OPTION;
}
confirm.docString = "shows a confirmation message box to the user";

/**
 * Echoes zero or more arguments supplied to screen.
 * This is print equivalent for GUI.
 *
 * @param zero or more items to echo.
 */
function echo() {
    var args = arguments;
    (function() {
        var len = args.length;
        for (var i = 0; i < len; i++) {
            window.print(args[i]);
            window.print(" ");
        }
        window.print("\n");
    }).invokeLater();
}
echo.docString = "echoes arguments to interactive console screen";


/**
 * Clear the screen
 */
function clear() {
    (function() { window.clear(false) }).invokeLater();
}
clear.docString = "clears interactive console screen";


// synonym for clear
var cls = clear;


/**
 * Exit the process after confirmation from user 
 * 
 * @param exitCode return code to OS [optional]
 */
function exit(exitCode) {
    if (exitCode == undefined) exitCode = 0;
    if (confirm("Do you really want to exit?")) {
        java.lang.System.exit(exitCode);
    } 
}
exit.docString = "exits jconsole";

// synonym to exit
var quit = exit;

