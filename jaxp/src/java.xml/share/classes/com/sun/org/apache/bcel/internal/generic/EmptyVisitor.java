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


/**
 * Supplies empty method bodies to be overridden by subclasses.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class EmptyVisitor implements Visitor {
  public void visitStackInstruction(StackInstruction obj) { }
  public void visitLocalVariableInstruction(LocalVariableInstruction obj) { }
  public void visitBranchInstruction(BranchInstruction obj) { }
  public void visitLoadClass(LoadClass obj) { }
  public void visitFieldInstruction(FieldInstruction obj) { }
  public void visitIfInstruction(IfInstruction obj) { }
  public void visitConversionInstruction(ConversionInstruction obj) { }
  public void visitPopInstruction(PopInstruction obj) { }
  public void visitJsrInstruction(JsrInstruction obj) { }
  public void visitGotoInstruction(GotoInstruction obj) { }
  public void visitStoreInstruction(StoreInstruction obj) { }
  public void visitTypedInstruction(TypedInstruction obj) { }
  public void visitSelect(Select obj) { }
  public void visitUnconditionalBranch(UnconditionalBranch obj) { }
  public void visitPushInstruction(PushInstruction obj) { }
  public void visitArithmeticInstruction(ArithmeticInstruction obj) { }
  public void visitCPInstruction(CPInstruction obj) { }
  public void visitInvokeInstruction(InvokeInstruction obj) { }
  public void visitArrayInstruction(ArrayInstruction obj) { }
  public void visitAllocationInstruction(AllocationInstruction obj) { }
  public void visitReturnInstruction(ReturnInstruction obj) { }
  public void visitFieldOrMethod(FieldOrMethod obj) { }
  public void visitConstantPushInstruction(ConstantPushInstruction obj) { }
  public void visitExceptionThrower(ExceptionThrower obj) { }
  public void visitLoadInstruction(LoadInstruction obj) { }
  public void visitVariableLengthInstruction(VariableLengthInstruction obj) { }
  public void visitStackProducer(StackProducer obj) { }
  public void visitStackConsumer(StackConsumer obj) { }
  public void visitACONST_NULL(ACONST_NULL obj) { }
  public void visitGETSTATIC(GETSTATIC obj) { }
  public void visitIF_ICMPLT(IF_ICMPLT obj) { }
  public void visitMONITOREXIT(MONITOREXIT obj) { }
  public void visitIFLT(IFLT obj) { }
  public void visitLSTORE(LSTORE obj) { }
  public void visitPOP2(POP2 obj) { }
  public void visitBASTORE(BASTORE obj) { }
  public void visitISTORE(ISTORE obj) { }
  public void visitCHECKCAST(CHECKCAST obj) { }
  public void visitFCMPG(FCMPG obj) { }
  public void visitI2F(I2F obj) { }
  public void visitATHROW(ATHROW obj) { }
  public void visitDCMPL(DCMPL obj) { }
  public void visitARRAYLENGTH(ARRAYLENGTH obj) { }
  public void visitDUP(DUP obj) { }
  public void visitINVOKESTATIC(INVOKESTATIC obj) { }
  public void visitLCONST(LCONST obj) { }
  public void visitDREM(DREM obj) { }
  public void visitIFGE(IFGE obj) { }
  public void visitCALOAD(CALOAD obj) { }
  public void visitLASTORE(LASTORE obj) { }
  public void visitI2D(I2D obj) { }
  public void visitDADD(DADD obj) { }
  public void visitINVOKESPECIAL(INVOKESPECIAL obj) { }
  public void visitIAND(IAND obj) { }
  public void visitPUTFIELD(PUTFIELD obj) { }
  public void visitILOAD(ILOAD obj) { }
  public void visitDLOAD(DLOAD obj) { }
  public void visitDCONST(DCONST obj) { }
  public void visitNEW(NEW obj) { }
  public void visitIFNULL(IFNULL obj) { }
  public void visitLSUB(LSUB obj) { }
  public void visitL2I(L2I obj) { }
  public void visitISHR(ISHR obj) { }
  public void visitTABLESWITCH(TABLESWITCH obj) { }
  public void visitIINC(IINC obj) { }
  public void visitDRETURN(DRETURN obj) { }
  public void visitFSTORE(FSTORE obj) { }
  public void visitDASTORE(DASTORE obj) { }
  public void visitIALOAD(IALOAD obj) { }
  public void visitDDIV(DDIV obj) { }
  public void visitIF_ICMPGE(IF_ICMPGE obj) { }
  public void visitLAND(LAND obj) { }
  public void visitIDIV(IDIV obj) { }
  public void visitLOR(LOR obj) { }
  public void visitCASTORE(CASTORE obj) { }
  public void visitFREM(FREM obj) { }
  public void visitLDC(LDC obj) { }
  public void visitBIPUSH(BIPUSH obj) { }
  public void visitDSTORE(DSTORE obj) { }
  public void visitF2L(F2L obj) { }
  public void visitFMUL(FMUL obj) { }
  public void visitLLOAD(LLOAD obj) { }
  public void visitJSR(JSR obj) { }
  public void visitFSUB(FSUB obj) { }
  public void visitSASTORE(SASTORE obj) { }
  public void visitALOAD(ALOAD obj) { }
  public void visitDUP2_X2(DUP2_X2 obj) { }
  public void visitRETURN(RETURN obj) { }
  public void visitDALOAD(DALOAD obj) { }
  public void visitSIPUSH(SIPUSH obj) { }
  public void visitDSUB(DSUB obj) { }
  public void visitL2F(L2F obj) { }
  public void visitIF_ICMPGT(IF_ICMPGT obj) { }
  public void visitF2D(F2D obj) { }
  public void visitI2L(I2L obj) { }
  public void visitIF_ACMPNE(IF_ACMPNE obj) { }
  public void visitPOP(POP obj) { }
  public void visitI2S(I2S obj) { }
  public void visitIFEQ(IFEQ obj) { }
  public void visitSWAP(SWAP obj) { }
  public void visitIOR(IOR obj) { }
  public void visitIREM(IREM obj) { }
  public void visitIASTORE(IASTORE obj) { }
  public void visitNEWARRAY(NEWARRAY obj) { }
  public void visitINVOKEINTERFACE(INVOKEINTERFACE obj) { }
  public void visitINEG(INEG obj) { }
  public void visitLCMP(LCMP obj) { }
  public void visitJSR_W(JSR_W obj) { }
  public void visitMULTIANEWARRAY(MULTIANEWARRAY obj) { }
  public void visitDUP_X2(DUP_X2 obj) { }
  public void visitSALOAD(SALOAD obj) { }
  public void visitIFNONNULL(IFNONNULL obj) { }
  public void visitDMUL(DMUL obj) { }
  public void visitIFNE(IFNE obj) { }
  public void visitIF_ICMPLE(IF_ICMPLE obj) { }
  public void visitLDC2_W(LDC2_W obj) { }
  public void visitGETFIELD(GETFIELD obj) { }
  public void visitLADD(LADD obj) { }
  public void visitNOP(NOP obj) { }
  public void visitFALOAD(FALOAD obj) { }
  public void visitINSTANCEOF(INSTANCEOF obj) { }
  public void visitIFLE(IFLE obj) { }
  public void visitLXOR(LXOR obj) { }
  public void visitLRETURN(LRETURN obj) { }
  public void visitFCONST(FCONST obj) { }
  public void visitIUSHR(IUSHR obj) { }
  public void visitBALOAD(BALOAD obj) { }
  public void visitDUP2(DUP2 obj) { }
  public void visitIF_ACMPEQ(IF_ACMPEQ obj) { }
  public void visitIMPDEP1(IMPDEP1 obj) { }
  public void visitMONITORENTER(MONITORENTER obj) { }
  public void visitLSHL(LSHL obj) { }
  public void visitDCMPG(DCMPG obj) { }
  public void visitD2L(D2L obj) { }
  public void visitIMPDEP2(IMPDEP2 obj) { }
  public void visitL2D(L2D obj) { }
  public void visitRET(RET obj) { }
  public void visitIFGT(IFGT obj) { }
  public void visitIXOR(IXOR obj) { }
  public void visitINVOKEVIRTUAL(INVOKEVIRTUAL obj) { }
  public void visitFASTORE(FASTORE obj) { }
  public void visitIRETURN(IRETURN obj) { }
  public void visitIF_ICMPNE(IF_ICMPNE obj) { }
  public void visitFLOAD(FLOAD obj) { }
  public void visitLDIV(LDIV obj) { }
  public void visitPUTSTATIC(PUTSTATIC obj) { }
  public void visitAALOAD(AALOAD obj) { }
  public void visitD2I(D2I obj) { }
  public void visitIF_ICMPEQ(IF_ICMPEQ obj) { }
  public void visitAASTORE(AASTORE obj) { }
  public void visitARETURN(ARETURN obj) { }
  public void visitDUP2_X1(DUP2_X1 obj) { }
  public void visitFNEG(FNEG obj) { }
  public void visitGOTO_W(GOTO_W obj) { }
  public void visitD2F(D2F obj) { }
  public void visitGOTO(GOTO obj) { }
  public void visitISUB(ISUB obj) { }
  public void visitF2I(F2I obj) { }
  public void visitDNEG(DNEG obj) { }
  public void visitICONST(ICONST obj) { }
  public void visitFDIV(FDIV obj) { }
  public void visitI2B(I2B obj) { }
  public void visitLNEG(LNEG obj) { }
  public void visitLREM(LREM obj) { }
  public void visitIMUL(IMUL obj) { }
  public void visitIADD(IADD obj) { }
  public void visitLSHR(LSHR obj) { }
  public void visitLOOKUPSWITCH(LOOKUPSWITCH obj) { }
  public void visitDUP_X1(DUP_X1 obj) { }
  public void visitFCMPL(FCMPL obj) { }
  public void visitI2C(I2C obj) { }
  public void visitLMUL(LMUL obj) { }
  public void visitLUSHR(LUSHR obj) { }
  public void visitISHL(ISHL obj) { }
  public void visitLALOAD(LALOAD obj) { }
  public void visitASTORE(ASTORE obj) { }
  public void visitANEWARRAY(ANEWARRAY obj) { }
  public void visitFRETURN(FRETURN obj) { }
  public void visitFADD(FADD obj) { }
  public void visitBREAKPOINT(BREAKPOINT obj) { }
}
