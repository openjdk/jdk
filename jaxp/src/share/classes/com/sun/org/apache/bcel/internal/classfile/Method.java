/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.classfile;

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
import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.generic.Type;
import java.io.*;

/**
 * This class represents the method info structure, i.e., the representation
 * for a method in the class. See JVM specification for details.
 * A method has access flags, a name, a signature and a number of attributes.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class Method extends FieldOrMethod {
  /**
   * Empty constructor, all attributes have to be defined via `setXXX'
   * methods. Use at your own risk.
   */
  public Method() {}

  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use clone() for a physical copy.
   */
  public Method(Method c) {
    super(c);
  }

  /**
   * Construct object from file stream.
   * @param file Input stream
   * @throws IOException
   * @throws ClassFormatException
   */
  Method(DataInputStream file, ConstantPool constant_pool)
    throws IOException, ClassFormatException
  {
    super(file, constant_pool);
  }

  /**
   * @param access_flags Access rights of method
   * @param name_index Points to field name in constant pool
   * @param signature_index Points to encoded signature
   * @param attributes Collection of attributes
   * @param constant_pool Array of constants
   */
  public Method(int access_flags, int name_index, int signature_index,
                Attribute[] attributes, ConstantPool constant_pool)
  {
    super(access_flags, name_index, signature_index, attributes, constant_pool);
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitMethod(this);
  }

  /**
   * @return Code attribute of method, if any
   */
  public final Code getCode() {
    for(int i=0; i < attributes_count; i++)
      if(attributes[i] instanceof Code)
        return (Code)attributes[i];

    return null;
  }

  /**
   * @return ExceptionTable attribute of method, if any, i.e., list all
   * exceptions the method may throw not exception handlers!
   */
  public final ExceptionTable getExceptionTable() {
    for(int i=0; i < attributes_count; i++)
      if(attributes[i] instanceof ExceptionTable)
        return (ExceptionTable)attributes[i];

    return null;
  }

  /** @return LocalVariableTable of code attribute if any, i.e. the call is forwarded
   * to the Code atribute.
   */
  public final LocalVariableTable getLocalVariableTable() {
    Code code = getCode();

    if(code != null)
      return code.getLocalVariableTable();
    else
      return null;
  }

  /** @return LineNumberTable of code attribute if any, i.e. the call is forwarded
   * to the Code atribute.
   */
  public final LineNumberTable getLineNumberTable() {
    Code code = getCode();

    if(code != null)
      return code.getLineNumberTable();
    else
      return null;
  }

  /**
   * Return string representation close to declaration format,
   * `public static void _main(String[] args) throws IOException', e.g.
   *
   * @return String representation of the method.
   */
  public final String toString() {
    ConstantUtf8  c;
    String        name, signature, access; // Short cuts to constant pool
    StringBuffer  buf;

    access = Utility.accessToString(access_flags);

    // Get name and signature from constant pool
    c = (ConstantUtf8)constant_pool.getConstant(signature_index,
                                                Constants.CONSTANT_Utf8);
    signature = c.getBytes();

    c = (ConstantUtf8)constant_pool.getConstant(name_index, Constants.CONSTANT_Utf8);
    name = c.getBytes();

    signature = Utility.methodSignatureToString(signature, name, access, true,
                                                getLocalVariableTable());
    buf = new StringBuffer(signature);

    for(int i=0; i < attributes_count; i++) {
      Attribute a = attributes[i];

      if(!((a instanceof Code) || (a instanceof ExceptionTable)))
        buf.append(" [" + a.toString() + "]");
    }

    ExceptionTable e = getExceptionTable();
    if(e != null) {
      String str = e.toString();
      if(!str.equals(""))
        buf.append("\n\t\tthrows " + str);
    }

    return buf.toString();
  }

  /**
   * @return deep copy of this method
   */
  public final Method copy(ConstantPool constant_pool) {
    return (Method)copy_(constant_pool);
  }

  /**
   * @return return type of method
   */
  public Type getReturnType() {
    return Type.getReturnType(getSignature());
  }

  /**
   * @return array of method argument types
   */
  public Type[] getArgumentTypes() {
    return Type.getArgumentTypes(getSignature());
  }
}
