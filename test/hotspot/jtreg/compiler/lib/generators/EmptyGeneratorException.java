package compiler.lib.generators;

/**
 * An EmptyGeneratorException is thrown if a generator configuration is requested that would result in an empty
 * set of values. For example, bounds such as [1, 0] cause an EmptyGeneratorException. Another example would be
 * restricting a uniform integer generator over the range [0, 1] to [10, 11].
 */
public class EmptyGeneratorException extends RuntimeException {
    public EmptyGeneratorException() {}
}
