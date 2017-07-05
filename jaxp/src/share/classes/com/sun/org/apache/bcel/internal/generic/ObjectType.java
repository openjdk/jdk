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
