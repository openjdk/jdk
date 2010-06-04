/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.asm;

import sun.tools.java.*;
import java.util.Enumeration;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.util.Vector;
// JCOV
import sun.tools.javac.*;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.String;
// end JCOV

/**
 * This class is used to assemble the bytecode instructions for a method.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author Arthur van Hoff
 */
public final
class Assembler implements Constants {
    static final int NOTREACHED         = 0;
    static final int REACHED            = 1;
    static final int NEEDED             = 2;

    Label first = new Label();
    Instruction last = first;
    int maxdepth;
    int maxvar;
    int maxpc;

    /**
     * Add an instruction
     */
    public void add(Instruction inst) {
        if (inst != null) {
            last.next = inst;
            last = inst;
        }
    }
    public void add(long where, int opc) {
        add(new Instruction(where, opc, null));
    }
    public void add(long where, int opc, Object obj) {
        add(new Instruction(where, opc, obj));
    }
// JCOV
    public void add(long where, int opc, Object obj, boolean flagCondInverted) {
        add(new Instruction(where, opc, obj, flagCondInverted));
    }

    public void add(boolean flagNoCovered, long where, int opc, Object obj) {
        add(new Instruction(flagNoCovered, where, opc, obj));
    }

    public void add(long where, int opc, boolean flagNoCovered) {
        add(new Instruction(where, opc, flagNoCovered));
    }

    static Vector SourceClassList = new Vector();

    static Vector TmpCovTable = new Vector();

    static int[]  JcovClassCountArray = new int[CT_LAST_KIND + 1];

    static String JcovMagicLine     = "JCOV-DATA-FILE-VERSION: 2.0";
    static String JcovClassLine     = "CLASS: ";
    static String JcovSrcfileLine   = "SRCFILE: ";
    static String JcovTimestampLine = "TIMESTAMP: ";
    static String JcovDataLine      = "DATA: ";
    static String JcovHeadingLine   = "#kind\tcount";

    static int[]  arrayModifiers    =
                {M_PUBLIC, M_PRIVATE, M_PROTECTED, M_ABSTRACT, M_FINAL, M_INTERFACE};
    static int[]  arrayModifiersOpc =
                {PUBLIC, PRIVATE, PROTECTED, ABSTRACT, FINAL, INTERFACE};
//end JCOV

    /**
     * Optimize instructions and mark those that can be reached
     */
    void optimize(Environment env, Label lbl) {
        lbl.pc = REACHED;

        for (Instruction inst = lbl.next ; inst != null ; inst = inst.next)  {
            switch (inst.pc) {
              case NOTREACHED:
                inst.optimize(env);
                inst.pc = REACHED;
                break;
              case REACHED:
                return;
              case NEEDED:
                break;
            }

            switch (inst.opc) {
              case opc_label:
              case opc_dead:
                if (inst.pc == REACHED) {
                    inst.pc = NOTREACHED;
                }
                break;

              case opc_ifeq:
              case opc_ifne:
              case opc_ifgt:
              case opc_ifge:
              case opc_iflt:
              case opc_ifle:
              case opc_if_icmpeq:
              case opc_if_icmpne:
              case opc_if_icmpgt:
              case opc_if_icmpge:
              case opc_if_icmplt:
              case opc_if_icmple:
              case opc_if_acmpeq:
              case opc_if_acmpne:
              case opc_ifnull:
              case opc_ifnonnull:
                optimize(env, (Label)inst.value);
                break;

              case opc_goto:
                optimize(env, (Label)inst.value);
                return;

              case opc_jsr:
                optimize(env, (Label)inst.value);
                break;

              case opc_ret:
              case opc_return:
              case opc_ireturn:
              case opc_lreturn:
              case opc_freturn:
              case opc_dreturn:
              case opc_areturn:
              case opc_athrow:
                return;

              case opc_tableswitch:
              case opc_lookupswitch: {
                SwitchData sw = (SwitchData)inst.value;
                optimize(env, sw.defaultLabel);
                for (Enumeration e = sw.tab.elements() ; e.hasMoreElements();) {
                    optimize(env, (Label)e.nextElement());
                }
                return;
              }

              case opc_try: {
                TryData td = (TryData)inst.value;
                td.getEndLabel().pc = NEEDED;
                for (Enumeration e = td.catches.elements() ; e.hasMoreElements();) {
                    CatchData cd = (CatchData)e.nextElement();
                    optimize(env, cd.getLabel());
                }
                break;
              }
            }
        }
    }

    /**
     * Eliminate instructions that are not reached
     */
    boolean eliminate() {
        boolean change = false;
        Instruction prev = first;

        for (Instruction inst = first.next ; inst != null ; inst = inst.next) {
            if (inst.pc != NOTREACHED) {
                prev.next = inst;
                prev = inst;
                inst.pc = NOTREACHED;
            } else {
                change = true;
            }
        }
        first.pc = NOTREACHED;
        prev.next = null;
        return change;
    }

    /**
     * Optimize the byte codes
     */
    public void optimize(Environment env) {
        //listing(System.out);
        do {
            // Figure out which instructions are reached
            optimize(env, first);

            // Eliminate instructions that are not reached
        } while (eliminate() && env.opt());
    }

    /**
     * Collect all constants into the constant table
     */
    public void collect(Environment env, MemberDefinition field, ConstantPool tab) {
        // Collect constants for arguments only
        // if a local variable table is generated
        if ((field != null) && env.debug_vars()) {
            if (field.getArguments() != null) {
                for (Enumeration e = field.getArguments().elements() ; e.hasMoreElements() ;) {
                    MemberDefinition f = (MemberDefinition)e.nextElement();
                    tab.put(f.getName().toString());
                    tab.put(f.getType().getTypeSignature());
                }
            }
        }

        // Collect constants from the instructions
        for (Instruction inst = first ; inst != null ; inst = inst.next) {
            inst.collect(tab);
        }
    }

    /**
     * Determine stack size, count local variables
     */
    void balance(Label lbl, int depth) {
        for (Instruction inst = lbl ; inst != null ; inst = inst.next)  {
            //Environment.debugOutput(inst.toString() + ": " + depth + " => " +
            //                                 (depth + inst.balance()));
            depth += inst.balance();
            if (depth < 0) {
               throw new CompilerError("stack under flow: " + inst.toString() + " = " + depth);
            }
            if (depth > maxdepth) {
                maxdepth = depth;
            }
            switch (inst.opc) {
              case opc_label:
                lbl = (Label)inst;
                if (inst.pc == REACHED) {
                    if (lbl.depth != depth) {
                        throw new CompilerError("stack depth error " +
                                                depth + "/" + lbl.depth +
                                                ": " + inst.toString());
                    }
                    return;
                }
                lbl.pc = REACHED;
                lbl.depth = depth;
                break;

              case opc_ifeq:
              case opc_ifne:
              case opc_ifgt:
              case opc_ifge:
              case opc_iflt:
              case opc_ifle:
              case opc_if_icmpeq:
              case opc_if_icmpne:
              case opc_if_icmpgt:
              case opc_if_icmpge:
              case opc_if_icmplt:
              case opc_if_icmple:
              case opc_if_acmpeq:
              case opc_if_acmpne:
              case opc_ifnull:
              case opc_ifnonnull:
                balance((Label)inst.value, depth);
                break;

              case opc_goto:
                balance((Label)inst.value, depth);
                return;

              case opc_jsr:
                balance((Label)inst.value, depth + 1);
                break;

              case opc_ret:
              case opc_return:
              case opc_ireturn:
              case opc_lreturn:
              case opc_freturn:
              case opc_dreturn:
              case opc_areturn:
              case opc_athrow:
                return;

              case opc_iload:
              case opc_fload:
              case opc_aload:
              case opc_istore:
              case opc_fstore:
              case opc_astore: {
                int v = ((inst.value instanceof Number)
                            ? ((Number)inst.value).intValue()
                            : ((LocalVariable)inst.value).slot) + 1;
                if (v > maxvar)
                    maxvar = v;
                break;
              }

              case opc_lload:
              case opc_dload:
              case opc_lstore:
              case opc_dstore: {
                int v = ((inst.value instanceof Number)
                            ? ((Number)inst.value).intValue()
                            : ((LocalVariable)inst.value).slot) + 2;
                if (v  > maxvar)
                    maxvar = v;
                break;
              }

              case opc_iinc: {
                  int v = ((int[])inst.value)[0] + 1;
                  if (v  > maxvar)
                      maxvar = v + 1;
                  break;
              }

              case opc_tableswitch:
              case opc_lookupswitch: {
                SwitchData sw = (SwitchData)inst.value;
                balance(sw.defaultLabel, depth);
                for (Enumeration e = sw.tab.elements() ; e.hasMoreElements();) {
                    balance((Label)e.nextElement(), depth);
                }
                return;
              }

              case opc_try: {
                TryData td = (TryData)inst.value;
                for (Enumeration e = td.catches.elements() ; e.hasMoreElements();) {
                    CatchData cd = (CatchData)e.nextElement();
                    balance(cd.getLabel(), depth + 1);
                }
                break;
              }
            }
        }
    }

    /**
     * Generate code
     */
    public void write(Environment env, DataOutputStream out,
                      MemberDefinition field, ConstantPool tab)
                 throws IOException {
        //listing(System.out);

        if ((field != null) && field.getArguments() != null) {
              int sum = 0;
              Vector v = field.getArguments();
              for (Enumeration e = v.elements(); e.hasMoreElements(); ) {
                  MemberDefinition f = ((MemberDefinition)e.nextElement());
                  sum += f.getType().stackSize();
              }
              maxvar = sum;
        }

        // Make sure the stack balances.  Also calculate maxvar and maxstack
        try {
            balance(first, 0);
        } catch (CompilerError e) {
            System.out.println("ERROR: " + e);
            listing(System.out);
            throw e;
        }

        // Assign PCs
        int pc = 0, nexceptions = 0;
        for (Instruction inst = first ; inst != null ; inst = inst.next) {
            inst.pc = pc;
            int sz = inst.size(tab);
            if (pc<65536 && (pc+sz)>=65536) {
               env.error(inst.where, "warn.method.too.long");
            }
            pc += sz;

            if (inst.opc == opc_try) {
                nexceptions += ((TryData)inst.value).catches.size();
            }
        }

        // Write header
        out.writeShort(maxdepth);
        out.writeShort(maxvar);
        out.writeInt(maxpc = pc);

        // Generate code
        for (Instruction inst = first.next ; inst != null ; inst = inst.next) {
            inst.write(out, tab);
        }

        // write exceptions
        out.writeShort(nexceptions);
        if (nexceptions > 0) {
            //listing(System.out);
            writeExceptions(env, out, tab, first, last);
        }
    }

    /**
     * Write the exceptions table
     */
    void writeExceptions(Environment env, DataOutputStream out, ConstantPool tab, Instruction first, Instruction last) throws IOException {
        for (Instruction inst = first ; inst != last.next ; inst = inst.next) {
            if (inst.opc == opc_try) {
                TryData td = (TryData)inst.value;
                writeExceptions(env, out, tab, inst.next, td.getEndLabel());
                for (Enumeration e = td.catches.elements() ; e.hasMoreElements();) {
                    CatchData cd = (CatchData)e.nextElement();
                    //System.out.println("EXCEPTION: " + env.getSource() + ", pc=" + inst.pc + ", end=" + td.getEndLabel().pc + ", hdl=" + cd.getLabel().pc + ", tp=" + cd.getType());
                    out.writeShort(inst.pc);
                    out.writeShort(td.getEndLabel().pc);
                    out.writeShort(cd.getLabel().pc);
                    if (cd.getType() != null) {
                        out.writeShort(tab.index(cd.getType()));
                    } else {
                        out.writeShort(0);
                    }
                }
                inst = td.getEndLabel();
            }
        }
    }

//JCOV
    /**
     * Write the coverage table
     */
    public void writeCoverageTable(Environment env, ClassDefinition c, DataOutputStream out, ConstantPool tab, long whereField) throws IOException {
        Vector TableLot = new Vector();         /* Coverage table */
        boolean begseg = false;
        boolean begmeth = false;
        long whereClass = ((SourceClass)c).getWhere();
        Vector whereTry = new Vector();
        int numberTry = 0;
        int count = 0;

        for (Instruction inst = first ; inst != null ; inst = inst.next) {
            long n = (inst.where >> WHEREOFFSETBITS);
            if (n > 0 && inst.opc != opc_label) {
                if (!begmeth) {
                  if ( whereClass == inst.where)
                        TableLot.addElement(new Cover(CT_FIKT_METHOD, whereField, inst.pc));
                  else
                        TableLot.addElement(new Cover(CT_METHOD, whereField, inst.pc));
                  count++;
                  begmeth = true;
                }
                if (!begseg && !inst.flagNoCovered ) {
                  boolean findTry = false;
                  for (Enumeration e = whereTry.elements(); e.hasMoreElements();) {
                       if ( ((Long)(e.nextElement())).longValue() == inst.where) {
                              findTry = true;
                              break;
                       }
                  }
                  if (!findTry) {
                      TableLot.addElement(new Cover(CT_BLOCK, inst.where, inst.pc));
                      count++;
                      begseg = true;
                  }
                }
            }
            switch (inst.opc) {
              case opc_label:
                begseg = false;
                break;
              case opc_ifeq:
              case opc_ifne:
              case opc_ifnull:
              case opc_ifnonnull:
              case opc_ifgt:
              case opc_ifge:
              case opc_iflt:
              case opc_ifle:
              case opc_if_icmpeq:
              case opc_if_icmpne:
              case opc_if_icmpgt:
              case opc_if_icmpge:
              case opc_if_icmplt:
              case opc_if_icmple:
              case opc_if_acmpeq:
              case opc_if_acmpne: {
                if ( inst.flagCondInverted ) {
                   TableLot.addElement(new Cover(CT_BRANCH_TRUE, inst.where, inst.pc));
                   TableLot.addElement(new Cover(CT_BRANCH_FALSE, inst.where, inst.pc));
                } else {
                   TableLot.addElement(new Cover(CT_BRANCH_FALSE, inst.where, inst.pc));
                   TableLot.addElement(new Cover(CT_BRANCH_TRUE, inst.where, inst.pc));
                }
                count += 2;
                begseg = false;
                break;
              }

              case opc_goto: {
                begseg = false;
                break;
              }

              case opc_ret:
              case opc_return:
              case opc_ireturn:
              case opc_lreturn:
              case opc_freturn:
              case opc_dreturn:
              case opc_areturn:
              case opc_athrow: {
                break;
              }

              case opc_try: {
                whereTry.addElement(new Long(inst.where));
                begseg = false;
                break;
              }

              case opc_tableswitch: {
                SwitchData sw = (SwitchData)inst.value;
                for (int i = sw.minValue; i <= sw.maxValue; i++) {
                     TableLot.addElement(new Cover(CT_CASE, sw.whereCase(new Integer(i)), inst.pc));
                     count++;
                }
                if (!sw.getDefault()) {
                     TableLot.addElement(new Cover(CT_SWITH_WO_DEF, inst.where, inst.pc));
                     count++;
                } else {
                     TableLot.addElement(new Cover(CT_CASE, sw.whereCase("default"), inst.pc));
                     count++;
                }
                begseg = false;
                break;
              }
              case opc_lookupswitch: {
                SwitchData sw = (SwitchData)inst.value;
                for (Enumeration e = sw.sortedKeys(); e.hasMoreElements() ; ) {
                     Integer v = (Integer)e.nextElement();
                     TableLot.addElement(new Cover(CT_CASE, sw.whereCase(v), inst.pc));
                     count++;
                }
                if (!sw.getDefault()) {
                     TableLot.addElement(new Cover(CT_SWITH_WO_DEF, inst.where, inst.pc));
                     count++;
                } else {
                     TableLot.addElement(new Cover(CT_CASE, sw.whereCase("default"), inst.pc));
                     count++;
                }
                begseg = false;
                break;
              }
            }
        }
        Cover Lot;
        long ln, pos;

        out.writeShort(count);
        for (int i = 0; i < count; i++) {
           Lot = (Cover)TableLot.elementAt(i);
           ln = (Lot.Addr >> WHEREOFFSETBITS);
           pos = (Lot.Addr << (64 - WHEREOFFSETBITS)) >> (64 - WHEREOFFSETBITS);
           out.writeShort(Lot.NumCommand);
           out.writeShort(Lot.Type);
           out.writeInt((int)ln);
           out.writeInt((int)pos);

           if ( !(Lot.Type == CT_CASE && Lot.Addr == 0) ) {
                JcovClassCountArray[Lot.Type]++;
           }
        }

    }

/*
 *  Increase count of methods for native methods
 */

public void addNativeToJcovTab(Environment env, ClassDefinition c) {
        JcovClassCountArray[CT_METHOD]++;
}

/*
 *  Create class jcov element
 */

private String createClassJcovElement(Environment env, ClassDefinition c) {
        String SourceClass = (Type.mangleInnerType((c.getClassDeclaration()).getName())).toString();
        String ConvSourceClass;
        String classJcovLine;

        SourceClassList.addElement(SourceClass);
        ConvSourceClass = SourceClass.replace('.', '/');
        classJcovLine = JcovClassLine + ConvSourceClass;

        classJcovLine = classJcovLine + " [";
        String blank = "";

        for (int i = 0; i < arrayModifiers.length; i++ ) {
            if ((c.getModifiers() & arrayModifiers[i]) != 0) {
                classJcovLine = classJcovLine + blank + opNames[arrayModifiersOpc[i]];
                blank = " ";
            }
        }
        classJcovLine = classJcovLine + "]";

        return classJcovLine;
}

/*
 *  generate coverage data
 */

public void GenVecJCov(Environment env, ClassDefinition c, long Time) {
        String SourceFile = ((SourceClass)c).getAbsoluteName();

        TmpCovTable.addElement(createClassJcovElement(env, c));
        TmpCovTable.addElement(JcovSrcfileLine + SourceFile);
        TmpCovTable.addElement(JcovTimestampLine + Time);
        TmpCovTable.addElement(JcovDataLine + "A");             // data format
        TmpCovTable.addElement(JcovHeadingLine);

        for (int i = CT_FIRST_KIND; i <= CT_LAST_KIND; i++) {
            if (JcovClassCountArray[i] != 0) {
                TmpCovTable.addElement(new String(i + "\t" + JcovClassCountArray[i]));
                JcovClassCountArray[i] = 0;
            }
        }
}


/*
 * generate file of coverage data
 */

public void GenJCov(Environment env) {

     try {
        File outFile = env.getcovFile();
        if( outFile.exists()) {
           DataInputStream JCovd = new DataInputStream(
                                                       new BufferedInputStream(
                                                                               new FileInputStream(outFile)));
           String CurrLine = null;
           boolean first = true;
           String Class;

           CurrLine = JCovd.readLine();
           if ((CurrLine != null) && CurrLine.startsWith(JcovMagicLine)) {
                // this is a good Jcov file

                   while((CurrLine = JCovd.readLine()) != null ) {
                      if ( CurrLine.startsWith(JcovClassLine) ) {
                             first = true;
                             for(Enumeration e = SourceClassList.elements(); e.hasMoreElements();) {
                                 String clsName = CurrLine.substring(JcovClassLine.length());
                                 int idx = clsName.indexOf(' ');

                                 if (idx != -1) {
                                     clsName = clsName.substring(0, idx);
                                 }
                                 Class = (String)e.nextElement();
                                 if ( Class.compareTo(clsName) == 0) {
                                     first = false;
                                     break;
                                 }
                             }
                      }
                      if (first)        // re-write old class
                          TmpCovTable.addElement(CurrLine);
                   }
           }
           JCovd.close();
        }
        PrintStream CovFile = new PrintStream(new DataOutputStream(new FileOutputStream(outFile)));
        CovFile.println(JcovMagicLine);
        for(Enumeration e = TmpCovTable.elements(); e.hasMoreElements();) {
              CovFile.println(e.nextElement());
        }
        CovFile.close();
    }
    catch (FileNotFoundException e) {
       System.out.println("ERROR: " + e);
    }
    catch (IOException e) {
       System.out.println("ERROR: " + e);
    }
}
// end JCOV


    /**
     * Write the linenumber table
     */
    public void writeLineNumberTable(Environment env, DataOutputStream out, ConstantPool tab) throws IOException {
        long ln = -1;
        int count = 0;

        for (Instruction inst = first ; inst != null ; inst = inst.next) {
            long n = (inst.where >> WHEREOFFSETBITS);
            if ((n > 0) && (ln != n)) {
                ln = n;
                count++;
            }
        }

        ln = -1;
        out.writeShort(count);
        for (Instruction inst = first ; inst != null ; inst = inst.next) {
            long n = (inst.where >> WHEREOFFSETBITS);
            if ((n > 0) && (ln != n)) {
                ln = n;
                out.writeShort(inst.pc);
                out.writeShort((int)ln);
                //System.out.println("pc = " + inst.pc + ", ln = " + ln);
            }
        }
    }

    /**
     * Figure out when registers contain a legal value. This is done
     * using a simple data flow algorithm. This information is later used
     * to generate the local variable table.
     */
    void flowFields(Environment env, Label lbl, MemberDefinition locals[]) {
        if (lbl.locals != null) {
            // Been here before. Erase any conflicts.
            MemberDefinition f[] = lbl.locals;
            for (int i = 0 ; i < maxvar ; i++) {
                if (f[i] != locals[i]) {
                    f[i] = null;
                }
            }
            return;
        }

        // Remember the set of active registers at this point
        lbl.locals = new MemberDefinition[maxvar];
        System.arraycopy(locals, 0, lbl.locals, 0, maxvar);

        MemberDefinition newlocals[] = new MemberDefinition[maxvar];
        System.arraycopy(locals, 0, newlocals, 0, maxvar);
        locals = newlocals;

        for (Instruction inst = lbl.next ; inst != null ; inst = inst.next)  {
            switch (inst.opc) {
              case opc_istore:   case opc_istore_0: case opc_istore_1:
              case opc_istore_2: case opc_istore_3:
              case opc_fstore:   case opc_fstore_0: case opc_fstore_1:
              case opc_fstore_2: case opc_fstore_3:
              case opc_astore:   case opc_astore_0: case opc_astore_1:
              case opc_astore_2: case opc_astore_3:
              case opc_lstore:   case opc_lstore_0: case opc_lstore_1:
              case opc_lstore_2: case opc_lstore_3:
              case opc_dstore:   case opc_dstore_0: case opc_dstore_1:
              case opc_dstore_2: case opc_dstore_3:
                if (inst.value instanceof LocalVariable) {
                    LocalVariable v = (LocalVariable)inst.value;
                    locals[v.slot] = v.field;
                }
                break;

              case opc_label:
                flowFields(env, (Label)inst, locals);
                return;

              case opc_ifeq: case opc_ifne: case opc_ifgt:
              case opc_ifge: case opc_iflt: case opc_ifle:
              case opc_if_icmpeq: case opc_if_icmpne: case opc_if_icmpgt:
              case opc_if_icmpge: case opc_if_icmplt: case opc_if_icmple:
              case opc_if_acmpeq: case opc_if_acmpne:
              case opc_ifnull: case opc_ifnonnull:
              case opc_jsr:
                flowFields(env, (Label)inst.value, locals);
                break;

              case opc_goto:
                flowFields(env, (Label)inst.value, locals);
                return;

              case opc_return:   case opc_ireturn:  case opc_lreturn:
              case opc_freturn:  case opc_dreturn:  case opc_areturn:
              case opc_athrow:   case opc_ret:
                return;

              case opc_tableswitch:
              case opc_lookupswitch: {
                SwitchData sw = (SwitchData)inst.value;
                flowFields(env, sw.defaultLabel, locals);
                for (Enumeration e = sw.tab.elements() ; e.hasMoreElements();) {
                    flowFields(env, (Label)e.nextElement(), locals);
                }
                return;
              }

              case opc_try: {
                Vector catches = ((TryData)inst.value).catches;
                for (Enumeration e = catches.elements(); e.hasMoreElements();) {
                    CatchData cd = (CatchData)e.nextElement();
                    flowFields(env, cd.getLabel(), locals);
                }
                break;
              }
            }
        }
    }

    /**
     * Write the local variable table. The necessary constants have already been
     * added to the constant table by the collect() method. The flowFields method
     * is used to determine which variables are alive at each pc.
     */
    public void writeLocalVariableTable(Environment env, MemberDefinition field, DataOutputStream out, ConstantPool tab) throws IOException {
        MemberDefinition locals[] = new MemberDefinition[maxvar];
        int i = 0;

        // Initialize arguments
        if ((field != null) && (field.getArguments() != null)) {
            int reg = 0;
            Vector v = field.getArguments();
            for (Enumeration e = v.elements(); e.hasMoreElements(); ) {
                MemberDefinition f = ((MemberDefinition)e.nextElement());
                locals[reg] = f;
                reg += f.getType().stackSize();
            }
        }

        flowFields(env, first, locals);
        LocalVariableTable lvtab = new LocalVariableTable();

        // Initialize arguments again
        for (i = 0; i < maxvar; i++)
            locals[i] = null;
        if ((field != null) && (field.getArguments() != null)) {
            int reg = 0;
            Vector v = field.getArguments();
            for (Enumeration e = v.elements(); e.hasMoreElements(); ) {
                MemberDefinition f = ((MemberDefinition)e.nextElement());
                locals[reg] = f;
                lvtab.define(f, reg, 0, maxpc);
                reg += f.getType().stackSize();
            }
        }

        int pcs[] = new int[maxvar];

        for (Instruction inst = first ; inst != null ; inst = inst.next)  {
            switch (inst.opc) {
              case opc_istore:   case opc_istore_0: case opc_istore_1:
              case opc_istore_2: case opc_istore_3: case opc_fstore:
              case opc_fstore_0: case opc_fstore_1: case opc_fstore_2:
              case opc_fstore_3:
              case opc_astore:   case opc_astore_0: case opc_astore_1:
              case opc_astore_2: case opc_astore_3:
              case opc_lstore:   case opc_lstore_0: case opc_lstore_1:
              case opc_lstore_2: case opc_lstore_3:
              case opc_dstore:   case opc_dstore_0: case opc_dstore_1:
              case opc_dstore_2: case opc_dstore_3:
                if (inst.value instanceof LocalVariable) {
                    LocalVariable v = (LocalVariable)inst.value;
                    int pc = (inst.next != null) ? inst.next.pc : inst.pc;
                    if (locals[v.slot] != null) {
                        lvtab.define(locals[v.slot], v.slot, pcs[v.slot], pc);
                    }
                    pcs[v.slot] = pc;
                    locals[v.slot] = v.field;
                }
                break;

              case opc_label: {
                // flush  previous labels
                for (i = 0 ; i < maxvar ; i++) {
                    if (locals[i] != null) {
                        lvtab.define(locals[i], i, pcs[i], inst.pc);
                    }
                }
                // init new labels
                int pc = inst.pc;
                MemberDefinition[] labelLocals = ((Label)inst).locals;
                if (labelLocals == null) { // unreachable code??
                    for (i = 0; i < maxvar; i++)
                        locals[i] = null;
                } else {
                    System.arraycopy(labelLocals, 0, locals, 0, maxvar);
                }
                for (i = 0 ; i < maxvar ; i++) {
                    pcs[i] = pc;
                }
                break;
              }
            }
        }

        // flush  remaining labels
        for (i = 0 ; i < maxvar ; i++) {
            if (locals[i] != null) {
                lvtab.define(locals[i], i, pcs[i], maxpc);
            }
        }

        // write the local variable table
        lvtab.write(env, out, tab);
    }

    /**
     * Return true if empty
     */
    public boolean empty() {
        return first == last;
    }

    /**
     * Print the byte codes
     */
    public void listing(PrintStream out) {
        out.println("-- listing --");
        for (Instruction inst = first ; inst != null ; inst = inst.next) {
            out.println(inst.toString());
        }
    }
}
