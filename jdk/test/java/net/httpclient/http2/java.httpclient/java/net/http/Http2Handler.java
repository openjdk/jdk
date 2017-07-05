package java.net.http;

import java.io.IOException;

/**
 * A handler which is invoked to process HTTP exchanges. Each
 * HTTP exchange is handled by one of these handlers.
 */
public interface Http2Handler {
    /**
     * Handle the given request and generate an appropriate response.
     * @param exchange the exchange containing the request from the
     *      client and used to send the response
     * @throws NullPointerException if exchange is <code>null</code>
     */
    public abstract void handle (Http2TestExchange exchange) throws IOException;
}

