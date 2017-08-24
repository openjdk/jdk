/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;


import com.sun.org.apache.bcel.internal.classfile.*;

/**
 * This class represents a line number within a method, i.e., give an instruction
 * a line number corresponding to the source code line.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     LineNumber
 * @see     MethodGen
 */
public class LineNumberGen
  implements InstructionTargeter, Cloneable, java.io.Serializable
{
  private InstructionHandle ih;
  private int               src_line;

  /**
   * Create a line number.
   *
   * @param ih instruction handle to reference
   */
  public LineNumberGen(InstructionHandle ih, int src_line) {
    setInstruction(ih);
    setSourceLine(src_line);
  }

  /**
   * @return true, if ih is target of this line number
   */
  @Override
  public boolean containsTarget(InstructionHandle ih) {
    return this.ih == ih;
  }

  /**
   * @param old_ih old target
   * @param new_ih new target
   */
  @Override
  public void updateTarget(InstructionHandle old_ih, InstructionHandle new_ih) {
    if(old_ih != ih)
      throw new ClassGenException("Not targeting " + old_ih + ", but " + ih + "}");
    else
      setInstruction(new_ih);
  }

  /**
   * Get LineNumber attribute .
   *
   * This relies on that the instruction list has already been dumped to byte code or
   * or that the `setPositions' methods has been called for the instruction list.
   */
  public LineNumber getLineNumber() {
    return new LineNumber(ih.getPosition(), src_line);
  }

  public final void setInstruction(InstructionHandle ih) {
    BranchInstruction.notifyTargetChanging(this.ih, this);
    this.ih = ih;
    BranchInstruction.notifyTargetChanged(this.ih, this);
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch(CloneNotSupportedException e) {
      System.err.println(e);
      return null;
    }
  }

  public InstructionHandle getInstruction()               { return ih; }
  public void              setSourceLine(int src_line)    { this.src_line = src_line; }
  public int               getSourceLine()                { return src_line; }
}
