/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * A SAX based parser of LogCompilation output from HotSpot.  It takes a complete
 * @author never
 */

package com.sun.hotspot.tools.compiler;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Stack;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public class LogParser extends DefaultHandler implements ErrorHandler, Constants {

    static final HashMap<String, String> typeMap;
    static {
        typeMap = new HashMap<String, String>();
        typeMap.put("[I", "int[]");
        typeMap.put("[C", "char[]");
        typeMap.put("[Z", "boolean[]");
        typeMap.put("[L", "Object[]");
        typeMap.put("[B", "byte[]");
    }

    static Comparator<LogEvent> sortByStart = new Comparator<LogEvent>() {

        public int compare(LogEvent a, LogEvent b) {
            double difference = (a.getStart() - b.getStart());
            if (difference < 0) {
                return -1;
            }
            if (difference > 0) {
                return 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    };
    static Comparator<LogEvent> sortByNameAndStart = new Comparator<LogEvent>() {

        public int compare(LogEvent a, LogEvent b) {
            Compilation c1 = a.getCompilation();
            Compilation c2 = b.getCompilation();
            if (c1 != null && c2 != null) {
                int result = c1.getMethod().toString().compareTo(c2.getMethod().toString());
                if (result != 0) {
                    return result;
                }
            }
            double difference = (a.getStart() - b.getStart());
            if (difference < 0) {
                return -1;
            }
            if (difference > 0) {
                return 1;
            }
            return 0;
        }

        public boolean equals(Object other) {
            return false;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    };
    static Comparator<LogEvent> sortByElapsed = new Comparator<LogEvent>() {

        public int compare(LogEvent a, LogEvent b) {
            double difference = (a.getElapsedTime() - b.getElapsedTime());
            if (difference < 0) {
                return -1;
            }
            if (difference > 0) {
                return 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    };

    private ArrayList<LogEvent> events = new ArrayList<LogEvent>();

    private HashMap<String, String> types = new HashMap<String, String>();
    private HashMap<String, Method> methods = new HashMap<String, Method>();
    private LinkedHashMap<String, NMethod> nmethods = new LinkedHashMap<String, NMethod>();
    private HashMap<String, Compilation> compiles = new HashMap<String, Compilation>();
    private String failureReason;
    private int bci;
    private Stack<CallSite> scopes = new Stack<CallSite>();
    private Compilation compile;
    private CallSite site;
    private Stack<Phase> phaseStack = new Stack<Phase>();
    private UncommonTrapEvent currentTrap;

    long parseLong(String l) {
        try {
            return Long.decode(l).longValue();
        } catch (NumberFormatException nfe) {
            int split = l.length() - 8;
            String s1 = "0x" + l.substring(split);
            String s2 = l.substring(0, split);
            long v1 = Long.decode(s1).longValue() & 0xffffffffL;
            long v2 = (Long.decode(s2).longValue() & 0xffffffffL) << 32;
            if (!l.equals("0x" + Long.toHexString(v1 + v2))) {
                System.out.println(l);
                System.out.println(s1);
                System.out.println(s2);
                System.out.println(v1);
                System.out.println(v2);
                System.out.println(Long.toHexString(v1 + v2));
                throw new InternalError("bad conversion");
            }
            return v1 + v2;
        }
    }

    public static ArrayList<LogEvent> parse(String file, boolean cleanup) throws Exception {
        return parse(new FileReader(file), cleanup);
    }

    public static ArrayList<LogEvent> parse(Reader reader, boolean cleanup) throws Exception {
        // Create the XML input factory
        SAXParserFactory factory = SAXParserFactory.newInstance();

        // Create the XML LogEvent reader
        SAXParser p = factory.newSAXParser();

        if (cleanup) {
            // some versions of the log have slightly malformed XML, so clean it
            // up before passing it to SAX
            reader = new LogCleanupReader(reader);
        }

        LogParser log = new LogParser();
        p.parse(new InputSource(reader), log);

        // Associate compilations with their NMethods
        for (NMethod nm : log.nmethods.values()) {
            Compilation c = log.compiles.get(nm.getId());
            nm.setCompilation(c);
            // Native wrappers for methods don't have a compilation
            if (c != null) {
                c.setNMethod(nm);
            }
        }

        // Initially we want the LogEvent log sorted by timestamp
        Collections.sort(log.events, sortByStart);

        return log.events;
    }

    String search(Attributes attr, String name) {
        return search(attr, name, null);
    }

    String search(Attributes attr, String name, String defaultValue) {
        String result = attr.getValue(name);
        if (result != null) {
            return result;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        for (int i = 0; i < attr.getLength(); i++) {
            System.out.println(attr.getQName(i) + " " + attr.getValue(attr.getQName(i)));
        }
        throw new InternalError("can't find " + name);
    }
    int indent = 0;
    String compile_id;

    String type(String id) {
        String result = types.get(id);
        if (result == null) {
            throw new InternalError(id);
        }
        String remapped = typeMap.get(result);
        if (remapped != null) {
            return remapped;
        }
        return result;
    }

    void type(String id, String name) {
        assert type(id) == null;
        types.put(id, name);
    }

    Method method(String id) {
        Method result = methods.get(id);
        if (result == null) {
            throw new InternalError(id);
        }
        return result;
    }

    public String makeId(Attributes atts) {
        String id = atts.getValue("compile_id");
        String kind = atts.getValue("kind");
        if (kind != null && kind.equals("osr")) {
            id += "%";
        }
        return id;
    }

    @Override
    public void startElement(String uri,
            String localName,
            String qname,
            Attributes atts) {
        if (qname.equals("phase")) {
            Phase p = new Phase(search(atts, "name"),
                    Double.parseDouble(search(atts, "stamp")),
                    Integer.parseInt(search(atts, "nodes")));
            phaseStack.push(p);
        } else if (qname.equals("phase_done")) {
            Phase p = phaseStack.pop();
            p.setEndNodes(Integer.parseInt(search(atts, "nodes")));
            p.setEnd(Double.parseDouble(search(atts, "stamp")));
            compile.getPhases().add(p);
        } else if (qname.equals("task")) {
            compile = new Compilation(Integer.parseInt(search(atts, "compile_id", "-1")));
            compile.setStart(Double.parseDouble(search(atts, "stamp")));
            compile.setICount(search(atts, "count", "0"));
            compile.setBCount(search(atts, "backedge_count", "0"));

            String method = atts.getValue("method");
            int space = method.indexOf(' ');
            method = method.substring(0, space) + "::" +
                    method.substring(space + 1, method.indexOf(' ', space + 1) + 1);
            String compiler = atts.getValue("compiler");
            if (compiler == null) {
                compiler = "";
            }
            String kind = atts.getValue("compile_kind");
            if (kind == null) {
                kind = "normal";
            }
            if (kind.equals("osr")) {
                compile.setOsr(true);
                compile.setOsr_bci(Integer.parseInt(search(atts, "osr_bci")));
            } else if (kind.equals("c2i")) {
                compile.setSpecial("--- adapter " + method);
            } else {
                compile.setSpecial(compile.getId() + " " + method + " (0 bytes)");
            }
            events.add(compile);
            compiles.put(makeId(atts), compile);
        } else if (qname.equals("type")) {
            type(search(atts, "id"), search(atts, "name"));
        } else if (qname.equals("bc")) {
            bci = Integer.parseInt(search(atts, "bci"));
        } else if (qname.equals("klass")) {
            type(search(atts, "id"), search(atts, "name"));
        } else if (qname.equals("method")) {
            String id = search(atts, "id");
            Method m = new Method();
            m.setHolder(type(search(atts, "holder")));
            m.setName(search(atts, "name"));
            m.setReturnType(type(search(atts, "return")));
            m.setArguments(search(atts, "arguments", "void"));
            m.setBytes(search(atts, "bytes"));
            m.setIICount(search(atts, "iicount"));
            m.setFlags(search(atts, "flags"));
            methods.put(id, m);
        } else if (qname.equals("call")) {
            site = new CallSite(bci, method(search(atts, "method")));
            site.setCount(Integer.parseInt(search(atts, "count")));
            String receiver = atts.getValue("receiver");
            if (receiver != null) {
                site.setReceiver(type(receiver));
                site.setReceiver_count(Integer.parseInt(search(atts, "receiver_count")));
            }
            scopes.peek().add(site);
        } else if (qname.equals("regalloc")) {
            compile.setAttempts(Integer.parseInt(search(atts, "attempts")));
        } else if (qname.equals("inline_fail")) {
            scopes.peek().last().setReason(search(atts, "reason"));
        } else if (qname.equals("failure")) {
            failureReason = search(atts, "reason");
        } else if (qname.equals("task_done")) {
            compile.setEnd(Double.parseDouble(search(atts, "stamp")));
            if (Integer.parseInt(search(atts, "success")) == 0) {
                compile.setFailureReason(failureReason);
            }
        } else if (qname.equals("make_not_entrant")) {
            String id = makeId(atts);
            NMethod nm = nmethods.get(id);
            if (nm == null) throw new InternalError();
            LogEvent e = new MakeNotEntrantEvent(Double.parseDouble(search(atts, "stamp")), id,
                                                 atts.getValue("zombie") != null, nm);
            events.add(e);
        } else if (qname.equals("uncommon_trap")) {
            String id = atts.getValue("compile_id");
            if (id != null) {
                id = makeId(atts);
                currentTrap = new UncommonTrapEvent(Double.parseDouble(search(atts, "stamp")),
                        id,
                        atts.getValue("reason"),
                        atts.getValue("action"),
                        Integer.parseInt(search(atts, "count", "0")));
                events.add(currentTrap);
            } else {
                // uncommon trap inserted during parsing.
                // ignore for now
            }
        } else if (qname.equals("jvms")) {
            // <jvms bci='4' method='java/io/DataInputStream readChar ()C' bytes='40' count='5815' iicount='20815'/>
            if (currentTrap != null) {
                currentTrap.addJVMS(atts.getValue("method"), Integer.parseInt(atts.getValue("bci")));
            } else {
                System.err.println("Missing uncommon_trap for jvms");
            }
        } else if (qname.equals("nmethod")) {
            String id = makeId(atts);
            NMethod nm = new NMethod(Double.parseDouble(search(atts, "stamp")),
                    id,
                    parseLong(atts.getValue("address")),
                    parseLong(atts.getValue("size")));
            nmethods.put(id, nm);
            events.add(nm);
        } else if (qname.equals("parse")) {
            Method m = method(search(atts, "method"));
            if (scopes.size() == 0) {
                compile.setMethod(m);
                scopes.push(compile.getCall());
            } else {
                if (site.getMethod() == m) {
                    scopes.push(site);
                } else if (scopes.peek().getCalls().size() > 2 && m == scopes.peek().last(-2).getMethod()) {
                    scopes.push(scopes.peek().last(-2));
                } else {
                    System.out.println(site.getMethod());
                    System.out.println(m);
                    throw new InternalError("call site and parse don't match");
                }
            }
        }
    }

    @Override
    public void endElement(String uri,
            String localName,
            String qname) {
        if (qname.equals("parse")) {
            indent -= 2;
            scopes.pop();
        } else if (qname.equals("uncommon_trap")) {
            currentTrap = null;
        } else if (qname.equals("task")) {
            types.clear();
            methods.clear();
            site = null;
        }
    }

    @Override
    public void warning(org.xml.sax.SAXParseException e) {
        System.err.println(e.getMessage() + " at line " + e.getLineNumber() + ", column " + e.getColumnNumber());
        e.printStackTrace();
    }

    @Override
    public void error(org.xml.sax.SAXParseException e) {
        System.err.println(e.getMessage() + " at line " + e.getLineNumber() + ", column " + e.getColumnNumber());
        e.printStackTrace();
    }

    @Override
    public void fatalError(org.xml.sax.SAXParseException e) {
        System.err.println(e.getMessage() + " at line " + e.getLineNumber() + ", column " + e.getColumnNumber());
        e.printStackTrace();
    }
}
