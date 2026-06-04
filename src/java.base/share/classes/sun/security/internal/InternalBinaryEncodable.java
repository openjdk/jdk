package sun.security.internal;

import java.security.BinaryEncodable;

/**
 * This class is a non-public subtype of BinaryEncodable.  This type
 * allows the BinaryEncodable list of permitted subtypes to change
 * over time without causing pre-existing switches to fail because of an
 * unrecognized subtype.
 */

public final class InternalBinaryEncodable implements BinaryEncodable {
}
