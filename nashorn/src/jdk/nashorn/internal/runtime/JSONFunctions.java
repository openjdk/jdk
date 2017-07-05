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
import java.util.Iterator;
import java.util.List;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.parser.JSONParser;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Utilities used by "JSON" object implementation.
 */
public final class JSONFunctions {
    private JSONFunctions() {}
    private static final MethodHandle REVIVER_INVOKER = Bootstrap.createDynamicInvoker("dyn:call", Object.class,
            ScriptFunction.class, ScriptObject.class, String.class, Object.class);

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
        final Context    context = Context.getContextTrusted();
        final JSONParser parser  = new JSONParser(
                new Source("<json>", str),
                new Context.ThrowErrorManager(),
                (context != null) ?
                    context.getEnv()._strict :
                    false);

        Node node;

        try {
            node = parser.parse();
        } catch (final ParserException e) {
            throw ECMAErrors.syntaxError(e, "invalid.json", e.getMessage());
        }

        final ScriptObject global = Context.getGlobalTrusted();
        Object unfiltered = convertNode(global, node);
        return applyReviver(global, unfiltered, reviver);
    }

    // -- Internals only below this point

    // parse helpers

    // apply 'reviver' function if available
    private static Object applyReviver(final ScriptObject global, final Object unfiltered, final Object reviver) {
        if (reviver instanceof ScriptFunction) {
            assert global instanceof GlobalObject;
            final ScriptObject root = ((GlobalObject)global).newObject();
            root.set("", unfiltered, root.isStrictContext());
            return walk(root, "", (ScriptFunction)reviver);
        }
        return unfiltered;
    }

    // This is the abstract "Walk" operation from the spec.
    private static Object walk(final ScriptObject holder, final Object name, final ScriptFunction reviver) {
        final Object val = holder.get(name);
        if (val instanceof ScriptObject) {
            final ScriptObject     valueObj = (ScriptObject)val;
            final boolean          strict   = valueObj.isStrictContext();
            final Iterator<String> iter     = valueObj.propertyIterator();

            while (iter.hasNext()) {
                final String key        = iter.next();
                final Object newElement = walk(valueObj, key, reviver);

                if (newElement == ScriptRuntime.UNDEFINED) {
                    valueObj.delete(key, strict);
                } else {
                    valueObj.set(key, newElement, strict);
                }
            }
        }

        try {
             // Object.class, ScriptFunction.class, ScriptObject.class, String.class, Object.class);
             return REVIVER_INVOKER.invokeExact(reviver, holder, JSType.toString(name), val);
        } catch(Error|RuntimeException t) {
            throw t;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Converts IR node to runtime value
    private static Object convertNode(final ScriptObject global, final Node node) {
        assert global instanceof GlobalObject;

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
                    return ((GlobalObject)global).wrapAsObject(values);
                }

                final Object[] values = new Object[elements.length];
                int   index = 0;

                for (final Node elem : elements) {
                    values[index++] = convertNode(global, elem);
                }

                return ((GlobalObject)global).wrapAsObject(values);
            }

            return ((LiteralNode<?>)node).getValue();

        } else if (node instanceof ObjectNode) {
            final ObjectNode   objNode  = (ObjectNode) node;
            final ScriptObject object   = ((GlobalObject)global).newObject();
            final boolean      strict   = global.isStrictContext();
            final List<Node>   elements = objNode.getElements();

            for (final Node elem : elements) {
                final PropertyNode pNode     = (PropertyNode) elem;
                final Node         valueNode = pNode.getValue();

                object.set(pNode.getKeyName(), convertNode(global, valueNode), strict);
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
}
