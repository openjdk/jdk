package sun.management.cmd.annotation;

import sun.management.cmd.Constant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provide the metadata of a command
 *
 * @author Denghui Dong
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Command {

    /**
     * Returns the name of this command.
     *
     * @return the name of this command
     */
    String name();

    /**
     * Returns the description of this command.
     *
     * @return the description of this command
     */
    String description() default Constant.DEFAULT_DESCRIPTION;

    /**
     * Returns the impact on the application of this command.
     *
     * @return the impact on the application of this command
     */
    String impact() default Constant.DEFAULT_IMPACT;

    /**
     * Returns the permission of this command.
     *
     * @return the permission of this command
     */
    String[] permission() default {"", "", ""};

    /**
     * Returns the export flags.
     *
     * @return the export flags
     */
    int exportFlags() default Constant.FULL_EXPORT;

    /**
     * Check whether this command is enabled or not.
     *
     * @return true if this command is enabled, or false otherwise
     */
    boolean enabled() default true;

    /**
     * Returns the customized message when the command is disabled.
     *
     * @return the customized message when the command is disabled
     */
    String disabledMessage() default Constant.DEFAULT_DISABLED_MESSAGE;
}
