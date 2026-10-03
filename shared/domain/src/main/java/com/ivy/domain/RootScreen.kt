package com.ivy.domain

import android.net.Uri

interface RootScreen {
    /**
     * BuildConfig.DEBUG
     */
    val isDebug: Boolean

    /**
     * BuildConfig.VERSION_NAME
     */
    val buildVersionName: String

    /**
     * BuildConfig.VERSION_CODE
     */
    val buildVersionCode: Int



    fun openUrlInBrowser(url: String)

    fun shareCSVFile(fileUri: Uri)

    /**
     * Shares an exported file with the right MIME type.
     *
     * Sharing an .xlsx as "text/csv" makes some apps open it as broken text
     * rather than as a spreadsheet, so the type has to travel with the file.
     */
    fun shareFile(fileUri: Uri, mimeType: String)

    fun shareZipFile(fileUri: Uri)

    fun openGooglePlayAppPage(appId: String)

}
