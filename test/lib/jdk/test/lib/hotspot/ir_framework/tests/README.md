# Framework internal tests
This folder contains tests which test the functionality of the framework. These should be run with JTreg and without additional VM and Javaopts flags whenever the framework is modified. 

These tests are not part of the normal tier testing as they only should be run when the framework is changed in any way.

Additional testing should be performed with the converted Valhalla tests (see [JDK-8263024](https://bugs.openjdk.java.net/browse/JDK-8263024)) to make sure a changeset is correct (these are part of the Valhalla CI).

