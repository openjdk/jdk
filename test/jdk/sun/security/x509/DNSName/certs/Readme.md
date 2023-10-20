Reproduces a bug in OpenJDK 17 related to TLS certificate Name Constraints.


This bug occurs when a CA NameConstraint starts with a leading `.`. \
This is a valid constrait value as it allows certifcates to be issued for subdomains of the constraint, but not the domain itself. \
i.e. a name constrait of `example.com` would allow certifcates for `www.example.com` and `example.com` whereas a name constraint of `.example.com` would only allow `www.example.com` (or any other subdomain) but not `example.com`

However Java will reject a certificate issued by a CA with such a name constraint with `java.security.cert.CertPathValidatorException: name constraints check failed`.

The `generate.sh` script creates two sets of CAs and certificates, one with a Name Constraint with a leading period, and one without, that are otherwise identical.
Running `java NameConstraintBug.java` wil attempt to load both sets of certificates and validate them but will fail for the certificates with the leading period.

There awas a similar bug previously raised in go for further reference: https://github.com/golang/go/issues/16347