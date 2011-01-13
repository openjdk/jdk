/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * COMPONENT_NAME: idl.parser
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.constExpr;

// NOTES:

import java.math.BigInteger;

public abstract class Expression
{
  /**
   * Compute the value of this expression.
   **/
  public abstract Object evaluate () throws EvaluationException;

  /**
   * Set the value of this expression.
   **/
  public void value (Object value)
  {
    _value = value;
  }
  /**
   * Get the value of this expression.
   **/
  public Object value ()
  {
    return _value;
  }

  /**
   * Set the representation of this expression.
   **/
  public void rep (String rep)
  {
    _rep = rep;
  }
  /**
   * Get the representation of this expression.
   **/
  public String rep ()
  {
    return _rep;
  }

  /**
   * Set the target type of this expression.
   **/
  public void type (String type)
  {
    _type = type;
  }
  /**
   * Get the target type of this expression.
   **/
  public String type ()
  {
    return _type;
  }

  /**
   * Return the default computation type for the given target type.
   **/
  protected static String defaultType (String targetType)
  {
    return (targetType == null) ? new String ("") : targetType;
  } // defaultType

  // BigInteger is a multi-precision number whose representation contains
  // a signum (sign-number = 1, -1) and a magnitude.  To support "long long",
  // all integer expressions are now performed over BigInteger and stored as
  // such.  During the evaluation of an integer expression, the signum of its
  // value may toggle, which may cause the value of an expression to conflict
  // with its target type: [Case 1] If the resulting value is negative
  // (signum=-1) and the target type is unsigned; or [Case 2] if the resulting
  // value is positive (signum=1) and greater than 2**(target-type-length - 1),
  // and the target type is signed, then the resulting value will be out of
  // range.  However, this value is correct and must be coerced to the target
  // type.  E.G., After appying "not" to a BigInteger, the result is
  // a BigInteger that represents its 2's-complement (~5 => -6 in a byte-space).
  // In this example, the signum toggles and the magnatude is 6.  If the target
  // type of this value were unsigned short, it must be coerced to a positive
  // number whose bits truly represent -6 in 2's-complement (250 in a byte-space).
  //
  // Also, floating types may now be intialized with any integer expression.
  // The result must be coerced to Double.
  //
  // Use the following routines to coerce this expression's value to its
  // "target" type.

  /**
   * Coerces a number to the target type of this expression.
   * @param  obj  The number to coerce.
   * @return  the value of number coerced to the (target) type of
   *  this expression.
   **/
  public Object coerceToTarget (Object obj)
  {
    if (obj instanceof BigInteger)
    {
      if (type ().indexOf ("unsigned") >= 0)
        return toUnsignedTarget ((BigInteger)obj);
      else
        return toSignedTarget ((BigInteger)obj);
    }
    return obj;
  } // coerceToTarget

  /**
   * Coerces an integral value (BigInteger) to its corresponding unsigned
   * representation, if the target type of this expression is unsigned.
   * @param b The BigInteger to be coerced.
   * @return the value of an integral type coerced to its corresponding
   *  unsigned integral type, if the target type of this expression is
   *  unsigned.
   **/
  protected BigInteger toUnsignedTarget (BigInteger b)
  {
    if (type ().equals ("unsigned short")) // target type of this expression
    {
      if (b != null && b.compareTo (zero) < 0) // error if value < min = -(2**(l-1)).
        return b.add (twoPow16);
    }
    else if (type ().equals ("unsigned long"))
    {
      if (b != null && b.compareTo (zero) < 0)
        return b.add (twoPow32);
    }
    else if (type ().equals ("unsigned long long"))
    {
      if (b != null && b.compareTo (zero) < 0)
        return b.add (twoPow64);
    }
    return b;
  } // toUnsignedTarget

  /**
   * Coerces an integral value (BigInteger) to its corresponding signed
   * representation, if the target type of this expression is signed.
   * @param  b  The BigInteger to be coerced.
   * @return  the value of an integral type coerced to its corresponding
   *  signed integral type, if the target type of this expression is
   *  signed.
   **/
  protected BigInteger toSignedTarget (BigInteger b)
  {
    if (type ().equals ("short"))
    {
      if (b != null && b.compareTo (sMax) > 0)
        return b.subtract (twoPow16);
    }
    else if (type ().equals ("long"))
    {
      if (b != null && b.compareTo (lMax) > 0)
        return b.subtract (twoPow32);
    }
    else if (type ().equals ("long long"))
    {
      if (b != null && b.compareTo (llMax) > 0)
        return b.subtract (twoPow64);
    }
    return b;
  } // toSignedTarget

  /**
   * Return the unsigned value of a BigInteger.
   **/
  protected BigInteger toUnsigned (BigInteger b)
  {
    if (b != null && b.signum () == -1)
      if (type ().equals ("short"))
        return b.add (twoPow16);
      else if (type ().equals ("long"))
        return b.add (twoPow32);
      else if (type ().equals ("long long"))
        return b.add (twoPow64);
    return b;
  }

  // Integral-type boundaries.

  public static final BigInteger negOne = BigInteger.valueOf (-1);
  public static final BigInteger zero   = BigInteger.valueOf (0);
  public static final BigInteger one    = BigInteger.valueOf (1);
  public static final BigInteger two    = BigInteger.valueOf (2);

  public static final BigInteger twoPow15 = two.pow (15);
  public static final BigInteger twoPow16 = two.pow (16);
  public static final BigInteger twoPow31 = two.pow (31);
  public static final BigInteger twoPow32 = two.pow (32);
  public static final BigInteger twoPow63 = two.pow (63);
  public static final BigInteger twoPow64 = two.pow (64);

  public static final BigInteger sMax = BigInteger.valueOf (Short.MAX_VALUE);
  public static final BigInteger sMin = BigInteger.valueOf (Short.MAX_VALUE);

  public static final BigInteger usMax = sMax.multiply (two).add (one);
  public static final BigInteger usMin = zero;

  public static final BigInteger lMax = BigInteger.valueOf (Integer.MAX_VALUE);
  public static final BigInteger lMin = BigInteger.valueOf (Integer.MAX_VALUE);

  public static final BigInteger ulMax = lMax.multiply (two).add (one);
  public static final BigInteger ulMin = zero;

  public static final BigInteger llMax = BigInteger.valueOf (Long.MAX_VALUE);
  public static final BigInteger llMin = BigInteger.valueOf (Long.MIN_VALUE);

  public static final BigInteger ullMax = llMax.multiply (two).add (one);
  public static final BigInteger ullMin = zero;

  /**
   * Value of this expression: Boolean, Char, Byte, BigInteger, Double,
   * String, Expression, ConstEntry.
   **/
  private Object _value = null;
  /**
   * String representation of this expression.
   **/
  private String _rep   = null;
  /**
   * Computation type of this (sub)expression = Target type for now.
   **/
  private String _type  = null;
} // abstract class Expression
