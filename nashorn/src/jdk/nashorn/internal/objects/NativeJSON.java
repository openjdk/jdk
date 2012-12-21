/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.parser.JSONParser;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Source;
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
    private static final MethodHandle REVIVER_INVOKER = Bootstrap.createDynamicInvoker("dyn:call", Object.class,
            ScriptFunction.class, ScriptObject.class, String.class, Object.class);


    NativeJSON() {
        this.setProto(Global.objectPrototype());
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
        final String     str     = JSType.toString(text);
        final Context    context = Global.getThisContext();
        final JSONParser parser  = new JSONParser(
                new Source("<json>", str),
                new Context.ThrowErrorManager(),
                (context != null) ?
                    context._strict :
                    false);

        Node node;

        try {
            node = parser.parse();
        } catch (final ParserException e) {
            syntaxError(Global.instance(), e, "invalid.json", e.getMessage());
            return UNDEFINED;
        }

        final Object unfiltered = convertNode(node);

        return applyReviver(unfiltered, reviver);
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
        wrapper.set("", value, Global.isStrict());

        return str("", wrapper, state);
    }

    // -- Internals only below this point

    // parse helpers

    // apply 'reviver' function if available
    private static Object applyReviver(final Object unfiltered, final Object reviver) {
        if (reviver instanceof ScriptFunction) {
            final ScriptObject root = Global.newEmptyInstance();
            root.set("", unfiltered, Global.isStrict());
            return walk(root, "", (ScriptFunction)reviver);
        }
        return unfiltered;
    }

    // This is the abstract "Walk" operation from the spec.
    private static Object walk(final ScriptObject holder, final Object name, final ScriptFunction reviver) {
        final Object val = holder.get(name);
        if (val == UNDEFINED) {
            return val;
        } else if (val instanceof ScriptObject) {
            final ScriptObject     valueObj = (ScriptObject)val;
            final boolean          strict   = Global.isStrict();
            final Iterator<String> iter     = valueObj.propertyIterator();

            while (iter.hasNext()) {
                final String key        = iter.next();
                final Object newElement = walk(valueObj, key, reviver);

                if (newElement == UNDEFINED) {
                    valueObj.delete(key, strict);
                } else {
                    valueObj.set(key, newElement, strict);
                }
            }

            return valueObj;
        } else if (isArray(val)) {
            final NativeArray      valueArray = (NativeArray)val;
            final boolean          strict     = Global.isStrict();
            final Iterator<String> iter       = valueArray.propertyIterator();

            while (iter.hasNext()) {
                final String key        = iter.next();
                final Object newElement = walk(valueArray, valueArray.get(key), reviver);

                if (newElement == UNDEFINED) {
                    valueArray.delete(key, strict);
                } else {
                    valueArray.set(key, newElement, strict);
                }
            }
            return valueArray;
        } else {
            try {
                // Object.class, ScriptFunction.class, ScriptObject.class, String.class, Object.class);
                return REVIVER_INVOKER.invokeExact(reviver, holder, JSType.toString(name), val);
            } catch(Error|RuntimeException t) {
                throw t;
            } catch(final Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    // Converts IR node to runtime value
    private static Object convertNode(final Node node) {

       if (node instanceof LiteralNode) {
            // check for array literal
            if (node.tokenType() == TokenType.LBRACKET) {
                assert node instanceof ArrayLiteralNode;
                final Node[] elements = ((ArrayLiteralNode)node).getValue();

                // NOTE: We cannot use LiteralNode.isNumericArray() here as that
                // method uses symbols of element nodes. Since we don't do lower
                // pass, there won't be any symbols!
                if (isNumericArray(elements)) {
                    final double[] values = new double[elements.length];
                    int   index = 0;

                    for (final Node elem : elements) {
                        values[index++] = JSType.toNumber(convertNode(elem));
                    }
                    return Global.allocate(values);
                }

                final Object[] values = new Object[elements.length];
                int   index = 0;

                for (final Node elem : elements) {
                    values[index++] = convertNode(elem);
                }

                return Global.allocate(values);
            }

            return ((LiteralNode<?>)node).getValue();

        } else if (node instanceof ObjectNode) {
            final ObjectNode   objNode  = (ObjectNode) node;
            final ScriptObject object   = Global.newEmptyInstance();
            final boolean      strict   = Global.isStrict();
            final List<Node>   elements = objNode.getElements();

            for (final Node elem : elements) {
                final PropertyNode pNode     = (PropertyNode) elem;
                final Node         valueNode = pNode.getValue();

                object.set(pNode.getKeyName(), convertNode(valueNode), strict);
            }

            return object;
        } else if (node instanceof UnaryNode) {
            // UnaryNode used only to represent negative number JSON value
            final UnaryNode unaryNode = (UnaryNode)node;
            return -((LiteralNode<?>)unaryNode.rhs()).getNumber();
        } else {
            return null;
        }
    }

    // does the given IR node represent a numeric array?
    private static boolean isNumericArray(final Node[] values) {
        for (final Node node : values) {
            if (node instanceof LiteralNode && ((LiteralNode<?>)node).getValue() instanceof Number) {
                continue;
            }
            return false;
        }
        return true;
    }

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
            return JSONParser.quote((String)value);
        } else if (value instanceof ConsString) {
            return JSONParser.quote(value.toString());
        }

        if (value instanceof Number) {
            return JSType.isFinite(((Number)value).doubleValue()) ? JSType.toString(value) : "null";
        }

        final JSType type = JSType.of(value);
        if (type == JSType.OBJECT) {
            if (isArray(value)) {
                return JA((NativeArray)value, state);
            } else if (value instanceof ScriptObject) {
                return JO((ScriptObject)value, state);
            }
        }

        return UNDEFINED;
    }

    // Spec: The abstract operation JO(value) serializes an object.
    private static String JO(final ScriptObject value, final StringifyState state) {
        if (state.stack.containsKey(value)) {
            typeError(Global.instance(), "JSON.stringify.cyclic");
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

                member.append(JSONParser.quote(p.toString())).append(':');
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
    private static Object JA(final NativeArray value, final StringifyState state) {
        if (state.stack.containsKey(value)) {
            typeError(Global.instance(), "JSON.stringify.cyclic");
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
