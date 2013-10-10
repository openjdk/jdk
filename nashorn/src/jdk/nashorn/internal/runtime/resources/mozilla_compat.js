/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * This script contains non-standard, Mozilla compatibility functionality on
 * the standard global objects. Please note that it is incomplete. Only the most
 * often used functionality is supported.
 */

// JavaAdapter
Object.defineProperty(this, "JavaAdapter", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        if (arguments.length < 2) {
            throw new TypeError("JavaAdapter requires atleast two arguments");
        }

        var types = Array.prototype.slice.call(arguments, 0, arguments.length - 1);
        var NewType = Java.extend.apply(Java, types);
        return new NewType(arguments[arguments.length - 1]);
    }
});


// importPackage
// avoid unnecessary chaining of __noSuchProperty__ again
// in case user loads this script more than once.
if (typeof importPackage == 'undefined') {

Object.defineProperty(this, "importPackage", {
    configurable: true, enumerable: false, writable: true,
    value: (function() {
        var _packages = [];
        var global = this;
        var oldNoSuchProperty = global.__noSuchProperty__;
        var __noSuchProperty__ = function(name) {
            'use strict';
            for (var i in _packages) {
                try {
                    var type = Java.type(_packages[i] + "." + name);
                    global[name] = type;
                    return type;
                } catch (e) {}
            }

            if (oldNoSuchProperty) {
                return oldNoSuchProperty.call(this, name);
            } else {
                if (this === undefined) {
                    throw new ReferenceError(name + " is not defined");
                } else {
                    return undefined;
                }
            }
        }

        Object.defineProperty(global, "__noSuchProperty__", {
            writable: true, configurable: true, enumerable: false,
            value: __noSuchProperty__
        });

        var prefix = "[JavaPackage ";
        return function() {
            for (var i in arguments) {
                var pkgName = arguments[i];
                if ((typeof pkgName) != 'string') {
                    pkgName = String(pkgName);
                    // extract name from JavaPackage object
                    if (pkgName.startsWith(prefix)) {
                        pkgName = pkgName.substring(prefix.length, pkgName.length - 1);
                    }
                }
                _packages.push(pkgName);
            }
        }
    })()
});

}

// Object.prototype.__defineGetter__
Object.defineProperty(Object.prototype, "__defineGetter__", {
    configurable: true, enumerable: false, writable: true,
    value: function(name, func) {
        Object.defineProperty(this, name, {
            configurable: true, enumerable: true, get: func });
    }
});

// Object.prototype.__defineSetter__
Object.defineProperty(Object.prototype, "__defineSetter__", {
    configurable: true, enumerable: false, writable: true,
    value: function(name, func) {
        Object.defineProperty(this, name, {
            configurable: true, enumerable: true, set: func });
    }
});

// Object.prototype.__lookupGetter__
Object.defineProperty(Object.prototype, "__lookupGetter__", {
    configurable: true, enumerable: false, writable: true,
    value: function(name) {
        var obj = this;
        while (obj) {
            var desc = Object.getOwnPropertyDescriptor(obj, name);
            if (desc) return desc.get;
            obj = Object.getPrototypeOf(obj);
        }
        return undefined;
    }
});

// Object.prototype.__lookupSetter__
Object.defineProperty(Object.prototype, "__lookupSetter__", {
    configurable: true, enumerable: false, writable: true,
    value: function(name) {
        var obj = this;
        while (obj) {
            var desc = Object.getOwnPropertyDescriptor(obj, name);
            if (desc) return desc.set;
            obj = Object.getPrototypeOf(obj);
        }
        return undefined;
    }
});

// Object.prototype.toSource
Object.defineProperty(Object.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function(state) {
        if (! state) {
            state = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap());
        }
        if (state.contains(this)) {
            return "{}";
        }
        state.add(this);
        var str = new java.lang.StringBuffer('({');
        for (i in this) {
            str.append(i);
            str.append(':');
            if (this[i] instanceof Object && typeof(this[i].toSource) == 'function') {
                str.append(this[i].toSource(state));
            } else {
                str.append(String(this[i]));
            }
            str.append(', ');
        }
        // delete last extra command and space
        str = str.deleteCharAt(str.length() - 1);
        str = str.deleteCharAt(str.length() - 1);
        str.append('})');
        return str.toString();
    }
});

// Boolean.prototype.toSource
Object.defineProperty(Boolean.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '(new Boolean(' + this.toString() + '))';
    }
});

// Date.prototype.toSource
Object.defineProperty(Date.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '(new Date(' + this.valueOf() + '))';
    }
});

// Function.prototype.toSource -- already implemented in nashorn

// Number.prototype.toSource
Object.defineProperty(Number.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '(new Number(' + this.toString() + '))';
    }
});

// RegExp.prototype.toSource
Object.defineProperty(RegExp.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return this.toString();
    }
});

// String.prototype.toSource
Object.defineProperty(String.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '(new String(' + this.quote() + '))';
    }
});

// Error.prototype.toSource
Object.defineProperty(Error.prototype, "toSource", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        var msg = this.message? String(this.message).quote() : "''";
        return '(new ' + this.name + '(' + msg + '))';
    }
});

// Function.prototype.arity
Object.defineProperty(Function.prototype, "arity", {
    configurable: true, enumerable: false,
    get: function() { return this.length; },
    set: function(x) {
        throw new TypeError("Function arity can not be modified");
    }
});

// String.prototype.quote
Object.defineProperty(String.prototype, "quote", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return JSON.stringify(this);
    }
});

// HTML generation methods of String.prototype

// String.prototype.anchor
Object.defineProperty(String.prototype, "anchor", {
    configurable: true, enumerable: false, writable: true,
    value: function(name) {
        return '<a name="' + name + '">' + this + '</a>';
    }
});

// String.prototype.big
Object.defineProperty(String.prototype, "big", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<big>' + this + '</big>';
    }
});

// String.prototype.blink
Object.defineProperty(String.prototype, "blink", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<blink>' + this + '</blink>';
    }
});

// String.prototype.bold
Object.defineProperty(String.prototype, "bold", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<b>' + this + '</b>';
    }
});

// String.prototype.fixed
Object.defineProperty(String.prototype, "fixed", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<tt>' + this + '</tt>';
    }
});

// String.prototype.fontcolor
Object.defineProperty(String.prototype, "fontcolor", {
    configurable: true, enumerable: false, writable: true,
    value: function(clr) {
        return '<font color="' + clr + '">' + this + '</font>';
    }
});

// String.prototype.fontsize
Object.defineProperty(String.prototype, "fontsize", {
    configurable: true, enumerable: false, writable: true,
    value: function(size) {
        return '<font size="' + size + '">' + this + '</font>';
    }
});

// String.prototype.italics
Object.defineProperty(String.prototype, "italics", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<i>' + this + '</i>';
    }
});

// String.prototype.link
Object.defineProperty(String.prototype, "link", {
    configurable: true, enumerable: false, writable: true,
    value: function(url) {
        return '<a href="' + url + '">' + this + '</a>';
    }
});

// String.prototype.small
Object.defineProperty(String.prototype, "small", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<small>' + this + '</small>';
    }
});

// String.prototype.strike
Object.defineProperty(String.prototype, "strike", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<strike>' + this + '</strike>';
    }
});

// String.prototype.sub
Object.defineProperty(String.prototype, "sub", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<sub>' + this + '</sub>';
    }
});

// String.prototype.sup
Object.defineProperty(String.prototype, "sup", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        return '<sup>' + this + '</sup>';
    }
});

// Rhino: global.importClass
Object.defineProperty(this, "importClass", {
    configurable: true, enumerable: false, writable: true,
    value: function() {
        for (var arg in arguments) {
            var clazz = arguments[arg];
            if (Java.isType(clazz)) {
                var className = Java.typeName(clazz);
                var simpleName = className.substring(className.lastIndexOf('.') + 1);
                this[simpleName] = clazz;
            } else {
                throw new TypeError(clazz + " is not a Java class");
            }
        }
    }
});
