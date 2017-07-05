DTrace HotSpot probes samples
=============================

This directory contains the list of D scripts which could be used to trace
Java application with help of Solaris(tm) 10 Dynamic Tracing (DTrace)
probes.

The directory is organized as:

* helpers/

  This directory contains the auxiliary script to launch Java application
  with D script to debug. See more comments in the scripts.

* hotspot/
  
  This directory contains D scripts which demonstrate usage of 'hotspot'
  provider probes.


* hotspot_jni/

  This directory contains D scripts which demonstrate usage of 'hotspot_jni'
  provider probes.



Requirements to run DTrace
==========================

1. dtrace framework should be installed; (check if /usr/sbin/dtrace exists)

2. the user should have the following rights: 
   dtrace_proc, dtrace_user, dtrace_kernel

    To give a user a privilege on login, insert a line into the 
    /etc/user_attr file of the form: 
    user-name::::defaultpriv=basic,dtrace_proc,dtrace_user,dtrace_kernel

    or

    To give a running process an DTrace privilege, use the ppriv(1) command:
    # ppriv -s A+privilege process-ID
