package io.github.cococraft.puckradiosync.shared

data class RadioStation(
    val id: String,
    val name: String,
    val tagline: String,
    val streamUrl: String,
    val language: String,
)

object RadioStations {
    val chmp = RadioStation(
        id = "chmp",
        name = "98,5 FM",
        tagline = "Cogeco - French hockey radio",
        streamUrl = "https://playerservices.streamtheworld.com/api/livestream-redirect/CHMPFM.mp3",
        language = "fr",
    )

    val all: List<RadioStation> = listOf(chmp)

    fun requireById(id: String): RadioStation =
        all.firstOrNull { it.id == id } ?: error("Unknown radio station: $id")
}
