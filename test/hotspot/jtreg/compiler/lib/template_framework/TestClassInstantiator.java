/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * TODO
 */
public final class TestClassInstantiator {
    private boolean isUsed = false;
    private final BaseScope baseScope;
    private final ClassScope classScope;
    private final Scope staticsScope;
    private final Scope mainScope;

    public TestClassInstantiator(String packageName, String className) {
        // Open the base scope, and open the class.
        baseScope = new BaseScope();
        baseScope.setDebugContext("for TestClassInstantiator", null);
        new Template("test_class_instantiator_open",
            """
            package #{packageName};

            public class #{className} {
            """
        ).where("packageName", packageName).where("className", className).instantiate(baseScope);
        baseScope.stream.indent();
        baseScope.stream.addNewline();

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
        mainScope.stream.addNewline();
    }

    public class Instantiator {
        private boolean isUsed = false;
        private TestClassInstantiator parent;
        private final Template staticsTemplate = null;
        private final Template mainTemplate = null;
        private final Template testTemplate = null;
        private final HashMap<String,List<String>> argumentsMap = new HashMap<String,List<String>>();

        Instantiator(TestClassInstantiator parent) {
            this.parent = parent;
        }

        public void add(Template staticsTemplate, Template mainTemplate, Template testTemplate) {
            if (isUsed) {
                throw new TemplateFrameworkException("Repeated use of Instantiator not allowed.");
            }
            isUsed = true;

            ArrayList<Parameters> setOfParameters = parametersCrossProduct();
            for (Parameters p : setOfParameters) {
                generate(staticsTemplate, mainTemplate, testTemplate, p);
            }
        }

        private ArrayList<Parameters> parametersCrossProduct() {
            String[] keys = argumentsMap.keySet().toArray(new String[0]);
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
            List<String> values = argumentsMap.get(key);
            for (Parameters pOld : setOfParameters) {
                for (String v : values) {
                    Parameters p = new Parameters(pOld.getArguments());
                    p.add(key, v);
                    newSet.add(p);
                }
            }
            return parametersCrossProduct(keys, keysPos + 1, newSet);
        }

        private void generate(Template staticsTemplate, Template mainTemplate, Template testTemplate,
                             Parameters parameters) {
            // Shared parameters for the 3 templates, so the variable names are shared.
            if (staticsTemplate != null) {
                Scope staticsSubScope = new Scope(staticsScope, staticsScope.fuel);
                staticsTemplate.instantiate(staticsSubScope, parameters);
                staticsSubScope.stream.addNewline();
                staticsSubScope.stream.addNewline();
                staticsSubScope.close();
                staticsScope.stream.addCodeStream(staticsSubScope.stream);
            }

            if (mainTemplate != null) {
                Scope mainSubScope = new Scope(mainScope, mainScope.fuel);
                mainTemplate.instantiate(mainSubScope, parameters);
                mainSubScope.stream.addNewline();
                mainSubScope.close();
                mainScope.stream.addCodeStream(mainSubScope.stream);
            }

            if (testTemplate != null) {
                Scope testScope = new Scope(classScope, classScope.fuel);
                testTemplate.instantiate(testScope, parameters);
                testScope.stream.addNewline();
                testScope.stream.addNewline();
                testScope.close();
                classScope.stream.addCodeStream(testScope.stream);
            }
        }

        public Instantiator where(String paramKey, String paramValue) {
            if (argumentsMap.containsKey(paramKey)) {
                throw new TemplateFrameworkException("Duplicate parameter key: " + paramKey);
            }
            argumentsMap.put(paramKey, Arrays.asList(paramValue));
            return this;
        }

        public Instantiator where(String paramKey, List<String> paramValues) {
            if (argumentsMap.containsKey(paramKey)) {
                throw new TemplateFrameworkException("Duplicate parameter key: " + paramKey);
            }
            argumentsMap.put(paramKey, paramValues);
            return this;
        }
    }

    public void add(Template staticsTemplate, Template mainTemplate, Template testTemplate) {
        new Instantiator(this).add(staticsTemplate, mainTemplate, testTemplate);
    }

    public Instantiator where(String paramKey, String paramValue) {
        return new Instantiator(this).where(paramKey, paramValue);
    }

    public Instantiator where(String paramKey, List<String> paramValues) {
        return new Instantiator(this).where(paramKey, paramValues);
    }

    public String instantiate() {
        if (isUsed) {
            throw new TemplateFrameworkException("Repeated use of Instantiator not allowed.");
        }

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
