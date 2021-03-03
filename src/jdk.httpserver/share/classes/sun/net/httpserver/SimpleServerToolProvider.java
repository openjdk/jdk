package sun.net.httpserver;

import java.io.PrintWriter;
import java.util.spi.ToolProvider;

/**
 * An implementation of the {@link java.util.spi.ToolProvider ToolProvider} SPI,
 * providing access to the jdk.httpserver simpleserver tool.
 */
public class SimpleServerToolProvider implements ToolProvider {
    public String name() {
        return "simpleserver";
    }

    public int run(PrintWriter out, PrintWriter err, String... args) {
        SimpleFileServerImpl.mainForProvider(out, args);
        return 0;
    }
}
