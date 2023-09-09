package sun.security.util;

import java.io.IOException;
import java.io.Reader;

public interface Decoder<T> {
    T decode(String string, Class <T> tClass) throws IOException;
    T decode(Reader reader, Class <T> tClass) throws IOException;
    T decode(String string) throws IOException;
    T decode(Reader reader) throws IOException;
}

