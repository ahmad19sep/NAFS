# Copies the chosen screen renders into store/screenshots, cropped from the top to exactly 9:16,
# the portrait aspect ratio Google Play requires for phone screenshots. Run after: .\gradlew.bat :app:testDebugUnitTest
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
            # A width divisible by 9 gives an exact 9:16 height; trim the spare columns evenly.
            $width = [int]([Math]::Floor($image.Width / 9) * 9)
            $height = [int]($width / 9 * 16)
            $left = [int][Math]::Floor(($image.Width - $width) / 2)
            $crop = New-Object System.Drawing.Bitmap($width, $height)
            $graphics = [System.Drawing.Graphics]::FromImage($crop)
            $graphics.DrawImage($image, (New-Object System.Drawing.Rectangle(0, 0, $width, $height)),
                (New-Object System.Drawing.Rectangle($left, 0, $width, $height)), [System.Drawing.GraphicsUnit]::Pixel)
            $graphics.Dispose()
            $target = Join-Path "$Out\$theme" ('{0:D2}_{1}.png' -f $index, $name)
            $crop.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
            $crop.Dispose()
            Write-Output "$target ${width}x$height"
        } finally { $image.Dispose() }
        $index++
    }
}
