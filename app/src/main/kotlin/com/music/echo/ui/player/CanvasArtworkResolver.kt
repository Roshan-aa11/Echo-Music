package echo.music.iad1tya.ui.player

import echo.music.iad1tya.applecanvas.AppleMusicCanvasProvider
import echo.music.iad1tya.canvas.CanvasArtwork
import echo.music.iad1tya.canvas.TidalCanvasProvider
import echo.music.iad1tya.echomusiccanvas.echomusicCanvasProvider
import java.util.Locale

/**
 * Resolves Canvas artwork using the same provider order and metadata validation used by the
 * normal player thumbnail.
 */
internal suspend fun resolveCanvasArtwork(
  songTitle: String,
  artistName: String,
  albumName: String?,
): CanvasArtwork? {
  val storefront =
    Locale.getDefault().country
      .takeIf { it.length == 2 }
      ?.lowercase(Locale.ROOT)
      ?: "us"

  val normalizedSong = normalizeCanvasSongTitle(songTitle)
  val normalizedArtist = normalizeCanvasArtistName(artistName)

  val candidates =
    linkedSetOf(
      normalizedSong to normalizedArtist,
      songTitle to normalizedArtist,
      normalizedSong to artistName,
      songTitle to artistName,
    ).filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }

  val fetched =
    candidates.firstNotNullOfOrNull { (song, artist) ->
      if (!albumName.isNullOrBlank()) {
        AppleMusicCanvasProvider.getByAlbumArtist(
            album = albumName,
            artist = artist,
            storefront = storefront,
          )
          ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
          ?.let { return@firstNotNullOfOrNull it }
      }

      echomusicCanvasProvider.getBySongArtist(song = song, artist = artist)
        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        ?: TidalCanvasProvider.getBySongArtist(
            song = song,
            artist = artist,
            album = albumName,
          )
          ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        ?: AppleMusicCanvasProvider.getBySongArtist(
            song = song,
            artist = artist,
            album = albumName,
            storefront = storefront,
          )
          ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
    }

  return validateCanvasArtwork(
    artwork = fetched,
    requestedTitle = songTitle,
    requestedArtist = artistName,
    requestedAlbum = albumName.orEmpty(),
  )
}

private fun validateCanvasArtwork(
  artwork: CanvasArtwork?,
  requestedTitle: String,
  requestedArtist: String,
  requestedAlbum: String,
): CanvasArtwork? {
  if (artwork == null) return null

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
        val normalizedRequestedAlbum =
          if (requestedAlbum.isNotBlank()) normalizeCanvasSongTitle(requestedAlbum) else ""

        canvasSongName.contains(requestedTitle, ignoreCase = true) ||
          requestedTitle.contains(canvasSongName, ignoreCase = true) ||
          normalizedCanvasSong.contains(normalizedRequestedTitle, ignoreCase = true) ||
          normalizedRequestedTitle.contains(normalizedCanvasSong, ignoreCase = true) ||
          (requestedAlbum.isNotBlank() &&
            (canvasSongName.contains(requestedAlbum, ignoreCase = true) ||
              requestedAlbum.contains(canvasSongName, ignoreCase = true) ||
              normalizedCanvasSong.contains(normalizedRequestedAlbum, ignoreCase = true) ||
              normalizedRequestedAlbum.contains(normalizedCanvasSong, ignoreCase = true)))
      }
      else -> true
    }

  return artwork.takeIf { artistMatches && titleMatches }
}
