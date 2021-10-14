package sun.management.cmd.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provide the metadata of a parameter
 *
 * @author Denghui Dong
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Parameter {

    /**
     * Returns the name of this parameter.
     *
     * @return the name of this parameter
     */
    String name() default "";

    /**
     * Returns the description of this parameter.
     *
     * @return the description of this parameter
     */
    String description() default "";

    /**
     * Returns the order of this parameter, or -1 it's an option.
     *
     * @return the order of this parameter, or -1 it's an option.
     */
    int ordinal() default -1;

    /**
     * Returns the default value of this parameter
     *
     * @return the default value of this parameter.
     */
    String defaultValue() default "";

    /**
     * Returns true if this parameter must have a value, or false otherwise.
     *
     * @return true if this parameter must have a value, or false otherwise
     */
    boolean isMandatory() default false;
}
