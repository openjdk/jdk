| Option | Scope | With runtime installer | With predefined app image | Recognized in add launcher .property file | Merge
| --- | --- | :---: | :---: | :---: | :---: |
| --about-url | native-bundle | x | x |  | USE_LAST |
| --add-launcher | bundle |  |  |  | CONCATENATE |
| --add-modules | bundle |  |  |  | CONCATENATE |
| --app-content | bundle |  |  |  | CONCATENATE |
| --app-image | mac-sign, native-bundle |  | x |  | USE_LAST |
| --app-version | bundle | x | x |  | USE_LAST |
| --arguments | bundle |  |  | x | CONCATENATE |
| --copyright | bundle | x | x |  | USE_LAST |
| --description | bundle | x | x | x | USE_LAST |
| --dest, -d | bundle | x | x |  | USE_LAST |
| --file-associations | bundle |  | x |  | CONCATENATE |
| --help, -h, -? | all | x | x |  | USE_LAST |
| --icon | bundle | x | x | x | USE_LAST |
| --input, -i | bundle |  |  |  | USE_LAST |
| --install-dir | bundle | x | x |  | USE_LAST |
| --java-options | bundle |  |  | x | CONCATENATE |
| --jlink-options | bundle |  |  |  | CONCATENATE |
| --launcher-as-service | native-bundle | x | x | x | USE_LAST |
| --license-file | bundle | x | x |  | USE_LAST |
| --linux-app-category | linux-deb, linux-rpm | x | x |  | USE_LAST |
| --linux-app-release | linux-deb, linux-rpm | x | x |  | USE_LAST |
| --linux-deb-maintainer | linux-deb | x | x |  | USE_LAST |
| --linux-menu-group | linux-deb, linux-rpm | x | x |  | USE_LAST |
| --linux-package-deps | linux-deb, linux-rpm | x | x |  | USE_LAST |
| --linux-package-name | linux-deb, linux-rpm | x | x |  | USE_LAST |
| --linux-rpm-license-type | linux-rpm | x | x |  | USE_LAST |
| --linux-shortcut | linux-deb, linux-rpm | x | x | x | USE_LAST |
| --mac-app-category | mac-bundle | x | x |  | USE_LAST |
| --mac-app-image-sign-identity | mac | x | x |  | USE_LAST |
| --mac-app-store | mac-bundle | x | x |  | USE_LAST |
| --mac-dmg-content | mac-dmg | x | x |  | CONCATENATE |
| --mac-entitlements | mac | x | x |  | USE_LAST |
| --mac-installer-sign-identity | mac-pkg | x | x |  | USE_LAST |
| --mac-package-identifier | mac-bundle | x | x |  | USE_LAST |
| --mac-package-name | mac-bundle | x | x |  | USE_LAST |
| --mac-package-signing-prefix | mac | x | x |  | USE_LAST |
| --mac-sign, -s | mac | x | x |  | USE_LAST |
| --mac-signing-key-user-name | mac | x | x |  | USE_LAST |
| --mac-signing-keychain | mac | x | x |  | USE_LAST |
| --main-class | bundle |  |  | x | USE_LAST |
| --main-jar | bundle |  |  | x | USE_LAST |
| --module, -m | bundle |  |  | x | USE_LAST |
| --module-path, -p | bundle |  |  |  | CONCATENATE |
| --name, -n | bundle | x | x |  | USE_LAST |
| --resource-dir | all | x | x |  | USE_LAST |
| --runtime-image | bundle | x |  |  | USE_LAST |
| --temp | bundle | x | x |  | USE_LAST |
| --type, -t | all | x | x |  | USE_LAST |
| --vendor | bundle | x | x |  | USE_LAST |
| --verbose | all | x | x |  | USE_LAST |
| --version | all | x | x |  | USE_LAST |
| --win-console | win |  |  | x | USE_LAST |
| --win-dir-chooser | win-exe, win-msi | x | x |  | USE_LAST |
| --win-help-url | win-exe, win-msi | x | x |  | USE_LAST |
| --win-menu | win-exe, win-msi | x | x | x | USE_LAST |
| --win-menu-group | win-exe, win-msi | x | x |  | USE_LAST |
| --win-per-user-install | win-exe, win-msi | x | x |  | USE_LAST |
| --win-shortcut | win-exe, win-msi | x | x | x | USE_LAST |
| --win-shortcut-prompt | win-exe, win-msi | x | x |  | USE_LAST |
| --win-update-url | win-exe, win-msi | x | x |  | USE_LAST |
| --win-upgrade-uuid | win-exe, win-msi | x | x |  | USE_LAST |
