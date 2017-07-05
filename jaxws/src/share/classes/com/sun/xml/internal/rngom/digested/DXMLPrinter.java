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
package com.sun.xml.internal.rngom.digested;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.util.CheckingSchemaBuilder;
import com.sun.xml.internal.rngom.nc.NameClass;
import com.sun.xml.internal.rngom.nc.NameClassVisitor;
import com.sun.xml.internal.rngom.nc.SimpleNameClass;
import com.sun.xml.internal.rngom.parse.Parseable;
import com.sun.xml.internal.rngom.parse.compact.CompactParseable;
import com.sun.xml.internal.rngom.parse.xml.SAXParseable;
import com.sun.xml.internal.rngom.xml.util.WellKnownNamespaces;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Printer of RELAX NG digested model to XML using StAX {@link XMLStreamWriter}.
 *
 * @author <A href="mailto:demakov@ispras.ru">Alexey Demakov</A>
 */
public class DXMLPrinter {
    protected XMLStreamWriter out;
    protected String indentStep = "\t";
    protected String newLine = System.getProperty("line.separator");
    protected int indent;
    protected boolean afterEnd = false;
    protected DXMLPrinterVisitor visitor;
    protected NameClassXMLPrinterVisitor ncVisitor;
    protected DOMPrinter domPrinter;

    /**
     * @param out Output stream.
     */
    public DXMLPrinter(XMLStreamWriter out) {
        this.out = out;
        this.visitor = new DXMLPrinterVisitor();
        this.ncVisitor = new NameClassXMLPrinterVisitor();
        this.domPrinter = new DOMPrinter(out);
    }

    /**
     * Prints grammar enclosed by start/end document.
     *
     * @param grammar
     * @throws XMLStreamException
     */
    public void printDocument(DGrammarPattern grammar) throws XMLStreamException {
        try {
            visitor.startDocument();
            visitor.on(grammar);
            visitor.endDocument();
        } catch (XMLWriterException e) {
            throw (XMLStreamException) e.getCause();
        }
    }

    /**
     * Prints XML fragment for the given pattern.
     *
     * @throws XMLStreamException
     */
    public void print(DPattern pattern) throws XMLStreamException {
        try {
            pattern.accept(visitor);
        } catch (XMLWriterException e) {
            throw (XMLStreamException) e.getCause();
        }
    }

    /**
     * Prints XML fragment for the given name class.
     *
     * @throws XMLStreamException
     */
    public void print(NameClass nc) throws XMLStreamException {
        try {
            nc.accept(ncVisitor);
        } catch (XMLWriterException e) {
            throw (XMLStreamException) e.getCause();
        }
    }

    public void print(Node node) throws XMLStreamException {
        domPrinter.print(node);
    }

    protected class XMLWriterException extends RuntimeException {
        protected XMLWriterException(Throwable cause) {
            super(cause);
        }
    }

    protected class XMLWriter {
        protected void newLine() {
            try {
                out.writeCharacters(newLine);
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        protected void indent() {
            try {
                for (int i = 0; i < indent; i++) {
                    out.writeCharacters(indentStep);
                }
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void startDocument() {
            try {
                out.writeStartDocument();
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void endDocument() {
            try {
                out.writeEndDocument();
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public final void start(String element) {
            try {
                newLine();
                indent();
                out.writeStartElement(element);
                indent++;
                afterEnd = false;
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void end() {
            try {
                indent--;
                if (afterEnd) {
                    newLine();
                    indent();
                }
                out.writeEndElement();
                afterEnd = true;
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void attr(String prefix, String ns, String name, String value) {
            try {
                out.writeAttribute(prefix, ns, name, value);
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void attr(String name, String value) {
            try {
                out.writeAttribute(name, value);
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void ns(String prefix, String uri) {
            try {
                out.writeNamespace(prefix, uri);
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }

        public void body(String text) {
            try {
                out.writeCharacters(text);
                afterEnd = false;
            } catch (XMLStreamException e) {
                throw new XMLWriterException(e);
            }
        }
    }

    protected class DXMLPrinterVisitor extends XMLWriter implements DPatternVisitor<Void> {
        protected void on(DPattern p) {
            p.accept(this);
        }

        protected void unwrapGroup(DPattern p) {
            if (p instanceof DGroupPattern && p.getAnnotation() == DAnnotation.EMPTY) {
                for (DPattern d : (DGroupPattern) p) {
                    on(d);
                }
            } else {
                on(p);
            }
        }

        protected void unwrapChoice(DPattern p) {
            if (p instanceof DChoicePattern && p.getAnnotation() == DAnnotation.EMPTY) {
                for (DPattern d : (DChoicePattern) p) {
                    on(d);
                }
            } else {
                on(p);
            }
        }

        protected void on(NameClass nc) {
            if (nc instanceof SimpleNameClass) {
                QName qname = ((SimpleNameClass) nc).name;
                String name = qname.getLocalPart();
                if (!qname.getPrefix().equals("")) name = qname.getPrefix() + ":";
                attr("name", name);
            } else {
                nc.accept(ncVisitor);
            }
        }

        protected void on(DAnnotation ann) {
            if (ann == DAnnotation.EMPTY) return;
            for (DAnnotation.Attribute attr : ann.getAttributes().values()) {
                attr(attr.getPrefix(), attr.getNs(), attr.getLocalName(), attr.getValue());
            }
            for (Element elem : ann.getChildren()) {
                try {
                    newLine();
                    indent();
                    print(elem);
                }
                catch (XMLStreamException e) {
                    throw new XMLWriterException(e);
                }
            }
        }

        public Void onAttribute(DAttributePattern p) {
            start("attribute");
            on(p.getName());
            on(p.getAnnotation());
            DPattern child = p.getChild();
            // do not print default value
            if (!(child instanceof DTextPattern)) {
                on(p.getChild());
            }
            end();
            return null;
        }

        public Void onChoice(DChoicePattern p) {
            start("choice");
            on(p.getAnnotation());
            for (DPattern d : p) {
                on(d);
            }
            end();
            return null;
        }

        public Void onData(DDataPattern p) {
            List<DDataPattern.Param> params = p.getParams();
            DPattern except = p.getExcept();
            start("data");
            attr("datatypeLibrary", p.getDatatypeLibrary());
            attr("type", p.getType());
            on(p.getAnnotation());
            for (DDataPattern.Param param : params) {
                start("param");
                attr("ns", param.getNs());
                attr("name", param.getName());
                body(param.getValue());
                end();
            }
            if (except != null) {
                start("except");
                unwrapChoice(except);
                end();
            }
            end();
            return null;
        }

        public Void onElement(DElementPattern p) {
            start("element");
            on(p.getName());
            on(p.getAnnotation());
            unwrapGroup(p.getChild());
            end();
            return null;
        }

        public Void onEmpty(DEmptyPattern p) {
            start("empty");
            on(p.getAnnotation());
            end();
            return null;
        }

        public Void onGrammar(DGrammarPattern p) {
            start("grammar");
            ns(null, WellKnownNamespaces.RELAX_NG);
            on(p.getAnnotation());
            start("start");
            on(p.getStart());
            end();
            for (DDefine d : p) {
                start("define");
                attr("name", d.getName());
                on(d.getAnnotation());
                unwrapGroup(d.getPattern());
                end();
            }
            end();
            return null;
        }

        public Void onGroup(DGroupPattern p) {
            start("group");
            on(p.getAnnotation());
            for (DPattern d : p) {
                on(d);
            }
            end();
            return null;
        }

        public Void onInterleave(DInterleavePattern p) {
            start("interleave");
            on(p.getAnnotation());
            for (DPattern d : p) {
                on(d);
            }
            end();
            return null;
        }

        public Void onList(DListPattern p) {
            start("list");
            on(p.getAnnotation());
            unwrapGroup(p.getChild());
            end();
            return null;
        }

        public Void onMixed(DMixedPattern p) {
            start("mixed");
            on(p.getAnnotation());
            unwrapGroup(p.getChild());
            end();
            return null;
        }

        public Void onNotAllowed(DNotAllowedPattern p) {
            start("notAllowed");
            on(p.getAnnotation());
            end();
            return null;
        }

        public Void onOneOrMore(DOneOrMorePattern p) {
            start("oneOrMore");
            on(p.getAnnotation());
            unwrapGroup(p.getChild());
            end();
            return null;
        }

        public Void onOptional(DOptionalPattern p) {
            start("optional");
            on(p.getAnnotation());
            unwrapGroup(p.getChild());
            end();
            return null;
        }

        public Void onRef(DRefPattern p) {
            start("ref");
            attr("name", p.getName());
            on(p.getAnnotation());
            end();
            return null;
        }

        public Void onText(DTextPattern p) {
            start("text");
            on(p.getAnnotation());
            end();
            return null;
        }

        public Void onValue(DValuePattern p) {
            start("value");
            if (!p.getNs().equals("")) attr("ns", p.getNs());
            attr("datatypeLibrary", p.getDatatypeLibrary());
            attr("type", p.getType());
            on(p.getAnnotation());
            body(p.getValue());
            end();
            return null;
        }

        public Void onZeroOrMore(DZeroOrMorePattern p) {
            start("zeroOrMore");
            on(p.getAnnotation());
            unwrapGroup(p.getChild());
            end();
            return null;
        }
    }

    protected class NameClassXMLPrinterVisitor extends XMLWriter implements NameClassVisitor<Void> {
        public Void visitChoice(NameClass nc1, NameClass nc2) {
            // TODO: flatten nested choices
            start("choice");
            nc1.accept(this);
            nc2.accept(this);
            end();
            return null;
        }

        public Void visitNsName(String ns) {
            start("nsName");
            attr("ns", ns);
            end();
            return null;
        }

        public Void visitNsNameExcept(String ns, NameClass nc) {
            start("nsName");
            attr("ns", ns);
            start("except");
            nc.accept(this);
            end();
            end();
            return null;
        }

        public Void visitAnyName() {
            start("anyName");
            end();
            return null;
        }

        public Void visitAnyNameExcept(NameClass nc) {
            start("anyName");
            start("except");
            nc.accept(this);
            end();
            end();
            return null;
        }

        public Void visitName(QName name) {
            start("name");
            if (!name.getPrefix().equals("")) {
                body(name.getPrefix() + ":");
            }
            body(name.getLocalPart());
            end();
            return null;
        }

        public Void visitNull() {
            throw new UnsupportedOperationException("visitNull");
        }
    }

    public static void main(String[] args) throws Exception {
        Parseable p;

        ErrorHandler eh = new DefaultHandler() {
            public void error(SAXParseException e) throws SAXException {
                throw e;
            }
        };

        // the error handler passed to Parseable will receive parsing errors.
        if (args[0].endsWith(".rng")) {
            p = new SAXParseable(new InputSource(args[0]), eh);
        } else {
            p = new CompactParseable(new InputSource(args[0]), eh);
        }

        // the error handler passed to CheckingSchemaBuilder will receive additional
        // errors found during the RELAX NG restrictions check.
        // typically you'd want to pass in the same error handler,
        // as there's really no distinction between those two kinds of errors.
        SchemaBuilder sb = new CheckingSchemaBuilder(new DSchemaBuilderImpl(), eh);
        try {
            // run the parser
            DGrammarPattern grammar = (DGrammarPattern) p.parse(sb);
            OutputStream out = new FileOutputStream(args[1]);
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter output = factory.createXMLStreamWriter(out);
            DXMLPrinter printer = new DXMLPrinter(output);
            printer.printDocument(grammar);
            output.close();
            out.close();
        } catch (BuildException e) {
            if (e.getCause() instanceof SAXParseException) {
                SAXParseException se = (SAXParseException) e.getCause();
                System.out.println("("
                    + se.getLineNumber()
                    + ","
                    + se.getColumnNumber()
                    + "): "
                    + se.getMessage());
                return;
            } else
                // I found that Crimson doesn't show the proper stack trace
                // when a RuntimeException happens inside a SchemaBuilder.
                // the following code shows the actual exception that happened.
                if (e.getCause() instanceof SAXException) {
                    SAXException se = (SAXException) e.getCause();
                    if (se.getException() != null)
                        se.getException().printStackTrace();
                }
            throw e;
        }
    }
}
