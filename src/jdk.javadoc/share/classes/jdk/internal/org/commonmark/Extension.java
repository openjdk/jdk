package jdk.internal.org.commonmark;

/**
 * Base interface for a parser/renderer extension.
 * <p>
 * Doesn't have any methods itself, but has specific sub interfaces to
 * configure parser/renderer. This base interface is for convenience, so that a list of extensions can be built and then
 * used for configuring both the parser and renderer in the same way.
 */
public interface Extension {
}
