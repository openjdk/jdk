| Dialog | Control | Event | Argument | Condition | Ordering |
| --- | --- | --- | --- | --- | --- |
| InstallDirDlg | Back | NewDialog | WelcomeDlg | NOT Installed | 6 |
| InstallDirNotEmptyDlg | No | NewDialog | InstallDirDlg | 1 | 1 |
| InstallDirNotEmptyDlg | Yes | NewDialog | VerifyReadyDlg | 1 | 1 |
| WelcomeDlg | Next | NewDialog | InstallDirDlg | NOT Installed | 6 |
