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
import java.io.*;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * TABLESWITCH - Switch within given range of values, i.e., low..high
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see SWITCH
 */
public class TABLESWITCH extends Select {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  TABLESWITCH() {}

  /**
   * @param match sorted array of match values, match[0] must be low value,
   * match[match_length - 1] high value
   * @param targets where to branch for matched values
   * @param target default branch
   */
  public TABLESWITCH(int[] match, InstructionHandle[] targets,
                     InstructionHandle target) {
    super(com.sun.org.apache.bcel.internal.Constants.TABLESWITCH, match, targets, target);

    length = (short)(13 + match_length * 4); /* Alignment remainder assumed
                                              * 0 here, until dump time */
    fixed_length = length;
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    super.dump(out);

    int low = (match_length > 0)? match[0] : 0;
    out.writeInt(low);

    int high = (match_length > 0)? match[match_length - 1] : 0;
    out.writeInt(high);

    for(int i=0; i < match_length; i++)     // jump offsets
      out.writeInt(indices[i] = getTargetOffset(targets[i]));
  }

  /**
   * Read needed data (e.g. index) from file.
   */
  protected void initFromFile(ByteSequence bytes, boolean wide) throws IOException
  {
    super.initFromFile(bytes, wide);

    int low    = bytes.readInt();
    int high   = bytes.readInt();

    match_length = high - low + 1;
    fixed_length = (short)(13 + match_length * 4);
    length       = (short)(fixed_length + padding);

    match   = new int[match_length];
    indices = new int[match_length];
    targets = new InstructionHandle[match_length];

    for(int i=low; i <= high; i++)
      match[i - low] = i;

    for(int i=0; i < match_length; i++) {
      indices[i] = bytes.readInt();
    }
  }


  /**
   * Call corresponding visitor method(s). The order is:
   * Call visitor methods of implemented interfaces first, then
   * call methods according to the class hierarchy in descending order,
   * i.e., the most specific visitXXX() call comes last.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitVariableLengthInstruction(this);
    v.visitStackProducer(this);
    v.visitBranchInstruction(this);
    v.visitSelect(this);
    v.visitTABLESWITCH(this);
  }
}
