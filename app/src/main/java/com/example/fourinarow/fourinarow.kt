package com.example.fourinarow

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow



@Composable
fun TicTacToe2() {
    val navController = rememberNavController()
    val model = GameModel()
    model.initGame()

    NavHost(navController = navController, startDestination = "player") {
        composable("player") { NewPlayerScreen(navController, model) }
        composable("lobby") { LobbyScreen(navController, model) }
        composable("game/{gameId}") { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId")
            GameScreen(navController, model, gameId)
        }
    }
}

@Composable
fun NewPlayerScreen(navController: NavController, model: GameModel) {
    val sharedPreferences = LocalContext.current
        .getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

    // Check for playerId in SharedPreferences
    LaunchedEffect(Unit) {
        model.localPlayerId.value = sharedPreferences.getString("playerId", null)
        if (model.localPlayerId.value != null) {
            navController.navigate("lobby")
        }
    }

    if (model.localPlayerId.value == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.newplayerbackground),
                contentDescription = "Background Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            var playerName by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Welcome to Kalabs TicTacToe!",
                    color = Color.Black,
                    style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Enter your name", color = Color.Black) },
                    modifier = Modifier.fillMaxWidth().background(Color.White)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (playerName.isNotBlank()) {
                        // Create new player in Firestore
                        val newPlayer = Player(name = playerName)

                        model.db.collection("players")
                            .add(newPlayer)
                            .addOnSuccessListener { documentRef ->
                                val newPlayerId = documentRef.id

                                // Save playerId in SharedPreferences
                                sharedPreferences.edit().putString("playerId", newPlayerId).apply()

                                // Update local variable and navigate to lobby
                                model.localPlayerId.value = newPlayerId
                                navController.navigate("lobby")
                            }.addOnFailureListener { error ->
                                Log.e("KalabError", "Error creating player: ${error.message}")
                            }
                    } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Player")
                }
            }
        }
    } else {
        Text("Loding....")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(navController: NavController, model: GameModel) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    LaunchedEffect(games) {
        games.forEach { (gameId, game) ->
            if ((game.player1Id == model.localPlayerId.value || game.player2Id == model.localPlayerId.value)
                && (game.gameState == "player1_turn" || game.gameState == "player2_turn")
            ) {
                navController.navigate("game/${gameId}")
            }
        }
    }

    var playerName = "Unknown?"
    players[model.localPlayerId.value]?.let {
        playerName = it.name
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.fade),
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopAppBar(
                title = { Text("TicTacToe - $playerName") }, colors = TopAppBarDefaults.
                smallTopAppBarColors(containerColor = Color.Transparent)) }
        ) { innerPadding ->
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(players.entries.toList()) { (documentId, player) ->
                    if (documentId != model.localPlayerId.value) { // Don't show yourself
                        ListItem(
                            headlineContent = {
                                Text("Player Name: ${player.name}")
                            },
                            supportingContent = {
                                Text("Status: ...")
                            },
                            trailingContent = {
                                var hasGame = false
                                games.forEach { (gameId, game) ->
                                    if (game.player1Id == model.localPlayerId.value && game.gameState == "invite") {
                                        Text("Waiting for accept...")
                                        hasGame = true
                                    } else if (game.player2Id == model.localPlayerId.value && game.gameState == "invite") {
                                        Row {
                                            // Accept Button
                                            Button(onClick = {
                                                model.db.collection("games").document(gameId)
                                                    .update("gameState", "player1_turn")
                                                    .addOnSuccessListener {
                                                        navController.navigate("game/${gameId}")
                                                    }
                                                    .addOnFailureListener {
                                                        Log.e("KalabError", "Error updating game: $gameId")
                                                    }
                                            }) {
                                                Text("Accept Invite")
                                            }

                                            Spacer(modifier = Modifier.width(8.dp)) // Add spacing between buttons

                                            // Decline Button
                                            Button(onClick = {
                                                model.db.collection("games").document(gameId)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        Log.d("KalabInfo", "Invite declined and game deleted: $gameId")
                                                    }
                                                    .addOnFailureListener {
                                                        Log.e("KalabError", "Error declining invite: $gameId")
                                                    }
                                            }) {
                                                Text("Decline Invite")
                                            }
                                        }
                                        hasGame = true
                                    }
                                }

                                if (!hasGame) {
                                    // Challenge Button
                                    Button(onClick = {
                                        model.db.collection("games")
                                            .add(
                                                Game(
                                                    gameState = "invite",
                                                    player1Id = model.localPlayerId.value!!,
                                                    player2Id = documentId
                                                )
                                            )
                                            .addOnSuccessListener { documentRef ->
                                                Log.d("KalabInfo", "Challenge sent: ${documentRef.id}")
                                            }
                                            .addOnFailureListener {
                                                Log.e("KalabError", "Error sending challenge: ${it.message}")
                                            }
                                    }) {
                                        Text("Challenge")
                                    }
                                }
                            }

                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(navController: NavController, model: GameModel, gameId: String?) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    var playerName = "Unknown?"
    players[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    if (gameId != null && games.containsKey(gameId)) {
        val game = games[gameId]!!

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.fade),
                contentDescription = "Background Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopAppBar(title =  { Text("TicTacToe - $playerName")}, colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Transparent)) }
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(innerPadding).fillMaxWidth()
            ) {
                when (game.gameState) {
                    "player1_won", "player2_won", "draw" -> {

                        Text("Game over!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.padding(20.dp))

                        if (game.gameState == "draw") {
                            Text("It's a Draw!", style = MaterialTheme.typography.headlineMedium)
                        } else {
                            Text(
                                "Player ${if (game.gameState == "player1_won") "1" else "2"} won!",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Button(onClick = {
                            navController.navigate("lobby")
                        }) {
                            Text("Back to lobby")
                        }
                    }

                    else -> {

                        val myTurn =
                            game.gameState == "player1_turn" && game.player1Id == model.localPlayerId.value || game.gameState == "player2_turn" && game.player2Id == model.localPlayerId.value
                        val turn = if (myTurn) "Your turn!" else "Wait for other player"
                        Text(turn, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.padding(20.dp))

                        Text("Player 1: ${players[game.player1Id]!!.name}")
                        Text("Player 2: ${players[game.player2Id]!!.name}")
                        Text("State: ${game.gameState}")
                        Text("GameId: ${gameId}")
                    }
                }


                Spacer(modifier = Modifier.padding(20.dp))

                // row * 3 + col
                // i * 3 + j

                for (i in 0 ..< rows) {
                    Row {
                        for (j in 0..< cols) {
                            Button(
                                shape = CircleShape,
                                modifier = Modifier.size(55.dp).padding(2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                                onClick = {
                                    model.checkGameState(gameId, i , j)
                                }, contentPadding = PaddingValues(0.dp)
                            ) {
                                // Text("Cell ${i * cols + j} Value: ${game.gameBoard[i * cols + j]}")
                                if (game.gameBoard[i * cols + j] == 1) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cricel),
                                        tint = Color.Red,
                                        contentDescription = "X",
                                        modifier = Modifier.size(55.dp).padding(0.dp)
                                    )
                                } else if (game.gameBoard[i * cols + j] == 2) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cricel),
                                        tint = Color.Blue,
                                        contentDescription = "O",
                                        modifier = Modifier.size(55.dp).padding(0.dp)
                                    )
                                } else {
                                    Text("")
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    } else {
        Log.e(
            "kalabError",
            "Error Game not found: $gameId"
        )
        navController.navigate("lobby")
    }
}

