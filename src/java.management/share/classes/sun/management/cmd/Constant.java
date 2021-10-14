package sun.management.cmd;

/**
 * Command constant
 *
 * @author Denghui Dong
 */
public interface Constant {

    /**
     * Accept invocation from the JVM
     */
    int INTERNAL = 1;

    /**
     * Accept invocation via the attachAPI
     */
    int ATTACH_API = 2;

    /**
     * Accept invocation via the MBean
     */
    int MBEAN = 4;

    /**
     * Accept all invocation
     */
    int FULL_EXPORT = INTERNAL | ATTACH_API | MBEAN;

    /**
     * Empty value
     */
    String EMPTY_VALUE = "";

    /**
     * Default description
     */
    String DEFAULT_DESCRIPTION = EMPTY_VALUE;

    /**
     * Default impact
     */
    String DEFAULT_IMPACT = "Low";


    /**
     * Default permission
     */
    String[] DEFAULT_PERMISSION = {EMPTY_VALUE, EMPTY_VALUE, EMPTY_VALUE};

    /**
     * Default disabled message
     */
    String DEFAULT_DISABLED_MESSAGE = "Command currently disabled";
}
