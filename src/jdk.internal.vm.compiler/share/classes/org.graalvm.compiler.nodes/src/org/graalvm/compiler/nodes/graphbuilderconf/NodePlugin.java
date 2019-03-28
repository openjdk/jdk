/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.nodes.graphbuilderconf;

import org.graalvm.compiler.graph.Node.ValueNumberable;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public interface NodePlugin extends GraphBuilderPlugin {
    /**
     * Handle the parsing of a method invocation bytecode to a method that can be bound statically.
     * If the method returns true, it must {@link GraphBuilderContext#push push} a value as the
     * result of the method invocation using the {@link Signature#getReturnKind return kind} of the
     * method.
     *
     * @param b the context
     * @param method the statically bound, invoked method
     * @param args the arguments of the method invocation
     * @return true if the plugin handles the invocation, false otherwise
     */
    default boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return false;
    }

    /**
     * Handle the parsing of a GETFIELD bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value using the
     * {@link ResolvedJavaField#getJavaKind() kind} of the field.
     *
     * @param b the context
     * @param object the receiver object for the field access
     * @param field the accessed field
     * @return true if the plugin handles the field access, false otherwise
     */
    default boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        return false;
    }

    /**
     * Handle the parsing of a GETSTATIC bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value using the
     * {@link ResolvedJavaField#getJavaKind() kind} of the field.
     *
     * @param b the context
     * @param field the accessed field
     * @return true if the plugin handles the field access, false otherwise
     */
    default boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        return false;
    }

    /**
     * Handle the parsing of a PUTFIELD bytecode.
     *
     * @param b the context
     * @param object the receiver object for the field access
     * @param field the accessed field
     * @param value the value to be stored into the field
     * @return true if the plugin handles the field access, false otherwise
     */
    default boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        return false;
    }

    /**
     * Handle the parsing of a PUTSTATIC bytecode.
     *
     * @param b the context
     * @param field the accessed field
     * @param value the value to be stored into the field
     * @return true if the plugin handles the field access, false otherwise.
     */
    default boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        return false;
    }

    /**
     * Handle the parsing of an array load bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value using the provided elementKind.
     *
     * @param b the context
     * @param array the accessed array
     * @param index the index for the array access
     * @param boundsCheck the explicit bounds check already emitted, or null if no bounds check was
     *            emitted yet
     * @param elementKind the element kind of the accessed array
     * @return true if the plugin handles the array access, false otherwise.
     */
    default boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        return false;
    }

    /**
     * Handle the parsing of an array store bytecode.
     *
     * @param b the context
     * @param array the accessed array
     * @param index the index for the array access
     * @param boundsCheck the explicit array bounds check already emitted, or null if no array
     *            bounds check was emitted yet
     * @param storeCheck the explicit array store check already emitted, or null if no array store
     *            check was emitted yet
     * @param elementKind the element kind of the accessed array
     * @param value the value to be stored into the array
     * @return true if the plugin handles the array access, false otherwise.
     */
    default boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind elementKind, ValueNode value) {
        return false;
    }

    /**
     * Handle the parsing of a CHECKCAST bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the cast using
     * {@link JavaKind#Object}.
     *
     * @param b the context
     * @param object the object to be type checked
     * @param type the type that the object is checked against
     * @param profile the profiling information for the type check, or null if no profiling
     *            information is available
     * @return true if the plugin handles the cast, false otherwise
     */
    default boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        return false;
    }

    /**
     * Handle the parsing of a INSTANCEOF bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the instanceof using
     * {@link JavaKind#Int}.
     *
     * @param b the context
     * @param object the object to be type checked
     * @param type the type that the object is checked against
     * @param profile the profiling information for the type check, or null if no profiling
     *            information is available
     * @return true if the plugin handles the instanceof, false otherwise
     */
    default boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        return false;
    }

    /**
     * Handle the parsing of a NEW bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the allocation using
     * {@link JavaKind#Object}.
     *
     * @param b the context
     * @param type the type to be instantiated
     * @return true if the plugin handles the bytecode, false otherwise
     */
    default boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        return false;
    }

    /**
     * Handle the parsing of a NEWARRAY and ANEWARRAY bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the allocation using
     * {@link JavaKind#Object}.
     *
     * @param b the context
     * @param elementType the element type of the array to be instantiated
     * @param length the length of the new array
     * @return true if the plugin handles the bytecode, false otherwise
     */
    default boolean handleNewArray(GraphBuilderContext b, ResolvedJavaType elementType, ValueNode length) {
        return false;
    }

    /**
     * Handle the parsing of a MULTIANEWARRAY bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the allocation using
     * {@link JavaKind#Object}.
     *
     * @param b the context
     * @param type the type of the outermost array to be instantiated
     * @param dimensions the array of lengths for all the dimensions to be instantiated
     * @return true if the plugin handles the bytecode, false otherwise
     */
    default boolean handleNewMultiArray(GraphBuilderContext b, ResolvedJavaType type, ValueNode[] dimensions) {
        return false;
    }

    /**
     * Allows this plugin to add nodes after the exception object has been loaded in the dispatch
     * sequence. Note that a {@link StructuredGraph} is provided to this call instead of a
     * {@link GraphBuilderContext} so that the caller has a guarantee that its current control flow
     * insertion point is not changed by this call. This means nodes must be added to the graph with
     * the appropriate method (e.g., {@link StructuredGraph#unique} for {@link ValueNumberable}
     * nodes) and fixed nodes must be manually {@linkplain FixedWithNextNode#setNext added} as
     * successors of {@code afterExceptionLoaded}.
     *
     * The reason for this constraint is that when this plugin runs, it's inserting instructions
     * into a different block than the one currently being parsed.
     *
     * @param graph the graph being parsed
     * @param afterExceptionLoaded the last fixed node after loading the exception
     * @return the last fixed node after instrumentation
     */
    default FixedWithNextNode instrumentExceptionDispatch(StructuredGraph graph, FixedWithNextNode afterExceptionLoaded) {
        return afterExceptionLoaded;
    }

    /**
     * If the plugin {@link GraphBuilderContext#push pushes} a value with a different
     * {@link JavaKind} than specified by the bytecode, it must override this method and return
     * {@code true}. This disables assertion checking for value kinds.
     *
     * @param b the context
     */
    default boolean canChangeStackKind(GraphBuilderContext b) {
        return false;
    }
}

