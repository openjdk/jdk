| Dialog | Control | Event | Argument | Condition | Ordering |
| --- | --- | --- | --- | --- | --- |
| InstallDirDlg | Back | NewDialog | WelcomeDlg | NOT Installed | 6 |
| InstallDirNotEmptyDlg | No | NewDialog | InstallDirDlg | 1 | 1 |
| InstallDirNotEmptyDlg | Yes | NewDialog | ShortcutPromptDlg | 1 | 1 |
| ShortcutPromptDlg | Back | NewDialog | InstallDirDlg | 1 | 1 |
| ShortcutPromptDlg | Cancel | SpawnDialog | CancelDlg | 1 | 1 |
| ShortcutPromptDlg | Next | NewDialog | VerifyReadyDlg | 1 | 1 |
| VerifyReadyDlg | Back | NewDialog | ShortcutPromptDlg | NOT Installed | 6 |
| WelcomeDlg | Next | NewDialog | InstallDirDlg | NOT Installed | 6 |
