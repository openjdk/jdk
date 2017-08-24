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
import com.sun.org.apache.bcel.internal.Repository;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;

/**
 * Denotes reference such as java.lang.String.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class ObjectType extends ReferenceType {
  private String class_name; // Class name of type

  /**
   * @param class_name fully qualified class name, e.g. java.lang.String
   */
  public ObjectType(String class_name) {
    super(Constants.T_REFERENCE, "L" + class_name.replace('.', '/') + ";");
    this.class_name = class_name.replace('/', '.');
  }

  /** @return name of referenced class
   */
  public String getClassName() { return class_name; }

  /** @return a hash code value for the object.
   */
  public int hashCode()  { return class_name.hashCode(); }

  /** @return true if both type objects refer to the same class.
   */
  public boolean equals(Object type) {
    return (type instanceof ObjectType)?
      ((ObjectType)type).class_name.equals(class_name) : false;
  }

  /**
   * If "this" doesn't reference a class, it references an interface
   * or a non-existant entity.
   */
  public boolean referencesClass(){
    JavaClass jc = Repository.lookupClass(class_name);
    if (jc == null)
      return false;
    else
      return jc.isClass();
  }

  /**
   * If "this" doesn't reference an interface, it references a class
   * or a non-existant entity.
   */
  public boolean referencesInterface(){
    JavaClass jc = Repository.lookupClass(class_name);
    if (jc == null)
      return false;
    else
      return !jc.isClass();
  }

  public boolean subclassOf(ObjectType superclass){
    if (this.referencesInterface() || superclass.referencesInterface())
      return false;

    return Repository.instanceOf(this.class_name, superclass.class_name);
  }

  /**
   * Java Virtual Machine Specification edition 2, 5.4.4 Access Control
   */
  public boolean accessibleTo(ObjectType accessor) {
    JavaClass jc = Repository.lookupClass(class_name);

    if(jc.isPublic()) {
      return true;
    } else {
      JavaClass acc = Repository.lookupClass(accessor.class_name);
      return acc.getPackageName().equals(jc.getPackageName());
    }
  }
}
