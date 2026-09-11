param(
    [int]$TimeoutSeconds = 90,
    [switch]$KeepDiagnosticConfig,
    [string[]]$OnlyProject
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$minecraftRoot = "D:\Minecraft"
$reportDirectory = Join-Path $repoRoot "diagnostics-reports"
$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $reportDirectory "bubble-diagnostics-$runStamp.json"

$targets = @(
    [pscustomobject]@{
        Loader = "Forge 1.19.2"
        Project = "forge-1.19.2"
        Instance = "D:\Minecraft\versions\1.19.2-Forge_43.5.2 MOD测试"
        Java = "C:\Program Files\Java\jdk-17\bin\java.exe"
        Save = "新的世界"
        QuickPlay = $false
        Fabric = $false
    },
    [pscustomobject]@{
        Loader = "Fabric 1.20.1"
        Project = "fabric-1.20.1"
        Instance = "D:\Minecraft\versions\1.20.1-Fabric 0.19.5 MOD测试"
        Java = "C:\Program Files\Java\jdk-21\bin\java.exe"
        Save = "新的世界"
        QuickPlay = $true
        Fabric = $true
    },
    [pscustomobject]@{
        Loader = "Fabric 1.21.1"
        Project = "fabric-1.21.1"
        Instance = "D:\Minecraft\versions\1.21.1-Fabric 0.19.5 MOD测试"
        Java = "C:\Program Files\Java\jdk-21\bin\java.exe"
        Save = "新的世界"
        QuickPlay = $true
        Fabric = $true
    },
    [pscustomobject]@{
        Loader = "Forge 1.20.1"
        Project = "forge-1.20.1"
        Instance = "D:\Minecraft\versions\Last One"
        Java = "C:\Program Files\Java\jdk-17\bin\java.exe"
        Save = "新的世界 (8)"
        QuickPlay = $true
        Fabric = $false
    },
    [pscustomobject]@{
        Loader = "NeoForge 1.21.1"
        Project = "neoforge-1.21.1"
        Instance = "D:\Minecraft\versions\aaa"
        Java = "C:\Program Files\Java\jdk-21\bin\java.exe"
        Save = "新的世界 (2)"
        QuickPlay = $true
        Fabric = $false
    },
    [pscustomobject]@{
        Loader = "NeoForge 1.26.1.2"
        Project = "neoforge-1.26.1.2"
        Instance = "D:\Minecraft\versions\26.1.2-NeoForge_26.1.2.107 MOD测试"
        Java = "C:\Program Files\Java\jdk-25.0.4.1\bin\java.exe"
        Save = "新的世界"
        QuickPlay = $true
        Fabric = $false
    }
)

if ($OnlyProject.Count -gt 0) {
    $requestedProjects = @($OnlyProject | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $targets = @($targets | Where-Object { $requestedProjects -contains $_.Project })
    if ($targets.Count -eq 0) {
        throw "No matching project found. Valid projects: forge-1.19.2, forge-1.20.1, fabric-1.20.1, fabric-1.21.1, neoforge-1.21.1, neoforge-1.26.1.2"
    }
}

function Get-VersionJson([string]$instance) {
    $candidate = Get-ChildItem -LiteralPath $instance -Filter "*.json" -File |
        Where-Object { $_.Name -notmatch "^(usercache|usernamecache|emi|ops|patchouli)" } |
        Select-Object -First 1
    if (-not $candidate) {
        throw "No version JSON found in $instance"
    }
    return Get-Content -LiteralPath $candidate.FullName -Raw | ConvertFrom-Json
}

function Expand-Value($value) {
    if ($null -eq $value) {
        return @()
    }
    if ($value -is [System.Array]) {
        $result = @()
        foreach ($entry in $value) {
            $result += Expand-Value $entry
        }
        return $result
    }
    return @([string]$value)
}

function Rule-AllowsWindows($entry) {
    if ($entry.PSObject.Properties.Name -notcontains "rules") {
        return $true
    }
    foreach ($rule in @($entry.rules)) {
        if ($rule.PSObject.Properties.Name -contains "os" -and
                $rule.os.PSObject.Properties.Name -contains "name" -and
                $rule.os.name -ne "windows") {
            return $false
        }
        if ($rule.PSObject.Properties.Name -contains "features") {
            return $false
        }
        if ($rule.PSObject.Properties.Name -contains "action" -and $rule.action -eq "disallow") {
            return $false
        }
    }
    return $true
}

function Expand-LaunchItem($item) {
    if ($item -is [string]) {
        return @([string]$item)
    }
    if (-not (Rule-AllowsWindows $item)) {
        return @()
    }
    return Expand-Value $item.value
}

function Replace-Placeholders([string]$value, $target, $version, [string]$classpath, [string]$natives) {
    $versionId = [string]$version.PSObject.Properties['id'].Value
    $result = $value
    $result = $result.Replace('${natives_directory}', $natives)
    $result = $result.Replace('${library_directory}', $minecraftRoot + "\libraries")
    $result = $result.Replace('${classpath_separator}', ';')
    $result = $result.Replace('${classpath}', $classpath)
    $result = $result.Replace('${version_name}', $versionId)
    $result = $result.Replace('${launcher_name}', 'BubbleAnywayDiagnostics')
    $result = $result.Replace('${launcher_version}', '1')
    return $result
}

function Get-ArtifactPath($library) {
    if ($library.PSObject.Properties.Name -contains "downloads" -and
            $library.downloads.PSObject.Properties.Name -contains "artifact" -and
            $library.downloads.artifact.PSObject.Properties.Name -contains "path") {
        return [string]$library.downloads.artifact.path
    }
    if ($library.PSObject.Properties.Name -notcontains "name") {
        return $null
    }
    $parts = ([string]$library.name).Split(':')
    if ($parts.Count -lt 3) {
        return $null
    }
    $group = $parts[0].Replace('.', '\')
    $artifact = $parts[1]
    $version = $parts[2]
    return "$group\$artifact\$version\$artifact-$version.jar"
}

function Set-DiagnosticsConfig($target) {
    $path = Join-Path $target.Instance "config\bubble_anyway\client.toml"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing client config: $path"
    }
    $original = Get-Content -LiteralPath $path -Raw
    if ($target.Fabric) {
        $updated = $original
        if ($updated -match '(?m)^debug\.diagnosticsEnabled\s*=') {
            $updated = [regex]::Replace($updated, '(?m)^debug\.diagnosticsEnabled\s*=.*$', 'debug.diagnosticsEnabled = true')
        } else {
            $updated += "`ndebug.diagnosticsEnabled = true`n"
        }
        if ($updated -match '(?m)^debug\.showTestBubble\s*=') {
            $updated = [regex]::Replace($updated, '(?m)^debug\.showTestBubble\s*=.*$', 'debug.showTestBubble = true')
        } else {
            $updated += "debug.showTestBubble = true`n"
        }
    } else {
        $updated = $original
        if ($updated -match '(?m)^\[debug\]') {
            $updated = [regex]::Replace($updated, '(?ms)(^\[debug\].*?)(?=^\[|\z)', {
                    param($match)
                    $section = $match.Groups[1].Value
                    if ($section -match '(?m)^\s*diagnosticsEnabled\s*=') {
                        $section = [regex]::Replace($section, '(?m)^\s*diagnosticsEnabled\s*=.*$', "`tdiagnosticsEnabled = true")
                    } else {
                        $section += "`tdiagnosticsEnabled = true`n"
                    }
                    if ($section -match '(?m)^\s*showTestBubble\s*=') {
                        $section = [regex]::Replace($section, '(?m)^\s*showTestBubble\s*=.*$', "`tshowTestBubble = true")
                    } else {
                        $section += "`tshowTestBubble = true`n"
                    }
                    return $section
                })
        } else {
            $updated += "`n[debug]`n`tdiagnosticsEnabled = true`n`tshowTestBubble = true`n"
        }
    }
    [IO.File]::WriteAllText($path, $updated, [Text.UTF8Encoding]::new($false))
    return [pscustomobject]@{ Path = $path; Original = $original }
}

function Restore-Config($backup) {
    [IO.File]::WriteAllText($backup.Path, $backup.Original, [Text.UTF8Encoding]::new($false))
}

function Get-LaunchArguments($target, $version) {
    $libraryRoot = Join-Path $minecraftRoot "libraries"
    $classpathEntries = @()
    $classpathSeen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $versionId = [string]$version.PSObject.Properties['id'].Value
    if ([string]::IsNullOrWhiteSpace($versionId)) {
        throw "Version JSON in $($target.Instance) does not contain an id"
    }
    $versionJar = Join-Path $target.Instance ($versionId + ".jar")
    if ((Test-Path -LiteralPath $versionJar) -and $classpathSeen.Add($versionJar)) {
        $classpathEntries += $versionJar
    }
    foreach ($library in @($version.libraries)) {
        $artifactPath = Get-ArtifactPath $library
        if ([string]::IsNullOrWhiteSpace($artifactPath)) {
            continue
        }
        $path = Join-Path $libraryRoot ($artifactPath -replace '/', '\')
        if ((Test-Path -LiteralPath $path) -and $classpathSeen.Add($path)) {
            $classpathEntries += $path
        }
    }
    $classpath = $classpathEntries -join ';'
    $natives = Join-Path $target.Instance ($versionId + "-natives")

    $jvmArguments = @()
    foreach ($item in @($version.arguments.jvm)) {
        foreach ($value in Expand-LaunchItem $item) {
            if ($value -eq '-XstartOnFirstThread') {
                continue
            }
            $jvmArguments += Replace-Placeholders $value $target $version $classpath $natives
        }
    }

    $gameArguments = @(
        '--username', 'BubbleDiag',
        '--version', $versionId,
        '--gameDir', $target.Instance,
        '--assetsDir', (Join-Path $minecraftRoot 'assets'),
        '--assetIndex', [string]$version.assets,
        '--uuid', '00000000-0000-0000-0000-000000000001',
        '--accessToken', '0',
        '--clientId', '0',
        '--xuid', '0',
        '--userType', 'legacy',
        '--versionType', 'release'
    )

    $simpleGameValues = @()
    foreach ($item in @($version.arguments.game)) {
        $simpleGameValues += Expand-LaunchItem $item
    }
    $specialArgumentIndexes = @()
    foreach ($specialName in @('--launchTarget', '--fml.neoForgeVersion', '--fml.forgeVersion')) {
        $index = [Array]::IndexOf($simpleGameValues, $specialName)
        if ($index -ge 0) {
            $specialArgumentIndexes += $index
        }
    }
    if ($specialArgumentIndexes.Count -gt 0) {
        $specialArgumentStart = ($specialArgumentIndexes | Measure-Object -Minimum).Minimum
        $gameArguments += $simpleGameValues[$specialArgumentStart..($simpleGameValues.Count - 1)]
    }
    if ($target.QuickPlay) {
        $gameArguments += '--quickPlaySingleplayer'
        $gameArguments += $target.Save
    }

    return [pscustomobject]@{
        Jvm = $jvmArguments
        Main = [string]$version.mainClass
        Game = $gameArguments
        ClasspathEntries = $classpathEntries.Count
    }
}

function Start-MinecraftTest($target, $version) {
    $launch = Get-LaunchArguments $target $version
    $allArguments = @($launch.Jvm + $launch.Main + $launch.Game)
    $debugFile = Join-Path $reportDirectory (($target.Project + "-launch.txt") -replace '[^A-Za-z0-9._-]', '_')
    $allArguments | ForEach-Object { "[$_]" } | Set-Content -LiteralPath $debugFile -Encoding UTF8
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $target.Java
    $info.WorkingDirectory = $target.Instance
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $false
    foreach ($argument in $allArguments) {
        [void]$info.ArgumentList.Add([string]$argument)
    }
    $process = [Diagnostics.Process]::Start($info)
    return [pscustomobject]@{ Process = $process; ClasspathEntries = $launch.ClasspathEntries }
}

function Stop-MinecraftTest($process) {
    if ($null -eq $process -or $process.HasExited) {
        return
    }
    try {
        [void]$process.CloseMainWindow()
        if (-not $process.WaitForExit(5000)) {
            $process.Kill($true)
            $process.WaitForExit()
        }
    } catch {
        if (-not $process.HasExited) {
            $process.Kill($true)
        }
    }
}

New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$backups = @{}
$running = @()
$results = @()

try {
    foreach ($target in $targets) {
        Write-Host "=== Testing $($target.Loader) ==="
        $sourceJar = Get-ChildItem (Join-Path $repoRoot ($target.Project + "\build\libs")) -Filter "*1.1.0*.jar" -File |
            Where-Object { $_.Name -notlike '*sources*' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if (-not $sourceJar) {
            throw "Build JAR not found for $($target.Project)"
        }

        $targetMod = Get-ChildItem (Join-Path $target.Instance 'mods') -Filter 'bubble_anyway-*.jar' -File |
            Select-Object -First 1
        if (-not $targetMod) {
            throw "Installed Bubble Anyway JAR not found in $($target.Instance)\mods"
        }
        Copy-Item -LiteralPath $sourceJar.FullName -Destination $targetMod.FullName -Force

        $backup = Set-DiagnosticsConfig $target
        $backups[$backup.Path] = $backup
        $version = Get-VersionJson $target.Instance
        $logPath = Join-Path $target.Instance 'logs\latest.log'
        $initialDiagnosticLines = @()
        if (Test-Path -LiteralPath $logPath) {
            $initialDiagnosticLines = @(Get-Content -LiteralPath $logPath -ErrorAction SilentlyContinue |
                Where-Object { $_ -match 'Bubble Anyway diagnostics \[.*\] session=' })
        }
        $launchResult = $null
        $status = 'NOT_RUN'
        $message = ''
        try {
            $launchResult = Start-MinecraftTest $target $version
            $running += $launchResult.Process
            $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
            $reportSeen = $false
            $startupSeen = $false
            $text = ''
            $capturePath = Join-Path $reportDirectory ("$($target.Project)-captured-$runStamp.log")
            while ((Get-Date) -lt $deadline) {
                if (Test-Path -LiteralPath $logPath) {
                    $text = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
                    $newReportLine = @($text -split "`r?`n" |
                        Where-Object { $_ -match [regex]::Escape("Bubble Anyway diagnostics [$($target.Loader)] session=") -and
                            $initialDiagnosticLines -notcontains $_ } | Select-Object -First 1)
                    if ($newReportLine.Count -gt 0) {
                        $reportSeen = $true
                        [IO.File]::WriteAllText($capturePath, $text, [Text.UTF8Encoding]::new($false))
                        break
                    }
                    if ($text -match 'Bubble Anyway diagnostics failed') {
                        break
                    }
                    if (-not $target.QuickPlay -and $text -match 'Setting user:|Backend library: LWJGL') {
                        $startupSeen = $true
                        break
                    }
                }
                if ($launchResult.Process.HasExited) {
                    break
                }
                Start-Sleep -Seconds 2
            }
            if (-not (Test-Path -LiteralPath $capturePath)) {
                [IO.File]::WriteAllText($capturePath, $text, [Text.UTF8Encoding]::new($false))
            }
            $processExited = $launchResult.Process.HasExited
            if ($reportSeen) {
                $status = 'PASS'
                $message = 'Diagnostics report observed in latest.log'
            } elseif ($text -match 'Bubble Anyway diagnostics failed') {
                $status = 'FAIL'
                $message = 'Diagnostics reported an exception'
            } elseif ($processExited -and $target.QuickPlay) {
                $status = 'STARTUP_FAIL'
                $message = 'Client exited before the diagnostics report was observed'
            } elseif (-not $target.QuickPlay -and $startupSeen) {
                $status = 'STARTUP_ONLY'
                $message = '1.19.2 has no Quick Play argument; client startup was tested, but no world report was expected'
            } elseif (-not $target.QuickPlay) {
                $status = 'STARTUP_FAIL'
                $message = 'Client did not reach the startup marker before timeout'
            } else {
                $status = 'TIMEOUT'
                $message = 'No diagnostics report observed before timeout'
            }
            if ($text -match 'MixinApplyError|InvalidMixin|NoClassDefFoundError|Could not find required mod|Exception in thread "main"') {
                $status = 'FAIL'
                $message += '; fatal startup marker found in latest.log'
            }
            $results += [pscustomobject]@{
                Loader = $target.Loader
                Project = $target.Project
                Instance = $target.Instance
                Status = $status
                Message = $message
                ClasspathEntries = $launchResult.ClasspathEntries
                ProcessExited = $processExited
                LogPath = $logPath
                CapturePath = $capturePath
            }
        } finally {
            if ($launchResult) {
                Stop-MinecraftTest $launchResult.Process
                $running = @($running | Where-Object { $_.Id -ne $launchResult.Process.Id })
            }
        }
    }
} finally {
    foreach ($process in @($running)) {
        Stop-MinecraftTest $process
    }
    if (-not $KeepDiagnosticConfig) {
        foreach ($backup in $backups.Values) {
            Restore-Config $backup
        }
    }
}

$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $reportPath -Encoding UTF8
$results | Format-Table -AutoSize
Write-Host "Report: $reportPath"
