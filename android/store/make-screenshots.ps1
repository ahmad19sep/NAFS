# Copies the chosen screen renders into store/screenshots, cropped from the top to Google Play's limit
# (the longer side may be at most twice the shorter). Run after: .\gradlew.bat :app:testDebugUnitTest
param([string]$Renders = "$PSScriptRoot\..\app\build\outputs\roborazzi", [string]$Out = "$PSScriptRoot\screenshots")
Add-Type -AssemblyName System.Drawing
$screens = '04_home', '20_tasks_today', '30_habits_today', '71_prayer_times_today', '80_coach_chat',
    '91_reports_period_weekly', '40_goals', '60_health_today'
foreach ($theme in 'dark', 'light') {
    New-Item -ItemType Directory -Force "$Out\$theme" | Out-Null
    $index = 1
    foreach ($name in $screens) {
        $source = Join-Path $Renders "${name}_$theme.png"
        if (-not (Test-Path $source)) { Write-Warning "Missing $source"; continue }
        $image = [System.Drawing.Image]::FromFile((Resolve-Path $source))
        try {
            $height = [Math]::Min($image.Height, 2 * $image.Width)
            $crop = New-Object System.Drawing.Bitmap($image.Width, $height)
            $graphics = [System.Drawing.Graphics]::FromImage($crop)
            $graphics.DrawImage($image, (New-Object System.Drawing.Rectangle(0, 0, $image.Width, $height)),
                (New-Object System.Drawing.Rectangle(0, 0, $image.Width, $height)), [System.Drawing.GraphicsUnit]::Pixel)
            $graphics.Dispose()
            $target = Join-Path "$Out\$theme" ('{0:D2}_{1}.png' -f $index, $name)
            $crop.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
            $crop.Dispose()
            Write-Output "$target $($image.Width)x$height"
        } finally { $image.Dispose() }
        $index++
    }
}
