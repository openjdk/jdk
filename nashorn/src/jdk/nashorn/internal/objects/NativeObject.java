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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.internal.dynalink.support.LinkRequestImpl;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * ECMA 15.2 Object objects
 *
 * JavaScript Object constructor/prototype. Note: instances of this class are
 * never created. This class is not even a subclass of ScriptObject. But, we use
 * this class to generate prototype and constructor for "Object".
 *
 */
@ScriptClass("Object")
public final class NativeObject {
    private static final Object TO_STRING = new Object();

    private static InvokeByName getTO_STRING() {
        return Global.instance().getInvokeByName(TO_STRING,
                new Callable<InvokeByName>() {
                    @Override
                    public InvokeByName call() {
                        return new InvokeByName("toString", ScriptObject.class);
                    }
                });
    }

    private static final MethodType MIRROR_GETTER_TYPE = MethodType.methodType(Object.class, ScriptObjectMirror.class);
    private static final MethodType MIRROR_SETTER_TYPE = MethodType.methodType(Object.class, ScriptObjectMirror.class, Object.class);

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeObject() {
        // don't create me!
        throw new UnsupportedOperationException();
    }

    private static ECMAException notAnObject(final Object obj) {
        return typeError("not.an.object", ScriptRuntime.safeToString(obj));
    }

    /**
     * ECMA 15.2.3.2 Object.getPrototypeOf ( O )
     *
     * @param  self self reference
     * @param  obj object to get prototype from
     * @return the prototype of an object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object getPrototypeOf(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).getProto();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).getProto();
        } else {
            final JSType type = JSType.of(obj);
            if (type == JSType.OBJECT) {
                // host (Java) objects have null __proto__
                return null;
            }

            // must be some JS primitive
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.3 Object.getOwnPropertyDescriptor ( O, P )
     *
     * @param self  self reference
     * @param obj   object from which to get property descriptor for {@code ToString(prop)}
     * @param prop  property descriptor
     * @return property descriptor
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object getOwnPropertyDescriptor(final Object self, final Object obj, final Object prop) {
        if (obj instanceof ScriptObject) {
            final String       key  = JSType.toString(prop);
            final ScriptObject sobj = (ScriptObject)obj;

            return sobj.getOwnPropertyDescriptor(key);
        } else if (obj instanceof ScriptObjectMirror) {
            final String       key  = JSType.toString(prop);
            final ScriptObjectMirror sobjMirror = (ScriptObjectMirror)obj;

            return sobjMirror.getOwnPropertyDescriptor(key);
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.4 Object.getOwnPropertyNames ( O )
     *
     * @param self self reference
     * @param obj  object to query for property names
     * @return array of property names
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object getOwnPropertyNames(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return new NativeArray(((ScriptObject)obj).getOwnKeys(true));
        } else if (obj instanceof ScriptObjectMirror) {
            return new NativeArray(((ScriptObjectMirror)obj).getOwnKeys(true));
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.5 Object.create ( O [, Properties] )
     *
     * @param self  self reference
     * @param proto prototype object
     * @param props properties to define
     * @return object created
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object create(final Object self, final Object proto, final Object props) {
        if (proto != null) {
            Global.checkObject(proto);
        }

        // FIXME: should we create a proper object with correct number of
        // properties?
        final ScriptObject newObj = Global.newEmptyInstance();
        newObj.setProtoCheck(proto);
        if (props != UNDEFINED) {
            NativeObject.defineProperties(self, newObj, props);
        }

        return newObj;
    }

    /**
     * ECMA 15.2.3.6 Object.defineProperty ( O, P, Attributes )
     *
     * @param self self reference
     * @param obj  object in which to define a property
     * @param prop property to define
     * @param attr attributes for property descriptor
     * @return object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object defineProperty(final Object self, final Object obj, final Object prop, final Object attr) {
        Global.checkObject(obj);
        ((ScriptObject)obj).defineOwnProperty(JSType.toString(prop), attr, true);
        return obj;
    }

    /**
     * ECMA 5.2.3.7 Object.defineProperties ( O, Properties )
     *
     * @param self  self reference
     * @param obj   object in which to define properties
     * @param props properties
     * @return object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object defineProperties(final Object self, final Object obj, final Object props) {
        Global.checkObject(obj);

        final ScriptObject sobj     = (ScriptObject)obj;
        final Object       propsObj = Global.toObject(props);

        if (propsObj instanceof ScriptObject) {
            final Object[] keys = ((ScriptObject)propsObj).getOwnKeys(false);
            for (final Object key : keys) {
                final String prop = JSType.toString(key);
                sobj.defineOwnProperty(prop, ((ScriptObject)propsObj).get(prop), true);
            }
        }
        return sobj;
    }

    /**
     * ECMA 15.2.3.8 Object.seal ( O )
     *
     * @param self self reference
     * @param obj  object to seal
     * @return sealed object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object seal(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).seal();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).seal();
        } else {
            throw notAnObject(obj);
        }
    }


    /**
     * ECMA 15.2.3.9 Object.freeze ( O )
     *
     * @param self self reference
     * @param obj object to freeze
     * @return frozen object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object freeze(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).freeze();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).freeze();
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.10 Object.preventExtensions ( O )
     *
     * @param self self reference
     * @param obj  object, for which to set the internal extensible property to false
     * @return object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object preventExtensions(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).preventExtensions();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).preventExtensions();
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.11 Object.isSealed ( O )
     *
     * @param self self reference
     * @param obj check whether an object is sealed
     * @return true if sealed, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object isSealed(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).isSealed();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).isSealed();
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.12 Object.isFrozen ( O )
     *
     * @param self self reference
     * @param obj check whether an object
     * @return true if object is frozen, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object isFrozen(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).isFrozen();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).isFrozen();
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.13 Object.isExtensible ( O )
     *
     * @param self self reference
     * @param obj check whether an object is extensible
     * @return true if object is extensible, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object isExtensible(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).isExtensible();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).isExtensible();
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.3.14 Object.keys ( O )
     *
     * @param self self reference
     * @param obj  object from which to extract keys
     * @return array of keys in object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object keys(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            return new NativeArray(sobj.getOwnKeys(false));
        } else if (obj instanceof ScriptObjectMirror) {
            final ScriptObjectMirror sobjMirror = (ScriptObjectMirror)obj;
            return new NativeArray(sobjMirror.getOwnKeys(false));
        } else {
            throw notAnObject(obj);
        }
    }

    /**
     * ECMA 15.2.2.1 , 15.2.1.1 new Object([value]) and Object([value])
     *
     * Constructor
     *
     * @param newObj is the new object instantiated with the new operator
     * @param self   self reference
     * @param value  value of object to be instantiated
     * @return the new NativeObject
     */
    @Constructor
    public static Object construct(final boolean newObj, final Object self, final Object value) {
        final JSType type = JSType.of(value);

        // Object(null), Object(undefined), Object() are same as "new Object()"

        if (newObj || (type == JSType.NULL || type == JSType.UNDEFINED)) {
            switch (type) {
            case BOOLEAN:
            case NUMBER:
            case STRING:
                return Global.toObject(value);
            case OBJECT:
            case FUNCTION:
                return value;
            case NULL:
            case UNDEFINED:
                // fall through..
            default:
                break;
            }

            return Global.newEmptyInstance();
        }

        return Global.toObject(value);
    }

    /**
     * ECMA 15.2.4.2 Object.prototype.toString ( )
     *
     * @param self self reference
     * @return ToString of object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        return ScriptRuntime.builtinObjectToString(self);
    }

    /**
     * ECMA 15.2.4.3 Object.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return localized ToString
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleString(final Object self) {
        final Object obj = JSType.toScriptObject(self);
        if (obj instanceof ScriptObject) {
            final InvokeByName toStringInvoker = getTO_STRING();
            final ScriptObject sobj = (ScriptObject)self;
            try {
                final Object toString = toStringInvoker.getGetter().invokeExact(sobj);

                if (Bootstrap.isCallable(toString)) {
                    return toStringInvoker.getInvoker().invokeExact(toString, sobj);
                }
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }

            throw typeError("not.a.function", "toString");
        }

        return ScriptRuntime.builtinObjectToString(self);
    }

    /**
     * ECMA 15.2.4.4 Object.prototype.valueOf ( )
     *
     * @param self self reference
     * @return value of object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        return Global.toObject(self);
    }

    /**
     * ECMA 15.2.4.5 Object.prototype.hasOwnProperty (V)
     *
     * @param self self reference
     * @param v property to check for
     * @return true if property exists in object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object hasOwnProperty(final Object self, final Object v) {
        final String str = JSType.toString(v);
        final Object obj = Global.toObject(self);

        return (obj instanceof ScriptObject) && ((ScriptObject)obj).hasOwnProperty(str);
    }

    /**
     * ECMA 15.2.4.6 Object.prototype.isPrototypeOf (V)
     *
     * @param self self reference
     * @param v v prototype object to check against
     * @return true if object is prototype of v
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object isPrototypeOf(final Object self, final Object v) {
        if (!(v instanceof ScriptObject)) {
            return false;
        }

        final Object obj   = Global.toObject(self);
        ScriptObject proto = (ScriptObject)v;

        do {
            proto = proto.getProto();
            if (proto == obj) {
                return true;
            }
        } while (proto != null);

        return false;
    }

    /**
     * ECMA 15.2.4.7 Object.prototype.propertyIsEnumerable (V)
     *
     * @param self self reference
     * @param v property to check if enumerable
     * @return true if property is enumerable
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object propertyIsEnumerable(final Object self, final Object v) {
        final String str = JSType.toString(v);
        final Object obj = Global.toObject(self);

        if (obj instanceof ScriptObject) {
            final jdk.nashorn.internal.runtime.Property property = ((ScriptObject)obj).getMap().findProperty(str);
            return property != null && property.isEnumerable();
        }

        return false;
    }

    /**
     * Nashorn extension: Object.bindProperties
     *
     * Binds the source object's properties to the target object. Binding
     * properties allows two-way read/write for the properties of the source object.
     *
     * Example:
     * <pre>
     * var obj = { x: 34, y: 100 };
     * var foo = {}
     *
     * // bind properties of "obj" to "foo" object
     * Object.bindProperties(foo, obj);
     *
     * // now, we can access/write on 'foo' properties
     * print(foo.x); // prints obj.x which is 34
     *
     * // update obj.x via foo.x
     * foo.x = "hello";
     * print(obj.x); // prints "hello" now
     *
     * obj.x = 42;   // foo.x also becomes 42
     * print(foo.x); // prints 42
     * </pre>
     * <p>
     * The source object bound can be a ScriptObject or a ScriptOjectMirror.
     * null or undefined source object results in TypeError being thrown.
     * </p>
     * Example:
     * <pre>
     * var obj = loadWithNewGlobal({
     *    name: "test",
     *    script: "obj = { x: 33, y: 'hello' }"
     * });
     *
     * // bind 'obj's properties to global scope 'this'
     * Object.bindProperties(this, obj);
     * print(x);         // prints 33
     * print(y);         // prints "hello"
     * x = Math.PI;      // changes obj.x to Math.PI
     * print(obj.x);     // prints Math.PI
     * </pre>
     *
     * Limitations of property binding:
     * <ul>
     * <li> Only enumerable, immediate (not proto inherited) properties of the source object are bound.
     * <li> If the target object already contains a property called "foo", the source's "foo" is skipped (not bound).
     * <li> Properties added to the source object after binding to the target are not bound.
     * <li> Property configuration changes on the source object (or on the target) is not propagated.
     * <li> Delete of property on the target (or the source) is not propagated -
     * only the property value is set to 'undefined' if the property happens to be a data property.
     * </ul>
     * <p>
     * It is recommended that the bound properties be treated as non-configurable
     * properties to avoid surprises.
     * </p>
     *
     * @param self self reference
     * @param target the target object to which the source object's properties are bound
     * @param source the source object whose properties are bound to the target
     * @return the target object after property binding
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object bindProperties(final Object self, final Object target, final Object source) {
        // target object has to be a ScriptObject
        Global.checkObject(target);
        // check null or undefined source object
        Global.checkObjectCoercible(source);

        final ScriptObject targetObj = (ScriptObject)target;

        if (source instanceof ScriptObject) {
            final ScriptObject sourceObj = (ScriptObject)source;
            final Property[] properties = sourceObj.getMap().getProperties();

            // filter non-enumerable properties
            final ArrayList<Property> propList = new ArrayList<>();
            for (Property prop : properties) {
                if (prop.isEnumerable()) {
                    propList.add(prop);
                }
            }

            if (! propList.isEmpty()) {
                targetObj.addBoundProperties(sourceObj, propList.toArray(new Property[propList.size()]));
            }
        } else if (source instanceof ScriptObjectMirror) {
            // get enumerable, immediate properties of mirror
            final ScriptObjectMirror mirror = (ScriptObjectMirror)source;
            final String[] keys = mirror.getOwnKeys(false);
            if (keys.length == 0) {
                // nothing to bind
                return target;
            }

            // make accessor properties using dynamic invoker getters and setters
            final AccessorProperty[] props = new AccessorProperty[keys.length];
            for (int idx = 0; idx < keys.length; idx++) {
                final String name = keys[idx];
                final MethodHandle getter = Bootstrap.createDynamicInvoker("dyn:getMethod|getProp|getElem:" + name, MIRROR_GETTER_TYPE);
                final MethodHandle setter = Bootstrap.createDynamicInvoker("dyn:setProp|setElem:" + name, MIRROR_SETTER_TYPE);
                props[idx] = (AccessorProperty.create(name, 0, getter, setter));
            }

            targetObj.addBoundProperties(source, props);
        } else if (source instanceof StaticClass) {
            final Class<?> clazz = ((StaticClass)source).getRepresentedClass();
            bindBeanProperties(targetObj, source, BeansLinker.getReadableStaticPropertyNames(clazz),
                    BeansLinker.getWritableStaticPropertyNames(clazz), BeansLinker.getStaticMethodNames(clazz));
        } else {
            final Class<?> clazz = source.getClass();
            bindBeanProperties(targetObj, source, BeansLinker.getReadableInstancePropertyNames(clazz),
                    BeansLinker.getWritableInstancePropertyNames(clazz), BeansLinker.getInstanceMethodNames(clazz));
        }

        return target;
    }

    private static void bindBeanProperties(final ScriptObject targetObj, final Object source,
            final Collection<String> readablePropertyNames, final Collection<String> writablePropertyNames,
            final Collection<String> methodNames) {
        final Set<String> propertyNames = new HashSet<>(readablePropertyNames);
        propertyNames.addAll(writablePropertyNames);

        final Class<?> clazz = source.getClass();
        Bootstrap.checkReflectionAccess(clazz);

        final MethodType getterType = MethodType.methodType(Object.class, clazz);
        final MethodType setterType = MethodType.methodType(Object.class, clazz, Object.class);

        final GuardingDynamicLinker linker = BeansLinker.getLinkerForClass(clazz);

        final List<AccessorProperty> properties = new ArrayList<>(propertyNames.size() + methodNames.size());
        for(final String methodName: methodNames) {
            properties.add(AccessorProperty.create(methodName, Property.NOT_WRITABLE,
                    getBoundBeanMethodGetter(source, getBeanOperation(linker, "dyn:getMethod:" + methodName, getterType, source)),
                    null));
        }
        for(final String propertyName: propertyNames) {
            final boolean isWritable = writablePropertyNames.contains(propertyName);
            properties.add(AccessorProperty.create(propertyName, isWritable ? 0 : Property.NOT_WRITABLE,
                    readablePropertyNames.contains(propertyName) ? getBeanOperation(linker, "dyn:getProp:" + propertyName, getterType, source) : Lookup.EMPTY_GETTER,
                    isWritable ? getBeanOperation(linker, "dyn:setProp:" + propertyName, setterType, source) : Lookup.EMPTY_SETTER));
        }

        targetObj.addBoundProperties(source, properties.toArray(new AccessorProperty[properties.size()]));
    }

    private static MethodHandle getBoundBeanMethodGetter(Object source, MethodHandle methodGetter) {
        try {
            // NOTE: we're relying on the fact that "dyn:getMethod:..." return value is constant for any given method
            // name and object linked with BeansLinker. (Actually, an even stronger assumption is true: return value is
            // constant for any given method name and object's class.)
            return MethodHandles.dropArguments(MethodHandles.constant(Object.class,
                    Bootstrap.bindDynamicMethod(methodGetter.invoke(source), source)), 0, Object.class);
        } catch(RuntimeException|Error e) {
            throw e;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static MethodHandle getBeanOperation(final GuardingDynamicLinker linker, final String operation,
            final MethodType methodType, final Object source) {
        final GuardedInvocation inv;
        try {
            inv = linker.getGuardedInvocation(createLinkRequest(operation, methodType, source),
                Bootstrap.getLinkerServices());
            assert passesGuard(source, inv.getGuard());
        } catch(RuntimeException|Error e) {
            throw e;
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
        assert inv.getSwitchPoint() == null; // Linkers in Dynalink's beans package don't use switchpoints.
        // We discard the guard, as all method handles will be bound to a specific object.
        return inv.getInvocation();
    }

    private static boolean passesGuard(final Object obj, final MethodHandle guard) throws Throwable {
        return guard == null || (boolean)guard.invoke(obj);
    }

    private static LinkRequest createLinkRequest(String operation, MethodType methodType, Object source) {
        return new LinkRequestImpl(CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), operation,
                methodType), false, source);
    }
}
