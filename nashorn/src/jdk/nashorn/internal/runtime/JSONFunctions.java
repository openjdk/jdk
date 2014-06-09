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

import static jdk.nashorn.internal.runtime.Source.sourceFor;

import java.lang.invoke.MethodHandle;
import java.util.Iterator;
import java.util.concurrent.Callable;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.parser.JSONParser;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Utilities used by "JSON" object implementation.
 */
public final class JSONFunctions {
    private JSONFunctions() {}

    private static final Object REVIVER_INVOKER = new Object();

    private static MethodHandle getREVIVER_INVOKER() {
        return Context.getGlobal().getDynamicInvoker(REVIVER_INVOKER,
                new Callable<MethodHandle>() {
                    @Override
                    public MethodHandle call() {
                        return Bootstrap.createDynamicInvoker("dyn:call", Object.class,
                            ScriptFunction.class, ScriptObject.class, String.class, Object.class);
                    }
                });
    }

    /**
     * Returns JSON-compatible quoted version of the given string.
     *
     * @param str String to be quoted
     * @return JSON-compatible quoted string
     */
    public static String quote(final String str) {
        return JSONParser.quote(str);
    }

    /**
     * Parses the given JSON text string and returns object representation.
     *
     * @param text JSON text to be parsed
     * @param reviver  optional value: function that takes two parameters (key, value)
     * @return Object representation of JSON text given
     */
    public static Object parse(final Object text, final Object reviver) {
        final String     str     = JSType.toString(text);
        final JSONParser parser  = new JSONParser(sourceFor("<json>", str), new Context.ThrowErrorManager());

        Node node;

        try {
            node = parser.parse();
        } catch (final ParserException e) {
            throw ECMAErrors.syntaxError(e, "invalid.json", e.getMessage());
        }

        final Global global = Context.getGlobal();
        final Object unfiltered = convertNode(global, node);
        return applyReviver(global, unfiltered, reviver);
    }

    // -- Internals only below this point

    // parse helpers

    // apply 'reviver' function if available
    private static Object applyReviver(final Global global, final Object unfiltered, final Object reviver) {
        if (reviver instanceof ScriptFunction) {
            final ScriptObject root = global.newObject();
            root.addOwnProperty("", Property.WRITABLE_ENUMERABLE_CONFIGURABLE, unfiltered);
            return walk(root, "", (ScriptFunction)reviver);
        }
        return unfiltered;
    }

    // This is the abstract "Walk" operation from the spec.
    private static Object walk(final ScriptObject holder, final Object name, final ScriptFunction reviver) {
        final Object val = holder.get(name);
        if (val instanceof ScriptObject) {
            final ScriptObject     valueObj = (ScriptObject)val;
            final Iterator<String> iter     = valueObj.propertyIterator();

            while (iter.hasNext()) {
                final String key        = iter.next();
                final Object newElement = walk(valueObj, key, reviver);

                if (newElement == ScriptRuntime.UNDEFINED) {
                    valueObj.delete(key, false);
                } else {
                    setPropertyValue(valueObj, key, newElement, false);
                }
            }
        }

        try {
             // Object.class, ScriptFunction.class, ScriptObject.class, String.class, Object.class);
             return getREVIVER_INVOKER().invokeExact(reviver, holder, JSType.toString(name), val);
        } catch(Error|RuntimeException t) {
            throw t;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Converts IR node to runtime value
    private static Object convertNode(final Global global, final Node node) {
        if (node instanceof LiteralNode) {
            // check for array literal
            if (node.tokenType() == TokenType.ARRAY) {
                assert node instanceof LiteralNode.ArrayLiteralNode;
                final Node[] elements = ((LiteralNode.ArrayLiteralNode)node).getValue();

                // NOTE: We cannot use LiteralNode.isNumericArray() here as that
                // method uses symbols of element nodes. Since we don't do lower
                // pass, there won't be any symbols!
                if (isNumericArray(elements)) {
                    final double[] values = new double[elements.length];
                    int   index = 0;

                    for (final Node elem : elements) {
                        values[index++] = JSType.toNumber(convertNode(global, elem));
                    }
                    return global.wrapAsObject(values);
                }

                final Object[] values = new Object[elements.length];
                int   index = 0;

                for (final Node elem : elements) {
                    values[index++] = convertNode(global, elem);
                }

                return global.wrapAsObject(values);
            }

            return ((LiteralNode<?>)node).getValue();

        } else if (node instanceof ObjectNode) {
            final ObjectNode   objNode  = (ObjectNode) node;
            final ScriptObject object   = global.newObject();

            for (final PropertyNode pNode: objNode.getElements()) {
                final Node         valueNode = pNode.getValue();

                final String name = pNode.getKeyName();
                final Object value = convertNode(global, valueNode);
                setPropertyValue(object, name, value, false);
            }

            return object;
        } else if (node instanceof UnaryNode) {
            // UnaryNode used only to represent negative number JSON value
            final UnaryNode unaryNode = (UnaryNode)node;
            return -((LiteralNode<?>)unaryNode.getExpression()).getNumber();
        } else {
            return null;
        }
    }

    // add a new property if does not exist already, or else set old property
    private static void setPropertyValue(final ScriptObject sobj, final String name, final Object value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(name);
        if (ArrayIndex.isValidArrayIndex(index)) {
            // array index key
            sobj.defineOwnProperty(index, value);
        } else if (sobj.getMap().findProperty(name) != null) {
            // pre-existing non-inherited property, call set
            sobj.set(name, value, strict);
        } else {
            // add new property
            sobj.addOwnProperty(name, Property.WRITABLE_ENUMERABLE_CONFIGURABLE, value);
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
}
