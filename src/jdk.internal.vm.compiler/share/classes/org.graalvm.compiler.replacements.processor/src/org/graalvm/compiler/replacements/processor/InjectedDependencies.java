/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.processor;

import static org.graalvm.compiler.processor.AbstractProcessor.getAnnotationValue;
import static org.graalvm.compiler.replacements.processor.NodeIntrinsicHandler.NODE_INTRINSIC_CLASS_NAME;

import java.util.HashMap;
import java.util.Iterator;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.graalvm.compiler.processor.AbstractProcessor;

public class InjectedDependencies implements Iterable<InjectedDependencies.Dependency> {

    public interface Dependency {

        String getName(AbstractProcessor processor, ExecutableElement inject);

        String getExpression(AbstractProcessor processor, ExecutableElement inject);

        String getType();
    }

    private abstract static class DependencyImpl implements Dependency {

        private final String name;
        private final String type;

        private DependencyImpl(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public abstract String getExpression(AbstractProcessor processor, ExecutableElement inject);

        @Override
        public String getName(AbstractProcessor processor, ExecutableElement inject) {
            return name;
        }

        @Override
        public String getType() {
            return type;
        }
    }

    protected static final class InjectedDependency extends DependencyImpl {

        protected InjectedDependency(String name, String type) {
            super(name, type);
        }

        @Override
        public String getExpression(AbstractProcessor processor, ExecutableElement inject) {
            return String.format("injection.getInjectedArgument(%s.class)", getType());
        }
    }

    private static final class InjectedStampDependency extends DependencyImpl {

        private InjectedStampDependency() {
            super("stamp", "org.graalvm.compiler.core.common.type.Stamp");
        }

        @Override
        public String getExpression(AbstractProcessor processor, ExecutableElement inject) {
            AnnotationMirror nodeIntrinsic = processor.getAnnotation(inject, processor.getType(NODE_INTRINSIC_CLASS_NAME));
            boolean nonNull = nodeIntrinsic != null && getAnnotationValue(nodeIntrinsic, "injectedStampIsNonNull", Boolean.class);
            return String.format("injection.getInjectedStamp(%s.class, %s)", GeneratedPlugin.getErasedType(inject.getReturnType()), nonNull);
        }
    }

    public enum WellKnownDependency implements Dependency {
        CONSTANT_REFLECTION("b.getConstantReflection()", "jdk.vm.ci.meta.ConstantReflectionProvider"),
        META_ACCESS("b.getMetaAccess()", "jdk.vm.ci.meta.MetaAccessProvider"),
        ASSUMPTIONS("b.getAssumptions()", "jdk.vm.ci.meta.Assumptions"),
        OPTIONVALUES("b.getOptions()", "org.graalvm.compiler.options.OptionValues"),
        INJECTED_STAMP(new InjectedStampDependency()),
        SNIPPET_REFLECTION(new InjectedDependency("snippetReflection", "org.graalvm.compiler.api.replacements.SnippetReflectionProvider")),
        STAMP_PROVIDER("b.getStampProvider()", "org.graalvm.compiler.nodes.spi.StampProvider"),
        INTRINSIC_CONTEXT("b.getIntrinsic()", "org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext"),
        STRUCTURED_GRAPH("b.getGraph()", "org.graalvm.compiler.nodes.StructuredGraph");

        private final String expr;
        private final String type;
        protected final DependencyImpl generateMember;

        WellKnownDependency(String expr, String type) {
            this.expr = expr;
            this.type = type;
            this.generateMember = null;
        }

        WellKnownDependency(DependencyImpl generateMember) {
            this.expr = null;
            this.type = generateMember.getType();
            this.generateMember = generateMember;
        }

        protected TypeMirror getType(AbstractProcessor processor) {
            return processor.getType(type);
        }

        @Override
        public String getExpression(AbstractProcessor processor, ExecutableElement inject) {
            if (generateMember != null) {
                return generateMember.getExpression(processor, inject);
            }
            return expr;
        }

        @Override
        public String getName(AbstractProcessor processor, ExecutableElement inject) {
            if (generateMember != null) {
                return generateMember.getName(processor, inject);
            }
            return expr;
        }

        @Override
        public String getType() {
            if (generateMember != null) {
                return generateMember.getType();
            }
            return type;
        }
    }

    protected final HashMap<String, Dependency> deps;
    protected final boolean useVariables;
    protected final ExecutableElement intrinsicMethod;

    public InjectedDependencies(boolean useVariables, ExecutableElement intrinsicMethod) {
        this.useVariables = useVariables;
        this.intrinsicMethod = intrinsicMethod;
        deps = new HashMap<>();
    }

    public String use(AbstractProcessor processor, WellKnownDependency wellKnown) {
        if (wellKnown.generateMember != null) {
            deps.put(wellKnown.type, wellKnown.generateMember);
        }
        if (useVariables) {
            return wellKnown.getName(processor, intrinsicMethod) + "/* A " + wellKnown + " */";
        } else {
            return wellKnown.getExpression(processor, intrinsicMethod) + "/* B " + wellKnown + " */";
        }
    }

    public Dependency find(AbstractProcessor processor, DeclaredType type) {
        for (WellKnownDependency wellKnown : WellKnownDependency.values()) {
            if (processor.env().getTypeUtils().isAssignable(wellKnown.getType(processor), type)) {
                use(processor, wellKnown);
                return wellKnown;
            }
        }

        String typeName = type.toString();
        Dependency ret = deps.get(typeName);
        if (ret == null) {
            ret = new InjectedDependency("injected" + type.asElement().getSimpleName(), typeName);
            deps.put(typeName, ret);
        }
        return ret;
    }

    public String use(AbstractProcessor processor, DeclaredType type) {
        return find(processor, type).getName(processor, intrinsicMethod);
    }

    public String inject(AbstractProcessor processor, DeclaredType type) {
        return find(processor, type).getExpression(processor, intrinsicMethod);
    }

    @Override
    public Iterator<Dependency> iterator() {
        return deps.values().iterator();
    }

    public boolean isEmpty() {
        return deps.isEmpty();
    }
}
