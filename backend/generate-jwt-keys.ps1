# Génère une paire de clés RSA 2048 et met à jour backend/.env avec les variables JWT
# Usage : .\generate-jwt-keys.ps1

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $scriptDir '.env'

if (-not (Test-Path $envFile)) {
    Write-Error "Fichier .env introuvable dans $scriptDir"
    exit 1
}

function Encode-Asn1Length($length) {
    if ($length -lt 0x80) {
        return [byte[]] @($length)
    }

    $bytes = @()
    while ($length -gt 0) {
        $bytes = ,([byte]($length -band 0xFF)) + $bytes
        $length = $length -shr 8
    }
    return ,([byte](0x80 -bor $bytes.Length)) + $bytes
}

function Encode-Asn1Integer($bytes) {
    $bytes = $bytes.TrimStart([byte]0)
    if ($bytes.Length -eq 0) { $bytes = [byte[]] @(0) }
    if ($bytes[0] -band 0x80) { $bytes = ,0 + $bytes }
    $len = Encode-Asn1Length($bytes.Length)
    return ,0x02 + $len + $bytes
}

function Encode-Asn1Sequence($content) {
    $len = Encode-Asn1Length($content.Length)
    return ,0x30 + $len + $content
}

function Encode-Asn1Oid($oid) {
    $parts = $oid.Split('.') | ForEach-Object { [int]$_ }
    $first = ($parts[0] * 40) + $parts[1]
    $body = [byte[]] @($first)
    for ($i = 2; $i -lt $parts.Length; $i++) {
        $value = $parts[$i]
        $encoded = @()
        do {
            $encoded = ,([byte](0x80 -bor ($value -band 0x7F))) + $encoded
            $value = $value -shr 7
        } while ($value -gt 0)
        $encoded[-1] = [byte]($encoded[-1] -band 0x7F)
        $body += $encoded
    }
    $len = Encode-Asn1Length($body.Length)
    return ,0x06 + $len + $body
}

function Pemify($bytes, $header) {
    $base64 = [Convert]::ToBase64String($bytes)
    $lines = $base64 -split '(.{64})' | Where-Object { $_ -ne '' }
    return "-----BEGIN $header-----`n$($lines -join "`n")`n-----END $header-----"
}

function Export-RsaPrivateKeyPem($rsa) {
    $p = $rsa.ExportParameters($true)
    $seq = Encode-Asn1Sequence(
        (Encode-Asn1Integer([byte]0)) +
        (Encode-Asn1Integer($p.Modulus)) +
        (Encode-Asn1Integer($p.Exponent)) +
        (Encode-Asn1Integer($p.D)) +
        (Encode-Asn1Integer($p.P)) +
        (Encode-Asn1Integer($p.Q)) +
        (Encode-Asn1Integer($p.DP)) +
        (Encode-Asn1Integer($p.DQ)) +
        (Encode-Asn1Integer($p.InverseQ))
    )
    return Pemify($seq, 'RSA PRIVATE KEY')
}

function Export-RsaPublicKeyPem($rsa) {
    $p = $rsa.ExportParameters($false)
    $rsaSeq = Encode-Asn1Sequence(
        (Encode-Asn1Integer($p.Modulus)) +
        (Encode-Asn1Integer($p.Exponent))
    )
    $bitString = ,0x03 + (Encode-Asn1Length($rsaSeq.Length + 1)) + ,0x00 + $rsaSeq
    $algSeq = Encode-Asn1Sequence(
        (Encode-Asn1Oid('1.2.840.113549.1.1.1')) +
        (,0x05 + (Encode-Asn1Length(0)))
    )
    $pubSeq = Encode-Asn1Sequence($algSeq + $bitString)
    return Pemify($pubSeq, 'PUBLIC KEY')
}

function Escape-Newlines($text) {
    return $text -replace '\\', '\\\\' -replace "`n", '\\n'
}

function Set-EnvValue($content, $key, $value) {
    $pattern = "(?m)^[ \t]*($([Regex]::Escape($key))[ \t]*=).*"
    if ($content -match $pattern) {
        return [Regex]::Replace($content, $pattern, { param($m) "$($m.Groups[1].Value)$value" })
    }
    else {
        if ($content.TrimEnd() -eq '') {
            return "$key=$value`n"
        }
        return if ($content.TrimEnd().EndsWith("`n")) { "$content$key=$value`n" } else { "$content`n$key=$value`n" }
    }
}

$privatePem = $null
$publicPem = $null

$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if ($openssl) {
    $tmpDir = Join-Path $scriptDir 'tmp-jwt-keys'
    if (Test-Path $tmpDir) { Remove-Item $tmpDir -Recurse -Force }
    New-Item -ItemType Directory -Path $tmpDir | Out-Null
    $privateFile = Join-Path $tmpDir 'jwt_private.pem'
    $publicFile = Join-Path $tmpDir 'jwt_public.pem'

    & $openssl.Source genrsa -out $privateFile 2048 | Out-Null
    & $openssl.Source rsa -in $privateFile -pubout -out $publicFile | Out-Null

    $privatePem = Get-Content -Raw -Encoding UTF8 $privateFile
    $publicPem = Get-Content -Raw -Encoding UTF8 $publicFile
    Remove-Item $tmpDir -Recurse -Force
}
else {
    try {
        $rsa = [System.Security.Cryptography.RSA]::Create(2048)
        $privatePem = Export-RsaPrivateKeyPem $rsa
        $publicPem = Export-RsaPublicKeyPem $rsa
    }
    catch {
        Write-Error "Impossible de générer les clés RSA nativement et OpenSSL n'est pas disponible. Installez OpenSSL ou exécutez PowerShell avec une version .NET plus récente."
        exit 1
    }
}

$privateValue = Escape-Newlines($privatePem)
$publicValue = Escape-Newlines($publicPem)

$content = Get-Content -Raw -Encoding UTF8 $envFile
$content = Set-EnvValue $content 'DOCUAI_JWT_PRIVATE_KEY' $privateValue
$content = Set-EnvValue $content 'DOCUAI_JWT_PUBLIC_KEY' $publicValue

Set-Content -Path $envFile -Value $content -Encoding UTF8

Write-Host "Clés JWT générées et injectées dans $envFile"
Write-Host "DOCUAI_JWT_PRIVATE_KEY et DOCUAI_JWT_PUBLIC_KEY ont été mis à jour."
