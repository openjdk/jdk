/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.rngom.binary;

import org.xml.sax.SAXException;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

public abstract class BinaryPattern extends Pattern {
  protected final Pattern p1;
  protected final Pattern p2;

  BinaryPattern(boolean nullable, int hc, Pattern p1, Pattern p2) {
    super(nullable, Math.max(p1.getContentType(), p2.getContentType()), hc);
    this.p1 = p1;
    this.p2 = p2;
  }

  void checkRecursion(int depth) throws SAXException {
    p1.checkRecursion(depth);
    p2.checkRecursion(depth);
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha)
    throws RestrictionViolationException {
    p1.checkRestrictions(context, dad, alpha);
    p2.checkRestrictions(context, dad, alpha);
  }

  boolean samePattern(Pattern other) {
    if (getClass() != other.getClass())
      return false;
    BinaryPattern b = (BinaryPattern)other;
    return p1 == b.p1 && p2 == b.p2;
  }

  public final Pattern getOperand1() {
    return p1;
  }

  public final Pattern getOperand2() {
    return p2;
  }

  /**
   * Adds all the children of this pattern to the given collection.
   *
   * <p>
   * For example, if this pattern is (A|B|C), it adds A, B, and C
   * to the collection, even though internally it's represented
   * as (A|(B|C)).
   */
  public final void fillChildren( Collection col ) {
    fillChildren(getClass(),p1,col);
    fillChildren(getClass(),p2,col);
  }

  /**
   * Same as {@link #fillChildren(Collection)} but returns an array.
   */
  public final Pattern[] getChildren() {
      List lst = new ArrayList();
      fillChildren(lst);
      return (Pattern[]) lst.toArray(new Pattern[lst.size()]);
  }

  private void fillChildren( Class c, Pattern p, Collection col ) {
    if(p.getClass()==c) {
      BinaryPattern bp = (BinaryPattern)p;
      bp.fillChildren(c,bp.p1,col);
      bp.fillChildren(c,bp.p2,col);
    } else {
      col.add(p);
    }
  }
}
