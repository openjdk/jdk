/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import java.util.Arrays;
import java.util.Comparator;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This class encapsulates different strategies on how to generate code for switch instructions.
 *
 * The {@link #getBestStrategy(double[], JavaConstant[], LabelRef[])} method can be used to get
 * strategy with the smallest average effort (average number of comparisons until a decision is
 * reached). The strategy returned by this method will have its averageEffort set, while a strategy
 * constructed directly will not.
 */
public abstract class SwitchStrategy {

    private interface SwitchClosure {
        /**
         * Generates a conditional or unconditional jump. The jump will be unconditional if
         * condition is null. If defaultTarget is true, then the jump will go the default.
         *
         * @param index Index of the value and the jump target (only used if defaultTarget == false)
         * @param condition The condition on which to jump (can be null)
         * @param defaultTarget true if the jump should go to the default target, false if index
         *            should be used.
         */
        void conditionalJump(int index, Condition condition, boolean defaultTarget);

        /**
         * Generates a conditional jump to the target with the specified index. The fall through
         * should go to the default target.
         *
         * @param index Index of the value and the jump target
         * @param condition The condition on which to jump
         * @param canFallThrough true if this is the last instruction in the switch statement, to
         *            allow for fall-through optimizations.
         */
        void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough);

        /**
         * Create a new label and generate a conditional jump to it.
         *
         * @param index Index of the value and the jump target
         * @param condition The condition on which to jump
         * @return a new Label
         */
        Label conditionalJump(int index, Condition condition);

        /**
         * Binds a label returned by {@link #conditionalJump(int, Condition)}.
         */
        void bind(Label label);

        /**
         * Return true iff the target of both indexes is the same.
         */
        boolean isSameTarget(int index1, int index2);
    }

    /**
     * Backends can subclass this abstract class and generate code for switch strategies by
     * implementing the {@link #conditionalJump(int, Condition, Label)} method.
     */
    public abstract static class BaseSwitchClosure implements SwitchClosure {

        private final CompilationResultBuilder crb;
        private final Assembler masm;
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;

        public BaseSwitchClosure(CompilationResultBuilder crb, Assembler masm, LabelRef[] keyTargets, LabelRef defaultTarget) {
            this.crb = crb;
            this.masm = masm;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
        }

        /**
         * This method generates code for a comparison between the actual value and the constant at
         * the given index and a condition jump to target.
         */
        protected abstract void conditionalJump(int index, Condition condition, Label target);

        @Override
        public void conditionalJump(int index, Condition condition, boolean targetDefault) {
            Label target = targetDefault ? defaultTarget.label() : keyTargets[index].label();
            if (condition == null) {
                masm.jmp(target);
            } else {
                conditionalJump(index, condition, target);
            }
        }

        @Override
        public void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough) {
            if (canFallThrough && crb.isSuccessorEdge(defaultTarget)) {
                conditionalJump(index, condition, keyTargets[index].label());
            } else if (canFallThrough && crb.isSuccessorEdge(keyTargets[index])) {
                conditionalJump(index, condition.negate(), defaultTarget.label());
            } else {
                conditionalJump(index, condition, keyTargets[index].label());
                masm.jmp(defaultTarget.label());
            }
        }

        @Override
        public Label conditionalJump(int index, Condition condition) {
            Label label = new Label();
            conditionalJump(index, condition, label);
            return label;
        }

        @Override
        public void bind(Label label) {
            masm.bind(label);
        }

        @Override
        public boolean isSameTarget(int index1, int index2) {
            return keyTargets[index1] == keyTargets[index2];
        }

    }

    /**
     * This closure is used internally to determine the average effort for a certain strategy on a
     * given switch instruction.
     */
    private class EffortClosure implements SwitchClosure {

        private int defaultEffort;
        private int defaultCount;
        private final int[] keyEfforts = new int[keyProbabilities.length];
        private final int[] keyCounts = new int[keyProbabilities.length];
        private final LabelRef[] keyTargets;

        EffortClosure(LabelRef[] keyTargets) {
            this.keyTargets = keyTargets;
        }

        @Override
        public void conditionalJump(int index, Condition condition, boolean defaultTarget) {
            // nothing to do
        }

        @Override
        public void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough) {
            // nothing to do
        }

        @Override
        public Label conditionalJump(int index, Condition condition) {
            // nothing to do
            return null;
        }

        @Override
        public void bind(Label label) {
            // nothing to do
        }

        @Override
        public boolean isSameTarget(int index1, int index2) {
            return keyTargets[index1] == keyTargets[index2];
        }

        public double getAverageEffort() {
            double defaultProbability = 1;
            double effort = 0;
            for (int i = 0; i < keyProbabilities.length; i++) {
                effort += keyEfforts[i] * keyProbabilities[i] / keyCounts[i];
                defaultProbability -= keyProbabilities[i];
            }
            return effort + defaultEffort * defaultProbability / defaultCount;
        }
    }

    public final double[] keyProbabilities;
    private double averageEffort = -1;
    private EffortClosure effortClosure;

    public SwitchStrategy(double[] keyProbabilities) {
        assert keyProbabilities.length >= 2;
        this.keyProbabilities = keyProbabilities;
    }

    public abstract Constant[] getKeyConstants();

    public double getAverageEffort() {
        assert averageEffort >= 0 : "average effort was not calculated yet for this strategy";
        return averageEffort;
    }

    /**
     * Tells the system that the given (inclusive) range of keys is reached after depth number of
     * comparisons, which is used to calculate the average effort.
     */
    protected void registerEffort(int rangeStart, int rangeEnd, int depth) {
        if (effortClosure != null) {
            for (int i = rangeStart; i <= rangeEnd; i++) {
                effortClosure.keyEfforts[i] += depth;
                effortClosure.keyCounts[i]++;
            }
        }
    }

    /**
     * Tells the system that the default successor is reached after depth number of comparisons,
     * which is used to calculate average effort.
     */
    protected void registerDefaultEffort(int depth) {
        if (effortClosure != null) {
            effortClosure.defaultEffort += depth;
            effortClosure.defaultCount++;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[avgEffort=" + averageEffort + "]";
    }

    /**
     * This strategy orders the keys according to their probability and creates one equality
     * comparison per key.
     */
    public static class SequentialStrategy extends SwitchStrategy {
        private final Integer[] indexes;
        private final Constant[] keyConstants;

        public SequentialStrategy(final double[] keyProbabilities, Constant[] keyConstants) {
            super(keyProbabilities);
            assert keyProbabilities.length == keyConstants.length;

            this.keyConstants = keyConstants;
            int keyCount = keyConstants.length;
            indexes = new Integer[keyCount];
            for (int i = 0; i < keyCount; i++) {
                indexes[i] = i;
            }
            Arrays.sort(indexes, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return keyProbabilities[o1] < keyProbabilities[o2] ? 1 : keyProbabilities[o1] > keyProbabilities[o2] ? -1 : 0;
                }
            });
        }

        @Override
        public Constant[] getKeyConstants() {
            return keyConstants;
        }

        @Override
        public void run(SwitchClosure closure) {
            for (int i = 0; i < keyConstants.length - 1; i++) {
                closure.conditionalJump(indexes[i], Condition.EQ, false);
                registerEffort(indexes[i], indexes[i], i + 1);
            }
            closure.conditionalJumpOrDefault(indexes[keyConstants.length - 1], Condition.EQ, true);
            registerEffort(indexes[keyConstants.length - 1], indexes[keyConstants.length - 1], keyConstants.length);
            registerDefaultEffort(keyConstants.length);
        }
    }

    /**
     * Base class for strategies that rely on primitive integer keys.
     */
    private abstract static class PrimitiveStrategy extends SwitchStrategy {
        protected final JavaConstant[] keyConstants;

        protected PrimitiveStrategy(double[] keyProbabilities, JavaConstant[] keyConstants) {
            super(keyProbabilities);
            assert keyProbabilities.length == keyConstants.length;
            this.keyConstants = keyConstants;
        }

        @Override
        public JavaConstant[] getKeyConstants() {
            return keyConstants;
        }

        /**
         * Looks for the end of a stretch of key constants that are successive numbers and have the
         * same target.
         */
        protected int getSliceEnd(SwitchClosure closure, int pos) {
            int slice = pos;
            while (slice < (keyConstants.length - 1) && keyConstants[slice + 1].asLong() == keyConstants[slice].asLong() + 1 && closure.isSameTarget(slice, slice + 1)) {
                slice++;
            }
            return slice;
        }
    }

    /**
     * This strategy divides the keys into ranges of successive keys with the same target and
     * creates comparisons for these ranges.
     */
    public static class RangesStrategy extends PrimitiveStrategy {
        private final Integer[] indexes;

        public RangesStrategy(final double[] keyProbabilities, JavaConstant[] keyConstants) {
            super(keyProbabilities, keyConstants);

            int keyCount = keyConstants.length;
            indexes = new Integer[keyCount];
            for (int i = 0; i < keyCount; i++) {
                indexes[i] = i;
            }
            Arrays.sort(indexes, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return keyProbabilities[o1] < keyProbabilities[o2] ? 1 : keyProbabilities[o1] > keyProbabilities[o2] ? -1 : 0;
                }
            });
        }

        @Override
        public void run(SwitchClosure closure) {
            int depth = 0;
            closure.conditionalJump(0, Condition.LT, true);
            registerDefaultEffort(++depth);
            int rangeStart = 0;
            int rangeEnd = getSliceEnd(closure, rangeStart);
            while (rangeEnd != keyConstants.length - 1) {
                if (rangeStart == rangeEnd) {
                    closure.conditionalJump(rangeStart, Condition.EQ, false);
                    registerEffort(rangeStart, rangeEnd, ++depth);
                } else {
                    if (rangeStart == 0 || keyConstants[rangeStart - 1].asLong() + 1 != keyConstants[rangeStart].asLong()) {
                        closure.conditionalJump(rangeStart, Condition.LT, true);
                        registerDefaultEffort(++depth);
                    }
                    closure.conditionalJump(rangeEnd, Condition.LE, false);
                    registerEffort(rangeStart, rangeEnd, ++depth);
                }
                rangeStart = rangeEnd + 1;
                rangeEnd = getSliceEnd(closure, rangeStart);
            }
            if (rangeStart == rangeEnd) {
                closure.conditionalJumpOrDefault(rangeStart, Condition.EQ, true);
                registerEffort(rangeStart, rangeEnd, ++depth);
                registerDefaultEffort(depth);
            } else {
                if (rangeStart == 0 || keyConstants[rangeStart - 1].asLong() + 1 != keyConstants[rangeStart].asLong()) {
                    closure.conditionalJump(rangeStart, Condition.LT, true);
                    registerDefaultEffort(++depth);
                }
                closure.conditionalJumpOrDefault(rangeEnd, Condition.LE, true);
                registerEffort(rangeStart, rangeEnd, ++depth);
                registerDefaultEffort(depth);
            }
        }
    }

    /**
     * This strategy recursively subdivides the list of keys to create a binary search based on
     * probabilities.
     */
    public static class BinaryStrategy extends PrimitiveStrategy {

        private static final double MIN_PROBABILITY = 0.00001;

        private final double[] probabilitySums;

        public BinaryStrategy(double[] keyProbabilities, JavaConstant[] keyConstants) {
            super(keyProbabilities, keyConstants);
            probabilitySums = new double[keyProbabilities.length + 1];
            double sum = 0;
            for (int i = 0; i < keyConstants.length; i++) {
                sum += Math.max(keyProbabilities[i], MIN_PROBABILITY);
                probabilitySums[i + 1] = sum;
            }
        }

        @Override
        public void run(SwitchClosure closure) {
            recurseBinarySwitch(closure, 0, keyConstants.length - 1, 0);
        }

        /**
         * Recursively generate a list of comparisons that always subdivides the keys in the given
         * (inclusive) range in the middle (in terms of probability, not index). If left is bigger
         * than zero, then we always know that the value is equal to or bigger than the left key.
         * This does not hold for the right key, as there may be a gap afterwards.
         */
        private void recurseBinarySwitch(SwitchClosure closure, int left, int right, int startDepth) {
            assert startDepth < keyConstants.length * 3 : "runaway recursion in binary switch";
            int depth = startDepth;
            boolean leftBorder = left == 0;
            boolean rightBorder = right == keyConstants.length - 1;

            if (left + 1 == right) {
                // only two possible values
                if (leftBorder || rightBorder || keyConstants[right].asLong() + 1 != keyConstants[right + 1].asLong() || keyConstants[left].asLong() + 1 != keyConstants[right].asLong()) {
                    closure.conditionalJump(left, Condition.EQ, false);
                    registerEffort(left, left, ++depth);
                    closure.conditionalJumpOrDefault(right, Condition.EQ, rightBorder);
                    registerEffort(right, right, ++depth);
                    registerDefaultEffort(depth);
                } else {
                    // here we know that the value can only be one of these two keys in the range
                    closure.conditionalJump(left, Condition.EQ, false);
                    registerEffort(left, left, ++depth);
                    closure.conditionalJump(right, null, false);
                    registerEffort(right, right, depth);
                }
                return;
            }
            double probabilityStart = probabilitySums[left];
            double probabilityMiddle = (probabilityStart + probabilitySums[right + 1]) / 2;
            assert probabilityStart >= probabilityStart;
            int middle = left;
            while (getSliceEnd(closure, middle + 1) < right && probabilitySums[getSliceEnd(closure, middle + 1)] < probabilityMiddle) {
                middle = getSliceEnd(closure, middle + 1);
            }
            middle = getSliceEnd(closure, middle);
            assert middle < keyConstants.length - 1;

            if (getSliceEnd(closure, left) == middle) {
                if (left == 0) {
                    closure.conditionalJump(0, Condition.LT, true);
                    registerDefaultEffort(++depth);
                }
                closure.conditionalJump(middle, Condition.LE, false);
                registerEffort(left, middle, ++depth);

                if (middle + 1 == right) {
                    closure.conditionalJumpOrDefault(right, Condition.EQ, rightBorder);
                    registerEffort(right, right, ++depth);
                    registerDefaultEffort(depth);
                } else {
                    if (keyConstants[middle].asLong() + 1 != keyConstants[middle + 1].asLong()) {
                        closure.conditionalJump(middle + 1, Condition.LT, true);
                        registerDefaultEffort(++depth);
                    }
                    if (getSliceEnd(closure, middle + 1) == right) {
                        if (right == keyConstants.length - 1 || keyConstants[right].asLong() + 1 != keyConstants[right + 1].asLong()) {
                            closure.conditionalJumpOrDefault(right, Condition.LE, rightBorder);
                            registerEffort(middle + 1, right, ++depth);
                            registerDefaultEffort(depth);
                        } else {
                            closure.conditionalJump(middle + 1, null, false);
                            registerEffort(middle + 1, right, depth);
                        }
                    } else {
                        recurseBinarySwitch(closure, middle + 1, right, depth);
                    }
                }
            } else if (getSliceEnd(closure, middle + 1) == right) {
                if (rightBorder || keyConstants[right].asLong() + 1 != keyConstants[right + 1].asLong()) {
                    closure.conditionalJump(right, Condition.GT, true);
                    registerDefaultEffort(++depth);
                }
                closure.conditionalJump(middle + 1, Condition.GE, false);
                registerEffort(middle + 1, right, ++depth);
                recurseBinarySwitch(closure, left, middle, depth);
            } else {
                Label label = closure.conditionalJump(middle + 1, Condition.GE);
                depth++;
                recurseBinarySwitch(closure, left, middle, depth);
                closure.bind(label);
                recurseBinarySwitch(closure, middle + 1, right, depth);
            }
        }
    }

    public abstract void run(SwitchClosure closure);

    private static SwitchStrategy[] getStrategies(double[] keyProbabilities, JavaConstant[] keyConstants, LabelRef[] keyTargets) {
        SwitchStrategy[] strategies = new SwitchStrategy[]{new SequentialStrategy(keyProbabilities, keyConstants), new RangesStrategy(keyProbabilities, keyConstants),
                        new BinaryStrategy(keyProbabilities, keyConstants)};
        for (SwitchStrategy strategy : strategies) {
            strategy.effortClosure = strategy.new EffortClosure(keyTargets);
            strategy.run(strategy.effortClosure);
            strategy.averageEffort = strategy.effortClosure.getAverageEffort();
            strategy.effortClosure = null;
        }
        return strategies;
    }

    /**
     * Creates all switch strategies for the given switch, evaluates them (based on average effort)
     * and returns the best one.
     */
    public static SwitchStrategy getBestStrategy(double[] keyProbabilities, JavaConstant[] keyConstants, LabelRef[] keyTargets) {
        SwitchStrategy[] strategies = getStrategies(keyProbabilities, keyConstants, keyTargets);
        double bestEffort = Integer.MAX_VALUE;
        SwitchStrategy bestStrategy = null;
        for (SwitchStrategy strategy : strategies) {
            if (strategy.getAverageEffort() < bestEffort) {
                bestEffort = strategy.getAverageEffort();
                bestStrategy = strategy;
            }
        }
        return bestStrategy;
    }
}
