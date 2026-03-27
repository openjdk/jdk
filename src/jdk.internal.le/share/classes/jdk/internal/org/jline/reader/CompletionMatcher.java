/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.util.List;
import java.util.Map;

/**
 * Interface for matching and filtering completion candidates.
 * <p>
 * The CompletionMatcher is responsible for determining which completion candidates
 * should be presented to the user based on what they've typed so far. It implements
 * the logic for matching candidates against the input, handling case sensitivity,
 * typo tolerance, and other matching strategies.
 * <p>
 * This interface allows for different matching algorithms to be used, such as:
 * <ul>
 *   <li>Prefix matching - candidates that start with the input text</li>
 *   <li>Substring matching - candidates that contain the input text</li>
 *   <li>Fuzzy matching - candidates that approximately match the input text</li>
 *   <li>Camel case matching - matching based on camel case patterns</li>
 * </ul>
 * <p>
 * The default implementation is {@link org.jline.reader.impl.CompletionMatcherImpl}.
 *
 * @see org.jline.reader.impl.CompletionMatcherImpl
 * @see LineReader.Option#COMPLETE_MATCHER_TYPO
 * @see LineReader.Option#COMPLETE_MATCHER_CAMELCASE
 */
public interface CompletionMatcher {

    /**
     * Initializes the matcher with the current completion context.
     * <p>
     * This method is called before any matching operations to set up the matcher
     * with the current completion context, including the line being completed,
     * reader options, and other parameters that affect how matching should be performed.
     * <p>
     * The matcher uses this information to compile its internal matching functions
     * that will be used to filter candidates.
     *
     * @param options LineReader options that may affect matching behavior
     * @param prefix true if invoked by complete-prefix or expand-or-complete-prefix widget
     * @param line the parsed line within which completion has been requested
     * @param caseInsensitive true if completion should be case insensitive
     * @param errors number of typo errors accepted in matching (for fuzzy matching)
     * @param originalGroupName value of the LineReader variable original-group-name
     */
    void compile(
            Map<LineReader.Option, Boolean> options,
            boolean prefix,
            CompletingParsedLine line,
            boolean caseInsensitive,
            int errors,
            String originalGroupName);

    /**
     * Filters the provided candidates based on the current matching criteria.
     * <p>
     * This method applies the matching algorithm to the list of candidates and
     * returns only those that match the current input according to the configured
     * matching rules. The returned list may be sorted based on match quality.
     *
     * @param candidates the list of candidates to filter
     * @return a list of candidates that match the current input
     */
    List<Candidate> matches(List<Candidate> candidates);

    /**
     * Returns a candidate that exactly matches the current input, if any.
     * <p>
     * An exact match typically means the candidate's value is identical to what
     * the user has typed, possibly ignoring case depending on the matcher configuration.
     * This is used to determine if the completion should be accepted immediately
     * without showing a list of options.
     *
     * @return a candidate that exactly matches the current input, or null if no exact match is found
     */
    Candidate exactMatch();

    /**
     * Returns the longest common prefix shared by all matched candidates.
     * <p>
     * This is used to implement tab completion behavior where pressing tab will
     * automatically complete as much of the input as can be unambiguously determined
     * from the available candidates.
     *
     * @return the longest common prefix of all matched candidates, or an empty string if none
     */
    String getCommonPrefix();
}
