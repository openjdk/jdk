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
import com.sun.org.apache.bcel.internal.Constants;

/**
 * Denotes array type, such as int[][]
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class ArrayType extends ReferenceType {
  private int  dimensions;
  private Type basic_type;

  /**
   * Convenience constructor for array type, e.g. int[]
   *
   * @param type array type, e.g. T_INT
   */
  public ArrayType(byte type, int dimensions) {
    this(BasicType.getType(type), dimensions);
  }

  /**
   * Convenience constructor for reference array type, e.g. Object[]
   *
   * @param class_name complete name of class (java.lang.String, e.g.)
   */
  public ArrayType(String class_name, int dimensions) {
    this(new ObjectType(class_name), dimensions);
  }

  /**
   * Constructor for array of given type
   *
   * @param type type of array (may be an array itself)
   */
  public ArrayType(Type type, int dimensions) {
    super(Constants.T_ARRAY, "<dummy>");

    if((dimensions < 1) || (dimensions > Constants.MAX_BYTE))
      throw new ClassGenException("Invalid number of dimensions: " + dimensions);

    switch(type.getType()) {
    case Constants.T_ARRAY:
      ArrayType array = (ArrayType)type;
      this.dimensions = dimensions + array.dimensions;
      basic_type      = array.basic_type;
      break;

    case Constants.T_VOID:
      throw new ClassGenException("Invalid type: void[]");

    default: // Basic type or reference
      this.dimensions = dimensions;
      basic_type = type;
      break;
    }

    StringBuffer buf = new StringBuffer();
    for(int i=0; i < this.dimensions; i++)
      buf.append('[');

    buf.append(basic_type.getSignature());

    signature = buf.toString();
  }

  /**
   * @return basic type of array, i.e., for int[][][] the basic type is int
   */
  public Type getBasicType() {
    return basic_type;
  }

  /**
   * @return element type of array, i.e., for int[][][] the element type is int[][]
   */
  public Type getElementType() {
    if(dimensions == 1)
      return basic_type;
    else
      return new ArrayType(basic_type, dimensions - 1);
  }

  /** @return number of dimensions of array
   */
  public int getDimensions() { return dimensions; }

  /** @return a hash code value for the object.
   */
  public int hashCode() { return basic_type.hashCode() ^ dimensions; }

  /** @return true if both type objects refer to the same array type.
   */
  public boolean equals(Object type) {
    if(type instanceof ArrayType) {
      ArrayType array = (ArrayType)type;
      return (array.dimensions == dimensions) && array.basic_type.equals(basic_type);
    } else
      return false;
  }
}
