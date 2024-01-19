#include <errno.h>


// Preserve errno across a range of calls

class ErrnoPreserver {
    int _e;
    public:
        ErrnoPreserver() {
            _e = errno;
        }

        ~ErrnoPreserver() {
            errno = _e;
        }
};