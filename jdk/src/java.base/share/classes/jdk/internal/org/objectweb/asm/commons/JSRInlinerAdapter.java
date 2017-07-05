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
package jdk.internal.org.objectweb.asm.commons;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.InsnNode;
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LocalVariableNode;
import jdk.internal.org.objectweb.asm.tree.LookupSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TableSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * A {@link jdk.internal.org.objectweb.asm.MethodVisitor} that removes JSR instructions and
 * inlines the referenced subroutines.
 *
 * <b>Explanation of how it works</b> TODO
 *
 * @author Niko Matsakis
 */
public class JSRInlinerAdapter extends MethodNode implements Opcodes {

    private static final boolean LOGGING = false;

    /**
     * For each label that is jumped to by a JSR, we create a BitSet instance.
     */
    private final Map<LabelNode, BitSet> subroutineHeads = new HashMap<LabelNode, BitSet>();

    /**
     * This subroutine instance denotes the line of execution that is not
     * contained within any subroutine; i.e., the "subroutine" that is executing
     * when a method first begins.
     */
    private final BitSet mainSubroutine = new BitSet();

    /**
     * This BitSet contains the index of every instruction that belongs to more
     * than one subroutine. This should not happen often.
     */
    final BitSet dualCitizens = new BitSet();

    /**
     * Creates a new JSRInliner. <i>Subclasses must not use this
     * constructor</i>. Instead, they must use the
     * {@link #JSRInlinerAdapter(int, MethodVisitor, int, String, String, String, String[])}
     * version.
     *
     * @param mv
     *            the <code>MethodVisitor</code> to send the resulting inlined
     *            method code to (use <code>null</code> for none).
     * @param access
     *            the method's access flags (see {@link Opcodes}). This
     *            parameter also indicates if the method is synthetic and/or
     *            deprecated.
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link Type}).
     * @param signature
     *            the method's signature. May be <tt>null</tt>.
     * @param exceptions
     *            the internal names of the method's exception classes (see
     *            {@link Type#getInternalName() getInternalName}). May be
     *            <tt>null</tt>.
     * @throws IllegalStateException
     *             If a subclass calls this constructor.
     */
    public JSRInlinerAdapter(final MethodVisitor mv, final int access,
            final String name, final String desc, final String signature,
            final String[] exceptions) {
        this(Opcodes.ASM5, mv, access, name, desc, signature, exceptions);
        if (getClass() != JSRInlinerAdapter.class) {
            throw new IllegalStateException();
        }
    }

    /**
     * Creates a new JSRInliner.
     *
     * @param api
     *            the ASM API version implemented by this visitor. Must be one
     *            of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
     * @param mv
     *            the <code>MethodVisitor</code> to send the resulting inlined
     *            method code to (use <code>null</code> for none).
     * @param access
     *            the method's access flags (see {@link Opcodes}). This
     *            parameter also indicates if the method is synthetic and/or
     *            deprecated.
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link Type}).
     * @param signature
     *            the method's signature. May be <tt>null</tt>.
     * @param exceptions
     *            the internal names of the method's exception classes (see
     *            {@link Type#getInternalName() getInternalName}). May be
     *            <tt>null</tt>.
     */
    protected JSRInlinerAdapter(final int api, final MethodVisitor mv,
            final int access, final String name, final String desc,
            final String signature, final String[] exceptions) {
        super(api, access, name, desc, signature, exceptions);
        this.mv = mv;
    }

    /**
     * Detects a JSR instruction and sets a flag to indicate we will need to do
     * inlining.
     */
    @Override
    public void visitJumpInsn(final int opcode, final Label lbl) {
        super.visitJumpInsn(opcode, lbl);
        LabelNode ln = ((JumpInsnNode) instructions.getLast()).label;
        if (opcode == JSR && !subroutineHeads.containsKey(ln)) {
            subroutineHeads.put(ln, new BitSet());
        }
    }

    /**
     * If any JSRs were seen, triggers the inlining process. Otherwise, forwards
     * the byte codes untouched.
     */
    @Override
    public void visitEnd() {
        if (!subroutineHeads.isEmpty()) {
            markSubroutines();
            if (LOGGING) {
                log(mainSubroutine.toString());
                Iterator<BitSet> it = subroutineHeads.values().iterator();
                while (it.hasNext()) {
                    BitSet sub = it.next();
                    log(sub.toString());
                }
            }
            emitCode();
        }

        // Forward the translate opcodes on if appropriate:
        if (mv != null) {
            accept(mv);
        }
    }

    /**
     * Walks the method and determines which internal subroutine(s), if any,
     * each instruction is a method of.
     */
    private void markSubroutines() {
        BitSet anyvisited = new BitSet();

        // First walk the main subroutine and find all those instructions which
        // can be reached without invoking any JSR at all
        markSubroutineWalk(mainSubroutine, 0, anyvisited);

        // Go through the head of each subroutine and find any nodes reachable
        // to that subroutine without following any JSR links.
        for (Iterator<Map.Entry<LabelNode, BitSet>> it = subroutineHeads
                .entrySet().iterator(); it.hasNext();) {
            Map.Entry<LabelNode, BitSet> entry = it.next();
            LabelNode lab = entry.getKey();
            BitSet sub = entry.getValue();
            int index = instructions.indexOf(lab);
            markSubroutineWalk(sub, index, anyvisited);
        }
    }

    /**
     * Performs a depth first search walking the normal byte code path starting
     * at <code>index</code>, and adding each instruction encountered into the
     * subroutine <code>sub</code>. After this walk is complete, iterates over
     * the exception handlers to ensure that we also include those byte codes
     * which are reachable through an exception that may be thrown during the
     * execution of the subroutine. Invoked from <code>markSubroutines()</code>.
     *
     * @param sub
     *            the subroutine whose instructions must be computed.
     * @param index
     *            an instruction of this subroutine.
     * @param anyvisited
     *            indexes of the already visited instructions, i.e. marked as
     *            part of this subroutine or any previously computed subroutine.
     */
    private void markSubroutineWalk(final BitSet sub, final int index,
            final BitSet anyvisited) {
        if (LOGGING) {
            log("markSubroutineWalk: sub=" + sub + " index=" + index);
        }

        // First find those instructions reachable via normal execution
        markSubroutineWalkDFS(sub, index, anyvisited);

        // Now, make sure we also include any applicable exception handlers
        boolean loop = true;
        while (loop) {
            loop = false;
            for (Iterator<TryCatchBlockNode> it = tryCatchBlocks.iterator(); it
                    .hasNext();) {
                TryCatchBlockNode trycatch = it.next();

                if (LOGGING) {
                    // TODO use of default toString().
                    log("Scanning try/catch " + trycatch);
                }

                // If the handler has already been processed, skip it.
                int handlerindex = instructions.indexOf(trycatch.handler);
                if (sub.get(handlerindex)) {
                    continue;
                }

                int startindex = instructions.indexOf(trycatch.start);
                int endindex = instructions.indexOf(trycatch.end);
                int nextbit = sub.nextSetBit(startindex);
                if (nextbit != -1 && nextbit < endindex) {
                    if (LOGGING) {
                        log("Adding exception handler: " + startindex + '-'
                                + endindex + " due to " + nextbit + " handler "
                                + handlerindex);
                    }
                    markSubroutineWalkDFS(sub, handlerindex, anyvisited);
                    loop = true;
                }
            }
        }
    }

    /**
     * Performs a simple DFS of the instructions, assigning each to the
     * subroutine <code>sub</code>. Starts from <code>index</code>. Invoked only
     * by <code>markSubroutineWalk()</code>.
     *
     * @param sub
     *            the subroutine whose instructions must be computed.
     * @param index
     *            an instruction of this subroutine.
     * @param anyvisited
     *            indexes of the already visited instructions, i.e. marked as
     *            part of this subroutine or any previously computed subroutine.
     */
    private void markSubroutineWalkDFS(final BitSet sub, int index,
            final BitSet anyvisited) {
        while (true) {
            AbstractInsnNode node = instructions.get(index);

            // don't visit a node twice
            if (sub.get(index)) {
                return;
            }
            sub.set(index);

            // check for those nodes already visited by another subroutine
            if (anyvisited.get(index)) {
                dualCitizens.set(index);
                if (LOGGING) {
                    log("Instruction #" + index + " is dual citizen.");
                }
            }
            anyvisited.set(index);

            if (node.getType() == AbstractInsnNode.JUMP_INSN
                    && node.getOpcode() != JSR) {
                // we do not follow recursively called subroutines here; but any
                // other sort of branch we do follow
                JumpInsnNode jnode = (JumpInsnNode) node;
                int destidx = instructions.indexOf(jnode.label);
                markSubroutineWalkDFS(sub, destidx, anyvisited);
            }
            if (node.getType() == AbstractInsnNode.TABLESWITCH_INSN) {
                TableSwitchInsnNode tsnode = (TableSwitchInsnNode) node;
                int destidx = instructions.indexOf(tsnode.dflt);
                markSubroutineWalkDFS(sub, destidx, anyvisited);
                for (int i = tsnode.labels.size() - 1; i >= 0; --i) {
                    LabelNode l = tsnode.labels.get(i);
                    destidx = instructions.indexOf(l);
                    markSubroutineWalkDFS(sub, destidx, anyvisited);
                }
            }
            if (node.getType() == AbstractInsnNode.LOOKUPSWITCH_INSN) {
                LookupSwitchInsnNode lsnode = (LookupSwitchInsnNode) node;
                int destidx = instructions.indexOf(lsnode.dflt);
                markSubroutineWalkDFS(sub, destidx, anyvisited);
                for (int i = lsnode.labels.size() - 1; i >= 0; --i) {
                    LabelNode l = lsnode.labels.get(i);
                    destidx = instructions.indexOf(l);
                    markSubroutineWalkDFS(sub, destidx, anyvisited);
                }
            }

            // check to see if this opcode falls through to the next instruction
            // or not; if not, return.
            switch (instructions.get(index).getOpcode()) {
            case GOTO:
            case RET:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case RETURN:
            case ATHROW:
                /*
                 * note: this either returns from this subroutine, or a parent
                 * subroutine which invoked it
                 */
                return;
            }

            // Use tail recursion here in the form of an outer while loop to
            // avoid our stack growing needlessly:
            index++;

            // We implicitly assumed above that execution can always fall
            // through to the next instruction after a JSR. But a subroutine may
            // never return, in which case the code after the JSR is unreachable
            // and can be anything. In particular, it can seem to fall off the
            // end of the method, so we must handle this case here (we could
            // instead detect whether execution can return or not from a JSR,
            // but this is more complicated).
            if (index >= instructions.size()) {
                return;
            }
        }
    }

    /**
     * Creates the new instructions, inlining each instantiation of each
     * subroutine until the code is fully elaborated.
     */
    private void emitCode() {
        LinkedList<Instantiation> worklist = new LinkedList<Instantiation>();
        // Create an instantiation of the "root" subroutine, which is just the
        // main routine
        worklist.add(new Instantiation(null, mainSubroutine));

        // Emit instantiations of each subroutine we encounter, including the
        // main subroutine
        InsnList newInstructions = new InsnList();
        List<TryCatchBlockNode> newTryCatchBlocks = new ArrayList<TryCatchBlockNode>();
        List<LocalVariableNode> newLocalVariables = new ArrayList<LocalVariableNode>();
        while (!worklist.isEmpty()) {
            Instantiation inst = worklist.removeFirst();
            emitSubroutine(inst, worklist, newInstructions, newTryCatchBlocks,
                    newLocalVariables);
        }
        instructions = newInstructions;
        tryCatchBlocks = newTryCatchBlocks;
        localVariables = newLocalVariables;
    }

    /**
     * Emits one instantiation of one subroutine, specified by
     * <code>instant</code>. May add new instantiations that are invoked by this
     * one to the <code>worklist</code> parameter, and new try/catch blocks to
     * <code>newTryCatchBlocks</code>.
     *
     * @param instant
     *            the instantiation that must be performed.
     * @param worklist
     *            list of the instantiations that remain to be done.
     * @param newInstructions
     *            the instruction list to which the instantiated code must be
     *            appended.
     * @param newTryCatchBlocks
     *            the exception handler list to which the instantiated handlers
     *            must be appended.
     */
    private void emitSubroutine(final Instantiation instant,
            final List<Instantiation> worklist, final InsnList newInstructions,
            final List<TryCatchBlockNode> newTryCatchBlocks,
            final List<LocalVariableNode> newLocalVariables) {
        LabelNode duplbl = null;

        if (LOGGING) {
            log("--------------------------------------------------------");
            log("Emitting instantiation of subroutine " + instant.subroutine);
        }

        // Emit the relevant instructions for this instantiation, translating
        // labels and jump targets as we go:
        for (int i = 0, c = instructions.size(); i < c; i++) {
            AbstractInsnNode insn = instructions.get(i);
            Instantiation owner = instant.findOwner(i);

            // Always remap labels:
            if (insn.getType() == AbstractInsnNode.LABEL) {
                // Translate labels into their renamed equivalents.
                // Avoid adding the same label more than once. Note
                // that because we own this instruction the gotoTable
                // and the rangeTable will always agree.
                LabelNode ilbl = (LabelNode) insn;
                LabelNode remap = instant.rangeLabel(ilbl);
                if (LOGGING) {
                    // TODO use of default toString().
                    log("Translating lbl #" + i + ':' + ilbl + " to " + remap);
                }
                if (remap != duplbl) {
                    newInstructions.add(remap);
                    duplbl = remap;
                }
                continue;
            }

            // We don't want to emit instructions that were already
            // emitted by a subroutine higher on the stack. Note that
            // it is still possible for a given instruction to be
            // emitted twice because it may belong to two subroutines
            // that do not invoke each other.
            if (owner != instant) {
                continue;
            }

            if (LOGGING) {
                log("Emitting inst #" + i);
            }

            if (insn.getOpcode() == RET) {
                // Translate RET instruction(s) to a jump to the return label
                // for the appropriate instantiation. The problem is that the
                // subroutine may "fall through" to the ret of a parent
                // subroutine; therefore, to find the appropriate ret label we
                // find the lowest subroutine on the stack that claims to own
                // this instruction. See the class javadoc comment for an
                // explanation on why this technique is safe (note: it is only
                // safe if the input is verifiable).
                LabelNode retlabel = null;
                for (Instantiation p = instant; p != null; p = p.previous) {
                    if (p.subroutine.get(i)) {
                        retlabel = p.returnLabel;
                    }
                }
                if (retlabel == null) {
                    // This is only possible if the mainSubroutine owns a RET
                    // instruction, which should never happen for verifiable
                    // code.
                    throw new RuntimeException("Instruction #" + i
                            + " is a RET not owned by any subroutine");
                }
                newInstructions.add(new JumpInsnNode(GOTO, retlabel));
            } else if (insn.getOpcode() == JSR) {
                LabelNode lbl = ((JumpInsnNode) insn).label;
                BitSet sub = subroutineHeads.get(lbl);
                Instantiation newinst = new Instantiation(instant, sub);
                LabelNode startlbl = newinst.gotoLabel(lbl);

                if (LOGGING) {
                    log(" Creating instantiation of subr " + sub);
                }

                // Rather than JSRing, we will jump to the inline version and
                // push NULL for what was once the return value. This hack
                // allows us to avoid doing any sort of data flow analysis to
                // figure out which instructions manipulate the old return value
                // pointer which is now known to be unneeded.
                newInstructions.add(new InsnNode(ACONST_NULL));
                newInstructions.add(new JumpInsnNode(GOTO, startlbl));
                newInstructions.add(newinst.returnLabel);

                // Insert this new instantiation into the queue to be emitted
                // later.
                worklist.add(newinst);
            } else {
                newInstructions.add(insn.clone(instant));
            }
        }

        // Emit try/catch blocks that are relevant to this method.
        for (Iterator<TryCatchBlockNode> it = tryCatchBlocks.iterator(); it
                .hasNext();) {
            TryCatchBlockNode trycatch = it.next();

            if (LOGGING) {
                // TODO use of default toString().
                log("try catch block original labels=" + trycatch.start + '-'
                        + trycatch.end + "->" + trycatch.handler);
            }

            final LabelNode start = instant.rangeLabel(trycatch.start);
            final LabelNode end = instant.rangeLabel(trycatch.end);

            // Ignore empty try/catch regions
            if (start == end) {
                if (LOGGING) {
                    log(" try catch block empty in this subroutine");
                }
                continue;
            }

            final LabelNode handler = instant.gotoLabel(trycatch.handler);

            if (LOGGING) {
                // TODO use of default toString().
                log(" try catch block new labels=" + start + '-' + end + "->"
                        + handler);
            }

            if (start == null || end == null || handler == null) {
                throw new RuntimeException("Internal error!");
            }

            newTryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                    trycatch.type));
        }

        for (Iterator<LocalVariableNode> it = localVariables.iterator(); it
                .hasNext();) {
            LocalVariableNode lvnode = it.next();
            if (LOGGING) {
                log("local var " + lvnode.name);
            }
            final LabelNode start = instant.rangeLabel(lvnode.start);
            final LabelNode end = instant.rangeLabel(lvnode.end);
            if (start == end) {
                if (LOGGING) {
                    log("  local variable empty in this sub");
                }
                continue;
            }
            newLocalVariables.add(new LocalVariableNode(lvnode.name,
                    lvnode.desc, lvnode.signature, start, end, lvnode.index));
        }
    }

    private static void log(final String str) {
        System.err.println(str);
    }

    /**
     * A class that represents an instantiation of a subroutine. Each
     * instantiation has an associate "stack" --- which is a listing of those
     * instantiations that were active when this particular instance of this
     * subroutine was invoked. Each instantiation also has a map from the
     * original labels of the program to the labels appropriate for this
     * instantiation, and finally a label to return to.
     */
    private class Instantiation extends AbstractMap<LabelNode, LabelNode> {

        /**
         * Previous instantiations; the stack must be statically predictable to
         * be inlinable.
         */
        final Instantiation previous;

        /**
         * The subroutine this is an instantiation of.
         */
        public final BitSet subroutine;

        /**
         * This table maps Labels from the original source to Labels pointing at
         * code specific to this instantiation, for use in remapping try/catch
         * blocks,as well as gotos.
         *
         * Note that in the presence of dual citizens instructions, that is,
         * instructions which belong to more than one subroutine due to the
         * merging of control flow without a RET instruction, we will map the
         * target label of a GOTO to the label used by the instantiation lowest
         * on the stack. This avoids code duplication during inlining in most
         * cases.
         *
         * @see #findOwner(int)
         */
        public final Map<LabelNode, LabelNode> rangeTable = new HashMap<LabelNode, LabelNode>();

        /**
         * All returns for this instantiation will be mapped to this label
         */
        public final LabelNode returnLabel;

        Instantiation(final Instantiation prev, final BitSet sub) {
            previous = prev;
            subroutine = sub;
            for (Instantiation p = prev; p != null; p = p.previous) {
                if (p.subroutine == sub) {
                    throw new RuntimeException("Recursive invocation of " + sub);
                }
            }

            // Determine the label to return to when this subroutine terminates
            // via RET: note that the main subroutine never terminates via RET.
            if (prev != null) {
                returnLabel = new LabelNode();
            } else {
                returnLabel = null;
            }

            // Each instantiation will remap the labels from the code above to
            // refer to its particular copy of its own instructions. Note that
            // we collapse labels which point at the same instruction into one:
            // this is fairly common as we are often ignoring large chunks of
            // instructions, so what were previously distinct labels become
            // duplicates.
            LabelNode duplbl = null;
            for (int i = 0, c = instructions.size(); i < c; i++) {
                AbstractInsnNode insn = instructions.get(i);

                if (insn.getType() == AbstractInsnNode.LABEL) {
                    LabelNode ilbl = (LabelNode) insn;

                    if (duplbl == null) {
                        // if we already have a label pointing at this spot,
                        // don't recreate it.
                        duplbl = new LabelNode();
                    }

                    // Add an entry in the rangeTable for every label
                    // in the original code which points at the next
                    // instruction of our own to be emitted.
                    rangeTable.put(ilbl, duplbl);
                } else if (findOwner(i) == this) {
                    // We will emit this instruction, so clear the 'duplbl' flag
                    // since the next Label will refer to a distinct
                    // instruction.
                    duplbl = null;
                }
            }
        }

        /**
         * Returns the "owner" of a particular instruction relative to this
         * instantiation: the owner referes to the Instantiation which will emit
         * the version of this instruction that we will execute.
         *
         * Typically, the return value is either <code>this</code> or
         * <code>null</code>. <code>this</code> indicates that this
         * instantiation will generate the version of this instruction that we
         * will execute, and <code>null</code> indicates that this instantiation
         * never executes the given instruction.
         *
         * Sometimes, however, an instruction can belong to multiple
         * subroutines; this is called a "dual citizen" instruction (though it
         * may belong to more than 2 subroutines), and occurs when multiple
         * subroutines branch to common points of control. In this case, the
         * owner is the subroutine that appears lowest on the stack, and which
         * also owns the instruction in question.
         *
         * @param i
         *            the index of the instruction in the original code
         * @return the "owner" of a particular instruction relative to this
         *         instantiation.
         */
        public Instantiation findOwner(final int i) {
            if (!subroutine.get(i)) {
                return null;
            }
            if (!dualCitizens.get(i)) {
                return this;
            }
            Instantiation own = this;
            for (Instantiation p = previous; p != null; p = p.previous) {
                if (p.subroutine.get(i)) {
                    own = p;
                }
            }
            return own;
        }

        /**
         * Looks up the label <code>l</code> in the <code>gotoTable</code>, thus
         * translating it from a Label in the original code, to a Label in the
         * inlined code that is appropriate for use by an instruction that
         * branched to the original label.
         *
         * @param l
         *            The label we will be translating
         * @return a label for use by a branch instruction in the inlined code
         * @see #rangeLabel
         */
        public LabelNode gotoLabel(final LabelNode l) {
            // owner should never be null, because owner is only null
            // if an instruction cannot be reached from this subroutine
            Instantiation owner = findOwner(instructions.indexOf(l));
            return owner.rangeTable.get(l);
        }

        /**
         * Looks up the label <code>l</code> in the <code>rangeTable</code>,
         * thus translating it from a Label in the original code, to a Label in
         * the inlined code that is appropriate for use by an try/catch or
         * variable use annotation.
         *
         * @param l
         *            The label we will be translating
         * @return a label for use by a try/catch or variable annotation in the
         *         original code
         * @see #rangeTable
         */
        public LabelNode rangeLabel(final LabelNode l) {
            return rangeTable.get(l);
        }

        // AbstractMap implementation

        @Override
        public Set<Map.Entry<LabelNode, LabelNode>> entrySet() {
            return null;
        }

        @Override
        public LabelNode get(final Object o) {
            return gotoLabel((LabelNode) o);
        }
    }
}
