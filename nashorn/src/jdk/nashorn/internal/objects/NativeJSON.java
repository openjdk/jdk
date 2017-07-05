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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSONFunctions;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * ECMAScript 262 Edition 5, Section 15.12 The NativeJSON Object
 *
 */
@ScriptClass("JSON")
public final class NativeJSON extends ScriptObject {
    private static final InvokeByName TO_JSON = new InvokeByName("toJSON", ScriptObject.class, Object.class, Object.class);
    private static final MethodHandle REPLACER_INVOKER = Bootstrap.createDynamicInvoker("dyn:call", Object.class,
            ScriptFunction.class, ScriptObject.class, Object.class, Object.class);

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    NativeJSON() {
        super(Global.objectPrototype(), $nasgenmap$);
    }

    /**
     * ECMA 15.12.2 parse ( text [ , reviver ] )
     *
     * @param self     self reference
     * @param text     a JSON formatted string
     * @param reviver  optional value: function that takes two parameters (key, value)
     *
     * @return an ECMA script value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object parse(final Object self, final Object text, final Object reviver) {
        return JSONFunctions.parse(text, reviver);
    }

    /**
     * ECMA 15.12.3 stringify ( value [ , replacer [ , space ] ] )
     *
     * @param self     self reference
     * @param value    ECMA script value (usually object or array)
     * @param replacer either a function or an array of strings and numbers
     * @param space    optional parameter - allows result to have whitespace injection
     *
     * @return a string in JSON format
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object stringify(final Object self, final Object value, final Object replacer, final Object space) {
        // The stringify method takes a value and an optional replacer, and an optional
        // space parameter, and returns a JSON text. The replacer can be a function
        // that can replace values, or an array of strings that will select the keys.

        // A default replacer method can be provided. Use of the space parameter can
        // produce text that is more easily readable.

        final StringifyState state = new StringifyState();

        // If there is a replacer, it must be a function or an array.
        if (replacer instanceof ScriptFunction) {
            state.replacerFunction = (ScriptFunction) replacer;
        } else if (isArray(replacer) ||
                replacer instanceof Iterable ||
                (replacer != null && replacer.getClass().isArray())) {

            state.propertyList = new ArrayList<>();

            final Iterator<Object> iter = ArrayLikeIterator.arrayLikeIterator(replacer);

            while (iter.hasNext()) {
                String item = null;
                final Object v = iter.next();

                if (v instanceof String) {
                    item = (String) v;
                } else if (v instanceof ConsString) {
                    item = v.toString();
                } else if (v instanceof Number ||
                        v instanceof NativeNumber ||
                        v instanceof NativeString) {
                    item = JSType.toString(v);
                }

                if (item != null) {
                    state.propertyList.add(item);
                }
            }
        }

        // If the space parameter is a number, make an indent
        // string containing that many spaces.

        String gap;

        if (space instanceof Number || space instanceof NativeNumber) {
            int indent;
            if (space instanceof NativeNumber) {
                indent = ((NativeNumber)space).intValue();
            } else {
                indent = ((Number)space).intValue();
            }

            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(10, indent); i++) {
                sb.append(' ');
            }
            gap = sb.toString();

        } else if (space instanceof String || space instanceof ConsString || space instanceof NativeString) {
            final String str = (space instanceof String) ? (String)space : space.toString();
            gap = str.substring(0, Math.min(10, str.length()));
        } else {
            gap = "";
        }

        state.gap = gap;

        final ScriptObject wrapper = Global.newEmptyInstance();
        wrapper.set("", value, false);

        return str("", wrapper, state);
    }

    // -- Internals only below this point

    // stringify helpers.

    private static class StringifyState {
        final Map<ScriptObject, ScriptObject> stack = new IdentityHashMap<>();

        StringBuilder  indent = new StringBuilder();
        String         gap = "";
        List<String>   propertyList = null;
        ScriptFunction replacerFunction = null;
    }

    // Spec: The abstract operation Str(key, holder).
    private static Object str(final Object key, final ScriptObject holder, final StringifyState state) {
        Object value = holder.get(key);

        try {
            if (value instanceof ScriptObject) {
                final ScriptObject svalue = (ScriptObject)value;
                final Object toJSON = TO_JSON.getGetter().invokeExact(svalue);
                if (toJSON instanceof ScriptFunction) {
                    value = TO_JSON.getInvoker().invokeExact(toJSON, svalue, key);
                }
            }

            if (state.replacerFunction != null) {
                value = REPLACER_INVOKER.invokeExact(state.replacerFunction, holder, key, value);
            }
        } catch(Error|RuntimeException t) {
            throw t;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
        final boolean isObj = (value instanceof ScriptObject);
        if (isObj) {
            if (value instanceof NativeNumber) {
                value = JSType.toNumber(value);
            } else if (value instanceof NativeString) {
                value = JSType.toString(value);
            } else if (value instanceof NativeBoolean) {
                value = ((NativeBoolean)value).booleanValue();
            }
        }

        if (value == null) {
            return "null";
        } else if (Boolean.TRUE.equals(value)) {
            return "true";
        } else if (Boolean.FALSE.equals(value)) {
            return "false";
        }

        if (value instanceof String) {
            return JSONFunctions.quote((String)value);
        } else if (value instanceof ConsString) {
            return JSONFunctions.quote(value.toString());
        }

        if (value instanceof Number) {
            return JSType.isFinite(((Number)value).doubleValue()) ? JSType.toString(value) : "null";
        }

        final JSType type = JSType.of(value);
        if (type == JSType.OBJECT) {
            if (isArray(value)) {
                return JA((ScriptObject)value, state);
            } else if (value instanceof ScriptObject) {
                return JO((ScriptObject)value, state);
            }
        }

        return UNDEFINED;
    }

    // Spec: The abstract operation JO(value) serializes an object.
    private static String JO(final ScriptObject value, final StringifyState state) {
        if (state.stack.containsKey(value)) {
            throw typeError("JSON.stringify.cyclic");
        }

        state.stack.put(value, value);
        final StringBuilder stepback = new StringBuilder(state.indent.toString());
        state.indent.append(state.gap);

        final StringBuilder finalStr = new StringBuilder();
        final List<Object>  partial  = new ArrayList<>();
        final List<String>  k        = state.propertyList == null ? Arrays.asList(value.getOwnKeys(false)) : state.propertyList;

        for (final Object p : k) {
            final Object strP = str(p, value, state);

            if (strP != UNDEFINED) {
                final StringBuilder member = new StringBuilder();

                member.append(JSONFunctions.quote(p.toString())).append(':');
                if (!state.gap.isEmpty()) {
                    member.append(' ');
                }

                member.append(strP);
                partial.add(member);
            }
        }

        if (partial.isEmpty()) {
            finalStr.append("{}");
        } else {
            if (state.gap.isEmpty()) {
                final int size = partial.size();
                int       index = 0;

                finalStr.append('{');

                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(',');
                    }
                    index++;
                }

                finalStr.append('}');
            } else {
                final int size  = partial.size();
                int       index = 0;

                finalStr.append("{\n");
                finalStr.append(state.indent);

                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(",\n");
                        finalStr.append(state.indent);
                    }
                    index++;
                }

                finalStr.append('\n');
                finalStr.append(stepback);
                finalStr.append('}');
            }
        }

        state.stack.remove(value);
        state.indent = stepback;

        return finalStr.toString();
    }

    // Spec: The abstract operation JA(value) serializes an array.
    private static Object JA(final ScriptObject value, final StringifyState state) {
        if (state.stack.containsKey(value)) {
            throw typeError("JSON.stringify.cyclic");
        }

        state.stack.put(value, value);
        final StringBuilder stepback = new StringBuilder(state.indent.toString());
        state.indent.append(state.gap);
        final List<Object> partial = new ArrayList<>();

        final int length = JSType.toInteger(value.getLength());
        int index = 0;

        while (index < length) {
            Object strP = str(index, value, state);
            if (strP == UNDEFINED) {
                strP = "null";
            }
            partial.add(strP);
            index++;
        }

        final StringBuilder finalStr = new StringBuilder();
        if (partial.isEmpty()) {
            finalStr.append("[]");
        } else {
            if (state.gap.isEmpty()) {
                final int size = partial.size();
                index = 0;
                finalStr.append('[');
                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(',');
                    }
                    index++;
                }

                finalStr.append(']');
            } else {
                final int size = partial.size();
                index = 0;
                finalStr.append("[\n");
                finalStr.append(state.indent);
                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(",\n");
                        finalStr.append(state.indent);
                    }
                    index++;
                }

                finalStr.append('\n');
                finalStr.append(stepback);
                finalStr.append(']');
            }
        }

        state.stack.remove(value);
        state.indent = stepback;

        return finalStr.toString();
    }
}
