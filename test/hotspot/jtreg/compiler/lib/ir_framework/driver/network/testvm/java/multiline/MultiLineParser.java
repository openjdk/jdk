/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm.java.multiline;

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessage;
import compiler.lib.ir_framework.test.network.MessageTag;

/**
 * Generic multi-line parser that takes a {@link MultiLineParsingStrategy} to decide how to parse a single line of
 * a multi line {@link JavaMessage}. Once parsing is done, the strategy is queried for the final parsed output.
 */
public class MultiLineParser<Output extends JavaMessage> {
    private enum ParserState {
        NOTHING_PARSED, PARSING, FINISHED_PARSING
    }

    private ParserState parserState;
    private final MultiLineParsingStrategy<Output> multiLineParsingStrategy;

    public MultiLineParser(MultiLineParsingStrategy<Output> multiLineParsingStrategy) {
        this.multiLineParsingStrategy = multiLineParsingStrategy;
        this.parserState = ParserState.NOTHING_PARSED;
    }

    public void parseLine(String line) {
        TestFramework.check(parserState != ParserState.FINISHED_PARSING,
                            "cannot parse another block");
        parserState = ParserState.PARSING;
        multiLineParsingStrategy.parseLine(line);
    }

    /**
     * Once the {@link MessageTag#END_MARKER} was seen, this method is called to mark the end of this multi-line message.
     */
    public void markFinished() {
        TestFramework.check(parserState == ParserState.PARSING,
                            "nothing parsed, cannot have empty block");
        parserState = ParserState.FINISHED_PARSING;
    }

    public Output output() {
        TestFramework.check(parserState != ParserState.PARSING, "either nothing parsed or finished");
        return multiLineParsingStrategy.output();
    }
}
