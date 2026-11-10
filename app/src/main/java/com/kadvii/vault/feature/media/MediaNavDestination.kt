package com.kadvii.vault.feature.media

/**
 * Navigation route contract for Media management feature.
 */
object MediaNavDestination {
    const val MEDIA_VIEWER_ROUTE = "secure_image_viewer/{fileId}"
    const val VIDEO_PLAYER_ROUTE = "secure_video_player/{fileId}"
    const val DOC_VIEWER_ROUTE = "secure_doc_viewer/{fileId}"

    fun imageViewerRoute(fileId: Long): String = "secure_image_viewer/$fileId"
    fun videoPlayerRoute(fileId: Long): String = "secure_video_player/$fileId"
    fun docViewerRoute(fileId: Long): String = "secure_doc_viewer/$fileId"
}
