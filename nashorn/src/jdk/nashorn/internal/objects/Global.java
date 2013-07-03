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
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.GlobalFunctions;
import jdk.nashorn.internal.runtime.GlobalObject;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.NativeJavaPackage;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.regexp.RegExpResult;
import jdk.nashorn.internal.runtime.Scope;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.ScriptingFunctions;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.linker.InvokeByName;
import jdk.nashorn.internal.scripts.JO;

/**
 * Representation of global scope.
 */
@ScriptClass("Global")
public final class Global extends ScriptObject implements GlobalObject, Scope {
    private static final InvokeByName TO_STRING = new InvokeByName("toString", ScriptObject.class);
    private static final InvokeByName VALUE_OF  = new InvokeByName("valueOf",  ScriptObject.class);

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
    public final Object NaN = Double.NaN;

    /** Value property Infinity of the Global Object - ECMA 15.1.1.2 Infinity */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public final Object Infinity = Double.POSITIVE_INFINITY;

    /** Value property Undefined of the Global Object - ECMA 15.1.1.3 Undefined */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public final Object undefined = UNDEFINED;

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

    /** ECMA 15.1.4.7 Date constructor */
    @Property(name = "Date", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object date;

    /** ECMA 15.1.4.8 RegExp constructor */
    @Property(name = "RegExp", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object regexp;

    /** ECMA 15.12 - The JSON object */
    @Property(name = "JSON", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object json;

    /** Nashorn extension: global.JSAdapter */
    @Property(name = "JSAdapter", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object jsadapter;

    /** ECMA 15.8 - The Math object */
    @Property(name = "Math", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object math;

    /** Error object */
    @Property(name = "Error", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object error;

    /** EvalError object */
    @Property(name = "EvalError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object evalError;

    /** RangeError object */
    @Property(name = "RangeError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object rangeError;

    /** ReferenceError object */
    @Property(name = "ReferenceError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object referenceError;

    /** SyntaxError object */
    @Property(name = "SyntaxError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object syntaxError;

    /** TypeError object */
    @Property(name = "TypeError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object typeError;

    /** URIError object */
    @Property(name = "URIError", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object uriError;

    /** ArrayBuffer object */
    @Property(name = "ArrayBuffer", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object arrayBuffer;

    /** TypedArray (int8) */
    @Property(name = "Int8Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object int8Array;

    /** TypedArray (uint8) */
    @Property(name = "Uint8Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object uint8Array;

    /** TypedArray (uint8) - Clamped */
    @Property(name = "Uint8ClampedArray", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object uint8ClampedArray;

    /** TypedArray (int16) */
    @Property(name = "Int16Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object int16Array;

    /** TypedArray (uint16) */
    @Property(name = "Uint16Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object uint16Array;

    /** TypedArray (int32) */
    @Property(name = "Int32Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object int32Array;

    /** TypedArray (uint32) */
    @Property(name = "Uint32Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object uint32Array;

    /** TypedArray (float32) */
    @Property(name = "Float32Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object float32Array;

    /** TypedArray (float64) */
    @Property(name = "Float64Array", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object float64Array;

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

    /** Nashorn extension: Java access - global.javaImporter */
    @Property(name = "JavaImporter", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object javaImporter;

    /** Nashorn extension: global.Java Object constructor. */
    @Property(name = "Java", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object javaApi;

    /** Nashorn extension: current script's file name */
    @Property(name = "__FILE__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public Object __FILE__;

    /** Nashorn extension: current script's directory */
    @Property(name = "__DIR__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public Object __DIR__;

    /** Nashorn extension: current source line number being executed */
    @Property(name = "__LINE__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public Object __LINE__;

    /** Used as Date.prototype's default value */
    public NativeDate   DEFAULT_DATE;

    /** Used as RegExp.prototype's default value */
    public NativeRegExp DEFAULT_REGEXP;

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
    private ScriptObject   builtinJavaImporter;
    private ScriptObject   builtinJavaApi;
    private ScriptObject   builtinArrayBuffer;
    private ScriptObject   builtinInt8Array;
    private ScriptObject   builtinUint8Array;
    private ScriptObject   builtinUint8ClampedArray;
    private ScriptObject   builtinInt16Array;
    private ScriptObject   builtinUint16Array;
    private ScriptObject   builtinInt32Array;
    private ScriptObject   builtinUint32Array;
    private ScriptObject   builtinFloat32Array;
    private ScriptObject   builtinFloat64Array;

    // Flag to indicate that a split method issued a return statement
    private int splitState = -1;

    // class cache
    private ClassCache classCache;

    // Used to store the last RegExp result to support deprecated RegExp constructor properties
    private RegExpResult lastRegExpResult;

    private static final MethodHandle EVAL              = findOwnMH("eval",              Object.class, Object.class, Object.class);
    private static final MethodHandle PRINT             = findOwnMH("print",             Object.class, Object.class, Object[].class);
    private static final MethodHandle PRINTLN           = findOwnMH("println",           Object.class, Object.class, Object[].class);
    private static final MethodHandle LOAD              = findOwnMH("load",              Object.class, Object.class, Object.class);
    private static final MethodHandle LOADWITHNEWGLOBAL = findOwnMH("loadWithNewGlobal", Object.class, Object.class, Object[].class);
    private static final MethodHandle EXIT              = findOwnMH("exit",              Object.class, Object.class, Object.class);

    private final Context context;

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    /**
     * Constructor
     *
     * @param context the context
     */
    public Global(final Context context) {
        this.context = context;
        this.setIsScope();
        /*
         * Duplicate global's map and use it. This way the initial Map filled
         * by nasgen (referenced from static field in this class) is retained
         * 'as is'. This allows multiple globals to be used within a context.
         */
        this.setMap(getMap().duplicate());

        final int cacheSize = context.getEnv()._class_cache_size;
        if (cacheSize > 0) {
            classCache = new ClassCache(cacheSize);
        }
    }

    /**
     * Script access to "current" Global instance
     *
     * @return the global singleton
     */
    public static Global instance() {
        ScriptObject global = Context.getGlobal();
        if (! (global instanceof Global)) {
            throw new IllegalStateException("no current global instance");
        }
        return (Global)global;
    }

    /**
     * Script access to {@link ScriptEnvironment}
     *
     * @return the script environment
     */
    static ScriptEnvironment getEnv() {
        return instance().context.getEnv();
    }

    /**
     * Script access to {@link Context}
     *
     * @return the context
     */
    static Context getThisContext() {
        return instance().context;
    }

    // GlobalObject interface implementation

    @Override
    public void initBuiltinObjects() {
        if (this.builtinObject != null) {
            // already initialized, just return
            return;
        }

        init();
    }

    @Override
    public ScriptFunction newScriptFunction(final String name, final MethodHandle handle, final ScriptObject scope, final boolean strict) {
        return new ScriptFunctionImpl(name, handle, scope, null, strict, false, true);
    }

    @Override
    public Object wrapAsObject(final Object obj) {
        if (obj instanceof Boolean) {
            return new NativeBoolean((Boolean)obj);
        } else if (obj instanceof Number) {
            return new NativeNumber(((Number)obj).doubleValue());
        } else if (obj instanceof String || obj instanceof ConsString) {
            return new NativeString((CharSequence)obj);
        } else if (obj instanceof Object[]) { // extension
            return new NativeArray((Object[])obj);
        } else if (obj instanceof double[]) { // extension
            return new NativeArray((double[])obj);
        } else if (obj instanceof long[]) {
            return new NativeArray((long[])obj);
        } else if (obj instanceof int[]) {
            return new NativeArray((int[])obj);
        } else {
            // FIXME: more special cases? Map? List?
            return obj;
        }
    }

    @Override
    public GuardedInvocation primitiveLookup(final LinkRequest request, final Object self) {
        if (self instanceof String || self instanceof ConsString) {
            return NativeString.lookupPrimitive(request, self);
        } else if (self instanceof Number) {
            return NativeNumber.lookupPrimitive(request, self);
        } else if (self instanceof Boolean) {
            return NativeBoolean.lookupPrimitive(request, self);
        }
        throw new IllegalArgumentException("Unsupported primitive: " + self);
    }

    @Override
    public ScriptObject newObject() {
        return new JO(getObjectPrototype());
    }

    @Override
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
                if (toString instanceof ScriptFunction) {
                    final Object value = TO_STRING.getInvoker().invokeExact(toString, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }

                final Object valueOf = VALUE_OF.getGetter().invokeExact(sobj);
                if (valueOf instanceof ScriptFunction) {
                    final Object value = VALUE_OF.getInvoker().invokeExact(valueOf, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }
                throw typeError(this, "cannot.get.default.string");
            }

            if (hint == Number.class) {
                final Object valueOf = VALUE_OF.getGetter().invokeExact(sobj);
                if (valueOf instanceof ScriptFunction) {
                    final Object value = VALUE_OF.getInvoker().invokeExact(valueOf, sobj);
                    if (JSType.isPrimitive(value)) {
                        return value;
                    }
                }

                final Object toString = TO_STRING.getGetter().invokeExact(sobj);
                if (toString instanceof ScriptFunction) {
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

    @Override
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

    @Override
    public ScriptObject newError(final String msg) {
        return new NativeError(msg);
    }

    @Override
    public ScriptObject newEvalError(final String msg) {
        return new NativeEvalError(msg);
    }

    @Override
    public ScriptObject newRangeError(final String msg) {
        return new NativeRangeError(msg);
    }

    @Override
    public ScriptObject newReferenceError(final String msg) {
        return new NativeReferenceError(msg);
    }

    @Override
    public ScriptObject newSyntaxError(final String msg) {
        return new NativeSyntaxError(msg);
    }

    @Override
    public ScriptObject newTypeError(final String msg) {
        return new NativeTypeError(msg);
    }

    @Override
    public ScriptObject newURIError(final String msg) {
        return new NativeURIError(msg);
    }

    @Override
    public PropertyDescriptor newGenericDescriptor(final boolean configurable, final boolean enumerable) {
        return new GenericPropertyDescriptor(configurable, enumerable);
    }

    @Override
    public PropertyDescriptor newDataDescriptor(final Object value, final boolean configurable, final boolean enumerable, final boolean writable) {
        return new DataPropertyDescriptor(configurable, enumerable, writable, value);
    }

    @Override
    public PropertyDescriptor newAccessorDescriptor(final Object get, final Object set, final boolean configurable, final boolean enumerable) {
        final AccessorPropertyDescriptor desc = new AccessorPropertyDescriptor(configurable, enumerable, get == null ? UNDEFINED : get, set == null ? UNDEFINED : set);

        if (get == null) {
            desc.delete(PropertyDescriptor.GET, false);
        }

        if (set == null) {
            desc.delete(PropertyDescriptor.SET, false);
        }

        return desc;
    }


    /**
     * Cache for compiled script classes.
     */
    @SuppressWarnings("serial")
    private static class ClassCache extends LinkedHashMap<Source, SoftReference<Class<?>>> {
        private final int size;

        ClassCache(int size) {
            super(size, 0.75f, true);
            this.size = size;
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<Source, SoftReference<Class<?>>> eldest) {
            return size() >= size;
        }
    }

    // Class cache management
    @Override
    public Class<?> findCachedClass(final Source source) {
        assert classCache != null : "Class cache used without being initialized";
        SoftReference<Class<?>> ref = classCache.get(source);
        if (ref != null) {
            final Class<?> clazz = ref.get();
            if (clazz == null) {
                classCache.remove(source);
            }
            return clazz;
        }

        return null;
    }

    @Override
    public void cacheClass(final Source source, final Class<?> clazz) {
        assert classCache != null : "Class cache used without being initialized";
        classCache.put(source, new SoftReference<Class<?>>(clazz));
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
        return directEval(self, str, UNDEFINED, UNDEFINED, UNDEFINED);
    }

    /**
     * Direct eval
     *
     * @param self     The scope of eval passed as 'self'
     * @param str      Evaluated code
     * @param callThis "this" to be passed to the evaluated code
     * @param location location of the eval call
     * @param strict   is eval called a strict mode code?
     *
     * @return the return value of the eval
     *
     * This is directly invoked from generated when eval(code) is called in user code
     */
    public static Object directEval(final Object self, final Object str, final Object callThis, final Object location, final Object strict) {
        if (!(str instanceof String || str instanceof ConsString)) {
            return str;
        }
        final Global global = Global.instance();
        final ScriptObject scope = (self instanceof ScriptObject) ? (ScriptObject)self : global;

        return global.context.eval(scope, str.toString(), callThis, location, Boolean.TRUE.equals(strict));
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
        return printImpl(false, objects);
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
        return printImpl(true, objects);
    }

    /**
     * Global load implementation - Nashorn extension
     *
     * @param self    scope
     * @param source  source to load
     *
     * @return result of load (undefined)
     *
     * @throws IOException if source could not be read
     */
    public static Object load(final Object self, final Object source) throws IOException {
        final Global global = Global.instance();
        final ScriptObject scope = (self instanceof ScriptObject) ? (ScriptObject)self : global;
        return global.context.load(scope, source);
    }

    /**
     * Global loadWithNewGlobal implementation - Nashorn extension
     *
     * @param self scope
     * @param args from plus (optional) arguments to be passed to the loaded script
     *
     * @return result of load (may be undefined)
     *
     * @throws IOException if source could not be read
     */
    public static Object loadWithNewGlobal(final Object self, final Object...args) throws IOException {
        final Global global = Global.instance();
        final int length = args.length;
        final boolean hasArgs = 0 < length;
        final Object from = hasArgs ? args[0] : UNDEFINED;
        final Object[] arguments = hasArgs ? Arrays.copyOfRange(args, 1, length) : args;

        return global.context.loadWithNewGlobal(from, arguments);
    }

    /**
     * Global exit and quit implementation - Nashorn extension: perform a {@code System.exit} call from the script
     *
     * @param self  self reference
     * @param code  exit code
     *
     * @return undefined (will never be reacheD)
     */
    public static Object exit(final Object self, final Object code) {
        System.exit(JSType.toInt32(code));
        return UNDEFINED;
    }

    ScriptObject getFunctionPrototype() {
        return ScriptFunction.getPrototype(builtinFunction);
    }

    ScriptObject getObjectPrototype() {
        return ScriptFunction.getPrototype(builtinObject);
    }

    ScriptObject getArrayPrototype() {
        return ScriptFunction.getPrototype(builtinArray);
    }

    ScriptObject getBooleanPrototype() {
        return ScriptFunction.getPrototype(builtinBoolean);
    }

    ScriptObject getNumberPrototype() {
        return ScriptFunction.getPrototype(builtinNumber);
    }

    ScriptObject getDatePrototype() {
        return ScriptFunction.getPrototype(builtinDate);
    }

    ScriptObject getRegExpPrototype() {
        return ScriptFunction.getPrototype(builtinRegExp);
    }

    ScriptObject getStringPrototype() {
        return ScriptFunction.getPrototype(builtinString);
    }

    ScriptObject getErrorPrototype() {
        return ScriptFunction.getPrototype(builtinError);
    }

    ScriptObject getEvalErrorPrototype() {
        return ScriptFunction.getPrototype(builtinEvalError);
    }

    ScriptObject getRangeErrorPrototype() {
        return ScriptFunction.getPrototype(builtinRangeError);
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
        return ScriptFunction.getPrototype(builtinURIError);
    }

    ScriptObject getJavaImporterPrototype() {
        return ScriptFunction.getPrototype(builtinJavaImporter);
    }

    ScriptObject getJSAdapterPrototype() {
        return ScriptFunction.getPrototype(builtinJSAdapter);
    }

    ScriptObject getArrayBufferPrototype() {
        return ScriptFunction.getPrototype(builtinArrayBuffer);
    }

    ScriptObject getInt8ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinInt8Array);
    }

    ScriptObject getUint8ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinUint8Array);
    }

    ScriptObject getUint8ClampedArrayPrototype() {
        return ScriptFunction.getPrototype(builtinUint8ClampedArray);
    }

    ScriptObject getInt16ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinInt16Array);
    }

    ScriptObject getUint16ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinUint16Array);
    }

    ScriptObject getInt32ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinInt32Array);
    }

    ScriptObject getUint32ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinUint32Array);
    }

    ScriptObject getFloat32ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinFloat32Array);
    }

    ScriptObject getFloat64ArrayPrototype() {
        return ScriptFunction.getPrototype(builtinFloat64Array);
    }

    private ScriptFunction getBuiltinArray() {
        return builtinArray;
    }


    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin array has not been overridden
     */
    public static boolean isBuiltinArray() {
        final Global instance = Global.instance();
        return instance.array == instance.getBuiltinArray();
    }

    private ScriptFunction getBuiltinBoolean() {
        return builtinBoolean;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin boolean has not been overridden
     */
    public static boolean isBuiltinBoolean() {
        final Global instance = Global.instance();
        return instance._boolean == instance.getBuiltinBoolean();
    }

    private ScriptFunction getBuiltinDate() {
        return builtinDate;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin date has not been overridden
     */
    public static boolean isBuiltinDate() {
        final Global instance = Global.instance();
        return instance.date == instance.getBuiltinDate();
    }

    private ScriptFunction getBuiltinError() {
        return builtinError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin error has not been overridden
     */
    public static boolean isBuiltinError() {
        final Global instance = Global.instance();
        return instance.error == instance.getBuiltinError();
    }

    private ScriptFunction getBuiltinEvalError() {
        return builtinEvalError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin eval error has not been overridden
     */
    public static boolean isBuiltinEvalError() {
        final Global instance = Global.instance();
        return instance.evalError == instance.getBuiltinEvalError();
    }

    private ScriptFunction getBuiltinFunction() {
        return builtinFunction;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin function has not been overridden
     */
    public static boolean isBuiltinFunction() {
        final Global instance = Global.instance();
        return instance.function == instance.getBuiltinFunction();
    }

    private ScriptFunction getBuiltinJSAdapter() {
        return builtinJSAdapter;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin JSAdapter has not been overridden
     */
    public static boolean isBuiltinJSAdapter() {
        final Global instance = Global.instance();
        return instance.jsadapter == instance.getBuiltinJSAdapter();
    }

    private ScriptObject getBuiltinJSON() {
        return builtinJSON;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin JSON has has not been overridden
     */
    public static boolean isBuiltinJSON() {
        final Global instance = Global.instance();
        return instance.json == instance.getBuiltinJSON();
    }

    private ScriptObject getBuiltinJava() {
        return builtinJava;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin Java has not been overridden
     */
    public static boolean isBuiltinJava() {
        final Global instance = Global.instance();
        return instance.java == instance.getBuiltinJava();
    }

    private ScriptObject getBuiltinJavax() {
        return builtinJavax;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin Javax has not been overridden
     */
    public static boolean isBuiltinJavax() {
        final Global instance = Global.instance();
        return instance.javax == instance.getBuiltinJavax();
    }

    private ScriptObject getBuiltinJavaImporter() {
        return builtinJavaImporter;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin Java importer has not been overridden
     */
    public static boolean isBuiltinJavaImporter() {
        final Global instance = Global.instance();
        return instance.javaImporter == instance.getBuiltinJavaImporter();
    }

    private ScriptObject getBuiltinMath() {
        return builtinMath;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin math has not been overridden
     */
    public static boolean isBuiltinMath() {
        final Global instance = Global.instance();
        return instance.math == instance.getBuiltinMath();
    }

    private ScriptFunction getBuiltinNumber() {
        return builtinNumber;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin number has not been overridden
     */
    public static boolean isBuiltinNumber() {
        final Global instance = Global.instance();
        return instance.number == instance.getBuiltinNumber();
    }

    private ScriptFunction getBuiltinObject() {
        return builtinObject;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin object has not been overridden
     */
    public static boolean isBuiltinObject() {
        final Global instance = Global.instance();
        return instance.object == instance.getBuiltinObject();
    }

    private ScriptObject getBuiltinPackages() {
        return builtinPackages;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin package has not been overridden
     */
    public static boolean isBuiltinPackages() {
        final Global instance = Global.instance();
        return instance.packages == instance.getBuiltinPackages();
    }

    private ScriptFunction getBuiltinRangeError() {
        return builtinRangeError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin range error has not been overridden
     */
    public static boolean isBuiltinRangeError() {
        final Global instance = Global.instance();
        return instance.rangeError == instance.getBuiltinRangeError();
    }

    private ScriptFunction getBuiltinReferenceError() {
        return builtinReferenceError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin reference error has not been overridden
     */
    public static boolean isBuiltinReferenceError() {
        final Global instance = Global.instance();
        return instance.referenceError == instance.getBuiltinReferenceError();
    }

    private ScriptFunction getBuiltinRegExp() {
        return builtinRegExp;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin regexp has not been overridden
     */
    public static boolean isBuiltinRegExp() {
        final Global instance = Global.instance();
        return instance.regexp == instance.getBuiltinRegExp();
    }

    private ScriptFunction getBuiltinString() {
        return builtinString;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin Java has not been overridden
     */
    public static boolean isBuiltinString() {
        final Global instance = Global.instance();
        return instance.string == instance.getBuiltinString();
    }

    private ScriptFunction getBuiltinSyntaxError() {
        return builtinSyntaxError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin syntax error has not been overridden
     */
    public static boolean isBuiltinSyntaxError() {
        final Global instance = Global.instance();
        return instance.syntaxError == instance.getBuiltinSyntaxError();
    }

    private ScriptFunction getBuiltinTypeError() {
        return builtinTypeError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin type error has not been overridden
     */
    public static boolean isBuiltinTypeError() {
        final Global instance = Global.instance();
        return instance.typeError == instance.getBuiltinTypeError();
    }

    private ScriptFunction getBuiltinURIError() {
        return builtinURIError;
    }

    /**
     * Called from compiled script code to test if builtin has been overridden
     *
     * @return true if builtin URI error has not been overridden
     */
    public static boolean isBuiltinURIError() {
        final Global instance = Global.instance();
        return instance.uriError == instance.getBuiltinURIError();
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
     * Allocate a new long array.
     *
     * @param initial number values.
     * @return the new array
     */
    public static NativeArray allocate(final long[] initial) {
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
     */
    public static void checkObject(final Object obj) {
        if (!(obj instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }
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
     * Get the current split state.
     *
     * @return current split state
     */
    @Override
    public int getSplitState() {
        return splitState;
    }

    /**
     * Set the current split state.
     *
     * @param state current split state
     */
    @Override
    public void setSplitState(final int state) {
        splitState = state;
    }

    private void init() {
        assert Context.getGlobal() == this : "this global is not set as current";

        final ScriptEnvironment env = context.getEnv();
        // initialize Function and Object constructor
        initFunctionAndObject();

        // Now fix Global's own proto.
        this.setProto(getObjectPrototype());

        // initialize global function properties
        this.eval = this.builtinEval = ScriptFunctionImpl.makeFunction("eval", EVAL);

        this.parseInt           = ScriptFunctionImpl.makeFunction("parseInt",   GlobalFunctions.PARSEINT);
        this.parseFloat         = ScriptFunctionImpl.makeFunction("parseFloat", GlobalFunctions.PARSEFLOAT);
        this.isNaN              = ScriptFunctionImpl.makeFunction("isNaN",      GlobalFunctions.IS_NAN);
        this.isFinite           = ScriptFunctionImpl.makeFunction("isFinite",   GlobalFunctions.IS_FINITE);
        this.encodeURI          = ScriptFunctionImpl.makeFunction("encodeURI",  GlobalFunctions.ENCODE_URI);
        this.encodeURIComponent = ScriptFunctionImpl.makeFunction("encodeURIComponent", GlobalFunctions.ENCODE_URICOMPONENT);
        this.decodeURI          = ScriptFunctionImpl.makeFunction("decodeURI",  GlobalFunctions.DECODE_URI);
        this.decodeURIComponent = ScriptFunctionImpl.makeFunction("decodeURIComponent", GlobalFunctions.DECODE_URICOMPONENT);
        this.escape             = ScriptFunctionImpl.makeFunction("escape",     GlobalFunctions.ESCAPE);
        this.unescape           = ScriptFunctionImpl.makeFunction("unescape",   GlobalFunctions.UNESCAPE);
        this.print              = ScriptFunctionImpl.makeFunction("print",      env._print_no_newline ? PRINT : PRINTLN);
        this.load               = ScriptFunctionImpl.makeFunction("load",       LOAD);
        this.loadWithNewGlobal  = ScriptFunctionImpl.makeFunction("loadWithNewGlobal", LOADWITHNEWGLOBAL);
        this.exit               = ScriptFunctionImpl.makeFunction("exit",       EXIT);
        this.quit               = ScriptFunctionImpl.makeFunction("quit",       EXIT);

        // built-in constructors
        this.builtinArray     = (ScriptFunction)initConstructor("Array");
        this.builtinBoolean   = (ScriptFunction)initConstructor("Boolean");
        this.builtinDate      = (ScriptFunction)initConstructor("Date");
        this.builtinJSON      = initConstructor("JSON");
        this.builtinJSAdapter = (ScriptFunction)initConstructor("JSAdapter");
        this.builtinMath      = initConstructor("Math");
        this.builtinNumber    = (ScriptFunction)initConstructor("Number");
        this.builtinRegExp    = (ScriptFunction)initConstructor("RegExp");
        this.builtinString    = (ScriptFunction)initConstructor("String");

        // initialize String.prototype.length to 0
        // add String.prototype.length
        final ScriptObject stringPrototype = getStringPrototype();
        stringPrototype.addOwnProperty("length", Attribute.NON_ENUMERABLE_CONSTANT, 0.0);

        // add Array.prototype.length
        final ScriptObject arrayPrototype = getArrayPrototype();
        arrayPrototype.addOwnProperty("length", Attribute.NOT_ENUMERABLE|Attribute.NOT_CONFIGURABLE, 0.0);

        this.DEFAULT_DATE = new NativeDate(Double.NaN);

        // initialize default regexp object
        this.DEFAULT_REGEXP = new NativeRegExp("(?:)");

        // RegExp.prototype should behave like a RegExp object. So copy the
        // properties.
        final ScriptObject regExpProto = getRegExpPrototype();
        regExpProto.addBoundProperties(DEFAULT_REGEXP);

        // Error stuff
        initErrorObjects();

        // java access
        initJavaAccess();

        initTypedArray();

        if (env._scripting) {
            initScripting();
        }

        if (Context.DEBUG && System.getSecurityManager() == null) {
            initDebug();
        }

        copyBuiltins();

        // initialized with strings so that typeof will work as expected.
        this.__FILE__ = "";
        this.__DIR__  = "";
        this.__LINE__ = 0.0;

        // expose script (command line) arguments as "arguments" property of global
        final List<String> arguments = env.getArguments();
        final Object argsObj = wrapAsObject(arguments.toArray());

        addOwnProperty("arguments", Attribute.NOT_ENUMERABLE, argsObj);
        if (env._scripting) {
            // synonym for "arguments" in scripting mode
            addOwnProperty("$ARG", Attribute.NOT_ENUMERABLE, argsObj);
        }
    }

    private void initErrorObjects() {
        // Error objects
        this.builtinError = (ScriptFunction)initConstructor("Error");
        final ScriptObject errorProto = getErrorPrototype();

        // Nashorn specific accessors on Error.prototype - stack, lineNumber, columnNumber and fileName
        final ScriptFunction getStack = ScriptFunctionImpl.makeFunction("getStack", NativeError.GET_STACK);
        final ScriptFunction setStack = ScriptFunctionImpl.makeFunction("setStack", NativeError.SET_STACK);
        errorProto.addOwnProperty("stack", Attribute.NOT_ENUMERABLE, getStack, setStack);
        final ScriptFunction getLineNumber = ScriptFunctionImpl.makeFunction("getLineNumber", NativeError.GET_LINENUMBER);
        final ScriptFunction setLineNumber = ScriptFunctionImpl.makeFunction("setLineNumber", NativeError.SET_LINENUMBER);
        errorProto.addOwnProperty("lineNumber", Attribute.NOT_ENUMERABLE, getLineNumber, setLineNumber);
        final ScriptFunction getColumnNumber = ScriptFunctionImpl.makeFunction("getColumnNumber", NativeError.GET_COLUMNNUMBER);
        final ScriptFunction setColumnNumber = ScriptFunctionImpl.makeFunction("setColumnNumber", NativeError.SET_COLUMNNUMBER);
        errorProto.addOwnProperty("columnNumber", Attribute.NOT_ENUMERABLE, getColumnNumber, setColumnNumber);
        final ScriptFunction getFileName = ScriptFunctionImpl.makeFunction("getFileName", NativeError.GET_FILENAME);
        final ScriptFunction setFileName = ScriptFunctionImpl.makeFunction("setFileName", NativeError.SET_FILENAME);
        errorProto.addOwnProperty("fileName", Attribute.NOT_ENUMERABLE, getFileName, setFileName);

        // ECMA 15.11.4.2 Error.prototype.name
        // Error.prototype.name = "Error";
        errorProto.set(NativeError.NAME, "Error", false);
        // ECMA 15.11.4.3 Error.prototype.message
        // Error.prototype.message = "";
        errorProto.set(NativeError.MESSAGE, "", false);

        this.builtinEvalError = initErrorSubtype("EvalError", errorProto);
        this.builtinRangeError = initErrorSubtype("RangeError", errorProto);
        this.builtinReferenceError = initErrorSubtype("ReferenceError", errorProto);
        this.builtinSyntaxError = initErrorSubtype("SyntaxError", errorProto);
        this.builtinTypeError = initErrorSubtype("TypeError", errorProto);
        this.builtinURIError = initErrorSubtype("URIError", errorProto);
    }

    private ScriptFunction initErrorSubtype(final String name, final ScriptObject errorProto) {
        final ScriptObject cons = initConstructor(name);
        final ScriptObject prototype = ScriptFunction.getPrototype(cons);
        prototype.set(NativeError.NAME, name, false);
        prototype.set(NativeError.MESSAGE, "", false);
        prototype.setProto(errorProto);
        return (ScriptFunction)cons;
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
        this.builtinJavaImporter = initConstructor("JavaImporter");
        this.builtinJavaApi = initConstructor("Java");
    }

    private void initScripting() {
        Object value;
        value = ScriptFunctionImpl.makeFunction("readLine", ScriptingFunctions.READLINE);
        addOwnProperty("readLine", Attribute.NOT_ENUMERABLE, value);

        value = ScriptFunctionImpl.makeFunction("readFully", ScriptingFunctions.READFULLY);
        addOwnProperty("readFully", Attribute.NOT_ENUMERABLE, value);

        final String execName = ScriptingFunctions.EXEC_NAME;
        value = ScriptFunctionImpl.makeFunction(execName, ScriptingFunctions.EXEC);
        addOwnProperty(execName, Attribute.NOT_ENUMERABLE, value);

        // Nashorn extension: global.echo (scripting-mode-only)
        // alias for "print"
        value = get("print");
        addOwnProperty("echo", Attribute.NOT_ENUMERABLE, value);

        // Nashorn extension: global.$OPTIONS (scripting-mode-only)
        final ScriptObject options = newObject();
        final ScriptEnvironment scriptEnv = context.getEnv();
        copyOptions(options, scriptEnv);
        addOwnProperty("$OPTIONS", Attribute.NOT_ENUMERABLE, options);

        // Nashorn extension: global.$ENV (scripting-mode-only)
        if (System.getSecurityManager() == null) {
            // do not fill $ENV if we have a security manager around
            // Retrieve current state of ENV variables.
            final ScriptObject env = newObject();
            env.putAll(System.getenv());
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
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                for (Field f : scriptEnv.getClass().getFields()) {
                    try {
                        options.set(f.getName(), f.get(scriptEnv), false);
                    } catch (final IllegalArgumentException | IllegalAccessException exp) {
                        throw new RuntimeException(exp);
                    }
                }
                return null;
            }
        });
    }

    private void initTypedArray() {
        this.builtinArrayBuffer       = initConstructor("ArrayBuffer");
        this.builtinInt8Array         = initConstructor("Int8Array");
        this.builtinUint8Array        = initConstructor("Uint8Array");
        this.builtinUint8ClampedArray = initConstructor("Uint8ClampedArray");
        this.builtinInt16Array        = initConstructor("Int16Array");
        this.builtinUint16Array       = initConstructor("Uint16Array");
        this.builtinInt32Array        = initConstructor("Int32Array");
        this.builtinUint32Array       = initConstructor("Uint32Array");
        this.builtinFloat32Array      = initConstructor("Float32Array");
        this.builtinFloat64Array      = initConstructor("Float64Array");
    }

    private void copyBuiltins() {
        this.array             = this.builtinArray;
        this._boolean          = this.builtinBoolean;
        this.date              = this.builtinDate;
        this.error             = this.builtinError;
        this.evalError         = this.builtinEvalError;
        this.function          = this.builtinFunction;
        this.jsadapter         = this.builtinJSAdapter;
        this.json              = this.builtinJSON;
        this.com               = this.builtinCom;
        this.edu               = this.builtinEdu;
        this.java              = this.builtinJava;
        this.javafx            = this.builtinJavafx;
        this.javax             = this.builtinJavax;
        this.org               = this.builtinOrg;
        this.javaImporter      = this.builtinJavaImporter;
        this.javaApi           = this.builtinJavaApi;
        this.math              = this.builtinMath;
        this.number            = this.builtinNumber;
        this.object            = this.builtinObject;
        this.packages          = this.builtinPackages;
        this.rangeError        = this.builtinRangeError;
        this.referenceError    = this.builtinReferenceError;
        this.regexp            = this.builtinRegExp;
        this.string            = this.builtinString;
        this.syntaxError       = this.builtinSyntaxError;
        this.typeError         = this.builtinTypeError;
        this.uriError          = this.builtinURIError;
        this.arrayBuffer       = this.builtinArrayBuffer;
        this.int8Array         = this.builtinInt8Array;
        this.uint8Array        = this.builtinUint8Array;
        this.uint8ClampedArray = this.builtinUint8ClampedArray;
        this.int16Array        = this.builtinInt16Array;
        this.uint16Array       = this.builtinUint16Array;
        this.int32Array        = this.builtinInt32Array;
        this.uint32Array       = this.builtinUint32Array;
        this.float32Array      = this.builtinFloat32Array;
        this.float64Array      = this.builtinFloat64Array;
    }

    private void initDebug() {
        this.addOwnProperty("Debug", Attribute.NOT_ENUMERABLE, initConstructor("Debug"));
    }

    @SuppressWarnings("resource")
    private static Object printImpl(final boolean newLine, final Object... objects) {
        final PrintWriter out = Global.getEnv().getOut();
        final StringBuilder sb = new StringBuilder();

        for (final Object object : objects) {
            if (sb.length() != 0) {
                sb.append(' ');
            }

            sb.append(JSType.toString(object));
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

    /**
     * These classes are generated by nasgen tool and so we have to use
     * reflection to load and create new instance of these classes.
     */
    private ScriptObject initConstructor(final String name) {
        try {
            // Assuming class name pattern for built-in JS constructors.
            final StringBuilder sb = new StringBuilder("jdk.nashorn.internal.objects.");

            sb.append("Native");
            sb.append(name);
            sb.append("$Constructor");

            final Class<?>     funcClass = Class.forName(sb.toString());
            final ScriptObject res       = (ScriptObject)funcClass.newInstance();

            if (res instanceof ScriptFunction) {
                // All global constructor prototypes are not-writable,
                // not-enumerable and not-configurable.
                final ScriptFunction func = (ScriptFunction)res;
                func.modifyOwnProperty(func.getProperty("prototype"), Attribute.NON_ENUMERABLE_CONSTANT);
            }

            if (res.getProto() == null) {
                res.setProto(getObjectPrototype());
            }

            return res;

        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
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
        this.builtinFunction = (ScriptFunction)initConstructor("Function");

        // create global anonymous function
        final ScriptFunction anon = ScriptFunctionImpl.newAnonymousFunction();
        // need to copy over members of Function.prototype to anon function
        anon.addBoundProperties(getFunctionPrototype());

        // Function.prototype === Object.getPrototypeOf(Function) ===
        // <anon-function>
        builtinFunction.setProto(anon);
        builtinFunction.setPrototype(anon);
        anon.set("constructor", builtinFunction, false);
        anon.deleteOwnProperty(anon.getMap().findProperty("prototype"));

        // now initialize Object
        this.builtinObject = (ScriptFunction)initConstructor("Object");
        final ScriptObject ObjectPrototype = getObjectPrototype();
        // Object.getPrototypeOf(Function.prototype) === Object.prototype
        anon.setProto(ObjectPrototype);

        // Function valued properties of Function.prototype were not properly
        // initialized. Because, these were created before global.function and
        // global.object were not initialized.
        jdk.nashorn.internal.runtime.Property[] properties = getFunctionPrototype().getMap().getProperties();
        for (final jdk.nashorn.internal.runtime.Property property : properties) {
            final Object key = property.getKey();
            final Object value = builtinFunction.get(key);

            if (value instanceof ScriptFunction && value != anon) {
                final ScriptFunction func = (ScriptFunction)value;
                func.setProto(getFunctionPrototype());
                final ScriptObject prototype = ScriptFunction.getPrototype(func);
                if (prototype != null) {
                    prototype.setProto(ObjectPrototype);
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
                    prototype.setProto(ObjectPrototype);
                }
            }
        }

        properties = getObjectPrototype().getMap().getProperties();
        for (final jdk.nashorn.internal.runtime.Property property : properties) {
            final Object key   = property.getKey();
            final Object value = ObjectPrototype.get(key);

            if (key.equals("constructor")) {
                continue;
            }

            if (value instanceof ScriptFunction) {
                final ScriptFunction func = (ScriptFunction)value;
                final ScriptObject prototype = ScriptFunction.getPrototype(func);
                if (prototype != null) {
                    prototype.setProto(ObjectPrototype);
                }
            }
        }
    }


    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        try {
            return MethodHandles.lookup().findStatic(Global.class, name, MH.type(rtype, types));
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new MethodHandleFactory.LookupException(e);
        }
    }

    RegExpResult getLastRegExpResult() {
        return lastRegExpResult;
    }

    void setLastRegExpResult(final RegExpResult regExpResult) {
        this.lastRegExpResult = regExpResult;
    }

}
