package com.donut.app.feature.gamedetail

import com.donut.app.core.auth.AuthSession
import com.donut.app.core.data.model.Game
import com.donut.app.core.data.model.isClosed
import com.donut.app.core.data.repository.EquipmentRepository
import com.donut.app.core.data.repository.GamesRepository
import com.donut.app.core.network.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.HttpURLConnection

/**
 * The game screen's equipment: the signed-in user's items, and which of them they use in this game.
 * Shares the game screen's state
 * @param gameId the game on screen
 * @param uiState the game screen's state, which this updates
 * @param scope the game screen view model's scope, so requests stop with it
 * @param gamesRepository the /games routes, to save the selection
 * @param equipmentRepository the /user/equipment routes, for the user's items
 * @param session sends the equipment requests as the signed-in user
 */
class GameDetailEquipment(
    private val gameId: String,
    private val uiState: MutableStateFlow<GameDetailUiState>,
    private val scope: CoroutineScope,
    private val gamesRepository: GamesRepository,
    private val equipmentRepository: EquipmentRepository,
    private val session: AuthSession,
) {
    /**
     * Loads the signed-in user's items, to pick from
     */
    fun load() {
        scope.launch {
            uiState.update { it.copy(isLoadingEquipment = true, equipmentErrorMessage = null) }
            equipmentRepository.getEquipment(session)
                .onSuccess { items ->
                    uiState.update { it.copy(isLoadingEquipment = false, userEquipment = items) }
                }
                .onFailure { error ->
                    uiState.update {
                        it.copy(isLoadingEquipment = false, equipmentErrorMessage = error.toUserMessage())
                    }
                }
        }
    }

    /**
     * Adds an item to the user's selection for the game, or takes it out, and saves it. The chip changes at once;
     * if saving fails, the selection goes back and the backend's reason is shown
     * @param equipmentId the user's item
     */
    fun toggle(equipmentId: Int) {
        val state = uiState.value
        if (state.game?.isClosed != false) return

        val before = state.selectedEquipmentIds
        val after = if (equipmentId in before) before - equipmentId else before + equipmentId
        uiState.update { it.copy(selectedEquipmentIds = after, equipmentErrorMessage = null) }

        scope.launch {
            gamesRepository.setGameEquipment(gameId, after.toList()).onFailure { error ->
                // The item was retired or deleted on another screen: show the user's items as they are now
                if (error is HttpException && error.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                    load()
                }
                uiState.update { it.copy(selectedEquipmentIds = before, equipmentErrorMessage = error.toUserMessage()) }
            }
        }
    }
}

/**
 * @param game the game on screen
 * @param username the signed-in user's username, null if signed out
 * @return the IDs of the items the signed-in user uses in the game; none if they aren't a player
 */
fun myEquipmentIds(game: Game, username: String?): Set<Int> {
    val myId = game.players.firstOrNull { it.username == username }?.id ?: return emptySet()
    return game.equipment.filter { it.userId == myId }.map { it.id }.toSet()
}
