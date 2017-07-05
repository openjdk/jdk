/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;
import jdk.test.lib.jittester.factories.Factory;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * The Rule. A helper to perform production.
 */
public class Rule extends Factory implements Comparable<Rule> {
    private String name;
    private Integer limit = -1;

    @Override
    public int compareTo(Rule rule) {
        return name.compareTo(rule.name);
    }

    private TreeSet<RuleEntry> variants;

    public Rule(String name) {
        this.name = name;
        variants = new TreeSet<>();
    }

    public void add(String ruleName, Factory factory) {
        add(ruleName, factory, 1.0);
    }

    public void add(String ruleName, Factory factory, double weight) {
        variants.add(new RuleEntry(ruleName, factory, weight));
    }

    public int size() {
        return variants.size();
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        if (!variants.isEmpty()) {
            // Begin production.
            LinkedList<RuleEntry> rulesList = new LinkedList<>(variants);
            PseudoRandom.shuffle(rulesList);

            while (!rulesList.isEmpty() && (limit == -1 || limit > 0)) {
                double sum = rulesList.stream()
                        .mapToDouble(r -> r.weight)
                        .sum();
                double rnd = PseudoRandom.random() * sum;
                Iterator<RuleEntry> iterator = rulesList.iterator();
                RuleEntry ruleEntry;
                double weightAccumulator = 0;
                do {
                    ruleEntry = iterator.next();
                    weightAccumulator += ruleEntry.weight;
                    if (weightAccumulator >= rnd) {
                        break;
                    }
                } while (iterator.hasNext());
                try {
                    return ruleEntry.produce();
                } catch (ProductionFailedException e) {
                }
                iterator.remove();
                if (limit != -1) {
                    limit--;
                }
            }
            //throw new ProductionFailedException();
        }
        // should probably throw exception here..
        //return getChildren().size() > 0 ? getChild(0).produce() : null;
        throw new ProductionFailedException();
    }

    private class RuleEntry extends Factory implements Comparable<RuleEntry> {
        private final double weight;
        private final Factory factory;
        private final String name;

        private RuleEntry(String name, Factory factory, double weight) {
            this.name = name;
            this.weight = weight;
            this.factory = factory;
        }

        @Override
        public IRNode produce() throws ProductionFailedException {
            return factory.produce();
        }

        @Override
        public int compareTo(RuleEntry entry) {
            return name.compareTo(entry.name);
        }
    }
}
