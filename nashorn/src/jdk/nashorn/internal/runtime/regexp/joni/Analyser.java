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

import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsAll;
import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsAt;
import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsClear;
import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsOnAt;
import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsOnAtSimple;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isCaptureGroup;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isFindCondition;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isIgnoreCase;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isMultiline;
import static jdk.nashorn.internal.runtime.regexp.joni.ast.ConsAltNode.newAltNode;
import static jdk.nashorn.internal.runtime.regexp.joni.ast.QuantifierNode.isRepeatInfinite;

import java.util.HashSet;

import jdk.nashorn.internal.runtime.regexp.joni.ast.AnchorNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.BackRefNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CClassNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CTypeNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.CallNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.ConsAltNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.EncloseNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.Node;
import jdk.nashorn.internal.runtime.regexp.joni.ast.QuantifierNode;
import jdk.nashorn.internal.runtime.regexp.joni.ast.StringNode;
import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.EncloseType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.NodeType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.RegexState;
import jdk.nashorn.internal.runtime.regexp.joni.constants.StackPopLevel;
import jdk.nashorn.internal.runtime.regexp.joni.constants.TargetInfo;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.CharacterType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.ObjPtr;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.Ptr;

final class Analyser extends Parser {

    protected Analyser(ScanEnvironment env, char[] chars, int p, int end) {
        super(env, chars, p, end);
    }

    protected final void compile() {
        regex.state = RegexState.COMPILING;

        if (Config.DEBUG) {
            Config.log.println(new String(chars, getBegin(), getEnd()));
        }

        reset();

        regex.numMem = 0;
        regex.numRepeat = 0;
        regex.numNullCheck = 0;
        //regex.repeatRangeAlloc = 0;
        regex.repeatRangeLo = null;
        regex.repeatRangeHi = null;
        regex.numCombExpCheck = 0;

        if (Config.USE_COMBINATION_EXPLOSION_CHECK) regex.numCombExpCheck = 0;

        parse();

        if (Config.USE_NAMED_GROUP) {
            /* mixed use named group and no-named group */
            if (env.numNamed > 0 && syntax.captureOnlyNamedGroup() && !isCaptureGroup(regex.options)) {
                if (env.numNamed != env.numMem) {
                    root = disableNoNameGroupCapture(root);
                } else {
                    numberedRefCheck(root);
                }
            }
        } // USE_NAMED_GROUP

        if (Config.USE_NAMED_GROUP) {
            if (env.numCall > 0) {
                env.unsetAddrList = new UnsetAddrList(env.numCall);
                setupSubExpCall(root);
                // r != 0 ???
                subexpRecursiveCheckTrav(root);
                // r < 0 -< err, FOUND_CALLED_NODE = 1
                subexpInfRecursiveCheckTrav(root);
                // r != 0  recursion infinite ???
                regex.numCall = env.numCall;
            } else {
                regex.numCall = 0;
            }
        } // USE_NAMED_GROUP

        if (Config.DEBUG_PARSE_TREE_RAW && Config.DEBUG_PARSE_TREE) {
            Config.log.println("<RAW TREE>");
            Config.log.println(root + "\n");
        }

        root = setupTree(root, 0);
        if (Config.DEBUG_PARSE_TREE) {
            if (Config.DEBUG_PARSE_TREE_RAW) Config.log.println("<TREE>");
            root.verifyTree(new HashSet<Node>(), env.reg.warnings);
            Config.log.println(root + "\n");
        }

        regex.captureHistory = env.captureHistory;
        regex.btMemStart = env.btMemStart;
        regex.btMemEnd = env.btMemEnd;

        if (isFindCondition(regex.options)) {
            regex.btMemEnd = bsAll();
        } else {
            regex.btMemEnd = env.btMemEnd;
            regex.btMemEnd |= regex.captureHistory;
        }

        if (Config.USE_COMBINATION_EXPLOSION_CHECK) {
            if (env.backrefedMem == 0 || (Config.USE_SUBEXP_CALL && env.numCall == 0)) {
                setupCombExpCheck(root, 0);

                if (Config.USE_SUBEXP_CALL && env.hasRecursion) {
                    env.numCombExpCheck = 0;
                } else { // USE_SUBEXP_CALL
                    if (env.combExpMaxRegNum > 0) {
                        for (int i=1; i<env.combExpMaxRegNum; i++) {
                            if (bsAt(env.backrefedMem, i)) {
                                env.numCombExpCheck = 0;
                                break;
                            }
                        }
                    }
                }

            } // USE_SUBEXP_CALL
            regex.numCombExpCheck = env.numCombExpCheck;
        } // USE_COMBINATION_EXPLOSION_CHECK

        regex.clearOptimizeInfo();

        if (!Config.DONT_OPTIMIZE) setOptimizedInfoFromTree(root);

        env.memNodes = null;

        new ArrayCompiler(this).compile();
        //new AsmCompiler(this).compile();

        if (regex.numRepeat != 0 || regex.btMemEnd != 0) {
            regex.stackPopLevel = StackPopLevel.ALL;
        } else {
            if (regex.btMemStart != 0) {
                regex.stackPopLevel = StackPopLevel.MEM_START;
            } else {
                regex.stackPopLevel = StackPopLevel.FREE;
            }
        }

        if (Config.DEBUG_COMPILE) {
            if (Config.USE_NAMED_GROUP) Config.log.print(regex.nameTableToString());
            Config.log.println("stack used: " + regex.stackNeeded);
            if (Config.USE_STRING_TEMPLATES) Config.log.print("templates: " + regex.templateNum + "\n");
            Config.log.println(new ByteCodePrinter(regex).byteCodeListToString());

        } // DEBUG_COMPILE

        regex.state = RegexState.NORMAL;
    }

    private void noNameDisableMapFor_cosAlt(Node node, int[]map, Ptr counter) {
        ConsAltNode can = (ConsAltNode)node;
        do {
            can.setCar(noNameDisableMap(can.car, map, counter));
        } while ((can = can.cdr) != null);
    }

    private void noNameDisableMapFor_quantifier(Node node, int[]map, Ptr counter) {
        QuantifierNode qn = (QuantifierNode)node;
        Node target = qn.target;
        Node old = target;
        target = noNameDisableMap(target, map, counter);

        if (target != old) {
            qn.setTarget(target);
            if (target.getType() == NodeType.QTFR) qn.reduceNestedQuantifier((QuantifierNode)target);
        }
    }

    private Node noNameDisableMapFor_enclose(Node node, int[]map, Ptr counter) {
        EncloseNode en = (EncloseNode)node;
        if (en.type == EncloseType.MEMORY) {
            if (en.isNamedGroup()) {
                counter.p++;
                map[en.regNum] = counter.p;
                en.regNum = counter.p;
                //en.target = noNameDisableMap(en.target, map, counter);
                en.setTarget(noNameDisableMap(en.target, map, counter)); // ???
            } else {
                node = en.target;
                en.target = null; // remove first enclose: /(a)(?<b>c)/
                node = noNameDisableMap(node, map, counter);
            }
        } else {
            //en.target = noNameDisableMap(en.target, map, counter);
            en.setTarget(noNameDisableMap(en.target, map, counter)); // ???
        }
        return node;
    }

    private void noNameDisableMapFor_anchor(Node node, int[]map, Ptr counter) {
        AnchorNode an = (AnchorNode)node;
        switch (an.type) {
            case AnchorNode.PREC_READ:
            case AnchorNode.PREC_READ_NOT:
            case AnchorNode.LOOK_BEHIND:
            case AnchorNode.LOOK_BEHIND_NOT:
                an.setTarget(noNameDisableMap(an.target, map, counter));
        }
    }

    private Node noNameDisableMap(Node node, int[]map, Ptr counter) {
        switch (node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            noNameDisableMapFor_cosAlt(node, map, counter);
            break;
        case NodeType.QTFR:
            noNameDisableMapFor_quantifier(node, map, counter);
            break;
        case NodeType.ENCLOSE:
            node = noNameDisableMapFor_enclose(node, map, counter);
            break;
        case NodeType.ANCHOR:
            noNameDisableMapFor_anchor(node, map, counter);
            break;
        } // switch
        return node;
    }

    private void renumberByMap(Node node, int[]map) {
        switch (node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                renumberByMap(can.car, map);
            } while ((can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            renumberByMap(((QuantifierNode)node).target, map);
            break;

        case NodeType.ENCLOSE:
            renumberByMap(((EncloseNode)node).target, map);
            break;

        case NodeType.BREF:
            ((BackRefNode)node).renumber(map);
            break;
        } // switch
    }

    protected final void numberedRefCheck(Node node) {
        switch (node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                numberedRefCheck(can.car);
            } while ((can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            numberedRefCheck(((QuantifierNode)node).target);
            break;

        case NodeType.ENCLOSE:
            numberedRefCheck(((EncloseNode)node).target);
            break;

        case NodeType.BREF:
            BackRefNode br = (BackRefNode)node;
            if (!br.isNameRef()) newValueException(ERR_NUMBERED_BACKREF_OR_CALL_NOT_ALLOWED);
            break;
        } // switch
    }

    protected final Node disableNoNameGroupCapture(Node root) {
        int[]map = new int[env.numMem + 1];

        for (int i=1; i<=env.numMem; i++) map[i] = 0;

        root = noNameDisableMap(root, map, new Ptr(0));
        renumberByMap(root, map);

        for (int i=1, pos=1; i<=env.numMem; i++) {
            if (map[i] > 0) {
                env.memNodes[pos] = env.memNodes[i];
                pos++;
            }
        }

        int loc = env.captureHistory;
        env.captureHistory = bsClear();

        for (int i=1; i<=Config.MAX_CAPTURE_HISTORY_GROUP; i++) {
            if (bsAt(loc, i)) {
                env.captureHistory = bsOnAtSimple(env.captureHistory, map[i]);
            }
        }

        env.numMem = env.numNamed;
        regex.numMem = env.numNamed;

        regex.renumberNameTable(map);

        return root;
    }

    private void swap(Node a, Node b) {
        a.swap(b);

        if (root == b) {
            root = a;
        } else if (root == a) {
            root = b;
        }
    }

    // USE_INFINITE_REPEAT_MONOMANIAC_MEM_STATUS_CHECK
    private int quantifiersMemoryInfo(Node node) {
        int info = 0;

        switch(node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                int v = quantifiersMemoryInfo(can.car);
                if (v > info) info = v;
            } while ((can = can.cdr) != null);
            break;

        case NodeType.CALL:
            if (Config.USE_SUBEXP_CALL) {
                CallNode cn = (CallNode)node;
                if (cn.isRecursion()) {
                    return TargetInfo.IS_EMPTY_REC; /* tiny version */
                } else {
                    info = quantifiersMemoryInfo(cn.target);
                }
            } // USE_SUBEXP_CALL
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            if (qn.upper != 0) {
                info = quantifiersMemoryInfo(qn.target);
            }
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            switch (en.type) {
            case EncloseType.MEMORY:
                return TargetInfo.IS_EMPTY_MEM;

            case EncloseType.OPTION:
            case EncloseNode.STOP_BACKTRACK:
                info = quantifiersMemoryInfo(en.target);
                break;

            default:
                break;
            } // inner switch
            break;

        case NodeType.BREF:
        case NodeType.STR:
        case NodeType.CTYPE:
        case NodeType.CCLASS:
        case NodeType.CANY:
        case NodeType.ANCHOR:
        default:
            break;
        } // switch

        return info;
    }

    private int getMinMatchLength(Node node) {
        int min = 0;

        switch (node.getType()) {
        case NodeType.BREF:
            BackRefNode br = (BackRefNode)node;
            if (br.isRecursion()) break;

            if (br.back[0] > env.numMem) newValueException(ERR_INVALID_BACKREF);
            min = getMinMatchLength(env.memNodes[br.back[0]]);

            for (int i=1; i<br.backNum; i++) {
                if (br.back[i] > env.numMem) newValueException(ERR_INVALID_BACKREF);
                int tmin = getMinMatchLength(env.memNodes[br.back[i]]);
                if (min > tmin) min = tmin;
            }
            break;

        case NodeType.CALL:
            if (Config.USE_SUBEXP_CALL) {
                CallNode cn = (CallNode)node;
                if (cn.isRecursion()) {
                    EncloseNode en = (EncloseNode)cn.target;
                    if (en.isMinFixed()) min = en.minLength;
                } else {
                    min = getMinMatchLength(cn.target);
                }
            } // USE_SUBEXP_CALL
            break;

        case NodeType.LIST:
            ConsAltNode can = (ConsAltNode)node;
            do {
                min += getMinMatchLength(can.car);
            } while ((can = can.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode y = (ConsAltNode)node;
            do {
                Node x = y.car;
                int tmin = getMinMatchLength(x);
                if (y == node) {
                    min = tmin;
                } else if (min > tmin) {
                    min = tmin;
                }
            } while ((y = y.cdr) != null);
            break;

        case NodeType.STR:
            min = ((StringNode)node).length();
            break;

        case NodeType.CTYPE:
            min = 1;
            break;

        case NodeType.CCLASS:
        case NodeType.CANY:
            min = 1;
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            if (qn.lower > 0) {
                min = getMinMatchLength(qn.target);
                min = MinMaxLen.distanceMultiply(min, qn.lower);
            }
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            switch (en.type) {
            case EncloseType.MEMORY:
                if (Config.USE_SUBEXP_CALL) {
                    if (en.isMinFixed()) {
                        min = en.minLength;
                    } else {
                        min = getMinMatchLength(en.target);
                        en.minLength = min;
                        en.setMinFixed();
                    }
                } // USE_SUBEXP_CALL
                break;

            case EncloseType.OPTION:
            case EncloseType.STOP_BACKTRACK:
                min = getMinMatchLength(en.target);
                break;
            } // inner switch
            break;

        case NodeType.ANCHOR:
        default:
            break;
        } // switch

        return min;
    }

    private int getMaxMatchLength(Node node) {
        int max = 0;

        switch (node.getType()) {
        case NodeType.LIST:
            ConsAltNode ln = (ConsAltNode)node;
            do {
                int tmax = getMaxMatchLength(ln.car);
                max = MinMaxLen.distanceAdd(max, tmax);
            } while ((ln = ln.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode an = (ConsAltNode)node;
            do {
                int tmax = getMaxMatchLength(an.car);
                if (max < tmax) max = tmax;
            } while ((an = an.cdr) != null);
            break;

        case NodeType.STR:
            max = ((StringNode)node).length();
            break;

        case NodeType.CTYPE:
            max = 1;
            break;

        case NodeType.CCLASS:
        case NodeType.CANY:
            max = 1;
            break;

        case NodeType.BREF:
            BackRefNode br = (BackRefNode)node;
            if (br.isRecursion()) {
                max = MinMaxLen.INFINITE_DISTANCE;
                break;
            }

            for (int i=0; i<br.backNum; i++) {
                if (br.back[i] > env.numMem) newValueException(ERR_INVALID_BACKREF);
                int tmax = getMaxMatchLength(env.memNodes[br.back[i]]);
                if (max < tmax) max = tmax;
            }
            break;

        case NodeType.CALL:
            if (Config.USE_SUBEXP_CALL) {
                CallNode cn = (CallNode)node;
                if (!cn.isRecursion()) {
                    max = getMaxMatchLength(cn.target);
                } else {
                    max = MinMaxLen.INFINITE_DISTANCE;
                }
            } // USE_SUBEXP_CALL
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            if (qn.upper != 0) {
                max = getMaxMatchLength(qn.target);
                if (max != 0) {
                    if (!isRepeatInfinite(qn.upper)) {
                        max = MinMaxLen.distanceMultiply(max, qn.upper);
                    } else {
                        max = MinMaxLen.INFINITE_DISTANCE;
                    }
                }
            }
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            switch (en.type) {
            case EncloseType.MEMORY:
                if (Config.USE_SUBEXP_CALL) {
                    if (en.isMaxFixed()) {
                        max = en.maxLength;
                    } else {
                        max = getMaxMatchLength(en.target);
                        en.maxLength = max;
                        en.setMaxFixed();
                    }
                } // USE_SUBEXP_CALL
                break;

            case EncloseType.OPTION:
            case EncloseType.STOP_BACKTRACK:
                max = getMaxMatchLength(en.target);
                break;
            } // inner switch
            break;

        case NodeType.ANCHOR:
        default:
            break;
        } // switch

        return max;
    }

    private static final int GET_CHAR_LEN_VARLEN            = -1;
    private static final int GET_CHAR_LEN_TOP_ALT_VARLEN    = -2;
    protected final int getCharLengthTree(Node node) {
        return getCharLengthTree(node, 0);
    }

    private int getCharLengthTree(Node node, int level) {
        level++;

        int len = 0;
        returnCode = 0;

        switch(node.getType()) {
        case NodeType.LIST:
            ConsAltNode ln = (ConsAltNode)node;
            do {
                int tlen = getCharLengthTree(ln.car, level);
                if (returnCode == 0) len = MinMaxLen.distanceAdd(len, tlen);
            } while (returnCode == 0 && (ln = ln.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode an = (ConsAltNode)node;
            boolean varLen = false;

            int tlen = getCharLengthTree(an.car, level);
            while (returnCode == 0 && (an = an.cdr) != null) {
                int tlen2 = getCharLengthTree(an.car, level);
                if (returnCode == 0) {
                    if (tlen != tlen2) varLen = true;
                }
            }

            if (returnCode == 0) {
                if (varLen) {
                    if (level == 1) {
                        returnCode = GET_CHAR_LEN_TOP_ALT_VARLEN;
                    } else {
                        returnCode = GET_CHAR_LEN_VARLEN;
                    }
                } else {
                    len = tlen;
                }
            }
            break;

        case NodeType.STR:
            StringNode sn = (StringNode)node;
            len = sn.length();
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            if (qn.lower == qn.upper) {
                tlen = getCharLengthTree(qn.target, level);
                if (returnCode == 0) len = MinMaxLen.distanceMultiply(tlen, qn.lower);
            } else {
                returnCode = GET_CHAR_LEN_VARLEN;
            }
            break;

        case NodeType.CALL:
            if (Config.USE_SUBEXP_CALL) {
                CallNode cn = (CallNode)node;
                if (!cn.isRecursion()) {
                    len = getCharLengthTree(cn.target, level);
                } else {
                    returnCode = GET_CHAR_LEN_VARLEN;
                }
            } // USE_SUBEXP_CALL
            break;

        case NodeType.CTYPE:
            len = 1;

        case NodeType.CCLASS:
        case NodeType.CANY:
            len = 1;
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            switch(en.type) {
            case EncloseType.MEMORY:
                if (Config.USE_SUBEXP_CALL) {
                    if (en.isCLenFixed()) {
                        len = en.charLength;
                    } else {
                        len = getCharLengthTree(en.target, level);
                        if (returnCode == 0) {
                            en.charLength = len;
                            en.setCLenFixed();
                        }
                    }
                } // USE_SUBEXP_CALL
                break;

            case EncloseType.OPTION:
            case EncloseType.STOP_BACKTRACK:
                len = getCharLengthTree(en.target, level);
                break;
            } // inner switch
            break;

        case NodeType.ANCHOR:
            break;

        default:
            returnCode = GET_CHAR_LEN_VARLEN;
        } // switch
        return len;
    }

    /* x is not included y ==>  1 : 0 */
    private boolean isNotIncluded(Node x, Node y) {
        Node tmp;

        // !retry:!
        retry: while(true) {

        int yType = y.getType();

        switch(x.getType()) {
        case NodeType.CTYPE:
            switch(yType) {
            case NodeType.CTYPE:
                CTypeNode cny = (CTypeNode)y;
                CTypeNode cnx = (CTypeNode)x;
                return cny.ctype == cnx.ctype && cny.not != cnx.not;

            case NodeType.CCLASS:
                // !swap:!
                tmp = x;
                x = y;
                y = tmp;
                // !goto retry;!
                continue retry;

            case NodeType.STR:
                // !goto swap;!
                tmp = x;
                x = y;
                y = tmp;
                continue retry;

            default:
                break;
            } // inner switch
            break;

        case NodeType.CCLASS:
            CClassNode xc = (CClassNode)x;

            switch(yType) {
            case NodeType.CTYPE:
                switch(((CTypeNode)y).ctype) {
                case CharacterType.WORD:
                    if (!((CTypeNode)y).not) {
                        if (xc.mbuf == null && !xc.isNot()) {
                            for (int i=0; i<BitSet.SINGLE_BYTE_SIZE; i++) {
                                if (xc.bs.at(i)) {
                                    if (EncodingHelper.isWord(i)) return false;
                                }
                            }
                            return true;
                        }
                        return false;
                    } else {
                        for (int i=0; i<BitSet.SINGLE_BYTE_SIZE; i++) {
                            if (!EncodingHelper.isWord(i)) {
                                if (!xc.isNot()) {
                                    if (xc.bs.at(i)) return false;
                                } else {
                                    if (!xc.bs.at(i)) return false;
                                }
                            }
                        }
                        return true;
                    }
                    // break; not reached

                default:
                    break;
                } // inner switch
                break;

            case NodeType.CCLASS:
                CClassNode yc = (CClassNode)y;

                for (int i=0; i<BitSet.SINGLE_BYTE_SIZE; i++) {
                    boolean v = xc.bs.at(i);
                    if ((v && !xc.isNot()) || (!v && xc.isNot())) {
                        v = yc.bs.at(i);
                        if ((v && !yc.isNot()) || (!v && yc.isNot())) return false;
                    }
                }
                if ((xc.mbuf == null && !xc.isNot()) || yc.mbuf == null && !yc.isNot()) return true;
                return false;
                // break; not reached

            case NodeType.STR:
                // !goto swap;!
                tmp = x;
                x = y;
                y = tmp;
                continue retry;

            default:
                break;

            } // inner switch
            break; // case NodeType.CCLASS

        case NodeType.STR:
            StringNode xs = (StringNode)x;
            if (xs.length() == 0) break;

            switch (yType) {
            case NodeType.CTYPE:
                CTypeNode cy = ((CTypeNode)y);
                switch (cy.ctype) {
                case CharacterType.WORD:
                    return !cy.not;

                default:
                    break;

                } // inner switch
                break;

            case NodeType.CCLASS:
                CClassNode cc = (CClassNode)y;
                int code = xs.chars[xs.p];
                return !cc.isCodeInCC(code);

            case NodeType.STR:
                StringNode ys = (StringNode)y;
                int len = xs.length();
                if (len > ys.length()) len = ys.length();
                if (xs.isAmbig() || ys.isAmbig()) {
                    /* tiny version */
                    return false;
                } else {
                    for (int i=0, p=ys.p, q=xs.p; i<len; i++, p++, q++) {
                        if (ys.chars[p] != xs.chars[q]) return true;
                    }
                }
                break;

            default:
                break;
            } // inner switch

            break; // case NodeType.STR

        } // switch

        break;
        } // retry: while
        return false;
    }

    private Node getHeadValueNode(Node node, boolean exact) {
        Node n = null;

        switch(node.getType()) {
        case NodeType.BREF:
        case NodeType.ALT:
        case NodeType.CANY:
            break;

        case NodeType.CALL:
            break; // if (Config.USE_SUBEXP_CALL)

        case NodeType.CTYPE:
        case NodeType.CCLASS:
            if (!exact) n = node;
            break;

        case NodeType.LIST:
            n = getHeadValueNode(((ConsAltNode)node).car, exact);
            break;

        case NodeType.STR:
            StringNode sn = (StringNode)node;
            if (sn.end <= sn.p) break; // ???

            if (exact && !sn.isRaw() && isIgnoreCase(regex.options)){
                // nothing
            } else {
                n = node;
            }
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            if (qn.lower > 0) {
                if (qn.headExact != null) {
                    n = qn.headExact;
                } else {
                    n = getHeadValueNode(qn.target, exact);
                }
            }
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;

            switch (en.type) {
            case EncloseType.OPTION:
                int options = regex.options;
                regex.options = en.option;
                n = getHeadValueNode(en.target, exact);
                regex.options = options;
                break;

            case EncloseType.MEMORY:
            case EncloseType.STOP_BACKTRACK:
                n = getHeadValueNode(en.target, exact);
                break;
            } // inner switch
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            if (an.type == AnchorType.PREC_READ) n = getHeadValueNode(an.target, exact);
            break;

        default:
            break;
        } // switch

        return n;
    }

    // true: invalid
    private boolean checkTypeTree(Node node, int typeMask, int encloseMask, int anchorMask) {
        if ((node.getType2Bit() & typeMask) == 0) return true;

        boolean invalid = false;

        switch(node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                invalid = checkTypeTree(can.car, typeMask, encloseMask, anchorMask);
            } while (!invalid && (can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            invalid = checkTypeTree(((QuantifierNode)node).target, typeMask, encloseMask, anchorMask);
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            if ((en.type & encloseMask) == 0) return true;
            invalid = checkTypeTree(en.target, typeMask, encloseMask, anchorMask);
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            if ((an.type & anchorMask) == 0) return true;

            if (an.target != null) invalid = checkTypeTree(an.target, typeMask, encloseMask, anchorMask);
            break;

        default:
            break;

        } // switch

        return invalid;
    }

    private static final int RECURSION_EXIST       = 1;
    private static final int RECURSION_INFINITE    = 2;
    private int subexpInfRecursiveCheck(Node node, boolean head) {
        int r = 0;

        switch (node.getType()) {
        case NodeType.LIST:
            int min;
            ConsAltNode x = (ConsAltNode)node;
            do {
                int ret = subexpInfRecursiveCheck(x.car, head);
                if (ret == RECURSION_INFINITE) return ret;
                r |= ret;
                if (head) {
                    min = getMinMatchLength(x.car);
                    if (min != 0) head = false;
                }
            } while ((x = x.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            r = RECURSION_EXIST;
            do {
                int ret = subexpInfRecursiveCheck(can.car, head);
                if (ret == RECURSION_INFINITE) return ret;
                r &= ret;
            } while ((can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            r = subexpInfRecursiveCheck(qn.target, head);
            if (r == RECURSION_EXIST) {
                if (qn.lower == 0) r = 0;
            }
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.PREC_READ:
            case AnchorType.PREC_READ_NOT:
            case AnchorType.LOOK_BEHIND:
            case AnchorType.LOOK_BEHIND_NOT:
                r = subexpInfRecursiveCheck(an.target, head);
                break;
            } // inner switch
            break;

        case NodeType.CALL:
            r = subexpInfRecursiveCheck(((CallNode)node).target, head);
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            if (en.isMark2()) {
                return 0;
            } else if (en.isMark1()) {
                return !head ? RECURSION_EXIST : RECURSION_INFINITE;
                // throw exception here ???
            } else {
                en.setMark2();
                r = subexpInfRecursiveCheck(en.target, head);
                en.clearMark2();
            }
            break;

        default:
            break;
        } // switch
        return r;
    }

    protected final int subexpInfRecursiveCheckTrav(Node node) {
        int r = 0;

        switch (node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                r = subexpInfRecursiveCheckTrav(can.car);
            } while (r == 0 && (can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            r = subexpInfRecursiveCheckTrav(((QuantifierNode)node).target);
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.PREC_READ:
            case AnchorType.PREC_READ_NOT:
            case AnchorType.LOOK_BEHIND:
            case AnchorType.LOOK_BEHIND_NOT:
                r = subexpInfRecursiveCheckTrav(an.target);
                break;
            } // inner switch
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            if (en.isRecursion()) {
                en.setMark1();
                r = subexpInfRecursiveCheck(en.target, true);
                if (r > 0) newValueException(ERR_NEVER_ENDING_RECURSION);
                en.clearMark1();
            }
            r = subexpInfRecursiveCheckTrav(en.target);
            break;

        default:
            break;
        } // switch

        return r;
    }

    private int subexpRecursiveCheck(Node node) {
        int r = 0;

        switch (node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                r |= subexpRecursiveCheck(can.car);
            } while ((can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            r = subexpRecursiveCheck(((QuantifierNode)node).target);
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.PREC_READ:
            case AnchorType.PREC_READ_NOT:
            case AnchorType.LOOK_BEHIND:
            case AnchorType.LOOK_BEHIND_NOT:
                r = subexpRecursiveCheck(an.target);
                break;
            } // inner switch
            break;

        case NodeType.CALL:
            CallNode cn = (CallNode)node;
            r = subexpRecursiveCheck(cn.target);
            if (r != 0) cn.setRecursion();
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            if (en.isMark2()) {
                return 0;
            } else if (en.isMark1()) {
                return 1; /* recursion */
            } else {
                en.setMark2();
                r = subexpRecursiveCheck(en.target);
                en.clearMark2();
            }
            break;

        default:
            break;
        } // switch

        return r;
    }

    private static final int FOUND_CALLED_NODE  = 1;
    protected final int subexpRecursiveCheckTrav(Node node) {
        int r = 0;

        switch (node.getType()) {
        case NodeType.LIST:
        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                int ret = subexpRecursiveCheckTrav(can.car);
                if (ret == FOUND_CALLED_NODE) {
                    r = FOUND_CALLED_NODE;
                }
                // else if (ret < 0) return ret; ???
            } while ((can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            r = subexpRecursiveCheckTrav(qn.target);
            if (qn.upper == 0) {
                if (r == FOUND_CALLED_NODE) qn.isRefered = true;
            }
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.PREC_READ:
            case AnchorType.PREC_READ_NOT:
            case AnchorType.LOOK_BEHIND:
            case AnchorType.LOOK_BEHIND_NOT:
                r = subexpRecursiveCheckTrav(an.target);
                break;
            } // inner switch
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            if (!en.isRecursion()) {
                if (en.isCalled()) {
                    en.setMark1();
                    r = subexpRecursiveCheck(en.target);
                    if (r != 0) en.setRecursion();
                    en.clearMark1();
                }
            }
            r = subexpRecursiveCheckTrav(en.target);
            if (en.isCalled()) r |= FOUND_CALLED_NODE;
            break;

        default:
            break;
        } // switch

        return r;
    }

    private void setCallAttr(CallNode cn) {
        cn.target = env.memNodes[cn.groupNum]; // no setTarget in call nodes!
        if (cn.target == null) newValueException(ERR_UNDEFINED_NAME_REFERENCE, cn.nameP, cn.nameEnd);

        ((EncloseNode)cn.target).setCalled();
        env.btMemStart = BitStatus.bsOnAt(env.btMemStart, cn.groupNum);
        cn.unsetAddrList = env.unsetAddrList;
    }

    protected final void setupSubExpCall(Node node) {

        switch(node.getType()) {
        case NodeType.LIST:
            ConsAltNode ln = (ConsAltNode)node;
            do {
                setupSubExpCall(ln.car);
            } while ((ln = ln.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode can = (ConsAltNode)node;
            do {
                setupSubExpCall(can.car);
            } while ((can = can.cdr) != null);
            break;

        case NodeType.QTFR:
            setupSubExpCall(((QuantifierNode)node).target);
            break;

        case NodeType.ENCLOSE:
            setupSubExpCall(((EncloseNode)node).target);
            break;

        case NodeType.CALL:
            CallNode cn = (CallNode)node;

            if (cn.groupNum != 0) {
                int gNum = cn.groupNum;

                if (Config.USE_NAMED_GROUP) {
                    if (env.numNamed > 0 && syntax.captureOnlyNamedGroup() && !isCaptureGroup(env.option)) {
                        newValueException(ERR_NUMBERED_BACKREF_OR_CALL_NOT_ALLOWED);
                    }
                } // USE_NAMED_GROUP
                if (gNum > env.numMem) newValueException(ERR_UNDEFINED_GROUP_REFERENCE, cn.nameP, cn.nameEnd);
                setCallAttr(cn);
            } else {
                if (Config.USE_NAMED_GROUP) {
                    NameEntry ne = regex.nameToGroupNumbers(cn.name, cn.nameP, cn.nameEnd);

                    if (ne == null) {
                        newValueException(ERR_UNDEFINED_NAME_REFERENCE, cn.nameP, cn.nameEnd);
                    } else if (ne.backNum > 1) {
                        newValueException(ERR_MULTIPLEX_DEFINITION_NAME_CALL, cn.nameP, cn.nameEnd);
                    } else {
                        cn.groupNum = ne.backRef1; // ne.backNum == 1 ? ne.backRef1 : ne.backRefs[0]; // ??? need to check ?
                        setCallAttr(cn);
                    }
                }
            }
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.PREC_READ:
            case AnchorType.PREC_READ_NOT:
            case AnchorType.LOOK_BEHIND:
            case AnchorType.LOOK_BEHIND_NOT:
                setupSubExpCall(an.target);
                break;
            }
            break;

        } // switch
    }

    /* divide different length alternatives in look-behind.
    (?<=A|B) ==> (?<=A)|(?<=B)
    (?<!A|B) ==> (?<!A)(?<!B)
     */
    private Node divideLookBehindAlternatives(Node node) {
        AnchorNode an = (AnchorNode)node;
        int anchorType = an.type;
        Node head = an.target;
        Node np = ((ConsAltNode)head).car;

        swap(node, head);

        Node tmp = node;
        node = head;
        head = tmp;

        ((ConsAltNode)node).setCar(head);
        ((AnchorNode)head).setTarget(np);
        np = node;

        while ((np = ((ConsAltNode)np).cdr) != null) {
            AnchorNode insert = new AnchorNode(anchorType);
            insert.setTarget(((ConsAltNode)np).car);
            ((ConsAltNode)np).setCar(insert);
        }

        if (anchorType == AnchorType.LOOK_BEHIND_NOT) {
            np = node;
            do {
                ((ConsAltNode)np).toListNode(); /* alt -> list */
            } while ((np = ((ConsAltNode)np).cdr) != null);
        }

        return node;
    }

    private Node setupLookBehind(Node node) {
        AnchorNode an = (AnchorNode)node;
        int len = getCharLengthTree(an.target);
        switch(returnCode) {
        case 0:
            an.charLength = len;
            break;
        case GET_CHAR_LEN_VARLEN:
            newSyntaxException(ERR_INVALID_LOOK_BEHIND_PATTERN);
            break;
        case GET_CHAR_LEN_TOP_ALT_VARLEN:
            if (syntax.differentLengthAltLookBehind()) {
                return divideLookBehindAlternatives(node);
            } else {
                newSyntaxException(ERR_INVALID_LOOK_BEHIND_PATTERN);
            }
        }
        return node;
    }

    private void nextSetup(Node node, Node nextNode) {
        // retry:
        retry: while(true) {

        int type = node.getType();
        if (type == NodeType.QTFR) {
            QuantifierNode qn = (QuantifierNode)node;
            if (qn.greedy && isRepeatInfinite(qn.upper)) {
                if (Config.USE_QTFR_PEEK_NEXT) {
                    StringNode n = (StringNode)getHeadValueNode(nextNode, true);
                    /* '\0': for UTF-16BE etc... */
                    if (n != null && n.chars[n.p] != 0) { // ?????????
                        qn.nextHeadExact = n;
                    }
                } // USE_QTFR_PEEK_NEXT
                /* automatic posseivation a*b ==> (?>a*)b */
                if (qn.lower <= 1) {
                    if (qn.target.isSimple()) {
                        Node x = getHeadValueNode(qn.target, false);
                        if (x != null) {
                            Node y = getHeadValueNode(nextNode, false);
                            if (y != null && isNotIncluded(x, y)) {
                                EncloseNode en = new EncloseNode(EncloseType.STOP_BACKTRACK); //onig_node_new_enclose
                                en.setStopBtSimpleRepeat();
                                //en.setTarget(qn.target); // optimize it ??
                                swap(node, en);

                                en.setTarget(node);
                            }
                        }
                    }
                }
            }
        } else if (type == NodeType.ENCLOSE) {
            EncloseNode en = (EncloseNode)node;
            if (en.isMemory()) {
                node = en.target;
                // !goto retry;!
                continue retry;
            }
        }

        break;
        } // while
    }

    private void updateStringNodeCaseFoldMultiByte(StringNode sn) {
        char[] chars = sn.chars;
        int end = sn.end;
        value = sn.p;
        int sp = 0;
        char buf;

        while (value < end) {
            int ovalue = value;
            buf = Character.toLowerCase(chars[value++]);

            if (chars[ovalue] != buf) {

                char[] sbuf = new char[sn.length() << 1];
                System.arraycopy(chars, sn.p, sbuf, 0, ovalue - sn.p);
                value = ovalue;
                while (value < end) {
                    buf = Character.toLowerCase(chars[value++]);
                    if (sp >= sbuf.length) {
                        char[]tmp = new char[sbuf.length << 1];
                        System.arraycopy(sbuf, 0, tmp, 0, sbuf.length);
                        sbuf = tmp;
                    }
                    sbuf[sp++] = buf;
                }
                sn.set(sbuf, 0, sp);
                return;
            }
            sp++;
        }
    }

    private void updateStringNodeCaseFold(Node node) {
        StringNode sn = (StringNode)node;
        updateStringNodeCaseFoldMultiByte(sn);
    }

    private Node expandCaseFoldMakeRemString(char[] chars, int p, int end) {
        StringNode node = new StringNode(chars, p, end);

        updateStringNodeCaseFold(node);
        node.setAmbig();
        node.setDontGetOptInfo();
        return node;
    }

    private boolean expandCaseFoldStringAlt(int itemNum, char[] items,
                                              char[] chars, int p, int slen, int end, ObjPtr<Node> node) {

        ConsAltNode altNode;
        node.p = altNode = newAltNode(null, null);

        StringNode snode = new StringNode(chars, p, p + slen);
        altNode.setCar(snode);

        for (int i=0; i<itemNum; i++) {
            snode = new StringNode();

            snode.catCode(items[i]);

            ConsAltNode an = newAltNode(null, null);
            an.setCar(snode);
            altNode.setCdr(an);
            altNode = an;
        }
        return false;
    }

    private static final int THRESHOLD_CASE_FOLD_ALT_FOR_EXPANSION = 8;
    private Node expandCaseFoldString(Node node) {
        StringNode sn = (StringNode)node;

        if (sn.isAmbig() || sn.length() <= 0) return node;

        char[] chars = sn.chars;
        int p = sn.p;
        int end = sn.end;
        int altNum = 1;

        ConsAltNode topRoot = null, root = null;
        ObjPtr<Node> prevNode = new ObjPtr<Node>();
        StringNode stringNode = null;

        while (p < end) {
            char[] items = EncodingHelper.caseFoldCodesByString(regex.caseFoldFlag, chars[p]);

            if (items.length == 0) {
                if (stringNode == null) {
                    if (root == null && prevNode.p != null) {
                        topRoot = root = ConsAltNode.listAdd(null, prevNode.p);
                    }

                    prevNode.p = stringNode = new StringNode(); // onig_node_new_str(NULL, NULL);

                    if (root != null) ConsAltNode.listAdd(root, stringNode);

                }

                stringNode.cat(chars, p, p + 1);
            } else {
                altNum *= (items.length + 1);
                if (altNum > THRESHOLD_CASE_FOLD_ALT_FOR_EXPANSION) break;

                if (root == null && prevNode.p != null) {
                    topRoot = root = ConsAltNode.listAdd(null, prevNode.p);
                }

                expandCaseFoldStringAlt(items.length, items, chars, p, 1, end, prevNode);
                if (root != null) ConsAltNode.listAdd(root, prevNode.p);
                stringNode = null;
            }
            p++;
        }

        if (p < end) {
            Node srem = expandCaseFoldMakeRemString(chars, p, end);

            if (prevNode.p != null && root == null) {
                topRoot = root = ConsAltNode.listAdd(null, prevNode.p);
            }

            if (root == null) {
                prevNode.p = srem;
            } else {
                ConsAltNode.listAdd(root, srem);
            }
        }
        /* ending */
        Node xnode = topRoot != null ? topRoot : prevNode.p;

        swap(node, xnode);
        return xnode;
    }

    private static final int CEC_THRES_NUM_BIG_REPEAT       = 512;
    private static final int CEC_INFINITE_NUM               = 0x7fffffff;

    private static final int CEC_IN_INFINITE_REPEAT         = (1<<0);
    private static final int CEC_IN_FINITE_REPEAT           = (1<<1);
    private static final int CEC_CONT_BIG_REPEAT            = (1<<2);

    protected final int setupCombExpCheck(Node node, int state) {
        int r = state;
        int ret;

        switch (node.getType()) {
        case NodeType.LIST:
            ConsAltNode ln = (ConsAltNode)node;

            do {
                r = setupCombExpCheck(ln.car, r);
                //prev = ((ConsAltNode)node).car;
            } while (r >= 0 && (ln = ln.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode an = (ConsAltNode)node;
            do {
                ret = setupCombExpCheck(an.car, state);
                r |= ret;
            } while (ret >= 0 && (an = an.cdr) != null);
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            int childState = state;
            int addState = 0;
            int varNum;

            if (!isRepeatInfinite(qn.upper)) {
                if (qn.upper > 1) {
                    /* {0,1}, {1,1} are allowed */
                    childState |= CEC_IN_FINITE_REPEAT;

                    /* check (a*){n,m}, (a+){n,m} => (a*){n,n}, (a+){n,n} */
                    if (env.backrefedMem == 0) {
                        if (qn.target.getType() == NodeType.ENCLOSE) {
                            EncloseNode en = (EncloseNode)qn.target;
                            if (en.type == EncloseType.MEMORY) {
                                if (en.target.getType() == NodeType.QTFR) {
                                    QuantifierNode q = (QuantifierNode)en.target;
                                    if (isRepeatInfinite(q.upper) && q.greedy == qn.greedy) {
                                        qn.upper = qn.lower == 0 ? 1 : qn.lower;
                                        if (qn.upper == 1) childState = state;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if ((state & CEC_IN_FINITE_REPEAT) != 0) {
                qn.combExpCheckNum = -1;
            } else {
                if (isRepeatInfinite(qn.upper)) {
                    varNum = CEC_INFINITE_NUM;
                    childState |= CEC_IN_INFINITE_REPEAT;
                } else {
                    varNum = qn.upper - qn.lower;
                }

                if (varNum >= CEC_THRES_NUM_BIG_REPEAT) addState |= CEC_CONT_BIG_REPEAT;

                if (((state & CEC_IN_INFINITE_REPEAT) != 0 && varNum != 0) ||
                   ((state & CEC_CONT_BIG_REPEAT) != 0 && varNum >= CEC_THRES_NUM_BIG_REPEAT)) {
                    if (qn.combExpCheckNum == 0) {
                        env.numCombExpCheck++;
                        qn.combExpCheckNum = env.numCombExpCheck;
                        if (env.currMaxRegNum > env.combExpMaxRegNum) {
                            env.combExpMaxRegNum = env.currMaxRegNum;
                        }
                    }
                }
            }
            r = setupCombExpCheck(qn.target, childState);
            r |= addState;
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            switch( en.type) {
            case EncloseNode.MEMORY:
                if (env.currMaxRegNum < en.regNum) {
                    env.currMaxRegNum = en.regNum;
                }
                r = setupCombExpCheck(en.target, state);
                break;

            default:
                r = setupCombExpCheck(en.target, state);
            } // inner switch
            break;

        case NodeType.CALL:
            if (Config.USE_SUBEXP_CALL) {
                CallNode cn = (CallNode)node;
                if (cn.isRecursion()) {
                    env.hasRecursion = true;
                } else {
                    r = setupCombExpCheck(cn.target, state);
                }
            } // USE_SUBEXP_CALL
            break;

        default:
            break;

        } // switch

        return r;
    }

    private static final int IN_ALT                     = (1<<0);
    private static final int IN_NOT                     = (1<<1);
    private static final int IN_REPEAT                  = (1<<2);
    private static final int IN_VAR_REPEAT              = (1<<3);
    private static final int EXPAND_STRING_MAX_LENGTH   = 100;

    /* setup_tree does the following work.
    1. check empty loop. (set qn->target_empty_info)
    2. expand ignore-case in char class.
    3. set memory status bit flags. (reg->mem_stats)
    4. set qn->head_exact for [push, exact] -> [push_or_jump_exact1, exact].
    5. find invalid patterns in look-behind.
    6. expand repeated string.
    */
    protected final Node setupTree(Node node, int state) {
        restart: while (true) {
        switch (node.getType()) {
        case NodeType.LIST:
            ConsAltNode lin = (ConsAltNode)node;
            Node prev = null;
            do {
                setupTree(lin.car, state);
                if (prev != null) {
                    nextSetup(prev, lin.car);
                }
                prev = lin.car;
            } while ((lin = lin.cdr) != null);
            break;

        case NodeType.ALT:
            ConsAltNode aln = (ConsAltNode)node;
            do {
                setupTree(aln.car, (state | IN_ALT));
            } while ((aln = aln.cdr) != null);
            break;

        case NodeType.CCLASS:
            break;

        case NodeType.STR:
            if (isIgnoreCase(regex.options) && !((StringNode)node).isRaw()) {
                node = expandCaseFoldString(node);
            }
            break;

        case NodeType.CTYPE:
        case NodeType.CANY:
            break;

        case NodeType.CALL: // if (Config.USE_SUBEXP_CALL) ?
            break;

        case NodeType.BREF:
            BackRefNode br = (BackRefNode)node;
            for (int i=0; i<br.backNum; i++) {
                if (br.back[i] > env.numMem) newValueException(ERR_INVALID_BACKREF);
                env.backrefedMem = bsOnAt(env.backrefedMem, br.back[i]);
                env.btMemStart = bsOnAt(env.btMemStart, br.back[i]);
                if (Config.USE_BACKREF_WITH_LEVEL) {
                    if (br.isNestLevel()) {
                        env.btMemEnd = bsOnAt(env.btMemEnd, br.back[i]);
                    }
                } // USE_BACKREF_AT_LEVEL
                ((EncloseNode)env.memNodes[br.back[i]]).setMemBackrefed();
            }
            break;

        case NodeType.QTFR:
            QuantifierNode qn = (QuantifierNode)node;
            Node target = qn.target;

            if ((state & IN_REPEAT) != 0) qn.setInRepeat();

            if (isRepeatInfinite(qn.upper) || qn.lower >= 1) {
                int d = getMinMatchLength(target);
                if (d == 0) {
                    qn.targetEmptyInfo = TargetInfo.IS_EMPTY;
                    if (Config.USE_MONOMANIAC_CHECK_CAPTURES_IN_ENDLESS_REPEAT) {
                        int info = quantifiersMemoryInfo(target);
                        if (info > 0) qn.targetEmptyInfo = info;
                    } // USE_INFINITE_REPEAT_MONOMANIAC_MEM_STATUS_CHECK
                    // strange stuff here (turned off)
                }
            }

            state |= IN_REPEAT;
            if (qn.lower != qn.upper) state |= IN_VAR_REPEAT;

            target = setupTree(target, state);

            /* expand string */
            if (target.getType() == NodeType.STR) {
                if (!isRepeatInfinite(qn.lower) && qn.lower == qn.upper &&
                    qn.lower > 1 && qn.lower <= EXPAND_STRING_MAX_LENGTH) {
                    StringNode sn = (StringNode)target;
                    int len = sn.length();

                    if (len * qn.lower <= EXPAND_STRING_MAX_LENGTH) {
                        StringNode str = qn.convertToString(sn.flag);
                        int n = qn.lower;
                        for (int i = 0; i < n; i++) {
                            str.cat(sn.chars, sn.p, sn.end);
                        }
                        break; /* break case NT_QTFR: */
                    }

                }
            }
            if (Config.USE_OP_PUSH_OR_JUMP_EXACT) {
                if (qn.greedy && qn.targetEmptyInfo != 0) {
                    if (target.getType() == NodeType.QTFR) {
                        QuantifierNode tqn = (QuantifierNode)target;
                        if (tqn.headExact != null) {
                            qn.headExact = tqn.headExact;
                            tqn.headExact = null;
                        }
                    } else {
                        qn.headExact = getHeadValueNode(qn.target, true);
                    }
                }
            } // USE_OP_PUSH_OR_JUMP_EXACT
            break;

        case NodeType.ENCLOSE:
            EncloseNode en = (EncloseNode)node;
            switch (en.type) {
            case EncloseType.OPTION:
                int options = regex.options;
                regex.options = en.option;
                setupTree(en.target, state);
                regex.options = options;
                break;

            case EncloseType.MEMORY:
                if ((state & (IN_ALT | IN_NOT | IN_VAR_REPEAT)) != 0) {
                    env.btMemStart = bsOnAt(env.btMemStart, en.regNum);
                    /* SET_ENCLOSE_STATUS(node, NST_MEM_IN_ALT_NOT); */

                }
                setupTree(en.target, state);
                break;

            case EncloseType.STOP_BACKTRACK:
                setupTree(en.target, state);
                if (en.target.getType() == NodeType.QTFR) {
                    QuantifierNode tqn = (QuantifierNode)en.target;
                    if (isRepeatInfinite(tqn.upper) && tqn.lower <= 1 && tqn.greedy) {
                        /* (?>a*), a*+ etc... */
                        if (tqn.target.isSimple()) en.setStopBtSimpleRepeat();
                    }
                }
                break;

            } // inner switch
            break;

        case NodeType.ANCHOR:
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.PREC_READ:
                setupTree(an.target, state);
                break;

            case AnchorType.PREC_READ_NOT:
                setupTree(an.target, (state | IN_NOT));
                break;

            case AnchorType.LOOK_BEHIND:
                if (checkTypeTree(an.target, NodeType.ALLOWED_IN_LB, EncloseType.ALLOWED_IN_LB, AnchorType.ALLOWED_IN_LB)) newSyntaxException(ERR_INVALID_LOOK_BEHIND_PATTERN);
                node = setupLookBehind(node);
                if (node.getType() != NodeType.ANCHOR) continue restart;
                setupTree(((AnchorNode)node).target, state);
                break;

            case AnchorType.LOOK_BEHIND_NOT:
                if (checkTypeTree(an.target, NodeType.ALLOWED_IN_LB, EncloseType.ALLOWED_IN_LB, AnchorType.ALLOWED_IN_LB)) newSyntaxException(ERR_INVALID_LOOK_BEHIND_PATTERN);
                node = setupLookBehind(node);
                if (node.getType() != NodeType.ANCHOR) continue restart;
                setupTree(((AnchorNode)node).target, (state | IN_NOT));
                break;

            } // inner switch
            break;
        } // switch
        return node;
        } // restart: while
    }

    private static final int MAX_NODE_OPT_INFO_REF_COUNT   = 5;
    private void optimizeNodeLeft(Node node, NodeOptInfo opt, OptEnvironment oenv) { // oenv remove, pass mmd
        opt.clear();
        opt.setBoundNode(oenv.mmd);

        switch (node.getType()) {
        case NodeType.LIST: {
            OptEnvironment nenv = new OptEnvironment();
            NodeOptInfo nopt = new NodeOptInfo();
            nenv.copy(oenv);
            ConsAltNode lin = (ConsAltNode)node;
            do {
                optimizeNodeLeft(lin.car, nopt, nenv);
                nenv.mmd.add(nopt.length);
                opt.concatLeftNode(nopt);
            } while ((lin = lin.cdr) != null);
            break;
        }

        case NodeType.ALT: {
            NodeOptInfo nopt = new NodeOptInfo();
            ConsAltNode aln = (ConsAltNode)node;
            do {
                optimizeNodeLeft(aln.car, nopt, oenv);
                if (aln == node) {
                    opt.copy(nopt);
                } else {
                    opt.altMerge(nopt, oenv);
                }
            } while ((aln = aln.cdr) != null);
            break;
        }

        case NodeType.STR: {
            StringNode sn = (StringNode)node;

            int slen = sn.length();

            if (!sn.isAmbig()) {
                opt.exb.concatStr(sn.chars, sn.p, sn.end, sn.isRaw());

                if (slen > 0) {
                    opt.map.addChar(sn.chars[sn.p]);
                }

                opt.length.set(slen, slen);
            } else {
                int max;
                if (sn.isDontGetOptInfo()) {
                    max = sn.length();
                } else {
                    opt.exb.concatStr(sn.chars, sn.p, sn.end, sn.isRaw());
                    opt.exb.ignoreCase = true;

                    if (slen > 0) {
                        opt.map.addCharAmb(sn.chars, sn.p, sn.end, oenv.caseFoldFlag);
                    }

                    max = slen;
                }
                opt.length.set(slen, max);
            }

            if (opt.exb.length == slen) {
                opt.exb.reachEnd = true;
            }
            break;
        }

        case NodeType.CCLASS: {
            CClassNode cc = (CClassNode)node;
            /* no need to check ignore case. (setted in setup_tree()) */
            if (cc.mbuf != null || cc.isNot()) {
                opt.length.set(1, 1);
            } else {
                for (int i=0; i<BitSet.SINGLE_BYTE_SIZE; i++) {
                    boolean z = cc.bs.at(i);
                    if ((z && !cc.isNot()) || (!z && cc.isNot())) {
                        opt.map.addChar(i);
                    }
                }
                opt.length.set(1, 1);
            }
            break;
        }

        case NodeType.CTYPE: {
            int min;
            int max = 1;
            if (max == 1) {
                min = 1;
                CTypeNode cn = (CTypeNode)node;

                switch (cn.ctype) {
                case CharacterType.WORD:
                    if (cn.not) {
                        for (int i=0; i<BitSet.SINGLE_BYTE_SIZE; i++) {
                            if (!EncodingHelper.isWord(i)) {
                                opt.map.addChar(i);
                            }
                        }
                    } else {
                        for (int i=0; i<BitSet.SINGLE_BYTE_SIZE; i++) {
                            if (EncodingHelper.isWord(i)) {
                                opt.map.addChar(i);
                            }
                        }
                    }
                    break;
                } // inner switch
            } else {
                min = 1;
            }
            opt.length.set(min, max);
            break;
        }

        case NodeType.CANY: {
            opt.length.set(1, 1);
            break;
        }

        case NodeType.ANCHOR: {
            AnchorNode an = (AnchorNode)node;
            switch (an.type) {
            case AnchorType.BEGIN_BUF:
            case AnchorType.BEGIN_POSITION:
            case AnchorType.BEGIN_LINE:
            case AnchorType.END_BUF:
            case AnchorType.SEMI_END_BUF:
            case AnchorType.END_LINE:
                opt.anchor.add(an.type);
                break;

            case AnchorType.PREC_READ:
                NodeOptInfo nopt = new NodeOptInfo();
                optimizeNodeLeft(an.target, nopt, oenv);
                if (nopt.exb.length > 0) {
                    opt.expr.copy(nopt.exb);
                } else if (nopt.exm.length > 0) {
                    opt.expr.copy(nopt.exm);
                }
                opt.expr.reachEnd = false;
                if (nopt.map.value > 0) opt.map.copy(nopt.map);
                break;

            case AnchorType.PREC_READ_NOT:
            case AnchorType.LOOK_BEHIND:    /* Sorry, I can't make use of it. */
            case AnchorType.LOOK_BEHIND_NOT:
                break;

            } // inner switch
            break;
        }

        case NodeType.BREF: {
            BackRefNode br = (BackRefNode)node;

            if (br.isRecursion()) {
                opt.length.set(0, MinMaxLen.INFINITE_DISTANCE);
                break;
            }

            Node[]nodes = oenv.scanEnv.memNodes;

            int min = getMinMatchLength(nodes[br.back[0]]);
            int max = getMaxMatchLength(nodes[br.back[0]]);

            for (int i=1; i<br.backNum; i++) {
                int tmin = getMinMatchLength(nodes[br.back[i]]);
                int tmax = getMaxMatchLength(nodes[br.back[i]]);
                if (min > tmin) min = tmin;
                if (max < tmax) max = tmax;
            }
            opt.length.set(min, max);
            break;
        }

        case NodeType.CALL: {
            if (Config.USE_SUBEXP_CALL) {
                CallNode cn = (CallNode)node;
                if (cn.isRecursion()) {
                    opt.length.set(0, MinMaxLen.INFINITE_DISTANCE);
                } else {
                    int safe = oenv.options;
                    oenv.options = ((EncloseNode)cn.target).option;
                    optimizeNodeLeft(cn.target, opt, oenv);
                    oenv.options = safe;
                }
            } // USE_SUBEXP_CALL
            break;
        }

        case NodeType.QTFR: {
            NodeOptInfo nopt = new NodeOptInfo();
            QuantifierNode qn = (QuantifierNode)node;
            optimizeNodeLeft(qn.target, nopt, oenv);
            if (qn.lower == 0 && isRepeatInfinite(qn.upper)) {
                if (oenv.mmd.max == 0 && qn.target.getType() == NodeType.CANY && qn.greedy) {
                    if (isMultiline(oenv.options)) {
                        opt.anchor.add(AnchorType.ANYCHAR_STAR_ML);
                    } else {
                        opt.anchor.add(AnchorType.ANYCHAR_STAR);
                    }
                }
            } else {
                if (qn.lower > 0) {
                    opt.copy(nopt);
                    if (nopt.exb.length > 0) {
                        if (nopt.exb.reachEnd) {
                            int i;
                            for (i = 2; i <= qn.lower && !opt.exb.isFull(); i++) {
                                opt.exb.concat(nopt.exb);
                            }
                            if (i < qn.lower) {
                                opt.exb.reachEnd = false;
                            }
                        }
                    }
                    if (qn.lower != qn.upper) {
                        opt.exb.reachEnd = false;
                        opt.exm.reachEnd = false;
                    }
                    if (qn.lower > 1) {
                        opt.exm.reachEnd = false;
                    }

                }
            }
            int min = MinMaxLen.distanceMultiply(nopt.length.min, qn.lower);
            int max;
            if (isRepeatInfinite(qn.upper)) {
                max = nopt.length.max > 0 ? MinMaxLen.INFINITE_DISTANCE : 0;
            } else {
                max = MinMaxLen.distanceMultiply(nopt.length.max, qn.upper);
            }
            opt.length.set(min, max);
            break;
        }

        case NodeType.ENCLOSE: {
            EncloseNode en = (EncloseNode)node;
            switch (en.type) {
            case EncloseType.OPTION:
                int save = oenv.options;
                oenv.options = en.option;
                optimizeNodeLeft(en.target, opt, oenv);
                oenv.options = save;
                break;

            case EncloseType.MEMORY:
                if (Config.USE_SUBEXP_CALL && ++en.optCount > MAX_NODE_OPT_INFO_REF_COUNT) {
                    int min = 0;
                    int max = MinMaxLen.INFINITE_DISTANCE;
                    if (en.isMinFixed()) min = en.minLength;
                    if (en.isMaxFixed()) max = en.maxLength;
                    opt.length.set(min, max);
                } else { // USE_SUBEXP_CALL
                    optimizeNodeLeft(en.target, opt, oenv);
                    if (opt.anchor.isSet(AnchorType.ANYCHAR_STAR_MASK)) {
                        if (bsAt(oenv.scanEnv.backrefedMem, en.regNum)) {
                            opt.anchor.remove(AnchorType.ANYCHAR_STAR_MASK);
                        }
                    }
                }
                break;

            case EncloseType.STOP_BACKTRACK:
                optimizeNodeLeft(en.target, opt, oenv);
                break;
            } // inner switch
            break;
        }

        default:
            newInternalException(ERR_PARSER_BUG);
        } // switch
    }

    protected final void setOptimizedInfoFromTree(Node node) {
        NodeOptInfo opt = new NodeOptInfo();
        OptEnvironment oenv = new OptEnvironment();

        oenv.options = regex.options;
        oenv.caseFoldFlag = regex.caseFoldFlag;
        oenv.scanEnv = env;
        oenv.mmd.clear(); // ??

        optimizeNodeLeft(node, opt, oenv);

        regex.anchor = opt.anchor.leftAnchor & (AnchorType.BEGIN_BUF |
                                                AnchorType.BEGIN_POSITION |
                                                AnchorType.ANYCHAR_STAR |
                                                AnchorType.ANYCHAR_STAR_ML);

        regex.anchor |= opt.anchor.rightAnchor & (AnchorType.END_BUF |
                                                  AnchorType.SEMI_END_BUF);

        if ((regex.anchor & (AnchorType.END_BUF | AnchorType.SEMI_END_BUF)) != 0) {
            regex.anchorDmin = opt.length.min;
            regex.anchorDmax = opt.length.max;
        }

        if (opt.exb.length > 0 || opt.exm.length > 0) {
            opt.exb.select(opt.exm);
            if (opt.map.value > 0 && opt.exb.compare(opt.map) > 0) {
                // !goto set_map;!
                regex.setOptimizeMapInfo(opt.map);
                regex.setSubAnchor(opt.map.anchor);
            } else {
                regex.setExactInfo(opt.exb);
                regex.setSubAnchor(opt.exb.anchor);
            }
        } else if (opt.map.value > 0) {
            // !set_map:!
            regex.setOptimizeMapInfo(opt.map);
            regex.setSubAnchor(opt.map.anchor);
        } else {
            regex.subAnchor |= opt.anchor.leftAnchor & AnchorType.BEGIN_LINE;
            if (opt.length.max == 0) regex.subAnchor |= opt.anchor.rightAnchor & AnchorType.END_LINE;
        }

        if (Config.DEBUG_COMPILE || Config.DEBUG_MATCH) {
            Config.log.println(regex.optimizeInfoToString());
        }
    }
}
