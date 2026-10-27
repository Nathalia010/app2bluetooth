$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$source = [System.Drawing.Image]::FromFile((Join-Path $root 'assets/logo-original.png'))
try {
    $side = [Math]::Min($source.Width, $source.Height)
    $crop = [System.Drawing.Rectangle]::new(($source.Width - $side) / 2, ($source.Height - $side) / 2, $side, $side)
    $sizes = @(16, 24, 32, 48, 64, 128, 256, 512)
    $images = @{}
    foreach ($size in $sizes) {
        $bitmap = [System.Drawing.Bitmap]::new($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, $size, $size), $crop, [System.Drawing.GraphicsUnit]::Pixel)
        $stream = [System.IO.MemoryStream]::new()
        $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
        $images[$size] = $stream.ToArray()
        $stream.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
    }
    [System.IO.File]::WriteAllBytes((Join-Path $root 'app/src/main/res/drawable-nodpi/launcher_logo.png'), $images[512])
    [System.IO.File]::WriteAllBytes((Join-Path $root 'publication/play-store-icon.png'), $images[512])
    $iconSizes = @(16, 24, 32, 48, 64, 128, 256)
    $file = [System.IO.File]::Create((Join-Path $root 'desktop/assets/juntos.ico'))
    $writer = [System.IO.BinaryWriter]::new($file)
    try {
        $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$iconSizes.Count)
        $offset = 6 + 16 * $iconSizes.Count
        foreach ($size in $iconSizes) {
            $dimension = if ($size -eq 256) { 0 } else { $size }
            $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
            $writer.Write([byte]0); $writer.Write([byte]0)
            $writer.Write([uint16]1); $writer.Write([uint16]32)
            $writer.Write([uint32]$images[$size].Length); $writer.Write([uint32]$offset)
            $offset += $images[$size].Length
        }
        foreach ($size in $iconSizes) { $writer.Write([byte[]]$images[$size]) }
    } finally { $writer.Dispose(); $file.Dispose() }
} finally { $source.Dispose() }
Write-Output 'Iconos Android, Windows y Google Play generados desde el logo original.'
