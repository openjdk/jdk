package jdk.internal.org.commonmark.node;

/**
 * A node that uses delimiters in the source form (e.g. <code>*bold*</code>).
 */
public interface Delimited {

    /**
     * @return the opening (beginning) delimiter, e.g. <code>*</code>
     */
    String getOpeningDelimiter();

    /**
     * @return the closing (ending) delimiter, e.g. <code>*</code>
     */
    String getClosingDelimiter();
}
