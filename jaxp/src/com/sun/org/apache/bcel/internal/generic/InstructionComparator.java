/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.generic;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache BCEL" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache BCEL", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

/**
 * Equality of instructions isn't clearly to be defined. You might
 * wish, for example, to compare whether instructions have the same
 * meaning. E.g., whether two INVOKEVIRTUALs describe the same
 * call.<br>The DEFAULT comparator however, considers two instructions
 * to be equal if they have same opcode and point to the same indexes
 * (if any) in the constant pool or the same local variable index. Branch
 * instructions must have the same target.
 *
 * @see Instruction
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public interface InstructionComparator {
  public static final InstructionComparator DEFAULT =
    new InstructionComparator() {
        public boolean equals(Instruction i1, Instruction i2) {
          if(i1.opcode == i2.opcode) {
            if(i1 instanceof Select) {
              InstructionHandle[] t1 = ((Select)i1).getTargets();
              InstructionHandle[] t2 = ((Select)i2).getTargets();

              if(t1.length == t2.length) {
                for(int i = 0; i < t1.length; i++) {
                  if(t1[i] != t2[i]) {
                    return false;
                  }
                }

                return true;
              }
            } else if(i1 instanceof BranchInstruction) {
              return ((BranchInstruction)i1).target ==
                ((BranchInstruction)i2).target;
            } else if(i1 instanceof ConstantPushInstruction) {
              return ((ConstantPushInstruction)i1).getValue().
                equals(((ConstantPushInstruction)i2).getValue());
            } else if(i1 instanceof IndexedInstruction) {
              return ((IndexedInstruction)i1).getIndex() ==
                ((IndexedInstruction)i2).getIndex();
            } else if(i1 instanceof NEWARRAY) {
              return ((NEWARRAY)i1).getTypecode() == ((NEWARRAY)i2).getTypecode();
            } else {
              return true;
            }
          }

          return false;
        }
      };

  public boolean equals(Instruction i1, Instruction i2);
}
