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
package jdk.classfile.components;

import java.lang.constant.ConstantDesc;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.classfile.CompoundElement;

import jdk.classfile.impl.ClassPrinterImpl;

/**
 *
 */
public final class ClassPrinter {

    public enum Verbosity { MEMBERS_ONLY, CRITICAL_ATTRIBUTES, TRACE_ALL }

    public sealed interface Node {

        public ConstantDesc name();

        public Stream<Node> walk();

        default public void toJson(Consumer<String> out) {
            ClassPrinterImpl.toJson(this, out);
        }

        default public void toXml(Consumer<String> out) {
            ClassPrinterImpl.toXml(this, out);
        }

        default public void toYaml(Consumer<String> out) {
            ClassPrinterImpl.toYaml(this, out);
        }
    }

    public sealed interface LeafNode extends Node
            permits ClassPrinterImpl.LeafNodeImpl {

        public ConstantDesc value();
    }

    public sealed interface ListNode extends Node, List<Node>
            permits ClassPrinterImpl.ListNodeImpl {
    }

    public sealed interface MapNode extends Node, Map<ConstantDesc, Node>
            permits ClassPrinterImpl.MapNodeImpl {
    }

    public static MapNode toTree(CompoundElement<?> model, Verbosity verbosity) {
        return ClassPrinterImpl.modelToTree(model, verbosity);
    }

    public static void toJson(CompoundElement<?> model, Verbosity verbosity, Consumer<String> out) {
        toTree(model, verbosity).toJson(out);
    }

    public static void toXml(CompoundElement<?> model, Verbosity verbosity, Consumer<String> out) {
        toTree(model, verbosity).toXml(out);
    }

    public static void toYaml(CompoundElement<?> model, Verbosity verbosity, Consumer<String> out) {
        toTree(model, verbosity).toYaml(out);
    }
}
