/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;


import com.sun.org.apache.bcel.internal.Constants;

/**
 * This interface contains shareable instruction objects.
 *
 * In order to save memory you can use some instructions multiply,
 * since they have an immutable state and are directly derived from
 * Instruction.  I.e. they have no instance fields that could be
 * changed. Since some of these instructions like ICONST_0 occur
 * very frequently this can save a lot of time and space. This
 * feature is an adaptation of the FlyWeight design pattern, we
 * just use an array instead of a factory.
 *
 * The Instructions can also accessed directly under their names, so
 * it's possible to write il.append(Instruction.ICONST_0);
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public interface InstructionConstants {
  /** Predefined instruction objects
   */
  public static final Instruction           NOP          = new NOP();
  public static final Instruction           ACONST_NULL  = new ACONST_NULL();
  public static final Instruction           ICONST_M1    = new ICONST(-1);
  public static final Instruction           ICONST_0     = new ICONST(0);
  public static final Instruction           ICONST_1     = new ICONST(1);
  public static final Instruction           ICONST_2     = new ICONST(2);
  public static final Instruction           ICONST_3     = new ICONST(3);
  public static final Instruction           ICONST_4     = new ICONST(4);
  public static final Instruction           ICONST_5     = new ICONST(5);
  public static final Instruction           LCONST_0     = new LCONST(0);
  public static final Instruction           LCONST_1     = new LCONST(1);
  public static final Instruction           FCONST_0     = new FCONST(0);
  public static final Instruction           FCONST_1     = new FCONST(1);
  public static final Instruction           FCONST_2     = new FCONST(2);
  public static final Instruction           DCONST_0     = new DCONST(0);
  public static final Instruction           DCONST_1     = new DCONST(1);
  public static final ArrayInstruction      IALOAD       = new IALOAD();
  public static final ArrayInstruction      LALOAD       = new LALOAD();
  public static final ArrayInstruction      FALOAD       = new FALOAD();
  public static final ArrayInstruction      DALOAD       = new DALOAD();
  public static final ArrayInstruction      AALOAD       = new AALOAD();
  public static final ArrayInstruction      BALOAD       = new BALOAD();
  public static final ArrayInstruction      CALOAD       = new CALOAD();
  public static final ArrayInstruction      SALOAD       = new SALOAD();
  public static final ArrayInstruction      IASTORE      = new IASTORE();
  public static final ArrayInstruction      LASTORE      = new LASTORE();
  public static final ArrayInstruction      FASTORE      = new FASTORE();
  public static final ArrayInstruction      DASTORE      = new DASTORE();
  public static final ArrayInstruction      AASTORE      = new AASTORE();
  public static final ArrayInstruction      BASTORE      = new BASTORE();
  public static final ArrayInstruction      CASTORE      = new CASTORE();
  public static final ArrayInstruction      SASTORE      = new SASTORE();
  public static final StackInstruction      POP          = new POP();
  public static final StackInstruction      POP2         = new POP2();
  public static final StackInstruction      DUP          = new DUP();
  public static final StackInstruction      DUP_X1       = new DUP_X1();
  public static final StackInstruction      DUP_X2       = new DUP_X2();
  public static final StackInstruction      DUP2         = new DUP2();
  public static final StackInstruction      DUP2_X1      = new DUP2_X1();
  public static final StackInstruction      DUP2_X2      = new DUP2_X2();
  public static final StackInstruction      SWAP         = new SWAP();
  public static final ArithmeticInstruction IADD         = new IADD();
  public static final ArithmeticInstruction LADD         = new LADD();
  public static final ArithmeticInstruction FADD         = new FADD();
  public static final ArithmeticInstruction DADD         = new DADD();
  public static final ArithmeticInstruction ISUB         = new ISUB();
  public static final ArithmeticInstruction LSUB         = new LSUB();
  public static final ArithmeticInstruction FSUB         = new FSUB();
  public static final ArithmeticInstruction DSUB         = new DSUB();
  public static final ArithmeticInstruction IMUL         = new IMUL();
  public static final ArithmeticInstruction LMUL         = new LMUL();
  public static final ArithmeticInstruction FMUL         = new FMUL();
  public static final ArithmeticInstruction DMUL         = new DMUL();
  public static final ArithmeticInstruction IDIV         = new IDIV();
  public static final ArithmeticInstruction LDIV         = new LDIV();
  public static final ArithmeticInstruction FDIV         = new FDIV();
  public static final ArithmeticInstruction DDIV         = new DDIV();
  public static final ArithmeticInstruction IREM         = new IREM();
  public static final ArithmeticInstruction LREM         = new LREM();
  public static final ArithmeticInstruction FREM         = new FREM();
  public static final ArithmeticInstruction DREM         = new DREM();
  public static final ArithmeticInstruction INEG         = new INEG();
  public static final ArithmeticInstruction LNEG         = new LNEG();
  public static final ArithmeticInstruction FNEG         = new FNEG();
  public static final ArithmeticInstruction DNEG         = new DNEG();
  public static final ArithmeticInstruction ISHL         = new ISHL();
  public static final ArithmeticInstruction LSHL         = new LSHL();
  public static final ArithmeticInstruction ISHR         = new ISHR();
  public static final ArithmeticInstruction LSHR         = new LSHR();
  public static final ArithmeticInstruction IUSHR        = new IUSHR();
  public static final ArithmeticInstruction LUSHR        = new LUSHR();
  public static final ArithmeticInstruction IAND         = new IAND();
  public static final ArithmeticInstruction LAND         = new LAND();
  public static final ArithmeticInstruction IOR          = new IOR();
  public static final ArithmeticInstruction LOR          = new LOR();
  public static final ArithmeticInstruction IXOR         = new IXOR();
  public static final ArithmeticInstruction LXOR         = new LXOR();
  public static final ConversionInstruction I2L          = new I2L();
  public static final ConversionInstruction I2F          = new I2F();
  public static final ConversionInstruction I2D          = new I2D();
  public static final ConversionInstruction L2I          = new L2I();
  public static final ConversionInstruction L2F          = new L2F();
  public static final ConversionInstruction L2D          = new L2D();
  public static final ConversionInstruction F2I          = new F2I();
  public static final ConversionInstruction F2L          = new F2L();
  public static final ConversionInstruction F2D          = new F2D();
  public static final ConversionInstruction D2I          = new D2I();
  public static final ConversionInstruction D2L          = new D2L();
  public static final ConversionInstruction D2F          = new D2F();
  public static final ConversionInstruction I2B          = new I2B();
  public static final ConversionInstruction I2C          = new I2C();
  public static final ConversionInstruction I2S          = new I2S();
  public static final Instruction           LCMP         = new LCMP();
  public static final Instruction           FCMPL        = new FCMPL();
  public static final Instruction           FCMPG        = new FCMPG();
  public static final Instruction           DCMPL        = new DCMPL();
  public static final Instruction           DCMPG        = new DCMPG();
  public static final ReturnInstruction     IRETURN      = new IRETURN();
  public static final ReturnInstruction     LRETURN      = new LRETURN();
  public static final ReturnInstruction     FRETURN      = new FRETURN();
  public static final ReturnInstruction     DRETURN      = new DRETURN();
  public static final ReturnInstruction     ARETURN      = new ARETURN();
  public static final ReturnInstruction     RETURN       = new RETURN();
  public static final Instruction           ARRAYLENGTH  = new ARRAYLENGTH();
  public static final Instruction           ATHROW       = new ATHROW();
  public static final Instruction           MONITORENTER = new MONITORENTER();
  public static final Instruction           MONITOREXIT  = new MONITOREXIT();

  /** You can use these constants in multiple places safely, if you can guarantee
   * that you will never alter their internal values, e.g. call setIndex().
   */
  public static final LocalVariableInstruction THIS    = new ALOAD(0);
  public static final LocalVariableInstruction ALOAD_0 = THIS;
  public static final LocalVariableInstruction ALOAD_1 = new ALOAD(1);
  public static final LocalVariableInstruction ALOAD_2 = new ALOAD(2);
  public static final LocalVariableInstruction ILOAD_0 = new ILOAD(0);
  public static final LocalVariableInstruction ILOAD_1 = new ILOAD(1);
  public static final LocalVariableInstruction ILOAD_2 = new ILOAD(2);
  public static final LocalVariableInstruction ASTORE_0 = new ASTORE(0);
  public static final LocalVariableInstruction ASTORE_1 = new ASTORE(1);
  public static final LocalVariableInstruction ASTORE_2 = new ASTORE(2);
  public static final LocalVariableInstruction ISTORE_0 = new ISTORE(0);
  public static final LocalVariableInstruction ISTORE_1 = new ISTORE(1);
  public static final LocalVariableInstruction ISTORE_2 = new ISTORE(2);


  /** Get object via its opcode, for immutable instructions like
   * branch instructions entries are set to null.
   */
  public static final Instruction[] INSTRUCTIONS = new Instruction[256];

  /** Interfaces may have no static initializers, so we simulate this
   * with an inner class.
   */
  static final Clinit bla = new Clinit();

  static class Clinit {
    Clinit() {
      INSTRUCTIONS[Constants.NOP] = NOP;
      INSTRUCTIONS[Constants.ACONST_NULL] = ACONST_NULL;
      INSTRUCTIONS[Constants.ICONST_M1] = ICONST_M1;
      INSTRUCTIONS[Constants.ICONST_0] = ICONST_0;
      INSTRUCTIONS[Constants.ICONST_1] = ICONST_1;
      INSTRUCTIONS[Constants.ICONST_2] = ICONST_2;
      INSTRUCTIONS[Constants.ICONST_3] = ICONST_3;
      INSTRUCTIONS[Constants.ICONST_4] = ICONST_4;
      INSTRUCTIONS[Constants.ICONST_5] = ICONST_5;
      INSTRUCTIONS[Constants.LCONST_0] = LCONST_0;
      INSTRUCTIONS[Constants.LCONST_1] = LCONST_1;
      INSTRUCTIONS[Constants.FCONST_0] = FCONST_0;
      INSTRUCTIONS[Constants.FCONST_1] = FCONST_1;
      INSTRUCTIONS[Constants.FCONST_2] = FCONST_2;
      INSTRUCTIONS[Constants.DCONST_0] = DCONST_0;
      INSTRUCTIONS[Constants.DCONST_1] = DCONST_1;
      INSTRUCTIONS[Constants.IALOAD] = IALOAD;
      INSTRUCTIONS[Constants.LALOAD] = LALOAD;
      INSTRUCTIONS[Constants.FALOAD] = FALOAD;
      INSTRUCTIONS[Constants.DALOAD] = DALOAD;
      INSTRUCTIONS[Constants.AALOAD] = AALOAD;
      INSTRUCTIONS[Constants.BALOAD] = BALOAD;
      INSTRUCTIONS[Constants.CALOAD] = CALOAD;
      INSTRUCTIONS[Constants.SALOAD] = SALOAD;
      INSTRUCTIONS[Constants.IASTORE] = IASTORE;
      INSTRUCTIONS[Constants.LASTORE] = LASTORE;
      INSTRUCTIONS[Constants.FASTORE] = FASTORE;
      INSTRUCTIONS[Constants.DASTORE] = DASTORE;
      INSTRUCTIONS[Constants.AASTORE] = AASTORE;
      INSTRUCTIONS[Constants.BASTORE] = BASTORE;
      INSTRUCTIONS[Constants.CASTORE] = CASTORE;
      INSTRUCTIONS[Constants.SASTORE] = SASTORE;
      INSTRUCTIONS[Constants.POP] = POP;
      INSTRUCTIONS[Constants.POP2] = POP2;
      INSTRUCTIONS[Constants.DUP] = DUP;
      INSTRUCTIONS[Constants.DUP_X1] = DUP_X1;
      INSTRUCTIONS[Constants.DUP_X2] = DUP_X2;
      INSTRUCTIONS[Constants.DUP2] = DUP2;
      INSTRUCTIONS[Constants.DUP2_X1] = DUP2_X1;
      INSTRUCTIONS[Constants.DUP2_X2] = DUP2_X2;
      INSTRUCTIONS[Constants.SWAP] = SWAP;
      INSTRUCTIONS[Constants.IADD] = IADD;
      INSTRUCTIONS[Constants.LADD] = LADD;
      INSTRUCTIONS[Constants.FADD] = FADD;
      INSTRUCTIONS[Constants.DADD] = DADD;
      INSTRUCTIONS[Constants.ISUB] = ISUB;
      INSTRUCTIONS[Constants.LSUB] = LSUB;
      INSTRUCTIONS[Constants.FSUB] = FSUB;
      INSTRUCTIONS[Constants.DSUB] = DSUB;
      INSTRUCTIONS[Constants.IMUL] = IMUL;
      INSTRUCTIONS[Constants.LMUL] = LMUL;
      INSTRUCTIONS[Constants.FMUL] = FMUL;
      INSTRUCTIONS[Constants.DMUL] = DMUL;
      INSTRUCTIONS[Constants.IDIV] = IDIV;
      INSTRUCTIONS[Constants.LDIV] = LDIV;
      INSTRUCTIONS[Constants.FDIV] = FDIV;
      INSTRUCTIONS[Constants.DDIV] = DDIV;
      INSTRUCTIONS[Constants.IREM] = IREM;
      INSTRUCTIONS[Constants.LREM] = LREM;
      INSTRUCTIONS[Constants.FREM] = FREM;
      INSTRUCTIONS[Constants.DREM] = DREM;
      INSTRUCTIONS[Constants.INEG] = INEG;
      INSTRUCTIONS[Constants.LNEG] = LNEG;
      INSTRUCTIONS[Constants.FNEG] = FNEG;
      INSTRUCTIONS[Constants.DNEG] = DNEG;
      INSTRUCTIONS[Constants.ISHL] = ISHL;
      INSTRUCTIONS[Constants.LSHL] = LSHL;
      INSTRUCTIONS[Constants.ISHR] = ISHR;
      INSTRUCTIONS[Constants.LSHR] = LSHR;
      INSTRUCTIONS[Constants.IUSHR] = IUSHR;
      INSTRUCTIONS[Constants.LUSHR] = LUSHR;
      INSTRUCTIONS[Constants.IAND] = IAND;
      INSTRUCTIONS[Constants.LAND] = LAND;
      INSTRUCTIONS[Constants.IOR] = IOR;
      INSTRUCTIONS[Constants.LOR] = LOR;
      INSTRUCTIONS[Constants.IXOR] = IXOR;
      INSTRUCTIONS[Constants.LXOR] = LXOR;
      INSTRUCTIONS[Constants.I2L] = I2L;
      INSTRUCTIONS[Constants.I2F] = I2F;
      INSTRUCTIONS[Constants.I2D] = I2D;
      INSTRUCTIONS[Constants.L2I] = L2I;
      INSTRUCTIONS[Constants.L2F] = L2F;
      INSTRUCTIONS[Constants.L2D] = L2D;
      INSTRUCTIONS[Constants.F2I] = F2I;
      INSTRUCTIONS[Constants.F2L] = F2L;
      INSTRUCTIONS[Constants.F2D] = F2D;
      INSTRUCTIONS[Constants.D2I] = D2I;
      INSTRUCTIONS[Constants.D2L] = D2L;
      INSTRUCTIONS[Constants.D2F] = D2F;
      INSTRUCTIONS[Constants.I2B] = I2B;
      INSTRUCTIONS[Constants.I2C] = I2C;
      INSTRUCTIONS[Constants.I2S] = I2S;
      INSTRUCTIONS[Constants.LCMP] = LCMP;
      INSTRUCTIONS[Constants.FCMPL] = FCMPL;
      INSTRUCTIONS[Constants.FCMPG] = FCMPG;
      INSTRUCTIONS[Constants.DCMPL] = DCMPL;
      INSTRUCTIONS[Constants.DCMPG] = DCMPG;
      INSTRUCTIONS[Constants.IRETURN] = IRETURN;
      INSTRUCTIONS[Constants.LRETURN] = LRETURN;
      INSTRUCTIONS[Constants.FRETURN] = FRETURN;
      INSTRUCTIONS[Constants.DRETURN] = DRETURN;
      INSTRUCTIONS[Constants.ARETURN] = ARETURN;
      INSTRUCTIONS[Constants.RETURN] = RETURN;
      INSTRUCTIONS[Constants.ARRAYLENGTH] = ARRAYLENGTH;
      INSTRUCTIONS[Constants.ATHROW] = ATHROW;
      INSTRUCTIONS[Constants.MONITORENTER] = MONITORENTER;
      INSTRUCTIONS[Constants.MONITOREXIT] = MONITOREXIT;
    }
  }
}
