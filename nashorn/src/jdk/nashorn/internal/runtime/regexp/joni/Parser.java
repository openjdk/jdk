/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni;

import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsOnAtSimple;
import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsOnOff;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isDontCaptureGroup;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isIgnoreCase;

import jdk.nashorn.internal.runtime.regexp.joni.encoding.CharacterType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.PosixBracket;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.Ptr;
import jdk.nashorn.internal.runtime.regexp.joni.ast.AnchorNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.AnyCharNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.BackRefNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CClassNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CTypeNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CallNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.ConsAltNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.EncloseNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.Node;
import jdk.nashorn.internal.runtime.regexp.joni.ast.QuantifierNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.StringNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CClassNode.CCStateArg;
import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.CCSTATE;
import jdk.nashorn.internal.runtime.regexp.joni.constants.CCVALTYPE;
import jdk.nashorn.internal.runtime.regexp.joni.constants.EncloseType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.NodeType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.TokenType;

class Parser extends Lexer {

    protected final Regex regex;
    protected Node root;

    protected int returnCode; // return code used by parser methods (they itself return parsed nodes)
                              // this approach will not affect recursive calls

    protected Parser(ScanEnvironment env, char[] chars, int p, int end) {
        super(env, chars, p, end);
        regex = env.reg;
    }

    // onig_parse_make_tree
    protected final Node parse() {
        root = parseRegexp();
        regex.numMem = env.numMem;
        return root;
    }

    private static final int POSIX_BRACKET_NAME_MIN_LEN            = 4;
    private static final int POSIX_BRACKET_CHECK_LIMIT_LENGTH      = 20;
    private static final char BRACKET_END[]                        = ":]".toCharArray();
    private boolean parsePosixBracket(CClassNode cc) {
        mark();

        boolean not;
        if (peekIs('^')) {
            inc();
            not = true;
        } else {
            not = false;
        }
        if (stop - p >= POSIX_BRACKET_NAME_MIN_LEN + 3) { // else goto not_posix_bracket
            char[][] pbs = PosixBracket.PBSNamesLower;
            for (int i=0; i<pbs.length; i++) {
                char[] name = pbs[i];
                // hash lookup here ?
                if (EncodingHelper.strNCmp(chars, p, stop, name, 0, name.length) == 0) {
                    p += name.length;
                    if (EncodingHelper.strNCmp(chars, p, stop, BRACKET_END, 0, BRACKET_END.length) != 0) {
                        newSyntaxException(ERR_INVALID_POSIX_BRACKET_TYPE);
                    }
                    cc.addCType(PosixBracket.PBSValues[i], not, env, this);
                    inc();
                    inc();
                    return false;
                }
            }

        }

        // not_posix_bracket:
        c = 0;
        int i= 0;
        while (left() && ((c=peek()) != ':') && c != ']') {
            inc();
            if (++i > POSIX_BRACKET_CHECK_LIMIT_LENGTH) break;
        }

        if (c == ':' && left()) {
            inc();
            if (left()) {
                fetch();
                if (c == ']') newSyntaxException(ERR_INVALID_POSIX_BRACKET_TYPE);
            }
        }
        restore();
        return true; /* 1: is not POSIX bracket, but no error. */
    }

    private CClassNode parseCharProperty() {
        int ctype = fetchCharPropertyToCType();
        CClassNode n = new CClassNode();
        n.addCType(ctype, false, env, this);
        if (token.getPropNot()) n.setNot();
        return n;
    }

    private boolean codeExistCheck(int code, boolean ignoreEscaped) {
        mark();

        boolean inEsc = false;
        while (left()) {
            if (ignoreEscaped && inEsc) {
                inEsc = false;
            } else {
                fetch();
                if (c == code) {
                    restore();
                    return true;
                }
                if (c == syntax.metaCharTable.esc) inEsc = true;
            }
        }

        restore();
        return false;
    }

    private CClassNode parseCharClass() {
        fetchTokenInCC();

        final boolean neg;
        if (token.type == TokenType.CHAR && token.getC() == '^' && !token.escaped) {
            neg = true;
            fetchTokenInCC();
        } else {
            neg = false;
        }

        if (token.type == TokenType.CC_CLOSE) {
            if (!codeExistCheck(']', true)) newSyntaxException(ERR_EMPTY_CHAR_CLASS);
            env.ccEscWarn("]");
            token.type = TokenType.CHAR; /* allow []...] */
        }

        CClassNode cc = new CClassNode();
        CClassNode prevCC = null;
        CClassNode workCC = null;

        CCStateArg arg = new CCStateArg();

        boolean andStart = false;
        arg.state = CCSTATE.START;

        while (token.type != TokenType.CC_CLOSE) {
            boolean fetched = false;

            switch (token.type) {

            case CHAR:
                if (token.getC() > 0xff) {
                    arg.inType = CCVALTYPE.CODE_POINT;
                } else {
                    arg.inType = CCVALTYPE.SB; // sb_char:
                }
                arg.v = token.getC();
                arg.vIsRaw = false;
                parseCharClassValEntry2(cc, arg); // goto val_entry2
                break;

            case RAW_BYTE:
                if (token.base != 0) { /* tok->base != 0 : octal or hexadec. */
                    byte[] buf = new byte[4];
                    int psave = p;
                    int base = token.base;
                    buf[0] = (byte)token.getC();
                    int i;
                    for (i=1; i<4; i++) {
                        fetchTokenInCC();
                        if (token.type != TokenType.RAW_BYTE || token.base != base) {
                            fetched = true;
                            break;
                        }
                        buf[i] = (byte)token.getC();
                    }

                    if (i == 1) {
                        arg.v = buf[0] & 0xff;
                        arg.inType = CCVALTYPE.SB; // goto raw_single
                    } else {
                        arg.v = EncodingHelper.mbcToCode(buf, 0, buf.length);
                        arg.inType = CCVALTYPE.CODE_POINT;
                    }
                } else {
                    arg.v = token.getC();
                    arg.inType = CCVALTYPE.SB; // raw_single:
                }
                arg.vIsRaw = true;
                parseCharClassValEntry2(cc, arg); // goto val_entry2
                break;

            case CODE_POINT:
                arg.v = token.getCode();
                arg.vIsRaw = true;
                parseCharClassValEntry(cc, arg); // val_entry:, val_entry2
                break;

            case POSIX_BRACKET_OPEN:
                if (parsePosixBracket(cc)) { /* true: is not POSIX bracket */
                    env.ccEscWarn("[");
                    p = token.backP;
                    arg.v = token.getC();
                    arg.vIsRaw = false;
                    parseCharClassValEntry(cc, arg); // goto val_entry
                    break;
                }
                cc.nextStateClass(arg, env); // goto next_class
                break;

            case CHAR_TYPE:
                cc.addCType(token.getPropCType(), token.getPropNot(), env, this);
                cc.nextStateClass(arg, env); // next_class:
                break;

            case CHAR_PROPERTY:
                int ctype = fetchCharPropertyToCType();
                cc.addCType(ctype, token.getPropNot(), env, this);
                cc.nextStateClass(arg, env); // goto next_class
                break;

            case CC_RANGE:
                if (arg.state == CCSTATE.VALUE) {
                    fetchTokenInCC();
                    fetched = true;
                    if (token.type == TokenType.CC_CLOSE) { /* allow [x-] */
                        parseCharClassRangeEndVal(cc, arg); // range_end_val:, goto val_entry;
                        break;
                    } else if (token.type == TokenType.CC_AND) {
                        env.ccEscWarn("-");
                        parseCharClassRangeEndVal(cc, arg); // goto range_end_val
                        break;
                    }
                    arg.state = CCSTATE.RANGE;
                } else if (arg.state == CCSTATE.START) {
                    arg.v = token.getC(); /* [-xa] is allowed */
                    arg.vIsRaw = false;
                    fetchTokenInCC();
                    fetched = true;
                    if (token.type == TokenType.CC_RANGE || andStart) env.ccEscWarn("-"); /* [--x] or [a&&-x] is warned. */
                    parseCharClassValEntry(cc, arg); // goto val_entry
                    break;
                } else if (arg.state == CCSTATE.RANGE) {
                    env.ccEscWarn("-");
                    parseCharClassSbChar(cc, arg); // goto sb_char /* [!--x] is allowed */
                    break;
                } else { /* CCS_COMPLETE */
                    fetchTokenInCC();
                    fetched = true;
                    if (token.type == TokenType.CC_CLOSE) { /* allow [a-b-] */
                        parseCharClassRangeEndVal(cc, arg); // goto range_end_val
                        break;
                    } else if (token.type == TokenType.CC_AND) {
                        env.ccEscWarn("-");
                        parseCharClassRangeEndVal(cc, arg); // goto range_end_val
                        break;
                    }

                    if (syntax.allowDoubleRangeOpInCC()) {
                        env.ccEscWarn("-");
                        parseCharClassSbChar(cc, arg); // goto sb_char /* [0-9-a] is allowed as [0-9\-a] */
                        break;
                    }
                    newSyntaxException(ERR_UNMATCHED_RANGE_SPECIFIER_IN_CHAR_CLASS);
                }
                break;

            case CC_CC_OPEN: /* [ */
                CClassNode acc = parseCharClass();
                cc.or(acc);
                break;

            case CC_AND:     /* && */
                if (arg.state == CCSTATE.VALUE) {
                    arg.v = 0; // ??? safe v ?
                    arg.vIsRaw = false;
                    cc.nextStateValue(arg, env);
                }
                /* initialize local variables */
                andStart = true;
                arg.state = CCSTATE.START;
                if (prevCC != null) {
                    prevCC.and(cc);
                } else {
                    prevCC = cc;
                    if (workCC == null) workCC = new CClassNode();
                    cc = workCC;
                }
                cc.clear();
                break;

            case EOT:
                newSyntaxException(ERR_PREMATURE_END_OF_CHAR_CLASS);

            default:
                newInternalException(ERR_PARSER_BUG);
            } // switch

            if (!fetched) fetchTokenInCC();

        } // while

        if (arg.state == CCSTATE.VALUE) {
            arg.v = 0; // ??? safe v ?
            arg.vIsRaw = false;
            cc.nextStateValue(arg, env);
        }

        if (prevCC != null) {
            prevCC.and(cc);
            cc = prevCC;
        }

        if (neg) {
            cc.setNot();
        } else {
            cc.clearNot();
        }

        if (cc.isNot() && syntax.notNewlineInNegativeCC()) {
            if (!cc.isEmpty()) {
                final int NEW_LINE = 0x0a;
                if (EncodingHelper.isNewLine(NEW_LINE)) {
                    cc.bs.set(NEW_LINE);
                }
            }
        }

        return cc;
    }

    private void parseCharClassSbChar(CClassNode cc, CCStateArg arg) {
        arg.inType = CCVALTYPE.SB;
        arg.v = token.getC();
        arg.vIsRaw = false;
        parseCharClassValEntry2(cc, arg); // goto val_entry2
    }

    private void parseCharClassRangeEndVal(CClassNode cc, CCStateArg arg) {
        arg.v = '-';
        arg.vIsRaw = false;
        parseCharClassValEntry(cc, arg); // goto val_entry
    }

    private void parseCharClassValEntry(CClassNode cc, CCStateArg arg) {
        arg.inType = arg.v <= 0xff ? CCVALTYPE.SB : CCVALTYPE.CODE_POINT;
        parseCharClassValEntry2(cc, arg); // val_entry2:
    }

    private void parseCharClassValEntry2(CClassNode cc, CCStateArg arg) {
        cc.nextStateValue(arg, env);
    }

    private Node parseEnclose(TokenType term) {
        Node node = null;

        if (!left()) newSyntaxException(ERR_END_PATTERN_WITH_UNMATCHED_PARENTHESIS);

        int option = env.option;

        if (peekIs('?') && syntax.op2QMarkGroupEffect()) {
            inc();
            if (!left()) newSyntaxException(ERR_END_PATTERN_IN_GROUP);

            boolean listCapture = false;

            fetch();
            switch(c) {
            case ':':  /* (?:...) grouping only */
                fetchToken(); // group:
                node = parseSubExp(term);
                returnCode = 1; /* group */
                return node;
            case '=':
                node = new AnchorNode(AnchorType.PREC_READ);
                break;
            case '!':  /*         preceding read */
                node = new AnchorNode(AnchorType.PREC_READ_NOT);
                break;
            case '>':  /* (?>...) stop backtrack */
                node = new EncloseNode(EncloseType.STOP_BACKTRACK); // node_new_enclose
                break;
            case '\'':
                if (Config.USE_NAMED_GROUP) {
                    if (syntax.op2QMarkLtNamedGroup()) {
                        listCapture = false; // goto named_group1
                        node = parseEncloseNamedGroup2(listCapture);
                        break;
                    } else {
                        newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                    }
                } // USE_NAMED_GROUP
                break;
            case '<':  /* look behind (?<=...), (?<!...) */
                fetch();
                if (c == '=') {
                    node = new AnchorNode(AnchorType.LOOK_BEHIND);
                } else if (c == '!') {
                    node = new AnchorNode(AnchorType.LOOK_BEHIND_NOT);
                } else {
                    if (Config.USE_NAMED_GROUP) {
                        if (syntax.op2QMarkLtNamedGroup()) {
                            unfetch();
                            c = '<';

                            listCapture = false; // named_group1:
                            node = parseEncloseNamedGroup2(listCapture); // named_group2:
                            break;
                        } else {
                            newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                        }

                    } else { // USE_NAMED_GROUP
                        newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                    } // USE_NAMED_GROUP
                }
                break;
            case '@':
                if (syntax.op2AtMarkCaptureHistory()) {
                    if (Config.USE_NAMED_GROUP) {
                        if (syntax.op2QMarkLtNamedGroup()) {
                            fetch();
                            if (c == '<' || c == '\'') {
                                listCapture = true;
                                node = parseEncloseNamedGroup2(listCapture); // goto named_group2 /* (?@<name>...) */
                            }
                            unfetch();
                        }
                    } // USE_NAMED_GROUP
                    EncloseNode en = new EncloseNode(env.option, false); // node_new_enclose_memory
                    int num = env.addMemEntry();
                    if (num >= BitStatus.BIT_STATUS_BITS_NUM) newValueException(ERR_GROUP_NUMBER_OVER_FOR_CAPTURE_HISTORY);
                    en.regNum = num;
                    node = en;
                } else {
                    newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                }
                break;

            // case 'p': #ifdef USE_POSIXLINE_OPTION
            case '-':
            case 'i':
            case 'm':
            case 's':
            case 'x':
                boolean neg = false;
                while (true) {
                    switch(c) {
                    case ':':
                    case ')':
                        break;
                    case '-':
                        neg = true;
                        break;
                    case 'x':
                        option = bsOnOff(option, Option.EXTEND, neg);
                        break;
                    case 'i':
                        option = bsOnOff(option, Option.IGNORECASE, neg);
                        break;
                    case 's':
                        if (syntax.op2OptionPerl()) {
                            option = bsOnOff(option, Option.MULTILINE, neg);
                        } else {
                            newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                        }
                        break;
                    case 'm':
                        if (syntax.op2OptionPerl()) {
                            option = bsOnOff(option, Option.SINGLELINE, !neg);
                        } else if (syntax.op2OptionRuby()) {
                            option = bsOnOff(option, Option.MULTILINE, neg);
                        } else {
                            newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                        }
                        break;
                    // case 'p': #ifdef USE_POSIXLINE_OPTION // not defined
                    // option = bsOnOff(option, Option.MULTILINE|Option.SINGLELINE, neg);
                    // break;

                    default:
                        newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
                    } // switch

                    if (c == ')') {
                        EncloseNode en = new EncloseNode(option, 0); // node_new_option
                        node = en;
                        returnCode = 2; /* option only */
                        return node;
                    } else if (c == ':') {
                        int prev = env.option;
                        env.option = option;
                        fetchToken();
                        Node target = parseSubExp(term);
                        env.option = prev;
                        EncloseNode en = new EncloseNode(option, 0); // node_new_option
                        en.setTarget(target);
                        node = en;
                        returnCode = 0;
                        return node;
                    }
                    if (!left()) newSyntaxException(ERR_END_PATTERN_IN_GROUP);
                    fetch();
                } // while

            default:
                newSyntaxException(ERR_UNDEFINED_GROUP_OPTION);
            } // switch

        } else {
            if (isDontCaptureGroup(env.option)) {
                fetchToken(); // goto group
                node = parseSubExp(term);
                returnCode = 1; /* group */
                return node;
            }
            EncloseNode en = new EncloseNode(env.option, false); // node_new_enclose_memory
            int num = env.addMemEntry();
            en.regNum = num;
            node = en;
        }

        fetchToken();
        Node target = parseSubExp(term);

        if (node.getType() == NodeType.ANCHOR) {
            AnchorNode an = (AnchorNode) node;
            an.setTarget(target);
        } else {
            EncloseNode en = (EncloseNode)node;
            en.setTarget(target);
            if (en.type == EncloseType.MEMORY) {
                /* Don't move this to previous of parse_subexp() */
                env.setMemNode(en.regNum, node);
            }
        }
        returnCode = 0;
        return node; // ??
    }

    private Node parseEncloseNamedGroup2(boolean listCapture) {
        int nm = p;
        int num = fetchName(c, false);
        int nameEnd = value;
        num = env.addMemEntry();
        if (listCapture && num >= BitStatus.BIT_STATUS_BITS_NUM) newValueException(ERR_GROUP_NUMBER_OVER_FOR_CAPTURE_HISTORY);

        regex.nameAdd(chars, nm, nameEnd, num, syntax);
        EncloseNode en = new EncloseNode(env.option, true); // node_new_enclose_memory
        en.regNum = num;

        Node node = en;

        if (listCapture) env.captureHistory = bsOnAtSimple(env.captureHistory, num);
        env.numNamed++;
        return node;
    }

    private int findStrPosition(int[]s, int n, int from, int to, Ptr nextChar) {
        int x;
        int q;
        int p = from;
        int i = 0;
        while (p < to) {
            x = chars[p];
            q = p + 1;
            if (x == s[0]) {
                for (i=1; i<n && q<to; i++) {
                    x = chars[q];
                    if (x != s[i]) break;
                    q++;
                }
                if (i >= n) {
                    if (chars[nextChar.p] != 0) nextChar.p = q; // we may need zero term semantics...
                    return p;
                }
            }
            p = q;
        }
        return -1;
    }

    private Node parseExp(TokenType term) {
        if (token.type == term) return StringNode.EMPTY; // goto end_of_token

        Node node = null;
        boolean group = false;

        switch(token.type) {
        case ALT:
        case EOT:
            return StringNode.EMPTY; // end_of_token:, node_new_empty

        case SUBEXP_OPEN:
            node = parseEnclose(TokenType.SUBEXP_CLOSE);
            if (returnCode == 1) {
                group = true;
            } else if (returnCode == 2) { /* option only */
                int prev = env.option;
                EncloseNode en = (EncloseNode)node;
                env.option = en.option;
                fetchToken();
                Node target = parseSubExp(term);
                env.option = prev;
                en.setTarget(target);
                return node;
            }
            break;
        case SUBEXP_CLOSE:
            if (!syntax.allowUnmatchedCloseSubexp()) newSyntaxException(ERR_UNMATCHED_CLOSE_PARENTHESIS);
            if (token.escaped) {
                return parseExpTkRawByte(group); // goto tk_raw_byte
            } else {
                return parseExpTkByte(group); // goto tk_byte
            }
        case STRING:
            return parseExpTkByte(group); // tk_byte:

        case RAW_BYTE:
            return parseExpTkRawByte(group); // tk_raw_byte:
        case CODE_POINT:
            char[] buf = new char[] {(char)token.getCode()};
            // #ifdef NUMBERED_CHAR_IS_NOT_CASE_AMBIG ... // setRaw() #else
            node = new StringNode(buf, 0, 1);
            break;

        case QUOTE_OPEN:
            int[] endOp = new int[] {syntax.metaCharTable.esc, 'E'};
            int qstart = p;
            Ptr nextChar = new Ptr();
            int qend = findStrPosition(endOp, endOp.length, qstart, stop, nextChar);
            if (qend == -1) nextChar.p = qend = stop;
            node = new StringNode(chars, qstart, qend);
            p = nextChar.p;
            break;

        case CHAR_TYPE:
            switch(token.getPropCType()) {
            case CharacterType.D:
            case CharacterType.S:
            case CharacterType.W:
                if (Config.NON_UNICODE_SDW) {
                    CClassNode cc = new CClassNode();
                    cc.addCType(token.getPropCType(), false, env, this);
                    if (token.getPropNot()) cc.setNot();
                    node = cc;
                }
                break;

            case CharacterType.WORD:
                node = new CTypeNode(token.getPropCType(), token.getPropNot());
                break;

            case CharacterType.SPACE:
            case CharacterType.DIGIT:
            case CharacterType.XDIGIT:
                // #ifdef USE_SHARED_CCLASS_TABLE ... #endif
                CClassNode ccn = new CClassNode();
                ccn.addCType(token.getPropCType(), false, env, this);
                if (token.getPropNot()) ccn.setNot();
                node = ccn;
                break;

            default:
                newInternalException(ERR_PARSER_BUG);

            } // inner switch
            break;

        case CHAR_PROPERTY:
            node = parseCharProperty();
            break;

        case CC_CC_OPEN:
            CClassNode cc = parseCharClass();
            node = cc;
            if (isIgnoreCase(env.option)) {
                ApplyCaseFoldArg arg = new ApplyCaseFoldArg(env, cc);
                EncodingHelper.applyAllCaseFold(env.caseFoldFlag, ApplyCaseFold.INSTANCE, arg);

                if (arg.altRoot != null) {
                    node = ConsAltNode.newAltNode(node, arg.altRoot);
                }
            }
            break;

        case ANYCHAR:
            node = new AnyCharNode();
            break;

        case ANYCHAR_ANYTIME:
            node = new AnyCharNode();
            QuantifierNode qn = new QuantifierNode(0, QuantifierNode.REPEAT_INFINITE, false);
            qn.setTarget(node);
            node = qn;
            break;

        case BACKREF:
            int[]backRefs = token.getBackrefNum() > 1 ? token.getBackrefRefs() : new int[]{token.getBackrefRef1()};
            node = new BackRefNode(token.getBackrefNum(),
                            backRefs,
                            token.getBackrefByName(),
                            token.getBackrefExistLevel(), // #ifdef USE_BACKREF_AT_LEVEL
                            token.getBackrefLevel(),      // ...
                            env);

            break;

        case CALL:
            if (Config.USE_SUBEXP_CALL) {
                int gNum = token.getCallGNum();

                if (gNum < 0) {
                    gNum = backrefRelToAbs(gNum);
                    if (gNum <= 0) newValueException(ERR_INVALID_BACKREF);
                }
                node = new CallNode(chars, token.getCallNameP(), token.getCallNameEnd(), gNum);
                env.numCall++;
            } // USE_SUBEXP_CALL
            break;

        case ANCHOR:
            node = new AnchorNode(token.getAnchor()); // possible bug in oniguruma
            break;

        case OP_REPEAT:
        case INTERVAL:
            if (syntax.contextIndepRepeatOps()) {
                if (syntax.contextInvalidRepeatOps()) {
                    newSyntaxException(ERR_TARGET_OF_REPEAT_OPERATOR_NOT_SPECIFIED);
                } else {
                    node = StringNode.EMPTY; // node_new_empty
                }
            } else {
                return parseExpTkByte(group); // goto tk_byte
            }
            break;

        default:
            newInternalException(ERR_PARSER_BUG);
        } //switch

        //targetp = node;

        fetchToken(); // re_entry:

        return parseExpRepeat(node, group); // repeat:
    }

    private Node parseExpTkByte(boolean group) {
        StringNode node = new StringNode(chars, token.backP, p); // tk_byte:
        while (true) {
            fetchToken();
            if (token.type != TokenType.STRING) break;

            if (token.backP == node.end) {
                node.end = p; // non escaped character, remain shared, just increase shared range
            } else {
                node.cat(chars, token.backP, p); // non continuous string stream, need to COW
            }
        }
        // targetp = node;
        return parseExpRepeat(node, group); // string_end:, goto repeat
    }

    private Node parseExpTkRawByte(boolean group) {
        // tk_raw_byte:

        // important: we don't use 0xff mask here neither in the compiler
        // (in the template string) so we won't have to mask target
        // strings when comparing against them in the matcher
        StringNode node = new StringNode((char)token.getC());
        node.setRaw();

        int len = 1;
        while (true) {
            if (len >= 1) {
                if (len == 1) {
                    fetchToken();
                    node.clearRaw();
                    // !goto string_end;!
                    return parseExpRepeat(node, group);
                }
            }

            fetchToken();
            if (token.type != TokenType.RAW_BYTE) {
                /* Don't use this, it is wrong for little endian encodings. */
                // USE_PAD_TO_SHORT_BYTE_CHAR ...

                newValueException(ERR_TOO_SHORT_MULTI_BYTE_STRING);
            }

            // important: we don't use 0xff mask here neither in the compiler
            // (in the template string) so we won't have to mask target
            // strings when comparing against them in the matcher
            node.cat((char)token.getC());
            len++;
        } // while
    }

    private Node parseExpRepeat(Node target, boolean group) {
        while (token.type == TokenType.OP_REPEAT || token.type == TokenType.INTERVAL) { // repeat:
            if (target.isInvalidQuantifier()) newSyntaxException(ERR_TARGET_OF_REPEAT_OPERATOR_INVALID);

            QuantifierNode qtfr = new QuantifierNode(token.getRepeatLower(),
                                                     token.getRepeatUpper(),
                                                     token.type == TokenType.INTERVAL);

            qtfr.greedy = token.getRepeatGreedy();
            int ret = qtfr.setQuantifier(target, group, env, chars, getBegin(), getEnd());
            Node qn = qtfr;

            if (token.getRepeatPossessive()) {
                EncloseNode en = new EncloseNode(EncloseType.STOP_BACKTRACK); // node_new_enclose
                en.setTarget(qn);
                qn = en;
            }

            if (ret == 0) {
                target = qn;
            } else if (ret == 2) { /* split case: /abc+/ */
                target = ConsAltNode.newListNode(target, null);
                ConsAltNode tmp = ((ConsAltNode)target).setCdr(ConsAltNode.newListNode(qn, null));

                fetchToken();
                return parseExpRepeatForCar(target, tmp, group);
            }
            fetchToken(); // goto re_entry
        }
        return target;
    }

    private Node parseExpRepeatForCar(Node top, ConsAltNode target, boolean group) {
        while (token.type == TokenType.OP_REPEAT || token.type == TokenType.INTERVAL) { // repeat:
            if (target.car.isInvalidQuantifier()) newSyntaxException(ERR_TARGET_OF_REPEAT_OPERATOR_INVALID);

            QuantifierNode qtfr = new QuantifierNode(token.getRepeatLower(),
                                                     token.getRepeatUpper(),
                                                     token.type == TokenType.INTERVAL);

            qtfr.greedy = token.getRepeatGreedy();
            int ret = qtfr.setQuantifier(target.car, group, env, chars, getBegin(), getEnd());
            Node qn = qtfr;

            if (token.getRepeatPossessive()) {
                EncloseNode en = new EncloseNode(EncloseType.STOP_BACKTRACK); // node_new_enclose
                en.setTarget(qn);
                qn = en;
            }

            if (ret == 0) {
                target.setCar(qn);
            } else if (ret == 2) { /* split case: /abc+/ */
                assert false;
            }
            fetchToken(); // goto re_entry
        }
        return top;
    }

    private Node parseBranch(TokenType term) {
        Node node = parseExp(term);

        if (token.type == TokenType.EOT || token.type == term || token.type == TokenType.ALT) {
            return node;
        } else {
            ConsAltNode top = ConsAltNode.newListNode(node, null);
            ConsAltNode t = top;

            while (token.type != TokenType.EOT && token.type != term && token.type != TokenType.ALT) {
                node = parseExp(term);
                if (node.getType() == NodeType.LIST) {
                    t.setCdr((ConsAltNode)node);
                    while (((ConsAltNode)node).cdr != null ) node = ((ConsAltNode)node).cdr;

                    t = ((ConsAltNode)node);
                } else {
                    t.setCdr(ConsAltNode.newListNode(node, null));
                    t = t.cdr;
                }
            }
            return top;
        }
    }

    /* term_tok: TK_EOT or TK_SUBEXP_CLOSE */
    private Node parseSubExp(TokenType term) {
        Node node = parseBranch(term);

        if (token.type == term) {
            return node;
        } else if (token.type == TokenType.ALT) {
            ConsAltNode top = ConsAltNode.newAltNode(node, null);
            ConsAltNode t = top;
            while (token.type == TokenType.ALT) {
                fetchToken();
                node = parseBranch(term);

                t.setCdr(ConsAltNode.newAltNode(node, null));
                t = t.cdr;
            }

            if (token.type != term) parseSubExpError(term);
            return top;
        } else {
            parseSubExpError(term);
            return null; //not reached
        }
    }

    private void parseSubExpError(TokenType term) {
        if (term == TokenType.SUBEXP_CLOSE) {
            newSyntaxException(ERR_END_PATTERN_WITH_UNMATCHED_PARENTHESIS);
        } else {
            newInternalException(ERR_PARSER_BUG);
        }
    }

    private Node parseRegexp() {
        fetchToken();
        return parseSubExp(TokenType.EOT);
    }
}
