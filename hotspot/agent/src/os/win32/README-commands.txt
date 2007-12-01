This debug server uses a largely text-based protocol, except for
certain bulk data transfer operations. All text is in single-byte
US-ASCII except for the strings returned in "proclist".

NOTE that the character '|' (vertical bar) is used as an escape
character to switch the incoming data stream to the debug server into
binary mode, so no text command may contain that character.

Commands understood:

ascii <EOL>                 ::=

    Changes to ASCII mode. This affects all outgoing strings. At
    startup the system is in unicode mode.

unicode <EOL>               ::=

    Changes to UNICODE mode. This affects all outgoing strings. This
    is the default mode upon startup.

proclist <EOL>              ::=
      <int num> [<unsigned int pid> <int charSize> <int numChars> [<binary char_t name>]...]... <EOL>

    Returns integer indicating number of processes to follow, followed
    by (pid, name) pairs. Names are given by (charSize, numChars,
    [char_t]...) tuples; charSize indicates the size of each character
    in bytes, numChars the number of characters in the string, and
    name the raw data for the string. Each individual character of the
    string, if multi-byte, is transmitted in network byte order.
    numChars and name are guaranteed to be separated by precisely one
    US-ASCII space. If process list is not available because of
    limitations of the underlying operating system, number of
    processes returned is 0.

attach <int pid> <EOL>      ::= <bool result> <EOL>

    Attempts to attach to the specified process. Returns 1 if
    successful, 0 if not. Will fail if already attached or if the
    process ID does not exist. Attaching to a process causes the
    process to be suspended.

detach <EOL>                ::= <bool result> <EOL>

    Detaches from the given process. Attaching and detaching multiple
    times during a debugging session is allowed. Detaching causes the
    process to resume execution.

libinfo <EOL>               ::=
      <int numLibs> [<int charSize> <int numChars> [<binary char_t name>]... <address baseAddr>]... <EOL>

    May only be called once attached and the target process must be
    suspended; otherwise, returns 0. Returns list of the full path
    names of all of the loaded modules (including the executable
    image) in the target process, as well as the base address at which
    each module was relocated. See proclist for format of strings, but
    NOTE that charSize is ALWAYS 1 for this particular routine,
    regardless of the setting of ASCII/UNICODE.

peek <address addr> <unsigned int numBytes> <EOL> ::=
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

poke <address addr> |[<binary unsigned int len> [<binary char data>]] <EOL> ::=
     <bool result> <EOL>

    NOTE that the binary portion of this message is prefixed by the
    uppercase US-ASCII character '|' (vertical bar), allowing easier
    synchronization by the server. There is no data between the '|'
    and the rest of the message. ('B' is not used here because
    addresses can contain that letter; no alphanumeric characters are
    used because some of the parsing routines are used by the Solaris
    SA port, and in that port any alphanumeric character can show up
    as a part of a symbol being looked up.)

    May only be called once attached. Writes the address space of the
    target process starting at the given address (see below for format
    specifications), extending the given number of bytes, and
    containing the given data. The number of bytes is a 32-bit
    unsigned integer transmitted with big-endian byte ordering (i.e.,
    most significant byte first). This is followed by the raw binary
    data to be placed at that address. The number of bytes of data
    must match the number of bytes specified in the message.

    Returns true if the write succeeded; false if it failed, for
    example because a portion of the region was not mapped in the
    target address space.

threadlist <EOL>            ::= <int numThreads> [<address threadHandle>...] <EOL>

    May only be called once attached and the target process must be
    suspended; otherwise, returns 0. If available, returns handles for
    all of the threads in the target process. These handles may be
    used as arguments to the getcontext and selectorentry
    commands. They do not need to be (and should not be) duplicated
    via the duphandle command and must not be closed via the
    closehandle command.

duphandle <address handle> <EOL> ::=
    <bool success> [<address duplicate>] <EOL>

    Duplicates a HANDLE read from the target process's address space.
    HANDLE is a Windows construct (typically typedef'd to void *).
    The returned handle should ultimately be closed via the
    closehandle command; failing to do so can cause resource leaks.

    The purpose of this command is to allow the debugger to read the
    value of a thread handle from the target process and query its
    register set and thread selector entries via the getcontext and
    selectorentry commands, below; such use implies that the target
    program has its own notion of the thread list, and further, that
    the debugger has a way of locating that thread list.

closehandle <address handle> <EOL> ::=

    Closes a handle retrieved via the duphandle command, above.

getcontext <address threadHandle> <EOL> ::= <bool success> [<context>] <EOL>
    
    Returns the context for the given thread. The handle must either
    be one of the handles returned from the threadlist command or the
    result of duplicating a thread handle out of the target process
    via the duphandle command. The target process must be suspended.

    The context is returned as a series of hex values which represent
    the following x86 registers in the following order:
      EAX, EBX, ECX, EDX, ESI, EDI, EBP, ESP, EIP, DS, ES, FS, GS,
      CS, SS, EFLAGS, DR0, DR1, DR2, DR3, DR6, DR7

    FIXME: needs to be generalized and/or specified for other
    architectures.

setcontext <address threadHandle> <context> ::= <bool success> <EOL>

    Sets the context of the given thread. The target process must be
    suspended. See the getcontext command for the ordering of the
    registers in the context.

    Even if the setcontext command succeeds, some of the bits in some
    of the registers (like the global enable bits in the debug
    registers) may be overridden by the operating system. To ensure
    the debugger's notion of the register set is up to date, it is
    recommended to follow up a setcontext with a getcontext.

selectorentry <address threadHandle> <int selector> <EOL> ::=
    <bool success>
    [<address limitLow> <address baseLow>
     <address baseMid>  <address flags1>
     <address flags2>   <address baseHi>] <EOL>

    Retrieves a descriptor table entry for the given thread and
    selector. This data structure allows conversion of a
    segment-relative address to a linear virtual address. It is most
    useful for locating the Thread Information Block for a given
    thread handle to be able to find that thread's ID, to be able to
    understand whether two different thread handles in fact refer to
    the same underlying thread.

    This command will only work on the X86 architecture and will
    return false for the success flag (with no additional information
    sent) on other architectures.

suspend                     ::=

    Suspends the target process. Must be attached to a target process.
    A process is suspended when attached to via the attach command. If
    the target process is already suspended then this command has no
    effect.

resume                      ::=

    Resumes the target process without detaching from it. Must be
    attached to a target process. After resuming a target process, the
    debugger client must be prepared to poll for events from the
    target process fairly frequently in order for execution in the
    target process to proceed normally. If the target process is
    already resumed then this command has no effect.

pollevent                   ::=
    <bool eventPresent> [<address threadHandle> <unsigned int eventCode>]

  Additional entries in result for given eventCode:

    LOAD/UNLOAD_DLL_DEBUG_EVENT: <address baseOfDLL>
    EXCEPTION_DEBUG_EVENT:       <unsigned int exceptionCode> <address faultingPC>

      Additional entries for given exceptionCode:

         EXCEPTION_ACCESS_VIOLATION: <bool wasWrite> <address faultingAddress>

    <EOL>

    Polls once to see whether a debug event has been generated by the
    target process. If none is present, returns 0 immediately.
    Otherwise, returns 1 along with a series of textual information
    about the event. The event is not cleared, and the thread resumed,
    until the continueevent command is sent, or the debugger client
    detaches from the target process.

    Typically a debugger client will suspend the target process upon
    reception of a debug event. Otherwise, it is not guaranteed that
    all threads will be suspended upon reception of a debug event, and
    any operations requiring that threads be suspended (including
    fetching the context for the thread which generated the event)
    will fail.

continueevent <bool passEventToClient> ::= <bool success> <EOL>

    Indicates that the current debug event has been used by the
    debugger client and that the target process should be resumed. The
    passEventToClient flag indicates whether the event should be
    propagated to the target process. Breakpoint and single-step
    events should not be propagated to the target. Returns false if
    there was no pending event, true otherwise.

exit <EOL>

    Exits this debugger session.

Format specifications:

// Data formats and example values:
<EOL>          ::=   end of line (typically \n on Unix platforms, or \n\r on Windows)
<address>      ::=   0x12345678[9ABCDEF0] /* up to 64-bit hex value */
<unsigned int> ::=   5                    /* up to 32-bit integer number; no leading sign */
<bool>         ::=   1                    /* ASCII '0' or '1' */
<context>      ::=   <address> ...
