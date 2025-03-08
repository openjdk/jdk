/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The {@link TestClassInstantiator} is a utility class, which generates a class, and allows instantiating
 * multiple {@link Template}s, for example in the static block, the main method, or further below in the
 * test section.
 *
 * First create a {@link TestClassInstantiator}, then {@link add} one or multiple {@link Template}s to it
 * and finally {@link instantiate} the class which generates a {@link String} from all generated code.
 */
public final class TestClassInstantiator {
    private boolean isUsed = false;
    private final BaseScope baseScope;
    private final ClassScope classScope;
    private final Scope staticsScope;
    private final Scope mainScope;
    private final MethodScope mainMethodScope;

    /**
     * Create a new {@link TestClassInstantiator} for a specific class, using the {@link CodeGeneratorLibrary#standard}.
     *
     * @param packageName Name of the package for the class.
     * @param className Name of the class.
     */
    public TestClassInstantiator(String packageName, String className) {
        this(packageName, className, null);
    }

    /**
     * Create a new {@link TestClassInstantiator} for a specific class, using the specified library.
     *
     * @param packageName Name of the package for the class.
     * @param className Name of the class.
     * @param codeGeneratorLibrary The library to be used for finding CodeGenerators in recursive instantiations.
     */
    public TestClassInstantiator(String packageName, String className, CodeGeneratorLibrary codeGeneratorLibrary) {
        this(packageName, className, codeGeneratorLibrary, new HashSet<String>());
    }

    /**
     * Create a new {@link TestClassInstantiator} for a specific class, using the specified library and
     * with a set of classes to import.
     *
     * @param packageName Name of the package for the class.
     * @param className Name of the class.
     * @param codeGeneratorLibrary The library to be used for finding CodeGenerators in recursive instantiations.
     * @param imports Set of imported classes.
     */
    public TestClassInstantiator(String packageName, String className, CodeGeneratorLibrary codeGeneratorLibrary, HashSet<String> imports) {
        // Open the base scope.
        baseScope = new BaseScope(codeGeneratorLibrary);
        baseScope.setDebugContext("for TestClassInstantiator", null);

        // package
        new Template("test_class_instantiator_package",
            """
            package #{packageName};
            """
        ).where("packageName", packageName).instantiate(baseScope);

        // import
        Template importTemplate = new Template("test_class_instantiator_import",
            """
            import #{name};
            """
        );
        for (String name : imports) {
            importTemplate.where("name", name).instantiate(baseScope);
        }

        // Open class
        new Template("test_class_instantiator_open",
            """
            public class #{className} {
            """
        ).where("className", className).instantiate(baseScope);
        baseScope.stream.indent();

        // Open the class scope
        // Inside we have:
        //  - statics scope: add all the statics templates
        //  - main scope: add all the main templates
        //  - and all the testTemplates go directly into the classScope
        classScope = new ClassScope(baseScope, baseScope.fuel);
        classScope.setDebugContext("for TestClassInstantiator", null);

        staticsScope = new Scope(classScope, classScope.fuel);
        staticsScope.setDebugContext("for TestClassInstantiator statics", null);

        mainScope = new Scope(classScope, classScope.fuel);
        mainScope.setDebugContext("for TestClassInstantiator main", null);
        new Template("test_class_instantiator_open_main",
            """
            public static void main(String[] args) {
            """
        ).instantiate(mainScope);
        mainScope.stream.indent();

        mainMethodScope = new MethodScope(mainScope, mainScope.fuel);
        mainMethodScope.setDebugContext("inside main for TestClassInstantiator", null);
    }

    /**
     * Helper class for adding templates into the static, main or test block.
     */
    public final class Instantiator {
        private boolean isUsed = false;
        private TestClassInstantiator parent;
        private final Template staticTemplate = null;
        private final Template mainTemplate = null;
        private final Template testTemplate = null;
        private final HashMap<String,List<String>> parameterMap = new HashMap<String,List<String>>();
        private int repeatCount = 1;

        Instantiator(TestClassInstantiator parent) {
            this.parent = parent;
        }

        /**
         * Add templates to the static, main and test block of the {@link TestClassInstantiator} class.
         * One can selectively provide a {@link Template} if one is to be added, or {@code null} if not.
         *
         * @param staticTemplate Template for static block, or {@code null} if not to be added.
         * @param mainTemplate Template for main block, or {@code null} if not to be added.
         * @param testTemplate Template for test block, or {@code null} if not to be added.
         */
        public void add(Template staticTemplate, Template mainTemplate, Template testTemplate) {
            if (isUsed) {
                throw new TemplateFrameworkException("Repeated use of Instantiator not allowed.");
            }
            isUsed = true;

            ArrayList<Parameters> setOfParameters = parametersCrossProduct();
            for (Parameters p : setOfParameters) {
                for (int i = 0; i < repeatCount; i++) {
                    // If we have more than 1 set, we must clone the parameters, so that we get a unique ID.
                    p = (i == 0) ? p : new Parameters(p.getParameterMap());
                    generate(staticTemplate, mainTemplate, testTemplate, p);
                }
            }
        }

        private ArrayList<Parameters> parametersCrossProduct() {
            String[] keys = parameterMap.keySet().toArray(new String[0]);
            ArrayList<Parameters> setOfParameters = new ArrayList<Parameters>();
            setOfParameters.add(new Parameters());
            return parametersCrossProduct(keys, 0, setOfParameters);
        }

        private ArrayList<Parameters> parametersCrossProduct(String[] keys, int keysPos, ArrayList<Parameters> setOfParameters) {
            if (keysPos == keys.length) {
                return setOfParameters;
            }
            ArrayList<Parameters> newSet = new ArrayList<Parameters>();
            String key = keys[keysPos];
            List<String> values = parameterMap.get(key);
            for (Parameters pOld : setOfParameters) {
                for (String v : values) {
                    Parameters p = new Parameters(pOld.getParameterMap());
                    p.add(key, v);
                    newSet.add(p);
                }
            }
            return parametersCrossProduct(keys, keysPos + 1, newSet);
        }

        private void generate(Template staticTemplate, Template mainTemplate, Template testTemplate,
                              Parameters parameters) {
            // The 3 instantiations share the same parameters, and the ReplacementState. This ensures
            // that the variable names and replacements are shared among the 3 instantiations.
            Template.ReplacementState replacementState = new Template.ReplacementState();
            if (staticTemplate != null) {
                Scope staticsSubScope = new Scope(staticsScope, staticsScope.fuel);
                staticTemplate.instantiate(staticsSubScope, parameters, replacementState);
                staticsSubScope.stream.addNewline();
                staticsSubScope.close();
                staticsScope.stream.addCodeStream(staticsSubScope.stream);
            }

            if (mainTemplate != null) {
                Scope mainSubScope = new Scope(mainMethodScope, mainMethodScope.fuel);
                mainTemplate.instantiate(mainSubScope, parameters, replacementState);
                mainSubScope.close();
                mainMethodScope.stream.addCodeStream(mainSubScope.stream);
            }

            if (testTemplate != null) {
                Scope testScope = new Scope(classScope, classScope.fuel);
                testTemplate.instantiate(testScope, parameters, replacementState);
                testScope.stream.addNewline();
                testScope.close();
                classScope.stream.addCodeStream(testScope.stream);
            }
        }

        /**
         * Add a parameter key-value pair.
         *
         * @param paramKey The name of the parameter.
         * @param paramValue The value to be set.
         * @return The Instantiator for chaining.
         */
        public Instantiator where(String paramKey, String paramValue) {
            if (parameterMap.containsKey(paramKey)) {
                throw new TemplateFrameworkException("Duplicate parameter key: " + paramKey);
            }
            parameterMap.put(paramKey, Arrays.asList(paramValue));
            return this;
        }

        /**
         * Add a list of values for a given parameter name, creating an instantiation for every value.
         * Note: if multiple {@link where} specify a list, then the cross-product of all these parameter
         *       sets is generated, and an instantiation is created for each.
         *
         * @param paramKey The name of the parameter.
         * @param paramValues List of parameter values.
         * @return The Instantiator for chaining.
         */
        public Instantiator where(String paramKey, List<String> paramValues) {
            if (parameterMap.containsKey(paramKey)) {
                throw new TemplateFrameworkException("Duplicate parameter key: " + paramKey);
            }
            parameterMap.put(paramKey, paramValues);
            return this;
        }

        /**
         * Repeat every instantiation {@code repeatCount} times, which is useful to instantiate {@link Template}s
         * with random components multiple times.
         *
         * @param repeatCount Number of times every instantiation is repeated.
         * @return The Instantiator for chaining.
         */
        public Instantiator repeat(int repeatCount) {
            if (repeatCount < 2 || repeatCount > 1000) {
                throw new TemplateFrameworkException("Bad repeat count: " + repeatCount + " should be 2..1000");
            }
            if (this.repeatCount > 1) {
                throw new TemplateFrameworkException("Repeat count already set.");
            }
            this.repeatCount = repeatCount;
            return this;
        }
    }

    /**
     * Add templates to the static, main and test block of the {@link TestClassInstantiator} class.
     * One can selectively provide a {@link Template} if one is to be added, or {@code null} if not.
     *
     * @param staticTemplate Template for static block, or {@code null} if not to be added.
     * @param mainTemplate Template for main block, or {@code null} if not to be added.
     * @param testTemplate Template for test block, or {@code null} if not to be added.
     */
    public void add(Template staticTemplate, Template mainTemplate, Template testTemplate) {
        new Instantiator(this).add(staticTemplate, mainTemplate, testTemplate);
    }

    /**
     * Create an {@link Instantiator}, which already has a first parameter key-value pair.
     *
     * @param paramKey The name of the parameter.
     * @param paramValue The value to be set.
     * @return The Instantiator.
     */
    public Instantiator where(String paramKey, String paramValue) {
        return new Instantiator(this).where(paramKey, paramValue);
    }

    /**
     * Create an {@link Instantiator}, which already has a list of values for a parameter name.
     * Note: if multiple {@link where} specify a list, then the cross-product of all these parameter
     *       sets is generated, and an instantiation is created for each.
     *
     * @param paramKey The name of the parameter.
     * @param paramValues List of parameter values.
     * @return The Instantiator for chaining.
     */
    public Instantiator where(String paramKey, List<String> paramValues) {
        return new Instantiator(this).where(paramKey, paramValues);
    }

    /**
     * Repeat every instantiation {@code repeatCount} times, which is useful to instantiate {@link Template}s
     * with random components multiple times.
     *
     * @param repeatCount Number of times every instantiation is repeated.
     * @return The Instantiator for chaining.
     */
    public Instantiator repeat(int repeatCount) {
        return new Instantiator(this).repeat(repeatCount);
    }

    /**
     * Instantiate the class with all the added templates, and return all the generated code in a String.
     *
     * @return The {@link String} of generated code.
     */
    public String instantiate() {
        if (isUsed) {
            throw new TemplateFrameworkException("Repeated use of Instantiator not allowed.");
        }

        mainMethodScope.close();
        mainScope.stream.addCodeStream(mainMethodScope.stream);

        mainScope.stream.outdent();
        mainScope.stream.addNewline();
        mainScope.stream.addCodeToLine("}");
        mainScope.stream.addNewline();
        mainScope.stream.addNewline();
        mainScope.close();
        classScope.stream.prependCodeStream(mainScope.stream);

        staticsScope.close();
        classScope.stream.prependCodeStream(staticsScope.stream);

        classScope.close();
        baseScope.stream.addCodeStream(classScope.stream);

        baseScope.stream.outdent();
        baseScope.stream.addCodeToLine("}");
        baseScope.close();
        return baseScope.toString();
    }
}
