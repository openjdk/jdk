/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.asmtools.jasm;

import static org.openjdk.asmtools.jasm.JasmTokens.*;
import static org.openjdk.asmtools.jasm.Tables.*;
import static org.openjdk.asmtools.jasm.OpcodeTables.*;
import java.io.IOException;
import java.lang.reflect.Modifier;

/**
 * ParserInstr
 *
 * ParserInstr is a parser class owned by Parser.java. It is primarily responsible for
 * parsing instruction byte codes.
 */
public class ParserInstr extends ParseBase {

    /**
     * local handle for the constant parser - needed for parsing constants during
     * instruction construction.
     */
    private ParserCP cpParser = null;

    /**
     * main constructor
     *
     * @param scanner
     * @param parser
     * @param env
     */
    protected ParserInstr(Scanner scanner, Parser parser, ParserCP cpParser, Environment env) {
        super.init(scanner, parser, env);
        this.cpParser = cpParser;
    }

    /**
     * Parse an instruction.
     */
    protected void parseInstr() throws Scanner.SyntaxError, IOException {
        // ignore possible line numbers after java disassembler
        if (scanner.token == Token.INTVAL) {
            scanner.scan();
        }
        // ignore possible numeric labels after java disassembler
        if (scanner.token == Token.INTVAL) {
            scanner.scan();
        }
        if (scanner.token == Token.COLON) {
            scanner.scan();
        }

        String mnemocode;
        int mnenoc_pos;
        for (;;) { // read labels
            if (scanner.token != Token.IDENT) {
                return;
            }
            mnemocode = scanner.idValue;
            mnenoc_pos = scanner.pos;
            scanner.scan();
            if (scanner.token != Token.COLON) {
                break;
            }
            // actually it was a label
            scanner.scan();
            parser.curCode.LabelDef(mnenoc_pos, mnemocode);
        }

        Opcode opcode = OpcodeTables.opcode(mnemocode);
        if (opcode == null) {
            debugScan(" Error:  mnemocode = '" + mnemocode + "'.   ");
        }
        OpcodeType optype = opcode.type();

        Argument arg = null;
        Object arg2 = null;
        StackMapData sMap = null;

        debugScan(" --IIIII---[ParserInstr:[parseInstr]:  (Pos: " + mnenoc_pos + ") mnemocode: '" + opcode.parsekey() + "' ");

        switch (optype) {
            case NORMAL:
                switch (opcode) {

                    // pseudo-instructions:
                    case opc_bytecode:
                        for (;;) {
                            parser.curCode.addInstr(mnenoc_pos, Opcode.opc_bytecode, parser.parseUInt(1), null);
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_try:
                        for (;;) {
                            parser.curCode.beginTrap(scanner.pos, parser.parseIdent());
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_endtry:
                        for (;;) {
                            parser.curCode.endTrap(scanner.pos, parser.parseIdent());
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_catch:
                        parser.curCode.trapHandler(scanner.pos, parser.parseIdent(),
                                cpParser.parseConstRef(ConstType.CONSTANT_CLASS));
                        return;
                    case opc_var:
                        for (;;) {
                            parser.parseLocVarDef();
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_endvar:
                        for (;;) {
                            parser.parseLocVarEnd();
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_locals_map:
                        sMap = parser.curCode.getStackMap();
                        if (sMap.localsMap != null) {
                            env.error(scanner.pos, "localsmap.repeated");
                        }
                        ;
                        DataVector localsMap = new DataVector();
                        sMap.localsMap = localsMap;
                        if (scanner.token == Token.SEMICOLON) {
                            return;  // empty locals_map allowed
                        }
                        for (;;) {
                            parser.parseMapItem(localsMap);
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_stack_map:
                        sMap = parser.curCode.getStackMap();
                        if (sMap.stackMap != null) {
                            env.error(scanner.pos, "stackmap.repeated");
                        }
                        ;
                        DataVector stackMap = new DataVector();
                        sMap.stackMap = stackMap;
                        if (scanner.token == Token.SEMICOLON) {
                            return;  // empty stack_map allowed
                        }
                        for (;;) {
                            parser.parseMapItem(stackMap);
                            if (scanner.token != Token.COMMA) {
                                return;
                            }
                            scanner.scan();
                        }
                    case opc_stack_frame_type:
                        sMap = parser.curCode.getStackMap();
                        if (sMap.stackFrameType != null) {
                            env.error(scanner.pos, "frametype.repeated");
                        }
                        ;
                        sMap.setStackFrameType(parser.parseIdent());
                        return;

                    // normal instructions:
                    case opc_aload:
                    case opc_astore:
                    case opc_fload:
                    case opc_fstore:
                    case opc_iload:
                    case opc_istore:
                    case opc_lload:
                    case opc_lstore:
                    case opc_dload:
                    case opc_dstore:
                    case opc_ret:
                    case opc_aload_w:
                    case opc_astore_w:
                    case opc_fload_w:
                    case opc_fstore_w:
                    case opc_iload_w:
                    case opc_istore_w:
                    case opc_lload_w:
                    case opc_lstore_w:
                    case opc_dload_w:
                    case opc_dstore_w:
                    case opc_ret_w:
                        // loc var
                        arg = parser.parseLocVarRef();
                        break;
                    case opc_iinc: // loc var, const
                        arg = parser.parseLocVarRef();
                        scanner.expect(Token.COMMA);
                        arg2 = parser.parseInt(1);
                        break;
                    case opc_tableswitch:
                    case opc_lookupswitch:
                        arg2 = parseSwitchTable();
                        break;
                    case opc_newarray: {
                        int type;
                        if (scanner.token == Token.INTVAL) {
                            type = scanner.intValue;
                        } else if ((type = Tables.basictypeValue(scanner.idValue)) == -1) {
                            env.error(scanner.pos, "type.expected");
                            throw new Scanner.SyntaxError();
                        }
                        scanner.scan();
                        arg = new Argument(type);
                        break;
                    }
                    case opc_new:
                    case opc_anewarray:
                    case opc_instanceof:
                    case opc_checkcast:
                        arg = cpParser.parseConstRef(ConstType.CONSTANT_CLASS);
                        break;
                    case opc_bipush:
                        arg = parser.parseInt(1);
                        break;
                    case opc_sipush:
                        arg = parser.parseInt(2);
                        break;
                    case opc_ldc:
                    case opc_ldc_w:
                    case opc_ldc2_w:
                        arg = cpParser.parseConstRef(null);
                        break;
                    case opc_putstatic:
                    case opc_getstatic:
                    case opc_putfield:
                    case opc_getfield:
                        arg = cpParser.parseConstRef(ConstType.CONSTANT_FIELD);
                        break;
                    case opc_invokevirtual:
                        arg = cpParser.parseConstRef(ConstType.CONSTANT_METHOD);
                        break;
                    case opc_invokestatic:
                    case opc_invokespecial:
                        ConstType ctype01  = ConstType.CONSTANT_METHOD;
                        ConstType ctype02  = ConstType.CONSTANT_INTERFACEMETHOD;
                        if(Modifier.isInterface(this.parser.cd.access)) {
                            ctype01  = ConstType.CONSTANT_INTERFACEMETHOD;
                            ctype02  = ConstType.CONSTANT_METHOD;
                        }
                        arg = cpParser.parseConstRef(ctype01, ctype02);
                        break;
                    case opc_jsr:
                    case opc_goto:
                    case opc_ifeq:
                    case opc_ifge:
                    case opc_ifgt:
                    case opc_ifle:
                    case opc_iflt:
                    case opc_ifne:
                    case opc_if_icmpeq:
                    case opc_if_icmpne:
                    case opc_if_icmpge:
                    case opc_if_icmpgt:
                    case opc_if_icmple:
                    case opc_if_icmplt:
                    case opc_if_acmpeq:
                    case opc_if_acmpne:
                    case opc_ifnull:
                    case opc_ifnonnull:
                    case opc_jsr_w:
                    case opc_goto_w:
                        arg = parseLabelRef();
                        break;

                    case opc_invokeinterface:
                        arg = cpParser.parseConstRef(ConstType.CONSTANT_INTERFACEMETHOD);
                        scanner.expect(Token.COMMA);
                        arg2 = parser.parseUInt(1);
                        break;
                    case opc_invokedynamic:
                        arg = cpParser.parseConstRef(ConstType.CONSTANT_INVOKEDYNAMIC);
                        break;

                    case opc_multianewarray:
                        arg = cpParser.parseConstRef(ConstType.CONSTANT_CLASS);
                        scanner.expect(Token.COMMA);
                        arg2 = parser.parseUInt(1);
                        break;
                    case opc_wide:
                    case opc_nonpriv:
                    case opc_priv:
                        int opc2 = (opcode.value() << 8) | parser.parseUInt(1).arg;
                        opcode = opcode(opc2);
                        break;
                }
                break;
            case WIDE:
                arg = parser.parseLocVarRef();
                if (opcode == Opcode.opc_iinc_w) { // loc var, const
                    scanner.expect(Token.COMMA);
                    arg2 = parser.parseInt(2);
                }
                break;
            case NONPRIVELEGED:
            case PRIVELEGED:
                break;
            default:
                env.error(scanner.prevPos, "wrong.mnemocode", mnemocode);
                throw new Scanner.SyntaxError();
        }
        // env.traceln(" [ParserInstr.parseInstr] ===============> Adding Instruction: [" + mnenoc_pos + "]: instr: "+ mnemocode /* opcNamesTab[opc] */);
        parser.curCode.addInstr(mnenoc_pos, opcode, arg, arg2);
    } //end parseInstr

    /**
     * Parse a Switch Table. return value: SwitchTable.
     */
    protected SwitchTable parseSwitchTable() throws Scanner.SyntaxError, IOException {
        scanner.expect(Token.LBRACE);
        Argument label;
        int numpairs = 0, key;
        SwitchTable table = new SwitchTable(env);
tableScan:
        {
            while (numpairs < 1000) {
//              env.traceln("start tableScan:" + token);
                switch (scanner.token) {
                    case INTVAL:
//                        env.traceln("enter tableScan:" + token);
                        key = scanner.intValue * scanner.sign;
                        scanner.scan();
                        scanner.expect(Token.COLON);
                        table.addEntry(key, parseLabelRef());
                        numpairs++;
                        if (scanner.token != Token.SEMICOLON) {
//                            env.traceln("break tableScan1:" + token);
                            break tableScan;
                        }
                        scanner.scan();
                        break;
                    case DEFAULT:
                        scanner.scan();
                        scanner.expect(Token.COLON);
                        if (table.deflabel != null) {
                            env.error("default.redecl");
                        }
                        table.deflabel = parseLabelRef();
                        if (scanner.token != Token.SEMICOLON) {
//                            env.traceln("break tableScan2:" + token);
                            break tableScan;
                        }
                        scanner.scan();
                        break;
                    default:
//                      env.traceln("break tableScan3:" + token + "val=" + intValue);
                        break tableScan;
                } // end switch
            } // while (numpairs<1000)
            env.error("long.switchtable", "1000");
        } // end tableScan
        scanner.expect(Token.RBRACE);
        return table;
    } // end parseSwitchTable

    /**
     * Parse a label instruction argument
     */
    protected Argument parseLabelRef() throws Scanner.SyntaxError, IOException {
        switch (scanner.token) {
            case INTVAL: {
                int v = scanner.intValue * scanner.sign;
                scanner.scan();
                return new Argument(v);
            }
            case IDENT: {
                String label = scanner.stringValue;
                scanner.scan();
                return parser.curCode.LabelRef(label);
            }
        }
        env.error("label.expected");
        throw new Scanner.SyntaxError();
    }

}
