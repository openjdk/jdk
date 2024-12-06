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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import jdk.test.lib.Utils;

/**
 * TODO desc
 */
public final class SelectorCodeGenerator implements CodeGenerator {
    private static final Random RANDOM = Utils.getRandomInstance();

    private HashMap<String,Float> choiceWeights;
    private String defaultGeneratorName;

    public SelectorCodeGenerator(String defaultGeneratorName) {
        this.defaultGeneratorName = defaultGeneratorName;
        this.choiceWeights = new HashMap<String,Float>();
    }

    public void add(String name, float weight) {
        if (!(0.1 < weight && weight < 10_000)) {
            throw new TemplateFrameworkException("Unreasonable weight " + weight + " for " + name);
	}
        if (choiceWeights.containsKey(name)) {
            throw new TemplateFrameworkException("Already added before: " + name);
	}
        choiceWeights.put(name, weight);
    }

    private String choose(Scope scope) {
        // TODO maybe cache the generators, so we can more quickly iterate?
        // Total weight of allowed choices
        double total = 0;
        for (Map.Entry<String,Float> entry : choiceWeights.entrySet()) {
            String name = entry.getKey();
            float weight = entry.getValue().floatValue();
            CodeGenerator codeGenerator = scope.library().find(name, " in selector");
            if (scope.fuel < codeGenerator.fuelCost()) { continue; }
            total += weight;
        }

        if (total == 0) {
            return defaultGeneratorName;
        }

        double r = RANDOM.nextDouble() * total;

        double total2 = 0;
        for (Map.Entry<String,Float> entry : choiceWeights.entrySet()) {
            String name = entry.getKey();
            float weight = entry.getValue().floatValue();
            CodeGenerator codeGenerator = scope.library().find(name, " in selector");
            if (scope.fuel < codeGenerator.fuelCost()) { continue; }
            total2 += weight;
            if (r <= total2) {
                return name;
            }
        }
        throw new TemplateFrameworkException("Failed to select total=" + total + ", r=" + r);
    }

    public int fuelCost() {
        return 0; // We only forward, at no cost.
    }

    public void instantiate(Scope scope, Parameters parameters) {
        // Sample a generator.
	String generatorName = choose(scope);
        CodeGenerator generator = scope.library().find(generatorName, " in selector");

        // Dispatch via with new scope, but the same parameters.
        Scope nestedScope = new Scope(scope, scope.fuel - generator.fuelCost());
        generator.instantiate(nestedScope, parameters);
        nestedScope.close();

        // Add all generated code to the outer scope's stream.
        scope.stream.addCodeStream(nestedScope.stream);
    }
} 
