/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.util.List;
import java.util.Map;

public interface CompletionMatcher {

    /**
     * Compiles completion matcher functions
     *
     * @param options LineReader options
     * @param prefix invoked by complete-prefix or expand-or-complete-prefix widget
     * @param line The parsed line within which completion has been requested
     * @param caseInsensitive if completion is case insensitive or not
     * @param errors number of errors accepted in matching
     * @param originalGroupName value of JLineReader variable original-group-name
     */
    void compile(Map<LineReader.Option, Boolean> options, boolean prefix, CompletingParsedLine line
            , boolean caseInsensitive, int errors, String originalGroupName);

    /**
     *
     * @param candidates list of candidates
     * @return a list of candidates that completion matcher matches
     */
    List<Candidate> matches(List<Candidate> candidates);

    /**
     *
     * @return a candidate that have exact match, null if no exact match found
     */
    Candidate exactMatch();

    /**
     *
     * @return a common prefix of matched candidates
     */
    String getCommonPrefix();

}
