/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.completer;

import java.util.List;

/**
 * A completer is the mechanism by which tab-completion candidates will be resolved.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public interface Completer
{
    //
    // FIXME: Check if we can use CharSequece for buffer?
    //

    /**
     * Populates <i>candidates</i> with a list of possible completions for the <i>buffer</i>.
     *
     * The <i>candidates</i> list will not be sorted before being displayed to the user: thus, the
     * complete method should sort the {@link List} before returning.
     *
     * @param buffer        The buffer
     * @param cursor        The current position of the cursor in the <i>buffer</i>
     * @param candidates    The {@link List} of candidates to populate
     * @return              The index of the <i>buffer</i> for which the completion will be relative
     */
    int complete(String buffer, int cursor, List<CharSequence> candidates);
}
