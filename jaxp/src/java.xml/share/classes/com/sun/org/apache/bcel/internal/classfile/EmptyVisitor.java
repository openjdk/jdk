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

package com.sun.org.apache.bcel.internal.classfile;


import com.sun.org.apache.bcel.internal.classfile.*;
import com.sun.org.apache.bcel.internal.*;

/**
 * Visitor with empty method bodies, can be extended and used in conjunction with the
 * DescendingVisitor class, e.g.
 *
 * By courtesy of David Spencer.
 *
 * @see DescendingVisitor
 *
 */
public class EmptyVisitor implements Visitor {
  protected EmptyVisitor() { }

  public void visitCode(Code obj) {}
  public void visitCodeException(CodeException obj) {}
  public void visitConstantClass(ConstantClass obj) {}
  public void visitConstantDouble(ConstantDouble obj) {}
  public void visitConstantFieldref(ConstantFieldref obj) {}
  public void visitConstantFloat(ConstantFloat obj) {}
  public void visitConstantInteger(ConstantInteger obj) {}
  public void visitConstantInterfaceMethodref(ConstantInterfaceMethodref obj) {}
  public void visitConstantLong(ConstantLong obj) {}
  public void visitConstantMethodref(ConstantMethodref obj) {}
  public void visitConstantNameAndType(ConstantNameAndType obj) {}
  public void visitConstantPool(ConstantPool obj) {}
  public void visitConstantString(ConstantString obj) {}
  public void visitConstantUtf8(ConstantUtf8 obj) {}
  public void visitConstantValue(ConstantValue obj) {}
  public void visitDeprecated(Deprecated obj) {}
  public void visitExceptionTable(ExceptionTable obj) {}
  public void visitField(Field obj) {}
  public void visitInnerClass(InnerClass obj) {}
  public void visitInnerClasses(InnerClasses obj) {}
  public void visitJavaClass(JavaClass obj) {}
  public void visitLineNumber(LineNumber obj) {}
  public void visitLineNumberTable(LineNumberTable obj) {}
  public void visitLocalVariable(LocalVariable obj) {}
  public void visitLocalVariableTable(LocalVariableTable obj) {}
  public void visitLocalVariableTypeTable(LocalVariableTypeTable obj) {}
  public void visitMethod(Method obj) {}
  public void visitSignature(Signature obj) {}
  public void visitSourceFile(SourceFile obj) {}
  public void visitSynthetic(Synthetic obj) {}
  public void visitUnknown(Unknown obj) {}
  public void visitStackMap(StackMap obj) {}
  public void visitStackMapEntry(StackMapEntry obj) {}
}
