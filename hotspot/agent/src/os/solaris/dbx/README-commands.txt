This import module uses a largely text-based protocol, except for
certain bulk data transfer operations. All text is in single-byte
US-ASCII.

Commands understood:

address_size                  ::= <int result>

    Returns 32 if attached to 32-bit process, 64 if 64-bit.

peek_fail_fast <bool arg>     ::=

    Indicates whether "peek" requests should "fail fast"; that is, if
    any of the addresses in the requested range are unmapped, report
    the entire range as unmapped. This is substantially faster than
    the alternative, which is to read the entire range byte-by-byte.
    However, it should only be used when it is guaranteed by the
    client application that peeks come from at most one page. The
    default is that peek_fast_fail is not enabled.

peek <address addr> <unsigned int numBytes> ::=
    B<binary char success>
       [<binary unsigned int len> <binary char isMapped> [<binary char data>]...]...

    NOTE that the binary portion of this message is prefixed by the
    uppercase US-ASCII letter 'B', allowing easier synchronization by
    clients. There is no data between the 'B' and the rest of the
    message.

    May only be called once attached. Reads the address space of the
    target process starting at the given address (see below for format
    specifications) and extending the given number of bytes. Whether
    the read succeeded is indicated by a single byte containing a 1 or
    0 (success or failure). If successful, the return result is given
    in a sequence of ranges. _len_, the length of each range, is
    indicated by a 32-bit unsigned integer transmitted with big-endian
    byte ordering (i.e., most significant byte first).  _isMapped_
    indicates whether the range is mapped or unmapped in the target
    process's address space, and will contain the value 1 or 0 for
    mapped or unmapped, respectively. If the range is mapped,
    _isMapped_ is followed by _data_, containing the raw binary data
    for the range. The sum of all ranges' lengths is guaranteed to be
    equivalent to the number of bytes requested.

poke <address addr> <int numBytes> B[<binary char data>]... ::= <bool result>

    NOTE that the binary portion of this message is prefixed by the
    uppercase US-ASCII letter 'B', allowing easier synchronization by
    clients. There is no data between the 'B' and the rest of the
    message.

    Writes the given data to the target process starting at the given
    address. Returns 1 on success, 0 on failure (i.e., one or more of
    target addresses were unmapped).

mapped <address addr> <int numBytes> ::= <bool result>

    Returns 1 if entire address range [address...address + int arg) is
    mapped in target process's address space, 0 if not

lookup <symbol objName> <symbol sym> ::= <address addr>

    First symbol is object name; second is symbol to be looked up.
    Looks up symbol in target process's symbol table and returns
    address. Returns NULL (0x0) if symbol is not found.

thr_gregs <int tid>                  ::= <int numAddresses> <address...>

    Fetch the "general" (integer) register set for the given thread.
    Returned as a series of hexidecimal values. NOTE: the meaning of
    the return value is architecture-dependent. In general it is the
    contents of the prgregset_t.

exit                                 ::=

    Exits the serviceability agent dbx module, returning control to
    the dbx prompt.

// Data formats and example values:
<address>      ::=   0x12345678[9ABCDEF0] /* up to 64-bit hex value */
<unsigned int> ::=   5                    /* up to 32-bit integer number; no leading sign */
<bool>         ::=   1                    /* ASCII '0' or '1' */
