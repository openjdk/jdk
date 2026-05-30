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

/**
 * A completer is the mechanism by which tab-completion candidates will be resolved.
 * <p>
 * Completers are used to provide context-sensitive suggestions when the user presses
 * the tab key while typing a command. They analyze the current input line and generate
 * a list of possible completions based on the context.
 * <p>
 * JLine provides several built-in completers in the {@code org.jline.reader.impl.completer}
 * package and in the {@code org.jline.builtins.Completers} class.
 * <p>
 * Completers can be combined and nested to create sophisticated completion behavior.
 * They are typically registered with a {@link LineReader} using the
 * {@link LineReaderBuilder#completer(Completer)} method.
 *
 * @since 2.3
 * @see Candidate
 * @see LineReaderBuilder#completer(Completer)
 */
public interface Completer {
    /**
     * Populates <i>candidates</i> with a list of possible completions for the <i>command line</i>.
     * <p>
     * The list of candidates will be sorted and filtered by the LineReader, so that
     * the list of candidates displayed to the user will usually be smaller than
     * the list given by the completer. Thus it is not necessary for the completer
     * to do any matching based on the current buffer. On the contrary, in order
     * for the typo matcher to work, all possible candidates for the word being
     * completed should be returned.
     * <p>
     * Implementations should add {@link Candidate} objects to the candidates list.
     * Each candidate can include additional information such as descriptions, groups,
     * and display attributes that will be used when presenting completion options
     * to the user.
     * <p>
     * This method is called by the LineReader when the user requests completion,
     * typically by pressing the Tab key.
     *
     * @param reader        The line reader instance that is requesting completion
     * @param line          The parsed command line containing the current input state
     * @param candidates    The {@link List} of candidates to populate with completion options
     * @see Candidate
     */
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates);
}
