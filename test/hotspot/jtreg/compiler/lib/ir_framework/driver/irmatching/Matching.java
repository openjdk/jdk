package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.IR;

/**
 * Interface which should be implemented by all entity classes representing a part of an {@link IR @IR} annotation on
 * which IR matching can be initiated as part of the entire IR matching process.
 */
public interface Matching {
    /**
     * Apply matching on the entity which the class represents that implement that interface.
     */
    MatchResult match();
}
