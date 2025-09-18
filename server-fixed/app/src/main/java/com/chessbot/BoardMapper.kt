package com.chessbot

import kotlin.math.roundToInt

/**
 * BoardMapper handles the conversion between UCI chess notation and screen coordinates.
 * 
 * Key responsibilities:
 * - Map UCI moves (e.g., "e2e4") to screen tap coordinates
 * - Handle board flip toggle for coordinate mirroring
 * - Maintain fixed 8x8 grid mapping from board boundaries
 * 
 * Board coordinates (fixed on phone):
 * - Top-left: x=12, y=502
 * - Bottom-right: x=710, y=1203
 * - 8x8 grid mapping with flip support
 */
class BoardMapper {
    
    companion object {
        // Fixed board boundaries on phone screen
        private const val BOARD_LEFT = 12
        private const val BOARD_TOP = 502
        private const val BOARD_RIGHT = 710
        private const val BOARD_BOTTOM = 1203
        
        // Calculate square dimensions
        private val SQUARE_WIDTH = (BOARD_RIGHT - BOARD_LEFT) / 8.0
        private val SQUARE_HEIGHT = (BOARD_BOTTOM - BOARD_TOP) / 8.0
        
        // Chess files and ranks
        private val FILES = arrayOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')
        private val RANKS = arrayOf('1', '2', '3', '4', '5', '6', '7', '8')
    }
    
    private var isBoardFlipped = false
    
    /**
     * Data class to represent screen coordinates
     */
    data class Coordinate(val x: Int, val y: Int) {
        override fun toString(): String = "($x, $y)"
    }
    
    /**
     * Data class to represent a move with from/to coordinates
     */
    data class Move(val from: Coordinate, val to: Coordinate) {
        override fun toString(): String = "Move: $from -> $to"
    }
    
    /**
     * Toggle board flip state
     */
    fun flipBoard() {
        isBoardFlipped = !isBoardFlipped
        println("Board flip toggled. Current state: ${if (isBoardFlipped) "flipped" else "normal"}")
    }
    
    /**
     * Get current board flip state
     */
    fun isBoardFlipped(): Boolean = isBoardFlipped
    
    /**
     * Convert UCI square notation (e.g., "e2") to screen coordinates
     */
    fun squareToCoordinate(square: String): Coordinate {
        if (square.length != 2) {
            throw IllegalArgumentException("Invalid square notation: $square")
        }
        
        val file = square[0].lowercaseChar()
        val rank = square[1]
        
        val fileIndex = FILES.indexOf(file)
        val rankIndex = RANKS.indexOf(rank)
        
        if (fileIndex == -1 || rankIndex == -1) {
            throw IllegalArgumentException("Invalid square notation: $square")
        }
        
        // Calculate base coordinates (center of square)
        val baseX = BOARD_LEFT + (fileIndex + 0.5) * SQUARE_WIDTH
        val baseY = BOARD_TOP + (7 - rankIndex + 0.5) * SQUARE_HEIGHT // Note: rank 1 is at bottom
        
        // Apply board flip if enabled
        val finalX = if (isBoardFlipped) {
            BOARD_LEFT + (BOARD_RIGHT - baseX)
        } else {
            baseX
        }
        
        val finalY = if (isBoardFlipped) {
            BOARD_TOP + (BOARD_BOTTOM - baseY)
        } else {
            baseY
        }
        
        return Coordinate(finalX.roundToInt(), finalY.roundToInt())
    }
    
    /**
     * Convert UCI move notation (e.g., "e2e4") to Move with screen coordinates
     */
    fun uciToMove(uciMove: String): Move {
        if (uciMove.length < 4) {
            throw IllegalArgumentException("Invalid UCI move: $uciMove")
        }
        
        val fromSquare = uciMove.substring(0, 2)
        val toSquare = uciMove.substring(2, 4)
        
        val fromCoord = squareToCoordinate(fromSquare)
        val toCoord = squareToCoordinate(toSquare)
        
        println("UCI Move: $uciMove")
        println("From: $fromSquare -> $fromCoord")
        println("To: $toSquare -> $toCoord")
        println("Board flipped: $isBoardFlipped")
        
        return Move(fromCoord, toCoord)
    }
    
    /**
     * Convert screen coordinate back to UCI square notation (for debugging)
     */
    fun coordinateToSquare(coord: Coordinate): String {
        // Apply reverse flip if board is flipped
        val baseX = if (isBoardFlipped) {
            BOARD_LEFT + (BOARD_RIGHT - coord.x)
        } else {
            coord.x.toDouble()
        }
        
        val baseY = if (isBoardFlipped) {
            BOARD_TOP + (BOARD_BOTTOM - coord.y)
        } else {
            coord.y.toDouble()
        }
        
        // Find the file and rank indices
        val fileIndex = ((baseX.toDouble() - BOARD_LEFT.toDouble()) / SQUARE_WIDTH).toInt().coerceIn(0, 7)
        val rankIndex = (7 - ((baseY.toDouble() - BOARD_TOP.toDouble()) / SQUARE_HEIGHT).toInt()).coerceIn(0, 7)
        
        return "${FILES[fileIndex]}${RANKS[rankIndex]}"
    }
    
    /**
     * Print complete board coordinate mapping for debugging
     */
    fun printBoardMapping() {
        println("\n=== Board Coordinate Mapping ===")
        println("Board boundaries: ($BOARD_LEFT,$BOARD_TOP) to ($BOARD_RIGHT,$BOARD_BOTTOM)")
        println("Square size: ${SQUARE_WIDTH.roundToInt()} x ${SQUARE_HEIGHT.roundToInt()}")
        println("Board flipped: $isBoardFlipped")
        println()
        
        // Print mapping for all squares
        for (rank in 8 downTo 1) {
            for (file in 'a'..'h') {
                val square = "$file$rank"
                val coord = squareToCoordinate(square)
                print("$square:${coord} ")
            }
            println()
        }
        println("================================\n")
    }
    
    /**
     * Validate that coordinates are within board boundaries
     */
    fun isValidCoordinate(coord: Coordinate): Boolean {
        return coord.x >= BOARD_LEFT && coord.x <= BOARD_RIGHT &&
               coord.y >= BOARD_TOP && coord.y <= BOARD_BOTTOM
    }
    
    /**
     * Get all possible square coordinates for testing
     */
    fun getAllSquareCoordinates(): Map<String, Coordinate> {
        val coordinates = mutableMapOf<String, Coordinate>()
        for (rank in '1'..'8') {
            for (file in 'a'..'h') {
                val square = "$file$rank"
                coordinates[square] = squareToCoordinate(square)
            }
        }
        return coordinates
    }
}