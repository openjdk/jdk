/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.rngom.nc.NameClass;
import org.relaxng.datatype.Datatype;
import org.xml.sax.Locator;

public class SchemaPatternBuilder extends PatternBuilder {
  private boolean idTypes;
  private final Pattern unexpandedNotAllowed =
        new NotAllowedPattern() {
        @Override
        boolean isNotAllowed() {
            return false;
        }
        @Override
        Pattern expand(SchemaPatternBuilder b) {
            return b.makeNotAllowed();
        }
    };

  private final TextPattern text = new TextPattern();
  private final PatternInterner schemaInterner = new PatternInterner();

  public SchemaPatternBuilder() { }

  public boolean hasIdTypes() {
    return idTypes;
  }

  Pattern makeElement(NameClass nameClass, Pattern content, Locator loc) {
    Pattern p = new ElementPattern(nameClass, content, loc);
    return schemaInterner.intern(p);
  }

  Pattern makeAttribute(NameClass nameClass, Pattern value, Locator loc) {
    if (value == notAllowed)
      return value;
    Pattern p = new AttributePattern(nameClass, value, loc);
    return schemaInterner.intern(p);
  }

  Pattern makeData(Datatype dt) {
    noteDatatype(dt);
    Pattern p = new DataPattern(dt);
    return schemaInterner.intern(p);
  }

  Pattern makeDataExcept(Datatype dt, Pattern except, Locator loc) {
    noteDatatype(dt);
    Pattern p = new DataExceptPattern(dt, except, loc);
    return schemaInterner.intern(p);
  }

  Pattern makeValue(Datatype dt, Object obj) {
    noteDatatype(dt);
    Pattern p = new ValuePattern(dt, obj);
    return schemaInterner.intern(p);
  }

  Pattern makeText() {
    return text;
  }

    @Override
  Pattern makeOneOrMore(Pattern p) {
    if (p == text)
      return p;
    return super.makeOneOrMore(p);
  }

  Pattern makeUnexpandedNotAllowed() {
    return unexpandedNotAllowed;
  }

  Pattern makeError() {
    Pattern p = new ErrorPattern();
    return schemaInterner.intern(p);
  }

    @Override
  Pattern makeChoice(Pattern p1, Pattern p2) {
    if (p1 == notAllowed || p1 == p2)
      return p2;
    if (p2 == notAllowed)
      return p1;
    return super.makeChoice(p1, p2);
  }

  Pattern makeList(Pattern p, Locator loc) {
    if (p == notAllowed)
      return p;
    Pattern p1 = new ListPattern(p, loc);
    return schemaInterner.intern(p1);
  }

  Pattern makeMixed(Pattern p) {
    return makeInterleave(text, p);
  }

  private void noteDatatype(Datatype dt) {
    if (dt.getIdType() != Datatype.ID_TYPE_NULL)
      idTypes = true;
  }
}
