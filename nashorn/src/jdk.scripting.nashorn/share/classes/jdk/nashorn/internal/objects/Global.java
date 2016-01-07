/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.isString;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Setter;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.FindProperty;
import jdk.nashorn.internal.runtime.GlobalConstants;
import jdk.nashorn.internal.runtime.GlobalFunctions;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.NativeJavaPackage;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.Scope;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.ScriptingFunctions;
import jdk.nashorn.internal.runtime.Specialization;
import jdk.nashorn.internal.runtime.Symbol;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.regexp.RegExpResult;
import jdk.nashorn.internal.scripts.JD;
import jdk.nashorn.internal.scripts.JO;
import jdk.nashorn.tools.ShellFunctions;

/**
 * Representation of global scope.
 */
@ScriptClass("Global")
public final class Global extends Scope {
    // This special value is used to flag a lazily initialized global property.
    // This also serves as placeholder value used in place of a location property
    // (__FILE__, __DIR__, __LINE__)
    private static final Object LAZY_SENTINEL = new Object();

    private InvokeByName TO_STRING;
    private InvokeByName VALUE_OF;

    /**
     * Optimistic builtin names that require switchpoint invalidation
     * upon assignment. Overly conservative, but works for now, to avoid
     * any complicated scope checks and especially heavy weight guards
     * like
     *
     * <pre>
     *     public boolean setterGuard(final Object receiver) {
     *         final Global          global = Global.instance();
     *         final ScriptObject    sobj   = global.getFunctionPrototype();
     *         final Object          apply  = sobj.get("apply");
     *         return apply == receiver;
     *     }
     * </pre>
     *
     * Naturally, checking for builtin classes like NativeFunction is cheaper,
     * it's when you start adding property checks for said builtins you have
     * problems with guard speed.
     */

    /** Nashorn extension: arguments array */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public Object arguments;

    /** ECMA 15.1.2.2 parseInt (string , radix) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object parseInt;

    /** ECMA 15.1.2.3 parseFloat (string) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object parseFloat;

    /** ECMA 15.1.2.4 isNaN (number) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object isNaN;

    /** ECMA 15.1.2.5 isFinite (number) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object isFinite;

    /** ECMA 15.1.3.3 encodeURI */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object encodeURI;

    /** ECMA 15.1.3.4 encodeURIComponent */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object encodeURIComponent;

    /** ECMA 15.1.3.1 decodeURI */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object decodeURI;

    /** ECMA 15.1.3.2 decodeURIComponent */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object decodeURIComponent;

    /** ECMA B.2.1 escape (string) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object escape;

    /** ECMA B.2.2 unescape (string) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object unescape;

    /** Nashorn extension: global.print */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object print;

    /** Nashorn extension: global.load */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object load;

    /** Nashorn extension: global.loadWithNewGlobal */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object loadWithNewGlobal;

    /** Nashorn extension: global.exit */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object exit;

    /** Nashorn extension: global.quit */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object quit;

    /** Value property NaN of the Global Object - ECMA 15.1.1.1 NaN */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static final double NaN = Double.NaN;

    /** Value property Infinity of the Global Object - ECMA 15.1.1.2 Infinity */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static final double Infinity = Double.POSITIVE_INFINITY;

    /** Value property Undefined of the Global Object - ECMA 15.1.1.3 Undefined */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static final Object undefined = UNDEFINED;

    /** ECMA 15.1.2.1 eval(x) */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object eval;

    /** ECMA 15.1.4.1 Object constructor. */
    @Property(name = "Object", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object object;

    /** ECMA 15.1.4.2 Function constructor. */
    @Property(name = "Function", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object function;

    /** ECMA 15.1.4.3 Array constructor. */
    @Property(name = "Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object array;

    /** ECMA 15.1.4.4 String constructor */
    @Property(name = "String", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object string;

    /** ECMA 15.1.4.5 Boolean constructor */
    @Property(name = "Boolean", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object _boolean;

    /** ECMA 15.1.4.6 - Number constructor */
    @Property(name = "Number", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object number;

    /** ECMA 2016 19.4.1 - Symbol constructor */
    @Property(name = "Symbol", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object symbol;

    /**
     * Getter for ECMA 15.1.4.7 Date property
     *
     * @param self self reference
     * @return Date property value
     */
    @Getter(name = "Date", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getDate(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.date == LAZY_SENTINEL) {
            global.date = global.getBuiltinDate();
        }
        return global.date;
    }

    /**
     * Setter for ECMA 15.1.4.7 Date property
     *
     * @param self self reference
     * @param value value for the Date property
     */
    @Setter(name = "Date", attributes = Attribute.NOT_ENUMERABLE)
    public static void setDate(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.date = value;
    }

    private volatile Object date = LAZY_SENTINEL;

    /**
     * Getter for ECMA 15.1.4.8 RegExp property
     *
     * @param self self reference
     * @return RegExp property value
     */
    @Getter(name = "RegExp", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getRegExp(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.regexp == LAZY_SENTINEL) {
            global.regexp = global.getBuiltinRegExp();
        }
        return global.regexp;
    }

    /**
     * Setter for ECMA 15.1.4.8 RegExp property
     *
     * @param self self reference
     * @param value value for the RegExp property
     */
    @Setter(name = "RegExp", attributes = Attribute.NOT_ENUMERABLE)
    public static void setRegExp(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.regexp = value;
    }

    private volatile Object regexp = LAZY_SENTINEL;

    /**
     * Getter for ECMA 15.12 - The JSON property
     * @param self self reference
     * @return the value of JSON property
     */
    @Getter(name = "JSON", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getJSON(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.json == LAZY_SENTINEL) {
            global.json = global.getBuiltinJSON();
        }
        return global.json;
    }

    /**
     * Setter for ECMA 15.12 - The JSON property
     * @param self self reference
     * @param value value for the JSON property
     */
    @Setter(name = "JSON", attributes = Attribute.NOT_ENUMERABLE)
    public static void setJSON(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.json = value;
    }

    private volatile Object json = LAZY_SENTINEL;

    /**
     * Getter for Nashorn extension: global.JSAdapter
     * @param self self reference
     * @return value of the JSAdapter property
     */
    @Getter(name = "JSAdapter", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getJSAdapter(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.jsadapter == LAZY_SENTINEL) {
            global.jsadapter = global.getBuiltinJSAdapter();
        }
        return global.jsadapter;
    }

    /**
     * Setter for Nashorn extension: global.JSAdapter
     * @param self self reference
     * @param value value for the JSAdapter property
     */
    @Setter(name = "JSAdapter", attributes = Attribute.NOT_ENUMERABLE)
    public static void setJSAdapter(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.jsadapter = value;
    }

    private volatile Object jsadapter = LAZY_SENTINEL;

    /** ECMA 15.8 - The Math object */
    @Property(name = "Math", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object math;

    /** Error object */
    @Property(name = "Error", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object error;

    /**
     * Getter for the EvalError property
     * @param self self reference
     * @return the value of EvalError property
     */
    @Getter(name = "EvalError", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getEvalError(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.evalError == LAZY_SENTINEL) {
            global.evalError = global.getBuiltinEvalError();
        }
        return global.evalError;
    }

    /**
     * Setter for the EvalError property
     * @param self self reference
     * @param value value of the EvalError property
     */
    @Setter(name = "EvalError", attributes = Attribute.NOT_ENUMERABLE)
    public static void setEvalError(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.evalError = value;
    }

    private volatile Object evalError = LAZY_SENTINEL;

    /**
     * Getter for the RangeError property.
     * @param self self reference
     * @return the value of RangeError property
     */
    @Getter(name = "RangeError", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getRangeError(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.rangeError == LAZY_SENTINEL) {
            global.rangeError = global.getBuiltinRangeError();
        }
        return global.rangeError;
    }


    /**
     * Setter for the RangeError property.
     * @param self self reference
     * @param value value for the RangeError property
     */
    @Setter(name = "RangeError", attributes = Attribute.NOT_ENUMERABLE)
    public static void setRangeError(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.rangeError = value;
    }

    private volatile Object rangeError = LAZY_SENTINEL;

    /** ReferenceError object */
    @Property(name = "ReferenceError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object referenceError;

    /** SyntaxError object */
    @Property(name = "SyntaxError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object syntaxError;

    /** TypeError object */
    @Property(name = "TypeError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object typeError;

    /**
     * Getter for the URIError property.
     * @param self self reference
     * @return the value of URIError property
     */
    @Getter(name = "URIError", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getURIError(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.uriError == LAZY_SENTINEL) {
            global.uriError = global.getBuiltinURIError();
        }
        return global.uriError;
    }

    /**
     * Setter for the URIError property.
     * @param self self reference
     * @param value value for the URIError property
     */
    @Setter(name = "URIError", attributes = Attribute.NOT_ENUMERABLE)
    public static void setURIError(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.uriError = value;
    }

    private volatile Object uriError = LAZY_SENTINEL;

    /**
     * Getter for the ArrayBuffer property.
     * @param self self reference
     * @return the value of the ArrayBuffer property
     */
    @Getter(name = "ArrayBuffer", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getArrayBuffer(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.arrayBuffer == LAZY_SENTINEL) {
            global.arrayBuffer = global.getBuiltinArrayBuffer();
        }
        return global.arrayBuffer;
    }

    /**
     * Setter for the ArrayBuffer property.
     * @param self self reference
     * @param value value of the ArrayBuffer property
     */
    @Setter(name = "ArrayBuffer", attributes = Attribute.NOT_ENUMERABLE)
    public static void setArrayBuffer(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.arrayBuffer = value;
    }

    private volatile Object arrayBuffer;

    /**
     * Getter for the DataView property.
     * @param self self reference
     * @return the value of the DataView property
     */
    @Getter(name = "DataView", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getDataView(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.dataView == LAZY_SENTINEL) {
            global.dataView = global.getBuiltinDataView();
        }
        return global.dataView;
    }


    /**
     * Setter for the DataView property.
     * @param self self reference
     * @param value value of the DataView property
     */
    @Setter(name = "DataView", attributes = Attribute.NOT_ENUMERABLE)
    public static void setDataView(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.dataView = value;
    }

    private volatile Object dataView;

    /**
     * Getter for the Int8Array property.
     * @param self self reference
     * @return the value of the Int8Array property.
     */
    @Getter(name = "Int8Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getInt8Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.int8Array == LAZY_SENTINEL) {
            global.int8Array = global.getBuiltinInt8Array();
        }
        return global.int8Array;
    }

    /**
     * Setter for the Int8Array property.
     * @param self self reference
     * @param value value of the Int8Array property
     */
    @Setter(name = "Int8Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setInt8Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.int8Array = value;
    }

    private volatile Object int8Array;

    /**
     * Getter for the Uin8Array property.
     * @param self self reference
     * @return the value of the Uint8Array property
     */
    @Getter(name = "Uint8Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUint8Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.uint8Array == LAZY_SENTINEL) {
            global.uint8Array = global.getBuiltinUint8Array();
        }
        return global.uint8Array;
    }

    /**
     * Setter for the Uin8Array property.
     * @param self self reference
     * @param value value of the Uin8Array property
     */
    @Setter(name = "Uint8Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setUint8Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.uint8Array = value;
    }

    private volatile Object uint8Array;

    /**
     * Getter for the Uint8ClampedArray property.
     * @param self self reference
     * @return the value of the Uint8ClampedArray property
     */
    @Getter(name = "Uint8ClampedArray", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUint8ClampedArray(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.uint8ClampedArray == LAZY_SENTINEL) {
            global.uint8ClampedArray = global.getBuiltinUint8ClampedArray();
        }
        return global.uint8ClampedArray;
    }

    /**
     * Setter for the Uint8ClampedArray property.
     * @param self self reference
     * @param value value of the Uint8ClampedArray property
     */
    @Setter(name = "Uint8ClampedArray", attributes = Attribute.NOT_ENUMERABLE)
    public static void setUint8ClampedArray(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.uint8ClampedArray = value;
    }

    private volatile Object uint8ClampedArray;

    /**
     * Getter for the Int16Array property.
     * @param self self reference
     * @return the value of the Int16Array property
     */
    @Getter(name = "Int16Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getInt16Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.int16Array == LAZY_SENTINEL) {
            global.int16Array = global.getBuiltinInt16Array();
        }
        return global.int16Array;
    }

    /**
     * Setter for the Int16Array property.
     * @param self self reference
     * @param value value of the Int16Array property
     */
    @Setter(name = "Int16Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setInt16Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.int16Array = value;
    }

    private volatile Object int16Array;

    /**
     * Getter for the Uint16Array property.
     * @param self self reference
     * @return the value of the Uint16Array property
     */
    @Getter(name = "Uint16Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUint16Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.uint16Array == LAZY_SENTINEL) {
            global.uint16Array = global.getBuiltinUint16Array();
        }
        return global.uint16Array;
    }

    /**
     * Setter for the Uint16Array property.
     * @param self self reference
     * @param value value of the Uint16Array property
     */
    @Setter(name = "Uint16Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setUint16Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.uint16Array = value;
    }

    private volatile Object uint16Array;

    /**
     * Getter for the Int32Array property.
     *
     * @param self self reference
     * @return the value of the Int32Array property
     */
    @Getter(name = "Int32Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getInt32Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.int32Array == LAZY_SENTINEL) {
            global.int32Array = global.getBuiltinInt32Array();
        }
        return global.int32Array;
    }


    /**
     * Setter for the Int32Array property.
     *
     * @param self self reference
     * @param value value of the Int32Array property
     */
    @Setter(name = "Int32Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setInt32Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.int32Array = value;
    }

    private volatile Object int32Array;

    /**
     * Getter of the Uint32Array property.
     *
     * @param self self reference
     * @return the value of the Uint32Array property
     */
    @Getter(name = "Uint32Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUint32Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.uint32Array == LAZY_SENTINEL) {
            global.uint32Array = global.getBuiltinUint32Array();
        }
        return global.uint32Array;
    }


    /**
     * Setter of the Uint32Array property.
     *
     * @param self self reference
     * @param value value of the Uint32Array property
     */
    @Setter(name = "Uint32Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setUint32Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.uint32Array = value;
    }

    private volatile Object uint32Array;

    /**
     * Getter for the Float32Array property.
     *
     * @param self self reference
     * @return the value of the Float32Array property
     */
    @Getter(name = "Float32Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getFloat32Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.float32Array == LAZY_SENTINEL) {
            global.float32Array = global.getBuiltinFloat32Array();
        }
        return global.float32Array;
    }

    /**
     * Setter for the Float32Array property.
     *
     * @param self self reference
     * @param value value of the Float32Array property
     */
    @Setter(name = "Float32Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setFloat32Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.float32Array = value;
    }

    private volatile Object float32Array;

    /**
     * Getter for the Float64Array property.
     *
     * @param self self reference
     * @return the value of the Float64Array property
     */
    @Getter(name = "Float64Array", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getFloat64Array(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.float64Array == LAZY_SENTINEL) {
            global.float64Array = global.getBuiltinFloat64Array();
        }
        return global.float64Array;
    }

    /**
     * Setter for the Float64Array property.
     *
     * @param self self reference
     * @param value value of the Float64Array property
     */
    @Setter(name = "Float64Array", attributes = Attribute.NOT_ENUMERABLE)
    public static void setFloat64Array(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.float64Array = value;
    }

    private volatile Object float64Array;

    /** Nashorn extension: Java access - global.Packages */
    @Property(name = "Packages", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object packages;

    /** Nashorn extension: Java access - global.com */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object com;

    /** Nashorn extension: Java access - global.edu */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object edu;

    /** Nashorn extension: Java access - global.java */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object java;

    /** Nashorn extension: Java access - global.javafx */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object javafx;

    /** Nashorn extension: Java access - global.javax */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object javax;

    /** Nashorn extension: Java access - global.org */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object org;

    /**
     * Getter for the Nashorn extension: Java access - global.javaImporter.
     *
     * @param self self reference
     * @return the value of the JavaImporter property
     */
    @Getter(name = "JavaImporter", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getJavaImporter(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.javaImporter == LAZY_SENTINEL) {
            global.javaImporter = global.getBuiltinJavaImporter();
        }
        return global.javaImporter;
    }

    /**
     * Setter for the Nashorn extension: Java access - global.javaImporter.
     *
     * @param self self reference
     * @param value value of the JavaImporter property
     */
    @Setter(name = "JavaImporter", attributes = Attribute.NOT_ENUMERABLE)
    public static void setJavaImporter(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.javaImporter = value;
    }

    private volatile Object javaImporter;

    /**
     * Getter for the Nashorn extension: global.Java property.
     *
     * @param self self reference
     * @return the value of the Java property
     */
    @Getter(name = "Java", attributes = Attribute.NOT_ENUMERABLE)
    public static Object getJavaApi(final Object self) {
        final Global global = Global.instanceFrom(self);
        if (global.javaApi == LAZY_SENTINEL) {
            global.javaApi = global.getBuiltinJavaApi();
        }
        return global.javaApi;
    }

    /**
     * Setter for the Nashorn extension: global.Java property.
     *
     * @param self self reference
     * @param value value of the Java property
     */
    @Setter(name = "Java", attributes = Attribute.NOT_ENUMERABLE)
    public static void setJavaApi(final Object self, final Object value) {
        final Global global = Global.instanceFrom(self);
        global.javaApi = value;
    }

    private volatile Object javaApi;

    /** Nashorn extension: current script's file name */
    @Property(name = "__FILE__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static final Object __FILE__ = LAZY_SENTINEL;

    /** Nashorn extension: current script's directory */
    @Property(name = "__DIR__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static final Object __DIR__ = LAZY_SENTINEL;

    /** Nashorn extension: current source line number being executed */
    @Property(name = "__LINE__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static final Object __LINE__ = LAZY_SENTINEL;

    private volatile NativeDate DEFAULT_DATE;

    /** Used as Date.prototype's default value */
    NativeDate getDefaultDate() {
        return DEFAULT_DATE;
    }

    private volatile NativeRegExp DEFAULT_REGEXP;

    /** Used as RegExp.prototype's default value */
    NativeRegExp getDefaultRegExp() {
        return DEFAULT_REGEXP;
    }

    /*
     * Built-in constructor objects: Even if user changes dynamic values of
     * "Object", "Array" etc., we still want to keep original values of these
     * constructors here. For example, we need to be able to create array,
     * regexp literals even after user overwrites global "Array" or "RegExp"
     * constructor - see also ECMA 262 spec. Annex D.
     */
    private ScriptFunction builtinFunction;
    private ScriptFunction builtinObject;
    private ScriptFunction builtinArray;
    private ScriptFunction builtinBoolean;
    private ScriptFunction builtinDate;
    private ScriptObject   builtinJSON;
    private ScriptFunction builtinJSAdapter;
    private ScriptObject   builtinMath;
    private ScriptFunction builtinNumber;
    private ScriptFunction builtinRegExp;
    private ScriptFunction builtinString;
    private ScriptFunction builtinError;
    private ScriptFunction builtinEval;
    private ScriptFunction builtinEvalError;
    private ScriptFunction builtinRangeError;
    private ScriptFunction builtinReferenceError;
    private ScriptFunction builtinSyntaxError;
    private ScriptFunction builtinTypeError;
    private ScriptFunction builtinURIError;
    private ScriptObject   builtinPackages;
    private ScriptObject   builtinCom;
    private ScriptObject   builtinEdu;
    private ScriptObject   builtinJava;
    private ScriptObject   builtinJavafx;
    private ScriptObject   builtinJavax;
    private ScriptObject   builtinOrg;
    private ScriptFunction builtinJavaImporter;
    private ScriptObject   builtinJavaApi;
    private ScriptFunction builtinArrayBuffer;
    private ScriptFunction builtinDataView;
    private ScriptFunction builtinInt8Array;
    private ScriptFunction builtinUint8Array;
    private ScriptFunction builtinUint8ClampedArray;
    private ScriptFunction builtinInt16Array;
    private ScriptFunction builtinUint16Array;
    private ScriptFunction builtinInt32Array;
    private ScriptFunction builtinUint32Array;
    private ScriptFunction builtinFloat32Array;
    private ScriptFunction builtinFloat64Array;
    private ScriptFunction builtinSymbol;

    /*
     * ECMA section 13.2.3 The [[ThrowTypeError]] Function Object
     */
    private ScriptFunction typeErrorThrower;

    // Used to store the last RegExp result to support deprecated RegExp constructor properties
    private RegExpResult lastRegExpResult;

    private static final MethodHandle EVAL                 = findOwnMH_S("eval",                Object.class, Object.class, Object.class);
    private static final MethodHandle NO_SUCH_PROPERTY     = findOwnMH_S(NO_SUCH_PROPERTY_NAME, Object.class, Object.class, Object.class);
    private static final MethodHandle PRINT                = findOwnMH_S("print",               Object.class, Object.class, Object[].class);
    private static final MethodHandle PRINTLN              = findOwnMH_S("println",             Object.class, Object.class, Object[].class);
    private static final MethodHandle LOAD                 = findOwnMH_S("load",                Object.class, Object.class, Object.class);
    private static final MethodHandle LOAD_WITH_NEW_GLOBAL = findOwnMH_S("loadWithNewGlobal",   Object.class, Object.class, Object[].class);
    private static final MethodHandle EXIT                 = findOwnMH_S("exit",                Object.class, Object.class, Object.class);
    private static final MethodHandle LEXICAL_SCOPE_FILTER = findOwnMH_S("lexicalScopeFilter",  Object.class, Object.class);

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    // context to which this global belongs to
    private final Context context;

    // current ScriptContext to use - can be null.
    private ThreadLocal<ScriptContext> scontext;
    // current ScriptEngine associated - can be null.
    private ScriptEngine engine;

    // ES6 global lexical scope.
    private final LexicalScope lexicalScope;

    // Switchpoint for non-constant global callsites in the presence of ES6 lexical scope.
    private SwitchPoint lexicalScopeSwitchPoint;

    /**
     * Set the current script context
     * @param ctxt script context
     */
    public void setScriptContext(final ScriptContext ctxt) {
        assert scontext != null;
        scontext.set(ctxt);
    }

    /**
     * Get the current script context
     * @return current script context
     */
    public ScriptContext getScriptContext() {
        assert scontext != null;
        return scontext.get();
    }

    private ScriptContext currentContext() {
        final ScriptContext sc = scontext != null? scontext.get() : null;
        return (sc != null)? sc : (engine != null? engine.getContext() : null);
    }

    @Override
    protected Context getContext() {
        return context;
    }

    @Override
    protected boolean useDualFields() {
        return context.useDualFields();
    }

    // performs initialization checks for Global constructor and returns the
    // PropertyMap, if everything is fine.
    private static PropertyMap checkAndGetMap(final Context context) {
        // security check first
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(Context.NASHORN_CREATE_GLOBAL));
        }

        Objects.requireNonNull(context);

        return $nasgenmap$;
    }

    /**
     * Constructor
     *
     * @param context the context
     */
    public Global(final Context context) {
        super(checkAndGetMap(context));
        this.context = context;
        this.lexicalScope = context.getEnv()._es6 ? new LexicalScope(this) : null;
    }

    /**
     * Script access to "current" Global instance
     *
     * @return the global singleton
     */
    public static Global instance() {
        return Objects.requireNonNull(Context.getGlobal());
    }

    private static Global instanceFrom(final Object self) {
        return self instanceof Global? (Global)self : instance();
    }

    /**
     * Check if we have a Global instance
     * @return true if one exists
     */
    public static boolean hasInstance() {
        return Context.getGlobal() != null;
    }

    /**
     * Script access to {@link ScriptEnvironment}
     *
     * @return the script environment
     */
    static ScriptEnvironment getEnv() {
        return instance().getContext().getEnv();
    }

    /**
     * Script access to {@link Context}
     *
     * @return the context
     */
    static Context getThisContext() {
        return instance().getContext();
    }

    // Runtime interface to Global

    /**
     * Is there a class filter in the current Context?
     * @return class filter
     */
    public ClassFilter getClassFilter() {
        return context.getClassFilter();
    }

    /**
     * Is this global of the given Context?
     * @param ctxt the context
     * @return true if this global belongs to the given Context
     */
    public boolean isOfContext(final Context ctxt) {
        return this.context == ctxt;
    }

    /**
     * Does this global belong to a strict Context?
     * @return true if this global belongs to a strict Context
     */
    public boolean isStrictContext() {
        return context.getEnv()._strict;
    }

    /**
     * Initialize standard builtin objects like "Object", "Array", "Function" etc.
     * as well as our extension builtin objects like "Java", "JSAdapter" as properties
     * of the global scope object.
     *
     * @param eng ScriptEngine to initialize
     */
    public void initBuiltinObjects(final ScriptEngine eng) {
        if (this.builtinObject != null) {
            // already initialized, just return
            return;
        }

        TO_STRING = new InvokeByName("toString", ScriptObject.class);
        VALUE_OF  = new InvokeByName("valueOf",  ScriptObject.class);

        this.engine = eng;
        if (this.engine != null) {
            this.scontext = new ThreadLocal<>();
        }
        init(eng);
    }

    /**
     * Wrap a Java object as corresponding script object
     *
     * @param obj object to wrap
     * @return    wrapped object
     */
    public Object wrapAsObject(final Object obj) {
        if (obj instanceof Boolean) {
            return new NativeBoolean((Boolean)obj, this);
        } else if (obj instanceof Number) {
            return new NativeNumber(((Number)obj).doubleValue(), this);
        } else if (isString(obj)) {
            return new NativeString((CharSequence)obj, this);
        } else if (obj instanceof Object[]) { // extension
            return new NativeArray(ArrayData.allocate((Object[])obj), this);
        } else if (obj instanceof double[]) { // extension
            return new NativeArray(ArrayData.allocate((double[])obj), this);
        } else if (obj instanceof int[]) {
            return new NativeArray(ArrayData.allocate((int[]) obj), this);
        } else if (obj instanceof ArrayData) {
            return new NativeArray((ArrayData) obj, this);
        } else if (obj instanceof Symbol) {
            return new NativeSymbol((Symbol) obj, this);
        } else {
            // FIXME: more special cases? Map? List?
            return obj;
        }
    }

    /**
     * Lookup helper for JS primitive types
     *
     * @param request the link request for the dynamic call site.
     * @param self     self reference
     *
     * @return guarded invocation
     */
    public static GuardedInvocation primitiveLookup(final LinkRequest request, final Object self) {
        if (isString(self)) {
            return NativeString.lookupPrimitive(request, self);
        } else if (self instanceof Number) {
            return NativeNumber.lookupPrimitive(request, self);
        } else if (self instanceof Boolean) {
            return NativeBoolean.lookupPrimitive(request, self);
        }
        throw new IllegalArgumentException("Unsupported primitive: " + self);
    }

    /**
     * Returns a method handle that creates a wrapper object for a JS primitive value.
     *
     * @param self receiver object
     * @return method handle to create wrapper objects for primitive receiver
     */
    public static MethodHandle getPrimitiveWrapFilter(final Object self) {
        if (isString(self)) {
            return NativeString.WRAPFILTER;
        } else if (self instanceof Number) {
            return NativeNumber.WRAPFILTER;
        } else if (self instanceof Boolean) {
            return NativeBoolean.WRAPFILTER;
        }
        throw new IllegalArgumentException("Unsupported primitive: " + self);
    }


    /**
     * Create a new empty script object
     *
     * @return the new ScriptObject
     */
    public ScriptObject newObject() {
        return useDualFields() ? new JD(getObjectPrototype()) : new JO(getObjectPrototype());
    }

    /**
     * Default value of given type
     *
     * @param sobj     script object
     * @param typeHint type hint
     *
     * @return default value
     */
    public Object getDefaultValue(final ScriptObject sobj, final Class<?> typeHint) {
        // When the [[DefaultValue]] internal method of O is called with no hint,
        // then it behaves as if the hint were Number, unless O is a Date object
        // in which case it behaves as if the hint were String.
        Class<?> hint = typeHint;
        if (hint == null) {
            hint = Number.class;
        }

        try {
            if (hint == String.class) {

                final Object toString = TO_STRING.getGetter().invokeExact(sobj);

                if (Bootstrap.isCallable(toString)) {
                    final Object value = TO_STRING.getInvoker().invokeExact(toString, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }

                final Object valueOf = VALUE_OF.getGetter().invokeExact(sobj);
                if (Bootstrap.isCallable(valueOf)) {
                    final Object value = VALUE_OF.getInvoker().invokeExact(valueOf, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }
                throw typeError(this, "cannot.get.default.string");
            }

            if (hint == Number.class) {
                final Object valueOf = VALUE_OF.getGetter().invokeExact(sobj);
                if (Bootstrap.isCallable(valueOf)) {
                    final Object value = VALUE_OF.getInvoker().invokeExact(valueOf, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }

                final Object toString = TO_STRING.getGetter().invokeExact(sobj);
                if (Bootstrap.isCallable(toString)) {
                    final Object value = TO_STRING.getInvoker().invokeExact(toString, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }

                throw typeError(this, "cannot.get.default.number");
            }
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }

        return UNDEFINED;
    }

    /**
     * Is the given ScriptObject an ECMAScript Error object?
     *
     * @param sobj the object being checked
     * @return true if sobj is an Error object
     */
    public boolean isError(final ScriptObject sobj) {
        final ScriptObject errorProto = getErrorPrototype();
        ScriptObject proto = sobj.getProto();
        while (proto != null) {
            if (proto == errorProto) {
                return true;
            }
            proto = proto.getProto();
        }
        return false;
    }

    /**
     * Create a new ECMAScript Error object.
     *
     * @param msg error message
     * @return newly created Error object
     */
    public ScriptObject newError(final String msg) {
        return new NativeError(msg, this);
    }

    /**
     * Create a new ECMAScript EvalError object.
     *
     * @param msg error message
     * @return newly created EvalError object
     */
    public ScriptObject newEvalError(final String msg) {
        return new NativeEvalError(msg, this);
    }

    /**
     * Create a new ECMAScript RangeError object.
     *
     * @param msg error message
     * @return newly created RangeError object
     */
    public ScriptObject newRangeError(final String msg) {
        return new NativeRangeError(msg, this);
    }

    /**
     * Create a new ECMAScript ReferenceError object.
     *
     * @param msg error message
     * @return newly created ReferenceError object
     */
    public ScriptObject newReferenceError(final String msg) {
        return new NativeReferenceError(msg, this);
    }

    /**
     * Create a new ECMAScript SyntaxError object.
     *
     * @param msg error message
     * @return newly created SyntaxError object
     */
    public ScriptObject newSyntaxError(final String msg) {
        return new NativeSyntaxError(msg, this);
    }

    /**
     * Create a new ECMAScript TypeError object.
     *
     * @param msg error message
     * @return newly created TypeError object
     */
    public ScriptObject newTypeError(final String msg) {
        return new NativeTypeError(msg, this);
    }

    /**
     * Create a new ECMAScript URIError object.
     *
     * @param msg error message
     * @return newly created URIError object
     */
    public ScriptObject newURIError(final String msg) {
        return new NativeURIError(msg, this);
    }

    /**
     * Create a new ECMAScript GenericDescriptor object.
     *
     * @param configurable is the property configurable?
     * @param enumerable is the property enumerable?
     * @return newly created GenericDescriptor object
     */
    public PropertyDescriptor newGenericDescriptor(final boolean configurable, final boolean enumerable) {
        return new GenericPropertyDescriptor(configurable, enumerable, this);
    }

    /**
     * Create a new ECMAScript DatePropertyDescriptor object.
     *
     * @param value of the data property
     * @param configurable is the property configurable?
     * @param enumerable is the property enumerable?
     * @param writable is the property writable?
     * @return newly created DataPropertyDescriptor object
     */
    public PropertyDescriptor newDataDescriptor(final Object value, final boolean configurable, final boolean enumerable, final boolean writable) {
        return new DataPropertyDescriptor(configurable, enumerable, writable, value, this);
    }

    /**
     * Create a new ECMAScript AccessorPropertyDescriptor object.
     *
     * @param get getter function of the user accessor property
     * @param set setter function of the user accessor property
     * @param configurable is the property configurable?
     * @param enumerable is the property enumerable?
     * @return newly created AccessorPropertyDescriptor object
     */
    public PropertyDescriptor newAccessorDescriptor(final Object get, final Object set, final boolean configurable, final boolean enumerable) {
        final AccessorPropertyDescriptor desc = new AccessorPropertyDescriptor(configurable, enumerable, get == null ? UNDEFINED : get, set == null ? UNDEFINED : set, this);

        if (get == null) {
            desc.delete(PropertyDescriptor.GET, false);
        }

        if (set == null) {
            desc.delete(PropertyDescriptor.SET, false);
        }

        return desc;
    }

    private <T> T getLazilyCreatedValue(final Object key, final Callable<T> creator, final Map<Object, T> map) {
        final T obj = map.get(key);
        if (obj != null) {
            return obj;
        }

        final Global oldGlobal = Context.getGlobal();
        final boolean differentGlobal = oldGlobal != this;
        try {
            if (differentGlobal) {
                Context.setGlobal(this);
            }
            final T newObj = creator.call();
            final T existingObj = map.putIfAbsent(key, newObj);
            return existingObj != null ? existingObj : newObj;
        } catch (final Exception exp) {
            throw new RuntimeException(exp);
        } finally {
            if (differentGlobal) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    private final Map<Object, InvokeByName> namedInvokers = new ConcurrentHashMap<>();


    /**
     * Get cached InvokeByName object for the given key
     * @param key key to be associated with InvokeByName object
     * @param creator if InvokeByName is absent 'creator' is called to make one (lazy init)
     * @return InvokeByName object associated with the key.
     */
    public InvokeByName getInvokeByName(final Object key, final Callable<InvokeByName> creator) {
        return getLazilyCreatedValue(key, creator, namedInvokers);
    }

    private final Map<Object, MethodHandle> dynamicInvokers = new ConcurrentHashMap<>();

    /**
     * Get cached dynamic method handle for the given key
     * @param key key to be associated with dynamic method handle
     * @param creator if method handle is absent 'creator' is called to make one (lazy init)
     * @return dynamic method handle associated with the key.
     */
    public MethodHandle getDynamicInvoker(final Object key, final Callable<MethodHandle> creator) {
        return getLazilyCreatedValue(key, creator, dynamicInvokers);
    }

    /**
     * Hook to search missing variables in ScriptContext if available
     * @param self used to detect if scope call or not (this function is 'strict')
     * @param name name of the variable missing
     * @return value of the missing variable or undefined (or TypeError for scope search)
     */
    public static Object __noSuchProperty__(final Object self, final Object name) {
        final Global global = Global.instance();
        final ScriptContext sctxt = global.currentContext();
        final String nameStr = name.toString();

        if (sctxt != null) {
            final int scope = sctxt.getAttributesScope(nameStr);
            if (scope != -1) {
                return ScriptObjectMirror.unwrap(sctxt.getAttribute(nameStr, scope), global);
            }
        }

        switch (nameStr) {
        case "context":
            return sctxt;
        case "engine":
            return global.engine;
        default:
            break;
        }

        if (self == UNDEFINED) {
            // scope access and so throw ReferenceError
            throw referenceError(global, "not.defined", nameStr);
        }

        return UNDEFINED;
    }

    /**
     * This is the eval used when 'indirect' eval call is made.
     *
     * var global = this;
     * global.eval("print('hello')");
     *
     * @param self  eval scope
     * @param str   eval string
     *
     * @return the result of eval
     */
    public static Object eval(final Object self, final Object str) {
        return directEval(self, str, Global.instanceFrom(self), UNDEFINED, false);
    }

    /**
     * Direct eval
     *
     * @param self     The scope of eval passed as 'self'
     * @param str      Evaluated code
     * @param callThis "this" to be passed to the evaluated code
     * @param location location of the eval call
     * @param strict   is eval called from a strict mode code?
     *
     * @return the return value of the eval
     *
     * This is directly invoked from generated when eval(code) is called in user code
     */
    public static Object directEval(final Object self, final Object str, final Object callThis, final Object location, final boolean strict) {
        if (!isString(str)) {
            return str;
        }
        final Global global = Global.instanceFrom(self);
        final ScriptObject scope = self instanceof ScriptObject && ((ScriptObject)self).isScope() ? (ScriptObject)self : global;

        return global.getContext().eval(scope, str.toString(), callThis, location, strict, true);
    }

    /**
     * Global print implementation - Nashorn extension
     *
     * @param self    scope
     * @param objects arguments to print
     *
     * @return result of print (undefined)
     */
    public static Object print(final Object self, final Object... objects) {
        return Global.instanceFrom(self).printImpl(false, objects);
    }

    /**
     * Global println implementation - Nashorn extension
     *
     * @param self    scope
     * @param objects arguments to print
     *
     * @return result of println (undefined)
     */
    public static Object println(final Object self, final Object... objects) {
        return Global.instanceFrom(self).printImpl(true, objects);
    }

    /**
     * Global load implementation - Nashorn extension.
     *
     * <p>
     * load builtin loads the given script. Script source can be a URL or a File
     * or a script object with name and script properties. Evaluated code gets
     * global object "this" and uses global object as scope for evaluation.
     * </p>
     * <p>
     * If self is undefined or null or global, then global object is used
     * as scope as well as "this" for the evaluated code. If self is any other
     * object, then it is indirect load call. With indirect load call, the
     * properties of scope are available to evaluated script as variables. Also,
     * global scope properties are accessible. Any var, function definition in
     * evaluated script goes into an object that is not accessible to user scripts.
     * </p>
     * Thus the indirect load call is equivalent to the following:
     * <pre>
     * <code>
     * (function (scope, source) {
     *    with(scope) {
     *        eval(&lt;script_from_source&gt;);
     *    }
     * })(self, source);
     * </code>
     * </pre>
     *
     * @param self    scope to use for the script evaluation
     * @param source  script source
     *
     * @return result of load (may be undefined)
     *
     * @throws IOException if source could not be read
     */
    public static Object load(final Object self, final Object source) throws IOException {
        final Global global = Global.instanceFrom(self);
        return global.getContext().load(self, source);
    }

    /**
     * Global loadWithNewGlobal implementation - Nashorn extension.
     *
     * loadWithNewGlobal builtin loads the given script from a URL or a File
     * or a script object with name and script properties. Evaluated code gets
     * new global object "this" and uses that new global object as scope for evaluation.
     *
     * @param self self This value is ignored by this function
     * @param args optional arguments to be passed to the loaded script
     *
     * @return result of load (may be undefined)
     *
     * @throws IOException if source could not be read
     */
    public static Object loadWithNewGlobal(final Object self, final Object...args) throws IOException {
        final Global global = Global.instanceFrom(self);
        final int length = args.length;
        final boolean hasArgs = 0 < length;
        final Object from = hasArgs ? args[0] : UNDEFINED;
        final Object[] arguments = hasArgs ? Arrays.copyOfRange(args, 1, length) : args;

        return global.getContext().loadWithNewGlobal(from, arguments);
    }

    /**
     * Global exit and quit implementation - Nashorn extension: perform a {@code System.exit} call from the script
     *
     * @param self  self reference
     * @param code  exit code
     *
     * @return undefined (will never be reached)
     */
    public static Object exit(final Object self, final Object code) {
        System.exit(JSType.toInt32(code));
        return UNDEFINED;
    }

    // builtin prototype accessors

    /**
     * Get the builtin Object prototype.
     * @return the Object prototype.
     */
    public ScriptObject getObjectPrototype() {
        return ScriptFunction.getPrototype(builtinObject);
    }

    /**
     * Get the builtin Function prototype.
     * @return the Function prototype.
     */
    public ScriptObject getFunctionPrototype() {
        return ScriptFunction.getPrototype(builtinFunction);
    }

    /**
     * Get the builtin Array prototype.
     * @return the Array prototype
     */
    public ScriptObject getArrayPrototype() {
        return ScriptFunction.getPrototype(builtinArray);
    }

    ScriptObject getBooleanPrototype() {
        return ScriptFunction.getPrototype(builtinBoolean);
    }

    ScriptObject getNumberPrototype() {
        return ScriptFunction.getPrototype(builtinNumber);
    }

    ScriptObject getDatePrototype() {
        return ScriptFunction.getPrototype(getBuiltinDate());
    }

    ScriptObject getRegExpPrototype() {
        return ScriptFunction.getPrototype(getBuiltinRegExp());
    }

    ScriptObject getStringPrototype() {
        return ScriptFunction.getPrototype(builtinString);
    }

    ScriptObject getErrorPrototype() {
        return ScriptFunction.getPrototype(builtinError);
    }

    ScriptObject getEvalErrorPrototype() {
        return ScriptFunction.getPrototype(getBuiltinEvalError());
    }

    ScriptObject getRangeErrorPrototype() {
        return ScriptFunction.getPrototype(getBuiltinRangeError());
    }

    ScriptObject getReferenceErrorPrototype() {
        return ScriptFunction.getPrototype(builtinReferenceError);
    }

    ScriptObject getSyntaxErrorPrototype() {
        return ScriptFunction.getPrototype(builtinSyntaxError);
    }

    ScriptObject getTypeErrorPrototype() {
        return ScriptFunction.getPrototype(builtinTypeError);
    }

    ScriptObject getURIErrorPrototype() {
        return ScriptFunction.getPrototype(getBuiltinURIError());
    }

    ScriptObject getJavaImporterPrototype() {
        return ScriptFunction.getPrototype(getBuiltinJavaImporter());
    }

    ScriptObject getJSAdapterPrototype() {
        return ScriptFunction.getPrototype(getBuiltinJSAdapter());
    }

    ScriptObject getSymbolPrototype() {
        return ScriptFunction.getPrototype(builtinSymbol);
    }

    private synchronized ScriptFunction getBuiltinArrayBuffer() {
        if (this.builtinArrayBuffer == null) {
            this.builtinArrayBuffer = initConstructorAndSwitchPoint("ArrayBuffer", ScriptFunction.class);
        }
        return this.builtinArrayBuffer;
    }

    ScriptObject getArrayBufferPrototype() {
        return ScriptFunction.getPrototype(getBuiltinArrayBuffer());
    }

    private synchronized ScriptFunction getBuiltinDataView() {
        if (this.builtinDataView == null) {
            this.builtinDataView = initConstructorAndSwitchPoint("DataView", ScriptFunction.class);
        }
        return this.builtinDataView;
    }

    ScriptObject getDataViewPrototype() {
        return ScriptFunction.getPrototype(getBuiltinDataView());
    }

    private synchronized ScriptFunction getBuiltinInt8Array() {
        if (this.builtinInt8Array == null) {
            this.builtinInt8Array = initConstructorAndSwitchPoint("Int8Array", ScriptFunction.class);
        }
        return this.builtinInt8Array;
    }

    ScriptObject getInt8ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinInt8Array());
    }

    private synchronized ScriptFunction getBuiltinUint8Array() {
        if (this.builtinUint8Array == null) {
            this.builtinUint8Array = initConstructorAndSwitchPoint("Uint8Array", ScriptFunction.class);
        }
        return this.builtinUint8Array;
    }

    ScriptObject getUint8ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinUint8Array());
    }

    private synchronized ScriptFunction getBuiltinUint8ClampedArray() {
        if (this.builtinUint8ClampedArray == null) {
            this.builtinUint8ClampedArray = initConstructorAndSwitchPoint("Uint8ClampedArray", ScriptFunction.class);
        }
        return this.builtinUint8ClampedArray;
    }

    ScriptObject getUint8ClampedArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinUint8ClampedArray());
    }

    private synchronized ScriptFunction getBuiltinInt16Array() {
        if (this.builtinInt16Array == null) {
            this.builtinInt16Array = initConstructorAndSwitchPoint("Int16Array", ScriptFunction.class);
        }
        return this.builtinInt16Array;
    }

    ScriptObject getInt16ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinInt16Array());
    }

    private synchronized ScriptFunction getBuiltinUint16Array() {
        if (this.builtinUint16Array == null) {
            this.builtinUint16Array = initConstructorAndSwitchPoint("Uint16Array", ScriptFunction.class);
        }
        return this.builtinUint16Array;
    }

    ScriptObject getUint16ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinUint16Array());
    }

    private synchronized ScriptFunction getBuiltinInt32Array() {
        if (this.builtinInt32Array == null) {
            this.builtinInt32Array = initConstructorAndSwitchPoint("Int32Array", ScriptFunction.class);
        }
        return this.builtinInt32Array;
    }

    ScriptObject getInt32ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinInt32Array());
    }

    private synchronized ScriptFunction getBuiltinUint32Array() {
        if (this.builtinUint32Array == null) {
            this.builtinUint32Array = initConstructorAndSwitchPoint("Uint32Array", ScriptFunction.class);
        }
        return this.builtinUint32Array;
    }

    ScriptObject getUint32ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinUint32Array());
    }

    private synchronized ScriptFunction getBuiltinFloat32Array() {
        if (this.builtinFloat32Array == null) {
            this.builtinFloat32Array = initConstructorAndSwitchPoint("Float32Array", ScriptFunction.class);
        }
        return this.builtinFloat32Array;
    }

    ScriptObject getFloat32ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinFloat32Array());
    }

    private synchronized ScriptFunction getBuiltinFloat64Array() {
        if (this.builtinFloat64Array == null) {
            this.builtinFloat64Array = initConstructorAndSwitchPoint("Float64Array", ScriptFunction.class);
        }
        return this.builtinFloat64Array;
    }

    ScriptObject getFloat64ArrayPrototype() {
        return ScriptFunction.getPrototype(getBuiltinFloat64Array());
    }

    /**
     * Return the function that throws TypeError unconditionally. Used as "poison" methods for certain Function properties.
     *
     * @return the TypeError throwing function
     */
    public ScriptFunction getTypeErrorThrower() {
        return typeErrorThrower;
    }

    private synchronized ScriptFunction getBuiltinDate() {
        if (this.builtinDate == null) {
            this.builtinDate = initConstructorAndSwitchPoint("Date", ScriptFunction.class);
            final ScriptObject dateProto = ScriptFunction.getPrototype(builtinDate);
            // initialize default date
            this.DEFAULT_DATE = new NativeDate(NaN, dateProto);
        }
        return this.builtinDate;
    }

    private synchronized ScriptFunction getBuiltinEvalError() {
        if (this.builtinEvalError == null) {
            this.builtinEvalError = initErrorSubtype("EvalError", getErrorPrototype());
        }
        return this.builtinEvalError;
    }

    private ScriptFunction getBuiltinFunction() {
        return builtinFunction;
    }

    /**
     * Get the switchpoint used to check property changes for Function.prototype.apply
     * @return the switchpoint guarding apply (same as guarding call, and everything else in function)
     */
    public static SwitchPoint getBuiltinFunctionApplySwitchPoint() {
        return ScriptFunction.getPrototype(Global.instance().getBuiltinFunction()).getProperty("apply").getBuiltinSwitchPoint();
    }

    private static boolean isBuiltinFunctionProperty(final String name) {
        final Global instance = Global.instance();
        final ScriptFunction builtinFunction = instance.getBuiltinFunction();
        if (builtinFunction == null) {
            return false; //conservative for compile-only mode
        }
        final boolean isBuiltinFunction = instance.function == builtinFunction;
        return isBuiltinFunction && ScriptFunction.getPrototype(builtinFunction).getProperty(name).isBuiltin();
    }

    /**
     * Check if the Function.prototype.apply has not been replaced
     * @return true if Function.prototype.apply has been replaced
     */
    public static boolean isBuiltinFunctionPrototypeApply() {
        return isBuiltinFunctionProperty("apply");
    }

    /**
     * Check if the Function.prototype.apply has not been replaced
     * @return true if Function.prototype.call has been replaced
     */
    public static boolean isBuiltinFunctionPrototypeCall() {
        return isBuiltinFunctionProperty("call");
    }

    private synchronized ScriptFunction getBuiltinJSAdapter() {
        if (this.builtinJSAdapter == null) {
            this.builtinJSAdapter = initConstructorAndSwitchPoint("JSAdapter", ScriptFunction.class);
        }
        return builtinJSAdapter;
    }

    private synchronized ScriptObject getBuiltinJSON() {
        if (this.builtinJSON == null) {
            this.builtinJSON = initConstructorAndSwitchPoint("JSON", ScriptObject.class);
        }
        return this.builtinJSON;
    }

    private synchronized ScriptFunction getBuiltinJavaImporter() {
        if (this.builtinJavaImporter == null) {
            this.builtinJavaImporter = initConstructor("JavaImporter", ScriptFunction.class);
        }
        return this.builtinJavaImporter;
    }

    private synchronized ScriptObject getBuiltinJavaApi() {
        if (this.builtinJavaApi == null) {
            this.builtinJavaApi = initConstructor("Java", ScriptObject.class);
        }
        return this.builtinJavaApi;
    }

    private synchronized ScriptFunction getBuiltinRangeError() {
        if (this.builtinRangeError == null) {
            this.builtinRangeError = initErrorSubtype("RangeError", getErrorPrototype());
        }
        return builtinRangeError;
    }

    private synchronized ScriptFunction getBuiltinRegExp() {
        if (this.builtinRegExp == null) {
            this.builtinRegExp = initConstructorAndSwitchPoint("RegExp", ScriptFunction.class);
            final ScriptObject regExpProto = ScriptFunction.getPrototype(builtinRegExp);
            // initialize default regexp object
            this.DEFAULT_REGEXP = new NativeRegExp("(?:)", "", this, regExpProto);
            // RegExp.prototype should behave like a RegExp object. So copy the
            // properties.
            regExpProto.addBoundProperties(DEFAULT_REGEXP);
        }
        return builtinRegExp;
    }

    private synchronized ScriptFunction getBuiltinURIError() {
        if (this.builtinURIError == null) {
            this.builtinURIError = initErrorSubtype("URIError", getErrorPrototype());
        }
        return this.builtinURIError;
    }

    @Override
    public String getClassName() {
        return "global";
    }

    /**
     * Copy function used to clone NativeRegExp objects.
     *
     * @param regexp a NativeRegExp to clone
     *
     * @return copy of the given regexp object
     */
    public static Object regExpCopy(final Object regexp) {
        return new NativeRegExp((NativeRegExp)regexp);
    }

    /**
     * Convert given object to NativeRegExp type.
     *
     * @param obj object to be converted
     * @return NativeRegExp instance
     */
    public static NativeRegExp toRegExp(final Object obj) {
        if (obj instanceof NativeRegExp) {
            return (NativeRegExp)obj;
        }
        return new NativeRegExp(JSType.toString(obj));
    }

    /**
     * ECMA 9.9 ToObject implementation
     *
     * @param obj  an item for which to run ToObject
     * @return ToObject version of given item
     */
    public static Object toObject(final Object obj) {
        if (obj == null || obj == UNDEFINED) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }

        if (obj instanceof ScriptObject) {
            return obj;
        }

        return instance().wrapAsObject(obj);
    }

    /**
     * Allocate a new object array.
     *
     * @param initial object values.
     * @return the new array
     */
    public static NativeArray allocate(final Object[] initial) {
        ArrayData arrayData = ArrayData.allocate(initial);

        for (int index = 0; index < initial.length; index++) {
            final Object value = initial[index];

            if (value == ScriptRuntime.EMPTY) {
                arrayData = arrayData.delete(index);
            }
        }

        return new NativeArray(arrayData);
    }

    /**
     * Allocate a new number array.
     *
     * @param initial number values.
     * @return the new array
     */
    public static NativeArray allocate(final double[] initial) {
        return new NativeArray(ArrayData.allocate(initial));
    }

    /**
     * Allocate a new integer array.
     *
     * @param initial number values.
     * @return the new array
     */
    public static NativeArray allocate(final int[] initial) {
        return new NativeArray(ArrayData.allocate(initial));
    }

    /**
     * Allocate a new object array for arguments.
     *
     * @param arguments initial arguments passed.
     * @param callee reference to the function that uses arguments object
     * @param numParams actual number of declared parameters
     *
     * @return the new array
     */
    public static ScriptObject allocateArguments(final Object[] arguments, final Object callee, final int numParams) {
        return NativeArguments.allocate(arguments, (ScriptFunction)callee, numParams);
    }

    /**
     * Called from generated to check if given function is the builtin 'eval'. If
     * eval is used in a script, a lot of optimizations and assumptions cannot be done.
     *
     * @param  fn function object that is checked
     * @return true if fn is the builtin eval
     */
    public static boolean isEval(final Object fn) {
        return fn == Global.instance().builtinEval;
    }

    /**
     * Called from generated to replace a location property placeholder with the actual location property value.
     *
     * @param  placeholder the value tested for being a placeholder for a location property
     * @param  locationProperty the actual value for the location property
     * @return locationProperty if placeholder is indeed a placeholder for a location property, the placeholder otherwise
     */
    public static Object replaceLocationPropertyPlaceholder(final Object placeholder, final Object locationProperty) {
        return isLocationPropertyPlaceholder(placeholder) ? locationProperty : placeholder;
    }

    /**
     * Called from runtime internals to check if the passed value is a location property placeholder.
     * @param  placeholder the value tested for being a placeholder for a location property
     * @return true if the value is a placeholder, false otherwise.
     */
    public static boolean isLocationPropertyPlaceholder(final Object placeholder) {
        return placeholder == LAZY_SENTINEL;
    }

    /**
     * Create a new RegExp object.
     *
     * @param expression Regular expression.
     * @param options    Search options.
     *
     * @return New RegExp object.
     */
    public static Object newRegExp(final String expression, final String options) {
        if (options == null) {
            return new NativeRegExp(expression);
        }
        return new NativeRegExp(expression, options);
    }

    /**
     * Get the object prototype
     *
     * @return the object prototype
     */
    public static ScriptObject objectPrototype() {
        return Global.instance().getObjectPrototype();
    }

    /**
     * Create a new empty object instance.
     *
     * @return New empty object.
     */
    public static ScriptObject newEmptyInstance() {
        return Global.instance().newObject();
    }

    /**
     * Check if a given object is a ScriptObject, raises an exception if this is
     * not the case
     *
     * @param obj and object to check
     * @return the script object
     */
    public static ScriptObject checkObject(final Object obj) {
        if (!(obj instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }
        return (ScriptObject)obj;
    }

    /**
     * ECMA 9.10 - implementation of CheckObjectCoercible, i.e. raise an exception
     * if this object is null or undefined.
     *
     * @param obj an object to check
     */
    public static void checkObjectCoercible(final Object obj) {
        if (obj == null || obj == UNDEFINED) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }
    }

    /**
     * Return the ES6 global scope for lexically declared bindings.
     * @return the ES6 lexical global scope.
     */
    public final ScriptObject getLexicalScope() {
        assert context.getEnv()._es6;
        return lexicalScope;
    }

    @Override
    public void addBoundProperties(final ScriptObject source, final jdk.nashorn.internal.runtime.Property[] properties) {
        PropertyMap ownMap = getMap();
        LexicalScope lexScope = null;
        PropertyMap lexicalMap = null;
        boolean hasLexicalDefinitions = false;

        if (context.getEnv()._es6) {
            lexScope = (LexicalScope) getLexicalScope();
            lexicalMap = lexScope.getMap();

            for (final jdk.nashorn.internal.runtime.Property property : properties) {
                if (property.isLexicalBinding()) {
                    hasLexicalDefinitions = true;
                }
                // ES6 15.1.8 steps 6. and 7.
                final jdk.nashorn.internal.runtime.Property globalProperty = ownMap.findProperty(property.getKey());
                if (globalProperty != null && !globalProperty.isConfigurable() && property.isLexicalBinding()) {
                    throw ECMAErrors.syntaxError("redeclare.variable", property.getKey().toString());
                }
                final jdk.nashorn.internal.runtime.Property lexicalProperty = lexicalMap.findProperty(property.getKey());
                if (lexicalProperty != null && !property.isConfigurable()) {
                    throw ECMAErrors.syntaxError("redeclare.variable", property.getKey().toString());
                }
            }
        }

        final boolean extensible = isExtensible();
        for (final jdk.nashorn.internal.runtime.Property property : properties) {
            if (property.isLexicalBinding()) {
                assert lexScope != null;
                lexicalMap = lexScope.addBoundProperty(lexicalMap, source, property, true);

                if (ownMap.findProperty(property.getKey()) != null) {
                    // If property exists in the global object invalidate any global constant call sites.
                    invalidateGlobalConstant(property.getKey());
                }
            } else {
                ownMap = addBoundProperty(ownMap, source, property, extensible);
            }
        }

        setMap(ownMap);

        if (hasLexicalDefinitions) {
            assert lexScope != null;
            lexScope.setMap(lexicalMap);
            invalidateLexicalSwitchPoint();
        }
    }

    @Override
    public GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request, final StandardOperation operation) {
        final String name = NashornCallSiteDescriptor.getOperand(desc);
        final boolean isScope = NashornCallSiteDescriptor.isScope(desc);

        if (lexicalScope != null && isScope && !NashornCallSiteDescriptor.isApplyToCall(desc)) {
            if (lexicalScope.hasOwnProperty(name)) {
                return lexicalScope.findGetMethod(desc, request, operation);
            }
        }

        final GuardedInvocation invocation =  super.findGetMethod(desc, request, operation);

        // We want to avoid adding our generic lexical scope switchpoint to global constant invocations,
        // because those are invalidated per-key in the addBoundProperties method above.
        // We therefore check if the invocation does already have a switchpoint and the property is non-inherited,
        // assuming this only applies to global constants. If other non-inherited properties will
        // start using switchpoints some time in the future we'll have to revisit this.
        if (isScope && context.getEnv()._es6 && (invocation.getSwitchPoints() == null || !hasOwnProperty(name))) {
            return invocation.addSwitchPoint(getLexicalScopeSwitchPoint());
        }

        return invocation;
    }

    @Override
    protected FindProperty findProperty(final Object key, final boolean deep, final ScriptObject start) {
        if (lexicalScope != null && start != this && start.isScope()) {
            final FindProperty find = lexicalScope.findProperty(key, false);
            if (find != null) {
                return find;
            }
        }
        return super.findProperty(key, deep, start);
    }

    @Override
    public GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final boolean isScope = NashornCallSiteDescriptor.isScope(desc);

        if (lexicalScope != null && isScope) {
            final String name = NashornCallSiteDescriptor.getOperand(desc);
            if (lexicalScope.hasOwnProperty(name)) {
                return lexicalScope.findSetMethod(desc, request);
            }
        }

        final GuardedInvocation invocation = super.findSetMethod(desc, request);

        if (isScope && context.getEnv()._es6) {
            return invocation.addSwitchPoint(getLexicalScopeSwitchPoint());
        }

        return invocation;
    }

    /**
     * Adds jjs shell interactive mode builtin functions to global scope.
     */
    public void addShellBuiltins() {
        Object value = ScriptFunction.createBuiltin("input", ShellFunctions.INPUT);
        addOwnProperty("input", Attribute.NOT_ENUMERABLE, value);

        value = ScriptFunction.createBuiltin("evalinput", ShellFunctions.EVALINPUT);
        addOwnProperty("evalinput", Attribute.NOT_ENUMERABLE, value);
    }

    private synchronized SwitchPoint getLexicalScopeSwitchPoint() {
        SwitchPoint switchPoint = lexicalScopeSwitchPoint;
        if (switchPoint == null || switchPoint.hasBeenInvalidated()) {
            switchPoint = lexicalScopeSwitchPoint = new SwitchPoint();
        }
        return switchPoint;
    }

    private synchronized void invalidateLexicalSwitchPoint() {
        if (lexicalScopeSwitchPoint != null) {
            context.getLogger(GlobalConstants.class).info("Invalidating non-constant globals on lexical scope update");
            SwitchPoint.invalidateAll(new SwitchPoint[]{ lexicalScopeSwitchPoint });
        }
    }


    @SuppressWarnings("unused")
    private static Object lexicalScopeFilter(final Object self) {
        if (self instanceof Global) {
            return ((Global) self).getLexicalScope();
        }
        return self;
    }

    private <T extends ScriptObject> T initConstructorAndSwitchPoint(final String name, final Class<T> clazz) {
        final T func = initConstructor(name, clazz);
        tagBuiltinProperties(name, func);
        return func;
    }

    private void init(final ScriptEngine eng) {
        assert Context.getGlobal() == this : "this global is not set as current";

        final ScriptEnvironment env = getContext().getEnv();

        // initialize Function and Object constructor
        initFunctionAndObject();

        // Now fix Global's own proto.
        this.setInitialProto(getObjectPrototype());

        // initialize global function properties
        this.eval = this.builtinEval = ScriptFunction.createBuiltin("eval", EVAL);

        this.parseInt = ScriptFunction.createBuiltin("parseInt",   GlobalFunctions.PARSEINT,
                    new Specialization[] {
                    new Specialization(GlobalFunctions.PARSEINT_Z),
                    new Specialization(GlobalFunctions.PARSEINT_I),
                    new Specialization(GlobalFunctions.PARSEINT_OI),
                    new Specialization(GlobalFunctions.PARSEINT_O) });
        this.parseFloat = ScriptFunction.createBuiltin("parseFloat", GlobalFunctions.PARSEFLOAT);
        this.isNaN = ScriptFunction.createBuiltin("isNaN",   GlobalFunctions.IS_NAN,
                   new Specialization[] {
                        new Specialization(GlobalFunctions.IS_NAN_I),
                        new Specialization(GlobalFunctions.IS_NAN_J),
                        new Specialization(GlobalFunctions.IS_NAN_D) });
        this.parseFloat         = ScriptFunction.createBuiltin("parseFloat", GlobalFunctions.PARSEFLOAT);
        this.isNaN              = ScriptFunction.createBuiltin("isNaN",      GlobalFunctions.IS_NAN);
        this.isFinite           = ScriptFunction.createBuiltin("isFinite",   GlobalFunctions.IS_FINITE);
        this.encodeURI          = ScriptFunction.createBuiltin("encodeURI",  GlobalFunctions.ENCODE_URI);
        this.encodeURIComponent = ScriptFunction.createBuiltin("encodeURIComponent", GlobalFunctions.ENCODE_URICOMPONENT);
        this.decodeURI          = ScriptFunction.createBuiltin("decodeURI",  GlobalFunctions.DECODE_URI);
        this.decodeURIComponent = ScriptFunction.createBuiltin("decodeURIComponent", GlobalFunctions.DECODE_URICOMPONENT);
        this.escape             = ScriptFunction.createBuiltin("escape",     GlobalFunctions.ESCAPE);
        this.unescape           = ScriptFunction.createBuiltin("unescape",   GlobalFunctions.UNESCAPE);
        this.print              = ScriptFunction.createBuiltin("print",      env._print_no_newline ? PRINT : PRINTLN);
        this.load               = ScriptFunction.createBuiltin("load",       LOAD);
        this.loadWithNewGlobal  = ScriptFunction.createBuiltin("loadWithNewGlobal", LOAD_WITH_NEW_GLOBAL);
        this.exit               = ScriptFunction.createBuiltin("exit",       EXIT);
        this.quit               = ScriptFunction.createBuiltin("quit",       EXIT);

        // built-in constructors
        this.builtinArray     = initConstructorAndSwitchPoint("Array", ScriptFunction.class);
        this.builtinBoolean   = initConstructorAndSwitchPoint("Boolean", ScriptFunction.class);
        this.builtinNumber    = initConstructorAndSwitchPoint("Number", ScriptFunction.class);
        this.builtinString    = initConstructorAndSwitchPoint("String", ScriptFunction.class);
        this.builtinMath      = initConstructorAndSwitchPoint("Math", ScriptObject.class);

        if (env._es6) {
            this.builtinSymbol = initConstructorAndSwitchPoint("Symbol", ScriptFunction.class);
        } else {
            // We need to manually delete nasgen-generated properties we don't want
            this.delete("Symbol", false);
            this.builtinObject.delete("getOwnPropertySymbols", false);
        }

        // initialize String.prototype.length to 0
        // add String.prototype.length
        final ScriptObject stringPrototype = getStringPrototype();
        stringPrototype.addOwnProperty("length", Attribute.NON_ENUMERABLE_CONSTANT, 0.0);

        // set isArray flag on Array.prototype
        final ScriptObject arrayPrototype = getArrayPrototype();
        arrayPrototype.setIsArray();

        // Error stuff
        initErrorObjects();

        // java access
        if (! env._no_java) {
            this.javaApi = LAZY_SENTINEL;
            this.javaImporter = LAZY_SENTINEL;
            initJavaAccess();
        }

        if (! env._no_typed_arrays) {
            this.arrayBuffer       = LAZY_SENTINEL;
            this.dataView          = LAZY_SENTINEL;
            this.int8Array         = LAZY_SENTINEL;
            this.uint8Array        = LAZY_SENTINEL;
            this.uint8ClampedArray = LAZY_SENTINEL;
            this.int16Array        = LAZY_SENTINEL;
            this.uint16Array       = LAZY_SENTINEL;
            this.int32Array        = LAZY_SENTINEL;
            this.uint32Array       = LAZY_SENTINEL;
            this.float32Array      = LAZY_SENTINEL;
            this.float64Array      = LAZY_SENTINEL;
        }

        if (env._scripting) {
            initScripting(env);
        }

        if (Context.DEBUG) {
            boolean debugOkay;
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                try {
                    sm.checkPermission(new RuntimePermission(Context.NASHORN_DEBUG_MODE));
                    debugOkay = true;
                } catch (final SecurityException ignored) {
                    // if no permission, don't initialize Debug object
                    debugOkay = false;
                }

            } else {
                debugOkay = true;
            }

            if (debugOkay) {
                initDebug();
            }
        }

        copyBuiltins();

        // expose script (command line) arguments as "arguments" property of global
        arguments = wrapAsObject(env.getArguments().toArray());
        if (env._scripting) {
            // synonym for "arguments" in scripting mode
            addOwnProperty("$ARG", Attribute.NOT_ENUMERABLE, arguments);
        }

        if (eng != null) {
            // default file name
            addOwnProperty(ScriptEngine.FILENAME, Attribute.NOT_ENUMERABLE, null);
            // __noSuchProperty__ hook for ScriptContext search of missing variables
            final ScriptFunction noSuchProp = ScriptFunction.createStrictBuiltin(NO_SUCH_PROPERTY_NAME, NO_SUCH_PROPERTY);
            addOwnProperty(NO_SUCH_PROPERTY_NAME, Attribute.NOT_ENUMERABLE, noSuchProp);
        }
    }

    private void initErrorObjects() {
        // Error objects
        this.builtinError = initConstructor("Error", ScriptFunction.class);
        final ScriptObject errorProto = getErrorPrototype();

        // Nashorn specific accessors on Error.prototype - stack, lineNumber, columnNumber and fileName
        final ScriptFunction getStack = ScriptFunction.createBuiltin("getStack", NativeError.GET_STACK);
        final ScriptFunction setStack = ScriptFunction.createBuiltin("setStack", NativeError.SET_STACK);
        errorProto.addOwnProperty("stack", Attribute.NOT_ENUMERABLE, getStack, setStack);
        final ScriptFunction getLineNumber = ScriptFunction.createBuiltin("getLineNumber", NativeError.GET_LINENUMBER);
        final ScriptFunction setLineNumber = ScriptFunction.createBuiltin("setLineNumber", NativeError.SET_LINENUMBER);
        errorProto.addOwnProperty("lineNumber", Attribute.NOT_ENUMERABLE, getLineNumber, setLineNumber);
        final ScriptFunction getColumnNumber = ScriptFunction.createBuiltin("getColumnNumber", NativeError.GET_COLUMNNUMBER);
        final ScriptFunction setColumnNumber = ScriptFunction.createBuiltin("setColumnNumber", NativeError.SET_COLUMNNUMBER);
        errorProto.addOwnProperty("columnNumber", Attribute.NOT_ENUMERABLE, getColumnNumber, setColumnNumber);
        final ScriptFunction getFileName = ScriptFunction.createBuiltin("getFileName", NativeError.GET_FILENAME);
        final ScriptFunction setFileName = ScriptFunction.createBuiltin("setFileName", NativeError.SET_FILENAME);
        errorProto.addOwnProperty("fileName", Attribute.NOT_ENUMERABLE, getFileName, setFileName);

        // ECMA 15.11.4.2 Error.prototype.name
        // Error.prototype.name = "Error";
        errorProto.set(NativeError.NAME, "Error", 0);
        // ECMA 15.11.4.3 Error.prototype.message
        // Error.prototype.message = "";
        errorProto.set(NativeError.MESSAGE, "", 0);

        tagBuiltinProperties("Error", builtinError);

        this.builtinReferenceError = initErrorSubtype("ReferenceError", errorProto);
        this.builtinSyntaxError = initErrorSubtype("SyntaxError", errorProto);
        this.builtinTypeError = initErrorSubtype("TypeError", errorProto);
    }

    private ScriptFunction initErrorSubtype(final String name, final ScriptObject errorProto) {
        final ScriptFunction cons = initConstructor(name, ScriptFunction.class);
        final ScriptObject prototype = ScriptFunction.getPrototype(cons);
        prototype.set(NativeError.NAME, name, 0);
        prototype.set(NativeError.MESSAGE, "", 0);
        prototype.setInitialProto(errorProto);
        tagBuiltinProperties(name, cons);
        return cons;
    }

    private void initJavaAccess() {
        final ScriptObject objectProto = getObjectPrototype();
        this.builtinPackages = new NativeJavaPackage("", objectProto);
        this.builtinCom = new NativeJavaPackage("com", objectProto);
        this.builtinEdu = new NativeJavaPackage("edu", objectProto);
        this.builtinJava = new NativeJavaPackage("java", objectProto);
        this.builtinJavafx = new NativeJavaPackage("javafx", objectProto);
        this.builtinJavax = new NativeJavaPackage("javax", objectProto);
        this.builtinOrg = new NativeJavaPackage("org", objectProto);
    }

    private void initScripting(final ScriptEnvironment scriptEnv) {
        ScriptObject value;
        value = ScriptFunction.createBuiltin("readLine", ScriptingFunctions.READLINE);
        addOwnProperty("readLine", Attribute.NOT_ENUMERABLE, value);

        value = ScriptFunction.createBuiltin("readFully", ScriptingFunctions.READFULLY);
        addOwnProperty("readFully", Attribute.NOT_ENUMERABLE, value);

        final String execName = ScriptingFunctions.EXEC_NAME;
        value = ScriptFunction.createBuiltin(execName, ScriptingFunctions.EXEC);
        value.addOwnProperty(ScriptingFunctions.THROW_ON_ERROR_NAME, Attribute.NOT_ENUMERABLE, false);

        addOwnProperty(execName, Attribute.NOT_ENUMERABLE, value);

        // Nashorn extension: global.echo (scripting-mode-only)
        // alias for "print"
        value = (ScriptObject)get("print");
        addOwnProperty("echo", Attribute.NOT_ENUMERABLE, value);

        // Nashorn extension: global.$OPTIONS (scripting-mode-only)
        final ScriptObject options = newObject();
        copyOptions(options, scriptEnv);
        addOwnProperty("$OPTIONS", Attribute.NOT_ENUMERABLE, options);

        // Nashorn extension: global.$ENV (scripting-mode-only)
        if (System.getSecurityManager() == null) {
            // do not fill $ENV if we have a security manager around
            // Retrieve current state of ENV variables.
            final ScriptObject env = newObject();
            env.putAll(System.getenv(), scriptEnv._strict);

            // Some platforms, e.g., Windows, do not define the PWD environment
            // variable, so that the $ENV.PWD property needs to be explicitly
            // set.
            if (!env.containsKey(ScriptingFunctions.PWD_NAME)) {
                env.put(ScriptingFunctions.PWD_NAME, System.getProperty("user.dir"), scriptEnv._strict);
            }

            addOwnProperty(ScriptingFunctions.ENV_NAME, Attribute.NOT_ENUMERABLE, env);
        } else {
            addOwnProperty(ScriptingFunctions.ENV_NAME, Attribute.NOT_ENUMERABLE, UNDEFINED);
        }

        // add other special properties for exec support
        addOwnProperty(ScriptingFunctions.OUT_NAME, Attribute.NOT_ENUMERABLE, UNDEFINED);
        addOwnProperty(ScriptingFunctions.ERR_NAME, Attribute.NOT_ENUMERABLE, UNDEFINED);
        addOwnProperty(ScriptingFunctions.EXIT_NAME, Attribute.NOT_ENUMERABLE, UNDEFINED);
    }

    private static void copyOptions(final ScriptObject options, final ScriptEnvironment scriptEnv) {
        for (final Field f : scriptEnv.getClass().getFields()) {
            try {
                options.set(f.getName(), f.get(scriptEnv), 0);
            } catch (final IllegalArgumentException | IllegalAccessException exp) {
                throw new RuntimeException(exp);
            }
        }
    }

    private void copyBuiltins() {
        this.array             = this.builtinArray;
        this._boolean          = this.builtinBoolean;
        this.error             = this.builtinError;
        this.function          = this.builtinFunction;
        this.com               = this.builtinCom;
        this.edu               = this.builtinEdu;
        this.java              = this.builtinJava;
        this.javafx            = this.builtinJavafx;
        this.javax             = this.builtinJavax;
        this.org               = this.builtinOrg;
        this.math              = this.builtinMath;
        this.number            = this.builtinNumber;
        this.object            = this.builtinObject;
        this.packages          = this.builtinPackages;
        this.referenceError    = this.builtinReferenceError;
        this.string            = this.builtinString;
        this.syntaxError       = this.builtinSyntaxError;
        this.typeError         = this.builtinTypeError;
        this.symbol            = this.builtinSymbol;
    }

    private void initDebug() {
        this.addOwnProperty("Debug", Attribute.NOT_ENUMERABLE, initConstructor("Debug", ScriptObject.class));
    }

    private Object printImpl(final boolean newLine, final Object... objects) {
        final ScriptContext sc = currentContext();
        @SuppressWarnings("resource")
        final PrintWriter out = sc != null? new PrintWriter(sc.getWriter()) : getContext().getEnv().getOut();
        final StringBuilder sb = new StringBuilder();

        for (final Object obj : objects) {
            if (sb.length() != 0) {
                sb.append(' ');
            }

            sb.append(JSType.toString(obj));
        }

        // Print all at once to ensure thread friendly result.
        if (newLine) {
            out.println(sb.toString());
        } else {
            out.print(sb.toString());
        }

        out.flush();

        return UNDEFINED;
    }

    private <T extends ScriptObject> T initConstructor(final String name, final Class<T> clazz) {
        try {
            // Assuming class name pattern for built-in JS constructors.
            final StringBuilder sb = new StringBuilder("jdk.nashorn.internal.objects.");

            sb.append("Native");
            sb.append(name);
            sb.append("$Constructor");

            final Class<?> funcClass = Class.forName(sb.toString());
            final T res = clazz.cast(funcClass.newInstance());

            if (res instanceof ScriptFunction) {
                // All global constructor prototypes are not-writable,
                // not-enumerable and not-configurable.
                final ScriptFunction func = (ScriptFunction)res;
                func.modifyOwnProperty(func.getProperty("prototype"), Attribute.NON_ENUMERABLE_CONSTANT);
            }

            if (res.getProto() == null) {
                res.setInitialProto(getObjectPrototype());
            }

            res.setIsBuiltin();

            return res;
        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private List<jdk.nashorn.internal.runtime.Property> extractBuiltinProperties(final String name, final ScriptObject func) {
        final List<jdk.nashorn.internal.runtime.Property> list = new ArrayList<>();

        list.addAll(Arrays.asList(func.getMap().getProperties()));

        if (func instanceof ScriptFunction) {
            final ScriptObject proto = ScriptFunction.getPrototype((ScriptFunction)func);
            if (proto != null) {
                list.addAll(Arrays.asList(proto.getMap().getProperties()));
            }
        }

        final jdk.nashorn.internal.runtime.Property prop = getProperty(name);
        if (prop != null) {
            list.add(prop);
        }

        return list;
    }

    /**
     * Given a builtin object, traverse its properties recursively and associate them with a name that
     * will be a key to their invalidation switchpoint.
     * @param name name for key
     * @param func builtin script object
     */
    private void tagBuiltinProperties(final String name, final ScriptObject func) {
        SwitchPoint sp = context.getBuiltinSwitchPoint(name);
        if (sp == null) {
            sp = context.newBuiltinSwitchPoint(name);
        }

        //get all builtin properties in this builtin object and register switchpoints keyed on the propery name,
        //one overwrite destroys all for now, e.g. Function.prototype.apply = 17; also destroys Function.prototype.call etc
        for (final jdk.nashorn.internal.runtime.Property prop : extractBuiltinProperties(name, func)) {
            prop.setBuiltinSwitchPoint(sp);
        }
    }

    // Function and Object constructors are inter-dependent. Also,
    // Function.prototype
    // functions are not properly initialized. We fix the references here.
    // NOTE: be careful if you want to re-order the operations here. You may
    // have
    // to play with object references carefully!!
    private void initFunctionAndObject() {
        // First-n-foremost is Function

        this.builtinFunction = initConstructor("Function", ScriptFunction.class);

        // create global anonymous function
        final ScriptFunction anon = ScriptFunction.createAnonymous();
        // need to copy over members of Function.prototype to anon function
        anon.addBoundProperties(getFunctionPrototype());

        // Function.prototype === Object.getPrototypeOf(Function) ===
        // <anon-function>
        builtinFunction.setInitialProto(anon);
        builtinFunction.setPrototype(anon);
        anon.set("constructor", builtinFunction, 0);
        anon.deleteOwnProperty(anon.getMap().findProperty("prototype"));

        // use "getter" so that [[ThrowTypeError]] function's arity is 0 - as specified in step 10 of section 13.2.3
        this.typeErrorThrower = ScriptFunction.createBuiltin("TypeErrorThrower", Lookup.TYPE_ERROR_THROWER_GETTER);
        typeErrorThrower.preventExtensions();

        // now initialize Object
        this.builtinObject = initConstructor("Object", ScriptFunction.class);
        final ScriptObject ObjectPrototype = getObjectPrototype();
        // Object.getPrototypeOf(Function.prototype) === Object.prototype
        anon.setInitialProto(ObjectPrototype);

        // ES6 draft compliant __proto__ property of Object.prototype
        // accessors on Object.prototype for "__proto__"
        final ScriptFunction getProto = ScriptFunction.createBuiltin("getProto", NativeObject.GET__PROTO__);
        final ScriptFunction setProto = ScriptFunction.createBuiltin("setProto", NativeObject.SET__PROTO__);
        ObjectPrototype.addOwnProperty("__proto__", Attribute.NOT_ENUMERABLE, getProto, setProto);

        // Function valued properties of Function.prototype were not properly
        // initialized. Because, these were created before global.function and
        // global.object were not initialized.
        jdk.nashorn.internal.runtime.Property[] properties = getFunctionPrototype().getMap().getProperties();
        for (final jdk.nashorn.internal.runtime.Property property : properties) {
            final Object key = property.getKey();
            final Object value = builtinFunction.get(key);

            if (value instanceof ScriptFunction && value != anon) {
                final ScriptFunction func = (ScriptFunction)value;
                func.setInitialProto(getFunctionPrototype());
                final ScriptObject prototype = ScriptFunction.getPrototype(func);
                if (prototype != null) {
                    prototype.setInitialProto(ObjectPrototype);
                }
            }
        }

        // For function valued properties of Object and Object.prototype, make
        // sure prototype's proto chain ends with Object.prototype
        for (final jdk.nashorn.internal.runtime.Property property : builtinObject.getMap().getProperties()) {
            final Object key = property.getKey();
            final Object value = builtinObject.get(key);

            if (value instanceof ScriptFunction) {
                final ScriptFunction func = (ScriptFunction)value;
                final ScriptObject prototype = ScriptFunction.getPrototype(func);
                if (prototype != null) {
                    prototype.setInitialProto(ObjectPrototype);
                }
            }
        }

        properties = getObjectPrototype().getMap().getProperties();

        for (final jdk.nashorn.internal.runtime.Property property : properties) {
            final Object key   = property.getKey();
            if (key.equals("constructor")) {
                continue;
            }

            final Object value = ObjectPrototype.get(key);
            if (value instanceof ScriptFunction) {
                final ScriptFunction func = (ScriptFunction)value;
                final ScriptObject prototype = ScriptFunction.getPrototype(func);
                if (prototype != null) {
                    prototype.setInitialProto(ObjectPrototype);
                }
            }
        }

        tagBuiltinProperties("Object", builtinObject);
        tagBuiltinProperties("Function", builtinFunction);
        tagBuiltinProperties("Function", anon);
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), Global.class, name, MH.type(rtype, types));
    }

    RegExpResult getLastRegExpResult() {
        return lastRegExpResult;
    }

    void setLastRegExpResult(final RegExpResult regExpResult) {
        this.lastRegExpResult = regExpResult;
    }

    @Override
    protected boolean isGlobal() {
        return true;
    }

    /**
     * A class representing the ES6 global lexical scope.
     */
    private static class LexicalScope extends ScriptObject {

        LexicalScope(final Global global) {
            super(global, PropertyMap.newMap());
        }

        @Override
        protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request, final StandardOperation operation) {
            return filterInvocation(super.findGetMethod(desc, request, operation));
        }

        @Override
        protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
            return filterInvocation(super.findSetMethod(desc, request));
        }

        @Override
        protected PropertyMap addBoundProperty(final PropertyMap propMap, final ScriptObject source, final jdk.nashorn.internal.runtime.Property property, final boolean extensible) {
            // We override this method just to make it callable by Global
            return super.addBoundProperty(propMap, source, property, extensible);
        }

        private static GuardedInvocation filterInvocation(final GuardedInvocation invocation) {
            final MethodType type = invocation.getInvocation().type();
            return invocation.asType(type.changeParameterType(0, Object.class)).filterArguments(0, LEXICAL_SCOPE_FILTER);
        }
    }

}
