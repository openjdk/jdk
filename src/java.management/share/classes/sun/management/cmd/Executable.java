package sun.management.cmd;

import java.io.PrintWriter;

/**
 * The {@code Executable} interface should be implemented by any
 * class which is annotated by {@code @Command}.
 *
 * @author Denghui Dong
 */
public interface Executable {

    /**
     * @param output the output when this executable is running
     */
    void execute(PrintWriter output);
}
