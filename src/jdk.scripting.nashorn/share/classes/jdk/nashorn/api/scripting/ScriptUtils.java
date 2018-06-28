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

import java.lang.invoke.MethodHandle;
import jdk.dynalink.beans.StaticClass;
import jdk.dynalink.linker.LinkerServices;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Utilities that are to be called from script code.
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 1.8u40
 */
@Deprecated(since="11", forRemoval=true)
public final class ScriptUtils {
    private ScriptUtils() {}

    /**
     * Returns AST as JSON compatible string. This is used to
     * implement "parse" function in resources/parse.js script.
     *
     * @param code code to be parsed
     * @param name name of the code source (used for location)
     * @param includeLoc tells whether to include location information for nodes or not
     * @return JSON string representation of AST of the supplied code
     */
    public static String parse(final String code, final String name, final boolean includeLoc) {
        return ScriptRuntime.parse(code, name, includeLoc);
    }

    /**
     * Method which converts javascript types to java types for the
     * String.format method (jrunscript function sprintf).
     *
     * @param format a format string
     * @param args arguments referenced by the format specifiers in format
     * @return a formatted string
     */
    public static String format(final String format, final Object[] args) {
        return Formatter.format(format, args);
    }

    /**
     * Create a wrapper function that calls {@code func} synchronized on {@code sync} or, if that is undefined,
     * {@code self}. Used to implement "sync" function in resources/mozilla_compat.js.
     *
     * @param func the function to wrap
     * @param sync the object to synchronize on
     * @return a synchronizing wrapper function
     * @throws IllegalArgumentException if func does not represent a script function
     */
    public static Object makeSynchronizedFunction(final Object func, final Object sync) {
        final Object unwrapped = unwrap(func);
        if (unwrapped instanceof ScriptFunction) {
            return ((ScriptFunction)unwrapped).createSynchronized(unwrap(sync));
        }

        throw new IllegalArgumentException();
    }

    /**
     * Make a script object mirror on given object if needed.
     *
     * @param obj object to be wrapped
     * @return wrapped object
     * @throws IllegalArgumentException if obj cannot be wrapped
     */
    public static ScriptObjectMirror wrap(final Object obj) {
        if (obj instanceof ScriptObjectMirror) {
            return (ScriptObjectMirror)obj;
        }

        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            return (ScriptObjectMirror) ScriptObjectMirror.wrap(sobj, Context.getGlobal());
        }

        throw new IllegalArgumentException();
    }

    /**
     * Unwrap a script object mirror if needed.
     *
     * @param obj object to be unwrapped
     * @return unwrapped object
     */
    public static Object unwrap(final Object obj) {
        if (obj instanceof ScriptObjectMirror) {
            return ScriptObjectMirror.unwrap(obj, Context.getGlobal());
        }

        return obj;
    }

    /**
     * Wrap an array of object to script object mirrors if needed.
     *
     * @param args array to be unwrapped
     * @return wrapped array
     */
    public static Object[] wrapArray(final Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }

        return ScriptObjectMirror.wrapArray(args, Context.getGlobal());
    }

    /**
     * Unwrap an array of script object mirrors if needed.
     *
     * @param args array to be unwrapped
     * @return unwrapped array
     */
    public static Object[] unwrapArray(final Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }

        return ScriptObjectMirror.unwrapArray(args, Context.getGlobal());
    }

    /**
     * Convert the given object to the given type.
     *
     * @param obj object to be converted
     * @param type destination type to convert to. type is either a Class
     * or nashorn representation of a Java type returned by Java.type() call in script.
     * @return converted object
     */
    public static Object convert(final Object obj, final Object type) {
        if (obj == null) {
            return null;
        }

        final Class<?> clazz;
        if (type instanceof Class) {
            clazz = (Class<?>)type;
        } else if (type instanceof StaticClass) {
            clazz = ((StaticClass)type).getRepresentedClass();
        } else {
            throw new IllegalArgumentException("type expected");
        }

        final LinkerServices linker = Bootstrap.getLinkerServices();
        final Object objToConvert = unwrap(obj);
        final MethodHandle converter = linker.getTypeConverter(objToConvert.getClass(), clazz);
        if (converter == null) {
            // no supported conversion!
            throw new UnsupportedOperationException("conversion not supported");
        }

        try {
            return converter.invoke(objToConvert);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
