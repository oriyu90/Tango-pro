#define AppVersion "2.1.0"
#ifndef SourceRoot
  #define SourceRoot "..\..\dist-windows\stage"
#endif

[Setup]
AppId={{B920A0EE-D55A-4B3C-A45F-3C1B1AF4B087}
AppName=Tango Pro
AppVersion={#AppVersion}
AppPublisher=Tango Pro
DefaultDirName={autopf}\Tango Pro
DefaultGroupName=Tango Pro
DisableProgramGroupPage=yes
OutputDir=..\..\dist-windows
OutputBaseFilename=Tango-Pro-{#AppVersion}-Windows-x64-Setup
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
Compression=lzma2
SolidCompression=yes
UninstallDisplayName=Tango Pro

[Files]
Source: "{#SourceRoot}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{autoprograms}\Tango Pro"; Filename: "{app}\TangoPro.exe"

[Registry]
Root: HKLM; Subkey: "Software\Classes\Applications\TangoPro.exe\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\TangoPro.exe"" ""%1"""; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\Classes\Applications\TangoPro.exe\SupportedTypes"; ValueType: string; ValueName: ".csv"; ValueData: ""; Flags: uninsdeletevalue

[Code]
var
  RemoveData: TNewCheckBox;

procedure InitializeUninstallProgressForm();
begin
  RemoveData := TNewCheckBox.Create(UninstallProgressForm);
  RemoveData.Parent := UninstallProgressForm;
  RemoveData.Caption := 'Tango Proの学習データも完全に削除しますか？';
  RemoveData.Checked := False;
  RemoveData.Left := ScaleX(8);
  RemoveData.Top := UninstallProgressForm.StatusLabel.Top + ScaleY(32);
  RemoveData.Width := UninstallProgressForm.ClientWidth - ScaleX(16);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: String;
begin
  if (CurUninstallStep = usPostUninstall) and Assigned(RemoveData) and RemoveData.Checked then begin
    DataDir := ExpandConstant('{localappdata}\TangoPro\data');
    DelTree(DataDir, True, True, True);
  end;
end;
