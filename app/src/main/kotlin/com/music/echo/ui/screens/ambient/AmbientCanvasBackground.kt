package echo.music.iad1tya.ui.screens.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import echo.music.iad1tya.applecanvas.AppleMusicCanvasProvider
import echo.music.iad1tya.canvas.CanvasArtwork
import echo.music.iad1tya.canvas.TidalCanvasProvider
import echo.music.iad1tya.echomusiccanvas.echomusicCanvasProvider
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.ui.player.CanvasArtworkPlaybackCache
import echo.music.iad1tya.ui.player.CanvasArtworkPlayer
import echo.music.iad1tya.ui.player.normalizeCanvasArtistName
import echo.music.iad1tya.ui.player.normalizeCanvasSongTitle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AmbientCanvasBackground(
  mediaMetadata: MediaMetadata?,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
) {
  val metadata = mediaMetadata ?: return
  var canvasArtwork by remember(metadata.id) { mutableStateOf<CanvasArtwork?>(null) }
  val storefront = remember {
    val country = Locale.getDefault().country
    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
  }

  LaunchedEffect(
    metadata.id,
    metadata.title,
    metadata.artist,
    metadata.albumTitle,
    storefront,
  ) {
    CanvasArtworkPlaybackCache.get(metadata.id)?.let {
      canvasArtwork = it
      return@LaunchedEffect
    }

    val fetched =
      withContext(Dispatchers.IO) {
        val albumName = metadata.albumTitle?.toString().orEmpty()
        val songTitleRaw = metadata.title?.toString().orEmpty()
        val artistNameRaw = metadata.artist?.toString().orEmpty()

        val songTitle = normalizeCanvasSongTitle(songTitleRaw)
        val artistName = normalizeCanvasArtistName(artistNameRaw)

        if (songTitle.isBlank() || artistName.isBlank()) {
          null
        } else {
          linkedSetOf(
              songTitle to artistName,
              songTitleRaw to artistName,
              songTitle to artistNameRaw,
              songTitleRaw to artistNameRaw,
            )
            .filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }
            .firstNotNullOfOrNull { (song, artist) ->
              if (albumName.isNotBlank()) {
                AppleMusicCanvasProvider.getByAlbumArtist(
                    album = albumName,
                    artist = artist,
                    storefront = storefront,
                  )
                  ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                  ?.let { return@firstNotNullOfOrNull it }
              }

              echomusicCanvasProvider.getBySongArtist(song = song, artist = artist)?.takeIf {
                !it.preferredAnimationUrl.isNullOrBlank()
              }
                ?: TidalCanvasProvider.getBySongArtist(
                    song = song,
                    artist = artist,
                    album = albumName.ifBlank { null },
                  )
                  ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(
                    song = song,
                    artist = artist,
                    album = albumName.ifBlank { null },
                    storefront = storefront,
                  )
                  ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            }
        }
      }

    val requestedArtist = metadata.artist?.toString().orEmpty()
    val requestedTitle = metadata.title?.toString().orEmpty()
    val requestedAlbum = metadata.albumTitle?.toString().orEmpty()

    canvasArtwork =
      fetched?.let { artwork ->
        val resultArtist = artwork.artist
        val artistMatches =
          if (resultArtist != null && requestedArtist.isNotBlank()) {
            val normalizedResult = normalizeCanvasArtistName(resultArtist)
            val normalizedRequested = normalizeCanvasArtistName(requestedArtist)
            resultArtist.contains(requestedArtist, ignoreCase = true) ||
              requestedArtist.contains(resultArtist, ignoreCase = true) ||
              normalizedResult.contains(normalizedRequested, ignoreCase = true) ||
              normalizedRequested.contains(normalizedResult, ignoreCase = true)
          } else {
            true
          }

        val canvasAlbumName = artwork.albumName
        val canvasSongName = artwork.name
        val titleMatches =
          when {
            canvasAlbumName != null && requestedAlbum.isNotBlank() -> {
              val normalizedCanvasAlbum = normalizeCanvasSongTitle(canvasAlbumName)
              val normalizedRequestedAlbum = normalizeCanvasSongTitle(requestedAlbum)
              canvasAlbumName.contains(requestedAlbum, ignoreCase = true) ||
                requestedAlbum.contains(canvasAlbumName, ignoreCase = true) ||
                normalizedCanvasAlbum.contains(normalizedRequestedAlbum, ignoreCase = true) ||
                normalizedRequestedAlbum.contains(normalizedCanvasAlbum, ignoreCase = true)
            }

            canvasSongName != null && requestedTitle.isNotBlank() -> {
              val normalizedCanvasSong = normalizeCanvasSongTitle(canvasSongName)
              val normalizedRequestedTitle = normalizeCanvasSongTitle(requestedTitle)
              canvasSongName.contains(requestedTitle, ignoreCase = true) ||
                requestedTitle.contains(canvasSongName, ignoreCase = true) ||
                normalizedCanvasSong.contains(normalizedRequestedTitle, ignoreCase = true) ||
                normalizedRequestedTitle.contains(normalizedCanvasSong, ignoreCase = true)
            }

            else -> true
          }

        if (artistMatches && titleMatches) artwork else null
      }

    canvasArtwork?.let { CanvasArtworkPlaybackCache.put(metadata.id, it) }
  }

  canvasArtwork?.let { artwork ->
    CanvasArtworkPlayer(
      primaryUrl = artwork.animated,
      fallbackUrl = artwork.videoUrl,
      isPlaying = isPlaying,
      modifier = modifier,
    )
  }
}
