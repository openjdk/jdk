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

package jdk.nashorn.api.scripting;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.script.Bindings;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Mirror object that wraps a given ScriptObject instance. User can
 * access ScriptObject via the javax.script.Bindings interface or
 * netscape.javascript.JSObject interface.
 */
public final class ScriptObjectMirror extends JSObject implements Bindings {
    private static AccessControlContext getContextAccCtxt() {
        final Permissions perms = new Permissions();
        perms.add(new RuntimePermission(Context.NASHORN_GET_CONTEXT));
        return new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, perms) });
    }

    private static final AccessControlContext GET_CONTEXT_ACC_CTXT = getContextAccCtxt();

    private final ScriptObject sobj;
    private final ScriptObject global;

    @Override
    public boolean equals(final Object other) {
        if (other instanceof ScriptObjectMirror) {
            return sobj.equals(((ScriptObjectMirror)other).sobj);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return sobj.hashCode();
    }

    @Override
    public String toString() {
        return inGlobal(new Callable<String>() {
            @Override
            public String call() {
                return ScriptRuntime.safeToString(sobj);
            }
        });
    }

    // JSObject methods
    @Override
    public Object call(final String functionName, final Object... args) {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);

        try {
            if (globalChanged) {
                Context.setGlobal(global);
            }

            final Object val = functionName == null? sobj : sobj.get(functionName);
            if (val instanceof ScriptFunction) {
                final Object[] modArgs = globalChanged? wrapArray(args, oldGlobal) : args;
                return wrap(ScriptRuntime.checkAndApply((ScriptFunction)val, sobj, unwrapArray(modArgs, global)), global);
            } else if (val instanceof ScriptObjectMirror && ((ScriptObjectMirror)val).isFunction()) {
                return ((ScriptObjectMirror)val).call(null, args);
            }

            throw new NoSuchMethodException("No such function " + ((functionName != null)? functionName : ""));
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    @Override
    public Object newObject(final String functionName, final Object... args) {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);

        try {
            if (globalChanged) {
                Context.setGlobal(global);
            }

            final Object val = functionName == null? sobj : sobj.get(functionName);
            if (val instanceof ScriptFunction) {
                final Object[] modArgs = globalChanged? wrapArray(args, oldGlobal) : args;
                return wrap(ScriptRuntime.checkAndConstruct((ScriptFunction)val, unwrapArray(modArgs, global)), global);
            } else if (val instanceof ScriptObjectMirror && ((ScriptObjectMirror)val).isFunction()) {
                return ((ScriptObjectMirror)val).newObject(null, args);
            }

            throw new RuntimeException("not a constructor " + ((functionName != null)? functionName : ""));
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    @Override
    public Object eval(final String s) {
        return inGlobal(new Callable<Object>() {
            @Override
            public Object call() {
                final Context context = AccessController.doPrivileged(
                        new PrivilegedAction<Context>() {
                            @Override
                            public Context run() {
                                return Context.getContext();
                            }
                        }, GET_CONTEXT_ACC_CTXT);
                return wrap(context.eval(global, s, null, null, false), global);
            }
        });
    }

    @Override
    public Object getMember(final String name) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return wrap(sobj.get(name), global);
            }
        });
    }

    @Override
    public Object getSlot(final int index) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return wrap(sobj.get(index), global);
            }
        });
    }

    @Override
    public void removeMember(final String name) {
        remove(name);
    }

    @Override
    public void setMember(final String name, final Object value) {
        put(name, value);
    }

    @Override
    public void setSlot(final int index, final Object value) {
        inGlobal(new Callable<Void>() {
            @Override public Void call() {
                sobj.set(index, unwrap(value, global), global.isStrictContext());
                return null;
            }
        });
    }

    // javax.script.Bindings methods

    @Override
    public void clear() {
        inGlobal(new Callable<Object>() {
            @Override public Object call() {
                sobj.clear();
                return null;
            }
        });
    }

    @Override
    public boolean containsKey(final Object key) {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.containsKey(unwrap(key, global));
            }
        });
    }

    @Override
    public boolean containsValue(final Object value) {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.containsValue(unwrap(value, global));
            }
        });
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return inGlobal(new Callable<Set<Map.Entry<String, Object>>>() {
            @Override public Set<Map.Entry<String, Object>> call() {
                final Iterator<String>               iter    = sobj.propertyIterator();
                final Set<Map.Entry<String, Object>> entries = new LinkedHashSet<>();

                while (iter.hasNext()) {
                    final String key   = iter.next();
                    final Object value = translateUndefined(wrap(sobj.get(key), global));
                    entries.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
                }

                return Collections.unmodifiableSet(entries);
            }
        });
    }

    @Override
    public Object get(final Object key) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return translateUndefined(wrap(sobj.get(key), global));
            }
        });
    }

    @Override
    public boolean isEmpty() {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.isEmpty();
            }
        });
    }

    @Override
    public Set<String> keySet() {
        return inGlobal(new Callable<Set<String>>() {
            @Override public Set<String> call() {
                final Iterator<String> iter   = sobj.propertyIterator();
                final Set<String>      keySet = new LinkedHashSet<>();

                while (iter.hasNext()) {
                    keySet.add(iter.next());
                }

                return Collections.unmodifiableSet(keySet);
            }
        });
    }

    @Override
    public Object put(final String key, final Object value) {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                final Object modValue = globalChanged? wrap(value, oldGlobal) : value;
                return translateUndefined(wrap(sobj.put(key, unwrap(modValue, global)), global));
            }
        });
    }

    @Override
    public void putAll(final Map<? extends String, ? extends Object> map) {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        inGlobal(new Callable<Object>() {
            @Override public Object call() {
                final boolean strict = global.isStrictContext();
                for (final Map.Entry<? extends String, ? extends Object> entry : map.entrySet()) {
                    final Object value = entry.getValue();
                    final Object modValue = globalChanged? wrap(value, oldGlobal) : value;
                    sobj.set(entry.getKey(), unwrap(modValue, global), strict);
                }
                return null;
            }
        });
    }

    @Override
    public Object remove(final Object key) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return wrap(sobj.remove(unwrap(key, global)), global);
            }
        });
    }

    /**
     * Delete a property from this object.
     *
     * @param key the property to be deleted
     *
     * @return if the delete was successful or not
     */
    public boolean delete(final Object key) {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.delete(unwrap(key, global));
            }
        });
    }

    @Override
    public int size() {
        return inGlobal(new Callable<Integer>() {
            @Override public Integer call() {
                return sobj.size();
            }
        });
    }

    @Override
    public Collection<Object> values() {
        return inGlobal(new Callable<Collection<Object>>() {
            @Override public Collection<Object> call() {
                final List<Object>     values = new ArrayList<>(size());
                final Iterator<Object> iter   = sobj.valueIterator();

                while (iter.hasNext()) {
                    values.add(translateUndefined(wrap(iter.next(), global)));
                }

                return Collections.unmodifiableList(values);
            }
        });
    }

    // Support for ECMAScript Object API on mirrors

    /**
     * Return the __proto__ of this object.
     * @return __proto__ object.
     */
    public Object getProto() {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return wrap(sobj.getProto(), global);
            }
        });
    }

    /**
     * Set the __proto__ of this object.
     * @param proto new proto for this object
     */
    public void setProto(final Object proto) {
        inGlobal(new Callable<Void>() {
            @Override public Void call() {
                sobj.setProtoCheck(unwrap(proto, global));
                return null;
            }
        });
    }

    /**
     * ECMA [[Class]] property
     *
     * @return ECMA [[Class]] property value of this object
     */
    public String getClassName() {
        return sobj.getClassName();
    }

    /**
     * ECMA 8.12.1 [[GetOwnProperty]] (P)
     *
     * @param key property key
     *
     * @return Returns the Property Descriptor of the named own property of this
     * object, or undefined if absent.
     */
    public Object getOwnPropertyDescriptor(final String key) {
        return inGlobal(new Callable<Object>() {
            @Override public Object call() {
                return wrap(sobj.getOwnPropertyDescriptor(key), global);
            }
        });
    }

    /**
     * return an array of own property keys associated with the object.
     *
     * @param all True if to include non-enumerable keys.
     * @return Array of keys.
     */
    public String[] getOwnKeys(final boolean all) {
        return inGlobal(new Callable<String[]>() {
            @Override public String[] call() {
                return sobj.getOwnKeys(all);
            }
        });
    }

    /**
     * Flag this script object as non extensible
     *
     * @return the object after being made non extensible
     */
    public ScriptObjectMirror preventExtensions() {
        return inGlobal(new Callable<ScriptObjectMirror>() {
            @Override public ScriptObjectMirror call() {
                sobj.preventExtensions();
                return ScriptObjectMirror.this;
            }
        });
    }

    /**
     * Check if this script object is extensible
     * @return true if extensible
     */
    public boolean isExtensible() {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.isExtensible();
            }
        });
    }

    /**
     * ECMAScript 15.2.3.8 - seal implementation
     * @return the sealed script object
     */
    public ScriptObjectMirror seal() {
        return inGlobal(new Callable<ScriptObjectMirror>() {
            @Override public ScriptObjectMirror call() {
                sobj.seal();
                return ScriptObjectMirror.this;
            }
        });
    }

    /**
     * Check whether this script object is sealed
     * @return true if sealed
     */
    public boolean isSealed() {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.isSealed();
            }
        });
    }

    /**
     * ECMA 15.2.39 - freeze implementation. Freeze this script object
     * @return the frozen script object
     */
    public ScriptObjectMirror freeze() {
        return inGlobal(new Callable<ScriptObjectMirror>() {
            @Override public ScriptObjectMirror call() {
                sobj.freeze();
                return ScriptObjectMirror.this;
            }
        });
    }

    /**
     * Check whether this script object is frozen
     * @return true if frozen
     */
    public boolean isFrozen() {
        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.isFrozen();
            }
        });
    }

    // ECMAScript instanceof check

    /**
     * Checking whether a script object is an instance of another by
     * walking the proto chain
     *
     * @param instance instace to check
     * @return true if 'instance' is an instance of this object
     */
    public boolean isInstance(final ScriptObjectMirror instance) {
        // if not belongs to my global scope, return false
        if (instance == null || global != instance.global) {
            return false;
        }

        return inGlobal(new Callable<Boolean>() {
            @Override public Boolean call() {
                return sobj.isInstance(instance.sobj);
            }
        });
    }

    /**
     * is this a function object?
     *
     * @return if this mirror wraps a ECMAScript function instance
     */
    public boolean isFunction() {
        return sobj instanceof ScriptFunction;
    }

    /**
     * is this a 'use strict' function object?
     *
     * @return true if this mirror represents a ECMAScript 'use strict' function
     */
    public boolean isStrictFunction() {
        return isFunction() && ((ScriptFunction)sobj).isStrict();
    }

    /**
     * is this an array object?
     *
     * @return if this mirror wraps a ECMAScript array object
     */
    public boolean isArray() {
        return sobj.isArray();
    }

    /**
     * Utility to check if given object is ECMAScript undefined value
     *
     * @param obj object to check
     * @return true if 'obj' is ECMAScript undefined value
     */
    public static boolean isUndefined(final Object obj) {
        return obj == ScriptRuntime.UNDEFINED;
    }

    /**
     * Make a script object mirror on given object if needed.
     *
     * @param obj object to be wrapped
     * @param homeGlobal global to which this object belongs
     * @return wrapped object
     */
    public static Object wrap(final Object obj, final ScriptObject homeGlobal) {
        return (obj instanceof ScriptObject && homeGlobal != null) ? new ScriptObjectMirror((ScriptObject)obj, homeGlobal) : obj;
    }

    /**
     * Unwrap a script object mirror if needed.
     *
     * @param obj object to be unwrapped
     * @param homeGlobal global to which this object belongs
     * @return unwrapped object
     */
    public static Object unwrap(final Object obj, final ScriptObject homeGlobal) {
        if (obj instanceof ScriptObjectMirror) {
            final ScriptObjectMirror mirror = (ScriptObjectMirror)obj;
            return (mirror.global == homeGlobal)? mirror.sobj : obj;
        }

        return obj;
    }

    /**
     * Wrap an array of object to script object mirrors if needed.
     *
     * @param args array to be unwrapped
     * @param homeGlobal global to which this object belongs
     * @return wrapped array
     */
    public static Object[] wrapArray(final Object[] args, final ScriptObject homeGlobal) {
        if (args == null || args.length == 0) {
            return args;
        }

        final Object[] newArgs = new Object[args.length];
        int index = 0;
        for (final Object obj : args) {
            newArgs[index] = wrap(obj, homeGlobal);
            index++;
        }
        return newArgs;
    }

    /**
     * Unwrap an array of script object mirrors if needed.
     *
     * @param args array to be unwrapped
     * @param homeGlobal global to which this object belongs
     * @return unwrapped array
     */
    public static Object[] unwrapArray(final Object[] args, final ScriptObject homeGlobal) {
        if (args == null || args.length == 0) {
            return args;
        }

        final Object[] newArgs = new Object[args.length];
        int index = 0;
        for (final Object obj : args) {
            newArgs[index] = unwrap(obj, homeGlobal);
            index++;
        }
        return newArgs;
    }

    // package-privates below this.

    ScriptObjectMirror(final ScriptObject sobj, final ScriptObject global) {
        assert sobj != null : "ScriptObjectMirror on null!";
        assert global != null : "null global for ScriptObjectMirror!";

        this.sobj = sobj;
        this.global = global;
    }

    // accessors for script engine
    ScriptObject getScriptObject() {
        return sobj;
    }

    ScriptObject getHomeGlobal() {
        return global;
    }

    static Object translateUndefined(Object obj) {
        return (obj == ScriptRuntime.UNDEFINED)? null : obj;
    }

    // internals only below this.
    private <V> V inGlobal(final Callable<V> callable) {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        if (globalChanged) {
            Context.setGlobal(global);
        }
        try {
            return callable.call();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new AssertionError("Cannot happen", e);
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }
}
