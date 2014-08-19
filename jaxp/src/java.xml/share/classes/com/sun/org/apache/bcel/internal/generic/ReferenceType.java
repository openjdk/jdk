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
 * Super class for object and array types.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class ReferenceType extends Type {
  protected ReferenceType(byte t, String s) {
    super(t, s);
  }

  /** Class is non-abstract but not instantiable from the outside
   */
  ReferenceType() {
    super(Constants.T_OBJECT, "<null object>");
  }

  /**
   * Return true iff this type is castable to another type t as defined in
   * the JVM specification.  The case where this is Type.NULL is not
   * defined (see the CHECKCAST definition in the JVM specification).
   * However, because e.g. CHECKCAST doesn't throw a
   * ClassCastException when casting a null reference to any Object,
   * true is returned in this case.
   */
  public boolean isCastableTo(Type t) {
    if (this.equals(Type.NULL))
      return true;              // If this is ever changed in isAssignmentCompatible()

    return isAssignmentCompatibleWith(t);
    /* Yes, it's true: It's the same definition.
     * See vmspec2 AASTORE / CHECKCAST definitions.
     */
  }

  /**
   * Return true iff this is assignment compatible with another type t
   * as defined in the JVM specification; see the AASTORE definition
   * there.
   */
  public boolean isAssignmentCompatibleWith(Type t) {
    if (!(t instanceof ReferenceType))
      return false;

    ReferenceType T = (ReferenceType) t;

    if (this.equals(Type.NULL))
      return true; // This is not explicitely stated, but clear. Isn't it?

    /* If this is a class type then
     */
    if ((this instanceof ObjectType) && (((ObjectType) this).referencesClass())) {
      /* If T is a class type, then this must be the same class as T,
         or this must be a subclass of T;
      */
      if ((T instanceof ObjectType) && (((ObjectType) T).referencesClass())) {
        if (this.equals(T))
          return true;

        if (Repository.instanceOf(((ObjectType) this).getClassName(),
                                  ((ObjectType) T).getClassName()))
          return true;
      }

      /* If T is an interface type, this must implement interface T.
       */
      if ((T instanceof ObjectType) && (((ObjectType) T).referencesInterface())) {
        if (Repository.implementationOf(((ObjectType) this).getClassName(),
                                        ((ObjectType) T).getClassName()))
          return true;
      }
    }

    /* If this is an interface type, then:
     */
    if ((this instanceof ObjectType) && (((ObjectType) this).referencesInterface())) {
      /* If T is a class type, then T must be Object (2.4.7).
       */
      if ((T instanceof ObjectType) && (((ObjectType) T).referencesClass())) {
        if (T.equals(Type.OBJECT)) return true;
      }

      /* If T is an interface type, then T must be the same interface
       * as this or a superinterface of this (2.13.2).
       */
      if ((T instanceof ObjectType) && (((ObjectType) T).referencesInterface())) {
        if (this.equals(T)) return true;
        if (Repository.implementationOf(((ObjectType) this).getClassName(),
                                        ((ObjectType) T).getClassName()))
          return true;
      }
    }

    /* If this is an array type, namely, the type SC[], that is, an
     * array of components of type SC, then:
     */
    if (this instanceof ArrayType) {
      /* If T is a class type, then T must be Object (2.4.7).
       */
      if ((T instanceof ObjectType) && (((ObjectType) T).referencesClass())) {
        if (T.equals(Type.OBJECT)) return true;
      }

      /* If T is an array type TC[], that is, an array of components
       * of type TC, then one of the following must be true:
       */
      if (T instanceof ArrayType) {
        /* TC and SC are the same primitive type (2.4.1).
         */
        Type sc = ((ArrayType) this).getElementType();
        Type tc = ((ArrayType) this).getElementType();

        if (sc instanceof BasicType && tc instanceof BasicType && sc.equals(tc))
          return true;

        /* TC and SC are reference types (2.4.6), and type SC is
         * assignable to TC by these runtime rules.
         */
        if (tc instanceof ReferenceType && sc instanceof ReferenceType &&
            ((ReferenceType) sc).isAssignmentCompatibleWith((ReferenceType) tc))
          return true;
      }

      /* If T is an interface type, T must be one of the interfaces implemented by arrays (2.15). */
      // TODO: Check if this is still valid or find a way to dynamically find out which
      // interfaces arrays implement. However, as of the JVM specification edition 2, there
      // are at least two different pages where assignment compatibility is defined and
      // on one of them "interfaces implemented by arrays" is exchanged with "'Cloneable' or
      // 'java.io.Serializable'"
      if ((T instanceof ObjectType) && (((ObjectType) T).referencesInterface())) {
        for (int ii = 0; ii < Constants.INTERFACES_IMPLEMENTED_BY_ARRAYS.length; ii++) {
          if (T.equals(new ObjectType(Constants.INTERFACES_IMPLEMENTED_BY_ARRAYS[ii]))) return true;
        }
      }
    }
    return false; // default.
  }

  /**
   * This commutative operation returns the first common superclass (narrowest ReferenceType
   * referencing a class, not an interface).
   * If one of the types is a superclass of the other, the former is returned.
   * If "this" is Type.NULL, then t is returned.
   * If t is Type.NULL, then "this" is returned.
   * If "this" equals t ['this.equals(t)'] "this" is returned.
   * If "this" or t is an ArrayType, then Type.OBJECT is returned;
   * unless their dimensions match. Then an ArrayType of the same
   * number of dimensions is returned, with its basic type being the
   * first common super class of the basic types of "this" and t.
   * If "this" or t is a ReferenceType referencing an interface, then Type.OBJECT is returned.
   * If not all of the two classes' superclasses cannot be found, "null" is returned.
   * See the JVM specification edition 2, "4.9.2 The Bytecode Verifier".
   */
  public ReferenceType getFirstCommonSuperclass(ReferenceType t) {
    if (this.equals(Type.NULL)) return t;
    if (t.equals(Type.NULL)) return this;
    if (this.equals(t)) return this;
    /*
     * TODO: Above sounds a little arbitrary. On the other hand, there is
     * no object referenced by Type.NULL so we can also say all the objects
     * referenced by Type.NULL were derived from java.lang.Object.
     * However, the Java Language's "instanceof" operator proves us wrong:
     * "null" is not referring to an instance of java.lang.Object :)
     */

    /* This code is from a bug report by Konstantin Shagin <konst@cs.technion.ac.il> */

    if ((this instanceof ArrayType) && (t instanceof ArrayType)) {
      ArrayType arrType1 = (ArrayType) this;
      ArrayType arrType2 = (ArrayType) t;
      if (
          (arrType1.getDimensions() == arrType2.getDimensions()) &&
          arrType1.getBasicType() instanceof ObjectType &&
          arrType2.getBasicType() instanceof ObjectType) {
        return new ArrayType(
                             ((ObjectType) arrType1.getBasicType()).getFirstCommonSuperclass((ObjectType) arrType2.getBasicType()),
                             arrType1.getDimensions()
                             );

      }
    }

    if ((this instanceof ArrayType) || (t instanceof ArrayType))
      return Type.OBJECT;
    // TODO: Is there a proof of OBJECT being the direct ancestor of every ArrayType?

    if (((this instanceof ObjectType) && ((ObjectType) this).referencesInterface()) ||
        ((t instanceof ObjectType) && ((ObjectType) t).referencesInterface()))
      return Type.OBJECT;
    // TODO: The above line is correct comparing to the vmspec2. But one could
    // make class file verification a bit stronger here by using the notion of
    // superinterfaces or even castability or assignment compatibility.


    // this and t are ObjectTypes, see above.
    ObjectType thiz = (ObjectType) this;
    ObjectType other = (ObjectType) t;
    JavaClass[] thiz_sups = Repository.getSuperClasses(thiz.getClassName());
    JavaClass[] other_sups = Repository.getSuperClasses(other.getClassName());

    if ((thiz_sups == null) || (other_sups == null)) {
      return null;
    }

    // Waaahh...
    JavaClass[] this_sups = new JavaClass[thiz_sups.length + 1];
    JavaClass[] t_sups = new JavaClass[other_sups.length + 1];
    System.arraycopy(thiz_sups, 0, this_sups, 1, thiz_sups.length);
    System.arraycopy(other_sups, 0, t_sups, 1, other_sups.length);
    this_sups[0] = Repository.lookupClass(thiz.getClassName());
    t_sups[0] = Repository.lookupClass(other.getClassName());

    for (int i = 0; i < t_sups.length; i++) {
      for (int j = 0; j < this_sups.length; j++) {
        if (this_sups[j].equals(t_sups[i])) return new ObjectType(this_sups[j].getClassName());
      }
    }

    // Huh? Did you ask for Type.OBJECT's superclass??
    return null;
  }

  /**
   * This commutative operation returns the first common superclass (narrowest ReferenceType
   * referencing a class, not an interface).
   * If one of the types is a superclass of the other, the former is returned.
   * If "this" is Type.NULL, then t is returned.
   * If t is Type.NULL, then "this" is returned.
   * If "this" equals t ['this.equals(t)'] "this" is returned.
   * If "this" or t is an ArrayType, then Type.OBJECT is returned.
   * If "this" or t is a ReferenceType referencing an interface, then Type.OBJECT is returned.
   * If not all of the two classes' superclasses cannot be found, "null" is returned.
   * See the JVM specification edition 2, "4.9.2 The Bytecode Verifier".
   *
   * @deprecated use getFirstCommonSuperclass(ReferenceType t) which has
   *             slightly changed semantics.
   */
  public ReferenceType firstCommonSuperclass(ReferenceType t) {
    if (this.equals(Type.NULL)) return t;
    if (t.equals(Type.NULL)) return this;
    if (this.equals(t)) return this;
    /*
     * TODO: Above sounds a little arbitrary. On the other hand, there is
     * no object referenced by Type.NULL so we can also say all the objects
     * referenced by Type.NULL were derived from java.lang.Object.
     * However, the Java Language's "instanceof" operator proves us wrong:
     * "null" is not referring to an instance of java.lang.Object :)
     */

    if ((this instanceof ArrayType) || (t instanceof ArrayType))
      return Type.OBJECT;
    // TODO: Is there a proof of OBJECT being the direct ancestor of every ArrayType?

    if (((this instanceof ObjectType) && ((ObjectType) this).referencesInterface()) ||
        ((t instanceof ObjectType) && ((ObjectType) t).referencesInterface()))
      return Type.OBJECT;
    // TODO: The above line is correct comparing to the vmspec2. But one could
    // make class file verification a bit stronger here by using the notion of
    // superinterfaces or even castability or assignment compatibility.


    // this and t are ObjectTypes, see above.
    ObjectType thiz = (ObjectType) this;
    ObjectType other = (ObjectType) t;
    JavaClass[] thiz_sups = Repository.getSuperClasses(thiz.getClassName());
    JavaClass[] other_sups = Repository.getSuperClasses(other.getClassName());

    if ((thiz_sups == null) || (other_sups == null)) {
      return null;
    }

    // Waaahh...
    JavaClass[] this_sups = new JavaClass[thiz_sups.length + 1];
    JavaClass[] t_sups = new JavaClass[other_sups.length + 1];
    System.arraycopy(thiz_sups, 0, this_sups, 1, thiz_sups.length);
    System.arraycopy(other_sups, 0, t_sups, 1, other_sups.length);
    this_sups[0] = Repository.lookupClass(thiz.getClassName());
    t_sups[0] = Repository.lookupClass(other.getClassName());

    for (int i = 0; i < t_sups.length; i++) {
      for (int j = 0; j < this_sups.length; j++) {
        if (this_sups[j].equals(t_sups[i])) return new ObjectType(this_sups[j].getClassName());
      }
    }

    // Huh? Did you ask for Type.OBJECT's superclass??
    return null;
  }
}
