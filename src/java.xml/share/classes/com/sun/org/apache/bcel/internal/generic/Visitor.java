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
 * Interface implementing the Visitor pattern programming style. I.e., a class that implements this interface can handle
 * all types of instructions with the properly typed methods just by calling the accept() method.
 */
public interface Visitor {

    /**
     * Visits an AALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitAALOAD(AALOAD obj);

    /**
     * Visits an AASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitAASTORE(AASTORE obj);

    /**
     * Visits an ACONST_NULL instruction.
     *
     * @param obj The instruction.
     */
    void visitACONST_NULL(ACONST_NULL obj);

    /**
     * Visits an AllocationInstruction.
     *
     * @param obj The instruction.
     */
    void visitAllocationInstruction(AllocationInstruction obj);

    /**
     * Visits an ALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitALOAD(ALOAD obj);

    /**
     * Visits an ANEWARRAY instruction.
     *
     * @param obj The instruction.
     */
    void visitANEWARRAY(ANEWARRAY obj);

    /**
     * Visits an ARETURN instruction.
     *
     * @param obj The instruction.
     */
    void visitARETURN(ARETURN obj);

    /**
     * Visits an ArithmeticInstruction.
     *
     * @param obj The instruction.
     */
    void visitArithmeticInstruction(ArithmeticInstruction obj);

    /**
     * Visits an ArrayInstruction.
     *
     * @param obj The instruction.
     */
    void visitArrayInstruction(ArrayInstruction obj);

    /**
     * Visits an ARRAYLENGTH instruction.
     *
     * @param obj The instruction.
     */
    void visitARRAYLENGTH(ARRAYLENGTH obj);

    /**
     * Visits an ASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitASTORE(ASTORE obj);

    /**
     * Visits an ATHROW instruction.
     *
     * @param obj The instruction.
     */
    void visitATHROW(ATHROW obj);

    /**
     * Visits a BALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitBALOAD(BALOAD obj);

    /**
     * Visits a BASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitBASTORE(BASTORE obj);

    /**
     * Visits a BIPUSH instruction.
     *
     * @param obj The instruction.
     */
    void visitBIPUSH(BIPUSH obj);

    /**
     * Visits a BranchInstruction.
     *
     * @param obj The instruction.
     */
    void visitBranchInstruction(BranchInstruction obj);

    /**
     * Visits a BREAKPOINT instruction.
     *
     * @param obj The instruction.
     */
    void visitBREAKPOINT(BREAKPOINT obj);

    /**
     * Visits a CALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitCALOAD(CALOAD obj);

    /**
     * Visits a CASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitCASTORE(CASTORE obj);

    /**
     * Visits a CHECKCAST instruction.
     *
     * @param obj The instruction.
     */
    void visitCHECKCAST(CHECKCAST obj);

    /**
     * Visits a ConstantPushInstruction.
     *
     * @param obj The instruction.
     */
    void visitConstantPushInstruction(ConstantPushInstruction obj);

    /**
     * Visits a ConversionInstruction.
     *
     * @param obj The instruction.
     */
    void visitConversionInstruction(ConversionInstruction obj);

    /**
     * Visits a CPInstruction.
     *
     * @param obj The instruction.
     */
    void visitCPInstruction(CPInstruction obj);

    /**
     * Visits a D2F instruction.
     *
     * @param obj The instruction.
     */
    void visitD2F(D2F obj);

    /**
     * Visits a D2I instruction.
     *
     * @param obj The instruction.
     */
    void visitD2I(D2I obj);

    /**
     * Visits a D2L instruction.
     *
     * @param obj The instruction.
     */
    void visitD2L(D2L obj);

    /**
     * Visits a DADD instruction.
     *
     * @param obj The instruction.
     */
    void visitDADD(DADD obj);

    /**
     * Visits a DALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitDALOAD(DALOAD obj);

    /**
     * Visits a DASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitDASTORE(DASTORE obj);

    /**
     * Visits a DCMPG instruction.
     *
     * @param obj The instruction.
     */
    void visitDCMPG(DCMPG obj);

    /**
     * Visits a DCMPL instruction.
     *
     * @param obj The instruction.
     */
    void visitDCMPL(DCMPL obj);

    /**
     * Visits a DCONST instruction.
     *
     * @param obj The instruction.
     */
    void visitDCONST(DCONST obj);

    /**
     * Visits a DDIV instruction.
     *
     * @param obj The instruction.
     */
    void visitDDIV(DDIV obj);

    /**
     * Visits a DLOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitDLOAD(DLOAD obj);

    /**
     * Visits a DMUL instruction.
     *
     * @param obj The instruction.
     */
    void visitDMUL(DMUL obj);

    /**
     * Visits a DNEG instruction.
     *
     * @param obj The instruction.
     */
    void visitDNEG(DNEG obj);

    /**
     * Visits a DREM instruction.
     *
     * @param obj The instruction.
     */
    void visitDREM(DREM obj);

    /**
     * Visits a DRETURN instruction.
     *
     * @param obj The instruction.
     */
    void visitDRETURN(DRETURN obj);

    /**
     * Visits a DSTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitDSTORE(DSTORE obj);

    /**
     * Visits a DSUB instruction.
     *
     * @param obj The instruction.
     */
    void visitDSUB(DSUB obj);

    /**
     * Visits a DUP instruction.
     *
     * @param obj The instruction.
     */
    void visitDUP(DUP obj);

    /**
     * Visits a DUP_X1 instruction.
     *
     * @param obj The instruction.
     */
    void visitDUP_X1(DUP_X1 obj);

    /**
     * Visits a DUP_X2 instruction.
     *
     * @param obj The instruction.
     */
    void visitDUP_X2(DUP_X2 obj);

    /**
     * Visits a DUP2 instruction.
     *
     * @param obj The instruction.
     */
    void visitDUP2(DUP2 obj);

    /**
     * Visits a DUP2_X1 instruction.
     *
     * @param obj The instruction.
     */
    void visitDUP2_X1(DUP2_X1 obj);

    /**
     * Visits a DUP2_X2 instruction.
     *
     * @param obj The instruction.
     */
    void visitDUP2_X2(DUP2_X2 obj);

    /**
     * Visits an ExceptionThrower.
     *
     * @param obj The instruction.
     */
    void visitExceptionThrower(ExceptionThrower obj);

    /**
     * Visits an F2D instruction.
     *
     * @param obj The instruction.
     */
    void visitF2D(F2D obj);

    /**
     * Visits an F2I instruction.
     *
     * @param obj The instruction.
     */
    void visitF2I(F2I obj);

    /**
     * Visits an F2L instruction.
     *
     * @param obj The instruction.
     */
    void visitF2L(F2L obj);

    /**
     * Visits an FADD instruction.
     *
     * @param obj The instruction.
     */
    void visitFADD(FADD obj);

    /**
     * Visits an FALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitFALOAD(FALOAD obj);

    /**
     * Visits an FASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitFASTORE(FASTORE obj);

    /**
     * Visits an FCMPG instruction.
     *
     * @param obj The instruction.
     */
    void visitFCMPG(FCMPG obj);

    /**
     * Visits an FCMPL instruction.
     *
     * @param obj The instruction.
     */
    void visitFCMPL(FCMPL obj);

    /**
     * Visits an FCONST instruction.
     *
     * @param obj The instruction.
     */
    void visitFCONST(FCONST obj);

    /**
     * Visits an FDIV instruction.
     *
     * @param obj The instruction.
     */
    void visitFDIV(FDIV obj);

    /**
     * Visits a FieldInstruction.
     *
     * @param obj The instruction.
     */
    void visitFieldInstruction(FieldInstruction obj);

    /**
     * Visits a FieldOrMethod.
     *
     * @param obj The instruction.
     */
    void visitFieldOrMethod(FieldOrMethod obj);

    /**
     * Visits an FLOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitFLOAD(FLOAD obj);

    /**
     * Visits an FMUL instruction.
     *
     * @param obj The instruction.
     */
    void visitFMUL(FMUL obj);

    /**
     * Visits an FNEG instruction.
     *
     * @param obj The instruction.
     */
    void visitFNEG(FNEG obj);

    /**
     * Visits an FREM instruction.
     *
     * @param obj The instruction.
     */
    void visitFREM(FREM obj);

    /**
     * Visits an FRETURN instruction.
     *
     * @param obj The instruction.
     */
    void visitFRETURN(FRETURN obj);

    /**
     * Visits an FSTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitFSTORE(FSTORE obj);

    /**
     * Visits an FSUB instruction.
     *
     * @param obj The instruction.
     */
    void visitFSUB(FSUB obj);

    /**
     * Visits a GETFIELD instruction.
     *
     * @param obj The instruction.
     */
    void visitGETFIELD(GETFIELD obj);

    /**
     * Visits a GETSTATIC instruction.
     *
     * @param obj The instruction.
     */
    void visitGETSTATIC(GETSTATIC obj);

    /**
     * Visits a GOTO instruction.
     *
     * @param obj The instruction.
     */
    void visitGOTO(GOTO obj);

    /**
     * Visits a GOTO_W instruction.
     *
     * @param obj The instruction.
     */
    void visitGOTO_W(GOTO_W obj);

    /**
     * Visits a GotoInstruction.
     *
     * @param obj The instruction.
     */
    void visitGotoInstruction(GotoInstruction obj);

    /**
     * Visits an I2B instruction.
     *
     * @param obj The instruction.
     */
    void visitI2B(I2B obj);

    /**
     * Visits an I2C instruction.
     *
     * @param obj The instruction.
     */
    void visitI2C(I2C obj);

    /**
     * Visits an I2D instruction.
     *
     * @param obj The instruction.
     */
    void visitI2D(I2D obj);

    /**
     * Visits an I2F instruction.
     *
     * @param obj The instruction.
     */
    void visitI2F(I2F obj);

    /**
     * Visits an I2L instruction.
     *
     * @param obj The instruction.
     */
    void visitI2L(I2L obj);

    /**
     * Visits an I2S instruction.
     *
     * @param obj The instruction.
     */
    void visitI2S(I2S obj);

    /**
     * Visits an IADD instruction.
     *
     * @param obj The instruction.
     */
    void visitIADD(IADD obj);

    /**
     * Visits an IALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitIALOAD(IALOAD obj);

    /**
     * Visits an IAND instruction.
     *
     * @param obj The instruction.
     */
    void visitIAND(IAND obj);

    /**
     * Visits an IASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitIASTORE(IASTORE obj);

    /**
     * Visits an ICONST instruction.
     *
     * @param obj The instruction.
     */
    void visitICONST(ICONST obj);

    /**
     * Visits an IDIV instruction.
     *
     * @param obj The instruction.
     */
    void visitIDIV(IDIV obj);

    /**
     * Visits an IF_ACMPEQ instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ACMPEQ(IF_ACMPEQ obj);

    /**
     * Visits an IF_ACMPNE instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ACMPNE(IF_ACMPNE obj);

    /**
     * Visits an IF_ICMPEQ instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ICMPEQ(IF_ICMPEQ obj);

    /**
     * Visits an IF_ICMPGE instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ICMPGE(IF_ICMPGE obj);

    /**
     * Visits an IF_ICMPGT instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ICMPGT(IF_ICMPGT obj);

    /**
     * Visits an IF_ICMPLE instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ICMPLE(IF_ICMPLE obj);

    /**
     * Visits an IF_ICMPLT instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ICMPLT(IF_ICMPLT obj);

    /**
     * Visits an IF_ICMPNE instruction.
     *
     * @param obj The instruction.
     */
    void visitIF_ICMPNE(IF_ICMPNE obj);

    /**
     * Visits an IFEQ instruction.
     *
     * @param obj The instruction.
     */
    void visitIFEQ(IFEQ obj);

    /**
     * Visits an IFGE instruction.
     *
     * @param obj The instruction.
     */
    void visitIFGE(IFGE obj);

    /**
     * Visits an IFGT instruction.
     *
     * @param obj The instruction.
     */
    void visitIFGT(IFGT obj);

    /**
     * Visits an IfInstruction.
     *
     * @param obj The instruction.
     */
    void visitIfInstruction(IfInstruction obj);

    /**
     * Visits an IFLE instruction.
     *
     * @param obj The instruction.
     */
    void visitIFLE(IFLE obj);

    /**
     * Visits an IFLT instruction.
     *
     * @param obj The instruction.
     */
    void visitIFLT(IFLT obj);

    /**
     * Visits an IFNE instruction.
     *
     * @param obj The instruction.
     */
    void visitIFNE(IFNE obj);

    /**
     * Visits an IFNONNULL instruction.
     *
     * @param obj The instruction.
     */
    void visitIFNONNULL(IFNONNULL obj);

    /**
     * Visits an IFNULL instruction.
     *
     * @param obj The instruction.
     */
    void visitIFNULL(IFNULL obj);

    /**
     * Visits an IINC instruction.
     *
     * @param obj The instruction.
     */
    void visitIINC(IINC obj);

    /**
     * Visits an ILOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitILOAD(ILOAD obj);

    /**
     * Visits an IMPDEP1 instruction.
     *
     * @param obj The instruction.
     */
    void visitIMPDEP1(IMPDEP1 obj);

    /**
     * Visits an IMPDEP2 instruction.
     *
     * @param obj The instruction.
     */
    void visitIMPDEP2(IMPDEP2 obj);

    /**
     * Visits an IMUL instruction.
     *
     * @param obj The instruction.
     */
    void visitIMUL(IMUL obj);

    /**
     * Visits an INEG instruction.
     *
     * @param obj The instruction.
     */
    void visitINEG(INEG obj);

    /**
     * Visits an INSTANCEOF instruction.
     *
     * @param obj The instruction.
     */
    void visitINSTANCEOF(INSTANCEOF obj);

    /**
     * Visits an INVOKEDYNAMIC instruction.
     *
     * @param obj The instruction.
     * @since 6.0
     */
    void visitINVOKEDYNAMIC(INVOKEDYNAMIC obj);

    /**
     * Visits an InvokeInstruction.
     *
     * @param obj The instruction.
     */
    void visitInvokeInstruction(InvokeInstruction obj);

    /**
     * Visits an INVOKEINTERFACE instruction.
     *
     * @param obj The instruction.
     */
    void visitINVOKEINTERFACE(INVOKEINTERFACE obj);

    /**
     * Visits an INVOKESPECIAL instruction.
     *
     * @param obj The instruction.
     */
    void visitINVOKESPECIAL(INVOKESPECIAL obj);

    /**
     * Visits an INVOKESTATIC instruction.
     *
     * @param obj The instruction.
     */
    void visitINVOKESTATIC(INVOKESTATIC obj);

    /**
     * Visits an INVOKEVIRTUAL instruction.
     *
     * @param obj The instruction.
     */
    void visitINVOKEVIRTUAL(INVOKEVIRTUAL obj);

    /**
     * Visits an IOR instruction.
     *
     * @param obj The instruction.
     */
    void visitIOR(IOR obj);

    /**
     * Visits an IREM instruction.
     *
     * @param obj The instruction.
     */
    void visitIREM(IREM obj);

    /**
     * Visits an IRETURN instruction.
     *
     * @param obj The instruction.
     */
    void visitIRETURN(IRETURN obj);

    /**
     * Visits an ISHL instruction.
     *
     * @param obj The instruction.
     */
    void visitISHL(ISHL obj);

    /**
     * Visits an ISHR instruction.
     *
     * @param obj The instruction.
     */
    void visitISHR(ISHR obj);

    /**
     * Visits an ISTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitISTORE(ISTORE obj);

    /**
     * Visits an ISUB instruction.
     *
     * @param obj The instruction.
     */
    void visitISUB(ISUB obj);

    /**
     * Visits an IUSHR instruction.
     *
     * @param obj The instruction.
     */
    void visitIUSHR(IUSHR obj);

    /**
     * Visits an IXOR instruction.
     *
     * @param obj The instruction.
     */
    void visitIXOR(IXOR obj);

    /**
     * Visits a JSR instruction.
     *
     * @param obj The instruction.
     */
    void visitJSR(JSR obj);

    /**
     * Visits a JSR_W instruction.
     *
     * @param obj The instruction.
     */
    void visitJSR_W(JSR_W obj);

    /**
     * Visits a JsrInstruction.
     *
     * @param obj The instruction.
     */
    void visitJsrInstruction(JsrInstruction obj);

    /**
     * Visits an L2D instruction.
     *
     * @param obj The instruction.
     */
    void visitL2D(L2D obj);

    /**
     * Visits an L2F instruction.
     *
     * @param obj The instruction.
     */
    void visitL2F(L2F obj);

    /**
     * Visits an L2I instruction.
     *
     * @param obj The instruction.
     */
    void visitL2I(L2I obj);

    /**
     * Visits an LADD instruction.
     *
     * @param obj The instruction.
     */
    void visitLADD(LADD obj);

    /**
     * Visits an LALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitLALOAD(LALOAD obj);

    /**
     * Visits an LAND instruction.
     *
     * @param obj The instruction.
     */
    void visitLAND(LAND obj);

    /**
     * Visits an LASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitLASTORE(LASTORE obj);

    /**
     * Visits an LCMP instruction.
     *
     * @param obj The instruction.
     */
    void visitLCMP(LCMP obj);

    /**
     * Visits an LCONST instruction.
     *
     * @param obj The instruction.
     */
    void visitLCONST(LCONST obj);

    /**
     * Visits an LDC instruction.
     *
     * @param obj The instruction.
     */
    void visitLDC(LDC obj);

    /**
     * Visits an LDC2_W instruction.
     *
     * @param obj The instruction.
     */
    void visitLDC2_W(LDC2_W obj);

    /**
     * Visits an LDIV instruction.
     *
     * @param obj The instruction.
     */
    void visitLDIV(LDIV obj);

    /**
     * Visits an LLOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitLLOAD(LLOAD obj);

    /**
     * Visits an LMUL instruction.
     *
     * @param obj The instruction.
     */
    void visitLMUL(LMUL obj);

    /**
     * Visits an LNEG instruction.
     *
     * @param obj The instruction.
     */
    void visitLNEG(LNEG obj);

    /**
     * Visits a LoadClass.
     *
     * @param obj The load class.
     */
    void visitLoadClass(LoadClass obj);

    /**
     * Visits a LoadInstruction.
     *
     * @param obj The instruction.
     */
    void visitLoadInstruction(LoadInstruction obj);

    /**
     * Visits a LocalVariableInstruction.
     *
     * @param obj The instruction.
     */
    void visitLocalVariableInstruction(LocalVariableInstruction obj);

    /**
     * Visits a LOOKUPSWITCH instruction.
     *
     * @param obj The instruction.
     */
    void visitLOOKUPSWITCH(LOOKUPSWITCH obj);

    /**
     * Visits an LOR instruction.
     *
     * @param obj The instruction.
     */
    void visitLOR(LOR obj);

    /**
     * Visits an LREM instruction.
     *
     * @param obj The instruction.
     */
    void visitLREM(LREM obj);

    /**
     * Visits an LRETURN instruction.
     *
     * @param obj The instruction.
     */
    void visitLRETURN(LRETURN obj);

    /**
     * Visits an LSHL instruction.
     *
     * @param obj The instruction.
     */
    void visitLSHL(LSHL obj);

    /**
     * Visits an LSHR instruction.
     *
     * @param obj The instruction.
     */
    void visitLSHR(LSHR obj);

    /**
     * Visits an LSTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitLSTORE(LSTORE obj);

    /**
     * Visits an LSUB instruction.
     *
     * @param obj The instruction.
     */
    void visitLSUB(LSUB obj);

    /**
     * Visits an LUSHR instruction.
     *
     * @param obj The instruction.
     */
    void visitLUSHR(LUSHR obj);

    /**
     * Visits an LXOR instruction.
     *
     * @param obj The instruction.
     */
    void visitLXOR(LXOR obj);

    /**
     * Visits a MONITORENTER instruction.
     *
     * @param obj The instruction.
     */
    void visitMONITORENTER(MONITORENTER obj);

    /**
     * Visits a MONITOREXIT instruction.
     *
     * @param obj The instruction.
     */
    void visitMONITOREXIT(MONITOREXIT obj);

    /**
     * Visits a MULTIANEWARRAY instruction.
     *
     * @param obj The instruction.
     */
    void visitMULTIANEWARRAY(MULTIANEWARRAY obj);

    /**
     * Visits a NEW instruction.
     *
     * @param obj The instruction.
     */
    void visitNEW(NEW obj);

    /**
     * Visits a NEWARRAY instruction.
     *
     * @param obj The instruction.
     */
    void visitNEWARRAY(NEWARRAY obj);

    /**
     * Visits a NOP instruction.
     *
     * @param obj The instruction.
     */
    void visitNOP(NOP obj);

    /**
     * Visits a POP instruction.
     *
     * @param obj The instruction.
     */
    void visitPOP(POP obj);

    /**
     * Visits a POP2 instruction.
     *
     * @param obj The instruction.
     */
    void visitPOP2(POP2 obj);

    /**
     * Visits a PopInstruction.
     *
     * @param obj The instruction.
     */
    void visitPopInstruction(PopInstruction obj);

    /**
     * Visits a PushInstruction.
     *
     * @param obj The instruction.
     */
    void visitPushInstruction(PushInstruction obj);

    /**
     * Visits a PUTFIELD instruction.
     *
     * @param obj The instruction.
     */
    void visitPUTFIELD(PUTFIELD obj);

    /**
     * Visits a PUTSTATIC instruction.
     *
     * @param obj The instruction.
     */
    void visitPUTSTATIC(PUTSTATIC obj);

    /**
     * Visits a RET instruction.
     *
     * @param obj The instruction.
     */
    void visitRET(RET obj);

    /**
     * Visits a RETURN instruction.
     *
     * @param obj The instruction.
     */
    void visitRETURN(RETURN obj);

    /**
     * Visits a ReturnInstruction.
     *
     * @param obj The instruction.
     */
    void visitReturnInstruction(ReturnInstruction obj);

    /**
     * Visits a SALOAD instruction.
     *
     * @param obj The instruction.
     */
    void visitSALOAD(SALOAD obj);

    /**
     * Visits a SASTORE instruction.
     *
     * @param obj The instruction.
     */
    void visitSASTORE(SASTORE obj);

    /**
     * Visits a Select instruction.
     *
     * @param obj The instruction.
     */
    void visitSelect(Select obj);

    /**
     * Visits a SIPUSH instruction.
     *
     * @param obj The instruction.
     */
    void visitSIPUSH(SIPUSH obj);

    /**
     * Visits a StackConsumer.
     *
     * @param obj The instruction.
     */
    void visitStackConsumer(StackConsumer obj);

    /**
     * Visits a StackInstruction.
     *
     * @param obj The instruction.
     */
    void visitStackInstruction(StackInstruction obj);

    /**
     * Visits a StackProducer.
     *
     * @param obj The instruction.
     */
    void visitStackProducer(StackProducer obj);

    /**
     * Visits a StoreInstruction.
     *
     * @param obj The instruction.
     */
    void visitStoreInstruction(StoreInstruction obj);

    /**
     * Visits a SWAP instruction.
     *
     * @param obj The instruction.
     */
    void visitSWAP(SWAP obj);

    /**
     * Visits a TABLESWITCH instruction.
     *
     * @param obj The instruction.
     */
    void visitTABLESWITCH(TABLESWITCH obj);

    /**
     * Visits a TypedInstruction.
     *
     * @param obj The instruction.
     */
    void visitTypedInstruction(TypedInstruction obj);

    /**
     * Visits an UnconditionalBranch.
     *
     * @param obj The instruction.
     */
    void visitUnconditionalBranch(UnconditionalBranch obj);

    /**
     * Visits a VariableLengthInstruction.
     *
     * @param obj The instruction.
     */
    void visitVariableLengthInstruction(VariableLengthInstruction obj);
}
