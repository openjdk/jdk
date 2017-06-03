/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.printer;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Elementary, generic generator of Ideal Graph Visualizer input for use in printers for specific
 * data structures.
 */
class BasicIdealGraphPrinter {

    /**
     * Edge between two nodes.
     */
    protected static class Edge {

        final String from;
        final int fromIndex;
        final String to;
        final int toIndex;
        final String label;

        public Edge(String from, int fromIndex, String to, int toIndex, String label) {
            assert (from != null && to != null);
            this.from = from;
            this.fromIndex = fromIndex;
            this.to = to;
            this.toIndex = toIndex;
            this.label = label;
        }

        @Override
        public int hashCode() {
            int h = from.hashCode() ^ to.hashCode();
            h = 3 * h + fromIndex;
            h = 5 * h + toIndex;
            if (label != null) {
                h ^= label.hashCode();
            }
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Edge) {
                Edge other = (Edge) obj;
                return from.equals(other.from) && fromIndex == other.fromIndex && to.equals(other.to) && toIndex == other.toIndex &&
                                (label == other.label || (label != null && label.equals(other.label)));
            }
            return false;
        }
    }

    private final PrintStream stream;

    /**
     * Creates a new {@link IdealGraphPrinter} that writes to the specified output stream.
     */
    protected BasicIdealGraphPrinter(OutputStream stream) {
        try {
            OutputStream buffered;
            if (stream instanceof BufferedOutputStream) {
                buffered = stream;
            } else {
                buffered = new BufferedOutputStream(stream, 256 * 1024);
            }
            this.stream = new PrintStream(buffered, false, Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Flushes any buffered output.
     */
    protected void flush() {
        stream.flush();
    }

    /**
     * Starts a new graph document.
     */
    protected void begin() {
        stream.println("<graphDocument>");
    }

    protected void beginGroup() {
        stream.println("<group>");
    }

    protected void beginMethod(String name, String shortName, int bci) {
        stream.printf(" <method name='%s' shortName='%s' bci='%d'>%n", escape(name), escape(shortName), bci);
    }

    protected void beginBytecodes() {
        stream.println("  <bytecodes>\n<![CDATA[");
    }

    protected void printBytecode(int bci, String mnemonic, int[] extra) {
        stream.print(bci);
        stream.print(' ');
        stream.print(mnemonic);
        if (extra != null) {
            for (int b : extra) {
                stream.print(' ');
                stream.print(b);
            }
        }
        stream.println();
    }

    protected void endBytecodes() {
        stream.println("  ]]></bytecodes>");
    }

    protected void printBytecodes(String disassembly) {
        beginBytecodes();
        stream.println(disassembly);
        endBytecodes();
    }

    protected void endMethod() {
        stream.println(" </method>");
    }

    protected void beginGraph(String title) {
        stream.printf(" <graph name='%s'>%n", escape(title));
    }

    protected void beginProperties() {
        stream.print("<properties>");
    }

    protected void printProperty(String name, String value) {
        stream.printf("<p name='%s'>%s</p>", escape(name), escape(value));
    }

    protected void endProperties() {
        stream.print("</properties>");
    }

    protected void printProperties(Map<String, String> properties) {
        beginProperties();
        for (Entry<String, String> entry : properties.entrySet()) {
            printProperty(entry.getKey(), entry.getValue());
        }
        endProperties();
    }

    protected void beginNodes() {
        stream.println("  <nodes>");
    }

    protected void beginNode(String id) {
        stream.printf("   <node id='%s'>", escape(id));
    }

    protected void endNode() {
        stream.println("   </node>");
    }

    protected void printNode(String id, Map<String, String> properties) {
        beginNode(id);
        if (properties != null) {
            printProperties(properties);
        }
        endNode();
    }

    protected void endNodes() {
        stream.println("  </nodes>");
    }

    protected void beginEdges() {
        stream.println("  <edges>");
    }

    protected void printEdge(Edge edge) {
        stream.printf("   <edge from='%s' fromIndex='%d' to='%s' toIndex='%d' label='%s' />%n", escape(edge.from), edge.fromIndex, escape(edge.to), edge.toIndex, escape(edge.label));
    }

    protected void endEdges() {
        stream.println("  </edges>");
    }

    protected void beginControlFlow() {
        stream.println("  <controlFlow>");
    }

    protected void beginBlock(String name) {
        stream.printf("   <block name='%s'>%n", escape(name));
    }

    protected void beginSuccessors() {
        stream.println("    <successors>");
    }

    protected void printSuccessor(String name) {
        stream.printf("     <successor name='%s'/>%n", escape(name));
    }

    protected void endSuccessors() {
        stream.println("    </successors>");
    }

    protected void beginBlockNodes() {
        stream.println("    <nodes>");
    }

    protected void printBlockNode(String nodeId) {
        stream.printf("     <node id='%s'/>%n", escape(nodeId));
    }

    protected void endBlockNodes() {
        stream.println("    </nodes>");
    }

    protected void endBlock() {
        stream.println("   </block>");
    }

    protected void endControlFlow() {
        stream.println("  </controlFlow>");
    }

    protected void endGraph() {
        stream.println(" </graph>");
    }

    /**
     * Ends the current group.
     */
    public void endGroup() {
        stream.println("</group>");
    }

    /**
     * Finishes the graph document and flushes the output stream.
     */
    protected void end() {
        stream.println("</graphDocument>");
        flush();
    }

    public void close() {
        end();
        stream.close();
    }

    public boolean isValid() {
        return !stream.checkError();
    }

    private static String escape(String s) {
        StringBuilder str = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                case '<':
                case '>':
                case '"':
                case '\'':
                    if (str == null) {
                        str = new StringBuilder();
                        str.append(s, 0, i);
                    }
                    switch (c) {
                        case '&':
                            str.append("&amp;");
                            break;
                        case '<':
                            str.append("&lt;");
                            break;
                        case '>':
                            str.append("&gt;");
                            break;
                        case '"':
                            str.append("&quot;");
                            break;
                        case '\'':
                            str.append("&apos;");
                            break;
                        default:
                            assert false;
                    }
                    break;
                case '\u0000':
                case '\u0001':
                case '\u0002':
                case '\u0003':
                case '\u0004':
                case '\u0005':
                case '\u0006':
                case '\u0007':
                case '\u0008':
                case '\u000b':
                case '\u000c':
                case '\u000e':
                case '\u000f':
                case '\u0010':
                case '\u0011':
                case '\u0012':
                case '\u0013':
                case '\u0014':
                case '\u0015':
                case '\u0016':
                case '\u0017':
                case '\u0018':
                case '\u0019':
                case '\u001a':
                case '\u001b':
                case '\u001c':
                case '\u001d':
                case '\u001e':
                case '\u001f':
                    if (str == null) {
                        str = new StringBuilder();
                        str.append(s, 0, i);
                    }
                    str.append("'0x").append(Integer.toHexString(c));
                    break;
                default:
                    if (str != null) {
                        str.append(c);
                    }
                    break;
            }
        }
        if (str == null) {
            return s;
        } else {
            return str.toString();
        }
    }
}
