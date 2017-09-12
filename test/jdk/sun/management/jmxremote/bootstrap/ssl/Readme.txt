The trustore and keystore are to be generated as follows:

1. keytool -genkey -alias duke -keystore keystore -validity 36500
- use password 'password' for the keystore and key passwords
- leave all values at default
- the certificate validity will be 100 years (should be enough for now)
2. keytool -export -keystore keystore -alias duke -file duke.crt
3. keytool -import -keystore truststore -alias duke -file duke.crt
- use password 'trustword' for the keystore and key passwords
- leave all values at default

