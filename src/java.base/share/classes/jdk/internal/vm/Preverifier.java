/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.vm;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.*;

/**
 * Patches a java class file taken as an argument, replacing all JSR and RET instructions with a valid equivalent
 * JSR and RET instructions exist in pairs, usually with an ASTORE_X at the top of the subroutine containing
 * the RET. These instructions are deprecated and must be replaced with valid bytecodes such as GOTO.
 * 
 * @version 1.00 28 July 2021 
 * @author Matias Saavedra Silva
 */
public class Preverifier extends ClassVisitor {

	private static byte[] bytecode; // Contents of the class file
	private static ClassNode cn;
	private static String fileName;

	/**
	 * Reads class file, locates all JSR/RET instructions, and writes new class file 
	 * with new valid instructions
	 * @param bytecode Byte array with class file contents
	 * @return Updated classfile as a byte array
	 */
	public static byte[] patch(byte [] bytecode) {
        ClassReader cr;
		cr = new ClassReader(bytecode);
		cn = replaceOpcodes(cr, bytecode);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        if (cw.toByteArray().length < 1) {
        	throw new InternalError("Classfile not parsed correctly")
        }
        return cw.toByteArray();
    }

    /**
     * Constructor
     * @param api ASM API version
     * @param cw ClassWriter to write out new classfile
     */ 
    public Preverifier(int api, ClassWriter cw) {
        super(api, cw);
    }

	/**
	 * Builds map for cloning instructions
	 * @param insns Instruction list from a method
	 * @return Map used for cloning instructions
	 */
	public static Map<LabelNode, LabelNode> cloneLabels(InsnList insns) {
		HashMap<LabelNode, LabelNode> labelMap = new HashMap<>();
		for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
   	    	if (insn.getType() == 8) {
				labelMap.put((LabelNode) insn, new LabelNode());
			}
		}
		return labelMap;
	}

	/**
	 * Replaces JST and RET opcodes in the class file
	 * @param bytecode byte array containing the contents of the class file
	 * @param cr ClassReader for classfile
	 * @return ClassNode with altered instruction list
	 */
	public static ClassNode replaceOpcodes(ClassReader cr, byte[] bytecode) {	
		//System.out.println("Replacing opcodes...");
		// Flag for expanding bytecode when JSRs and RETs overlap
		boolean mustExpand = false;
		// Flag for repeated scans through instruction list
		boolean continueScanning = false;
		// Create classnode to view methods and instructions
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		//List of methods
		List<MethodNode> mns = cn.methods;

		System.out.println("Class name: " + cn.name + "\nMethods: " + mns.size());
		for (MethodNode mn : mns) {
			boolean hasJSR = false;
			InsnList inList = mn.instructions;
			// New list of instructions that should replace the previous list
			InsnList newInst = new InsnList();
			// Return label for RET
			LabelNode retLb = null;
			// Map for cloning instructions
			Map<LabelNode, LabelNode> cloneMap = cloneLabels(inList);
			// Maps a RET instruction to the label it must return to once converted to GOTO instruction
			HashMap<AbstractInsnNode, LabelNode> retLabelMap = new HashMap<>();				
			do {
				System.out.println("Method name: " + mn.name + " Instructions: " + inList.size()); 				
				for (int i = 0; i < inList.size(); i++) {
					mustExpand = false;
					// JSR instructions are replaced with GOTO to the same label
					// A new label is added after the new GOTO that the associated RET will return to 
					if (inList.get(i).getOpcode() == Opcodes.JSR || inList.get(i).getOpcode() == 201) {
						hasJSR = true;
						boolean hasRet = false;
						//System.out.println("Replacing JSR...");
						// Extract the operator from JSR
						LabelNode lb = ((JumpInsnNode)inList.get(i)).label;

						// Start from the target label and find the next RET instruction
						for(int j = inList.indexOf(lb); j < inList.size(); j++) {
							if (inList.get(j).getOpcode() == Opcodes.RET) {
								hasRet = true;
								if (retLabelMap.containsKey(inList.get(j))) {
									//System.out.println("Another JSR points to this RET!");
									mustExpand = true;
								}
								else {
									//System.out.println("Matching RET found");
									retLb = new LabelNode(new Label());
									retLabelMap.put(inList.get(j), retLb);
								}
								break;
							}
						}
						if (mustExpand) {
							//System.out.println("Expanding code...");
							// Push null to stack to replicate JSR pushing address
							newInst.add(new InsnNode(Opcodes.ACONST_NULL));
							for (AbstractInsnNode n = inList.get(inList.indexOf(lb)+1); n.getOpcode() != Opcodes.RET; n=n.getNext()) {
								// If there is a JSR in the code to be copied, replace it with the subroutine it points to
								if (n.getOpcode() == Opcodes.JSR) {
									//System.out.println("Replacing nested JSR");
									// Push null to stack to replicate JSR pushing address
									newInst.add(new InsnNode(Opcodes.ACONST_NULL));
									LabelNode nestedLb = ((JumpInsnNode)n).label;
									for (AbstractInsnNode m = inList.get(inList.indexOf(nestedLb)+1); m.getOpcode() != Opcodes.RET; m=m.getNext()) {
										//System.out.println("Insn: " + m.getOpcode());
										newInst.add(m.clone(cloneMap));
									}
								}
								else {
									newInst.add(n.clone(cloneMap));
								}
							}
						}
						else if (hasRet) {
							newInst.add(new InsnNode(Opcodes.ACONST_NULL));	
							newInst.add(new JumpInsnNode(Opcodes.GOTO, lb));
							newInst.add(retLb);
						}
						else {
							newInst.add(new InsnNode(Opcodes.ACONST_NULL));
							newInst.add(new JumpInsnNode(Opcodes.GOTO, lb));
						}
					}
					else if (inList.get(i).getOpcode() == Opcodes.RET) {
						//System.out.println("Replacing RET...");
						// Replace RET with GOTO which jumps to the label corresponding to its associated JSR
						if (!retLabelMap.containsKey(inList.get(i))) {
							//System.out.println("RET has no matching JSR yet");
							newInst.add(inList.get(i));
							continueScanning = true; // Matching JSR may be above RET
						}
						else {
							continueScanning = false; 
							newInst.add(new JumpInsnNode(Opcodes.GOTO, retLabelMap.get(inList.get(i))));
						}
					}
					else {
						newInst.add(inList.get(i));
					}
				}
				// Replace instructions in the method
				//System.out.println("Writing new instruction list");
				inList.clear();
				inList.add(newInst);
				inList.resetLabels(); // Don't know if this is necessary
			} while (continueScanning);
		} 
		return cn;
    }
}
