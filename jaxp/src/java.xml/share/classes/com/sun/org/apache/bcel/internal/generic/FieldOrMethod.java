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
 * Super class for InvokeInstruction and FieldInstruction, since they have
 * some methods in common!
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class FieldOrMethod extends CPInstruction implements LoadClass {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  FieldOrMethod() {}

  /**
   * @param index to constant pool
   */
  protected FieldOrMethod(short opcode, int index) {
    super(opcode, index);
  }

  /** @return signature of referenced method/field.
   */
  public String getSignature(ConstantPoolGen cpg) {
    ConstantPool        cp   = cpg.getConstantPool();
    ConstantCP          cmr  = (ConstantCP)cp.getConstant(index);
    ConstantNameAndType cnat = (ConstantNameAndType)cp.getConstant(cmr.getNameAndTypeIndex());

    return ((ConstantUtf8)cp.getConstant(cnat.getSignatureIndex())).getBytes();
  }

  /** @return name of referenced method/field.
   */
  public String getName(ConstantPoolGen cpg) {
    ConstantPool        cp   = cpg.getConstantPool();
    ConstantCP          cmr  = (ConstantCP)cp.getConstant(index);
    ConstantNameAndType cnat = (ConstantNameAndType)cp.getConstant(cmr.getNameAndTypeIndex());
    return ((ConstantUtf8)cp.getConstant(cnat.getNameIndex())).getBytes();
  }

  /** @return name of the referenced class/interface
   */
  public String getClassName(ConstantPoolGen cpg) {
    ConstantPool cp  = cpg.getConstantPool();
    ConstantCP   cmr = (ConstantCP)cp.getConstant(index);
    return cp.getConstantString(cmr.getClassIndex(), com.sun.org.apache.bcel.internal.Constants.CONSTANT_Class).replace('/', '.');
  }

  /** @return type of the referenced class/interface
   */
  public ObjectType getClassType(ConstantPoolGen cpg) {
    return new ObjectType(getClassName(cpg));
  }

  /** @return type of the referenced class/interface
   */
  public ObjectType getLoadClassType(ConstantPoolGen cpg) {
    return getClassType(cpg);
  }
}
