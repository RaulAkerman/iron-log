package com.ironlog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ironlog.app.ui.theme.IronLogTheme

class MainActivity : ComponentActivity() {
  /** Goes up each time a supplement reminder is tapped; the navigation reacts to the change. */
  private var supplementsRequests by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Not after a rotation or theme change: the restored back stack already has the right screen.
    if (savedInstanceState == null) handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      IronLogTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(supplementsRequests = supplementsRequests)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent?.action == ACTION_OPEN_SUPPLEMENTS) supplementsRequests++
  }

  companion object {
    /** Opens the app on the Supplements tab. Used by reminder notifications. */
    const val ACTION_OPEN_SUPPLEMENTS = "com.ironlog.app.action.OPEN_SUPPLEMENTS"
  }
}
