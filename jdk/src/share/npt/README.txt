
README: For NPT Library.
------------------------

To use this Native Platform Toolkit library, you need to add 
-Isrc/share/npt and -I/src/${platform}/npt (platform is solaris or windows)
to your compilation lines.

To initialize/use the library:

    #include "npt.h"
    
    NptEnv *npt;
    
    NPT_INITIALIZE(&npt, NPT_VERSION, NULL);
    if (npt == NULL) {
        FATAL_ERROR_MESSAGE(("Unable to gain access to Npt library"));
    }

    /* To use the npt utf functions, they require initialization */
    npt->utf = (npt->utfInitialize)(NULL);
    if (npt->utf == NULL) {
        FATAL_ERROR_MESSAGE(("Unable to gain access to Npt utf functions"));
    }

    ...


    /* After all uses is done, it can be terminated, however, if the
     *   process will be exiting anyway it isn't necessary, and if
     *   you have other threads running that might use these handles
     *   you will need to wait here until all those threads have terminated.
     *   So in general, termination can be a pain and slow your process
     *   termination down.
     */
    (npt->utfTerminate)(npt->utf,NULL);
    NPT_TERMINATE(&npt, NULL);


