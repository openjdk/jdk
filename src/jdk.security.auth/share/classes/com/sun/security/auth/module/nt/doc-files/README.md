The classes in this package is generated with
```
jextract @atfile
```

After the generation, modify each class:
- Add `@SuppressWarnings("restricted")`
- Add Copyright information
- Remove the extra newline at the end

---++ Add GetLastError Function to Extracted Methods
Run the following command to generate alternative methods with `CapturableState` support.
```
java AddGLE.java ../my_win_h.java OpenThreadToken OpenProcessToken ````GetTokenInformation LookupAccountSidA LookupAccountNameA DuplicateToken
```
Paste the output into `NTSystem.java`.
