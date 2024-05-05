/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package jdk.internal.org.objectweb.asm.util;

import java.util.Collections;
import java.util.List;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.FrameNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.InsnNode;
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LookupSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TableSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;
import jdk.internal.org.objectweb.asm.tree.TypeInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.internal.org.objectweb.asm.tree.analysis.Interpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.Value;

/**
 * An {@link Analyzer} subclass which checks that methods provide stack map frames where expected
 * (i.e. at jump target and after instructions without immediate successor), and that these stack
 * map frames are valid (for the provided interpreter; they may still be invalid for the JVM, if the
 * {@link Interpreter} uses a simplified type system compared to the JVM verifier). This is done in
 * two steps:
 *
 * <ul>
 *   <li>First, the stack map frames in {@link FrameNode}s are expanded, and stored at their
 *       respective instruction offsets. The expansion process uncompresses the APPEND, CHOP and
 *       SAME frames to FULL frames. It also converts the stack map frame verification types to
 *       {@link Value}s, via the provided {@link Interpreter}. The expansion is done in {@link
 *       #expandFrames}, by looking at each {@link FrameNode} in sequence (compressed frames are
 *       defined relatively to the previous {@link FrameNode}, or the implicit first frame). The
 *       actual decompression is done in {@link #expandFrame}, and the type conversion in {@link
 *       #newFrameValue}.
 *   <li>Next, the method instructions are checked in sequence. Starting from the implicit initial
 *       frame, the execution of each instruction <em>i</em> is simulated on the current stack map
 *       frame, with the {@link Frame#execute} method. This gives a new stack map frame <em>f</em>,
 *       representing the stack map frame state after the execution of <em>i</em>. Then:
 *       <ul>
 *         <li>If there is a next instruction and if the control flow cannot continue to it (e.g. if
 *             <em>i</em> is a RETURN or an ATHROW, for instance): an existing stack map frame
 *             <em>f0</em> (coming from the first step) is expected after <em>i</em>.
 *         <li>If there is a next instruction and if the control flow can continue to it (e.g. if
 *             <em>i</em> is a ALOAD, for instance): either there an existing stack map frame
 *             <em>f0</em> (coming from the first step) after <em>i</em>, or there is none. In the
 *             first case <em>f</em> and <em>f0</em> must be <em>compatible</em>: the types in
 *             <em>f</em> must be sub types of the corresponding types in the existing frame
 *             <em>f0</em> (otherwise an exception is thrown). In the second case, <em>f0</em> is
 *             simply set to the value of <em>f</em>.
 *         <li>If the control flow can continue to some instruction <em>j</em> (e.g. if <em>i</em>
 *             is an IF_EQ, for instance): an existing stack map frame <em>f0</em> (coming from the
 *             first step) is expected at <em>j</em>, which must be compatible with <em>f</em> (as
 *             defined previously).
 *       </ul>
 *       The sequential loop over the instructions is done in {@link #init}, which is called from
 *       the {@link Analyzer#analyze} method. Cases where the control flow cannot continue to the
 *       next instruction are handled in {@link #endControlFlow}. Cases where the control flow can
 *       continue to the next instruction, or jump to another instruction, are handled in {@link
 *       #checkFrame}. This method checks that an existing stack map frame is present when required,
 *       and checks the stack map frames compatibility with {@link #checkMerge}.
 * </ul>
 *
 * @author Eric Bruneton
 * @param <V> type of the {@link Value} used for the analysis.
 */
class CheckFrameAnalyzer<V extends Value> extends Analyzer<V> {

    /** The interpreter to use to symbolically interpret the bytecode instructions. */
    private final Interpreter<V> interpreter;

    /** The instructions of the currently analyzed method. */
    private InsnList insnList;

    /**
      * The number of locals in the last stack map frame processed by {@link expandFrame}. Long and
      * double values are represented with two elements.
      */
    private int currentLocals;

    CheckFrameAnalyzer(final Interpreter<V> interpreter) {
        super(interpreter);
        this.interpreter = interpreter;
    }

    @Override
    protected void init(final String owner, final MethodNode method) throws AnalyzerException {
        insnList = method.instructions;
        currentLocals = Type.getArgumentsAndReturnSizes(method.desc) >> 2;
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            currentLocals -= 1;
        }

        Frame<V>[] frames = getFrames();
        Frame<V> currentFrame = frames[0];
        expandFrames(owner, method, currentFrame);
        for (int insnIndex = 0; insnIndex < insnList.size(); ++insnIndex) {
            Frame<V> oldFrame = frames[insnIndex];

            // Simulate the execution of this instruction.
            AbstractInsnNode insnNode = null;
            try {
                insnNode = method.instructions.get(insnIndex);
                int insnOpcode = insnNode.getOpcode();
                int insnType = insnNode.getType();

                if (insnType == AbstractInsnNode.LABEL
                        || insnType == AbstractInsnNode.LINE
                        || insnType == AbstractInsnNode.FRAME) {
                    checkFrame(insnIndex + 1, oldFrame, /* requireFrame = */ false);
                } else {
                    currentFrame.init(oldFrame).execute(insnNode, interpreter);

                    if (insnNode instanceof JumpInsnNode) {
                        if (insnOpcode == JSR) {
                            throw new AnalyzerException(insnNode, "JSR instructions are unsupported");
                        }
                        JumpInsnNode jumpInsn = (JumpInsnNode) insnNode;
                        int targetInsnIndex = insnList.indexOf(jumpInsn.label);
                        checkFrame(targetInsnIndex, currentFrame, /* requireFrame = */ true);
                        if (insnOpcode == GOTO) {
                            endControlFlow(insnIndex);
                        } else {
                            checkFrame(insnIndex + 1, currentFrame, /* requireFrame = */ false);
                        }
                    } else if (insnNode instanceof LookupSwitchInsnNode) {
                        LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode) insnNode;
                        int targetInsnIndex = insnList.indexOf(lookupSwitchInsn.dflt);
                        checkFrame(targetInsnIndex, currentFrame, /* requireFrame = */ true);
                        for (int i = 0; i < lookupSwitchInsn.labels.size(); ++i) {
                            LabelNode label = lookupSwitchInsn.labels.get(i);
                            targetInsnIndex = insnList.indexOf(label);
                            currentFrame.initJumpTarget(insnOpcode, label);
                            checkFrame(targetInsnIndex, currentFrame, /* requireFrame = */ true);
                        }
                        endControlFlow(insnIndex);
                    } else if (insnNode instanceof TableSwitchInsnNode) {
                        TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) insnNode;
                        int targetInsnIndex = insnList.indexOf(tableSwitchInsn.dflt);
                        currentFrame.initJumpTarget(insnOpcode, tableSwitchInsn.dflt);
                        checkFrame(targetInsnIndex, currentFrame, /* requireFrame = */ true);
                        newControlFlowEdge(insnIndex, targetInsnIndex);
                        for (int i = 0; i < tableSwitchInsn.labels.size(); ++i) {
                            LabelNode label = tableSwitchInsn.labels.get(i);
                            currentFrame.initJumpTarget(insnOpcode, label);
                            targetInsnIndex = insnList.indexOf(label);
                            checkFrame(targetInsnIndex, currentFrame, /* requireFrame = */ true);
                        }
                        endControlFlow(insnIndex);
                    } else if (insnOpcode == RET) {
                        throw new AnalyzerException(insnNode, "RET instructions are unsupported");
                    } else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
                        checkFrame(insnIndex + 1, currentFrame, /* requireFrame = */ false);
                    } else {
                        endControlFlow(insnIndex);
                    }
                }

                List<TryCatchBlockNode> insnHandlers = getHandlers(insnIndex);
                if (insnHandlers != null) {
                    for (TryCatchBlockNode tryCatchBlock : insnHandlers) {
                        Type catchType;
                        if (tryCatchBlock.type == null) {
                            catchType = Type.getObjectType("java/lang/Throwable");
                        } else {
                            catchType = Type.getObjectType(tryCatchBlock.type);
                        }
                        Frame<V> handler = newFrame(oldFrame);
                        handler.clearStack();
                        handler.push(interpreter.newExceptionValue(tryCatchBlock, handler, catchType));
                        checkFrame(insnList.indexOf(tryCatchBlock.handler), handler, /* requireFrame = */ true);
                    }
                }

                if (!hasNextJvmInsnOrFrame(insnIndex)) {
                    break;
                }
            } catch (AnalyzerException e) {
                throw new AnalyzerException(
                        e.node, "Error at instruction " + insnIndex + ": " + e.getMessage(), e);
            } catch (RuntimeException e) {
                // DontCheck(IllegalCatch): can't be fixed, for backward compatibility.
                throw new AnalyzerException(
                        insnNode, "Error at instruction " + insnIndex + ": " + e.getMessage(), e);
            }
        }
    }

    /**
      * Expands the {@link FrameNode} "instructions" of the given method into {@link Frame} objects and
      * stores them at the corresponding indices of the {@link #frames} array. The expanded frames are
      * also associated with the label and line number nodes immediately preceding each frame node.
      *
      * @param owner the internal name of the class to which 'method' belongs.
      * @param method the method whose frames must be expanded.
      * @param initialFrame the implicit initial frame of 'method'.
      * @throws AnalyzerException if the stack map frames of 'method', i.e. its FrameNode
      *     "instructions", are invalid.
      */
    private void expandFrames(
            final String owner, final MethodNode method, final Frame<V> initialFrame)
            throws AnalyzerException {
        int lastJvmOrFrameInsnIndex = -1;
        Frame<V> currentFrame = initialFrame;
        int currentInsnIndex = 0;
        for (AbstractInsnNode insnNode : method.instructions) {
            if (insnNode instanceof FrameNode) {
                try {
                    currentFrame = expandFrame(owner, currentFrame, (FrameNode) insnNode);
                } catch (AnalyzerException e) {
                    throw new AnalyzerException(
                            e.node, "Error at instruction " + currentInsnIndex + ": " + e.getMessage(), e);
                }
                for (int index = lastJvmOrFrameInsnIndex + 1; index <= currentInsnIndex; ++index) {
                    getFrames()[index] = currentFrame;
                }
            }
            if (isJvmInsnNode(insnNode) || insnNode instanceof FrameNode) {
                lastJvmOrFrameInsnIndex = currentInsnIndex;
            }
            currentInsnIndex += 1;
        }
    }

    /**
      * Returns the expanded representation of the given {@link FrameNode}.
      *
      * @param owner the internal name of the class to which 'frameNode' belongs.
      * @param previousFrame the frame before 'frameNode', in expanded form.
      * @param frameNode a possibly compressed stack map frame.
      * @return the expanded version of 'frameNode'.
      * @throws AnalyzerException if 'frameNode' is invalid.
      */
    @SuppressWarnings("fallthrough")
    private Frame<V> expandFrame(
            final String owner, final Frame<V> previousFrame, final FrameNode frameNode)
            throws AnalyzerException {
        Frame<V> frame = newFrame(previousFrame);
        List<Object> locals = frameNode.local == null ? Collections.emptyList() : frameNode.local;
        int currentLocal = currentLocals;
        switch (frameNode.type) {
            case Opcodes.F_NEW:
            case Opcodes.F_FULL:
                currentLocal = 0;
                // fall through
            case Opcodes.F_APPEND:
                for (Object type : locals) {
                    V value = newFrameValue(owner, frameNode, type);
                    if (currentLocal + value.getSize() > frame.getLocals()) {
                        throw new AnalyzerException(frameNode, "Cannot append more locals than maxLocals");
                    }
                    frame.setLocal(currentLocal++, value);
                    if (value.getSize() == 2) {
                        frame.setLocal(currentLocal++, interpreter.newValue(null));
                    }
                }
                break;
            case Opcodes.F_CHOP:
                for (Object unusedType : locals) {
                    if (currentLocal <= 0) {
                        throw new AnalyzerException(frameNode, "Cannot chop more locals than defined");
                    }
                    if (currentLocal > 1 && frame.getLocal(currentLocal - 2).getSize() == 2) {
                        currentLocal -= 2;
                    } else {
                        currentLocal -= 1;
                    }
                }
                break;
            case Opcodes.F_SAME:
            case Opcodes.F_SAME1:
                break;
            default:
                throw new AnalyzerException(frameNode, "Illegal frame type " + frameNode.type);
        }
        currentLocals = currentLocal;
        while (currentLocal < frame.getLocals()) {
            frame.setLocal(currentLocal++, interpreter.newValue(null));
        }

        List<Object> stack = frameNode.stack == null ? Collections.emptyList() : frameNode.stack;
        frame.clearStack();
        for (Object type : stack) {
            frame.push(newFrameValue(owner, frameNode, type));
        }
        return frame;
    }

    /**
      * Creates a new {@link Value} that represents the given stack map frame type.
      *
      * @param owner the internal name of the class to which 'frameNode' belongs.
      * @param frameNode the stack map frame to which 'type' belongs.
      * @param type an Integer, String or LabelNode object representing a primitive, reference or
      *     uninitialized a stack map frame type, respectively. See {@link FrameNode}.
      * @return a value that represents the given type.
      * @throws AnalyzerException if 'type' is an invalid stack map frame type.
      */
    private V newFrameValue(final String owner, final FrameNode frameNode, final Object type)
            throws AnalyzerException {
        if (type == Opcodes.TOP) {
            return interpreter.newValue(null);
        } else if (type == Opcodes.INTEGER) {
            return interpreter.newValue(Type.INT_TYPE);
        } else if (type == Opcodes.FLOAT) {
            return interpreter.newValue(Type.FLOAT_TYPE);
        } else if (type == Opcodes.LONG) {
            return interpreter.newValue(Type.LONG_TYPE);
        } else if (type == Opcodes.DOUBLE) {
            return interpreter.newValue(Type.DOUBLE_TYPE);
        } else if (type == Opcodes.NULL) {
            return interpreter.newOperation(new InsnNode(Opcodes.ACONST_NULL));
        } else if (type == Opcodes.UNINITIALIZED_THIS) {
            return interpreter.newValue(Type.getObjectType(owner));
        } else if (type instanceof String) {
            return interpreter.newValue(Type.getObjectType((String) type));
        } else if (type instanceof LabelNode) {
            AbstractInsnNode referencedNode = (LabelNode) type;
            while (referencedNode != null && !isJvmInsnNode(referencedNode)) {
                referencedNode = referencedNode.getNext();
            }
            if (referencedNode == null || referencedNode.getOpcode() != Opcodes.NEW) {
                throw new AnalyzerException(frameNode, "LabelNode does not designate a NEW instruction");
            }
            return interpreter.newValue(Type.getObjectType(((TypeInsnNode) referencedNode).desc));
        }
        throw new AnalyzerException(frameNode, "Illegal stack map frame value " + type);
    }

    /**
      * Checks that the given frame is compatible with the frame at the given instruction index, if
      * any. If there is no frame at this instruction index and none is required, the frame at
      * 'insnIndex' is set to the given frame. Otherwise, if the merge of the two frames is not equal
      * to the current frame at 'insnIndex', an exception is thrown.
      *
      * @param insnIndex an instruction index.
      * @param frame a frame. This frame is left unchanged by this method.
      * @param requireFrame whether a frame must already exist or not in {@link #frames} at
      *     'insnIndex'.
      * @throws AnalyzerException if the frames have incompatible sizes or if the frame at 'insnIndex'
      *     is missing (if required) or not compatible with 'frame'.
      */
    private void checkFrame(final int insnIndex, final Frame<V> frame, final boolean requireFrame)
            throws AnalyzerException {
        Frame<V> oldFrame = getFrames()[insnIndex];
        if (oldFrame == null) {
            if (requireFrame) {
                throw new AnalyzerException(null, "Expected stack map frame at instruction " + insnIndex);
            }
            getFrames()[insnIndex] = newFrame(frame);
        } else {
            String error = checkMerge(frame, oldFrame);
            if (error != null) {
                throw new AnalyzerException(
                        null,
                        "Stack map frame incompatible with frame at instruction "
                                + insnIndex
                                + " ("
                                + error
                                + ")");
            }
        }
    }

    /**
      * Checks that merging the two given frames would not produce any change, i.e. that the types in
      * the source frame are sub types of the corresponding types in the destination frame.
      *
      * @param srcFrame a source frame. This frame is left unchanged by this method.
      * @param dstFrame a destination frame. This frame is left unchanged by this method.
      * @return an error message if the frames have incompatible sizes, or if a type in the source
      *     frame is not a sub type of the corresponding type in the destination frame. Returns
      *     {@literal null} otherwise.
      */
    private String checkMerge(final Frame<V> srcFrame, final Frame<V> dstFrame) {
        int numLocals = srcFrame.getLocals();
        if (numLocals != dstFrame.getLocals()) {
            throw new AssertionError();
        }
        for (int i = 0; i < numLocals; ++i) {
            V v = interpreter.merge(srcFrame.getLocal(i), dstFrame.getLocal(i));
            if (!v.equals(dstFrame.getLocal(i))) {
                return "incompatible types at local "
                        + i
                        + ": "
                        + srcFrame.getLocal(i)
                        + " and "
                        + dstFrame.getLocal(i);
            }
        }
        int numStack = srcFrame.getStackSize();
        if (numStack != dstFrame.getStackSize()) {
            return "incompatible stack heights";
        }
        for (int i = 0; i < numStack; ++i) {
            V v = interpreter.merge(srcFrame.getStack(i), dstFrame.getStack(i));
            if (!v.equals(dstFrame.getStack(i))) {
                return "incompatible types at stack item "
                        + i
                        + ": "
                        + srcFrame.getStack(i)
                        + " and "
                        + dstFrame.getStack(i);
            }
        }
        return null;
    }

    /**
      * Ends the control flow graph at the given instruction. This method checks that there is an
      * existing frame for the next instruction, if any.
      *
      * @param insnIndex an instruction index.
      * @throws AnalyzerException if 'insnIndex' is not the last instruction and there is no frame at
      *     'insnIndex' + 1 in {@link #getFrames}.
      */
    private void endControlFlow(final int insnIndex) throws AnalyzerException {
        if (hasNextJvmInsnOrFrame(insnIndex) && getFrames()[insnIndex + 1] == null) {
            throw new AnalyzerException(
                    null, "Expected stack map frame at instruction " + (insnIndex + 1));
        }
    }

    /**
      * Returns true if the given instruction is followed by a JVM instruction or a by stack map frame.
      *
      * @param insnIndex an instruction index.
      * @return true if 'insnIndex' is followed by a JVM instruction or a by stack map frame.
      */
    private boolean hasNextJvmInsnOrFrame(final int insnIndex) {
        AbstractInsnNode insn = insnList.get(insnIndex).getNext();
        while (insn != null) {
            if (isJvmInsnNode(insn) || insn instanceof FrameNode) {
                return true;
            }
            insn = insn.getNext();
        }
        return false;
    }

    /**
      * Returns true if the given instruction node corresponds to a real JVM instruction.
      *
      * @param insnNode an instruction node.
      * @return true except for label, line number and stack map frame nodes.
      */
    private static boolean isJvmInsnNode(final AbstractInsnNode insnNode) {
        return insnNode.getOpcode() >= 0;
    }
}
