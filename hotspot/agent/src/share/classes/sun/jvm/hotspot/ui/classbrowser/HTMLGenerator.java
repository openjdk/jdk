/*
 * Copyright 2002-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.ui.classbrowser;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.asm.*;
import sun.jvm.hotspot.asm.sparc.*;
import sun.jvm.hotspot.asm.x86.*;
import sun.jvm.hotspot.asm.ia64.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.compiler.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.interpreter.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.tools.jcore.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class HTMLGenerator implements /* imports */ ClassConstants {
    static class Formatter {
        boolean html;
        StringBuffer buf = new StringBuffer();

        Formatter(boolean h) {
            html = h;
        }

        void append(String s) {
            buf.append(s);
        }

        void append(int s) {
            buf.append(s);
        }

        void append(char s) {
            buf.append(s);
        }

        void append(StringBuffer s) {
            buf.append(s);
        }

        void append(Formatter s) {
            buf.append(s);
        }

        StringBuffer getBuffer() {
            return buf;
        }

        public String toString() {
            return buf.toString();
        }

        void wrap(String tag, String text) {
            wrap(tag, tag, text);
        }
        void wrap(String before, String after, String text) {
            beginTag(before);
            append(text);
            endTag(after);
        }

        // header tags
        void h1(String s) { nl(); wrap("h1", s); nl(); }
        void h2(String s) { nl(); wrap("h2", s); nl(); }
        void h3(String s) { nl(); wrap("h3", s); nl(); }
        void h4(String s) { nl(); wrap("h4", s); nl(); }

        // list tags
        void beginList()  { beginTag("ul"); nl(); }
        void endList()    { endTag("ul"); nl();   }
        void beginListItem() { beginTag("li"); }
        void endListItem()   { endTag("li"); nl();   }
        void li(String s) { wrap("li", s); nl();  }

        // table tags
        void beginTable(int border) {
            beginTag("table border='" + border + "'");
        }
        void cell(String s) { wrap("td", s); }
        void headerCell(String s) { wrap("th", s); }
        void endTable() { endTag("table"); }

        void link(String href, String text) {
            wrap("a href='" + href + "'", "a", text);
        }
        void beginTag(String s) {
            if (html) { append("<"); append(s); append(">"); }
        }
        void endTag(String s) {
            if (html) {
               append("</"); append(s); append(">");
            } else {
               if (s.equals("table") || s.equals("tr")) {
                  nl();
               }
               if (s.equals("td") || s.equals("th")) {
                  append(" ");
               }
            }
        }
        void bold(String s) {
            wrap("b", s);
        }

        void nl() {
            if (!html) buf.append("\n");
        }

        void br() {
            if (html) append("<br>");
            else      append("\n");
        }
        void genEmptyHTML() {
            if (html) append("<html></html>");
        }

        void genHTMLPrologue() {
            if (html) append("<html><body>");
        }

        void genHTMLPrologue(String title) {
            if (html) {
                append("<html><head><title>");
                append(title);
                append("</title></head>");
                append("<body>");
            }
            h2(title);
        }
        void genHTMLEpilogue() {
            if (html) append("</body></html>");
        }

    }

   private static final String DUMP_KLASS_OUTPUT_DIR = ".";
   private static final int NATIVE_CODE_SIZE = 200;
   private final String spaces;
   private final String tab;

   private boolean genHTML = true;

   public HTMLGenerator() {
       this(true);
   }

   public HTMLGenerator(boolean html) {
       genHTML = html;
       if (html) {
           spaces = "&nbsp;&nbsp;";
           tab = "&nbsp;&nbsp;&nbsp;&nbsp;";
       } else {
           spaces = "  ";
           tab = "    ";
       }
    }

   private static CPUHelper cpuHelper;
   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize();
         }
      });
   }

   private static synchronized void initialize() {
      String cpu = VM.getVM().getCPU();
      if (cpu.equals("sparc")) {
         cpuHelper = new SPARCHelper();
      } else if (cpu.equals("x86")) {
         cpuHelper = new X86Helper();
      } else if (cpu.equals("ia64")) {
         cpuHelper = new IA64Helper();
      } else {
          throw new RuntimeException("cpu '" + cpu + "' is not yet supported!");
      }
   }

   protected static synchronized CPUHelper getCPUHelper() {
      return cpuHelper;
   }

   protected String escapeHTMLSpecialChars(String value) {
      if (!genHTML) return value;

      Formatter buf = new Formatter(genHTML);
      int len = value.length();
      for (int i=0; i < len; i++) {
         char c = value.charAt(i);
         switch (c) {
            case '<':
               buf.append("&lt;");
               break;
            case '>':
               buf.append("&gt;");
               break;
            case '&':
               buf.append("&amp;");
               break;
            default:
               buf.append(c);
               break;
         }
      }
      return buf.toString();
   }

   public String genHTMLForMessage(String message) {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue(message);
      buf.genHTMLEpilogue();
      return buf.toString();
   }

   public String genHTMLErrorMessage(Exception exp) {
      exp.printStackTrace();
      return genHTMLForMessage(exp.getClass().getName() + " : " + exp.getMessage());
   }

   public String genHTMLForWait(String message) {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue("Please wait ..");
      buf.h2(message);
      return buf.toString();
   }

   protected String genKlassTitle(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      AccessFlags acc = klass.getAccessFlagsObj();
      if (acc.isPublic()) {
         buf.append("public ");
      } else if (acc.isProtected()) {
         buf.append("protected ");
      } else if (acc.isPrivate()) {
         buf.append("private ");
      }

      if (acc.isStatic()) {
         buf.append("static ");
      }

      if (acc.isAbstract() ) {
         buf.append("abstract ");
      } else if (acc.isFinal()) {
         buf.append("final ");
      }

      if (acc.isStrict()) {
         buf.append("strict ");
      }

      // javac generated flags
      if (acc.isEnum()) {
         buf.append("[enum] ");
      }
      if (acc.isSynthetic()) {
         buf.append("[synthetic] ");
      }

      if (klass.isInterface()) {
         buf.append("interface");
      } else {
         buf.append("class");
      }

      buf.append(' ');
      buf.append(klass.getName().asString().replace('/', '.'));
      // is it generic?
      Symbol genSig = klass.getGenericSignature();
      if (genSig != null) {
         buf.append(" [signature ");
         buf.append(escapeHTMLSpecialChars(genSig.asString()));
         buf.append("] ");
      } else {
         buf.append(' ');
      }
      buf.append('@');
      buf.append(klass.getHandle().toString());
      return buf.toString();
   }

   protected String genBaseHref() {
      return "";
   }

   protected String genKlassHref(InstanceKlass klass) {
      return genBaseHref() + "klass=" + klass.getHandle();
   }

   protected String genKlassLink(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      buf.link(genKlassHref(klass), genKlassTitle(klass));
      return buf.toString();
   }

   protected String genMethodModifierString(AccessFlags acc) {
      Formatter buf = new Formatter(genHTML);
      if (acc.isPrivate()) {
         buf.append("private ");
      } else if (acc.isProtected()) {
         buf.append("protected ");
      } else if (acc.isPublic()) {
         buf.append("public ");
      }

      if (acc.isStatic()) {
         buf.append("static ");
      } else if (acc.isAbstract() ) {
         buf.append("abstract ");
      } else if (acc.isFinal()) {
         buf.append("final ");
      }

       if (acc.isNative()) {
         buf.append("native ");
      }

      if (acc.isStrict()) {
         buf.append("strict ");
      }

      if (acc.isSynchronized()) {
         buf.append("synchronized ");
      }

      // javac generated flags
      if (acc.isBridge()) {
         buf.append("[bridge] ");
      }

      if (acc.isSynthetic()) {
         buf.append("[synthetic] ");
      }

      if (acc.isVarArgs()) {
         buf.append("[varargs] ");
      }

      return buf.toString();
   }

   protected String genMethodNameAndSignature(Method method) {
      Formatter buf = new Formatter(genHTML);
      buf.append(genMethodModifierString(method.getAccessFlagsObj()));
      Symbol sig = method.getSignature();
      new SignatureConverter(sig, buf.getBuffer()).iterateReturntype();
      buf.append(" ");
      String methodName = method.getName().asString();
      buf.append(escapeHTMLSpecialChars(methodName));
      buf.append('(');
      new SignatureConverter(sig, buf.getBuffer()).iterateParameters();
      buf.append(')');
      // is it generic?
      Symbol genSig = method.getGenericSignature();
      if (genSig != null) {
         buf.append(" [signature ");
         buf.append(escapeHTMLSpecialChars(genSig.asString()));
         buf.append("] ");
      }
      return buf.toString().replace('/', '.');
   }

   protected String genMethodTitle(Method method) {
      Formatter buf = new Formatter(genHTML);
      buf.append(genMethodNameAndSignature(method));
      buf.append(' ');
      buf.append('@');
      buf.append(method.getHandle().toString());
      return buf.toString();
   }

   protected String genMethodHref(Method m) {
      return genBaseHref() + "method=" + m.getHandle();
   }

   protected String genMethodLink(Method m) {
      Formatter buf = new Formatter(genHTML);
      buf.link(genMethodHref(m), genMethodTitle(m));
      return buf.toString();
   }

   protected String genMethodAndKlassLink(Method m) {
      Formatter buf = new Formatter(genHTML);
      buf.append(genMethodLink(m));
      buf.append(" of ");
      buf.append(genKlassLink((InstanceKlass) m.getMethodHolder()));
      return buf.toString();
   }

   protected String genNMethodHref(NMethod nm) {
      return genBaseHref() + "nmethod=" + nm.getAddress();
   }

   public String genNMethodTitle(NMethod nmethod) {
      Formatter buf = new Formatter(genHTML);
      Method m = nmethod.getMethod();

      buf.append("Disassembly for compiled method [");
      buf.append(genMethodTitle(m));
      buf.append(" ] ");
      buf.append('@');
      buf.append(nmethod.getAddress().toString());
      return buf.toString();
   }

   protected String genNMethodLink(NMethod nm) {
      Formatter buf = new Formatter(genHTML);
      buf.link(genNMethodHref(nm), genNMethodTitle(nm));
      return buf.toString();
   }

   public String genCodeBlobTitle(CodeBlob blob) {
      Formatter buf = new Formatter(genHTML);
      buf.append("Disassembly for code blob " + blob.getName() + " [");
      buf.append(blob.getClass().getName());
      buf.append(" ] @");
      buf.append(blob.getAddress().toString());
      return buf.toString();
   }

   protected BytecodeDisassembler createBytecodeDisassembler(Method m) {
      return new BytecodeDisassembler(m);
   }

   private String genLowHighShort(int val) {
      Formatter buf = new Formatter(genHTML);
      buf.append('#');
      buf.append(Integer.toString(val & 0xFFFF));
      buf.append(" #");
      buf.append(Integer.toString((val >> 16) & 0xFFFF));
      return buf.toString();
   }

   protected String genHTMLTableForConstantPool(ConstantPool cpool) {
      Formatter buf = new Formatter(genHTML);
      buf.beginTable(1);

      buf.beginTag("tr");
      buf.headerCell("Index");
      buf.headerCell("Constant Type");
      buf.headerCell("Constant Value");
      buf.endTag("tr");

      final int length = (int) cpool.getLength();
      // zero'th pool entry is always invalid. ignore it.
      for (int index = 1; index < length; index++) {
         buf.beginTag("tr");
         buf.cell(Integer.toString(index));

         int ctag = (int) cpool.getTags().getByteAt((int) index);
         switch (ctag) {
            case JVM_CONSTANT_Integer:
               buf.cell("JVM_CONSTANT_Integer");
               buf.cell(Integer.toString(cpool.getIntAt(index)));
               break;

            case JVM_CONSTANT_Float:
               buf.cell("JVM_CONSTANT_Float");
               buf.cell(Float.toString(cpool.getFloatAt(index)));
               break;

            case JVM_CONSTANT_Long:
               buf.cell("JVM_CONSTANT_Long");
               buf.cell(Long.toString(cpool.getLongAt(index)));
               // long entries occupy two slots
               index++;
               break;

            case JVM_CONSTANT_Double:
               buf.cell("JVM_CONSTANT_Double");
               buf.cell(Double.toString(cpool.getDoubleAt(index)));
               // double entries occupy two slots
               index++;
               break;

            case JVM_CONSTANT_UnresolvedClass:
               buf.cell("JVM_CONSTANT_UnresolvedClass");
               buf.cell(cpool.getSymbolAt(index).asString());
               break;

            case JVM_CONSTANT_UnresolvedClassInError:
               buf.cell("JVM_CONSTANT_UnresolvedClassInError");
               buf.cell(cpool.getSymbolAt(index).asString());
               break;

            case JVM_CONSTANT_Class:
               buf.cell("JVM_CONSTANT_Class");
               Klass klass = (Klass) cpool.getObjAt(index);
               if (klass instanceof InstanceKlass) {
                  buf.cell(genKlassLink((InstanceKlass) klass));
               } else {
                  buf.cell(klass.getName().asString().replace('/', '.'));
               }
               break;

            case JVM_CONSTANT_UnresolvedString:
               buf.cell("JVM_CONSTANT_UnresolvedString");
               buf.cell("\"" +
                 escapeHTMLSpecialChars(cpool.getSymbolAt(index).asString()) +
                 "\"");
               break;

            case JVM_CONSTANT_Utf8:
               buf.cell("JVM_CONSTANT_Utf8");
               buf.cell("\"" +
                 escapeHTMLSpecialChars(cpool.getSymbolAt(index).asString()) +
                 "\"");
               break;

            case JVM_CONSTANT_String:
               buf.cell("JVM_CONSTANT_String");
               buf.cell("\"" +
                 escapeHTMLSpecialChars(OopUtilities.stringOopToString(cpool.getObjAt(index))) + "\"");
               break;

            case JVM_CONSTANT_Fieldref:
               buf.cell("JVM_CONSTANT_Fieldref");
               buf.cell(genLowHighShort(cpool.getIntAt(index)));
               break;

            case JVM_CONSTANT_Methodref:
               buf.cell("JVM_CONSTANT_Methodref");
               buf.cell(genLowHighShort(cpool.getIntAt(index)));
               break;

            case JVM_CONSTANT_InterfaceMethodref:
               buf.cell("JVM_CONSTANT_InterfaceMethodref");
               buf.cell(genLowHighShort(cpool.getIntAt(index)));
               break;

            case JVM_CONSTANT_NameAndType:
               buf.cell("JVM_CONSTANT_NameAndType");
               buf.cell(genLowHighShort(cpool.getIntAt(index)));
               break;

            case JVM_CONSTANT_ClassIndex:
               buf.cell("JVM_CONSTANT_ClassIndex");
               buf.cell(Integer.toString(cpool.getIntAt(index)));
               break;

            case JVM_CONSTANT_StringIndex:
               buf.cell("JVM_CONSTANT_StringIndex");
               buf.cell(Integer.toString(cpool.getIntAt(index)));
               break;

            default:
               throw new InternalError("unknown tag: " + ctag);
         }

         buf.endTag("tr");
      }

      buf.endTable();
      return buf.toString();
   }

   public String genHTML(ConstantPool cpool) {
      try {
         Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue(genConstantPoolTitle(cpool));
         buf.h3("Holder Class");
         buf.append(genKlassLink((InstanceKlass) cpool.getPoolHolder()));
         buf.h3("Constants");
         buf.append(genHTMLTableForConstantPool(cpool));
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genConstantPoolHref(ConstantPool cpool) {
      return genBaseHref() + "cpool=" + cpool.getHandle();
   }

   protected String genConstantPoolTitle(ConstantPool cpool) {
      Formatter buf = new Formatter(genHTML);
      buf.append("Constant Pool of [");
      buf.append(genKlassTitle((InstanceKlass) cpool.getPoolHolder()));
      buf.append("] @");
      buf.append(cpool.getHandle().toString());
      return buf.toString();
   }

   protected String genConstantPoolLink(ConstantPool cpool) {
      Formatter buf = new Formatter(genHTML);
      buf.link(genConstantPoolHref(cpool), genConstantPoolTitle(cpool));
      return buf.toString();
   }

   public String genHTML(Method method) {
      try {
         final Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue(genMethodTitle(method));

         buf.h3("Holder Class");
         buf.append(genKlassLink((InstanceKlass) method.getMethodHolder()));

         NMethod nmethod = method.getNativeMethod();
         if (nmethod != null) {
            buf.h3("Compiled Code");
            buf.append(genNMethodLink(nmethod));
         }

         boolean hasThrows = method.hasCheckedExceptions();
         ConstantPool cpool = ((InstanceKlass) method.getMethodHolder()).getConstants();
         if (hasThrows) {
            buf.h3("Checked Exception(s)");
            CheckedExceptionElement[] exceptions = method.getCheckedExceptions();
            buf.beginTag("ul");
            for (int exp = 0; exp < exceptions.length; exp++) {
               short cpIndex = (short) exceptions[exp].getClassCPIndex();
               Oop obj = cpool.getObjAt(cpIndex);
               if (obj instanceof Symbol) {
                  buf.li(((Symbol)obj).asString().replace('/', '.'));
               } else {
                  buf.li(genKlassLink((InstanceKlass)obj));
               }
            }
            buf.endTag("ul");
         }

         if (method.isNative() || method.isAbstract()) {
           buf.genHTMLEpilogue();
           return buf.toString();
         }

         buf.h3("Bytecode");
         BytecodeDisassembler disasm = createBytecodeDisassembler(method);
         final boolean hasLineNumbers = method.hasLineNumberTable();
         disasm.decode(new BytecodeVisitor() {
                          private Method method;
                          public void prologue(Method m) {
                             method = m;
                             buf.beginTable(0);
                             buf.beginTag("tr");
                             if (hasLineNumbers) {
                                buf.headerCell("line");
                             }
                             buf.headerCell("bci" + spaces);
                             buf.headerCell("bytecode");
                             buf.endTag("tr");
                          }

                          public void visit(Bytecode instr) {
                             int curBci = instr.bci();
                             buf.beginTag("tr");
                             if (hasLineNumbers) {
                                int lineNumber = method.getLineNumberFromBCI(curBci);
                                buf.cell(Integer.toString(lineNumber) + spaces);
                             }
                             buf.cell(Integer.toString(curBci) + spaces);

                             buf.beginTag("td");
                             String instrStr = null;
                             try {
                                 instrStr = escapeHTMLSpecialChars(instr.toString());
                             } catch (RuntimeException re) {
                                 buf.append("exception during bytecode processing");
                                 buf.endTag("td");
                                 buf.endTag("tr");
                                 re.printStackTrace();
                                 return;
                             }

                             if (instr instanceof BytecodeNew) {
                                BytecodeNew newBytecode = (BytecodeNew) instr;
                                InstanceKlass klass = newBytecode.getNewKlass();
                                if (klass != null) {
                                    buf.link(genKlassHref(klass), instrStr);
                                } else {
                                    buf.append(instrStr);
                                }
                             } else if(instr instanceof BytecodeInvoke) {
                                BytecodeInvoke invokeBytecode = (BytecodeInvoke) instr;
                                Method m = invokeBytecode.getInvokedMethod();
                                if (m != null) {
                                   buf.link(genMethodHref(m), instrStr);
                                   buf.append(" of ");
                                   InstanceKlass klass = (InstanceKlass) m.getMethodHolder();
                                   buf.link(genKlassHref(klass), genKlassTitle(klass));
                                } else {
                                   buf.append(instrStr);
                                }
                             } else if (instr instanceof BytecodeGetPut) {
                                BytecodeGetPut getPut = (BytecodeGetPut) instr;
                                sun.jvm.hotspot.oops.Field f = getPut.getField();
                                buf.append(instrStr);
                                if (f != null) {
                                   InstanceKlass klass = f.getFieldHolder();
                                   buf.append(" of ");
                                   buf.link(genKlassHref(klass), genKlassTitle(klass));
                                }
                             } else if (instr instanceof BytecodeLoadConstant) {
                                BytecodeLoadConstant ldc = (BytecodeLoadConstant) instr;
                                if (ldc.isKlassConstant()) {
                                   Oop oop = ldc.getKlass();
                                   if (oop instanceof Klass) {
                                      buf.append("<a href='");
                                      buf.append(genKlassHref((InstanceKlass) oop));
                                      buf.append("'>");
                                      buf.append(instrStr);
                                      buf.append("</a>");
                                   } else {
                                      // unresolved klass literal
                                      buf.append(instrStr);
                                   }
                                } else {
                                   // not a klass literal
                                   buf.append(instrStr);
                                }
                             } else {
                                buf.append(instrStr);
                             }
                             buf.endTag("td");
                             buf.endTag("tr");
                          }

                          public void epilogue() {
                             buf.endTable();
                          }
                       });

         // display exception table for this method
         TypeArray exceptionTable = method.getExceptionTable();
         // exception table is 4 tuple array of shorts
         int numEntries = (int)exceptionTable.getLength() / 4;
         if (numEntries != 0) {
            buf.h4("Exception Table");
            buf.beginTable(1);
            buf.beginTag("tr");
            buf.headerCell("start bci");
            buf.headerCell("end bci");
            buf.headerCell("handler bci");
            buf.headerCell("catch type");
            buf.endTag("tr");

            for (int e = 0; e < numEntries; e += 4) {
               buf.beginTag("tr");
               buf.cell(Integer.toString(exceptionTable.getIntAt(e)));
               buf.cell(Integer.toString(exceptionTable.getIntAt(e + 1)));
               buf.cell(Integer.toString(exceptionTable.getIntAt(e + 2)));
               short cpIndex = (short) exceptionTable.getIntAt(e + 3);
               Oop obj = cpIndex == 0? null : cpool.getObjAt(cpIndex);
               if (obj == null) {
                  buf.cell("Any");
               } else if (obj instanceof Symbol) {
                  buf.cell(((Symbol)obj).asString().replace('/', '.'));
               } else {
                  buf.cell(genKlassLink((InstanceKlass)obj));
               }
               buf.endTag("tr");
            }

            buf.endTable();
         }

         // display constant pool hyperlink
         buf.h3("Constant Pool");
         buf.append(genConstantPoolLink(cpool));
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected Disassembler createDisassembler(long startPc, byte[] code) {
      return getCPUHelper().createDisassembler(startPc, code);
   }

   protected SymbolFinder createSymbolFinder() {
      return new DummySymbolFinder();
   }

   // genHTML for a given address. Address may be a PC or
   // methodOop or klassOop.

   public String genHTMLForAddress(String addrStr) {
      return genHTML(parseAddress(addrStr));
   }

   public String genHTML(sun.jvm.hotspot.debugger.Address pc) {
      CodeBlob blob = null;

      try {
         blob = (CodeBlob)VM.getVM().getCodeCache().findBlobUnsafe(pc);
      } catch (Exception exp) {
         // ignore
      }

      if (blob != null) {
         if (blob instanceof NMethod) {
            return genHTML((NMethod)blob);
         } else {
            // may be interpreter code.
            Interpreter interp = VM.getVM().getInterpreter();
            if (interp.contains(pc)) {
               InterpreterCodelet codelet = interp.getCodeletContaining(pc);
               if (codelet == null) {
                  return "Unknown location in the Interpreter: " + pc;
               }
               return genHTML(codelet);
            }
            return genHTML(blob);
         }
      } else if (VM.getVM().getCodeCache().contains(pc)) {
         return "Unknown location in the CodeCache: " + pc;
      }

      // did not find nmethod.
      // try methodOop, klassOop and constantPoolOop.
      try {
         Oop obj = getOopAtAddress(pc);
         if (obj != null) {
            if (obj instanceof Method) {
               return genHTML((Method) obj);
            } else if (obj instanceof InstanceKlass) {
               return genHTML((InstanceKlass) obj);
            } else if (obj instanceof ConstantPool) {
               return genHTML((ConstantPool) obj);
            }
         }
      } catch (Exception exp) {
         // ignore
      }

      // didn't find any. do raw disassembly.
      return genHTMLForRawDisassembly(pc, null);
   }

   protected byte[] readBuffer(sun.jvm.hotspot.debugger.Address addr, int size) {
      byte[] buf = new byte[size];
      for (int b = 0; b < size; b++) {
         buf[b] = (byte) addr.getJByteAt(b);
      }
      return buf;
   }

    public String genHTMLForRawDisassembly(sun.jvm.hotspot.debugger.Address startPc, int size) {
      try {
         return genHTMLForRawDisassembly(startPc, null, readBuffer(startPc, size));
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genHTMLForRawDisassembly(sun.jvm.hotspot.debugger.Address startPc,
                                             String prevPCs) {
      try {
         return genHTMLForRawDisassembly(startPc, prevPCs, readBuffer(startPc, NATIVE_CODE_SIZE));
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genPCHref(long targetPc) {
      return genBaseHref() + "pc=0x" + Long.toHexString(targetPc);
   }

   protected String genMultPCHref(String pcs) {
      StringBuffer buf = new StringBuffer(genBaseHref());
      buf.append("pc_multiple=");
      buf.append(pcs);
      return buf.toString();
   }

   protected String genPCHref(long currentPc, sun.jvm.hotspot.asm.Address addr) {
      String href = null;
      if (addr instanceof PCRelativeAddress) {
         PCRelativeAddress pcRelAddr = (PCRelativeAddress) addr;
         href = genPCHref(currentPc + pcRelAddr.getDisplacement());
      } else if(addr instanceof DirectAddress) {
         href =  genPCHref(((DirectAddress) addr).getValue());
      }

      return href;
   }

   class RawCodeVisitor implements InstructionVisitor {
      private int instrSize = 0;
      private Formatter buf;
      private SymbolFinder symFinder = createSymbolFinder();

      RawCodeVisitor(Formatter buf) {
         this.buf = buf;
      }

      public int getInstructionSize() {
         return  instrSize;
      }

      public void prologue() {
      }

      public void visit(long currentPc, Instruction instr) {
         String href = null;
          if (instr.isCall()) {
             CallInstruction call = (CallInstruction) instr;
             sun.jvm.hotspot.asm.Address addr = call.getBranchDestination();
             href = genPCHref(currentPc, addr);
          }

          instrSize += instr.getSize();
          buf.append("0x");
          buf.append(Long.toHexString(currentPc));
          buf.append(':');
          buf.append(tab);

          if (href != null) {
             buf.link(href, instr.asString(currentPc, symFinder));
          } else {
             buf.append(instr.asString(currentPc, symFinder));
          }
          buf.br();
      }

      public void epilogue() {
      }
   };

   protected String genHTMLForRawDisassembly(sun.jvm.hotspot.debugger.Address addr,
                                             String prevPCs,
                                             byte[] code) {
      try {
         long startPc = addressToLong(addr);
         Disassembler disasm = createDisassembler(startPc, code);
         final Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue("Disassembly @0x" + Long.toHexString(startPc));

         if (prevPCs != null && genHTML) {
             buf.beginTag("p");
             buf.link(genMultPCHref(prevPCs), "show previous code ..");
             buf.endTag("p");
         }


         buf.h3("Code");
         RawCodeVisitor visitor = new RawCodeVisitor(buf);
         disasm.decode(visitor);

         if (genHTML) buf.beginTag("p");
         Formatter tmpBuf = new Formatter(genHTML);
         tmpBuf.append("0x");
         tmpBuf.append(Long.toHexString(startPc + visitor.getInstructionSize()).toString());
         tmpBuf.append(",0x");
         tmpBuf.append(Long.toHexString(startPc));
         if (prevPCs != null) {
            tmpBuf.append(',');
            tmpBuf.append(prevPCs);
         }
         if (genHTML) {
             buf.link(genMultPCHref(tmpBuf.toString()), "show more code ..");
             buf.endTag("p");
         }

         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genSafepointInfo(NMethod nm, PCDesc pcDesc) {
       ScopeDesc sd = nm.getScopeDescAt(pcDesc.getRealPC(nm));
       Formatter buf = new Formatter(genHTML);
       Formatter tabs = new Formatter(genHTML);
       tabs.append(tab + tab + tab); // Initial indent for debug info

       buf.beginTag("pre");
       genScope(buf, tabs, sd);

       // Reset indent for scalar replaced objects
       tabs = new Formatter(genHTML);
       tabs.append(tab + tab + tab); // Initial indent for debug info

       genScObjInfo(buf, tabs, sd);
       buf.endTag("pre");

       buf.append(genOopMapInfo(nm, pcDesc));

       return buf.toString();
   }

    protected void genScope(Formatter buf, Formatter tabs, ScopeDesc sd) {
        if (sd == null) {
            return;
        }

        genScope(buf, tabs, sd.sender());

        buf.append(tabs);
        Method m = sd.getMethod();
        buf.append(genMethodAndKlassLink(m));
        int bci = sd.getBCI();
        buf.append(" @ bci = ");
        buf.append(Integer.toString(bci));

        int line = m.getLineNumberFromBCI(bci);
        if (line != -1) {
            buf.append(", line = ");
            buf.append(Integer.toString(line));
        }

        List locals = sd.getLocals();
        if (locals != null) {
            buf.br();
            buf.append(tabs);
            buf.append(genHTMLForLocals(sd, locals));
        }

        List expressions = sd.getExpressions();
        if (expressions != null) {
            buf.br();
            buf.append(tabs);
            buf.append(genHTMLForExpressions(sd, expressions));
        }

        List monitors = sd.getMonitors();
        if (monitors != null) {
            buf.br();
            buf.append(tabs);
            buf.append(genHTMLForMonitors(sd, monitors));
        }

        buf.br();
        tabs.append(tab);
    }

    protected void genScObjInfo(Formatter buf, Formatter tabs, ScopeDesc sd) {
        if (sd == null) {
            return;
        }

        List objects = sd.getObjects();
        if (objects == null) {
            return;
        }
        int length = objects.size();
        for (int i = 0; i < length; i++) {
            buf.append(tabs);
            ObjectValue ov = (ObjectValue)objects.get(i);
            buf.append("ScObj" + i);
            ScopeValue sv = ov.getKlass();
            if (Assert.ASSERTS_ENABLED) {
                Assert.that(sv.isConstantOop(), "scalar replaced object klass must be constant oop");
            }
            ConstantOopReadValue klv = (ConstantOopReadValue)sv;
            OopHandle klHandle = klv.getValue();
            if (Assert.ASSERTS_ENABLED) {
                Assert.that(klHandle != null, "scalar replaced object klass must be not NULL");
            }
            Oop obj = VM.getVM().getObjectHeap().newOop(klHandle);
            if (obj instanceof InstanceKlass) {
                InstanceKlass kls = (InstanceKlass) obj;
                buf.append(" " + kls.getName().asString() + "={");
                int flen = ov.fieldsSize();

                TypeArray klfields = kls.getFields();
                int klen = (int) klfields.getLength();

                ConstantPool cp = kls.getConstants();
                int findex = 0;
                for (int index = 0; index < klen; index += kls.NEXT_OFFSET) {
                    int accsFlags = klfields.getShortAt(index + kls.ACCESS_FLAGS_OFFSET);
                    int nameIndex = klfields.getShortAt(index + kls.NAME_INDEX_OFFSET);
                    AccessFlags access = new AccessFlags(accsFlags);
                    if (!access.isStatic()) {
                        ScopeValue svf = ov.getFieldAt(findex++);
                        String    fstr = scopeValueAsString(sd, svf);
                        Symbol f_name  = cp.getSymbolAt(nameIndex);
                        buf.append(" [" + f_name.asString() + " :"+ index + "]=(#" + fstr + ")");
                    }
                }
                buf.append(" }");
            } else {
                buf.append(" ");
                int flen = ov.fieldsSize();
                if (obj instanceof TypeArrayKlass) {
                    TypeArrayKlass kls = (TypeArrayKlass) obj;
                    buf.append(kls.getElementTypeName() + "[" + flen + "]");
                } else if (obj instanceof ObjArrayKlass) {
                    ObjArrayKlass kls = (ObjArrayKlass) obj;
                    Klass elobj = kls.getBottomKlass();
                    if (elobj instanceof InstanceKlass) {
                        buf.append(elobj.getName().asString());
                    } else if (elobj instanceof TypeArrayKlass) {
                        TypeArrayKlass elkls = (TypeArrayKlass) elobj;
                        buf.append(elkls.getElementTypeName());
                    } else {
                        if (Assert.ASSERTS_ENABLED) {
                            Assert.that(false, "unknown scalar replaced object klass!");
                        }
                    }
                    buf.append("[" + flen + "]");
                    int ndim = (int) kls.getDimension();
                    while (--ndim > 0) {
                        buf.append("[]");
                    }
                } else {
                    if (Assert.ASSERTS_ENABLED) {
                        Assert.that(false, "unknown scalar replaced object klass!");
                    }
                }
                buf.append("={");
                for (int findex = 0; findex < flen; findex++) {
                    ScopeValue svf = ov.getFieldAt(findex);
                    String fstr = scopeValueAsString(sd, svf);
                    buf.append(" [" + findex + "]=(#" + fstr + ")");
                }
                buf.append(" }");
            }
            buf.br();
        }
    }

   protected String genHTMLForOopMap(OopMap map) {
      final int stack0 = VMRegImpl.getStack0().getValue();
      Formatter buf = new Formatter(genHTML);

      final class OopMapValueIterator {
         final Formatter iterate(OopMapStream oms, String type, boolean printContentReg) {
            Formatter tmpBuf = new Formatter(genHTML);
            boolean found = false;
            tmpBuf.beginTag("tr");
            tmpBuf.beginTag("td");
            tmpBuf.append(type);
            for (; ! oms.isDone(); oms.next()) {
               OopMapValue omv = oms.getCurrent();
               if (omv == null) {
                  continue;
               }
               found = true;
               VMReg vmReg = omv.getReg();
               int reg = vmReg.getValue();
               if (reg < stack0) {
                  tmpBuf.append(VMRegImpl.getRegisterName(reg));
               } else {
                  tmpBuf.append('[');
                  tmpBuf.append(Integer.toString((reg - stack0) * 4));
                  tmpBuf.append(']');
               }
               if (printContentReg) {
                  tmpBuf.append(" = ");
                  VMReg vmContentReg = omv.getContentReg();
                  int contentReg = vmContentReg.getValue();
                  if (contentReg < stack0) {
                     tmpBuf.append(VMRegImpl.getRegisterName(contentReg));
                  } else {
                     tmpBuf.append('[');
                     tmpBuf.append(Integer.toString((contentReg - stack0) * 4));
                     tmpBuf.append(']');
                  }
               }
               tmpBuf.append(spaces);
            }
            tmpBuf.endTag("td");
            tmpBuf.endTag("tr");
            return found ? tmpBuf : new Formatter(genHTML);
         }
      }

      buf.beginTable(0);

      OopMapValueIterator omvIterator = new OopMapValueIterator();
      OopMapStream oms = new OopMapStream(map, OopMapValue.OopTypes.OOP_VALUE);
      buf.append(omvIterator.iterate(oms, "Oops:", false));

      oms = new OopMapStream(map, OopMapValue.OopTypes.NARROWOOP_VALUE);
      buf.append(omvIterator.iterate(oms, "narrowOops:", false));

      oms = new OopMapStream(map, OopMapValue.OopTypes.VALUE_VALUE);
      buf.append(omvIterator.iterate(oms, "Values:", false));

      oms = new OopMapStream(map, OopMapValue.OopTypes.CALLEE_SAVED_VALUE);
      buf.append(omvIterator.iterate(oms, "Callee saved:",  true));

      oms = new OopMapStream(map, OopMapValue.OopTypes.DERIVED_OOP_VALUE);
      buf.append(omvIterator.iterate(oms, "Derived oops:", true));

      buf.endTag("table");
      return buf.toString();
   }


   protected String genOopMapInfo(NMethod nmethod, PCDesc pcDesc) {
      OopMapSet mapSet = nmethod.getOopMaps();
      if (mapSet == null || (mapSet.getSize() <= 0))
        return "";
      int pcOffset = pcDesc.getPCOffset();
      OopMap map = mapSet.findMapAtOffset(pcOffset, VM.getVM().isDebugging());
      if (map == null) {
         throw new IllegalArgumentException("no oopmap at safepoint!");
      }

      return genOopMapInfo(map);
   }

   protected String genOopMapInfo(OopMap map) {
     Formatter buf = new Formatter(genHTML);
     buf.beginTag("pre");
     buf.append("OopMap: ");
     buf.br();
     buf.append(genHTMLForOopMap(map));
     buf.endTag("pre");

     return buf.toString();
   }

   protected String locationAsString(Location loc) {
      Formatter buf = new Formatter(genHTML);
      if (loc.isIllegal()) {
         buf.append("illegal");
      } else {
         Location.Where  w  = loc.getWhere();
         Location.Type type = loc.getType();

         if (w == Location.Where.ON_STACK) {
            buf.append("stack[" + loc.getStackOffset() + "]");
         } else if (w == Location.Where.IN_REGISTER) {
            boolean isFloat = (type == Location.Type.FLOAT_IN_DBL ||
                               type == Location.Type.DBL);
            int regNum = loc.getRegisterNumber();
            VMReg vmReg = new VMReg(regNum);
            buf.append(VMRegImpl.getRegisterName(vmReg.getValue()));
         }

         buf.append(", ");
         if (type == Location.Type.NORMAL) {
            buf.append("normal");
         } else if (type == Location.Type.OOP) {
            buf.append("oop");
         } else if (type == Location.Type.NARROWOOP) {
            buf.append("narrowoop");
         } else if (type == Location.Type.INT_IN_LONG) {
            buf.append("int");
         } else if (type == Location.Type.LNG) {
            buf.append("long");
         } else if (type == Location.Type.FLOAT_IN_DBL) {
            buf.append("float");
         } else if (type == Location.Type.DBL) {
            buf.append("double");
         } else if (type == Location.Type.ADDR) {
            buf.append("address");
         } else if (type == Location.Type.INVALID) {
            buf.append("invalid");
         }
      }
      return buf.toString();
   }

   private String scopeValueAsString(ScopeDesc sd, ScopeValue sv) {
      Formatter buf = new Formatter(genHTML);
      if (sv.isConstantInt()) {
         buf.append("int ");
         ConstantIntValue intValue = (ConstantIntValue) sv;
         buf.append(Integer.toString(intValue.getValue()));
      } else if (sv.isConstantLong()) {
         buf.append("long ");
         ConstantLongValue longValue = (ConstantLongValue) sv;
         buf.append(Long.toString(longValue.getValue()));
         buf.append("L");
      } else if (sv.isConstantDouble()) {
         buf.append("double ");
         ConstantDoubleValue dblValue = (ConstantDoubleValue) sv;
         buf.append(Double.toString(dblValue.getValue()));
         buf.append("D");
      } else if (sv.isConstantOop()) {
         buf.append("oop ");
         ConstantOopReadValue oopValue = (ConstantOopReadValue) sv;
         OopHandle oopHandle = oopValue.getValue();
         if (oopHandle != null) {
            buf.append(oopHandle.toString());
         } else {
            buf.append("null");
         }
      } else if (sv.isLocation()) {
         LocationValue lvalue = (LocationValue) sv;
         Location loc = lvalue.getLocation();
         if (loc != null) {
            buf.append(locationAsString(loc));
         } else {
            buf.append("null");
         }
      } else if (sv.isObject()) {
         ObjectValue ov = (ObjectValue)sv;
         buf.append("#ScObj" + sd.getObjects().indexOf(ov));
      } else {
         buf.append("unknown scope value " + sv);
      }
      return buf.toString();
   }

   protected String genHTMLForScopeValues(ScopeDesc sd, boolean locals, List values) {
      int length = values.size();
      Formatter buf = new Formatter(genHTML);
      buf.append(locals? "locals " : "expressions ");
      for (int i = 0; i < length; i++) {
         ScopeValue sv = (ScopeValue) values.get(i);
         if (sv == null) {
            continue;
         }
         buf.append('(');
         if (locals) {
            Symbol name = sd.getMethod().getLocalVariableName(sd.getBCI(), i);
            if (name != null) {
               buf.append("'");
               buf.append(name.asString());
               buf.append('\'');
            } else {
               buf.append("[");
               buf.append(Integer.toString(i));
               buf.append(']');
            }
         } else {
            buf.append("[");
            buf.append(Integer.toString(i));
            buf.append(']');
         }

         buf.append(", ");
         buf.append(scopeValueAsString(sd, sv));
         buf.append(") ");
      }

      return buf.toString();
   }

   protected String genHTMLForLocals(ScopeDesc sd, List locals) {
      return genHTMLForScopeValues(sd, true, locals);
   }

   protected String genHTMLForExpressions(ScopeDesc sd, List expressions) {
      return genHTMLForScopeValues(sd, false, expressions);
   }

   protected String genHTMLForMonitors(ScopeDesc sd, List monitors) {
      int length = monitors.size();
      Formatter buf = new Formatter(genHTML);
      buf.append("monitors ");
      for (int i = 0; i < length; i++) {
         MonitorValue mv = (MonitorValue) monitors.get(i);
         if (mv == null) {
            continue;
         }
         buf.append("(owner = ");
         ScopeValue owner = mv.owner();
         if (owner != null) {
            buf.append(scopeValueAsString(sd, owner));
         } else {
            buf.append("null");
         }
         buf.append(", lock = ");

         Location loc = mv.basicLock();
         if (loc != null) {
            buf.append(locationAsString(loc));
         } else {
            buf.append("null");
         }
         buf.append(") ");
      }
      return buf.toString();
   }

   public String genHTML(final NMethod nmethod) {
      try {
         final Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue(genNMethodTitle(nmethod));
         buf.h3("Method");
         buf.append(genMethodAndKlassLink(nmethod.getMethod()));

         buf.h3("Compiled Code");
         sun.jvm.hotspot.debugger.Address codeBegin = nmethod.codeBegin();
         sun.jvm.hotspot.debugger.Address codeEnd   = nmethod.codeEnd();
         final int codeSize = (int)codeEnd.minus(codeBegin);
         final long startPc = addressToLong(codeBegin);
         final byte[] code = new byte[codeSize];
         for (int i=0; i < code.length; i++)
            code[i] = codeBegin.getJByteAt(i);

         final long verifiedEntryPoint = addressToLong(nmethod.getVerifiedEntryPoint());
         final long entryPoint = addressToLong(nmethod.getEntryPoint());
         final Map safepoints = nmethod.getSafepoints();

         final SymbolFinder symFinder = createSymbolFinder();
         final Disassembler disasm = createDisassembler(startPc, code);
         class NMethodVisitor implements InstructionVisitor {
            public void prologue() {
            }

            public void visit(long currentPc, Instruction instr) {
               String href = null;
               if (instr.isCall()) {
                  CallInstruction call = (CallInstruction) instr;
                  sun.jvm.hotspot.asm.Address addr = call.getBranchDestination();
                  href = genPCHref(currentPc, addr);
               }

               if (currentPc == verifiedEntryPoint) {
                   buf.bold("Verified Entry Point"); buf.br();
               }
               if (currentPc == entryPoint) {
                   buf.bold(">Entry Point"); buf.br();
               }

               PCDesc pcDesc = (PCDesc) safepoints.get(longToAddress(currentPc));

               if (pcDesc != null) {
                  buf.append(genSafepointInfo(nmethod, pcDesc));
               }

               buf.append("0x");
               buf.append(Long.toHexString(currentPc));
               buf.append(':');
               buf.append(tab);

               if (href != null) {
                  buf.link(href, instr.asString(currentPc, symFinder));
               } else {
                  buf.append(instr.asString(currentPc, symFinder));
               }

               buf.br();
            }

            public void epilogue() {
            }
         };

         disasm.decode(new NMethodVisitor());

         sun.jvm.hotspot.debugger.Address stubBegin = nmethod.stubBegin();
         if (stubBegin != null) {
            sun.jvm.hotspot.debugger.Address stubEnd   = nmethod.stubEnd();
            buf.h3("Stub");
            long stubStartPc = addressToLong(stubBegin);
            long stubEndPc = addressToLong(stubEnd);
            int range = (int) (stubEndPc - stubStartPc);
            byte[] stubCode = readBuffer(stubBegin, range);
            Disassembler disasm2 = createDisassembler(stubStartPc, stubCode);
            disasm2.decode(new NMethodVisitor());
         }
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

  public String genHTML(final CodeBlob blob) {
      try {
         final Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue(genCodeBlobTitle(blob));
         buf.h3("CodeBlob");

         buf.h3("Compiled Code");
         final sun.jvm.hotspot.debugger.Address codeBegin = blob.instructionsBegin();
         final int codeSize = blob.getInstructionsSize();
         final long startPc = addressToLong(codeBegin);
         final byte[] code = new byte[codeSize];
         for (int i=0; i < code.length; i++)
            code[i] = codeBegin.getJByteAt(i);

         final SymbolFinder symFinder = createSymbolFinder();
         final Disassembler disasm = createDisassembler(startPc, code);
         class CodeBlobVisitor implements InstructionVisitor {
            OopMapSet maps;
            OopMap curMap;
            int curMapIndex;
            long curMapOffset;
            public void prologue() {
              maps = blob.getOopMaps();
              if (maps != null && (maps.getSize() > 0)) {
                curMap = maps.getMapAt(0);
                if (curMap != null) {
                  curMapOffset = curMap.getOffset();
                }
              }
            }

            public void visit(long currentPc, Instruction instr) {
               String href = null;
               if (instr.isCall()) {
                  CallInstruction call = (CallInstruction) instr;
                  sun.jvm.hotspot.asm.Address addr = call.getBranchDestination();
                  href = genPCHref(currentPc, addr);
               }

               buf.append("0x");
               buf.append(Long.toHexString(currentPc));
               buf.append(':');
               buf.append(tab);

               if (href != null) {
                  buf.link(href, instr.asString(currentPc, symFinder));
               } else {
                   buf.append(instr.asString(currentPc, symFinder));
               }
               buf.br();

               // See whether we have an oop map at this PC
               if (curMap != null) {
                 long curOffset = currentPc - startPc;
                 if (curOffset == curMapOffset) {
                   buf.append(genOopMapInfo(curMap));
                   if (++curMapIndex >= maps.getSize()) {
                     curMap = null;
                   } else {
                     curMap = maps.getMapAt(curMapIndex);
                     if (curMap != null) {
                       curMapOffset = curMap.getOffset();
                     }
                   }
                 }
               }
            }

            public void epilogue() {
            }
         };

         disasm.decode(new CodeBlobVisitor());

         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genInterpreterCodeletTitle(InterpreterCodelet codelet) {
      Formatter buf = new Formatter(genHTML);
      buf.append("Interpreter codelet [");
      buf.append(codelet.codeBegin().toString());
      buf.append(',');
      buf.append(codelet.codeEnd().toString());
      buf.append(") - ");
      buf.append(codelet.getDescription());
      return buf.toString();
   }

   protected String genInterpreterCodeletLinkPageHref(StubQueue stubq) {
      return genBaseHref() + "interp_codelets";
   }

   public String genInterpreterCodeletLinksPage() {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue("Interpreter Codelets");
      buf.beginTag("ul");

      Interpreter interp = VM.getVM().getInterpreter();
      StubQueue code = interp.getCode();
      InterpreterCodelet stub = (InterpreterCodelet) code.getFirst();
      while (stub != null) {
         buf.beginTag("li");
         sun.jvm.hotspot.debugger.Address addr = stub.codeBegin();
         buf.link(genPCHref(addressToLong(addr)), stub.getDescription() + " @" + addr);
         buf.endTag("li");
         stub = (InterpreterCodelet) code.getNext(stub);
      }

      buf.endTag("ul");
      buf.genHTMLEpilogue();
      return buf.toString();
   }

   public String genHTML(InterpreterCodelet codelet) {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue(genInterpreterCodeletTitle(codelet));
      Interpreter interp = VM.getVM().getInterpreter();
      StubQueue stubq = interp.getCode();

      if (genHTML) {
         buf.beginTag("h3");
         buf.link(genInterpreterCodeletLinkPageHref(stubq), "View links for all codelets");
         buf.endTag("h3");
         buf.br();
      }

      Stub prev = stubq.getPrev(codelet);
      if (prev != null) {
         if (genHTML) {
            buf.beginTag("h3");
            buf.link(genPCHref(addressToLong(prev.codeBegin())), "View Previous Codelet");
            buf.endTag("h3");
            buf.br();
         } else {
            buf.h3("Previous Codelet = 0x" + Long.toHexString(addressToLong(prev.codeBegin())));
         }
      }

      buf.h3("Code");
      long stubStartPc = addressToLong(codelet.codeBegin());
      long stubEndPc = addressToLong(codelet.codeEnd());
      int range = (int) (stubEndPc - stubStartPc);
      byte[] stubCode = readBuffer(codelet.codeBegin(), range);
      Disassembler disasm = createDisassembler(stubStartPc, stubCode);
      disasm.decode(new RawCodeVisitor(buf));


      Stub next = stubq.getNext(codelet);
      if (next != null) {
         if (genHTML) {
            buf.beginTag("h3");
            buf.link(genPCHref(addressToLong(next.codeBegin())), "View Next Codelet");
            buf.endTag("h3");
         } else {
            buf.h3("Next Codelet = 0x" + Long.toHexString(addressToLong(next.codeBegin())));
         }
      }

      buf.genHTMLEpilogue();
      return buf.toString();
   }

   protected String genDumpKlassesTitle(InstanceKlass[] klasses) {
      return (klasses.length == 1) ? "Create .class for this class"
                                   : "Create .class for all classes";
   }

   protected String genDumpKlassesHref(InstanceKlass[] klasses) {
      StringBuffer buf = new StringBuffer(genBaseHref());
      buf.append("jcore_multiple=");
      for (int k = 0; k < klasses.length; k++) {
         buf.append(klasses[k].getHandle().toString());
         buf.append(',');
      }
      return buf.toString();
   }

   protected String genDumpKlassesLink(InstanceKlass[] klasses) {
      if (!genHTML) return "";

      Formatter buf = new Formatter(genHTML);
      buf.link(genDumpKlassesHref(klasses), genDumpKlassesTitle(klasses));
      return buf.toString();
   }

   public String genHTMLForKlassNames(InstanceKlass[] klasses) {
      try {
         Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue();
         buf.h3(genDumpKlassesLink(klasses));

         buf.append(genHTMLListForKlassNames(klasses));
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genHTMLListForKlassNames(InstanceKlass[] klasses) {
      final Formatter buf = new Formatter(genHTML);
      buf.beginTable(0);
      for (int i = 0; i < klasses.length; i++) {
         InstanceKlass ik = klasses[i];
         buf.beginTag("tr");
         buf.cell(genKlassLink(ik));
         buf.endTag("tr");
      }

      buf.endTable();
      return buf.toString();
   }

   public String genHTMLForMethodNames(InstanceKlass klass) {
      try {
         Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue();
         buf.append(genHTMLListForMethods(klass));
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genHTMLListForMethods(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      ObjArray methods = klass.getMethods();
      int numMethods = (int) methods.getLength();
      if (numMethods != 0) {
         buf.h3("Methods");
         buf.beginTag("ul");
         for (int m = 0; m < numMethods; m++) {
            Method mtd = (Method) methods.getObjAt(m);
            buf.li(genMethodLink(mtd) + ";");
         }
         buf.endTag("ul");
      }
      return buf.toString();
   }

   protected String genHTMLListForInterfaces(InstanceKlass klass) {
      try {
         Formatter buf = new Formatter(genHTML);
         ObjArray interfaces = klass.getLocalInterfaces();
         int numInterfaces = (int) interfaces.getLength();
         if (numInterfaces != 0) {
            buf.h3("Interfaces");
            buf.beginTag("ul");
            for (int i = 0; i < numInterfaces; i++) {
               InstanceKlass inf = (InstanceKlass) interfaces.getObjAt(i);
               buf.li(genKlassLink(inf));
            }
            buf.endTag("ul");
         }
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genFieldModifierString(AccessFlags acc) {
      Formatter buf = new Formatter(genHTML);
      if (acc.isPrivate()) {
         buf.append("private ");
      } else if (acc.isProtected()) {
         buf.append("protected ");
      } else if (acc.isPublic()) {
         buf.append("public ");
      }

      if (acc.isStatic()) {
         buf.append("static ");
      }

      if (acc.isFinal()) {
         buf.append("final ");
      }
      if (acc.isVolatile()) {
         buf.append("volatile ");
      }
      if (acc.isTransient()) {
         buf.append("transient ");
      }

      // javac generated flags
      if (acc.isSynthetic()) {
         buf.append("[synthetic] ");
      }
      return buf.toString();
   }

   public String genHTMLForFieldNames(InstanceKlass klass) {
      try {
         Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue();
         buf.append(genHTMLListForFields(klass));
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch (Exception exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genHTMLListForFields(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      TypeArray fields = klass.getFields();
      int numFields = (int) fields.getLength();
      ConstantPool cp = klass.getConstants();
      if (numFields != 0) {
         buf.h3("Fields");
         buf.beginList();
         for (int f = 0; f < numFields; f += InstanceKlass.NEXT_OFFSET) {
           sun.jvm.hotspot.oops.Field field = klass.getFieldByIndex(f);
           String f_name = ((NamedFieldIdentifier)field.getID()).getName();
           Symbol f_sig  = field.getSignature();
           Symbol f_genSig = field.getGenericSignature();
           AccessFlags acc = field.getAccessFlagsObj();

           buf.beginListItem();
           buf.append(genFieldModifierString(acc));
           buf.append(' ');
           Formatter sigBuf = new Formatter(genHTML);
           new SignatureConverter(f_sig, sigBuf.getBuffer()).dispatchField();
           buf.append(sigBuf.toString().replace('/', '.'));
           buf.append(' ');
           buf.append(f_name);
           buf.append(';');
           // is it generic?
           if (f_genSig != null) {
              buf.append(" [signature ");
              buf.append(escapeHTMLSpecialChars(f_genSig.asString()));
              buf.append("] ");
           }
           buf.append(" (offset = " + field.getOffset() + ")");
           buf.endListItem();
         }
         buf.endList();
      }
      return buf.toString();
   }

   protected String genKlassHierarchyHref(InstanceKlass klass) {
      return genBaseHref() + "hierarchy=" + klass.getHandle();
   }

   protected String genKlassHierarchyTitle(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      buf.append("Class Hierarchy of ");
      buf.append(genKlassTitle(klass));
      return buf.toString();
   }

   protected String genKlassHierarchyLink(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      buf.link(genKlassHierarchyHref(klass), genKlassHierarchyTitle(klass));
      return buf.toString();
   }

   protected String genHTMLListForSubKlasses(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      Klass subklass = klass.getSubklassKlass();
      if (subklass != null) {
         buf.beginList();
         while (subklass != null) {
            if (subklass instanceof InstanceKlass) {
               buf.li(genKlassLink((InstanceKlass)subklass));
            }
            subklass = subklass.getNextSiblingKlass();
         }
         buf.endList();
      }
      return buf.toString();
   }

   public String genHTMLForKlassHierarchy(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue(genKlassHierarchyTitle(klass));


      buf.beginTag("pre");
      buf.append(genKlassLink(klass));
      buf.br();
      StringBuffer tabs = new StringBuffer(tab);
      InstanceKlass superKlass = klass;
      while ( (superKlass = (InstanceKlass) superKlass.getSuper()) != null ) {
         buf.append(tabs);
         buf.append(genKlassLink(superKlass));
         tabs.append(tab);
         buf.br();
      }
      buf.endTag("pre");

      // generate subklass list
      Klass subklass = klass.getSubklassKlass();
      if (subklass != null) {
         buf.h3("Direct Subclasses");
         buf.append(genHTMLListForSubKlasses(klass));
      }

      buf.genHTMLEpilogue();
      return buf.toString();
   }

   protected String genDumpKlassHref(InstanceKlass klass) {
      return genBaseHref() + "jcore=" + klass.getHandle();
   }

   protected String genDumpKlassLink(InstanceKlass klass) {
      if (!genHTML) return "";

      Formatter buf = new Formatter(genHTML);
      buf.link(genDumpKlassHref(klass), "Create .class File");
      return buf.toString();
   }

   public String genHTML(InstanceKlass klass) {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue(genKlassTitle(klass));
      InstanceKlass superKlass = (InstanceKlass) klass.getSuper();

      if (genHTML) {
          // super class tree and subclass list
          buf.beginTag("h3");
          buf.link(genKlassHierarchyHref(klass), "View Class Hierarchy");
          buf.endTag("h3");
      }

      // jcore - create .class link
      buf.h3(genDumpKlassLink(klass));

      // super class
      if (superKlass != null) {
         buf.h3("Super Class");
         buf.append(genKlassLink(superKlass));
      }

      // interfaces
      buf.append(genHTMLListForInterfaces(klass));

      // fields
      buf.append(genHTMLListForFields(klass));

      // methods
      buf.append(genHTMLListForMethods(klass));

      // constant pool link
      buf.h3("Constant Pool");
      buf.append(genConstantPoolLink(klass.getConstants()));

      buf.genHTMLEpilogue();
      return buf.toString();
   }

   protected sun.jvm.hotspot.debugger.Address parseAddress(String address) {
      VM vm = VM.getVM();
      sun.jvm.hotspot.debugger.Address addr = vm.getDebugger().parseAddress(address);
      return addr;
   }

   protected long addressToLong(sun.jvm.hotspot.debugger.Address addr) {
      return VM.getVM().getDebugger().getAddressValue(addr);
   }

   protected sun.jvm.hotspot.debugger.Address longToAddress(long addr) {
      return parseAddress("0x" + Long.toHexString(addr));
   }

   protected Oop getOopAtAddress(sun.jvm.hotspot.debugger.Address addr) {
      OopHandle oopHandle = addr.addOffsetToAsOopHandle(0);
      return VM.getVM().getObjectHeap().newOop(oopHandle);
   }

   protected Oop getOopAtAddress(String address) {
      sun.jvm.hotspot.debugger.Address addr = parseAddress(address);
      return getOopAtAddress(addr);
   }

   private void dumpKlass(InstanceKlass kls) throws IOException {
      String klassName = kls.getName().asString();
      klassName = klassName.replace('/', File.separatorChar);
      int index = klassName.lastIndexOf(File.separatorChar);
      File dir = null;
      if (index != -1) {
        String dirName = klassName.substring(0, index);
        dir =  new File(DUMP_KLASS_OUTPUT_DIR,  dirName);
      } else {
        dir = new File(DUMP_KLASS_OUTPUT_DIR);
      }

      dir.mkdirs();
      File f = new File(dir, klassName.substring(klassName.lastIndexOf(File.separatorChar) + 1)
                              + ".class");
      f.createNewFile();
      FileOutputStream fis = new FileOutputStream(f);
      ClassWriter cw = new ClassWriter(kls, fis);
      cw.write();
   }

   public String genDumpKlass(InstanceKlass kls) {
      try {
         dumpKlass(kls);
         Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue(genKlassTitle(kls));
         buf.append(".class created for ");
         buf.append(genKlassLink(kls));
         buf.genHTMLEpilogue();
         return buf.toString();
      } catch(IOException exp) {
         return genHTMLErrorMessage(exp);
      }
   }

   protected String genJavaStackTraceTitle(JavaThread thread) {
      Formatter buf = new Formatter(genHTML);
      buf.append("Java Stack Trace for ");
      buf.append(thread.getThreadName());
      return buf.toString();
   }

   public String genHTMLForJavaStackTrace(JavaThread thread) {
      Formatter buf = new Formatter(genHTML);
      buf.genHTMLPrologue(genJavaStackTraceTitle(thread));

      buf.append("Thread state = ");
      buf.append(thread.getThreadState().toString());
      buf.br();
      buf.beginTag("pre");
      for (JavaVFrame vf = thread.getLastJavaVFrameDbg(); vf != null; vf = vf.javaSender()) {
         Method method = vf.getMethod();
         buf.append(" - ");
         buf.append(genMethodLink(method));
         buf.append(" @bci = " + vf.getBCI());

         int lineNumber = method.getLineNumberFromBCI(vf.getBCI());
         if (lineNumber != -1) {
            buf.append(", line = ");
            buf.append(lineNumber);
         }

         sun.jvm.hotspot.debugger.Address pc = vf.getFrame().getPC();
         if (pc != null) {
            buf.append(", pc = ");
            buf.link(genPCHref(addressToLong(pc)), pc.toString());
         }

         if (vf.isCompiledFrame()) {
            buf.append(" (Compiled");
         }
         else if (vf.isInterpretedFrame()) {
            buf.append(" (Interpreted");
         }

         if (vf.mayBeImpreciseDbg()) {
            buf.append("; information may be imprecise");
         }
         buf.append(")");
         buf.br();
      }

      buf.endTag("pre");
      buf.genHTMLEpilogue();
      return buf.toString();
   }

   public String genHTMLForHyperlink(String href) {
      if (href.startsWith("klass=")) {
         href = href.substring(href.indexOf('=') + 1);
         Oop obj = getOopAtAddress(href);
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(obj instanceof InstanceKlass, "class= href with improper InstanceKlass!");
         }
         return genHTML((InstanceKlass) obj);
      } else if (href.startsWith("method=")) {
         href = href.substring(href.indexOf('=') + 1);
         Oop obj = getOopAtAddress(href);
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(obj instanceof Method, "method= href with improper Method!");
         }
         return genHTML((Method) obj);
      } else if (href.startsWith("nmethod=")) {
         String addr = href.substring(href.indexOf('=') + 1);
         Object obj = VMObjectFactory.newObject(NMethod.class, parseAddress(addr));
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(obj instanceof NMethod, "nmethod= href with improper NMethod!");
         }
         return genHTML((NMethod) obj);
      } else if (href.startsWith("pc=")) {
         String address = href.substring(href.indexOf('=') + 1);
         return genHTML(parseAddress(address));
      } else if (href.startsWith("pc_multiple=")) {
         int indexOfComma = href.indexOf(',');
         if (indexOfComma == -1) {
            String firstPC = href.substring(href.indexOf('=') + 1);
            return genHTMLForRawDisassembly(parseAddress(firstPC), null);
         } else {
            String firstPC = href.substring(href.indexOf('=') + 1, indexOfComma);
            return genHTMLForRawDisassembly(parseAddress(firstPC), href.substring(indexOfComma + 1));
         }
      } else if (href.startsWith("interp_codelets")) {
         return genInterpreterCodeletLinksPage();
      } else if (href.startsWith("hierarchy=")) {
         href = href.substring(href.indexOf('=') + 1);
         Oop obj = getOopAtAddress(href);
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(obj instanceof InstanceKlass, "class= href with improper InstanceKlass!");
         }
         return genHTMLForKlassHierarchy((InstanceKlass) obj);
      } else if (href.startsWith("cpool=")) {
         href = href.substring(href.indexOf('=') + 1);
         Oop obj = getOopAtAddress(href);
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(obj instanceof ConstantPool, "cpool= href with improper ConstantPool!");
         }
         return genHTML((ConstantPool) obj);
      } else if (href.startsWith("jcore=")) {
         href = href.substring(href.indexOf('=') + 1);
         Oop obj = getOopAtAddress(href);
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(obj instanceof InstanceKlass, "jcore= href with improper InstanceKlass!");
         }
         return genDumpKlass((InstanceKlass) obj);
      } else if (href.startsWith("jcore_multiple=")) {
         href = href.substring(href.indexOf('=') + 1);
         Formatter buf = new Formatter(genHTML);
         buf.genHTMLPrologue();
         StringTokenizer st = new StringTokenizer(href, ",");
         while (st.hasMoreTokens()) {
            Oop obj = getOopAtAddress(st.nextToken());
            if (Assert.ASSERTS_ENABLED) {
               Assert.that(obj instanceof InstanceKlass, "jcore_multiple= href with improper InstanceKlass!");
            }

            InstanceKlass kls = (InstanceKlass) obj;
            try {
               dumpKlass(kls);
               buf.append(".class created for ");
               buf.append(genKlassLink(kls));
            } catch(Exception exp) {
               buf.bold("can't .class for " +
                        genKlassTitle(kls) +
                        " : " +
                        exp.getMessage());
            }
            buf.br();
         }

         buf.genHTMLEpilogue();
         return buf.toString();
      } else {
         if (Assert.ASSERTS_ENABLED) {
            Assert.that(false, "unknown href link!");
         }
         return null;
      }
   }
}
