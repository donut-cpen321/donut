package com.example.cpen321application.feature.scheduledgame

import com.example.cpen321application.core.data.model.Game
import com.example.cpen321application.core.data.model.Tournament
import java.time.Instant

/**
 * One card of the Games tab: a game or a tournament
 * @property key unique across games and tournaments, for the list
 * @property startsAt when the game or the tournament starts, to sort the list
 */
sealed interface FeedItem {
    val key: String
    val startsAt: Instant

    data class GameItem(val game: Game) : FeedItem {
        override val key: String
            get() = "game-${game.id}"
        override val startsAt: Instant
            get() = game.startsAt
    }

    data class TournamentItem(val tournament: Tournament) : FeedItem {
        override val key: String
            get() = "tournament-${tournament.id}"
        override val startsAt: Instant
            get() = tournament.startsAt
    }
}

/**
 * Mixes games and tournaments into one list, soonest first.
 * The sort is stable, so a game and a tournament at the same time keep games first.
 */
fun mergeByStartTime(games: List<Game>, tournaments: List<Tournament>): List<FeedItem> {
    val items = games.map { FeedItem.GameItem(it) } + tournaments.map { FeedItem.TournamentItem(it) }
    return items.sortedBy { it.startsAt }
}
