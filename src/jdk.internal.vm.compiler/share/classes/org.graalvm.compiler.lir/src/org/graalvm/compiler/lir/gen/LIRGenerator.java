/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.gen;

import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.code.RegisterConfig;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRVerifier;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator implements LIRGeneratorTool {

    public static class Options {
        // @formatter:off
        @Option(help = "Print HIR along side LIR as the latter is generated", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintIRWithLIR = new OptionKey<>(false);
        @Option(help = "The trace level for the LIR generator", type = OptionType.Debug)
        public static final OptionKey<Integer> TraceLIRGeneratorLevel = new OptionKey<>(0);
        // @formatter:on
    }

    private final LIRKindTool lirKindTool;

    private final CodeGenProviders providers;

    private AbstractBlockBase<?> currentBlock;

    private LIRGenerationResult res;

    protected final ArithmeticLIRGenerator arithmeticLIRGen;
    private final MoveFactory moveFactory;

    private final boolean printIrWithLir;
    private final int traceLIRGeneratorLevel;

    public LIRGenerator(LIRKindTool lirKindTool, ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, CodeGenProviders providers, LIRGenerationResult res) {
        this.lirKindTool = lirKindTool;
        this.arithmeticLIRGen = arithmeticLIRGen;
        this.res = res;
        this.providers = providers;
        OptionValues options = res.getLIR().getOptions();
        this.printIrWithLir = !TTY.isSuppressed() && Options.PrintIRWithLIR.getValue(options);
        this.traceLIRGeneratorLevel = TTY.isSuppressed() ? 0 : Options.TraceLIRGeneratorLevel.getValue(options);

        assert arithmeticLIRGen.lirGen == null;
        arithmeticLIRGen.lirGen = this;
        this.moveFactory = moveFactory;
    }

    @Override
    public ArithmeticLIRGeneratorTool getArithmetic() {
        return arithmeticLIRGen;
    }

    @Override
    public MoveFactory getMoveFactory() {
        return moveFactory;
    }

    private MoveFactory spillMoveFactory;

    @Override
    public MoveFactory getSpillMoveFactory() {
        if (spillMoveFactory == null) {
            boolean verify = false;
            assert (verify = true) == true;
            if (verify) {
                spillMoveFactory = new VerifyingMoveFactory(moveFactory);
            } else {
                spillMoveFactory = moveFactory;
            }
        }
        return spillMoveFactory;
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(target().arch, javaKind);
    }

    @Override
    public TargetDescription target() {
        return getCodeCache().getTarget();
    }

    @Override
    public CodeGenProviders getProviders() {
        return providers;
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return providers.getMetaAccess();
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return providers.getCodeCache();
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    public LIRKindTool getLIRKindTool() {
        return lirKindTool;
    }

    /**
     * Hide {@link #nextVariable()} from other users.
     */
    public abstract static class VariableProvider {
        private int numVariables;

        public int numVariables() {
            return numVariables;
        }

        private int nextVariable() {
            return numVariables++;
        }
    }

    @Override
    public Variable newVariable(ValueKind<?> valueKind) {
        return new Variable(valueKind, ((VariableProvider) res.getLIR()).nextVariable());
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return res.getRegisterConfig();
    }

    @Override
    public RegisterAttributes attributes(Register register) {
        return getRegisterConfig().getAttributesMap()[register.number];
    }

    @Override
    public Variable emitMove(Value input) {
        assert !(input instanceof Variable) : "Creating a copy of a variable via this method is not supported (and potentially a bug): " + input;
        Variable result = newVariable(input.getValueKind());
        emitMove(result, input);
        return result;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        append(moveFactory.createMove(dst, src));
    }

    @Override
    public void emitMoveConstant(AllocatableValue dst, Constant src) {
        append(moveFactory.createLoad(dst, src));
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        if (moveFactory.canInlineConstant(constant)) {
            return new ConstantValue(toRegisterKind(kind), constant);
        } else {
            return emitLoadConstant(toRegisterKind(kind), constant);
        }
    }

    @Override
    public Value emitJavaConstant(JavaConstant constant) {
        return emitConstant(getValueKind(constant.getJavaKind()), constant);
    }

    @Override
    public AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant) {
        Variable result = newVariable(kind);
        emitMoveConstant(result, constant);
        return result;
    }

    @Override
    public AllocatableValue asAllocatable(Value value) {
        if (isAllocatableValue(value)) {
            return asAllocatableValue(value);
        } else if (isConstantValue(value)) {
            return emitLoadConstant(value.getValueKind(), asConstant(value));
        } else {
            return emitMove(value);
        }
    }

    @Override
    public Variable load(Value value) {
        if (!isVariable(value)) {
            return emitMove(value);
        }
        return (Variable) value;
    }

    @Override
    public Value loadNonConst(Value value) {
        if (isConstantValue(value) && !moveFactory.canInlineConstant(asConstant(value))) {
            return emitMove(value);
        }
        return value;
    }

    /**
     * Determines if only oop maps are required for the code generated from the LIR.
     */
    @Override
    public boolean needOnlyOopMaps() {
        return false;
    }

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param javaKind the kind of value being returned
     * @param valueKind the backend type of the value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    @Override
    public AllocatableValue resultOperandFor(JavaKind javaKind, ValueKind<?> valueKind) {
        Register reg = getRegisterConfig().getReturnRegister(javaKind);
        assert target().arch.canStoreValue(reg.getRegisterCategory(), valueKind.getPlatformKind()) : reg.getRegisterCategory() + " " + valueKind.getPlatformKind();
        return reg.asValue(valueKind);
    }

    NodeSourcePosition currentPosition;

    @Override
    public void setSourcePosition(NodeSourcePosition position) {
        currentPosition = position;
    }

    @Override
    public <I extends LIRInstruction> I append(I op) {
        LIR lir = res.getLIR();
        if (printIrWithLir) {
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        assert LIRVerifier.verify(op);
        ArrayList<LIRInstruction> lirForBlock = lir.getLIRforBlock(getCurrentBlock());
        op.setPosition(currentPosition);
        lirForBlock.add(op);
        return op;
    }

    @Override
    public boolean hasBlockEnd(AbstractBlockBase<?> block) {
        ArrayList<LIRInstruction> ops = getResult().getLIR().getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        return ops.get(ops.size() - 1) instanceof BlockEndOp;
    }

    private final class BlockScopeImpl extends BlockScope {

        private BlockScopeImpl(AbstractBlockBase<?> block) {
            currentBlock = block;
        }

        private void doBlockStart() {
            if (printIrWithLir) {
                TTY.print(currentBlock.toString());
            }

            // set up the list of LIR instructions
            assert res.getLIR().getLIRforBlock(currentBlock) == null : "LIR list already computed for this block";
            res.getLIR().setLIRforBlock(currentBlock, new ArrayList<LIRInstruction>());

            append(new LabelOp(new Label(currentBlock.getId()), currentBlock.isAligned()));

            if (traceLIRGeneratorLevel >= 1) {
                TTY.println("BEGIN Generating LIR for block B" + currentBlock.getId());
            }
        }

        private void doBlockEnd() {
            if (traceLIRGeneratorLevel >= 1) {
                TTY.println("END Generating LIR for block B" + currentBlock.getId());
            }

            if (printIrWithLir) {
                TTY.println();
            }
            currentBlock = null;
        }

        @Override
        public AbstractBlockBase<?> getCurrentBlock() {
            return currentBlock;
        }

        @Override
        public void close() {
            doBlockEnd();
        }

    }

    @Override
    public final BlockScope getBlockScope(AbstractBlockBase<?> block) {
        BlockScopeImpl blockScope = new BlockScopeImpl(block);
        blockScope.doBlockStart();
        return blockScope;
    }

    @Override
    public void emitIncomingValues(Value[] params) {
        ((LabelOp) res.getLIR().getLIRforBlock(getCurrentBlock()).get(0)).setIncomingValues(params);
    }

    @Override
    public abstract void emitJump(LabelRef label);

    @Override
    public abstract void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    @Override
    public abstract void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability);

    @Override
    public abstract void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    @Override
    public abstract Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    @Override
    public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    /**
     * Emits the single call operation at the heart of generating LIR for a
     * {@linkplain #emitForeignCall(ForeignCallLinkage, LIRFrameState, Value...) foreign call}.
     */
    protected abstract void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info);

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState frameState, Value... args) {
        LIRFrameState state = null;
        if (linkage.needsDebugInfo()) {
            if (frameState != null) {
                state = frameState;
            } else {
                assert needOnlyOopMaps();
                state = new LIRFrameState(null, null, null);
            }
        }

        // move the arguments into the correct location
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        res.getFrameMapBuilder().callsMethod(linkageCc);
        assert linkageCc.getArgumentCount() == args.length : "argument count mismatch";
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = linkageCc.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        res.setForeignCall(true);
        emitForeignCallOp(linkage, linkageCc.getReturn(), argLocations, linkage.getTemporaries(), state);

        if (isLegal(linkageCc.getReturn())) {
            return emitMove(linkageCc.getReturn());
        } else {
            return null;
        }
    }

    @Override
    public void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        int keyCount = keyConstants.length;
        SwitchStrategy strategy = SwitchStrategy.getBestStrategy(keyProbabilities, keyConstants, keyTargets);
        long valueRange = keyConstants[keyCount - 1].asLong() - keyConstants[0].asLong() + 1;
        double tableSwitchDensity = keyCount / (double) valueRange;
        /*
         * This heuristic tries to find a compromise between the effort for the best switch strategy
         * and the density of a tableswitch. If the effort for the strategy is at least 4, then a
         * tableswitch is preferred if better than a certain value that starts at 0.5 and lowers
         * gradually with additional effort.
         */
        if (strategy.getAverageEffort() < 4 || tableSwitchDensity < (1 / Math.sqrt(strategy.getAverageEffort()))) {
            emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
        } else {
            int minValue = keyConstants[0].asInt();
            assert valueRange < Integer.MAX_VALUE;
            LabelRef[] targets = new LabelRef[(int) valueRange];
            for (int i = 0; i < valueRange; i++) {
                targets[i] = defaultTarget;
            }
            for (int i = 0; i < keyCount; i++) {
                targets[keyConstants[i].asInt() - minValue] = keyTargets[i];
            }
            emitTableSwitch(minValue, defaultTarget, targets, value);
        }
    }

    @Override
    public abstract void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget);

    protected abstract void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key);

    @Override
    public void beforeRegisterAllocation() {
    }

    /**
     * Gets a garbage value for a given kind.
     */
    protected abstract JavaConstant zapValueForKind(PlatformKind kind);

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        return stamp.getLIRKind(lirKindTool);
    }

    protected LIRKind getAddressKind(Value base, long displacement, Value index) {
        if (LIRKind.isValue(base) && (index.equals(Value.ILLEGAL) || LIRKind.isValue(index))) {
            return LIRKind.value(target().arch.getWordKind());
        } else if (base.getValueKind() instanceof LIRKind && base.getValueKind(LIRKind.class).isReference(0) && displacement == 0L && index.equals(Value.ILLEGAL)) {
            return LIRKind.reference(target().arch.getWordKind());
        } else {
            return LIRKind.unknownReference(target().arch.getWordKind());
        }
    }

    @Override
    public AbstractBlockBase<?> getCurrentBlock() {
        return currentBlock;
    }

    @Override
    public LIRGenerationResult getResult() {
        return res;
    }

    @Override
    public void emitBlackhole(Value operand) {
        append(new StandardOp.BlackholeOp(operand));
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        throw GraalError.unimplemented();
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        throw GraalError.unimplemented();
    }

    @Override
    public abstract SaveRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues);

    @Override
    public SaveRegistersOp createZapRegisters() {
        Register[] zappedRegisters = getResult().getFrameMap().getRegisterConfig().getAllocatableRegisters().toArray();
        JavaConstant[] zapValues = new JavaConstant[zappedRegisters.length];
        for (int i = 0; i < zappedRegisters.length; i++) {
            PlatformKind kind = target().arch.getLargestStorableKind(zappedRegisters[i].getRegisterCategory());
            zapValues[i] = zapValueForKind(kind);
        }
        return createZapRegisters(zappedRegisters, zapValues);
    }

    @Override
    public abstract LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues);

    @Override
    public LIRInstruction zapArgumentSpace() {
        List<StackSlot> slots = null;
        for (AllocatableValue arg : res.getCallingConvention().getArguments()) {
            if (isStackSlot(arg)) {
                if (slots == null) {
                    slots = new ArrayList<>();
                }
                slots.add((StackSlot) arg);
            } else {
                assert !isVirtualStackSlot(arg);
            }
        }
        if (slots == null) {
            return null;
        }
        StackSlot[] zappedStack = slots.toArray(new StackSlot[slots.size()]);
        JavaConstant[] zapValues = new JavaConstant[zappedStack.length];
        for (int i = 0; i < zappedStack.length; i++) {
            PlatformKind kind = zappedStack[i].getPlatformKind();
            zapValues[i] = zapValueForKind(kind);
        }
        return createZapArgumentSpace(zappedStack, zapValues);
    }
}
