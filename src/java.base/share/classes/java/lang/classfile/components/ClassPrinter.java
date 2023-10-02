/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.components;

import java.lang.constant.ConstantDesc;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CompoundElement;

import jdk.internal.classfile.impl.ClassPrinterImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A printer of classfiles and its elements.
 * <p>
 * Any {@link ClassModel}, {@link FieldModel}, {@link MethodModel}, or {@link CodeModel}
 * can be printed to a human-readable structured text in JSON, XML, or YAML format.
 * Or it can be exported into a tree of traversable and printable nodes,
 * more exactly into a tree of {@link MapNode}, {@link ListNode}, and {@link LeafNode} instances.
 * <p>
 * Level of details to print or to export is driven by {@link Verbosity} option.
 * <p>
 * Printing is for debugging purposes only. Printed text schema, tree content and structure
 * not guaranteed. It may change anytime in a future.
 * <p>
 * The most frequent use case is to simply print a class:
 * {@snippet lang="java" class="PackageSnippets" region="printClass"}
 * <p>
 * {@link ClassPrinter} allows to traverse tree of simple printable nodes to hook custom printer:
 * {@snippet lang="java" class="PackageSnippets" region="customPrint"}
 * <p>
 * Another use case for {@link ClassPrinter} is to simplify writing of automated tests:
 * {@snippet lang="java" class="PackageSnippets" region="printNodesInTest"}
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public final class ClassPrinter {

    private ClassPrinter() {
    }

    /**
     * Level of detail to print or export.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public enum Verbosity {

        /**
         * Only top level class info, class members and attribute names are printed.
         */
        MEMBERS_ONLY,

        /**
         * Top level class info, class members, and critical attributes are printed.
         * <p>
         * Critical attributes are:
         * <ul>
         * <li>ConstantValue
         * <li>Code
         * <li>StackMapTable
         * <li>BootstrapMethods
         * <li>NestHost
         * <li>NestMembers
         * <li>PermittedSubclasses
         * </ul>
         * @jvms 4.7 Attributes
         */
        CRITICAL_ATTRIBUTES,

        /**
         * All class content is printed, including constant pool.
         */
        TRACE_ALL }

    /**
     * Named, traversable, and printable node parent.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public sealed interface Node {

        /**
         * Printable name of the node.
         * @return name of the node
         */
        ConstantDesc name();

        /**
         * Walks through the underlying tree.
         * @return ordered stream of nodes
         */
        Stream<Node> walk();

        /**
         * Prints the node and its sub-tree into JSON format.
         * @param out consumer of the printed fragments
         */
        default void toJson(Consumer<String> out) {
            ClassPrinterImpl.toJson(this, out);
        }

        /**
         * Prints the node and its sub-tree into XML format.
         * @param out consumer of the printed fragments
         */
        default void toXml(Consumer<String> out) {
            ClassPrinterImpl.toXml(this, out);
        }

        /**
         * Prints the node and its sub-tree into YAML format.
         * @param out consumer of the printed fragments
         */
        default void toYaml(Consumer<String> out) {
            ClassPrinterImpl.toYaml(this, out);
        }
    }

    /**
     * A leaf node holding single printable value.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public sealed interface LeafNode extends Node
            permits ClassPrinterImpl.LeafNodeImpl {

        /**
         * Printable node value
         * @return node value
         */
        ConstantDesc value();
    }

    /**
     * A tree node holding {@link List} of nested nodes.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public sealed interface ListNode extends Node, List<Node>
            permits ClassPrinterImpl.ListNodeImpl {
    }

    /**
     * A tree node holding {@link Map} of nested nodes.
     * <p>
     * Each {@link Map.Entry#getKey()} == {@link Map.Entry#getValue()}.{@link #name()}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public sealed interface MapNode extends Node, Map<ConstantDesc, Node>
            permits ClassPrinterImpl.MapNodeImpl {
    }

    /**
     * Exports provided model into a tree of printable nodes.
     * @param model a {@link ClassModel}, {@link FieldModel}, {@link MethodModel}, or {@link CodeModel} to export
     * @param verbosity level of details to export
     * @return root node of the exported tree
     */
    public static MapNode toTree(CompoundElement<?> model, Verbosity verbosity) {
        return ClassPrinterImpl.modelToTree(model, verbosity);
    }

    /**
     * Prints provided model as structured text in JSON format.
     * @param model a {@link ClassModel}, {@link FieldModel}, {@link MethodModel}, or {@link CodeModel} to print
     * @param verbosity level of details to print
     * @param out consumer of the print fragments
     */
    public static void toJson(CompoundElement<?> model, Verbosity verbosity, Consumer<String> out) {
        toTree(model, verbosity).toJson(out);
    }

    /**
     * Prints provided model as structured text in XML format.
     * @param model a {@link ClassModel}, {@link FieldModel}, {@link MethodModel}, or {@link CodeModel} to print
     * @param verbosity level of details to print
     * @param out consumer of the print fragments
     */
    public static void toXml(CompoundElement<?> model, Verbosity verbosity, Consumer<String> out) {
        toTree(model, verbosity).toXml(out);
    }

    /**
     * Prints provided model as structured text in YAML format.
     * @param model a {@link ClassModel}, {@link FieldModel}, {@link MethodModel}, or {@link CodeModel} to print
     * @param verbosity level of details to print
     * @param out consumer of the print fragments
     */
    public static void toYaml(CompoundElement<?> model, Verbosity verbosity, Consumer<String> out) {
        toTree(model, verbosity).toYaml(out);
    }
}
