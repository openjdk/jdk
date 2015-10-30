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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.internal.dynalink.linker.support.TypeUtilities;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ListAdapter;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.JavaAdapterFactory;

/**
 * This class is the implementation for the {@code Java} global object exposed to programs running under Nashorn. This
 * object acts as the API entry point to Java platform specific functionality, dealing with creating new instances of
 * Java classes, subclassing Java classes, implementing Java interfaces, converting between Java arrays and ECMAScript
 * arrays, and so forth.
 */
@ScriptClass("Java")
public final class NativeJava {

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeJava() {
        // don't create me
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the specified object is a Java type object, that is an instance of {@link StaticClass}.
     * @param self not used
     * @param type the object that is checked if it is a type object or not
     * @return tells whether given object is a Java type object or not.
     * @see #type(Object, Object)
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isType(final Object self, final Object type) {
        return type instanceof StaticClass;
    }

    /**
     * Returns synchronized wrapper version of the given ECMAScript function.
     * @param self not used
     * @param func the ECMAScript function whose synchronized version is returned.
     * @param obj the object (i.e, lock) on which the function synchronizes.
     * @return synchronized wrapper version of the given ECMAScript function.
     */
    @Function(name="synchronized", attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object synchronizedFunc(final Object self, final Object func, final Object obj) {
        if (func instanceof ScriptFunction) {
            return ((ScriptFunction)func).createSynchronized(obj);
        }

        throw typeError("not.a.function", ScriptRuntime.safeToString(func));
    }

    /**
     * Returns true if the specified object is a Java method.
     * @param self not used
     * @param obj the object that is checked if it is a Java method object or not
     * @return tells whether given object is a Java method object or not.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isJavaMethod(final Object self, final Object obj) {
        return Bootstrap.isDynamicMethod(obj);
    }

    /**
     * Returns true if the specified object is a java function (but not script function)
     * @param self not used
     * @param obj the object that is checked if it is a Java function or not
     * @return tells whether given object is a Java function or not
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isJavaFunction(final Object self, final Object obj) {
        return Bootstrap.isCallable(obj) && !(obj instanceof ScriptFunction);
    }

    /**
     * Returns true if the specified object is a Java object but not a script object
     * @param self not used
     * @param obj the object that is checked
     * @return tells whether given object is a Java object but not a script object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isJavaObject(final Object self, final Object obj) {
        return obj != null && !(obj instanceof ScriptObject);
    }

    /**
     * Returns true if the specified object is a ECMAScript object, that is an instance of {@link ScriptObject}.
     * @param self not used
     * @param obj the object that is checked if it is a ECMAScript object or not
     * @return tells whether given object is a ECMAScript object or not.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isScriptObject(final Object self, final Object obj) {
        return obj instanceof ScriptObject;
    }

    /**
     * Returns true if the specified object is a ECMAScript function, that is an instance of {@link ScriptFunction}.
     * @param self not used
     * @param obj the object that is checked if it is a ECMAScript function or not
     * @return tells whether given object is a ECMAScript function or not.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isScriptFunction(final Object self, final Object obj) {
        return obj instanceof ScriptFunction;
    }

    /**
     * <p>
     * Given a name of a Java type, returns an object representing that type in Nashorn. The Java class of the objects
     * used to represent Java types in Nashorn is not {@link java.lang.Class} but rather {@link StaticClass}. They are
     * the objects that you can use with the {@code new} operator to create new instances of the class as well as to
     * access static members of the class. In Nashorn, {@code Class} objects are just regular Java objects that aren't
     * treated specially. Instead of them, {@link StaticClass} instances - which we sometimes refer to as "Java type
     * objects" are used as constructors with the {@code new} operator, and they expose static fields, properties, and
     * methods. While this might seem confusing at first, it actually closely matches the Java language: you use a
     * different expression (e.g. {@code java.io.File}) as an argument in "new" and to address statics, and it is
     * distinct from the {@code Class} object (e.g. {@code java.io.File.class}). Below we cover in details the
     * properties of the type objects.
     * </p>
     * <p><b>Constructing Java objects</b></p>
     * Examples:
     * <pre>
     * var arrayListType = Java.type("java.util.ArrayList")
     * var intType = Java.type("int")
     * var stringArrayType = Java.type("java.lang.String[]")
     * var int2DArrayType = Java.type("int[][]")
     * </pre>
     * Note that the name of the type is always a string for a fully qualified name. You can use any of these types to
     * create new instances, e.g.:
     * <pre>
     * var anArrayList = new Java.type("java.util.ArrayList")
     * </pre>
     * or
     * <pre>
     * var ArrayList = Java.type("java.util.ArrayList")
     * var anArrayList = new ArrayList
     * var anArrayListWithSize = new ArrayList(16)
     * </pre>
     * In the special case of inner classes, you can either use the JVM fully qualified name, meaning using {@code $}
     * sign in the class name, or you can use the dot:
     * <pre>
     * var ftype = Java.type("java.awt.geom.Arc2D$Float")
     * </pre>
     * and
     * <pre>
     * var ftype = Java.type("java.awt.geom.Arc2D.Float")
     * </pre>
     * both work. Note however that using the dollar sign is faster, as Java.type first tries to resolve the class name
     * as it is originally specified, and the internal JVM names for inner classes use the dollar sign. If you use the
     * dot, Java.type will internally get a ClassNotFoundException and subsequently retry by changing the last dot to
     * dollar sign. As a matter of fact, it'll keep replacing dots with dollar signs until it either successfully loads
     * the class or runs out of all dots in the name. This way it can correctly resolve and load even multiply nested
     * inner classes with the dot notation. Again, this will be slower than using the dollar signs in the name. An
     * alternative way to access the inner class is as a property of the outer class:
     * <pre>
     * var arctype = Java.type("java.awt.geom.Arc2D")
     * var ftype = arctype.Float
     * </pre>
     * <p>
     * You can access both static and non-static inner classes. If you want to create an instance of a non-static
     * inner class, remember to pass an instance of its outer class as the first argument to the constructor.
     * </p>
     * <p>
     * If the type is abstract, you can instantiate an anonymous subclass of it using an argument list that is
     * applicable to any of its public or protected constructors, but inserting a JavaScript object with functions
     * properties that provide JavaScript implementations of the abstract methods. If method names are overloaded, the
     * JavaScript function will provide implementation for all overloads. E.g.:
     * </p>
     * <pre>
     * var TimerTask =  Java.type("java.util.TimerTask")
     * var task = new TimerTask({ run: function() { print("Hello World!") } })
     * </pre>
     * <p>
     * Nashorn supports a syntactic extension where a "new" expression followed by an argument is identical to
     * invoking the constructor and passing the argument to it, so you can write the above example also as:
     * </p>
     * <pre>
     * var task = new TimerTask {
     *     run: function() {
     *       print("Hello World!")
     *     }
     * }
     * </pre>
     * <p>
     * which is very similar to Java anonymous inner class definition. On the other hand, if the type is an abstract
     * type with a single abstract method (commonly referred to as a "SAM type") or all abstract methods it has share
     * the same overloaded name), then instead of an object, you can just pass a function, so the above example can
     * become even more simplified to:
     * </p>
     * <pre>
     * var task = new TimerTask(function() { print("Hello World!") })
     * </pre>
     * <p>
     * Note that in every one of these cases if you are trying to instantiate an abstract class that has constructors
     * that take some arguments, you can invoke those simply by specifying the arguments after the initial
     * implementation object or function.
     * </p>
     * <p>The use of functions can be taken even further; if you are invoking a Java method that takes a SAM type,
     * you can just pass in a function object, and Nashorn will know what you meant:
     * </p>
     * <pre>
     * var timer = new Java.type("java.util.Timer")
     * timer.schedule(function() { print("Hello World!") })
     * </pre>
     * <p>
     * Here, {@code Timer.schedule()} expects a {@code TimerTask} as its argument, so Nashorn creates an instance of a
     * {@code TimerTask} subclass and uses the passed function to implement its only abstract method, {@code run()}. In
     * this usage though, you can't use non-default constructors; the type must be either an interface, or must have a
     * protected or public no-arg constructor.
     * </p>
     * <p>
     * You can also subclass non-abstract classes; for that you will need to use the {@link #extend(Object, Object...)}
     * method.
     * </p>
     * <p><b>Accessing static members</b></p>
     * Examples:
     * <pre>
     * var File = Java.type("java.io.File")
     * var pathSep = File.pathSeparator
     * var tmpFile1 = File.createTempFile("abcdefg", ".tmp")
     * var tmpFile2 = File.createTempFile("abcdefg", ".tmp", new File("/tmp"))
     * </pre>
     * Actually, you can even assign static methods to variables, so the above example can be rewritten as:
     * <pre>
     * var File = Java.type("java.io.File")
     * var createTempFile = File.createTempFile
     * var tmpFile1 = createTempFile("abcdefg", ".tmp")
     * var tmpFile2 = createTempFile("abcdefg", ".tmp", new File("/tmp"))
     * </pre>
     * If you need to access the actual {@code java.lang.Class} object for the type, you can use the {@code class}
     * property on the object representing the type:
     * <pre>
     * var File = Java.type("java.io.File")
     * var someFile = new File("blah")
     * print(File.class === someFile.getClass()) // prints true
     * </pre>
     * Of course, you can also use the {@code getClass()} method or its equivalent {@code class} property on any
     * instance of the class. Other way round, you can use the synthetic {@code static} property on any
     * {@code java.lang.Class} object to retrieve its type-representing object:
     * <pre>
     * var File = Java.type("java.io.File")
     * print(File.class.static === File) // prints true
     * </pre>
     * <p><b>{@code instanceof} operator</b></p>
     * The standard ECMAScript {@code instanceof} operator is extended to recognize Java objects and their type objects:
     * <pre>
     * var File = Java.type("java.io.File")
     * var aFile = new File("foo")
     * print(aFile instanceof File) // prints true
     * print(aFile instanceof File.class) // prints false - Class objects aren't type objects.
     * </pre>
     * @param self not used
     * @param objTypeName the object whose JS string value represents the type name. You can use names of primitive Java
     * types to obtain representations of them, and you can use trailing square brackets to represent Java array types.
     * @return the object representing the named type
     * @throws ClassNotFoundException if the class is not found
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object type(final Object self, final Object objTypeName) throws ClassNotFoundException {
        return type(objTypeName);
    }

    private static StaticClass type(final Object objTypeName) throws ClassNotFoundException {
        return StaticClass.forClass(type(JSType.toString(objTypeName)));
    }

    private static Class<?> type(final String typeName) throws ClassNotFoundException {
        if (typeName.endsWith("[]")) {
            return arrayType(typeName);
        }

        return simpleType(typeName);
    }

    /**
     * Returns name of a java type {@link StaticClass}.
     * @param self not used
     * @param type the type whose name is returned
     * @return name of the given type
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object typeName(final Object self, final Object type) {
        if (type instanceof StaticClass) {
            return ((StaticClass)type).getRepresentedClass().getName();
        } else if (type instanceof Class) {
            return ((Class<?>)type).getName();
        } else {
            return UNDEFINED;
        }
    }

    /**
     * Given a script object and a Java type, converts the script object into the desired Java type. Currently it
     * performs shallow creation of Java arrays, as well as wrapping of objects in Lists, Dequeues, Queues,
     * and Collections. If conversion is not possible or fails for some reason, TypeError is thrown.
     * Example:
     * <pre>
     * var anArray = [1, "13", false]
     * var javaIntArray = Java.to(anArray, "int[]")
     * print(javaIntArray[0]) // prints 1
     * print(javaIntArray[1]) // prints 13, as string "13" was converted to number 13 as per ECMAScript ToNumber conversion
     * print(javaIntArray[2]) // prints 0, as boolean false was converted to number 0 as per ECMAScript ToNumber conversion
     * </pre>
     * @param self not used
     * @param obj the script object. Can be null.
     * @param objType either a {@link #type(Object, Object) type object} or a String describing the type of the Java
     * object to create. Can not be null. If undefined, a "default" conversion is presumed (allowing the argument to be
     * omitted).
     * @return a Java object whose value corresponds to the original script object's value. Specifically, for array
     * target types, returns a Java array of the same type with contents converted to the array's component type.
     * Converts recursively when the target type is multidimensional array. For {@link List}, {@link Deque},
     * {@link Queue}, or {@link Collection}, returns a live wrapper around the object, see {@link ListAdapter} for
     * details. Returns null if obj is null.
     * @throws ClassNotFoundException if the class described by objType is not found
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object to(final Object self, final Object obj, final Object objType) throws ClassNotFoundException {
        if (obj == null) {
            return null;
        }

        if (!(obj instanceof ScriptObject) && !(obj instanceof JSObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }

        final Class<?> targetClass;
        if(objType == UNDEFINED) {
            targetClass = Object[].class;
        } else {
            final StaticClass targetType;
            if(objType instanceof StaticClass) {
                targetType = (StaticClass)objType;
            } else {
                targetType = type(objType);
            }
            targetClass = targetType.getRepresentedClass();
        }

        if(targetClass.isArray()) {
            try {
                return JSType.toJavaArray(obj, targetClass.getComponentType());
            } catch (final Exception exp) {
                throw typeError(exp, "java.array.conversion.failed", targetClass.getName());
            }
        }

        if (targetClass == List.class || targetClass == Deque.class || targetClass == Queue.class || targetClass == Collection.class) {
            return ListAdapter.create(obj);
        }

        throw typeError("unsupported.java.to.type", targetClass.getName());
    }

    /**
     * Given a Java array or {@link Collection}, returns a JavaScript array with a shallow copy of its contents. Note
     * that in most cases, you can use Java arrays and lists natively in Nashorn; in cases where for some reason you
     * need to have an actual JavaScript native array (e.g. to work with the array comprehensions functions), you will
     * want to use this method. Example:
     * <pre>
     * var File = Java.type("java.io.File")
     * var listHomeDir = new File("~").listFiles()
     * var jsListHome = Java.from(listHomeDir)
     * var jpegModifiedDates = jsListHome
     *     .filter(function(val) { return val.getName().endsWith(".jpg") })
     *     .map(function(val) { return val.lastModified() })
     * </pre>
     * @param self not used
     * @param objArray the java array or collection. Can be null.
     * @return a JavaScript array with the copy of Java array's or collection's contents. Returns null if objArray is
     * null.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static NativeArray from(final Object self, final Object objArray) {
        if (objArray == null) {
            return null;
        } else if (objArray instanceof Collection) {
            return new NativeArray(((Collection<?>)objArray).toArray());
        } else if (objArray instanceof Object[]) {
            return new NativeArray(((Object[])objArray).clone());
        } else if (objArray instanceof int[]) {
            return new NativeArray(((int[])objArray).clone());
        } else if (objArray instanceof double[]) {
            return new NativeArray(((double[])objArray).clone());
        } else if (objArray instanceof long[]) {
            return new NativeArray(((long[])objArray).clone());
        } else if (objArray instanceof byte[]) {
            return new NativeArray(copyArray((byte[])objArray));
        } else if (objArray instanceof short[]) {
            return new NativeArray(copyArray((short[])objArray));
        } else if (objArray instanceof char[]) {
            return new NativeArray(copyArray((char[])objArray));
        } else if (objArray instanceof float[]) {
            return new NativeArray(copyArray((float[])objArray));
        } else if (objArray instanceof boolean[]) {
            return new NativeArray(copyArray((boolean[])objArray));
        }

        throw typeError("cant.convert.to.javascript.array", objArray.getClass().getName());
    }

    /**
     * Return properties of the given object. Properties also include "method names".
     * This is meant for source code completion in interactive shells or editors.
     *
     * @param object the object whose properties are returned.
     * @return list of properties
     */
    public static List<String> getProperties(final Object object) {
        if (object instanceof StaticClass) {
            // static properties of the given class
            final Class<?> clazz = ((StaticClass)object).getRepresentedClass();
            final ArrayList<String> props = new ArrayList<>();
            try {
                Bootstrap.checkReflectionAccess(clazz, true);
                // Usually writable properties are a subset as 'write-only' properties are rare
                props.addAll(BeansLinker.getReadableStaticPropertyNames(clazz));
                props.addAll(BeansLinker.getStaticMethodNames(clazz));
            } catch (Exception ignored) {}
            return props;
        } else if (object instanceof JSObject) {
            final JSObject jsObj = ((JSObject)object);
            final ArrayList<String> props = new ArrayList<>();
            props.addAll(jsObj.keySet());
            return props;
        } else if (object != null && object != UNDEFINED) {
            // instance properties of the given object
            final Class<?> clazz = object.getClass();
            final ArrayList<String> props = new ArrayList<>();
            try {
                Bootstrap.checkReflectionAccess(clazz, false);
                // Usually writable properties are a subset as 'write-only' properties are rare
                props.addAll(BeansLinker.getReadableInstancePropertyNames(clazz));
                props.addAll(BeansLinker.getInstanceMethodNames(clazz));
            } catch (Exception ignored) {}
            return props;
        }

        // don't know about that object
        return Collections.<String>emptyList();
    }

    private static int[] copyArray(final byte[] in) {
        final int[] out = new int[in.length];
        for(int i = 0; i < in.length; ++i) {
            out[i] = in[i];
        }
        return out;
    }

    private static int[] copyArray(final short[] in) {
        final int[] out = new int[in.length];
        for(int i = 0; i < in.length; ++i) {
            out[i] = in[i];
        }
        return out;
    }

    private static int[] copyArray(final char[] in) {
        final int[] out = new int[in.length];
        for(int i = 0; i < in.length; ++i) {
            out[i] = in[i];
        }
        return out;
    }

    private static double[] copyArray(final float[] in) {
        final double[] out = new double[in.length];
        for(int i = 0; i < in.length; ++i) {
            out[i] = in[i];
        }
        return out;
    }

    private static Object[] copyArray(final boolean[] in) {
        final Object[] out = new Object[in.length];
        for(int i = 0; i < in.length; ++i) {
            out[i] = in[i];
        }
        return out;
    }

    private static Class<?> simpleType(final String typeName) throws ClassNotFoundException {
        final Class<?> primClass = TypeUtilities.getPrimitiveTypeByName(typeName);
        if(primClass != null) {
            return primClass;
        }
        final Context ctx = Global.getThisContext();
        try {
            return ctx.findClass(typeName);
        } catch(final ClassNotFoundException e) {
            // The logic below compensates for a frequent user error - when people use dot notation to separate inner
            // class names, i.e. "java.lang.Character.UnicodeBlock" vs."java.lang.Character$UnicodeBlock". The logic
            // below will try alternative class names, replacing dots at the end of the name with dollar signs.
            final StringBuilder nextName = new StringBuilder(typeName);
            int lastDot = nextName.length();
            for(;;) {
                lastDot = nextName.lastIndexOf(".", lastDot - 1);
                if(lastDot == -1) {
                    // Exhausted the search space, class not found - rethrow the original exception.
                    throw e;
                }
                nextName.setCharAt(lastDot, '$');
                try {
                    return ctx.findClass(nextName.toString());
                } catch(final ClassNotFoundException cnfe) {
                    // Intentionally ignored, so the loop retries with the next name
                }
            }
        }

    }

    private static Class<?> arrayType(final String typeName) throws ClassNotFoundException {
        return Array.newInstance(type(typeName.substring(0, typeName.length() - 2)), 0).getClass();
    }

    /**
     * Returns a type object for a subclass of the specified Java class (or implementation of the specified interface)
     * that acts as a script-to-Java adapter for it. See {@link #type(Object, Object)} for a discussion of type objects,
     * and see {@link JavaAdapterFactory} for details on script-to-Java adapters. Note that you can also implement
     * interfaces and subclass abstract classes using {@code new} operator on a type object for an interface or abstract
     * class. However, to extend a non-abstract class, you will have to use this method. Example:
     * <pre>
     * var ArrayList = Java.type("java.util.ArrayList")
     * var ArrayListExtender = Java.extend(ArrayList)
     * var printSizeInvokedArrayList = new ArrayListExtender() {
     *     size: function() { print("size invoked!"); }
     * }
     * var printAddInvokedArrayList = new ArrayListExtender() {
     *     add: function(x, y) {
     *       if(typeof(y) === "undefined") {
     *           print("add(e) invoked!");
     *       } else {
     *           print("add(i, e) invoked!");
     *       }
     * }
     * </pre>
     * We can see several important concepts in the above example:
     * <ul>
     * <li>Every specified list of Java types will have one extender subclass in Nashorn per caller protection domain -
     * repeated invocations of {@code extend} for the same list of types for scripts same protection domain will yield
     * the same extender type. It's a generic adapter that delegates to whatever JavaScript functions its implementation
     * object has on a per-instance basis.</li>
     * <li>If the Java method is overloaded (as in the above example {@code List.add()}), then your JavaScript adapter
     * must be prepared to deal with all overloads.</li>
     * <li>To invoke super methods from adapters, call them on the adapter instance prefixing them with {@code super$},
     * or use the special {@link #_super(Object, Object) super-adapter}.</li>
     * <li>It is also possible to specify an ordinary JavaScript object as the last argument to {@code extend}. In that
     * case, it is treated as a class-level override. {@code extend} will return an extender class where all instances
     * will have the methods implemented by functions on that object, just as if that object were passed as the last
     * argument to their constructor. Example:
     * <pre>
     * var Runnable = Java.type("java.lang.Runnable")
     * var R1 = Java.extend(Runnable, {
     *     run: function() {
     *         print("R1.run() invoked!")
     *     }
     * })
     * var r1 = new R1
     * var t = new java.lang.Thread(r1)
     * t.start()
     * t.join()
     * </pre>
     * As you can see, you don't have to pass any object when you create a new instance of {@code R1} as its
     * {@code run()} function was defined already when extending the class. If you also want to add instance-level
     * overrides on these objects, you will have to repeatedly use {@code extend()} to subclass the class-level adapter.
     * For such adapters, the order of precedence is instance-level method, class-level method, superclass method, or
     * {@code UnsupportedOperationException} if the superclass method is abstract. If we continue our previous example:
     * <pre>
     * var R2 = Java.extend(R1);
     * var r2 = new R2(function() { print("r2.run() invoked!") })
     * r2.run()
     * </pre>
     * We'll see it'll print {@code "r2.run() invoked!"}, thus overriding on instance-level the class-level behavior.
     * Note that you must use {@code Java.extend} to explicitly create an instance-override adapter class from a
     * class-override adapter class, as the class-override adapter class is no longer abstract.
     * </li>
     * </ul>
     * @param self not used
     * @param types the original types. The caller must pass at least one Java type object of class {@link StaticClass}
     * representing either a public interface or a non-final public class with at least one public or protected
     * constructor. If more than one type is specified, at most one can be a class and the rest have to be interfaces.
     * Invoking the method twice with exactly the same types in the same order - in absence of class-level overrides -
     * will return the same adapter class, any reordering of types or even addition or removal of redundant types (i.e.
     * interfaces that other types in the list already implement/extend, or {@code java.lang.Object} in a list of types
     * consisting purely of interfaces) will result in a different adapter class, even though those adapter classes are
     * functionally identical; we deliberately don't want to incur the additional processing cost of canonicalizing type
     * lists. As a special case, the last argument can be a {@code ScriptObject} instead of a type. In this case, a
     * separate adapter class is generated - new one for each invocation - that will use the passed script object as its
     * implementation for all instances. Instances of such adapter classes can then be created without passing another
     * script object in the constructor, as the class has a class-level behavior defined by the script object. However,
     * you can still pass a script object (or if it's a SAM type, a function) to the constructor to provide further
     * instance-level overrides.
     *
     * @return a new {@link StaticClass} that represents the adapter for the original types.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object extend(final Object self, final Object... types) {
        if(types == null || types.length == 0) {
            throw typeError("extend.expects.at.least.one.argument");
        }
        final int l = types.length;
        final int typesLen;
        final ScriptObject classOverrides;
        if(types[l - 1] instanceof ScriptObject) {
            classOverrides = (ScriptObject)types[l - 1];
            typesLen = l - 1;
            if(typesLen == 0) {
                throw typeError("extend.expects.at.least.one.type.argument");
            }
        } else {
            classOverrides = null;
            typesLen = l;
        }
        final Class<?>[] stypes = new Class<?>[typesLen];
        try {
            for(int i = 0; i < typesLen; ++i) {
                stypes[i] = ((StaticClass)types[i]).getRepresentedClass();
            }
        } catch(final ClassCastException e) {
            throw typeError("extend.expects.java.types");
        }
        // Note that while the public API documentation claims self is not used, we actually use it.
        // ScriptFunction.findCallMethod will bind the lookup object into it, and we can then use that lookup when
        // requesting the adapter class. Note that if Java.extend is invoked with no lookup object, it'll pass the
        // public lookup which'll result in generation of a no-permissions adapter. A typical situation this can happen
        // is when the extend function is bound.
        final MethodHandles.Lookup lookup;
        if(self instanceof MethodHandles.Lookup) {
            lookup = (MethodHandles.Lookup)self;
        } else {
            lookup = MethodHandles.publicLookup();
        }
        return JavaAdapterFactory.getAdapterClassFor(stypes, classOverrides, lookup);
    }

    /**
     * When given an object created using {@code Java.extend()} or equivalent mechanism (that is, any JavaScript-to-Java
     * adapter), returns an object that can be used to invoke superclass methods on that object. E.g.:
     * <pre>
     * var cw = new FilterWriterAdapter(sw) {
     *     write: function(s, off, len) {
     *         s = capitalize(s, off, len)
     *         cw_super.write(s, 0, s.length())
     *     }
     * }
     * var cw_super = Java.super(cw)
     * </pre>
     * @param self the {@code Java} object itself - not used.
     * @param adapter the original Java adapter instance for which the super adapter is created.
     * @return a super adapter for the original adapter
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, name="super")
    public static Object _super(final Object self, final Object adapter) {
        return Bootstrap.createSuperAdapter(adapter);
    }

    /**
     * Returns an object that is compatible with Java JSON libraries expectations; namely, that if it itself, or any
     * object transitively reachable through it is a JavaScript array, then such objects will be exposed as
     * {@link JSObject} that also implements the {@link List} interface for exposing the array elements. An explicit
     * API is required as otherwise Nashorn exposes all objects externally as {@link JSObject}s that also implement the
     * {@link Map} interface instead. By using this method, arrays will be exposed as {@link List}s and all other
     * objects as {@link Map}s.
     * @param self not used
     * @param obj the object to be exposed in a Java JSON library compatible manner.
     * @return a wrapper around the object that will enforce Java JSON library compatible exposure.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object asJSONCompatible(final Object self, final Object obj) {
        return ScriptObjectMirror.wrapAsJSONCompatible(obj, Context.getGlobal());
    }
}
