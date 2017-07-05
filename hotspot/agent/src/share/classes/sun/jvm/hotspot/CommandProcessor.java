/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot;

import java.io.*;
import java.math.*;
import java.util.*;
import java.util.regex.*;

import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.Field;
import sun.jvm.hotspot.HotSpotTypeDataBase;
import sun.jvm.hotspot.types.basic.BasicType;
import sun.jvm.hotspot.types.CIntegerType;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.compiler.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.interpreter.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.soql.*;
import sun.jvm.hotspot.ui.classbrowser.*;
import sun.jvm.hotspot.ui.tree.*;
import sun.jvm.hotspot.tools.*;
import sun.jvm.hotspot.tools.ObjectHistogram;
import sun.jvm.hotspot.tools.StackTrace;

public class CommandProcessor {
    public abstract static class DebuggerInterface {
        public abstract HotSpotAgent getAgent();
        public abstract boolean isAttached();
        public abstract void attach(String pid);
        public abstract void attach(String java, String core);
        public abstract void detach();
        public abstract void reattach();
    }

    static class Tokens {
        final String input;
        int i;
        String[] tokens;
        int length;

        String[] splitWhitespace(String cmd) {
            String[] t = cmd.split("\\s");
            if (t.length == 1 && t[0].length() == 0) {
                return new String[0];
            }
            return t;
        }

        void add(String s, ArrayList t) {
            if (s.length() > 0) {
                t.add(s);
            }
        }

        Tokens(String cmd) {
            input = cmd;

            // check for quoting
            int quote = cmd.indexOf('"');
            ArrayList t = new ArrayList();
            if (quote != -1) {
                while (cmd.length() > 0) {
                    if (quote != -1) {
                        int endquote = cmd.indexOf('"', quote + 1);
                        if (endquote == -1) {
                            throw new RuntimeException("mismatched quotes: " + input);
                        }

                        String before = cmd.substring(0, quote).trim();
                        String quoted = cmd.substring(quote + 1, endquote);
                        cmd = cmd.substring(endquote + 1).trim();
                        if (before.length() > 0) {
                            String[] w = splitWhitespace(before);
                            for (int i = 0; i < w.length; i++) {
                                add(w[i], t);
                            }
                        }
                        add(quoted, t);
                        quote = cmd.indexOf('"');
                    } else {
                        String[] w = splitWhitespace(cmd);
                        for (int i = 0; i < w.length; i++) {
                            add(w[i], t);
                        }
                        cmd = "";

                    }
                }
            } else {
                String[] w = splitWhitespace(cmd);
                for (int i = 0; i < w.length; i++) {
                    add(w[i], t);
                }
            }
            tokens = (String[])t.toArray(new String[0]);
            i = 0;
            length = tokens.length;

            //for (int i = 0; i < tokens.length; i++) {
            //    System.out.println("\"" + tokens[i] + "\"");
            //}
        }

        String nextToken() {
            return tokens[i++];
        }
        boolean hasMoreTokens() {
            return i < length;
        }
        int countTokens() {
            return length - i;
        }
        void trim(int n) {
            if (length >= n) {
                length -= n;
            } else {
                throw new IndexOutOfBoundsException(String.valueOf(n));
            }
        }
        String join(String sep) {
            StringBuffer result = new StringBuffer();
            for (int w = i; w < length; w++) {
                result.append(tokens[w]);
                if (w + 1 < length) {
                    result.append(sep);
                }
            }
            return result.toString();
        }

        String at(int i) {
            if (i < 0 || i >= length) {
                throw new IndexOutOfBoundsException(String.valueOf(i));
            }
            return tokens[i];
        }
    }


    abstract class Command {
        Command(String n, String u, boolean ok) {
            name = n;
            usage = u;
            okIfDisconnected = ok;
        }

        Command(String n, boolean ok) {
            name = n;
            usage = n;
            okIfDisconnected = ok;
        }

        final String name;
        final String usage;
        final boolean okIfDisconnected;
        abstract void doit(Tokens t);
        void usage() {
            out.println("Usage: " + usage);
        }

        void printOopValue(Oop oop) {
            if (oop != null) {
                Klass k = oop.getKlass();
                Symbol s = k.getName();
                if (s != null) {
                    out.print("Oop for " + s.asString() + " @ ");
                } else {
                    out.print("Oop @ ");
                }
                Oop.printOopAddressOn(oop, out);
            } else {
                out.print("null");
            }
        }

        void printNode(SimpleTreeNode node) {
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                try {
                    SimpleTreeNode field = node.getChild(i);
                    if (field instanceof OopTreeNodeAdapter) {
                        out.print(field);
                        out.print(" ");
                        printOopValue(((OopTreeNodeAdapter)field).getOop());
                        out.println();
                    } else {
                        out.println(field);
                    }
                } catch (Exception e) {
                    out.println();
                    out.println("Error: " + e);
                    if (verboseExceptions) {
                        e.printStackTrace(out);
                    }
                }
            }
        }
    }

    void quote(String s) {
        if (s.indexOf(" ") == -1) {
            out.print(s);
        } else {
            out.print("\"");
            out.print(s);
            out.print("\"");
        }
    }

    void dumpType(Type type) {
        out.print("type ");
        quote(type.getName());
        out.print(" ");
        if (type.getSuperclass() != null) {
            quote(type.getSuperclass().getName());
            out.print(" ");
        } else {
            out.print("null ");
        }
        out.print(type.isOopType());
        out.print(" ");
        if (type.isCIntegerType()) {
            out.print("true ");
            out.print(((CIntegerType)type).isUnsigned());
            out.print(" ");
        } else {
            out.print("false false ");
        }
        out.print(type.getSize());
        out.println();
    }

    void dumpFields(Type type) {
        Iterator i = type.getFields();
        while (i.hasNext()) {
            Field f = (Field) i.next();
            out.print("field ");
            quote(type.getName());
            out.print(" ");
            out.print(f.getName());
            out.print(" ");
            quote(f.getType().getName());
            out.print(" ");
            out.print(f.isStatic());
            out.print(" ");
            if (f.isStatic()) {
                out.print("0 ");
                out.print(f.getStaticFieldAddress());
            } else {
                out.print(f.getOffset());
                out.print(" 0x0");
            }
            out.println();
        }
    }


    Address lookup(String symbol) {
        if (symbol.indexOf("::") != -1) {
            String[] parts = symbol.split("::");
            StringBuffer mangled = new StringBuffer("__1c");
            for (int i = 0; i < parts.length; i++) {
                int len = parts[i].length();
                if (len >= 26) {
                    mangled.append((char)('a' + (len / 26)));
                    len = len % 26;
                }
                mangled.append((char)('A' + len));
                mangled.append(parts[i]);
            }
            mangled.append("_");
            symbol = mangled.toString();
        }
        return VM.getVM().getDebugger().lookup(null, symbol);
    }

    Address parseAddress(String addr) {
        return VM.getVM().getDebugger().parseAddress(addr);
    }

    private final Command[] commandList = {
        new Command("reattach", true) {
            public void doit(Tokens t) {
                int tokens = t.countTokens();
                if (tokens != 0) {
                    usage();
                    return;
                }
                preAttach();
                debugger.reattach();
                postAttach();
            }
        },
        new Command("attach", "attach pid | exec core", true) {
            public void doit(Tokens t) {
                int tokens = t.countTokens();
                if (tokens == 1) {
                    preAttach();
                    debugger.attach(t.nextToken());
                    postAttach();
                } else if (tokens == 2) {
                    preAttach();
                    debugger.attach(t.nextToken(), t.nextToken());
                    postAttach();
                } else {
                    usage();
                }
            }
        },
        new Command("detach", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 0) {
                    usage();
                } else {
                    debugger.detach();
                }
            }
        },
        new Command("examine", "examine [ address/count ] | [ address,address]", false) {
            Pattern args1 = Pattern.compile("^(0x[0-9a-f]+)(/([0-9]*)([a-z]*))?$");
            Pattern args2 = Pattern.compile("^(0x[0-9a-f]+),(0x[0-9a-f]+)(/[a-z]*)?$");

            String fill(Address a, int width) {
                String s = "0x0";
                if (a != null) {
                    s = a.toString();
                }
                if (s.length() != width) {
                    return s.substring(0, 2) + "000000000000000000000".substring(0, width - s.length()) + s.substring(2);
                }
                return s;
            }

            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    String arg = t.nextToken();
                    Matcher m1 = args1.matcher(arg);
                    Matcher m2 = args2.matcher(arg);
                    Address start = null;
                    Address end   = null;
                    String format = "";
                    int formatSize = (int)VM.getVM().getAddressSize();

                    if (m1.matches()) {
                        start = VM.getVM().getDebugger().parseAddress(m1.group(1));
                        int count = 1;
                        if (m1.group(2) != null) {
                            count = Integer.parseInt(m1.group(3));
                        }
                        end = start.addOffsetTo(count * formatSize);
                    } else if (m2.matches()) {
                        start = VM.getVM().getDebugger().parseAddress(m2.group(1));
                        end   = VM.getVM().getDebugger().parseAddress(m2.group(2));
                    } else {
                        usage();
                        return;
                    }
                    int line = 80;
                    int formatWidth = formatSize * 8 / 4 + 2;

                    out.print(fill(start, formatWidth));
                    out.print(": ");
                    int width = line - formatWidth - 2;

                    boolean needsPrintln = true;
                    while (start != null && start.lessThan(end)) {
                        Address val = start.getAddressAt(0);
                        out.print(fill(val, formatWidth));
                        needsPrintln = true;
                        width -= formatWidth;
                        start = start.addOffsetTo(formatSize);
                        if (width <= formatWidth) {
                            out.println();
                            needsPrintln = false;
                            if (start.lessThan(end)) {
                                out.print(fill(start, formatWidth));
                                out.print(": ");
                                width = line - formatWidth - 2;
                            }
                        } else {
                            out.print(" ");
                            width -= 1;
                        }
                    }
                    if (needsPrintln) {
                        out.println();
                    }
                }
            }
        },
        new Command("findpc", "findpc address", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    Address a = VM.getVM().getDebugger().parseAddress(t.nextToken());
                    PointerLocation loc = PointerFinder.find(a);
                    loc.printOn(out);
                }
            }
        },
        new Command("flags", "flags [ flag ]", false) {
            public void doit(Tokens t) {
                int tokens = t.countTokens();
                if (tokens != 0 && tokens != 1) {
                    usage();
                } else {
                    String name = tokens > 0 ? t.nextToken() : null;

                    VM.Flag[] flags = VM.getVM().getCommandLineFlags();
                    if (flags == null) {
                        out.println("Command Flag info not available (use 1.4.1_03 or later)!");
                    } else {
                        boolean printed = false;
                        for (int f = 0; f < flags.length; f++) {
                            VM.Flag flag = flags[f];
                            if (name == null || flag.getName().equals(name)) {
                                out.println(flag.getName() + " = " + flag.getValue());
                                printed = true;
                            }
                        }
                        if (name != null && !printed) {
                            out.println("Couldn't find flag: " + name);
                        }
                    }
                }
            }
        },
        new Command("help", "help [ command ]", true) {
            public void doit(Tokens t) {
                int tokens = t.countTokens();
                Command cmd = null;
                if (tokens == 1) {
                    cmd = findCommand(t.nextToken());
                }

                if (cmd != null) {
                    cmd.usage();
                } else if (tokens == 0) {
                    out.println("Available commands:");
                    Object[] keys = commands.keySet().toArray();
                    Arrays.sort(keys, new Comparator() {
                             public int compare(Object o1, Object o2) {
                                 return o1.toString().compareTo(o2.toString());
                             }
                          });
                    for (int i = 0; i < keys.length; i++) {
                        out.print("  ");
                        out.println(((Command)commands.get(keys[i])).usage);
                    }
                }
            }
        },
        new Command("history", "history", true) {
            public void doit(Tokens t) {
                int tokens = t.countTokens();
                if (tokens != 0 && (tokens != 1 || !t.nextToken().equals("-h"))) {
                    usage();
                    return;
                }
                boolean printIndex = tokens == 0;
                for (int i = 0; i < history.size(); i++) {
                    if (printIndex) out.print(i + " ");
                    out.println(history.get(i));
                }
            }
        },
        new Command("revptrs", "revptrs address", false) {
            public void doit(Tokens t) {
                int tokens = t.countTokens();
                if (tokens != 1 && (tokens != 2 || !t.nextToken().equals("-c"))) {
                    usage();
                    return;
                }
                boolean chase = tokens == 2;
                ReversePtrs revptrs = VM.getVM().getRevPtrs();
                if (revptrs == null) {
                    out.println("Computing reverse pointers...");
                    ReversePtrsAnalysis analysis = new ReversePtrsAnalysis();
                    final boolean[] complete = new boolean[1];
                    HeapProgressThunk thunk = new HeapProgressThunk() {
                            public void heapIterationFractionUpdate(double d) {}
                            public synchronized void heapIterationComplete() {
                                complete[0] = true;
                                notify();
                            }
                        };
                    analysis.setHeapProgressThunk(thunk);
                    analysis.run();
                    while (!complete[0]) {
                        synchronized (thunk) {
                            try {
                                thunk.wait();
                            } catch (Exception e) {
                            }
                        }
                    }
                    revptrs = VM.getVM().getRevPtrs();
                    out.println("Done.");
                }
                Address a = VM.getVM().getDebugger().parseAddress(t.nextToken());
                if (VM.getVM().getUniverse().heap().isInReserved(a)) {
                    OopHandle handle = a.addOffsetToAsOopHandle(0);
                    Oop oop = VM.getVM().getObjectHeap().newOop(handle);
                    ArrayList ptrs = revptrs.get(oop);
                    if (ptrs == null) {
                        out.println("no live references to " + a);
                    } else {
                        if (chase) {
                            while (ptrs.size() == 1) {
                                LivenessPathElement e = (LivenessPathElement)ptrs.get(0);
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                Oop.printOopValueOn(e.getObj(), new PrintStream(bos));
                                out.println(bos.toString());
                                ptrs = revptrs.get(e.getObj());
                            }
                        } else {
                            for (int i = 0; i < ptrs.size(); i++) {
                                LivenessPathElement e = (LivenessPathElement)ptrs.get(i);
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                Oop.printOopValueOn(e.getObj(), new PrintStream(bos));
                                out.println(bos.toString());
                                oop = e.getObj();
                            }
                        }
                    }
                }
            }
        },
        new Command("inspect", "inspect expression", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    Address a = VM.getVM().getDebugger().parseAddress(t.nextToken());
                    SimpleTreeNode node = null;
                    if (VM.getVM().getUniverse().heap().isInReserved(a)) {
                        OopHandle handle = a.addOffsetToAsOopHandle(0);
                        Oop oop = VM.getVM().getObjectHeap().newOop(handle);
                        node = new OopTreeNodeAdapter(oop, null);

                        out.println("instance of " + node.getValue() + " @ " + a +
                                    " (size = " + oop.getObjectSize() + ")");
                    } else if (VM.getVM().getCodeCache().contains(a)) {
                        CodeBlob blob = VM.getVM().getCodeCache().findBlobUnsafe(a);
                        a = blob.headerBegin();
                    }
                    if (node == null) {
                        Type type = VM.getVM().getTypeDataBase().guessTypeForAddress(a);
                        if (type != null) {
                            out.println("Type is " + type.getName() + " (size of " + type.getSize() + ")");
                            node = new CTypeTreeNodeAdapter(a, type, null);
                        }
                    }
                    if (node != null) {
                        printNode(node);
                    }
                }
            }
        },
        new Command("jhisto", "jhisto", false) {
            public void doit(Tokens t) {
                 ObjectHistogram histo = new ObjectHistogram();
                 histo.run(out, err);
            }
        },
        new Command("jstack", "jstack [-v]", false) {
            public void doit(Tokens t) {
                boolean verbose = false;
                if (t.countTokens() > 0 && t.nextToken().equals("-v")) {
                    verbose = true;
                }
                StackTrace jstack = new StackTrace(verbose, true);
                jstack.run(out);
            }
        },
        new Command("print", "print expression", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    Address a = VM.getVM().getDebugger().parseAddress(t.nextToken());
                    HTMLGenerator gen = new HTMLGenerator(false);
                    out.println(gen.genHTML(a));
                }
            }
        },
        new Command("printas", "printas type expression", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 2) {
                    usage();
                } else {
                    Type type = agent.getTypeDataBase().lookupType(t.nextToken());
                    Address a = VM.getVM().getDebugger().parseAddress(t.nextToken());
                    CTypeTreeNodeAdapter node = new CTypeTreeNodeAdapter(a, type, null);

                    out.println("pointer to " + type + " @ " + a +
                                " (size = " + type.getSize() + ")");
                    printNode(node);
                }
            }
        },
        new Command("symbol", "symbol name", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    String symbol = t.nextToken();
                    Address a = lookup(symbol);
                    out.println(symbol + " = " + a);
                }
            }
        },
        new Command("printstatics", "printstatics [ type ]", false) {
            public void doit(Tokens t) {
                if (t.countTokens() > 1) {
                    usage();
                } else {
                    if (t.countTokens() == 0) {
                        out.println("All known static fields");
                        printNode(new CTypeTreeNodeAdapter(agent.getTypeDataBase().getTypes()));
                    } else {
                        Type type = agent.getTypeDataBase().lookupType(t.nextToken());
                        out.println("Static fields of " + type.getName());
                        printNode(new CTypeTreeNodeAdapter(type));
                    }
                }
            }
        },
        new Command("pmap", "pmap", false) {
            public void doit(Tokens t) {
                PMap pmap = new PMap();
                pmap.run(out, debugger.getAgent().getDebugger());
            }
        },
        new Command("pstack", "pstack [-v]", false) {
            public void doit(Tokens t) {
                boolean verbose = false;
                if (t.countTokens() > 0 && t.nextToken().equals("-v")) {
                    verbose = true;
                }
                PStack pstack = new PStack(verbose, true);
                pstack.run(out, debugger.getAgent().getDebugger());
            }
        },
        new Command("quit", true) {
            public void doit(Tokens t) {
                if (t.countTokens() != 0) {
                    usage();
                } else {
                    debugger.detach();
                    System.exit(0);
                }
            }
        },
        new Command("echo", "echo [ true | false ]", true) {
            public void doit(Tokens t) {
                if (t.countTokens() == 0) {
                    out.println("echo is " + doEcho);
                } else if (t.countTokens() == 1) {
                    doEcho = Boolean.valueOf(t.nextToken()).booleanValue();
                } else {
                    usage();
                }
            }
        },
        new Command("versioncheck", "versioncheck [ true | false ]", true) {
            public void doit(Tokens t) {
                if (t.countTokens() == 0) {
                    out.println("versioncheck is " +
                                (System.getProperty("sun.jvm.hotspot.runtime.VM.disableVersionCheck") == null));
                } else if (t.countTokens() == 1) {
                    if (Boolean.valueOf(t.nextToken()).booleanValue()) {
                        System.setProperty("sun.jvm.hotspot.runtime.VM.disableVersionCheck", null);
                    } else {
                        System.setProperty("sun.jvm.hotspot.runtime.VM.disableVersionCheck", "true");
                    }
                } else {
                    usage();
                }
            }
        },
        new Command("scanoops", "scanoops start end [ type ]", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 2 && t.countTokens() != 3) {
                    usage();
                } else {
                    long stride = VM.getVM().getAddressSize();
                    Address base = VM.getVM().getDebugger().parseAddress(t.nextToken());
                    Address end  = VM.getVM().getDebugger().parseAddress(t.nextToken());
                    Klass klass = null;
                    if (t.countTokens() == 1) {
                        klass = SystemDictionaryHelper.findInstanceKlass(t.nextToken());
                    }
                    while (base != null && base.lessThan(end)) {
                        long step = stride;
                        OopHandle handle = base.addOffsetToAsOopHandle(0);
                        if (RobustOopDeterminator.oopLooksValid(handle)) {
                            try {
                                Oop oop = VM.getVM().getObjectHeap().newOop(handle);
                                if (klass == null || oop.getKlass().isSubtypeOf(klass))
                                    out.println(handle.toString() + " " + oop.getKlass().getName().asString());
                                step = oop.getObjectSize();
                            } catch (UnknownOopException ex) {
                                // ok
                            } catch (RuntimeException ex) {
                                ex.printStackTrace();
                            }
                        }
                        base = base.addOffsetTo(step);
                    }
                }
            }
        },
        new Command("field", "field [ type [ name fieldtype isStatic offset address ] ]", true) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1 && t.countTokens() != 0 && t.countTokens() != 6) {
                    usage();
                    return;
                }
                if (t.countTokens() == 1) {
                    Type type = agent.getTypeDataBase().lookupType(t.nextToken());
                    dumpFields(type);
                } else if (t.countTokens() == 0) {
                    Iterator i = agent.getTypeDataBase().getTypes();
                    while (i.hasNext()) {
                        dumpFields((Type)i.next());
                    }
                } else {
                    BasicType containingType = (BasicType)agent.getTypeDataBase().lookupType(t.nextToken());

                    String fieldName = t.nextToken();

                    // The field's Type must already be in the database -- no exceptions
                    Type fieldType = agent.getTypeDataBase().lookupType(t.nextToken());

                    boolean isStatic = Boolean.valueOf(t.nextToken()).booleanValue();
                    long offset = Long.parseLong(t.nextToken());
                    Address staticAddress = parseAddress(t.nextToken());
                    if (isStatic && staticAddress == null) {
                        staticAddress = lookup(containingType.getName() + "::" + fieldName);
                    }

                    // check to see if the field already exists
                    Iterator i = containingType.getFields();
                    while (i.hasNext()) {
                        Field f = (Field) i.next();
                        if (f.getName().equals(fieldName)) {
                            if (f.isStatic() != isStatic) {
                                throw new RuntimeException("static/nonstatic mismatch: " + t.input);
                            }
                            if (!isStatic) {
                                if (f.getOffset() != offset) {
                                    throw new RuntimeException("bad redefinition of field offset: " + t.input);
                                }
                            } else {
                                if (!f.getStaticFieldAddress().equals(staticAddress)) {
                                    throw new RuntimeException("bad redefinition of field location: " + t.input);
                                }
                            }
                            if (f.getType() != fieldType) {
                                throw new RuntimeException("bad redefinition of field type: " + t.input);
                            }
                            return;
                        }
                    }

                    // Create field by type
                    HotSpotTypeDataBase db = (HotSpotTypeDataBase)agent.getTypeDataBase();
                    db.createField(containingType,
                                   fieldName, fieldType,
                                   isStatic,
                                   offset,
                                   staticAddress);

                }
            }

        },
        new Command("tokenize", "tokenize ...", true) {
            public void doit(Tokens t) {
                while (t.hasMoreTokens()) {
                    out.println("\"" + t.nextToken() + "\"");
                }
            }
        },
        new Command("type", "type [ type [ name super isOop isInteger isUnsigned size ] ]", true) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1 && t.countTokens() != 0 && t.countTokens() != 6) {
                    usage();
                    return;
                }
                if (t.countTokens() == 6) {
                    String typeName = t.nextToken();
                    String superclassName = t.nextToken();
                    if (superclassName.equals("null")) {
                        superclassName = null;
                    }
                    boolean isOop = Boolean.valueOf(t.nextToken()).booleanValue();
                    boolean isInteger = Boolean.valueOf(t.nextToken()).booleanValue();
                    boolean isUnsigned = Boolean.valueOf(t.nextToken()).booleanValue();
                    long size = Long.parseLong(t.nextToken());

                    BasicType type = null;
                    try {
                        type = (BasicType)agent.getTypeDataBase().lookupType(typeName);
                    } catch (RuntimeException e) {
                    }
                    if (type != null) {
                        if (type.isOopType() != isOop) {
                            throw new RuntimeException("oop mismatch in type definition: " + t.input);
                        }
                        if (type.isCIntegerType() != isInteger) {
                            throw new RuntimeException("integer type mismatch in type definition: " + t.input);
                        }
                        if (type.isCIntegerType() && (((CIntegerType)type).isUnsigned()) != isUnsigned) {
                            throw new RuntimeException("unsigned mismatch in type definition: " + t.input);
                        }
                        if (type.getSuperclass() == null) {
                            if (superclassName != null) {
                                if (type.getSize() == -1) {
                                    type.setSuperclass(agent.getTypeDataBase().lookupType(superclassName));
                                } else {
                                    throw new RuntimeException("unexpected superclass in type definition: " + t.input);
                                }
                            }
                        } else {
                            if (superclassName == null) {
                                throw new RuntimeException("missing superclass in type definition: " + t.input);
                            }
                            if (!type.getSuperclass().getName().equals(superclassName)) {
                                throw new RuntimeException("incorrect superclass in type definition: " + t.input);
                            }
                        }
                        if (type.getSize() != size) {
                            if (type.getSize() == -1) {
                                type.setSize(size);
                            }
                            throw new RuntimeException("size mismatch in type definition: " + t.input);
                        }
                        return;
                    }

                    // Create type
                    HotSpotTypeDataBase db = (HotSpotTypeDataBase)agent.getTypeDataBase();
                    db.createType(typeName, superclassName, isOop, isInteger, isUnsigned, size);
                } else if (t.countTokens() == 1) {
                    Type type = agent.getTypeDataBase().lookupType(t.nextToken());
                    dumpType(type);
                } else {
                    Iterator i = agent.getTypeDataBase().getTypes();
                    // Make sure the types are emitted in an order than can be read back in
                    HashSet emitted = new HashSet();
                    Stack pending = new Stack();
                    while (i.hasNext()) {
                        Type n = (Type)i.next();
                        if (emitted.contains(n.getName())) {
                            continue;
                        }

                        while (n != null && !emitted.contains(n.getName())) {
                            pending.push(n);
                            n = n.getSuperclass();
                        }
                        while (!pending.empty()) {
                            n = (Type)pending.pop();
                            dumpType(n);
                            emitted.add(n.getName());
                        }
                    }
                }
            }

        },
        new Command("source", "source filename", true) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                    return;
                }
                String file = t.nextToken();
                BufferedReader savedInput = in;
                try {
                    BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                    in = input;
                    run(false);
                } catch (Exception e) {
                    out.println("Error: " + e);
                    if (verboseExceptions) {
                        e.printStackTrace(out);
                    }
                } finally {
                    in = savedInput;
                }

            }
        },
        new Command("search", "search [ heap | perm | rawheap | codecache | threads ] value", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 2) {
                    usage();
                    return;
                }
                String type = t.nextToken();
                final Address value = VM.getVM().getDebugger().parseAddress(t.nextToken());
                final long stride = VM.getVM().getAddressSize();
                if (type.equals("threads")) {
                    Threads threads = VM.getVM().getThreads();
                    for (JavaThread thread = threads.first(); thread != null; thread = thread.next()) {
                        Address base = thread.getBaseOfStackPointer();
                        Address end = thread.getLastJavaSP();
                        if (end == null) continue;
                        if (end.lessThan(base)) {
                            Address tmp = base;
                            base = end;
                            end = tmp;
                        }
                        out.println("Searching " + base + " " + end);
                        while (base != null && base.lessThan(end)) {
                            Address val = base.getAddressAt(0);
                            if (AddressOps.equal(val, value)) {
                                out.println(base);
                            }
                            base = base.addOffsetTo(stride);
                        }
                    }
                } else if (type.equals("rawheap")) {
                    RawHeapVisitor iterator = new RawHeapVisitor() {
                            public void prologue(long used) {
                            }

                            public void visitAddress(Address addr) {
                                Address val = addr.getAddressAt(0);
                                if (AddressOps.equal(val, value)) {
                                        out.println("found at " + addr);
                                }
                            }
                            public void visitCompOopAddress(Address addr) {
                                Address val = addr.getCompOopAddressAt(0);
                                if (AddressOps.equal(val, value)) {
                                    out.println("found at " + addr);
                                }
                            }
                            public void epilogue() {
                            }
                        };
                    VM.getVM().getObjectHeap().iterateRaw(iterator);
                } else if (type.equals("heap") || type.equals("perm")) {
                    HeapVisitor iterator = new DefaultHeapVisitor() {
                            public boolean doObj(Oop obj) {
                                int index = 0;
                                Address start = obj.getHandle();
                                long end = obj.getObjectSize();
                                while (index < end) {
                                    Address val = start.getAddressAt(index);
                                    if (AddressOps.equal(val, value)) {
                                        out.println("found in " + obj.getHandle());
                                        break;
                                    }
                                    index += 4;
                                }
                                return false;
                            }
                        };
                    if (type.equals("heap")) {
                        VM.getVM().getObjectHeap().iterate(iterator);
                    } else {
                        VM.getVM().getObjectHeap().iteratePerm(iterator);
                    }
                } else if (type.equals("codecache")) {
                    CodeCacheVisitor v = new CodeCacheVisitor() {
                            public void prologue(Address start, Address end) {
                            }
                            public void visit(CodeBlob blob) {
                                boolean printed = false;
                                Address base = blob.getAddress();
                                Address end = base.addOffsetTo(blob.getSize());
                                while (base != null && base.lessThan(end)) {
                                    Address val = base.getAddressAt(0);
                                    if (AddressOps.equal(val, value)) {
                                        if (!printed) {
                                            printed = true;
                                            blob.printOn(out);
                                        }
                                        out.println("found at " + base + "\n");
                                    }
                                    base = base.addOffsetTo(stride);
                                }
                            }
                            public void epilogue() {
                            }


                        };
                    VM.getVM().getCodeCache().iterate(v);

                }
            }
        },
        new Command("dumpcodecache", "dumpcodecache", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 0) {
                    usage();
                } else {
                    final PrintStream fout = out;
                    final HTMLGenerator gen = new HTMLGenerator(false);
                    CodeCacheVisitor v = new CodeCacheVisitor() {
                            public void prologue(Address start, Address end) {
                            }
                            public void visit(CodeBlob blob) {
                                fout.println(gen.genHTML(blob.instructionsBegin()));
                            }
                            public void epilogue() {
                            }


                        };
                    VM.getVM().getCodeCache().iterate(v);
                }
            }
        },
        new Command("where", "where { -a | id }", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    String name = t.nextToken();
                    Threads threads = VM.getVM().getThreads();
                    boolean all = name.equals("-a");
                    for (JavaThread thread = threads.first(); thread != null; thread = thread.next()) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        thread.printThreadIDOn(new PrintStream(bos));
                        if (all || bos.toString().equals(name)) {
                            out.println(bos.toString() + " = " + thread.getAddress());
                            HTMLGenerator gen = new HTMLGenerator(false);
                            try {
                                out.println(gen.genHTMLForJavaStackTrace(thread));
                            } catch (Exception e) {
                                err.println("Error: " + e);
                                if (verboseExceptions) {
                                    e.printStackTrace(err);
                                }
                            }
                            if (!all) return;
                        }
                    }
                    if (!all) out.println("Couldn't find thread " + name);
                }
            }
        },
        new Command("thread", "thread { -a | id }", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    String name = t.nextToken();
                    Threads threads = VM.getVM().getThreads();
                    boolean all = name.equals("-a");
                    for (JavaThread thread = threads.first(); thread != null; thread = thread.next()) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        thread.printThreadIDOn(new PrintStream(bos));
                        if (all || bos.toString().equals(name)) {
                            out.println(bos.toString() + " = " + thread.getAddress());
                            if (!all) return;
                        }
                    }
                    out.println("Couldn't find thread " + name);
                }
            }
        },

        new Command("threads", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 0) {
                    usage();
                } else {
                    Threads threads = VM.getVM().getThreads();
                    for (JavaThread thread = threads.first(); thread != null; thread = thread.next()) {
                        thread.printThreadIDOn(out);
                        out.println(" " + thread.getThreadName());
                    }
                }
            }
        },

        new Command("livenmethods", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 0) {
                    usage();
                } else {
                    ArrayList nmethods = new ArrayList();
                    Threads threads = VM.getVM().getThreads();
                    HTMLGenerator gen = new HTMLGenerator(false);
                    for (JavaThread thread = threads.first(); thread != null; thread = thread.next()) {
                        try {
                            for (JavaVFrame vf = thread.getLastJavaVFrameDbg(); vf != null; vf = vf.javaSender()) {
                                if (vf instanceof CompiledVFrame) {
                                    NMethod c = ((CompiledVFrame)vf).getCode();
                                    if (!nmethods.contains(c)) {
                                        nmethods.add(c);
                                        out.println(gen.genHTML(c));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        },
        new Command("universe", false) {
            public void doit(Tokens t) {
                if (t.countTokens() != 0) {
                    usage();
                } else {
                    Universe u = VM.getVM().getUniverse();
                    out.println("Heap Parameters:");
                    u.heap().printOn(out);
                }
            }
        },
        new Command("verbose", "verbose true | false", true) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    verboseExceptions = Boolean.valueOf(t.nextToken()).booleanValue();
                }
            }
        },
        new Command("assert", "assert true | false", true) {
            public void doit(Tokens t) {
                if (t.countTokens() != 1) {
                    usage();
                } else {
                    Assert.ASSERTS_ENABLED = Boolean.valueOf(t.nextToken()).booleanValue();
                }
            }
        },
    };

    private boolean verboseExceptions = false;
    private ArrayList history = new ArrayList();
    private HashMap commands = new HashMap();
    private boolean doEcho = false;

    private Command findCommand(String key) {
        return (Command)commands.get(key);
    }

    public void printPrompt() {
        out.print("hsdb> ");
    }

    private DebuggerInterface debugger;
    private HotSpotAgent agent;
    private JSJavaScriptEngine jsengine;
    private BufferedReader in;
    private PrintStream out;
    private PrintStream err;

    // called before debuggee attach
    private void preAttach() {
        // nothing for now..
    }

    // called after debuggee attach
    private void postAttach() {
        // create JavaScript engine and start it
        jsengine = new JSJavaScriptEngine() {
                        private ObjectReader reader = new ObjectReader();
                        private JSJavaFactory factory = new JSJavaFactoryImpl();
                        public ObjectReader getObjectReader() {
                            return reader;
                        }
                        public JSJavaFactory getJSJavaFactory() {
                            return factory;
                        }
                        protected void quit() {
                            debugger.detach();
                            System.exit(0);
                        }
                        protected BufferedReader getInputReader() {
                            return in;
                        }
                        protected PrintStream getOutputStream() {
                            return out;
                        }
                        protected PrintStream getErrorStream() {
                            return err;
                        }
                   };
        try {
            jsengine.defineFunction(this,
                     this.getClass().getMethod("registerCommand",
                                new Class[] {
                                     String.class, String.class, String.class
                                }));
        } catch (NoSuchMethodException exp) {
            // should not happen, see below...!!
            exp.printStackTrace();
        }
        jsengine.start();
    }

    public void registerCommand(String cmd, String usage, final String func) {
        commands.put(cmd, new Command(cmd, usage, false) {
                              public void doit(Tokens t) {
                                  final int len = t.countTokens();
                                  Object[] args = new Object[len];
                                  for (int i = 0; i < len; i++) {
                                      args[i] = t.nextToken();
                                  }
                                  jsengine.call(func, args);
                              }
                          });
    }

    public void setOutput(PrintStream o) {
        out = o;
    }

    public void setErr(PrintStream e) {
        err = e;
    }

    public CommandProcessor(DebuggerInterface debugger, BufferedReader in, PrintStream out, PrintStream err) {
        this.debugger = debugger;
        this.agent = debugger.getAgent();
        this.in = in;
        this.out = out;
        this.err = err;
        for (int i = 0; i < commandList.length; i++) {
            Command c = commandList[i];
            commands.put(c.name, c);
        }
        if (debugger.isAttached()) {
            postAttach();
        }
    }


    public void run(boolean prompt) {
        // Process interactive commands.
        while (true) {
            if (prompt) printPrompt();
            String ln = null;
            try {
                ln = in.readLine();
            } catch (IOException e) {
            }
            if (ln == null) {
                if (prompt) err.println("Input stream closed.");
                return;
            }

            executeCommand(ln);
        }
    }

    static Pattern historyPattern = Pattern.compile("((!\\*)|(!\\$)|(!!-?)|(!-?[0-9][0-9]*)|(![a-zA-Z][^ ]*))");

    public void executeCommand(String ln) {
        if (ln.indexOf('!') != -1) {
            int size = history.size();
            if (size == 0) {
                ln = "";
                err.println("History is empty");
            } else {
                StringBuffer result = new StringBuffer();
                Matcher m = historyPattern.matcher(ln);
                int start = 0;
                while (m.find()) {
                    if (m.start() > start) {
                        result.append(ln.substring(start, m.start() - start));
                    }
                    start = m.end();

                    String cmd = m.group();
                    if (cmd.equals("!!")) {
                        result.append((String)history.get(history.size() - 1));
                    } else if (cmd.equals("!!-")) {
                        Tokens item = new Tokens((String)history.get(history.size() - 1));
                        item.trim(1);
                        result.append(item.join(" "));
                    } else if (cmd.equals("!*")) {
                        Tokens item = new Tokens((String)history.get(history.size() - 1));
                        item.nextToken();
                        result.append(item.join(" "));
                    } else if (cmd.equals("!$")) {
                        Tokens item = new Tokens((String)history.get(history.size() - 1));
                        result.append(item.at(item.countTokens() - 1));
                    } else {
                        String tail = cmd.substring(1);
                        switch (tail.charAt(0)) {
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                        case '-': {
                            int index = Integer.parseInt(tail);
                            if (index < 0) {
                                index = history.size() + index;
                            }
                            if (index > size) {
                                err.println("No such history item");
                            } else {
                                result.append((String)history.get(index));
                            }
                            break;
                        }
                        default: {
                            for (int i = history.size() - 1; i >= 0; i--) {
                                String s = (String)history.get(i);
                                if (s.startsWith(tail)) {
                                    result.append(s);
                                }
                            }
                        }
                        }
                    }
                }
                if (result.length() == 0) {
                    err.println("malformed history reference");
                    ln = "";
                } else {
                    if (start < ln.length()) {
                        result.append(ln.substring(start));
                    }
                    ln = result.toString();
                    if (!doEcho) {
                        out.println(ln);
                    }
                }
            }
        }

        if (doEcho) {
            out.println("+ " + ln);
        }

        PrintStream redirect = null;
        Tokens t = new Tokens(ln);
        if (t.hasMoreTokens()) {
            boolean error = false;
            history.add(ln);
            int len = t.countTokens();
            if (len > 2) {
                String r = t.at(len - 2);
                if (r.equals(">") || r.equals(">>")) {
                    boolean append = r.length() == 2;
                    String file = t.at(len - 1);
                    try {
                        redirect = new PrintStream(new BufferedOutputStream(new FileOutputStream(file, append)));
                        t.trim(2);
                    } catch (Exception e) {
                        out.println("Error: " + e);
                        if (verboseExceptions) {
                            e.printStackTrace(out);
                        }
                        error = true;
                    }
                }
            }
            if (!error) {
                PrintStream savedout = out;
                if (redirect != null) {
                    out = redirect;
                }
                try {
                    executeCommand(t);
                } catch (Exception e) {
                    err.println("Error: " + e);
                    if (verboseExceptions) {
                        e.printStackTrace(err);
                    }
                } finally {
                    if (redirect != null) {
                        out = savedout;
                        redirect.close();
                    }
                }
            }
        }
    }

    void executeCommand(Tokens args) {
        String cmd = args.nextToken();

        Command doit = findCommand(cmd);

        /*
         * Check for an unknown command
         */
        if (doit == null) {
            out.println("Unrecognized command.  Try help...");
        } else if (!debugger.isAttached() && !doit.okIfDisconnected) {
            out.println("Command not valid until the attached to a VM");
        } else {
            try {
                doit.doit(args);
            } catch (Exception e) {
                out.println("Error: " + e);
                if (verboseExceptions) {
                    e.printStackTrace(out);
                }
            }
        }
    }
}
