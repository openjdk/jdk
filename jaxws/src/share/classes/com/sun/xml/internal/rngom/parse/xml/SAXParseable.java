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
package com.sun.xml.internal.rngom.parse.xml;

import java.io.IOException;

import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.IncludedGrammar;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.builder.Scope;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.Parseable;
import com.sun.xml.internal.rngom.xml.sax.JAXPXMLReaderCreator;
import com.sun.xml.internal.rngom.xml.sax.XMLReaderCreator;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * RELAX NG schema in the XML syntax.
 *
 */
public class SAXParseable implements Parseable {
  private final InputSource in;

  final XMLReaderCreator xrc;
  final ErrorHandler eh;

  public SAXParseable(InputSource in, ErrorHandler eh, XMLReaderCreator xrc) {
      this.xrc = xrc;
      this.eh = eh;
    this.in = in;
  }

  public SAXParseable(InputSource in, ErrorHandler eh) {
      this(in,eh,new JAXPXMLReaderCreator());
  }

  public ParsedPattern parse(SchemaBuilder schemaBuilder) throws BuildException, IllegalSchemaException {
    try {
      XMLReader xr = xrc.createXMLReader();
      SchemaParser sp = new SchemaParser(this, xr, eh, schemaBuilder, null, null,"");
      xr.parse(in);
      ParsedPattern p = sp.getParsedPattern();
      return schemaBuilder.expandPattern(p);
    }
    catch (SAXException e) {
      throw toBuildException(e);
    }
    catch (IOException e) {
      throw new BuildException(e);
    }
  }

      public ParsedPattern parseInclude(String uri, SchemaBuilder schemaBuilder, IncludedGrammar g, String inheritedNs)
              throws BuildException, IllegalSchemaException {
        try {
          XMLReader xr = xrc.createXMLReader();
          SchemaParser sp = new SchemaParser(this, xr, eh, schemaBuilder, g, g, inheritedNs);
          xr.parse(makeInputSource(xr, uri));
          return sp.getParsedPattern();
        }
        catch (SAXException e) {
         throw SAXParseable.toBuildException(e);
        }
        catch (IOException e) {
         throw new BuildException(e);
        }
      }

      public ParsedPattern parseExternal(String uri, SchemaBuilder schemaBuilder, Scope s, String inheritedNs)
              throws BuildException, IllegalSchemaException {
        try {
          XMLReader xr = xrc.createXMLReader();
          SchemaParser sp = new SchemaParser(this, xr, eh, schemaBuilder, null, s, inheritedNs);
          xr.parse(makeInputSource(xr, uri));
          return sp.getParsedPattern();
        }
        catch (SAXException e) {
          throw SAXParseable.toBuildException(e);
        }
        catch (IOException e) {
          throw new BuildException(e);
        }
      }

      private static InputSource makeInputSource(XMLReader xr, String systemId) throws IOException, SAXException {
        EntityResolver er = xr.getEntityResolver();
        if (er != null) {
          InputSource inputSource = er.resolveEntity(null, systemId);
          if (inputSource != null)
        return inputSource;
        }
        return new InputSource(systemId);
      }

      static BuildException toBuildException(SAXException e) {
        Exception inner = e.getException();
        if (inner instanceof BuildException)
          throw (BuildException)inner;
        throw new BuildException(e);
      }
    }
