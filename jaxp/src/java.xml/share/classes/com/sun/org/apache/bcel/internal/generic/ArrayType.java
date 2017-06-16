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
