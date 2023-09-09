package sun.security.util;

import java.io.IOException;

public interface Encoder<T> {
    String encode(Class <T> T) throws IOException;
}
