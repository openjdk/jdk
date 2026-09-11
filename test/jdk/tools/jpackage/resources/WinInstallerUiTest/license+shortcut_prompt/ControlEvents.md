| Dialog | Control | Event | Argument | Condition | Ordering |
| --- | --- | --- | --- | --- | --- |
| LicenseAgreementDlg | Next | NewDialog | ShortcutPromptDlg | LicenseAccepted = "1" | 6 |
| ShortcutPromptDlg | Back | NewDialog | LicenseAgreementDlg | 1 | 1 |
| ShortcutPromptDlg | Cancel | SpawnDialog | CancelDlg | 1 | 1 |
| ShortcutPromptDlg | Next | NewDialog | VerifyReadyDlg | 1 | 1 |
| VerifyReadyDlg | Back | NewDialog | ShortcutPromptDlg | NOT Installed | 6 |
