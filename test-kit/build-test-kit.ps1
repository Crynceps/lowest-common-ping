# Builds build\test-kit\LowestCommonPing-test.zip: the plugin plus a starter that runs it with the RuneLite a tester
# already has installed, for testing before the plugin is on the Plugin Hub. The zip contains only this plugin's own
# code; RuneLite itself comes from the tester's installation.
#
# Usage, from the repository root (JAVA_HOME must point to a JDK 11-21):
#   powershell -ExecutionPolicy Bypass -File test-kit\build-test-kit.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path $PSScriptRoot
$kitName = 'LowestCommonPing-test'
$output = Join-Path $root 'build\test-kit'

Push-Location $root
try
{
	# no daemon: a daemon started here would keep this script's output open long after the build
	& .\gradlew.bat build --console=plain --no-daemon
	if ($LASTEXITCODE -ne 0)
	{
		throw 'Gradle build failed'
	}
}
finally
{
	Pop-Location
}

# zip entries must use forward slashes, which Compress-Archive on Windows PowerShell does not guarantee
function Add-Entry([System.IO.Compression.ZipArchive]$zip, [string]$name, [byte[]]$bytes)
{
	$entry = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
	$stream = $entry.Open()
	try
	{
		$stream.Write($bytes, 0, $bytes.Length)
	}
	finally
	{
		$stream.Dispose()
	}
}

function Add-Directory([System.IO.Compression.ZipArchive]$zip, [string]$directory, [string]$filter)
{
	foreach ($file in Get-ChildItem -LiteralPath $directory -Recurse -File -Filter $filter)
	{
		$name = $file.FullName.Substring($directory.Length + 1).Replace('\', '/')
		Add-Entry $zip $name ([System.IO.File]::ReadAllBytes($file.FullName))
	}
}

New-Item -ItemType Directory -Force $output | Out-Null
$jarPath = Join-Path $output 'lowest-common-ping-test.jar'
$zipPath = Join-Path $output "$kitName.zip"
foreach ($path in @($jarPath, $zipPath))
{
	if (Test-Path -LiteralPath $path)
	{
		Remove-Item -LiteralPath $path
	}
}

# the plugin, its resources and the small launcher class from the tests
$jar = [System.IO.Compression.ZipFile]::Open($jarPath, [System.IO.Compression.ZipArchiveMode]::Create)
try
{
	Add-Entry $jar 'META-INF/MANIFEST.MF' ([System.Text.Encoding]::ASCII.GetBytes("Manifest-Version: 1.0`r`n`r`n"))
	Add-Directory $jar (Join-Path $root 'build\classes\java\main') '*.class'
	Add-Directory $jar (Join-Path $root 'build\resources\main') '*'
	$launcher = Join-Path $root 'build\classes\java\test\com\lowestcommonping\LowestCommonPingPluginTest.class'
	Add-Entry $jar 'com/lowestcommonping/LowestCommonPingPluginTest.class' ([System.IO.File]::ReadAllBytes($launcher))
}
finally
{
	$jar.Dispose()
}

# the kit must never contain RuneLite or Jagex code, only this plugin's own classes
$check = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try
{
	$foreign = @($check.Entries | Where-Object { $_.FullName -notlike 'com/lowestcommonping/*' -and $_.FullName -ne 'META-INF/MANIFEST.MF' })
	if ($foreign.Count -gt 0)
	{
		throw ('Unexpected files in the kit jar: ' + (($foreign | ForEach-Object { $_.FullName }) -join ', '))
	}
}
finally
{
	$check.Dispose()
}

# the kit; batch files need Windows line endings
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try
{
	Add-Entry $zip "$kitName/lowest-common-ping-test.jar" ([System.IO.File]::ReadAllBytes($jarPath))
	foreach ($name in @('HOW-TO.txt', 'Start Lowest Common Ping.bat', 'start.ps1', 'Jagex login - set up.bat',
		'Jagex login - clean up.bat', 'jagex-login.ps1', 'NOTICE.txt'))
	{
		$text = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $name))
		if ($text -match '[^\x00-\x7F]')
		{
			# Windows PowerShell 5.1 and cmd read these files in legacy code pages and would misread other characters
			throw "$name contains non-ASCII characters"
		}
		$text = ($text -replace "`r`n", "`n") -replace "`n", "`r`n"
		Add-Entry $zip "$kitName/$name" ([System.Text.Encoding]::UTF8.GetBytes($text))
	}
}
finally
{
	$zip.Dispose()
}

Write-Host "Built $zipPath"
Write-Host ('SHA-256: ' + (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash)
