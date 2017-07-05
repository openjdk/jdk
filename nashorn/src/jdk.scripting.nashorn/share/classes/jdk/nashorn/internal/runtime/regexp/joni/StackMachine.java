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

import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsAt;
import java.lang.ref.WeakReference;
import jdk.nashorn.internal.runtime.regexp.joni.constants.StackPopLevel;
import jdk.nashorn.internal.runtime.regexp.joni.constants.StackType;

abstract class StackMachine extends Matcher implements StackType {
    protected static final int INVALID_INDEX = -1;

    protected StackEntry[]stack;
    protected int stk;  // stkEnd

    protected final int[]repeatStk;
    protected final int memStartStk, memEndStk;

    protected StackMachine(final Regex regex, final char[] chars, final int p , final int end) {
        super(regex, chars, p, end);

        this.stack = regex.stackNeeded ? fetchStack() : null;
        final int n = regex.numRepeat + (regex.numMem << 1);
        this.repeatStk = n > 0 ? new int[n] : null;

        memStartStk = regex.numRepeat - 1;
        memEndStk   = memStartStk + regex.numMem;
        /* for index start from 1, mem_start_stk[1]..mem_start_stk[num_mem] */
        /* for index start from 1, mem_end_stk[1]..mem_end_stk[num_mem] */
    }

    private static StackEntry[] allocateStack() {
        final StackEntry[]stack = new StackEntry[Config.INIT_MATCH_STACK_SIZE];
        stack[0] = new StackEntry();
        return stack;
    }

    private void doubleStack() {
        final StackEntry[] newStack = new StackEntry[stack.length << 1];
        System.arraycopy(stack, 0, newStack, 0, stack.length);
        stack = newStack;
    }

    static final ThreadLocal<WeakReference<StackEntry[]>> stacks
            = new ThreadLocal<WeakReference<StackEntry[]>>() {
        @SuppressWarnings("unused")
        @Override
        protected WeakReference<StackEntry[]> initialValue() {
            return new WeakReference<StackEntry[]>(allocateStack());
        }
    };

    @SuppressWarnings("unused")
    private static StackEntry[] fetchStack() {
        WeakReference<StackEntry[]> ref = stacks.get();
        StackEntry[] stack = ref.get();
        if (stack == null) {
            ref = new WeakReference<StackEntry[]>(stack = allocateStack());
            stacks.set(ref);
        }
        return stack;
    }

    protected final void init() {
        if (stack != null) {
            pushEnsured(ALT, regex.codeLength - 1); /* bottom stack */
        }
        if (repeatStk != null) {
            for (int i=1; i<=regex.numMem; i++) {
                repeatStk[i + memStartStk] = repeatStk[i + memEndStk] = INVALID_INDEX;
            }
        }
    }

    protected final StackEntry ensure1() {
        if (stk >= stack.length) {
            doubleStack();
        }
        StackEntry e = stack[stk];
        if (e == null) {
            stack[stk] = e = new StackEntry();
        }
        return e;
    }

    protected final void pushType(final int type) {
        ensure1().type = type;
        stk++;
    }

    private void push(final int type, final int pat, final int s, final int prev) {
        final StackEntry e = ensure1();
        e.type = type;
        e.setStatePCode(pat);
        e.setStatePStr(s);
        e.setStatePStrPrev(prev);
        stk++;
    }

    protected final void pushEnsured(final int type, final int pat) {
        final StackEntry e = stack[stk];
        e.type = type;
        e.setStatePCode(pat);
        stk++;
    }

    protected final void pushAlt(final int pat, final int s, final int prev) {
        push(ALT, pat, s, prev);
    }

    protected final void pushPos(final int s, final int prev) {
        push(POS, -1 /*NULL_UCHARP*/, s, prev);
    }

    protected final void pushPosNot(final int pat, final int s, final int prev) {
        push(POS_NOT, pat, s, prev);
    }

    protected final void pushStopBT() {
        pushType(STOP_BT);
    }

    protected final void pushLookBehindNot(final int pat, final int s, final int sprev) {
        push(LOOK_BEHIND_NOT, pat, s, sprev);
    }

    protected final void pushRepeat(final int id, final int pat) {
        final StackEntry e = ensure1();
        e.type = REPEAT;
        e.setRepeatNum(id);
        e.setRepeatPCode(pat);
        e.setRepeatCount(0);
        stk++;
    }

    protected final void pushRepeatInc(final int sindex) {
        final StackEntry e = ensure1();
        e.type = REPEAT_INC;
        e.setSi(sindex);
        stk++;
    }

    protected final void pushMemStart(final int mnum, final int s) {
        final StackEntry e = ensure1();
        e.type = MEM_START;
        e.setMemNum(mnum);
        e.setMemPstr(s);
        e.setMemStart(repeatStk[memStartStk + mnum]);
        e.setMemEnd(repeatStk[memEndStk + mnum]);
        repeatStk[memStartStk + mnum] = stk;
        repeatStk[memEndStk + mnum] = INVALID_INDEX;
        stk++;
    }

    protected final void pushMemEnd(final int mnum, final int s) {
        final StackEntry e = ensure1();
        e.type = MEM_END;
        e.setMemNum(mnum);
        e.setMemPstr(s);
        e.setMemStart(repeatStk[memStartStk + mnum]);
        e.setMemEnd(repeatStk[memEndStk + mnum]);
        repeatStk[memEndStk + mnum] = stk;
        stk++;
    }

    protected final void pushMemEndMark(final int mnum) {
        final StackEntry e = ensure1();
        e.type = MEM_END_MARK;
        e.setMemNum(mnum);
        stk++;
    }

    protected final int getMemStart(final int mnum) {
        int level = 0;
        int stkp = stk;

        while (stkp > 0) {
            stkp--;
            final StackEntry e = stack[stkp];
            if ((e.type & MASK_MEM_END_OR_MARK) != 0 && e.getMemNum() == mnum) {
                level++;
            } else if (e.type == MEM_START && e.getMemNum() == mnum) {
                if (level == 0) {
                    break;
                }
                level--;
            }
        }
        return stkp;
    }

    protected final void pushNullCheckStart(final int cnum, final int s) {
        final StackEntry e = ensure1();
        e.type = NULL_CHECK_START;
        e.setNullCheckNum(cnum);
        e.setNullCheckPStr(s);
        stk++;
    }

    protected final void pushNullCheckEnd(final int cnum) {
        final StackEntry e = ensure1();
        e.type = NULL_CHECK_END;
        e.setNullCheckNum(cnum);
        stk++;
    }

    // stack debug routines here
    // ...

    protected final void popOne() {
        stk--;
    }

    protected final StackEntry pop() {
        switch (regex.stackPopLevel) {
        case StackPopLevel.FREE:
            return popFree();
        case StackPopLevel.MEM_START:
            return popMemStart();
        default:
            return popDefault();
        }
    }

    private StackEntry popFree() {
        while (true) {
            final StackEntry e = stack[--stk];

            if ((e.type & MASK_POP_USED) != 0) {
                return e;
            }
        }
    }

    private StackEntry popMemStart() {
        while (true) {
            final StackEntry e = stack[--stk];

            if ((e.type & MASK_POP_USED) != 0) {
                return e;
            } else if (e.type == MEM_START) {
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemEnd();
            }
        }
    }

    private StackEntry popDefault() {
        while (true) {
            final StackEntry e = stack[--stk];

            if ((e.type & MASK_POP_USED) != 0) {
                return e;
            } else if (e.type == MEM_START) {
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemEnd();
            } else if (e.type == REPEAT_INC) {
                //int si = stack[stk + IREPEAT_INC_SI];
                //stack[si + IREPEAT_COUNT]--;
                stack[e.getSi()].decreaseRepeatCount();
            } else if (e.type == MEM_END) {
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemEnd();
            }
        }
    }

    protected final void popTilPosNot() {
        while (true) {
            stk--;
            final StackEntry e = stack[stk];

            if (e.type == POS_NOT) {
                break;
            } else if (e.type == MEM_START) {
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemStart();
            } else if (e.type == REPEAT_INC) {
                //int si = stack[stk + IREPEAT_INC_SI];
                //stack[si + IREPEAT_COUNT]--;
                stack[e.getSi()].decreaseRepeatCount();
            } else if (e.type == MEM_END){
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemStart();
            }
        }
    }

    protected final void popTilLookBehindNot() {
        while (true) {
            stk--;
            final StackEntry e = stack[stk];

            if (e.type == LOOK_BEHIND_NOT) {
                break;
            } else if (e.type == MEM_START) {
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemEnd();
            } else if (e.type == REPEAT_INC) {
                //int si = stack[stk + IREPEAT_INC_SI];
                //stack[si + IREPEAT_COUNT]--;
                stack[e.getSi()].decreaseRepeatCount();
            } else if (e.type == MEM_END) {
                repeatStk[memStartStk + e.getMemNum()] = e.getMemStart();
                repeatStk[memEndStk + e.getMemNum()] = e.getMemEnd();
            }
        }
    }

    protected final int posEnd() {
        int k = stk;
        while (true) {
            k--;
            final StackEntry e = stack[k];
            if ((e.type & MASK_TO_VOID_TARGET) != 0) {
                e.type = VOID;
            } else if (e.type == POS) {
                e.type = VOID;
                break;
            }
        }
        return k;
    }

    protected final void stopBtEnd() {
        int k = stk;
        while (true) {
            k--;
            final StackEntry e = stack[k];

            if ((e.type & MASK_TO_VOID_TARGET) != 0) {
                e.type = VOID;
            } else if (e.type == STOP_BT) {
                e.type = VOID;
                break;
            }
        }
    }

    // int for consistency with other null check routines
    protected final int nullCheck(final int id, final int s) {
        int k = stk;
        while (true) {
            k--;
            final StackEntry e = stack[k];

            if (e.type == NULL_CHECK_START) {
                if (e.getNullCheckNum() == id) {
                    return e.getNullCheckPStr() == s ? 1 : 0;
                }
            }
        }
    }

    protected final int nullCheckRec(final int id, final int s) {
        int level = 0;
        int k = stk;
        while (true) {
            k--;
            final StackEntry e = stack[k];

            if (e.type == NULL_CHECK_START) {
                if (e.getNullCheckNum() == id) {
                    if (level == 0) {
                        return e.getNullCheckPStr() == s ? 1 : 0;
                    }
                    level--;
                }
            } else if (e.type == NULL_CHECK_END) {
                level++;
            }
        }
    }

    protected final int nullCheckMemSt(final int id, final int s) {
        int k = stk;
        int isNull;
        while (true) {
            k--;
            StackEntry e = stack[k];

            if (e.type == NULL_CHECK_START) {
                if (e.getNullCheckNum() == id) {
                    if (e.getNullCheckPStr() != s) {
                        isNull = 0;
                        break;
                    }
                    int endp;
                    isNull = 1;
                    while (k < stk) {
                        if (e.type == MEM_START) {
                            if (e.getMemEnd() == INVALID_INDEX) {
                                isNull = 0;
                                break;
                            }
                            if (bsAt(regex.btMemEnd, e.getMemNum())) {
                                endp = stack[e.getMemEnd()].getMemPStr();
                            } else {
                                endp = e.getMemEnd();
                            }
                            if (stack[e.getMemStart()].getMemPStr() != endp) {
                                isNull = 0;
                                break;
                            } else if (endp != s) {
                                isNull = -1; /* empty, but position changed */
                            }
                        }
                        k++;
                        e = stack[k]; // !!
                    }
                    break;
                }
            }
        }
        return isNull;
    }

    protected final int nullCheckMemStRec(final int id, final int s) {
        int level = 0;
        int k = stk;
        int isNull;
        while (true) {
            k--;
            StackEntry e = stack[k];

            if (e.type == NULL_CHECK_START) {
                if (e.getNullCheckNum() == id) {
                    if (level == 0) {
                        if (e.getNullCheckPStr() != s) {
                            isNull = 0;
                            break;
                        }
                        int endp;
                        isNull = 1;
                        while (k < stk) {
                            if (e.type == MEM_START) {
                                if (e.getMemEnd() == INVALID_INDEX) {
                                    isNull = 0;
                                    break;
                                }
                                if (bsAt(regex.btMemEnd, e.getMemNum())) {
                                    endp = stack[e.getMemEnd()].getMemPStr();
                                } else {
                                    endp = e.getMemEnd();
                                }
                                if (stack[e.getMemStart()].getMemPStr() != endp) {
                                    isNull = 0;
                                    break;
                                } else if (endp != s) {
                                    isNull = -1; /* empty, but position changed */
                                }
                            }
                            k++;
                            e = stack[k];
                        }
                        break;
                    }
                    level--;
                }
            } else if (e.type == NULL_CHECK_END) {
                if (e.getNullCheckNum() == id) {
                    level++;
                }
            }
        }
        return isNull;
    }

    protected final int getRepeat(final int id) {
        int level = 0;
        int k = stk;
        while (true) {
            k--;
            final StackEntry e = stack[k];

            if (e.type == REPEAT) {
                if (level == 0) {
                    if (e.getRepeatNum() == id) {
                        return k;
                    }
                }
            } else if (e.type == CALL_FRAME) {
                level--;
            } else if (e.type == RETURN) {
                level++;
            }
        }
    }

    protected final int sreturn() {
        int level = 0;
        int k = stk;
        while (true) {
            k--;
            final StackEntry e = stack[k];

            if (e.type == CALL_FRAME) {
                if (level == 0) {
                    return e.getCallFrameRetAddr();
                }
                level--;
            } else if (e.type == RETURN) {
                level++;
            }
        }
    }
}
