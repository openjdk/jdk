
            Java(TM) Cryptography Extension Policy Files
    for the Java(TM) Platform, Standard Edition Runtime Environment

                               README
------------------------------------------------------------------------


The JCE architecture allows flexible cryptographic strength to be
configured via the jurisdiction policy files contained within these
directories.

The default JCE policy files bundled in this Java Runtime Environment
allow for "unlimited" cryptographic strengths.  For convenience,
this build also contains the historic "limited" strength policy files
which contain restrictions on cryptographic strengths, but they must be
specifically activated by updating the "crypto.policy" Security property
(e.g. <java-home>/conf/security/java.security) to point to the appropriate
directory.

Each subdirectory contains a complete policy configuration, and additional
subdirectories can be added/removed to reflect local regulations.

JCE for Java SE has been through the U.S. export review process.  The JCE
framework, along with the various JCE providers that come standard with it
(SunJCE, SunEC, SunPKCS11, SunMSCAPI, etc), is exportable from the
United States.

You are advised to consult your export/import control counsel or attorney
to determine the exact requirements of your location, and what policy
settings should be used.

Please see The Java(TM) Cryptography Architecture (JCA) Reference
Guide and the java.security file for more information.
