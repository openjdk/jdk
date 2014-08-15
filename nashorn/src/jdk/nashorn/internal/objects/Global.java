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

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.codegen.ApplySpecialization;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Context;
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
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;
import jdk.nashorn.internal.runtime.regexp.RegExpResult;
import jdk.nashorn.internal.scripts.JO;

/**
 * Representation of global scope.
 */
@ScriptClass("Global")
public final class Global extends ScriptObject implements Scope {
    // Placeholder value used in place of a location property (__FILE__, __DIR__, __LINE__)
    private static final Object LOCATION_PROPERTY_PLACEHOLDER = new Object();
    private final InvokeByName TO_STRING = new InvokeByName("toString", ScriptObject.class);
    private final InvokeByName VALUE_OF  = new InvokeByName("valueOf",  ScriptObject.class);

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
    public final Map<String, SwitchPoint> optimisticFunctionMap;

    /** Name invalidator for things like call/apply */
    public static final Call BOOTSTRAP = staticCall(MethodHandles.lookup(), Global.class, "invalidateNameBootstrap", CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);

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
    public final double NaN = Double.NaN;

    /** Value property Infinity of the Global Object - ECMA 15.1.1.2 Infinity */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public final double Infinity = Double.POSITIVE_INFINITY;

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

    /** DataView object */
    @Property(name = "DataView", attributes = Attribute.NOT_ENUMERABLE)
    public volatile Object dataView;

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
    public final Object __FILE__ = LOCATION_PROPERTY_PLACEHOLDER;

    /** Nashorn extension: current script's directory */
    @Property(name = "__DIR__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public final Object __DIR__ = LOCATION_PROPERTY_PLACEHOLDER;

    /** Nashorn extension: current source line number being executed */
    @Property(name = "__LINE__", attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public final Object __LINE__ = LOCATION_PROPERTY_PLACEHOLDER;

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

    /*
     * ECMA section 13.2.3 The [[ThrowTypeError]] Function Object
     */
    private ScriptFunction typeErrorThrower;

    // Flag to indicate that a split method issued a return statement
    private int splitState = -1;

    // Used to store the last RegExp result to support deprecated RegExp constructor properties
    private RegExpResult lastRegExpResult;

    private static final MethodHandle EVAL              = findOwnMH_S("eval",                Object.class, Object.class, Object.class);
    private static final MethodHandle NO_SUCH_PROPERTY  = findOwnMH_S(NO_SUCH_PROPERTY_NAME, Object.class, Object.class, Object.class);
    private static final MethodHandle PRINT             = findOwnMH_S("print",               Object.class, Object.class, Object[].class);
    private static final MethodHandle PRINTLN           = findOwnMH_S("println",             Object.class, Object.class, Object[].class);
    private static final MethodHandle LOAD              = findOwnMH_S("load",                Object.class, Object.class, Object.class);
    private static final MethodHandle LOADWITHNEWGLOBAL = findOwnMH_S("loadWithNewGlobal",   Object.class, Object.class, Object[].class);
    private static final MethodHandle EXIT              = findOwnMH_S("exit",                Object.class, Object.class, Object.class);

    /** Invalidate a reserved name, such as "apply" or "call" if assigned */
    public MethodHandle INVALIDATE_RESERVED_NAME = MH.bindTo(findOwnMH_V("invalidateReservedName", void.class, String.class), this);

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    // context to which this global belongs to
    private final Context context;

    // current ScriptContext to use - can be null.
    private ScriptContext scontext;
    // associated Property object for "context" property.
    private jdk.nashorn.internal.runtime.Property scontextProperty;

    /**
     * Set the current script context
     * @param scontext script context
     */
    public void setScriptContext(final ScriptContext scontext) {
        this.scontext = scontext;
        scontextProperty.setValue(this, this, scontext, false);
    }

    // global constants for this global - they can be replaced with MethodHandle.constant until invalidated
    private static AtomicReference<GlobalConstants> gcsInstance = new AtomicReference<>();

    @Override
    protected Context getContext() {
        return context;
    }

    // performs initialization checks for Global constructor and returns the
    // PropertyMap, if everything is fine.
    private static PropertyMap checkAndGetMap(final Context context) {
        // security check first
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(Context.NASHORN_CREATE_GLOBAL));
        }

        // null check on context
        context.getClass();

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
        this.setIsScope();
        this.optimisticFunctionMap = new HashMap<>();
        //we can only share one instance of Global constants between globals, or we consume way too much
        //memory - this is good enough for most programs
        while (gcsInstance.get() == null) {
            gcsInstance.compareAndSet(null, new GlobalConstants(context.getLogger(GlobalConstants.class)));
        }
    }

    /**
     * Script access to "current" Global instance
     *
     * @return the global singleton
     */
    public static Global instance() {
        final Global global = Context.getGlobal();
        global.getClass(); // null check
        return global;
    }

    private static Global instanceFrom(final Object self) {
        return self instanceof Global? (Global)self : instance();
    }

    /**
     * Return the global constants map for fields that
     * can be accessed as MethodHandle.constant
     * @return constant map
     */
    public static GlobalConstants getConstants() {
        return gcsInstance.get();
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
     * @param engine ScriptEngine to initialize
     */
    public void initBuiltinObjects(final ScriptEngine engine) {
        if (this.builtinObject != null) {
            // already initialized, just return
            return;
        }

        init(engine);
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
        } else if (obj instanceof String || obj instanceof ConsString) {
            return new NativeString((CharSequence)obj, this);
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

    /**
     * Lookup helper for JS primitive types
     *
     * @param request the link request for the dynamic call site.
     * @param self     self reference
     *
     * @return guarded invocation
     */
    public static GuardedInvocation primitiveLookup(final LinkRequest request, final Object self) {
        if (self instanceof String || self instanceof ConsString) {
            return NativeString.lookupPrimitive(request, self);
        } else if (self instanceof Number) {
            return NativeNumber.lookupPrimitive(request, self);
        } else if (self instanceof Boolean) {
            return NativeBoolean.lookupPrimitive(request, self);
        }
        throw new IllegalArgumentException("Unsupported primitive: " + self);
    }

    /**
     * Create a new empty script object
     *
     * @return the new ScriptObject
     */
    public ScriptObject newObject() {
        return new JO(getObjectPrototype(), JO.getInitialMap());
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

    private static <T> T getLazilyCreatedValue(final Object key, final Callable<T> creator, final Map<Object, T> map) {
        final T obj = map.get(key);
        if (obj != null) {
            return obj;
        }

        try {
            final T newObj = creator.call();
            final T existingObj = map.putIfAbsent(key, newObj);
            return existingObj != null ? existingObj : newObj;
        } catch (final Exception exp) {
            throw new RuntimeException(exp);
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
        final ScriptContext sctxt = global.scontext;
        final String nameStr = name.toString();

        if (sctxt != null) {
            final int scope = sctxt.getAttributesScope(nameStr);
            if (scope != -1) {
                return ScriptObjectMirror.unwrap(sctxt.getAttribute(nameStr, scope), global);
            }
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
        return directEval(self, str, UNDEFINED, UNDEFINED, false);
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
    public static Object directEval(final Object self, final Object str, final Object callThis, final Object location, final boolean strict) {
        if (!(str instanceof String || str instanceof ConsString)) {
            return str;
        }
        final Global global = Global.instanceFrom(self);
        final ScriptObject scope = self instanceof ScriptObject ? (ScriptObject)self : global;

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
        final Global global = Global.instanceFrom(self);
        final ScriptObject scope = self instanceof ScriptObject ? (ScriptObject)self : global;
        return global.getContext().load(scope, source);
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

    ScriptObject getDataViewPrototype() {
        return ScriptFunction.getPrototype(builtinDataView);
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

    ScriptFunction getTypeErrorThrower() {
        return typeErrorThrower;
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
        return placeholder == LOCATION_PROPERTY_PLACEHOLDER;
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

    private void init(final ScriptEngine engine) {
        assert Context.getGlobal() == this : "this global is not set as current";

        final ScriptEnvironment env = getContext().getEnv();

        // initialize Function and Object constructor
        initFunctionAndObject();

        // Now fix Global's own proto.
        this.setInitialProto(getObjectPrototype());

        // initialize global function properties
        this.eval = this.builtinEval = ScriptFunctionImpl.makeFunction("eval", EVAL);

        this.parseInt           = ScriptFunctionImpl.makeFunction("parseInt",   GlobalFunctions.PARSEINT,
                new MethodHandle[] { GlobalFunctions.PARSEINT_OI, GlobalFunctions.PARSEINT_O });
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
        this.builtinArray     = initConstructor("Array", ScriptFunction.class);
        this.builtinBoolean   = initConstructor("Boolean", ScriptFunction.class);
        this.builtinDate      = initConstructor("Date", ScriptFunction.class);
        this.builtinJSON      = initConstructor("JSON", ScriptObject.class);
        this.builtinJSAdapter = initConstructor("JSAdapter", ScriptFunction.class);
        this.builtinMath      = initConstructor("Math", ScriptObject.class);
        this.builtinNumber    = initConstructor("Number", ScriptFunction.class);
        this.builtinRegExp    = initConstructor("RegExp", ScriptFunction.class);
        this.builtinString    = initConstructor("String", ScriptFunction.class);

        // initialize String.prototype.length to 0
        // add String.prototype.length
        final ScriptObject stringPrototype = getStringPrototype();
        stringPrototype.addOwnProperty("length", Attribute.NON_ENUMERABLE_CONSTANT, 0.0);

        // set isArray flag on Array.prototype
        final ScriptObject arrayPrototype = getArrayPrototype();
        arrayPrototype.setIsArray();

        this.DEFAULT_DATE = new NativeDate(Double.NaN, this);

        // initialize default regexp object
        this.DEFAULT_REGEXP = new NativeRegExp("(?:)", this);

        // RegExp.prototype should behave like a RegExp object. So copy the
        // properties.
        final ScriptObject regExpProto = getRegExpPrototype();
        regExpProto.addBoundProperties(DEFAULT_REGEXP);

        // Error stuff
        initErrorObjects();

        // java access
        if (! env._no_java) {
            initJavaAccess();
        }

        if (! env._no_typed_arrays) {
            initTypedArray();
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

        if (engine != null) {
            final int NOT_ENUMERABLE_NOT_CONFIG = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE;
            scontextProperty = addOwnProperty("context", NOT_ENUMERABLE_NOT_CONFIG, null);
            addOwnProperty("engine", NOT_ENUMERABLE_NOT_CONFIG, engine);
            // default file name
            addOwnProperty(ScriptEngine.FILENAME, Attribute.NOT_ENUMERABLE, null);
            // __noSuchProperty__ hook for ScriptContext search of missing variables
            final ScriptFunction noSuchProp = ScriptFunctionImpl.makeStrictFunction(NO_SUCH_PROPERTY_NAME, NO_SUCH_PROPERTY);
            addOwnProperty(NO_SUCH_PROPERTY_NAME, Attribute.NOT_ENUMERABLE, noSuchProp);
        }
    }

    private void initErrorObjects() {
        // Error objects
        this.builtinError = initConstructor("Error", ScriptFunction.class);
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
        final ScriptFunction cons = initConstructor(name, ScriptFunction.class);
        final ScriptObject prototype = ScriptFunction.getPrototype(cons);
        prototype.set(NativeError.NAME, name, false);
        prototype.set(NativeError.MESSAGE, "", false);
        prototype.setInitialProto(errorProto);
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
        this.builtinJavaImporter = initConstructor("JavaImporter", ScriptFunction.class);
        this.builtinJavaApi = initConstructor("Java", ScriptObject.class);
    }

    private void initScripting(final ScriptEnvironment scriptEnv) {
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
        copyOptions(options, scriptEnv);
        addOwnProperty("$OPTIONS", Attribute.NOT_ENUMERABLE, options);

        // Nashorn extension: global.$ENV (scripting-mode-only)
        if (System.getSecurityManager() == null) {
            // do not fill $ENV if we have a security manager around
            // Retrieve current state of ENV variables.
            final ScriptObject env = newObject();
            env.putAll(System.getenv(), scriptEnv._strict);
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
                options.set(f.getName(), f.get(scriptEnv), false);
            } catch (final IllegalArgumentException | IllegalAccessException exp) {
                throw new RuntimeException(exp);
            }
        }
    }

    private void initTypedArray() {
        this.builtinArrayBuffer       = initConstructor("ArrayBuffer", ScriptFunction.class);
        this.builtinDataView          = initConstructor("DataView", ScriptFunction.class);
        this.builtinInt8Array         = initConstructor("Int8Array", ScriptFunction.class);
        this.builtinUint8Array        = initConstructor("Uint8Array", ScriptFunction.class);
        this.builtinUint8ClampedArray = initConstructor("Uint8ClampedArray", ScriptFunction.class);
        this.builtinInt16Array        = initConstructor("Int16Array", ScriptFunction.class);
        this.builtinUint16Array       = initConstructor("Uint16Array", ScriptFunction.class);
        this.builtinInt32Array        = initConstructor("Int32Array", ScriptFunction.class);
        this.builtinUint32Array       = initConstructor("Uint32Array", ScriptFunction.class);
        this.builtinFloat32Array      = initConstructor("Float32Array", ScriptFunction.class);
        this.builtinFloat64Array      = initConstructor("Float64Array", ScriptFunction.class);
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
        this.dataView          = this.builtinDataView;
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
        this.addOwnProperty("Debug", Attribute.NOT_ENUMERABLE, initConstructor("Debug", ScriptObject.class));
    }

    private Object printImpl(final boolean newLine, final Object... objects) {
        @SuppressWarnings("resource")
        final PrintWriter out = scontext != null? new PrintWriter(scontext.getWriter()) : getContext().getEnv().getOut();
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

    /**
     * These classes are generated by nasgen tool and so we have to use
     * reflection to load and create new instance of these classes.
     */
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

    // Function and Object constructors are inter-dependent. Also,
    // Function.prototype
    // functions are not properly initialized. We fix the references here.
    // NOTE: be careful if you want to re-order the operations here. You may
    // have
    // to play with object references carefully!!
    private void initFunctionAndObject() {
        // First-n-foremost is Function
        this.builtinFunction      = initConstructor("Function", ScriptFunction.class);

        // create global anonymous function
        final ScriptFunction anon = ScriptFunctionImpl.newAnonymousFunction();
        // need to copy over members of Function.prototype to anon function
        anon.addBoundProperties(getFunctionPrototype());

        // Function.prototype === Object.getPrototypeOf(Function) ===
        // <anon-function>
        builtinFunction.setInitialProto(anon);
        builtinFunction.setPrototype(anon);
        anon.set("constructor", builtinFunction, false);
        anon.deleteOwnProperty(anon.getMap().findProperty("prototype"));

        // use "getter" so that [[ThrowTypeError]] function's arity is 0 - as specified in step 10 of section 13.2.3
        this.typeErrorThrower = new ScriptFunctionImpl("TypeErrorThrower", Lookup.TYPE_ERROR_THROWER_GETTER, null, null, 0);
        typeErrorThrower.setPrototype(UNDEFINED);
        // Non-constructor built-in functions do not have "prototype" property
        typeErrorThrower.deleteOwnProperty(typeErrorThrower.getMap().findProperty("prototype"));
        typeErrorThrower.preventExtensions();

        // now initialize Object
        this.builtinObject = initConstructor("Object", ScriptFunction.class);
        final ScriptObject ObjectPrototype = getObjectPrototype();
        // Object.getPrototypeOf(Function.prototype) === Object.prototype
        anon.setInitialProto(ObjectPrototype);

        // ES6 draft compliant __proto__ property of Object.prototype
        // accessors on Object.prototype for "__proto__"
        final ScriptFunction getProto = ScriptFunctionImpl.makeFunction("getProto", NativeObject.GET__PROTO__);
        final ScriptFunction setProto = ScriptFunctionImpl.makeFunction("setProto", NativeObject.SET__PROTO__);
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

        //make sure apply and call have the same invalidation switchpoint
        final SwitchPoint sp = new SwitchPoint();
        optimisticFunctionMap.put("apply", sp);
        optimisticFunctionMap.put("call", sp);
        getFunctionPrototype().getProperty("apply").setChangeCallback(sp);
        getFunctionPrototype().getProperty("call").setChangeCallback(sp);

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
    }

    private static MethodHandle findOwnMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), Global.class, name, MH.type(rtype, types));
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
     * Check if there is a switchpoint for a reserved name. If there
     * is, it must be invalidated upon properties with this name
     * @param name property name
     * @return switchpoint for invalidating this property, or null if not registered
     */
    public SwitchPoint getChangeCallback(final String name) {
        return optimisticFunctionMap.get(name);
    }

    /**
     * Is this a special name, that might be subject to invalidation
     * on write, such as "apply" or "call"
     * @param name name to check
     * @return true if special name
     */
    public boolean isSpecialName(final String name) {
        return getChangeCallback(name) != null;
    }

    /**
     * Check if a reserved property name is invalidated
     * @param name property name
     * @return true if someone has written to it since Global was instantiated
     */
    public boolean isSpecialNameValid(final String name) {
        final SwitchPoint sp = getChangeCallback(name);
        return sp != null && !sp.hasBeenInvalidated();
    }

    /**
     * Tag a reserved name as invalidated - used when someone writes
     * to a property with this name - overly conservative, but link time
     * is too late to apply e.g. apply-&gt;call specialization
     * @param name property name
     */
    public void invalidateReservedName(final String name) {
        final SwitchPoint sp = getChangeCallback(name);
        if (sp != null) {
            getContext().getLogger(ApplySpecialization.class).info("Overwrote special name '" + name +"' - invalidating switchpoint");
            SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
        }
    }

    /**
     * Bootstrapper for invalidating a builtin name
     * @param lookup lookup
     * @param name   name to invalidate
     * @param type   methodhandle type
     * @return callsite for invalidator
     */
    public static CallSite invalidateNameBootstrap(final MethodHandles.Lookup lookup, final String name, final MethodType type) {
        final MethodHandle target = MH.insertArguments(Global.instance().INVALIDATE_RESERVED_NAME, 0, name);
        return new ConstantCallSite(target);
    }


}
