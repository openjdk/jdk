/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static jdk.vm.ci.code.BytecodeFrame.getPlaceholderBciName;
import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;
import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables and operand
 * stack) at a particular point in the abstract interpretation.
 *
 * This can be used as debug or deoptimization information.
 */
@NodeInfo(nameTemplate = "@{p#code/s}:{p#bci}", cycles = CYCLES_0, size = SIZE_1)
public final class FrameState extends VirtualState implements IterableNodeType {
    public static final NodeClass<FrameState> TYPE = NodeClass.create(FrameState.class);

    /**
     * Marker value for the second slot of values that occupy two local variable or expression stack
     * slots. The marker value is used by the bytecode parser, but replaced with {@code null} in the
     * {@link #values} of the {@link FrameState}.
     */
    public static final ValueNode TWO_SLOT_MARKER = new TwoSlotMarker();

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static final class TwoSlotMarker extends ValueNode {
        public static final NodeClass<TwoSlotMarker> TYPE = NodeClass.create(TwoSlotMarker.class);

        protected TwoSlotMarker() {
            super(TYPE, StampFactory.forKind(JavaKind.Illegal));
        }
    }

    protected final int localsSize;

    protected final int stackSize;

    /**
     * @see BytecodeFrame#rethrowException
     */
    protected final boolean rethrowException;

    protected final boolean duringCall;

    @OptionalInput(value = InputType.State) FrameState outerFrameState;

    /**
     * Contains the locals, the expressions and the locked objects, in this order.
     */
    @OptionalInput NodeInputList<ValueNode> values;

    @Input(Association) NodeInputList<MonitorIdNode> monitorIds;

    @OptionalInput(State) NodeInputList<EscapeObjectState> virtualObjectMappings;

    /**
     * The bytecode index to which this frame state applies.
     */
    public final int bci;

    /**
     * The bytecode to which this frame state applies.
     */
    protected final Bytecode code;

    public FrameState(FrameState outerFrameState, Bytecode code, int bci, int localsSize, int stackSize, int lockSize, boolean rethrowException, boolean duringCall,
                    List<MonitorIdNode> monitorIds, List<EscapeObjectState> virtualObjectMappings) {
        super(TYPE);
        if (code != null) {
            /*
             * Make sure the bci is within range of the bytecodes. If the code size is 0 then allow
             * any value, otherwise the bci must be less than the code size. Any negative value is
             * also allowed to represent special bytecode states.
             */
            int codeSize = code.getCodeSize();
            if (codeSize != 0 && bci >= codeSize) {
                throw new GraalError("bci %d is out of range for %s %d bytes", bci, code.getMethod().format("%H.%n(%p)"), codeSize);
            }
        }
        assert stackSize >= 0;
        this.outerFrameState = outerFrameState;
        assert outerFrameState == null || outerFrameState.bci >= 0;
        this.code = code;
        this.bci = bci;
        this.localsSize = localsSize;
        this.stackSize = stackSize;
        this.values = new NodeInputList<>(this, localsSize + stackSize + lockSize);

        if (monitorIds != null && monitorIds.size() > 0) {
            this.monitorIds = new NodeInputList<>(this, monitorIds);
        }

        if (virtualObjectMappings != null && virtualObjectMappings.size() > 0) {
            this.virtualObjectMappings = new NodeInputList<>(this, virtualObjectMappings);
        }

        this.rethrowException = rethrowException;
        this.duringCall = duringCall;
        assert !this.rethrowException || this.stackSize == 1 : "must have exception on top of the stack";
        assert this.locksSize() == this.monitorIdCount();
    }

    public FrameState(FrameState outerFrameState, Bytecode code, int bci, List<ValueNode> values, int localsSize, int stackSize, boolean rethrowException, boolean duringCall,
                    List<MonitorIdNode> monitorIds, List<EscapeObjectState> virtualObjectMappings) {
        this(outerFrameState, code, bci, localsSize, stackSize, values.size() - localsSize - stackSize, rethrowException, duringCall, monitorIds, virtualObjectMappings);
        for (int i = 0; i < values.size(); ++i) {
            this.values.initialize(i, values.get(i));
        }
    }

    private void verifyAfterExceptionState() {
        if (this.bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
            assert this.outerFrameState == null;
            for (int i = 0; i < this.localsSize; i++) {
                assertTrue(this.values.get(i) == null, "locals should be null in AFTER_EXCEPTION_BCI state");
            }
        }
    }

    public FrameState(int bci) {
        this(null, null, bci, 0, 0, 0, false, false, null, Collections.<EscapeObjectState> emptyList());
        assert bci == BytecodeFrame.BEFORE_BCI || bci == BytecodeFrame.AFTER_BCI || bci == BytecodeFrame.AFTER_EXCEPTION_BCI || bci == BytecodeFrame.UNKNOWN_BCI ||
                        bci == BytecodeFrame.INVALID_FRAMESTATE_BCI;
    }

    /**
     * Creates a placeholder frame state with a single element on the stack representing a return
     * value or thrown exception. This allows the parsing of an intrinsic to communicate the
     * returned or thrown value in a {@link StateSplit#stateAfter() stateAfter} to the inlining call
     * site.
     *
     * @param bci this must be {@link BytecodeFrame#AFTER_BCI}
     */
    public FrameState(int bci, ValueNode returnValueOrExceptionObject) {
        this(null, null, bci, 0, returnValueOrExceptionObject.getStackKind().getSlotCount(), 0, returnValueOrExceptionObject instanceof ExceptionObjectNode, false, null,
                        Collections.<EscapeObjectState> emptyList());
        assert (bci == BytecodeFrame.AFTER_BCI && !rethrowException()) || (bci == BytecodeFrame.AFTER_EXCEPTION_BCI && rethrowException());
        this.values.initialize(0, returnValueOrExceptionObject);
    }

    public FrameState(FrameState outerFrameState, Bytecode code, int bci, ValueNode[] locals, ValueNode[] stack, int stackSize, ValueNode[] locks, List<MonitorIdNode> monitorIds,
                    boolean rethrowException, boolean duringCall) {
        this(outerFrameState, code, bci, locals.length, stackSize, locks.length, rethrowException, duringCall, monitorIds, Collections.<EscapeObjectState> emptyList());
        createValues(locals, stack, locks);
    }

    private void createValues(ValueNode[] locals, ValueNode[] stack, ValueNode[] locks) {
        int index = 0;
        for (int i = 0; i < locals.length; ++i) {
            ValueNode value = locals[i];
            if (value == TWO_SLOT_MARKER) {
                value = null;
            }
            this.values.initialize(index++, value);
        }
        for (int i = 0; i < stackSize; ++i) {
            ValueNode value = stack[i];
            if (value == TWO_SLOT_MARKER) {
                value = null;
            }
            this.values.initialize(index++, value);
        }
        for (int i = 0; i < locks.length; ++i) {
            ValueNode value = locks[i];
            assert value != TWO_SLOT_MARKER;
            this.values.initialize(index++, value);
        }
    }

    public NodeInputList<ValueNode> values() {
        return values;
    }

    public NodeInputList<MonitorIdNode> monitorIds() {
        return monitorIds;
    }

    public FrameState outerFrameState() {
        return outerFrameState;
    }

    public void setOuterFrameState(FrameState x) {
        assert x == null || (!x.isDeleted() && x.bci >= 0) : "cannot set outer frame state of:\n" + toString(this) +
                        "\nto:\n" + toString(x) + "\nisDeleted=" + x.isDeleted();
        updateUsages(this.outerFrameState, x);
        this.outerFrameState = x;
    }

    public static NodeSourcePosition toSourcePosition(FrameState fs) {
        if (fs == null) {
            return null;
        }
        return new NodeSourcePosition(toSourcePosition(fs.outerFrameState()), fs.code.getMethod(), fs.bci);
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public boolean rethrowException() {
        return rethrowException;
    }

    public boolean duringCall() {
        return duringCall;
    }

    public Bytecode getCode() {
        return code;
    }

    public ResolvedJavaMethod getMethod() {
        return code == null ? null : code.getMethod();
    }

    /**
     * Determines if this frame state can be converted to a {@link BytecodeFrame}.
     *
     * Since a {@link BytecodeFrame} encodes {@link #getMethod()} and {@link #bci}, it does not
     * preserve {@link #getCode()}. {@link #bci} is only guaranteed to be valid in terms of
     * {@code getCode().getCode()} which may be different from {@code getMethod().getCode()} if the
     * latter has been subject to instrumentation.
     */
    public boolean canProduceBytecodeFrame() {
        return code != null && code.getCode() == code.getMethod().getCode();
    }

    public void addVirtualObjectMapping(EscapeObjectState virtualObject) {
        if (virtualObjectMappings == null) {
            virtualObjectMappings = new NodeInputList<>(this);
        }
        virtualObjectMappings.add(virtualObject);
    }

    public int virtualObjectMappingCount() {
        if (virtualObjectMappings == null) {
            return 0;
        }
        return virtualObjectMappings.size();
    }

    public EscapeObjectState virtualObjectMappingAt(int i) {
        return virtualObjectMappings.get(i);
    }

    public NodeInputList<EscapeObjectState> virtualObjectMappings() {
        return virtualObjectMappings;
    }

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate() {
        return graph().add(new FrameState(outerFrameState(), code, bci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, virtualObjectMappings));
    }

    /**
     * Duplicates a FrameState, along with a deep copy of all connected VirtualState (outer
     * FrameStates, VirtualObjectStates, ...).
     */
    @Override
    public FrameState duplicateWithVirtualState() {
        FrameState newOuterFrameState = outerFrameState();
        if (newOuterFrameState != null) {
            newOuterFrameState = newOuterFrameState.duplicateWithVirtualState();
        }
        ArrayList<EscapeObjectState> newVirtualMappings = null;
        if (virtualObjectMappings != null) {
            newVirtualMappings = new ArrayList<>(virtualObjectMappings.size());
            for (EscapeObjectState state : virtualObjectMappings) {
                newVirtualMappings.add(state.duplicateWithVirtualState());
            }
        }
        return graph().add(new FrameState(newOuterFrameState, code, bci, values, localsSize, stackSize, rethrowException, duringCall, monitorIds, newVirtualMappings));
    }

    /**
     * Creates a copy of this frame state with one stack element of type {@code popKind} popped from
     * the stack.
     */
    public FrameState duplicateModifiedDuringCall(int newBci, JavaKind popKind) {
        return duplicateModified(graph(), newBci, rethrowException, true, popKind, null, null);
    }

    public FrameState duplicateModifiedBeforeCall(int newBci, JavaKind popKind, JavaKind[] pushedSlotKinds, ValueNode[] pushedValues) {
        return duplicateModified(graph(), newBci, rethrowException, false, popKind, pushedSlotKinds, pushedValues);
    }

    /**
     * Creates a copy of this frame state with the top of stack replaced with with
     * {@code pushedValue} which must be of type {@code popKind}.
     */
    public FrameState duplicateModified(JavaKind popKind, JavaKind pushedSlotKind, ValueNode pushedValue) {
        assert pushedValue != null && pushedValue.getStackKind() == popKind;
        return duplicateModified(graph(), bci, rethrowException, duringCall, popKind, new JavaKind[]{pushedSlotKind}, new ValueNode[]{pushedValue});
    }

    public FrameState duplicateRethrow(ValueNode exceptionObject) {
        return duplicateModified(graph(), bci, true, duringCall, JavaKind.Void, new JavaKind[]{JavaKind.Object}, new ValueNode[]{exceptionObject});
    }

    /**
     * Creates a copy of this frame state with one stack element of type popKind popped from the
     * stack and the values in pushedValues pushed on the stack. The pushedValues will be formatted
     * correctly in slot encoding: a long or double will be followed by a null slot. The bci will be
     * changed to newBci.
     */
    public FrameState duplicateModified(StructuredGraph graph, int newBci, boolean newRethrowException, boolean newDuringCall, JavaKind popKind, JavaKind[] pushedSlotKinds, ValueNode[] pushedValues) {
        ArrayList<ValueNode> copy;
        if (newRethrowException && !rethrowException && popKind == JavaKind.Void) {
            assert popKind == JavaKind.Void;
            copy = new ArrayList<>(values.subList(0, localsSize));
        } else {
            copy = new ArrayList<>(values.subList(0, localsSize + stackSize));
            if (popKind != JavaKind.Void) {
                if (stackAt(stackSize() - 1) == null) {
                    copy.remove(copy.size() - 1);
                }
                ValueNode lastSlot = copy.get(copy.size() - 1);
                assert lastSlot.getStackKind() == popKind.getStackKind();
                copy.remove(copy.size() - 1);
            }
        }
        if (pushedValues != null) {
            assert pushedSlotKinds.length == pushedValues.length;
            for (int i = 0; i < pushedValues.length; i++) {
                copy.add(pushedValues[i]);
                if (pushedSlotKinds[i].needsTwoSlots()) {
                    copy.add(null);
                }
            }
        }
        int newStackSize = copy.size() - localsSize;
        copy.addAll(values.subList(localsSize + stackSize, values.size()));

        assert checkStackDepth(bci, stackSize, duringCall, rethrowException, newBci, newStackSize, newDuringCall, newRethrowException);
        return graph.add(new FrameState(outerFrameState(), code, newBci, copy, localsSize, newStackSize, newRethrowException, newDuringCall, monitorIds, virtualObjectMappings));
    }

    /**
     * Perform a few sanity checks on the transformation of the stack state. The current expectation
     * is that a stateAfter is being transformed into a stateDuring, so the stack depth may change.
     */
    private boolean checkStackDepth(int oldBci, int oldStackSize, boolean oldDuringCall, boolean oldRethrowException, int newBci, int newStackSize, boolean newDuringCall,
                    boolean newRethrowException) {
        if (BytecodeFrame.isPlaceholderBci(oldBci)) {
            return true;
        }
        /*
         * It would be nice to have a complete check of the shape of the FrameState based on a
         * dataflow of the bytecodes but for now just check for obvious expression stack depth
         * mistakes.
         */
        byte[] codes = code.getCode();
        if (codes == null) {
            /* Graph was constructed manually. */
            return true;
        }
        byte newCode = codes[newBci];
        if (oldBci == newBci) {
            assert oldStackSize == newStackSize || oldDuringCall != newDuringCall || oldRethrowException != newRethrowException : "bci is unchanged, stack depth shouldn't change";
        } else {
            byte oldCode = codes[oldBci];
            assert Bytecodes.lengthOf(newCode) + newBci == oldBci || Bytecodes.lengthOf(oldCode) + oldBci == newBci : "expecting roll back or forward";
        }
        return true;
    }

    /**
     * Gets the size of the local variables.
     */
    public int localsSize() {
        return localsSize;
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    /**
     * Gets the number of locked monitors in this frame state.
     */
    public int locksSize() {
        return values.size() - localsSize - stackSize;
    }

    /**
     * Gets the number of locked monitors in this frame state and all {@linkplain #outerFrameState()
     * outer} frame states.
     */
    public int nestedLockDepth() {
        int depth = locksSize();
        for (FrameState outer = outerFrameState(); outer != null; outer = outer.outerFrameState()) {
            depth += outer.locksSize();
        }
        return depth;
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public ValueNode localAt(int i) {
        assert i >= 0 && i < localsSize : "local variable index out of range: " + i;
        return values.get(i);
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public ValueNode stackAt(int i) {
        assert i >= 0 && i < stackSize;
        return values.get(localsSize + i);
    }

    /**
     * Get the monitor owner at the specified index.
     *
     * @param i the index into the list of locked monitors.
     * @return the lock owner at the given index.
     */
    public ValueNode lockAt(int i) {
        assert i >= 0 && i < locksSize();
        return values.get(localsSize + stackSize + i);
    }

    /**
     * Get the MonitorIdNode that corresponds to the locked object at the specified index.
     */
    public MonitorIdNode monitorIdAt(int i) {
        assert monitorIds != null && i >= 0 && i < locksSize();
        return monitorIds.get(i);
    }

    public int monitorIdCount() {
        if (monitorIds == null) {
            return 0;
        } else {
            return monitorIds.size();
        }
    }

    public NodeIterable<FrameState> innerFrameStates() {
        return usages().filter(FrameState.class);
    }

    private static String toString(FrameState frameState) {
        StringBuilder sb = new StringBuilder();
        String nl = CodeUtil.NEW_LINE;
        FrameState fs = frameState;
        while (fs != null) {
            Bytecode.appendLocation(sb, fs.getCode(), fs.bci);
            if (BytecodeFrame.isPlaceholderBci(fs.bci)) {
                sb.append("//").append(getPlaceholderBciName(fs.bci));
            }
            sb.append(nl);
            sb.append("locals: [");
            for (int i = 0; i < fs.localsSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.localAt(i) == null ? "_" : fs.localAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("stack: [");
            for (int i = 0; i < fs.stackSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.stackAt(i) == null ? "_" : fs.stackAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("locks: [");
            for (int i = 0; i < fs.locksSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.lockAt(i) == null ? "_" : fs.lockAt(i).toString(Verbosity.Id));
            }
            sb.append(']').append(nl);
            fs = fs.outerFrameState();
        }
        return sb.toString();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Debugger) {
            return toString(this);
        } else if (verbosity == Verbosity.Name) {
            String res = super.toString(Verbosity.Name) + "@" + bci;
            if (BytecodeFrame.isPlaceholderBci(bci)) {
                res += "[" + getPlaceholderBciName(bci) + "]";
            }
            return res;
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        if (code != null) {
            // properties.put("method", MetaUtil.format("%H.%n(%p):%r", method));
            StackTraceElement ste = code.asStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() >= 0) {
                properties.put("sourceFile", ste.getFileName());
                properties.put("sourceLine", ste.getLineNumber());
            }
        }
        if (isPlaceholderBci(bci)) {
            properties.put("bci", getPlaceholderBciName(bci));
        }
        properties.put("locksSize", values.size() - stackSize - localsSize);
        return properties;
    }

    @Override
    public boolean verify() {
        if (virtualObjectMappingCount() > 0) {
            for (EscapeObjectState state : virtualObjectMappings()) {
                assertTrue(state != null, "must be non-null");
            }
        }
        /*
         * The outermost FrameState should have a method that matches StructuredGraph.method except
         * when it's a substitution or it's null.
         */
        assertTrue(outerFrameState != null || graph() == null || graph().method() == null || code == null || Objects.equals(graph().method(), code.getMethod()) ||
                        graph().isSubstitution(), "wrong outerFrameState %s != %s", code == null ? "null" : code.getMethod(), graph().method());
        if (monitorIds() != null && monitorIds().size() > 0) {
            int depth = outerLockDepth();
            for (MonitorIdNode monitor : monitorIds()) {
                assertTrue(monitor.getLockDepth() == depth++, "wrong depth");
            }
        }
        assertTrue(locksSize() == monitorIdCount(), "mismatch in number of locks");
        for (ValueNode value : values) {
            assertTrue(value == null || !value.isDeleted(), "frame state must not contain deleted nodes: %s", value);
            assertTrue(value == null || value instanceof VirtualObjectNode || (value.getStackKind() != JavaKind.Void), "unexpected value: %s", value);
        }
        verifyAfterExceptionState();
        return super.verify();
    }

    private int outerLockDepth() {
        int depth = 0;
        FrameState outer = outerFrameState;
        while (outer != null) {
            depth += outer.monitorIdCount();
            outer = outer.outerFrameState;
        }
        return depth;
    }

    @Override
    public void applyToVirtual(VirtualClosure closure) {
        closure.apply(this);
        if (virtualObjectMappings != null) {
            for (EscapeObjectState state : virtualObjectMappings) {
                state.applyToVirtual(closure);
            }
        }
        if (outerFrameState() != null) {
            outerFrameState().applyToVirtual(closure);
        }
    }

    @Override
    public void applyToNonVirtual(NodePositionClosure<? super Node> closure) {
        Iterator<Position> iter = inputPositions().iterator();
        while (iter.hasNext()) {
            Position pos = iter.next();
            if (pos.get(this) != null) {
                if (pos.getInputType() == InputType.Value || pos.getInputType() == Association) {
                    closure.apply(this, pos);
                }
            }
        }
        if (virtualObjectMappings != null) {
            for (EscapeObjectState state : virtualObjectMappings) {
                state.applyToNonVirtual(closure);
            }
        }
        if (outerFrameState() != null) {
            outerFrameState().applyToNonVirtual(closure);
        }
    }

    @Override
    public boolean isPartOfThisState(VirtualState state) {
        if (state == this) {
            return true;
        }
        if (outerFrameState() != null && outerFrameState().isPartOfThisState(state)) {
            return true;
        }
        if (virtualObjectMappings != null) {
            for (EscapeObjectState objectState : virtualObjectMappings) {
                if (objectState.isPartOfThisState(state)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExceptionHandlingBCI() {
        return bci == BytecodeFrame.AFTER_EXCEPTION_BCI || bci == BytecodeFrame.UNWIND_BCI;
    }

}
