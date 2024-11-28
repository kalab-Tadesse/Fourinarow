package com.example.fourinarow


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow


data class Player(
    var name: String = ""
)

data class Game(
    var gameBoard: List<Int> = List(42) { 0 }, // 0: empty, 1: player1's move, 2: player2's move
    var gameState: String = "invite", // Possible values: "invite", "player1_turn", "player2_turn" "player1_won", "player2_won", "draw"
    var player1Id: String = "",
    var player2Id: String = ""
)

const val rows = 6
const val cols = 7

class GameModel: ViewModel() {
    val db = Firebase.firestore
    var localPlayerId = mutableStateOf<String?>(null)
    val playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    val gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())

    fun initGame() {

        // Listen for players
        db.collection("players")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Player::class.java)!!
                    }
                    playerMap.value = updatedMap
                }
            }

        // Listen for games
        db.collection("games")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Game::class.java)!!
                    }
                    gameMap.value = updatedMap
                }
            }
    }

    fun checkWinner(board: List<Int>, rows: Int = 6, cols: Int = 7): Int {
        // Helper function to check four in a line
        fun isWinningSequence(indices: List<Int>): Boolean {
            val first = board[indices[0]]
            return first != 0 && indices.all { board[it] == first }
        }

        // Check rows
        for (row in 0 until rows) {
            for (col in 0 until cols - 3) {
                val indices = listOf(
                    row * cols + col,
                    row * cols + col + 1,
                    row * cols + col + 2,
                    row * cols + col + 3
                )
                if (isWinningSequence(indices)) {
                    return board[indices[0]]
                }
            }
        }

        // Check columns
        for (col in 0 until cols) {
            for (row in 0 until rows - 3) {
                val indices = listOf(
                    row * cols + col,
                    (row + 1) * cols + col,
                    (row + 2) * cols + col,
                    (row + 3) * cols + col
                )
                if (isWinningSequence(indices)) {
                    return board[indices[0]]
                }
            }
        }

        // Check diagonals (top-left to bottom-right)
        for (row in 0 until rows - 3) {
            for (col in 0 until cols - 3) {
                val indices = listOf(
                    row * cols + col,
                    (row + 1) * cols + col + 1,
                    (row + 2) * cols + col + 2,
                    (row + 3) * cols + col + 3
                )
                if (isWinningSequence(indices)) {
                    return board[indices[0]]
                }
            }
        }

        // Check diagonals (top-right to bottom-left)
        for (row in 0 until rows - 3) {
            for (col in 3 until cols) {
                val indices = listOf(
                    row * cols + col,
                    (row + 1) * cols + col - 1,
                    (row + 2) * cols + col - 2,
                    (row + 3) * cols + col - 3
                )
                if (isWinningSequence(indices)) {
                    return board[indices[0]]
                }
            }
        }

        // Check for draw
        if (board.none { it == 0 }) {
            return 3 // Draw
        }

        // Game is still ongoing
        return 0
    }



    fun checkGameState(gameId: String?, cell: Int) {

        if (gameId != null) {
            val game: Game? = gameMap.value[gameId]
            if (game != null) {

                val myTurn = game.gameState == "player1_turn" && game.player1Id == localPlayerId.value || game.gameState == "player2_turn" && game.player2Id == localPlayerId.value
                if (!myTurn) return

                val list: MutableList<Int> = game.gameBoard.toMutableList()
                if (list[cell] != 0) return
                if (game.gameState == "player1_turn") {
                    list[cell] = 1

                } else if (game.gameState == "player2_turn") {
                    list[cell] = 2
                }
                var turn = ""
                if (game.gameState == "player1_turn") {
                    turn = "player2_turn"
                } else {
                    turn = "player1_turn"
                }

                val winner = checkWinner(list.toList())
                if (winner == 1) {
                    turn = "player1_won"
                } else if (winner == 2) {
                    turn = "player2_won"
                } else if (winner == 3) {
                    turn = "draw"
                }

                db.collection("games").document(gameId)
                    .update(
                        "gameBoard", list,
                        "gameState", turn
                    )
            }
        }
    }


}