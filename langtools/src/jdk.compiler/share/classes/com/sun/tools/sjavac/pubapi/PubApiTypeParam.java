package com.sun.tools.sjavac.pubapi;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class PubApiTypeParam implements Serializable {

    private static final long serialVersionUID = 8899204612014329162L;

    private final String identifier;
    private final List<TypeDesc> bounds;

    public PubApiTypeParam(String identifier, List<TypeDesc> bounds) {
        this.identifier = identifier;
        this.bounds = bounds;
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        PubApiTypeParam other = (PubApiTypeParam) obj;
        return identifier.equals(other.identifier)
            && bounds.equals(other.bounds);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode() ^ bounds.hashCode();
    }

    public String asString() {
        if (bounds.isEmpty())
            return identifier;
        String boundsStr = bounds.stream()
                                 .map(TypeDesc::encodeAsString)
                                 .collect(Collectors.joining(" & "));
        return identifier + " extends " + boundsStr;
    }

    @Override
    public String toString() {
        return String.format("%s[id: %s, bounds: %s]",
                             getClass().getSimpleName(),
                             identifier,
                             bounds);
    }
}
