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

package jdk.nashorn.internal.ir.debug;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.Reference;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;

/**
 * AST-as-text visualizer. Sometimes you want tree form and not source
 * code. This works for both lowered and unlowered IR
 *
 * see the flags --print-ast and --print-ast-lower
 */
public final class ASTWriter {
    /** Root node from which to start the traversal */
    private final Node root;

    private static final int TABWIDTH = 4;

    /**
     * Constructor
     * @param root root of the AST to visualize
     */
    public ASTWriter(final Node root) {
        this.root = root;
    }

    /**
     * Use the ASTWriter by instantiating it and retrieving its String
     * representation
     *
     * @return the string representation of the AST
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        printAST(sb, null, null, "root", root, 0);
        return sb.toString();
    }

    /**
     * Return the visited nodes in an ordered list
     * @return the list of nodes in order
     */
    public Node[] toArray() {
        final List<Node> preorder = new ArrayList<>();
        printAST(new StringBuilder(), preorder, null, "root", root, 0);
        return preorder.toArray(new Node[preorder.size()]);
    }

    @SuppressWarnings("unchecked")
    private void printAST(final StringBuilder sb, final List<Node> preorder, final Field field, final String name, final Node node, final int indent) {
        ASTWriter.indent(sb, indent);
        if (node == null) {
            sb.append("[Object ");
            sb.append(name);
            sb.append(" null]\n");
            return;
        }

        if (preorder != null) {
            preorder.add(node);
        }

        final boolean isReference = field != null && field.getAnnotation(Reference.class) != null;

        Class<?> clazz = node.getClass();
        String   type  = clazz.getName();

        type = type.substring(type.lastIndexOf('.') + 1, type.length());
        if (isReference) {
            type = "ref: " + type;
        }
        type += "@" + Debug.id(node);
        final Symbol symbol;
        if(node instanceof Expression) {
            symbol = ((Expression)node).getSymbol();
        } else {
            symbol = null;
        }

        if (symbol != null) {
            type += "#" + symbol;
        }

        if (node instanceof Block && ((Block)node).needsScope()) {
            type += " <scope>";
        }

        final List<Field> children = new LinkedList<>();

        if (!isReference) {
            enqueueChildren(node, clazz, children);
        }

        String status = "";

        if (node.isTerminal()) {
            status += " Terminal";
        }

        if (node.hasGoto()) {
            status += " Goto ";
        }

        if (symbol != null) {
            status += symbol;
        }

        status = status.trim();
        if (!"".equals(status)) {
            status = " [" + status + "]";
        }

        if (symbol != null) {
            String tname = ((Expression)node).getType().toString();
            if (tname.indexOf('.') != -1) {
                tname = tname.substring(tname.lastIndexOf('.') + 1, tname.length());
            }
            status += " (" + tname + ")";
        }

        if (children.isEmpty()) {
            sb.append("[").
                append(type).
                append(' ').
                append(name).
                append(" = '").
                append(node).
                append("'").
                append(status).
                append("] ").
                append('\n');
        } else {
            sb.append("[").
                append(type).
                append(' ').
                append(name).
                append(' ').
                append(Token.toString(node.getToken())).
                append(status).
                append("]").
                append('\n');

            for (final Field child : children) {
                if (child.getAnnotation(Ignore.class) != null) {
                    continue;
                }

                Object value;
                try {
                    value = child.get(node);
                } catch (final IllegalArgumentException | IllegalAccessException e) {
                    Context.printStackTrace(e);
                    return;
                }

                if (value instanceof Node) {
                    printAST(sb, preorder, child, child.getName(), (Node)value, indent + 1);
                } else if (value instanceof Collection) {
                    int pos = 0;
                    ASTWriter.indent(sb, indent + 1);
                    sb.append("[Collection ").
                        append(child.getName()).
                        append("[0..").
                        append(((Collection<Node>)value).size()).
                        append("]]").
                        append('\n');

                    for (final Node member : (Collection<Node>)value) {
                        printAST(sb, preorder, child, child.getName() + "[" + pos++ + "]", member, indent + 2);
                    }
                }
            }
        }
    }

    private static void enqueueChildren(final Node node, final Class<?> nodeClass, final List<Field> children) {
        final Deque<Class<?>> stack = new ArrayDeque<>();

        /**
         * Here is some ugliness that can be overcome by proper ChildNode annotations
         * with proper orders. Right now we basically sort all classes up to Node
         * with super class first, as this often is the natural order, e.g. base
         * before index for an IndexNode.
         *
         * Also there are special cases as this is not true for UnaryNodes(lhs) and
         * BinaryNodes extends UnaryNode (with lhs), and TernaryNodes.
         *
         * TODO - generalize traversal with an order built on annotations and this
         * will go away.
         */
        Class<?> clazz = nodeClass;
        do {
            stack.push(clazz);
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        if (node instanceof TernaryNode) {
            // HACK juggle "third"
            stack.push(stack.removeLast());
        }
        // HACK change operator order for BinaryNodes to get lhs first.
        final Iterator<Class<?>> iter = node instanceof BinaryNode ? stack.descendingIterator() : stack.iterator();

        while (iter.hasNext()) {
            final Class<?> c = iter.next();
            for (final Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    final Object child = f.get(node);
                    if (child == null) {
                        continue;
                    }

                    if (child instanceof Node) {
                        children.add(f);
                    } else if (child instanceof Collection) {
                        if (!((Collection<?>)child).isEmpty()) {
                            children.add(f);
                        }
                    }
                } catch (final IllegalArgumentException | IllegalAccessException e) {
                    return;
                }
            }
        }
    }

    private static void indent(final StringBuilder sb, final int indent) {
        for (int i = 0; i < indent; i++) {
            for (int j = 0; j < TABWIDTH; j++) {
                sb.append(' ');
            }
        }
    }
}
