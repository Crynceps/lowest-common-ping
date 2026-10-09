# Helps a tester log in with a Jagex account in the test build, following RuneLite's official guide:
# https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
#   -Mode setup    copies the client argument to the clipboard, opens "RuneLite (configure)" and checks it was saved
#   -Mode cleanup  opens "RuneLite (configure)" to remove the argument, checks it, and deletes the saved login token

param([ValidateSet('setup', 'cleanup')][string]$Mode = 'setup')

$ErrorActionPreference = 'Stop'
$argument = '--insecure-write-credentials'
$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'

# where the RuneLite installer registered itself (the Jagex Launcher finds RuneLite the same way), then the defaults
$installFolders = @()
$launcherVersion = $null
foreach ($key in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\RuneLite Launcher_is1',
	'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\RuneLite Launcher_is1',
	'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\RuneLite Launcher_is1'))
{
	$entry = Get-ItemProperty -LiteralPath $key -ErrorAction SilentlyContinue
	if ($entry -and $entry.InstallLocation)
	{
		$installFolders += $entry.InstallLocation
		if (-not $launcherVersion -and $entry.DisplayVersion)
		{
			$launcherVersion = $entry.DisplayVersion
		}
	}
}
foreach ($root in @($env:LOCALAPPDATA, ${env:ProgramFiles(x86)}, $env:ProgramFiles))
{
	if ($root)
	{
		$installFolders += Join-Path $root 'RuneLite'
	}
}
$installFolder = $installFolders | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'RuneLite.exe') } |
	Select-Object -First 1

function Test-ArgumentSaved
{
	# the launcher saves the "Client arguments" box in settings.json next to RuneLite.exe
	$settings = Join-Path $installFolder 'settings.json'
	if (-not (Test-Path -LiteralPath $settings))
	{
		return $false
	}
	$json = Get-Content -LiteralPath $settings -Raw | ConvertFrom-Json
	return @($json.clientArguments) -contains $argument
}

Write-Host ''
if (-not $installFolder)
{
	Write-Host 'Could not find RuneLite on this PC. Open "RuneLite (configure)" from the Start menu yourself and follow' -ForegroundColor Yellow
	Write-Host 'the steps in HOW-TO.txt.' -ForegroundColor Yellow
	exit 1
}

try
{
	if ($launcherVersion -and [version]$launcherVersion -lt [version]'2.6.3')
	{
		Write-Host 'Your RuneLite is too old for this. Reinstall it from runelite.net first, then try again.' -ForegroundColor Yellow
		exit 1
	}
}
catch
{
	# unknown version format; carry on
}

$runelite = Join-Path $installFolder 'RuneLite.exe'
if ($Mode -eq 'setup')
{
	try
	{
		Set-Clipboard -Value $argument
		Write-Host 'This text is now on your clipboard:' -ForegroundColor Cyan
	}
	catch
	{
		Write-Host 'Copy this text (select it with the mouse and press Enter), or type it exactly:' -ForegroundColor Yellow
	}
	Write-Host "    $argument"
	Write-Host ''
	Write-Host 'A RuneLite settings window opens now:'
	Write-Host '  1. Click in the "Client arguments" box. If there is already text in it, click at the end of'
	Write-Host '     the last line and press Enter first (one setting per line).'
	Write-Host '  2. Press Ctrl+V, click Save, then close that window.'
	Start-Process -FilePath $runelite -ArgumentList '--configure'
	Write-Host ''
	Read-Host 'Press Enter here when you have saved and closed the RuneLite window' | Out-Null

	if (Test-ArgumentSaved)
	{
		Write-Host 'Saved.' -ForegroundColor Green
		Write-Host 'Now start RuneLite once through the Jagex Launcher as you normally do. When the RuneLite login'
		Write-Host 'screen appears, close it. Then double-click "Start Lowest Common Ping" again.'
	}
	else
	{
		Write-Host "The setting was not saved. Run ""Jagex login - set up"" again and make sure $argument" -ForegroundColor Red
		Write-Host 'is on its own line in the "Client arguments" box before you click Save.' -ForegroundColor Red
	}
}
else
{
	Write-Host 'A RuneLite settings window opens now:'
	Write-Host "  1. In the ""Client arguments"" box, delete the line $argument (leave any other lines)."
	Write-Host '  2. Click Save, then close that window.'
	Start-Process -FilePath $runelite -ArgumentList '--configure'
	Write-Host ''
	Read-Host 'Press Enter here when you have saved and closed the RuneLite window' | Out-Null

	$stillSet = Test-ArgumentSaved
	if (Test-Path -LiteralPath $credentials)
	{
		Remove-Item -LiteralPath $credentials
		Write-Host 'Deleted your saved login token (credentials.properties).' -ForegroundColor Green
	}
	else
	{
		Write-Host 'No saved login token found; nothing to delete.' -ForegroundColor Green
	}

	if ($stillSet)
	{
		Write-Host "But $argument is still in the ""Client arguments"" box, so RuneLite will save a new token the" -ForegroundColor Red
		Write-Host 'next time you play. Run "Jagex login - clean up" again and remove that line.' -ForegroundColor Red
	}
	else
	{
		Write-Host 'All cleaned up: your RuneLite is back to normal.' -ForegroundColor Green
	}
	Write-Host ''
	Write-Host 'If you think someone else got a copy of the token file, use "End sessions" in your account'
	Write-Host 'settings on runescape.com: that makes the token useless.'
}
Write-Host ''
