$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )
    & adb @Args
}

function Uninstall-ForUser0 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Package
    )
    Write-Host "Uninstalling for user 0: $Package"
    Invoke-Adb @("shell", "pm", "uninstall", "--user", "0", $Package)
}

function Disable-ForUser0 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Package
    )
    Write-Host "Disabling for user 0: $Package"
    Invoke-Adb @("shell", "pm", "disable-user", "--user", "0", $Package)
}

Write-Host "Checking device..."
Invoke-Adb @("get-state")

# Low-risk removals: user apps / obvious extras.
$uninstallPackages = @(
    "com.claro.pe.miclaro",
    "ibm.claro.appautoventa",
    "com.motorola.aiservices",
    "com.motorola.ccc.mainplm",
    "com.motorola.ccc.notification",
    "com.motorola.ccc.ota",
    "com.motorola.dimo",
    "com.motorola.spaces",
    "com.motorola.paks",
    "com.motorola.paks.notification",
    "com.motorola.securityhub",
    "com.motorola.securityhubext",
    "com.motorola.batterycare",
    "com.motorola.batterycare.overlay",
    "com.motorola.screenshoteditor",
    "com.motorola.gesture",
    "com.motorola.actions",
    "com.motorola.appforecast",
    "com.motorola.hiddenmenuapp",
    "com.motorola.sstservice",
    "com.motorola.sarcontrol",
    "com.motorola.revoker.services",
    "com.motorola.slpc_sys",
    "com.motorola.smart5g",
    "com.motorola.thermalservice",
    "com.motorola.motocit",
    "com.motorola.enterprise.service",
    "com.motorola.setup",
    "com.motorola.android.fota",
    "com.motorola.android.provisioning",
    "com.motorola.launcherconfig.overlay.amxpe",
    "com.motorola.launcherconfig.overlay.amx",
    "com.motorola.launcherconfig.overlay.amxcl",
    "com.motorola.launcherconfig.overlay.amxco",
    "com.motorola.launcherconfig.overlay.amxla",
    "com.motorola.launcherconfig.overlay.amxmx",
    "com.motorola.android.systemui.overlay.att",
    "com.motorola.android.systemui.overlay.sprint",
    "com.motorola.android.systemui.overlay.vzw",
    "com.motorola.android.systemui.overlay.tmo",
    "com.motorola.android.systemui.overlay.usc",
    "com.motorola.omadm.service",
    "com.motorola.omadm.vzw",
    "com.motorola.vzw.pco.extensions.pcoreceiver",
    "com.motorola.att.phone.extensions",
    "com.motorola.attvowifi",
    "com.oem.euiccpartnerapp.overlay.att",
    "com.oem.euiccpartnerapp.overlay.vzw",
    "com.oem.euiccpartnerapp.overlay.tmo",
    "com.oem.euiccpartnerapp.overlay.na",
    "com.oem.euiccpartnerapp.overlay.br",
    "com.oem.euiccpartnerapp.overlay.retca",
    "com.oem.euiccpartnerapp.overlay.dish",
    "com.oem.euiccpartnerapp.overlay.cc",
    "net.zedge.android",
    "com.defianttech.diskdiggerpro",
    "com.binance.dev",
    "com.microsoft.office.outlook",
    "com.microsoft.office.excel",
    "com.microsoft.office.word",
    "com.microsoft.skydrive",  
    "com.redarbor.computrabajo",
    "com.yandex.yango",
    "com.moonshot.kimichat", 
    "com.passbolt.mobile.android",
    "com.sony.songpal.mdr",
    "com.pixel.art.coloring.color.number",
    "com.tripledot.woodoku",
    "com.dstukalov.wavideostickers",
    "com.mercadolibre",
    "com.zhiliaoapp.musically",
    "com.amazon.appmanager",
    "com.anydesk.anydeskandroid", 
    "funvent.tilepark",
    "com.payjoy.access" 
    "com.google.android.apps.tachyon",
    "com.google.android.apps.googleassistant",
    "com.google.android.feedback",
    "com.google.android.apps.bard",
    "com.niksoftware.snapseed"
)

$disablePackages = @()

foreach ($package in $uninstallPackages) {
    try {
        Uninstall-ForUser0 -Package $package
    } catch {
        Write-Warning "Could not uninstall $package : $($_.Exception.Message)"
    }
}

foreach ($package in $disablePackages) {
    try {
        Disable-ForUser0 -Package $package
    } catch {
        Write-Warning "Could not disable $package : $($_.Exception.Message)"
    }
}

Write-Host ""
Write-Host "Done."
Write-Host "If you need to restore a package disabled/uninstalled for user 0:"
Write-Host "adb shell cmd package install-existing <package>"
