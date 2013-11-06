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

package jdk.nashorn.internal.runtime;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.Callable;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * Runtime interface to the global scope objects.
 */

public interface GlobalObject {
    /**
     * Is this global of the given Context?
     * @param ctxt the context
     * @return true if this global belongs to the given Context
     */
    public boolean isOfContext(final Context ctxt);

    /**
     * Does this global belong to a strict Context?
     * @return true if this global belongs to a strict Context
     */
    public boolean isStrictContext();

    /**
     * Initialize standard builtin objects like "Object", "Array", "Function" etc.
     * as well as our extension builtin objects like "Java", "JSAdapter" as properties
     * of the global scope object.
     */
    public void initBuiltinObjects();

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newScriptFunction(String, MethodHandle, ScriptObject, boolean)}
     *
     * @param name   function name
     * @param handle invocation handle for function
     * @param scope  the scope
     * @param strict are we in strict mode
     *
     * @return new script function
     */
   public ScriptFunction newScriptFunction(String name, MethodHandle handle, ScriptObject scope, boolean strict);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#wrapAsObject(Object)}
     *
     * @param obj object to wrap
     * @return    wrapped object
     */
   public Object wrapAsObject(Object obj);


    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#primitiveLookup(LinkRequest, Object)}
     *
     * @param request the link request for the dynamic call site.
     * @param self     self reference
     *
     * @return guarded invocation
     */
   public GuardedInvocation primitiveLookup(LinkRequest request, Object self);


    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newObject()}
     *
     * @return the new ScriptObject
     */
   public ScriptObject newObject();

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#isError(ScriptObject)}
     *
     * @param sobj to check if it is an error object
     * @return true if error object
     */
   public boolean isError(ScriptObject sobj);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the error
     */
   public ScriptObject newError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newEvalError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the eval error
     */
   public ScriptObject newEvalError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newRangeError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the range error
     */
   public ScriptObject newRangeError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newReferenceError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the reference error
     */
   public ScriptObject newReferenceError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newSyntaxError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the syntax error
     */
   public ScriptObject newSyntaxError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newTypeError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the type error
     */
   public ScriptObject newTypeError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newURIError(String)}
     *
     * @param msg the error message
     *
     * @return the new ScriptObject representing the URI error
     */
    public ScriptObject newURIError(String msg);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newGenericDescriptor(boolean, boolean)}
     *
     * @param configurable is the described property configurable
     * @param enumerable   is the described property enumerable
     *
     * @return property descriptor
     */
    public PropertyDescriptor newGenericDescriptor(boolean configurable, boolean enumerable);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newDataDescriptor(Object, boolean, boolean, boolean)}
     *
     * @param value        data value
     * @param configurable is the described property configurable
     * @param enumerable   is the described property enumerable
     * @param writable     is the described property writable
     *
     * @return property descriptor
     */
    public PropertyDescriptor newDataDescriptor(Object value, boolean configurable, boolean enumerable, boolean writable);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#newAccessorDescriptor(Object, Object, boolean, boolean)}
     *
     * @param get          property getter, or null if none
     * @param set          property setter, or null if none
     * @param configurable is the described property configurable
     * @param enumerable   is the described property enumerable
     *
     * @return property descriptor
     */
    public PropertyDescriptor newAccessorDescriptor(Object get, Object set, boolean configurable, boolean enumerable);

    /**
     * Wrapper for {@link jdk.nashorn.internal.objects.Global#getDefaultValue(ScriptObject, Class)}
     *
     * @param sobj     script object
     * @param typeHint type hint
     *
     * @return default value
     */
    public Object getDefaultValue(ScriptObject sobj, Class<?> typeHint);

    /**
     * Find the compiled Class for the given script source, if available
     *
     * @param source Source object of the script
     * @return compiled Class object or null
     */
    public Class<?> findCachedClass(Source source);

    /**
     * Put the Source associated Class object in the Source-to-Class cache
     *
     * @param source Source of the script
     * @param clazz compiled Class object for the source
     */
    public void cacheClass(Source source, Class<?> clazz);

    /**
     * Get cached InvokeByName object for the given key
     * @param key key to be associated with InvokeByName object
     * @param creator if InvokeByName is absent 'creator' is called to make one (lazy init)
     * @return InvokeByName object associated with the key.
     */
    public InvokeByName getInvokeByName(final Object key, final Callable<InvokeByName> creator);

    /**
     * Get cached dynamic method handle for the given key
     * @param key key to be associated with dynamic method handle
     * @param creator if method handle is absent 'creator' is called to make one (lazy init)
     * @return dynamic method handle associated with the key.
     */
    public MethodHandle getDynamicInvoker(final Object key, final Callable<MethodHandle> creator);
}
