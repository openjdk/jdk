/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (C) 2004-2011
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.binary;

import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.binary.visitor.PatternFunction;
import com.sun.xml.internal.rngom.binary.visitor.PatternVisitor;
import org.xml.sax.SAXException;

public abstract class Pattern implements ParsedPattern {
  private boolean nullable;
  private int hc;
  private int contentType;

  static final int TEXT_HASH_CODE = 1;
  static final int ERROR_HASH_CODE = 3;
  static final int EMPTY_HASH_CODE = 5;
  static final int NOT_ALLOWED_HASH_CODE = 7;
  static final int CHOICE_HASH_CODE = 11;
  static final int GROUP_HASH_CODE = 13;
  static final int INTERLEAVE_HASH_CODE = 17;
  static final int ONE_OR_MORE_HASH_CODE = 19;
  static final int ELEMENT_HASH_CODE = 23;
  static final int VALUE_HASH_CODE = 27;
  static final int ATTRIBUTE_HASH_CODE = 29;
  static final int DATA_HASH_CODE = 31;
  static final int LIST_HASH_CODE = 37;
  static final int AFTER_HASH_CODE = 41;

  static int combineHashCode(int hc1, int hc2, int hc3) {
    return hc1 * hc2 * hc3;
  }

  static int combineHashCode(int hc1, int hc2) {
    return hc1 * hc2;
  }

  static final int EMPTY_CONTENT_TYPE = 0;
  static final int ELEMENT_CONTENT_TYPE = 1;
  static final int MIXED_CONTENT_TYPE = 2;
  static final int DATA_CONTENT_TYPE = 3;

  Pattern(boolean nullable, int contentType, int hc) {
    this.nullable = nullable;
    this.contentType = contentType;
    this.hc = hc;
  }

  Pattern() {
    this.nullable = false;
    this.hc = hashCode();
    this.contentType = EMPTY_CONTENT_TYPE;
  }

  void checkRecursion(int depth) throws SAXException { }

  Pattern expand(SchemaPatternBuilder b) {
    return this;
  }

  /**
   * Returns true if the pattern is nullable.
   *
   * <p>
   * A pattern is nullable when it can match the empty sequence.
   */
  public final boolean isNullable() {
    return nullable;
  }

  boolean isNotAllowed() {
    return false;
  }

  static final int START_CONTEXT = 0;
  static final int ELEMENT_CONTEXT = 1;
  static final int ELEMENT_REPEAT_CONTEXT = 2;
  static final int ELEMENT_REPEAT_GROUP_CONTEXT = 3;
  static final int ELEMENT_REPEAT_INTERLEAVE_CONTEXT = 4;
  static final int ATTRIBUTE_CONTEXT = 5;
  static final int LIST_CONTEXT = 6;
  static final int DATA_EXCEPT_CONTEXT = 7;

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha)
    throws RestrictionViolationException {
  }

  // Know that other is not null
  abstract boolean samePattern(Pattern other);

  final int patternHashCode() {
    return hc;
  }

  final int getContentType() {
    return contentType;
  }

  boolean containsChoice(Pattern p) {
    return this == p;
  }

  public abstract void accept(PatternVisitor visitor);
  public abstract Object apply(PatternFunction f);

//  DPattern applyForPattern(PatternFunction f) {
//    return (DPattern)apply(f);
//  }

  static boolean contentTypeGroupable(int ct1, int ct2) {
    if (ct1 == EMPTY_CONTENT_TYPE || ct2 == EMPTY_CONTENT_TYPE)
      return true;
    if (ct1 == DATA_CONTENT_TYPE || ct2 == DATA_CONTENT_TYPE)
      return false;
    return true;
  }

}
