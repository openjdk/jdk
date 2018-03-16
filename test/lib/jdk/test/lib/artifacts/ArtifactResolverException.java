package jdk.test.lib.artifacts;

/**
 * Thrown by the ArtifactResolver when failing to resolve an Artifact.
 */
public class ArtifactResolverException extends Exception {

    public ArtifactResolverException(String message) {
        super(message);
    }

    public ArtifactResolverException(String message, Throwable cause) {
        super(message, cause);
    }
}
