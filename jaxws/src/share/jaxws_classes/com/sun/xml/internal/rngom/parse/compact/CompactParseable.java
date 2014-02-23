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
package com.sun.xml.internal.rngom.parse.compact;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.net.URL;

import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.IncludedGrammar;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.builder.Scope;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.Parseable;
import com.sun.xml.internal.rngom.xml.util.EncodingMap;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;

/**
 * RELAX NG schema in the compact syntax.
 */
public class CompactParseable implements Parseable {
  private final InputSource in;
  private final ErrorHandler eh;

  public CompactParseable(InputSource in, ErrorHandler eh) {
    this.in = in;
    this.eh = eh;
  }

  public ParsedPattern parse(SchemaBuilder sb) throws BuildException, IllegalSchemaException {
      ParsedPattern p = new CompactSyntax(this, makeReader(in), in.getSystemId(), sb, eh, "").parse(null);
      return sb.expandPattern(p);
  }

  public ParsedPattern parseInclude(String uri, SchemaBuilder sb, IncludedGrammar g, String inheritedNs)
          throws BuildException, IllegalSchemaException {
    InputSource tem = new InputSource(uri);
    tem.setEncoding(in.getEncoding());
    return new CompactSyntax(this, makeReader(tem), uri, sb, eh, inheritedNs).parseInclude(g);
  }

  public ParsedPattern parseExternal(String uri, SchemaBuilder sb, Scope scope, String inheritedNs)
          throws BuildException, IllegalSchemaException {
    InputSource tem = new InputSource(uri);
    tem.setEncoding(in.getEncoding());
    return new CompactSyntax(this, makeReader(tem), uri, sb, eh, inheritedNs).parse(scope);
  }

  private static final String UTF8 = EncodingMap.getJavaName("UTF-8");
  private static final String UTF16 = EncodingMap.getJavaName("UTF-16");

  private static Reader makeReader(InputSource is) throws BuildException {
    try {
      Reader r = is.getCharacterStream();
      if (r == null) {
        InputStream in = is.getByteStream();
        if (in == null) {
          String systemId = is.getSystemId();
          in = new URL(systemId).openStream();
        }
        String encoding = is.getEncoding();
        if (encoding == null) {
          PushbackInputStream pb = new PushbackInputStream(in, 2);
          encoding = detectEncoding(pb);
          in = pb;
        }
        r = new InputStreamReader(in, encoding);
      }
      return r;
    }
    catch (IOException e) {
      throw new BuildException(e);
    }
  }

  static private String detectEncoding(PushbackInputStream in) throws IOException {
    String encoding = UTF8;
    int b1 = in.read();
    if (b1 != -1) {
      int b2 = in.read();
      if (b2 != -1) {
        in.unread(b2);
        if ((b1 == 0xFF && b2 == 0xFE) || (b1 == 0xFE && b2 == 0xFF))
          encoding = UTF16;
      }
      in.unread(b1);
    }
    return encoding;
  }
}
