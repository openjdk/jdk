/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.Arrays;
import static java.util.stream.Collectors.joining;
import static jdk.jshell.Util.DOIT_METHOD_NAME;

/**
 * Wrapping of source into Java methods, fields, etc.  All but outer layer
 * wrapping with imports and class.
 *
 * @author Robert Field
 */
abstract class Wrap implements GeneralWrap {

    private static Wrap methodWrap(String prefix, String source, String suffix) {
        Wrap wunit = new NoWrap(source);
        return new DoitMethodWrap(new CompoundWrap(prefix, wunit, suffix));
    }

    public static Wrap methodWrap(String source) {
        return methodWrap("", source, semi(source) + "        return null;\n");
    }

    public static Wrap methodReturnWrap(String source) {
        return methodWrap("return ", source, semi(source));
    }

    public static Wrap methodUnreachableSemiWrap(String source) {
        return methodWrap("", source, semi(source));
    }

    public static Wrap methodUnreachableWrap(String source) {
        return methodWrap("", source, "");
    }

    private static String indent(int n) {
        return "                              ".substring(0, n * 4);
    }

    private static String nlindent(int n) {
        return "\n" + indent(n);
    }

    /**
     *
     * @param in
     * @param rname
     * @param rinit Initializer or null
     * @param rdecl Type name and name
     * @return
     */
    public static Wrap varWrap(String source, Wrap wtype, String brackets,
                               Range rname, Wrap winit, Wrap anonDeclareWrap) {
        RangeWrap wname = new RangeWrap(source, rname);
        Wrap wVarDecl = new VarDeclareWrap(wtype, brackets, wname);
        Wrap wmeth;

        if (winit == null) {
            wmeth = new CompoundWrap(new NoWrap(" "), "   return null;\n");
        } else {
        // int x = y
            // int x_ = y; return x = x_;
            // decl + "_ = " + init ; + "return " + name + "=" + name + "_ ;"
            wmeth = new CompoundWrap(
                    wtype, brackets + " ", wname, "_ =\n        ", winit, semi(winit),
                    "        return ", wname, " = ", wname, "_;\n"
            );
        }
        Wrap wInitMeth = new DoitMethodWrap(wmeth);
        return anonDeclareWrap != null ? new CompoundWrap(wVarDecl, wInitMeth, anonDeclareWrap)
                                       : new CompoundWrap(wVarDecl, wInitMeth);
    }

    public static Wrap tempVarWrap(String source, String typename, String name) {
        RangeWrap winit = new NoWrap(source);
        // y
        // return $1 = y;
        // "return " + $1 + "=" + init ;
        Wrap wmeth = new CompoundWrap("return " + name + " =\n        ", winit, semi(winit));
        Wrap wInitMeth = new DoitMethodWrap(wmeth);

        String varDecl = "    public static\n    " + typename + " " + name + ";\n";
        return new CompoundWrap(varDecl, wInitMeth);
    }

    public static Wrap simpleWrap(String source) {
        return new NoWrap(source);
    }

    public static Wrap identityWrap(String source) {
        return new NoWrap(source);
    }

    public static Wrap rangeWrap(String source, Range range) {
        return new RangeWrap(source, range);
    }

    public static Wrap classMemberWrap(String source) {
        Wrap w = new NoWrap(source);
        return new CompoundWrap("    public static\n    ", w);
    }

    private static int countLines(String s) {
        return countLines(s, 0, s.length());
    }

    private static int countLines(String s, int from, int toEx) {
        int cnt = 0;
        int idx = from;
        while ((idx = s.indexOf('\n', idx)) > 0) {
            if (idx >= toEx) break;
            ++cnt;
            ++idx;
        }
        return cnt;
    }

    public static final class Range {
        final int begin;
        final int end;

        Range(int begin, int end) {
            this.begin = begin;
            this.end = end;
        }

        Range(String s) {
            this.begin = 0;
            this.end = s.length();
        }

        String part(String s) {
            return s.substring(begin, end);
        }

        int length() {
            return end - begin;
        }

        boolean isEmpty() {
            return end == begin;
        }

        void verify(String s) {
            if (begin < 0 || end <= begin || end > s.length()) {
                throw new InternalError("Bad Range: " + s + "[" + begin + "," + end + "]");
            }
        }

        @Override
        public String toString() {
            return "Range[" + begin + "," + end + "]";
        }
    }

    public static class CompoundWrap extends Wrap {

        final Object[] os;
        final String wrapped;
        final int snidxFirst;
        final int snidxLast;
        final int snlineFirst;
        final int snlineLast;

        CompoundWrap(Object... os) {
            this.os = os;
            int sniFirst = Integer.MAX_VALUE;
            int sniLast = Integer.MIN_VALUE;
            int snlnFirst = Integer.MAX_VALUE;
            int snlnLast = Integer.MIN_VALUE;
            StringBuilder sb = new StringBuilder();
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    sb.append(s);
                } else if (o instanceof Wrap) {
                    Wrap w = (Wrap) o;
                    if (w.firstSnippetIndex() < sniFirst) {
                        sniFirst = w.firstSnippetIndex();
                    }
                    if (w.lastSnippetIndex() > sniLast) {
                        sniLast = w.lastSnippetIndex();
                    }
                    if (w.firstSnippetLine() < snlnFirst) {
                        snlnFirst = w.firstSnippetLine();
                    }
                    if (w.lastSnippetLine() > snlnLast) {
                        snlnLast = w.lastSnippetLine();
                    }
                    sb.append(w.wrapped());
                } else {
                    throw new InternalError("Bad object in CommoundWrap: " + o);
                }
            }
            this.wrapped = sb.toString();
            this.snidxFirst = sniFirst;
            this.snidxLast = sniLast;
            this.snlineFirst = snlnFirst;
            this.snlineLast = snlnLast;
        }

        @Override
        public String wrapped() {
            return wrapped;
        }

        @Override
        public int snippetIndexToWrapIndex(int sni) {
            int before = 0;
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    before += s.length();
                } else if (o instanceof Wrap) {
                    Wrap w = (Wrap) o;
                    if (sni >= w.firstSnippetIndex() && sni <= w.lastSnippetIndex()) {
                        return w.snippetIndexToWrapIndex(sni) + before;
                    }
                    before += w.wrapped().length();
                }
            }
            return 0;
        }

        Wrap wrapIndexToWrap(long wi) {
            int before = 0;
            Wrap w = null;
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    before += s.length();
                } else if (o instanceof Wrap) {
                    w = (Wrap) o;
                    int len = w.wrapped().length();
                    if ((wi - before) <= len) {
                        //System.err.printf("Defer to wrap %s - wi: %d. before; %d   -- %s  >>> %s\n",
                        //        w, wi, before, w.debugPos(wi - before), w.wrapped());
                        return w;
                    }
                    before += len;
                }
            }
            return w;
        }

        @Override
        public int wrapIndexToSnippetIndex(int wi) {
            int before = 0;
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    before += s.length();
                } else if (o instanceof Wrap) {
                    Wrap w = (Wrap) o;
                    int len = w.wrapped().length();
                    if ((wi - before) <= len) {
                        //System.err.printf("Defer to wrap %s - wi: %d. before; %d   -- %s  >>> %s\n",
                        //        w, wi, before, w.debugPos(wi - before), w.wrapped());
                        return w.wrapIndexToSnippetIndex(wi - before);
                    }
                    before += len;
                }
            }
            return lastSnippetIndex();
        }

        @Override
        public int firstSnippetIndex() {
            return snidxFirst;
        }

        @Override
        public int lastSnippetIndex() {
            return snidxLast;
        }

        @Override
        public int snippetLineToWrapLine(int snline) {
            int before = 0;
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    before += countLines(s);
                } else if (o instanceof Wrap) {
                    Wrap w = (Wrap) o;
                    if (snline >= w.firstSnippetLine() && snline <= w.lastSnippetLine()) {
                        return w.snippetLineToWrapLine(snline) + before;
                    }
                    before += countLines(w.wrapped());
                }
            }
            return 0;
        }

        Wrap wrapLineToWrap(int wline) {
            int before = 0;
            Wrap w = null;
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    before += countLines(s);
                } else if (o instanceof Wrap) {
                    w = (Wrap) o;
                    int lns = countLines(w.wrapped());
                    if ((wline - before) < lns) {
                        return w;
                    }
                    before += lns;
                }
            }
            return w;
        }

        @Override
        public int wrapLineToSnippetLine(int wline) {
            int before = 0;
            for (Object o : os) {
                if (o instanceof String) {
                    String s = (String) o;
                    before += countLines(s);
                } else if (o instanceof Wrap) {
                    Wrap w = (Wrap) o;
                    int lns = countLines(w.wrapped());
                    if ((wline - before) < lns) {
                        return w.wrapLineToSnippetLine(wline - before);
                    }
                    before += lns;
                }
            }
            return 0;
        }

        @Override
        public int firstSnippetLine() {
            return snlineFirst;
        }

        @Override
        public int lastSnippetLine() {
            return snlineLast;
        }

        @Override
        public String toString() {
            return "CompoundWrap(" + Arrays.stream(os).map(Object::toString).collect(joining(",")) + ")";
        }
    }

    private static class RangeWrap extends Wrap {

        final Range range;
        final String wrapped;
        final int firstSnline;
        final int lastSnline;

        RangeWrap(String snippetSource, Range usedWithinSnippet) {
            this.range = usedWithinSnippet;
            this.wrapped = usedWithinSnippet.part(snippetSource);
            usedWithinSnippet.verify(snippetSource);
            this.firstSnline = countLines(snippetSource, 0, range.begin);
            this.lastSnline = firstSnline + countLines(snippetSource, range.begin, range.end);
        }

        @Override
        public String wrapped() {
            return wrapped;
        }

        @Override
        public int snippetIndexToWrapIndex(int sni) {
            if (sni < range.begin) {
                return 0;
            }
            if (sni > range.end) {
                return range.length();
            }
            return sni - range.begin;
        }

        @Override
        public int wrapIndexToSnippetIndex(int wi) {
            if (wi < 0) {
                return 0; // bad index
            }
            int max = range.length();
            if (wi > max) {
                wi = max;
            }
            return wi + range.begin;
        }

        @Override
        public int firstSnippetIndex() {
            return range.begin;
        }

        @Override
        public int lastSnippetIndex() {
            return range.end;
        }

        @Override
        public int snippetLineToWrapLine(int snline) {
            if (snline < firstSnline) {
                return 0;
            }
            if (snline >= lastSnline) {
                return lastSnline - firstSnline;
            }
            return snline - firstSnline;
        }

        @Override
        public int wrapLineToSnippetLine(int wline) {
            if (wline < 0) {
                return 0; // bad index
            }
            int max = lastSnline - firstSnline;
            if (wline > max) {
                wline = max;
            }
            return wline + firstSnline;
        }

        @Override
        public int firstSnippetLine() {
            return firstSnline;
        }

        @Override
        public int lastSnippetLine() {
            return lastSnline;
        }

        @Override
        public String toString() {
            return "RangeWrap(" + range + ")";
        }
    }

    private static class NoWrap extends RangeWrap {

        NoWrap(String unit) {
            super(unit, new Range(unit));
        }
    }

    private static String semi(Wrap w) {
        return semi(w.wrapped());
    }

    private static String semi(String s) {
        return ((s.endsWith(";")) ? "\n" : ((s.endsWith(";\n")) ? "" : ";\n"));
    }

    private static class DoitMethodWrap extends CompoundWrap {

        DoitMethodWrap(Wrap w) {
            super("    public static Object " + DOIT_METHOD_NAME + "() throws Throwable {\n"
                    + "        ", w,
                    "    }\n");
        }
    }

    private static class VarDeclareWrap extends CompoundWrap {

        VarDeclareWrap(Wrap wtype, String brackets, Wrap wname) {
            super("    public static ", wtype, brackets + " ", wname, semi(wname));
        }
    }
}
