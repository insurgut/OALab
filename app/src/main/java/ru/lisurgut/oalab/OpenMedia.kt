package ru.lisurgut.oalab

// The included sample is CC BY 3.0 by Blender Foundation, not a commercial anime episode.
// Source: https://durian.blender.org/about/ . Attribution is mandatory.
object OpenMedia {
    val sintel = Anime(
        id = "open:sintel-trailer",
        title = "Sintel — пример воспроизведения",
        originalTitle = "Sintel trailer · Blender Foundation",
        description = "Открыто лицензированный (CC BY 3.0) анимационный трейлер Blender Foundation. " +
            "Это проверка работы плеера OAlab: не японское аниме и не коммерческая серия. " +
            "© Blender Foundation | durian.blender.org",
        cover = "",
        year = 2010,
        format = "ОТКРЫТЫЙ РОЛИК",
        episodes = 1,
        genres = listOf("Animation", "Fantasy"),
        official = listOf(
            OfficialWatch("Blender / Sintel", "https://durian.blender.org/download/")
        )
    )

    private val sampleStreams = listOf(
        StreamVariant(
            source = "GStreamer / Open Media",
            episode = 1,
            language = "en",
            team = "Оригинал · CC BY 3.0",
            voices = 1,
            votes = 0,
            popularity = 100,
            timing = "Оригинал",
            url = "https://gstreamer.freedesktop.org/media/sintel_trailer-480p.mp4",
            subtitle = ""
        ),
        StreamVariant(
            source = "Blender / Direct",
            episode = 1,
            language = "en",
            team = "Оригинал · запасной сервер",
            voices = 1,
            votes = 0,
            popularity = 90,
            timing = "Оригинал",
            url = "https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4",
            subtitle = ""
        )
    )
    fun streamsFor(anime: Anime): List<StreamVariant> =
        if (anime.id == sintel.id) sampleStreams else emptyList()
}
