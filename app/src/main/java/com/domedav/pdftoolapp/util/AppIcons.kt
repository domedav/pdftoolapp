package com.domedav.pdftoolapp.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.domedav.pdftoolapp.R

object AppIcons {

    @Composable
    private fun getVectorResource(resourceId: Int): ImageVector {
        return ImageVector.vectorResource(resourceId)
    }

    @Composable fun Add(): ImageVector = getVectorResource(R.drawable.ic_add)
    @Composable fun AddPhotoAlternate(): ImageVector = getVectorResource(R.drawable.ic_add_photo_alternate)
    @Composable fun CameraAlt(): ImageVector = getVectorResource(R.drawable.ic_camera_alt)
    @Composable fun ChevronRight(): ImageVector = getVectorResource(R.drawable.ic_chevron_right)
    @Composable fun Close(): ImageVector = getVectorResource(R.drawable.ic_close)
    @Composable fun HighQuality(): ImageVector = getVectorResource(R.drawable.ic_high_quality)
    @Composable fun Info(): ImageVector = getVectorResource(R.drawable.ic_info)
    @Composable fun LowPriority(): ImageVector = getVectorResource(R.drawable.ic_low_priority)
    @Composable fun OpenInNew(): ImageVector = getVectorResource(R.drawable.ic_open_in_new)
    @Composable fun PhotoLibrary(): ImageVector = getVectorResource(R.drawable.ic_photo_library)
    @Composable fun PictureAsPdf(): ImageVector = getVectorResource(R.drawable.ic_picture_as_pdf)
    @Composable fun Save(): ImageVector = getVectorResource(R.drawable.ic_save)
    @Composable fun Share(): ImageVector = getVectorResource(R.drawable.ic_share)
}