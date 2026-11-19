package com.ironlog.app.ui.common

import android.content.Context
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.ironlog.app.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the system share sheet with [text], to send it as a message or to copy it. */
fun shareText(context: Context, text: String) {
  ShareCompat.IntentBuilder(context).setType("text/plain").setText(text).startChooser()
}

/**
 * Writes [content] to a file called [fileName] and opens the system share sheet with that file.
 * The file stays in the app's cache until one of the same name is shared again.
 */
suspend fun shareAsFile(context: Context, fileName: String, mimeType: String, content: String) {
  val file =
    try {
      withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, SHARED_FOLDER).apply { mkdirs() }
        File(folder, fileName).apply { writeText(content) }
      }
    } catch (_: IOException) {
      Toast.makeText(context, R.string.share_file_failed, Toast.LENGTH_LONG).show()
      return
    }
  // The share sheet passes on the right to read this one file to the app that is chosen.
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  ShareCompat.IntentBuilder(context).setType(mimeType).setStream(uri).startChooser()
}

/** Must match the path in `res/xml/shared_files.xml`. */
private const val SHARED_FOLDER = "shared"
