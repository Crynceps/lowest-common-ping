# Starts RuneLite with the Lowest Common Ping test build, using the RuneLite already installed on this PC:
# its bundled Java and the RuneLite files it downloaded to %USERPROFILE%\.runelite\repository2, checked against
# RuneLite's published checksums. Nothing is installed or modified; RuneLite uses your normal settings folder.

$ErrorActionPreference = 'Stop'

$pluginJar = Join-Path $PSScriptRoot 'lowest-common-ping-test.jar'
$mainClass = 'com.lowestcommonping.LowestCommonPingPluginTest'
$bootstrapUrl = 'https://static.runelite.net/bootstrap.json'
$updateMessage = 'RuneLite needs to update first. Start RuneLite normally, wait for the login screen, close it, then run this again.'

function Stop-WithMessage([string]$message)
{
	Write-Host ''
	Write-Host $message -ForegroundColor Red
	Write-Host ''
	exit 1
}

if (-not (Test-Path -LiteralPath $pluginJar))
{
	Stop-WithMessage ('lowest-common-ping-test.jar is missing. Extract the whole zip file first (right-click it, ' +
		'Extract All), then run the starter from the extracted folder.')
}

# --- RuneLite's own Java ------------------------------------------------------------------------------------------

# where the RuneLite installer registered itself (the Jagex Launcher finds RuneLite the same way), then the defaults
$installFolders = @()
foreach ($key in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\RuneLite Launcher_is1',
	'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\RuneLite Launcher_is1',
	'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\RuneLite Launcher_is1'))
{
	$entry = Get-ItemProperty -LiteralPath $key -ErrorAction SilentlyContinue
	if ($entry -and $entry.InstallLocation)
	{
		$installFolders += $entry.InstallLocation
	}
}
foreach ($root in @($env:LOCALAPPDATA, ${env:ProgramFiles(x86)}, $env:ProgramFiles))
{
	if ($root)
	{
		$installFolders += Join-Path $root 'RuneLite'
	}
}

$java = $installFolders | ForEach-Object { Join-Path $_ 'jre\bin\java.exe' } |
	Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $java)
{
	Stop-WithMessage ('Could not find RuneLite on this PC. Install RuneLite from runelite.net, start it once, then run ' +
		'this again. If RuneLite is installed, send a screenshot of this window to whoever sent you this test build.')
}

$javaMajor = 11
$releaseFile = Join-Path (Split-Path (Split-Path $java)) 'release'
if (Test-Path -LiteralPath $releaseFile)
{
	$versionLine = Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
	if ($versionLine)
	{
		$javaMajor = [int]$versionLine.Matches[0].Groups[1].Value
	}
}

# --- RuneLite's files -------------------------------------------------------------------------------------------

$repository = Join-Path $env:USERPROFILE '.runelite\repository2'
if (-not (Test-Path -LiteralPath $repository))
{
	Stop-WithMessage 'RuneLite has not been started on this PC yet. Start RuneLite normally once, close it, then run this again.'
}

try
{
	[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
	$bootstrap = Invoke-RestMethod -Uri $bootstrapUrl -UseBasicParsing -TimeoutSec 30
}
catch
{
	Stop-WithMessage ("Could not reach RuneLite's website (static.runelite.net). Check your internet connection, " +
		'then run this again.')
}

# exactly the files the RuneLite launcher uses for the current version, each checked against RuneLite's checksum;
# files for other systems are not on this PC
$jars = @()
$missing = @()
foreach ($artifact in $bootstrap.artifacts)
{
	if ($artifact.name -notlike '*.jar')
	{
		continue
	}

	$path = Join-Path $repository $artifact.name
	$valid = (Test-Path -LiteralPath $path) -and
		(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -eq ([string]$artifact.hash).ToUpperInvariant()
	if ($valid)
	{
		$jars += $artifact.name
	}
	elseif (-not $artifact.platform)
	{
		$missing += $artifact.name
	}
}

if ($missing.Count -gt 0 -or -not ($jars | Where-Object { $_ -like 'injected-client-*' }))
{
	Stop-WithMessage ($updateMessage + ' (Missing or incomplete: ' + ($missing -join ', ') + ')')
}

# the JVM settings RuneLite's launcher uses
$jvmArguments = $null
if ($javaMajor -ge 17)
{
	$jvmArguments = if ($bootstrap.clientJvm17WindowsArguments) { $bootstrap.clientJvm17WindowsArguments }
		else { $bootstrap.clientJvm17Arguments }
}
else
{
	$jvmArguments = $bootstrap.clientJvm9Arguments
}
if (-not $jvmArguments)
{
	$jvmArguments = @('-XX:+DisableAttachMechanism', '-Xmx768m', '-Xss2m', '-XX:CompileThreshold=1500')
	if ($javaMajor -ge 17)
	{
		$jvmArguments += @('--add-opens=java.base/java.net=ALL-UNNAMED', '--add-opens=java.base/java.io=ALL-UNNAMED')
	}
}

# --- Start ------------------------------------------------------------------------------------------------------

# Java reads its command line in the PC's legacy code page, so the classpath is kept to plain characters by running
# from RuneLite's folder (RuneLite's file names are plain) and pointing to the plugin with a relative path
Set-Location -LiteralPath $repository
$pluginPath = Resolve-Path -LiteralPath $pluginJar -Relative
if ($pluginPath -match '[^\x20-\x7E]')
{
	Stop-WithMessage ('Move the LowestCommonPing-test folder to a folder whose name has only plain letters, for example ' +
		'C:\LowestCommonPing-test, then run the starter from there.')
}

$classpath = (@($pluginPath) + $jars) -join ';'
Write-Host 'Starting RuneLite with the Lowest Common Ping test build...'
Write-Host 'Keep this window open while you play, but do not click inside it. Closing it closes RuneLite.'
if (Test-Path -LiteralPath (Join-Path $env:USERPROFILE '.runelite\credentials.properties'))
{
	Write-Host ('Reminder: your Jagex login token is saved on this PC. When you are done testing, double-click ' +
		'"Jagex login - clean up".') -ForegroundColor Yellow
}

# -ea is required by RuneLite for loading a plugin this way; no developer mode
$ErrorActionPreference = 'Continue'
$arguments = @($jvmArguments) + @('-ea', '-cp', $classpath, $mainClass) + @($args)
& $java @arguments
if ($LASTEXITCODE -ne 0)
{
	Stop-WithMessage ('RuneLite closed with an error (code ' + $LASTEXITCODE + '). Send a screenshot of this window, or the ' +
		'file client.log from %USERPROFILE%\.runelite\logs, to whoever sent you this test build.')
}
