package sun.net.httpserver.simpleserver;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.spi.ToolProvider;

/**
 * An implementation of the {@link java.util.spi.ToolProvider ToolProvider} SPI,
 * providing access to the jdk.httpserver simpleserver tool.
 */
public class SimpleServerToolProvider implements ToolProvider {
    public String name() {
        return "simpleserver";
    }

    /**
     * @param out a stream to which "expected" output should be written
     *
     * @param err a stream to which any error messages should be written
     *
     * @param args the command-line arguments for the tool
     *
     * @return the result of executing the tool.
     *         A return value of 0 means the tool did not encounter any errors;
     *         any other value indicates that at least one error occurred
     *         during execution.
     *
     * @throws NullPointerException if any of the arguments are {@code null},
     *         or if there are any {@code null} values in the {@code args}
     *         array
     */
    public int run(PrintWriter out, PrintWriter err, String... args) {
        Objects.requireNonNull(err);
        return Main.start(out, args);
    }
}
