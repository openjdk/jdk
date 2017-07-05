/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An Java instruction
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class Instruction implements Constants {
    long where;
    int pc;
    int opc;
    Object value;
    Instruction next;
//JCOV
    boolean flagCondInverted;        /* if true, the condition  is reversed
                                   relatively of source code */
    boolean flagNoCovered = false; /* if true, the command will
                                   ignored for coverage */


    /**
     * Constructor
     */
    public Instruction(long where, int opc, Object value, boolean flagCondInverted) {
        this.where = where;
        this.opc = opc;
        this.value = value;
        this.flagCondInverted = flagCondInverted;
    }

    /**
     * Constructor
     */
    public Instruction(boolean flagNoCovered, long where, int opc, Object value) {
        this.where = where;
        this.opc = opc;
        this.value = value;
        this.flagNoCovered = flagNoCovered;
    }

    /**
     * Constructor
     */
    public Instruction(long where, int opc, boolean flagNoCovered) {
        this.where = where;
        this.opc = opc;
        this.flagNoCovered = flagNoCovered;
    }
//end JCOV

    /**
     * Constructor
     */
    public Instruction(long where, int opc, Object value) {
        this.where = where;
        this.opc = opc;
        this.value = value;
    }

    /**
     * When deciding between a lookupswitch and a tableswitch, this
     * value is used in determining how much size increase is
     * acceptable.
     */
    public static final double SWITCHRATIO;

    static {
        // Set SWITCHRATIO from the property javac.switchratio
        // if it exists and is reasonable.  Otherwise, set
        // SWITCHRATIO to 1.5, meaning that we will accept a 1.5x
        // blowup (for the instruction) to use a tableswitch instead
        // of a lookupswitch.
        double ratio = 1.5;
        String valStr = System.getProperty("javac.switchratio");
        if (valStr != null) {
            try {
                double temp = Double.valueOf(valStr).doubleValue();
                if (!(Double.isNaN(temp) || temp < 0.0)) {
                    ratio = temp;
                }
            } catch (NumberFormatException ee) {}
        }
        SWITCHRATIO = ratio;
    }

    /**
     * Accessor
     */
    public int getOpcode() {
        return pc;
     }

    public Object getValue() {
        return value;
     }

    public void setValue(Object value) {
        this.value = value;
     }


    /**
     * Optimize
     */
    void optimize(Environment env) {
        switch (opc) {
          case opc_istore: case opc_lstore: case opc_fstore:
          case opc_dstore: case opc_astore:
            // Don't keep the LocalVariable info around, unless we
            // are actually going to generate a local variable table.
            if ((value instanceof LocalVariable) && !env.debug_vars()) {
                value = new Integer(((LocalVariable)value).slot);
            }
            break;

          case opc_goto: {
            Label lbl = (Label)value;
            value = lbl = lbl.getDestination();
            if (lbl == next) {
                // goto to the next instruction, obsolete
                opc = opc_dead;
                break;
            }

            // We optimize
            //
            //          goto Tag
            //          ...
            //    Tag:
            //          return
            //
            // except when we're generating debuggable code.  When
            // we're generating debuggable code, we leave it alone,
            // in order to provide better stepping behavior.  Consider
            // a method the end of which looks like this:
            //
            //          ...
            //          break;
            //      }   // end of loop
            //  }   // end of method
            //
            // If we optimize the goto away, we'll be left with a
            // single instruction (return) and the need to ascribe that
            // instruction to two source lines (the break statement and
            // the method's right curly).  Can't get there from here.
            // Depending on which line-number ascription we choose, the
            // stepping user will step directly from the break statement
            // back into the caller of the method (case 1) or from the
            // statement that precedes the break statement to the method's
            // right curly (case 2).  Similarly, he'll be able to set a
            // breakpoint on the break statement (case 1) or the method's
            // right curly (case 2), but not on both.  Neither case 1 nor
            // case 2 is desirable.  .We want him to see both the break
            // statement and the method's right curly when stepping,
            // and we want him to be able to set a breakpoint on either or
            // both.  So we suppress the optimization when generating
            // debuggable code.
            // (Above notes from brucek@eng in JDK1.0.2, copied here
            //  by kelly.ohair@eng for JDK1.1)
            //
            // With the changes to allow -O and -g at the same time,
            // I've changed the condition to be whether optimization is
            // on instead of the debugging flag being off.
            //     - david.stoutamire@eng for 1.2

            if (lbl.next != null && env.opt()) {
                switch (lbl.next.opc) {
                  case opc_return:  case opc_ireturn: case opc_lreturn:
                  case opc_freturn: case opc_dreturn: case opc_areturn:
                    // goto to return
                    opc = lbl.next.opc;
                    value = lbl.next.value;
                    break;
                }
            }
            break;
          }

          case opc_ifeq:   case opc_ifne:   case opc_ifgt:
          case opc_ifge:   case opc_iflt:   case opc_ifle:
          case opc_ifnull: case opc_ifnonnull:
            value = ((Label)value).getDestination();
            if (value == next) {
                // branch to next instruction, obsolete
                opc = opc_pop;
                break;
            }
            if ((next.opc == opc_goto) && (value == next.next)) {
                // Conditional branch over goto, invert
                // Note that you can't invert all conditions, condition
                // results for float/double compares are not invertable.
                switch (opc) {
                  case opc_ifeq:      opc = opc_ifne; break;
                  case opc_ifne:      opc = opc_ifeq; break;
                  case opc_iflt:      opc = opc_ifge; break;
                  case opc_ifle:      opc = opc_ifgt; break;
                  case opc_ifgt:      opc = opc_ifle; break;
                  case opc_ifge:      opc = opc_iflt; break;
                  case opc_ifnull:    opc = opc_ifnonnull; break;
                  case opc_ifnonnull: opc = opc_ifnull; break;
                }
//JCOV
                flagCondInverted = !flagCondInverted;
//end JCOV
                value = next.value;
                next.opc = opc_dead;
            }
            break;

          case opc_if_acmpeq:   case opc_if_acmpne:
          case opc_if_icmpeq:   case opc_if_icmpne:
          case opc_if_icmpgt:   case opc_if_icmpge:
          case opc_if_icmplt:   case opc_if_icmple:
            value = ((Label)value).getDestination();
            if (value == next) {
                // branch to next instruction, obsolete
                opc = opc_pop2;
                break;
            }
            if ((next.opc == opc_goto) && (value == next.next)) {
                // Conditional branch over goto, invert
                switch (opc) {
                  case opc_if_acmpeq: opc = opc_if_acmpne; break;
                  case opc_if_acmpne: opc = opc_if_acmpeq; break;
                  case opc_if_icmpeq: opc = opc_if_icmpne; break;
                  case opc_if_icmpne: opc = opc_if_icmpeq; break;
                  case opc_if_icmpgt: opc = opc_if_icmple; break;
                  case opc_if_icmpge: opc = opc_if_icmplt; break;
                  case opc_if_icmplt: opc = opc_if_icmpge; break;
                  case opc_if_icmple: opc = opc_if_icmpgt; break;
                }
//JCOV
                flagCondInverted = !flagCondInverted;
//end JCOV
                value = next.value;
                next.opc = opc_dead;
            }
            break;

          case opc_tableswitch:
          case opc_lookupswitch: {
            SwitchData sw = (SwitchData)value;
            sw.defaultLabel = sw.defaultLabel.getDestination();
            for (Enumeration<Integer> e = sw.tab.keys() ; e.hasMoreElements() ; ) {
                Integer k = e.nextElement();
                Label lbl = sw.tab.get(k);
                sw.tab.put(k, lbl.getDestination());
            }

            // Compute the approximate sizes of a tableswitch and a
            // lookupswitch.  Decide which one we want to generate.

            long range = (long)sw.maxValue - (long)sw.minValue + 1;
            long entries = sw.tab.size();

            long tableSize = 4 + range;
            long lookupSize = 3 + 2 * entries;

            if (tableSize <= lookupSize * SWITCHRATIO) {
                opc = opc_tableswitch;
            } else {
                opc = opc_lookupswitch;
            }
            break;
          }

        }
    }

    /**
     * Collect constants into the constant table
     */
    void collect(ConstantPool tab) {
        switch (opc) {
          case opc_istore:      case opc_lstore:        case opc_fstore:
          case opc_dstore:      case opc_astore:
            if (value instanceof LocalVariable) {
                MemberDefinition field = ((LocalVariable)value).field;
                tab.put(field.getName().toString());
                tab.put(field.getType().getTypeSignature());
            }
            return;

          case opc_new:                 case opc_putfield:
          case opc_putstatic:           case opc_getfield:
          case opc_getstatic:           case opc_invokevirtual:
          case opc_invokespecial:       case opc_invokestatic:
          case opc_invokeinterface:     case opc_instanceof:
          case opc_checkcast:
            tab.put(value);
            return;

          case opc_anewarray:
            tab.put(value);
            return;

          case opc_multianewarray:
            tab.put(((ArrayData)value).type);
            return;

          case opc_ldc:
          case opc_ldc_w:
            if (value instanceof Integer) {
                int v = ((Integer)value).intValue();
                if ((v >= -1) && (v <= 5)) {
                    opc = opc_iconst_0 + v;
                    return;
                } else if ((v >= -(1 << 7)) && (v < (1 << 7))) {
                    opc = opc_bipush;
                    return;
                } else if ((v >= -(1 << 15)) && (v < (1 << 15))) {
                    opc = opc_sipush;
                    return;
                }
            } else if (value instanceof Float) {
                float v = ((Float)value).floatValue();
                if (v == 0) {
                    if (Float.floatToIntBits(v) == 0) {
                        opc = opc_fconst_0;
                        return;
                    }
                } else if (v == 1) {
                    opc = opc_fconst_1;
                    return;
                } else if (v == 2) {
                    opc = opc_fconst_2;
                    return;
                }
            }
            tab.put(value);
            return;

          case opc_ldc2_w:
            if (value instanceof Long) {
                long v = ((Long)value).longValue();
                if (v == 0) {
                    opc = opc_lconst_0;
                    return;
                } else if (v == 1) {
                    opc = opc_lconst_1;
                    return;
                }
            } else if (value instanceof Double) {
                double v = ((Double)value).doubleValue();
                if (v == 0) {
                    if (Double.doubleToLongBits(v) == 0) {
                        opc = opc_dconst_0;
                        return;
                    }
                } else if (v == 1) {
                    opc = opc_dconst_1;
                    return;
                }
            }
            tab.put(value);
            return;

          case opc_try:
            for (Enumeration<CatchData> e = ((TryData)value).catches.elements() ; e.hasMoreElements() ;) {
                CatchData cd = e.nextElement();
                if (cd.getType() != null) {
                    tab.put(cd.getType());
                }
            }
            return;

          case opc_nop:
            if ((value != null) && (value instanceof ClassDeclaration))
                tab.put(value);
                return;
        }
    }

    /**
     * Balance the stack
     */
    int balance() {
        switch (opc) {
          case opc_dead:        case opc_label:         case opc_iinc:
          case opc_arraylength: case opc_laload:        case opc_daload:
          case opc_nop:         case opc_ineg:          case opc_fneg:
          case opc_lneg:        case opc_dneg:          case opc_i2f:
          case opc_f2i:         case opc_l2d:           case opc_d2l:
          case opc_i2b:         case opc_i2c:           case opc_i2s:
          case opc_jsr:         case opc_goto:          case opc_jsr_w:
          case opc_goto_w:      case opc_return:        case opc_ret:
          case opc_instanceof:  case opc_checkcast:     case opc_newarray:
          case opc_anewarray:   case opc_try:           case opc_swap:
            return 0;

          case opc_ldc:         case opc_ldc_w:         case opc_bipush:
          case opc_sipush:      case opc_aconst_null:   case opc_iconst_m1:
          case opc_iconst_0:    case opc_iconst_1:      case opc_iconst_2:
          case opc_iconst_3:    case opc_iconst_4:      case opc_iconst_5:
          case opc_fconst_0:    case opc_fconst_1:      case opc_fconst_2:
          case opc_iload:       case opc_fload:         case opc_aload:
          case opc_dup:         case opc_dup_x1:        case opc_dup_x2:
          case opc_i2l:         case opc_i2d:           case opc_f2l:
          case opc_f2d:         case opc_new:
            return 1;

          case opc_lload:       case opc_dload:         case opc_dup2:
          case opc_dup2_x1:     case opc_dup2_x2:       case opc_ldc2_w:
          case opc_lconst_0:    case opc_lconst_1:      case opc_dconst_0:
          case opc_dconst_1:
            return 2;

          case opc_istore:      case opc_fstore:        case opc_astore:
          case opc_iaload:      case opc_faload:        case opc_aaload:
          case opc_baload:      case opc_caload:        case opc_saload:
          case opc_pop:         case opc_iadd:          case opc_fadd:
          case opc_isub:        case opc_fsub:          case opc_imul:
          case opc_fmul:        case opc_idiv:          case opc_fdiv:
          case opc_irem:        case opc_frem:          case opc_ishl:
          case opc_ishr:        case opc_iushr:         case opc_lshl:
          case opc_lshr:        case opc_lushr:         case opc_iand:
          case opc_ior:         case opc_ixor:          case opc_l2i:
          case opc_l2f:         case opc_d2i:           case opc_d2f:
          case opc_ifeq:        case opc_ifne:          case opc_iflt:
          case opc_ifle:        case opc_ifgt:          case opc_ifge:
          case opc_ifnull:      case opc_ifnonnull:     case opc_fcmpl:
          case opc_fcmpg:       case opc_ireturn:       case opc_freturn:
          case opc_areturn:     case opc_tableswitch:   case opc_lookupswitch:
          case opc_athrow:      case opc_monitorenter:  case opc_monitorexit:
            return -1;

          case opc_lstore:      case opc_dstore:        case opc_pop2:
          case opc_ladd:        case opc_dadd:          case opc_lsub:
          case opc_dsub:        case opc_lmul:          case opc_dmul:
          case opc_ldiv:        case opc_ddiv:          case opc_lrem:
          case opc_drem:        case opc_land:          case opc_lor:
          case opc_lxor:        case opc_if_acmpeq:     case opc_if_acmpne:
          case opc_if_icmpeq:   case opc_if_icmpne:     case opc_if_icmplt:
          case opc_if_icmple:   case opc_if_icmpgt:     case opc_if_icmpge:
          case opc_lreturn:     case opc_dreturn:
            return -2;

          case opc_iastore:     case opc_fastore:       case opc_aastore:
          case opc_bastore:     case opc_castore:       case opc_sastore:
          case opc_lcmp:        case opc_dcmpl:         case opc_dcmpg:
            return -3;

          case opc_lastore:     case opc_dastore:
            return -4;

          case opc_multianewarray:
            return 1 - ((ArrayData)value).nargs;

          case opc_getfield:
            return ((MemberDefinition)value).getType().stackSize() - 1;

          case opc_putfield:
            return -1 - ((MemberDefinition)value).getType().stackSize();

          case opc_getstatic:
            return ((MemberDefinition)value).getType().stackSize();

          case opc_putstatic:
            return -((MemberDefinition)value).getType().stackSize();

          case opc_invokevirtual:
          case opc_invokespecial:
          case opc_invokeinterface:
            return ((MemberDefinition)value).getType().getReturnType().stackSize() -
                   (((MemberDefinition)value).getType().stackSize() + 1);

          case opc_invokestatic:
            return ((MemberDefinition)value).getType().getReturnType().stackSize() -
                   (((MemberDefinition)value).getType().stackSize());
        }
        throw new CompilerError("invalid opcode: " + toString());
    }

    /**
     * Return the size of the instruction
     */
    int size(ConstantPool tab) {
        switch (opc) {
          case opc_try:         case opc_label:         case opc_dead:
            return 0;

          case opc_bipush:      case opc_newarray:
            return 2;

          case opc_sipush:      case opc_goto:          case opc_jsr:
          case opc_ifeq:        case opc_ifne:          case opc_ifgt:
          case opc_ifge:        case opc_iflt:          case opc_ifle:
          case opc_ifnull:      case opc_ifnonnull:     case opc_if_acmpeq:
          case opc_if_acmpne:   case opc_if_icmpeq:     case opc_if_icmpne:
          case opc_if_icmpgt:   case opc_if_icmpge:     case opc_if_icmplt:
          case opc_if_icmple:
            return 3;

          case opc_ldc:
          case opc_ldc_w:
            if (tab.index(value) < 256) {
                opc = opc_ldc;
                return 2;
            } else {
                opc = opc_ldc_w;
                return 3;
            }

          case opc_iload:       case opc_lload:         case opc_fload:
          case opc_dload:       case opc_aload: {
            int v = ((Number)value).intValue();
            if (v < 4) {
                if (v < 0) {
                    throw new CompilerError("invalid slot: " + toString()
                        + "\nThis error possibly resulted from poorly constructed class paths.");
                }
                opc = opc_iload_0 + (opc - opc_iload) * 4 + v;
                return 1;
            } else if (v <= 255) {
                return 2;
            } else {
                opc += 256;     // indicate wide variant
                return 4;
            }
          }

           case opc_iinc: {
               int register = ((int[])value)[0];
               int increment = ((int[])value)[1];
               if (register < 0) {
                   throw new CompilerError("invalid slot: " + toString());
               }
               if (register <= 255 && (((byte)increment) == increment)) {
                   return 3;
               } else {
                   opc += 256;          // indicate wide variant
                   return 6;
               }
           }

          case opc_istore:      case opc_lstore:        case opc_fstore:
          case opc_dstore:      case opc_astore: {
            int v = (value instanceof Number) ?
                ((Number)value).intValue() : ((LocalVariable)value).slot;
            if (v < 4) {
                if (v < 0) {
                    throw new CompilerError("invalid slot: " + toString());
                }
                opc = opc_istore_0 + (opc - opc_istore) * 4 + v;
                return 1;
            } else if (v <= 255) {
                return 2;
            } else {
                opc += 256;     // indicate wide variant
                return 4;
            }
          }

          case opc_ret: {
              int v = ((Number)value).intValue();
              if (v <= 255) {
                  if (v < 0) {
                      throw new CompilerError("invalid slot: " + toString());
                  }
                  return 2;
              } else {
                  opc += 256;   // indicate wide variant
                  return 4;
              }
          }

          case opc_ldc2_w:              case opc_new:
          case opc_putstatic:           case opc_getstatic:
          case opc_putfield:            case opc_getfield:
          case opc_invokevirtual:       case opc_invokespecial:
          case opc_invokestatic:        case opc_instanceof:
          case opc_checkcast:           case opc_anewarray:
            return 3;

          case opc_multianewarray:
            return 4;

          case opc_invokeinterface:
          case opc_goto_w:
          case opc_jsr_w:
            return 5;

          case opc_tableswitch: {
            SwitchData sw = (SwitchData)value;
            int n = 1;
            for(; ((pc + n) % 4) != 0 ; n++);
            return n + 16 + (sw.maxValue - sw.minValue) * 4;
          }

          case opc_lookupswitch: {
            SwitchData sw = (SwitchData)value;
            int n = 1;
            for(; ((pc + n) % 4) != 0 ; n++);
            return n + 8 + sw.tab.size() * 8;
          }

          case opc_nop:
            if ((value != null) && !(value instanceof Integer))
                return 2;
            else
                return 1;
        }

        // most opcodes are only 1 byte long
        return 1;
    }

    /**
     * Generate code
     */
    @SuppressWarnings("fallthrough")
    void write(DataOutputStream out, ConstantPool tab) throws IOException {
        switch (opc) {
          case opc_try:         case opc_label:         case opc_dead:
            break;

          case opc_bipush:      case opc_newarray:
          case opc_iload:       case opc_lload:         case opc_fload:
          case opc_dload:       case opc_aload:         case opc_ret:
            out.writeByte(opc);
            out.writeByte(((Number)value).intValue());
            break;

          case opc_iload + 256:         case opc_lload + 256:
          case opc_fload + 256:         case opc_dload + 256:
          case opc_aload + 256:         case opc_ret   + 256:
            out.writeByte(opc_wide);
            out.writeByte(opc - 256);
            out.writeShort(((Number)value).intValue());
            break;

          case opc_istore:      case opc_lstore:        case opc_fstore:
          case opc_dstore:      case opc_astore:
            out.writeByte(opc);
            out.writeByte((value instanceof Number) ?
                          ((Number)value).intValue() : ((LocalVariable)value).slot);
            break;

          case opc_istore + 256:        case opc_lstore + 256:
          case opc_fstore + 256:        case opc_dstore + 256:
          case opc_astore + 256:
            out.writeByte(opc_wide);
            out.writeByte(opc - 256);
            out.writeShort((value instanceof Number) ?
                      ((Number)value).intValue() : ((LocalVariable)value).slot);
            break;

          case opc_sipush:
            out.writeByte(opc);
            out.writeShort(((Number)value).intValue());
            break;

          case opc_ldc:
            out.writeByte(opc);
            out.writeByte(tab.index(value));
            break;

          case opc_ldc_w:               case opc_ldc2_w:
          case opc_new:                 case opc_putstatic:
          case opc_getstatic:           case opc_putfield:
          case opc_getfield:            case opc_invokevirtual:
          case opc_invokespecial:       case opc_invokestatic:
          case opc_instanceof:          case opc_checkcast:
            out.writeByte(opc);
            out.writeShort(tab.index(value));
            break;

          case opc_iinc:
            out.writeByte(opc);
            out.writeByte(((int[])value)[0]); // register
            out.writeByte(((int[])value)[1]); // increment
            break;

          case opc_iinc + 256:
            out.writeByte(opc_wide);
            out.writeByte(opc - 256);
            out.writeShort(((int[])value)[0]); // register
            out.writeShort(((int[])value)[1]); // increment
            break;

          case opc_anewarray:
            out.writeByte(opc);
            out.writeShort(tab.index(value));
            break;

          case opc_multianewarray:
            out.writeByte(opc);
            out.writeShort(tab.index(((ArrayData)value).type));
            out.writeByte(((ArrayData)value).nargs);
            break;

          case opc_invokeinterface:
            out.writeByte(opc);
            out.writeShort(tab.index(value));
            out.writeByte(((MemberDefinition)value).getType().stackSize() + 1);
            out.writeByte(0);
            break;

          case opc_goto:        case opc_jsr:           case opc_ifeq:
          case opc_ifne:        case opc_ifgt:          case opc_ifge:
          case opc_iflt:        case opc_ifle:          case opc_ifnull:
          case opc_ifnonnull:   case opc_if_acmpeq:     case opc_if_acmpne:
          case opc_if_icmpeq:   case opc_if_icmpne:     case opc_if_icmpgt:
          case opc_if_icmpge:   case opc_if_icmplt:     case opc_if_icmple:
            out.writeByte(opc);
            out.writeShort(((Instruction)value).pc - pc);
            break;

          case opc_goto_w:
          case opc_jsr_w:
            out.writeByte(opc);
            out.writeLong(((Instruction)value).pc - pc);
            break;

          case opc_tableswitch: {
            SwitchData sw = (SwitchData)value;
            out.writeByte(opc);
            for(int n = 1 ; ((pc + n) % 4) != 0 ; n++) {
                out.writeByte(0);
            }
            out.writeInt(sw.defaultLabel.pc - pc);
            out.writeInt(sw.minValue);
            out.writeInt(sw.maxValue);
            for (int n = sw.minValue ; n <= sw.maxValue ; n++) {
                Label lbl = sw.get(n);
                int target_pc = (lbl != null) ? lbl.pc : sw.defaultLabel.pc;
                out.writeInt(target_pc - pc);
            }
            break;
          }

          case opc_lookupswitch: {
            SwitchData sw = (SwitchData)value;
            out.writeByte(opc);
            int n = pc + 1;
            for(; (n % 4) != 0 ; n++) {
                out.writeByte(0);
            }
            out.writeInt(sw.defaultLabel.pc - pc);
            out.writeInt(sw.tab.size());
            for (Enumeration<Integer> e = sw.sortedKeys(); e.hasMoreElements() ; ) {
                Integer v = e.nextElement();
                out.writeInt(v.intValue());
                out.writeInt(sw.get(v).pc - pc);
            }
            break;
          }

          case opc_nop:
            if (value != null) {
                if (value instanceof Integer)
                    out.writeByte(((Integer)value).intValue());
                else
                    out.writeShort(tab.index(value));
                return;
            }
            // fall through

          default:
            out.writeByte(opc);
            break;
        }
    }

    /**
     * toString
     */
    public String toString() {
        String prefix = (where >> WHEREOFFSETBITS) + ":\t";
        switch (opc) {
          case opc_try:
            return prefix + "try " + ((TryData)value).getEndLabel().hashCode();

          case opc_dead:
            return prefix + "dead";

          case opc_iinc: {
            int register = ((int[])value)[0];
            int increment = ((int[])value)[1];
            return prefix + opcNames[opc] + " " + register + ", " + increment;
          }

          default:
            if (value != null) {
                if (value instanceof Label) {
                    return prefix + opcNames[opc] + " " + value.toString();
                } else if (value instanceof Instruction) {
                    return prefix + opcNames[opc] + " " + value.hashCode();
                } else if (value instanceof String) {
                    return prefix + opcNames[opc] + " \"" + value + "\"";
                } else {
                    return prefix + opcNames[opc] + " " + value;
                }
            } else {
              return prefix + opcNames[opc];
            }
        }
    }
}
