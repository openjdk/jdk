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
package jdk.nashorn.internal.runtime.regexp.joni.ast;

import jdk.nashorn.internal.runtime.regexp.joni.constants.NodeStatus;

public abstract class StateNode extends Node implements NodeStatus {
    protected int state;

    @Override
    public String toString(final int level) {
        return "\n  state: " + stateToString();
    }

    public String stateToString() {
        final StringBuilder states = new StringBuilder();
        if (isMinFixed()) states.append("MIN_FIXED ");
        if (isMaxFixed()) states.append("MAX_FIXED ");
        if (isMark1()) states.append("MARK1 ");
        if (isMark2()) states.append("MARK2 ");
        if (isMemBackrefed()) states.append("MEM_BACKREFED ");
        if (isStopBtSimpleRepeat()) states.append("STOP_BT_SIMPLE_REPEAT ");
        if (isRecursion()) states.append("RECURSION ");
        if (isCalled()) states.append("CALLED ");
        if (isAddrFixed()) states.append("ADDR_FIXED ");
        if (isInRepeat()) states.append("IN_REPEAT ");
        if (isNestLevel()) states.append("NEST_LEVEL ");
        if (isByNumber()) states.append("BY_NUMBER ");

        return states.toString();
    }

    public boolean isMinFixed() {
        return (state & NST_MIN_FIXED) != 0;
    }

    public void setMinFixed() {
        state |= NST_MIN_FIXED;
    }

    public boolean isMaxFixed() {
        return (state & NST_MAX_FIXED) != 0;
    }

    public void setMaxFixed() {
        state |= NST_MAX_FIXED;
    }

    public boolean isCLenFixed() {
        return (state & NST_CLEN_FIXED) != 0;
    }

    public void setCLenFixed() {
        state |= NST_CLEN_FIXED;
    }

    public boolean isMark1() {
        return (state & NST_MARK1) != 0;
    }

    public void setMark1() {
        state |= NST_MARK1;
    }

    public boolean isMark2() {
        return (state & NST_MARK2) != 0;
    }

    public void setMark2() {
        state |= NST_MARK2;
    }

    public void clearMark2() {
        state &= ~NST_MARK2;
    }

    public boolean isMemBackrefed() {
        return (state & NST_MEM_BACKREFED) != 0;
    }

    public void setMemBackrefed() {
        state |= NST_MEM_BACKREFED;
    }

    public boolean isStopBtSimpleRepeat() {
        return (state & NST_STOP_BT_SIMPLE_REPEAT) != 0;
    }

    public void setStopBtSimpleRepeat() {
        state |= NST_STOP_BT_SIMPLE_REPEAT;
    }

    public boolean isRecursion() {
        return (state & NST_RECURSION) != 0;
    }

    public void setRecursion() {
        state |= NST_RECURSION;
    }

    public boolean isCalled() {
        return (state & NST_CALLED) != 0;
    }

    public void setCalled() {
        state |= NST_CALLED;
    }

    public boolean isAddrFixed() {
        return (state & NST_ADDR_FIXED) != 0;
    }

    public void setAddrFixed() {
        state |= NST_ADDR_FIXED;
    }

    public boolean isInRepeat() {
        return (state & NST_IN_REPEAT) != 0;
    }

    public void setInRepeat() {
        state |= NST_IN_REPEAT;
    }

    public boolean isNestLevel() {
        return (state & NST_NEST_LEVEL) != 0;
    }

    public void setNestLevel() {
        state |= NST_NEST_LEVEL;
    }

    public boolean isByNumber() {
        return (state & NST_BY_NUMBER) != 0;
    }

    public void setByNumber() {
        state |= NST_BY_NUMBER;
    }

}
