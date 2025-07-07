The Class-File Verifier
===
The Class-File API provides a verifier, a debug utility that reports as many
verification errors as possible in a class file.

Currently, the verifier closely follows the C++ code that implements the hotspot
verifier. However, there are a few differences:
- The Class-File API verifier tries to collect as many errors as possible, while
  the hotspot verifier fails fast.
- The hotspot verifier has access to other classes and can check access control;
  the Class-File API verifier cannot.

Thus, this verifier cannot serve as a complete implementation of the verifier
specified in the JVMS because it has no access to other class files or loaded
classes.  However, it is still in our interest to make this verifier up to date:
for example, this should not fail upon encountering new language features, and
should at best include all new checks hotspot has as long as the required
information are accessible to the Class-File API.

Last sync: jdk-26+5, July 3rd 2025
