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
 * The {@link SelectorCodeGenerator} randomly selects one {@link CodeGenerator} from
 * a list, according to the weights assigned to each {@link CodeGenerator}. However,
 * we first filter the list for {link CodeGenerator}s that have low enough
 * {@link CodeGenerator#fuelCost} for the remaining {@link Scope#fuel}, and if
 * none of them have sufficient fuel, the we take the generator with the
 * {@link defaultGeneratorName}. Optionally, one can also provide {@link Predicate}s
 * which filter which generators are available, based on the {@link Scope} and
 * {@link Parameters}.
 */
public final class SelectorCodeGenerator extends CodeGenerator {
    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * {@link Predicate}s are used to enable / disable choices based on the
     * state of the {@link Scope} and {@link Parameters}.
     */
    public interface Predicate {
        /**
         * Checks if the corresponding choice should be available.
         *
         * @param scope Scope of the {@link SelectorCodeGenerator}.
         * @param parameters Parameters that would be passed to the choice's generator.
         * @return A boolean indicating if the choice is to be available.
         */
        public boolean check(Scope scope, Parameters parameters);
    }

    private HashMap<String,Float> choiceWeights;
    private HashMap<String,Predicate> choicePredicates;
    private String defaultGeneratorName;

    /**
     * Create a new {@link SelectorCodeGenerator}.
     *
     * @param selectorName Name of the selector, can be used for lookup in the
     *                     {@link CodeGeneratorLibrary} if the {@link Template}
     *                     is added to a library.
     * @param defaultGeneratorName Name of the default generator if none of the generators
     *                             in the list have low enough fuel cost.
     */
    public SelectorCodeGenerator(String selectorName, String defaultGeneratorName) {
        super(selectorName, 0);
        this.defaultGeneratorName = defaultGeneratorName;
        this.choiceWeights = new HashMap<String,Float>();
        this.choicePredicates = new HashMap<String,Predicate>();
    }

    /**
     * Add another {@link CodeGenerator} name to the list.
     *
     * @param name Name of the additional {@link CodeGenerator}.
     * @param weight Weight of the generator, used in random sampling.
     * @param predicate Predicate that indicates if the choice is to be available.
     */
    public void add(String name, float weight, Predicate predicate) {
        if (!(0.1 < weight && weight <= 10_000)) {
            throw new TemplateFrameworkException("Unreasonable weight " + weight + " for " + name);
	}
        if (choiceWeights.containsKey(name)) {
            throw new TemplateFrameworkException("Already added before: " + name);
	}
        choiceWeights.put(name, weight);
        choicePredicates.put(name, predicate);
    }

    /**
     * Add another {@link CodeGenerator} name to the list, with a {@link Predicate} that
     * always returns true, i.e. makes the choice always available.
     *
     * @param name Name of the additional {@link CodeGenerator}.
     * @param weight Weight of the generator, used in random sampling.
     */
    public void add(String name, float weight) {
        add(name, weight, (_, _) -> { return true; });
    }

    /**
     * Randomly sample one of the generators from the list, according to filter and weight.
     */
    private String choose(Scope scope, Parameters parameters) {
        // Total weight of allowed choices
        double total = 0;
        for (Map.Entry<String,Float> entry : choiceWeights.entrySet()) {
            String name = entry.getKey();
            float weight = entry.getValue().floatValue();
            Predicate predicate = choicePredicates.get(name);
            CodeGenerator codeGenerator = scope.library().find(name, " in selector");
            if (scope.fuel < codeGenerator.fuelCost) { continue; }
            if (!predicate.check(scope, parameters)) { continue; }
            total += weight;
        }

        if (total == 0) {
            // No generator in the list had low enough cost.
            return defaultGeneratorName;
        }

        double r = RANDOM.nextDouble() * total;

        double total2 = 0;
        for (Map.Entry<String,Float> entry : choiceWeights.entrySet()) {
            String name = entry.getKey();
            float weight = entry.getValue().floatValue();
            Predicate predicate = choicePredicates.get(name);
            CodeGenerator codeGenerator = scope.library().find(name, " in selector");
            if (scope.fuel < codeGenerator.fuelCost) { continue; }
            if (!predicate.check(scope, parameters)) { continue; }
            total2 += weight;
            if (r <= total2) {
                return name;
            }
        }
        scope.print();
        throw new TemplateFrameworkException("Failed to select total=" + total + ", r=" + r);
    }

    /**
     * Instantiate the {@link SelectorCodeGenerator}, which randomly samples one of the generators
     * from the list according to remaining fuel cost and weights.
     *
     * @param scope Scope into which the code is generated.
     * @param parameters Provides the parameters for the instantiation.
     */
    @Override
    public void instantiate(Scope scope, Parameters parameters) {
        scope.setDebugContext(name, parameters);
        // Sample a generator.
	String generatorName = choose(scope, parameters);
        CodeGenerator generator = scope.library().find(generatorName, " in selector");

        // Dispatch via with new scope, but the same parameters.
        Scope nestedScope = new Scope(scope, scope.fuel - generator.fuelCost);
        generator.instantiate(nestedScope, parameters);
        nestedScope.close();

        // Add all generated code to the outer scope's stream.
        scope.stream.addCodeStream(nestedScope.stream);
    }
} 
