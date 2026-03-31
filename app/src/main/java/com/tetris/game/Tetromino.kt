package com.tetris.game

import android.graphics.Color

enum class TetrominoType {
    I, O, T, S, Z, J, L
}

data class Tetromino(
    val type: TetrominoType,
    var x: Int = 0,
    var y: Int = 0,
    var rotation: Int = 0
) {
    companion object {
        // Shape definitions: [rotation][row][col]
        private val SHAPES = mapOf(
            TetrominoType.I to arrayOf(
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(1, 1, 1, 1),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(0, 0, 1, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(1, 1, 1, 1),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0)
                )
            ),
            TetrominoType.O to arrayOf(
                arrayOf(
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                )
            ),
            TetrominoType.T to arrayOf(
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(1, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(1, 1, 1, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                )
            ),
            TetrominoType.S to arrayOf(
                arrayOf(
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(1, 0, 0, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                )
            ),
            TetrominoType.Z to arrayOf(
                arrayOf(
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(1, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                )
            ),
            TetrominoType.J to arrayOf(
                arrayOf(
                    intArrayOf(1, 0, 0, 0),
                    intArrayOf(1, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(1, 1, 1, 0),
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                )
            ),
            TetrominoType.L to arrayOf(
                arrayOf(
                    intArrayOf(0, 0, 1, 0),
                    intArrayOf(1, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 1, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(1, 1, 1, 0),
                    intArrayOf(1, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                ),
                arrayOf(
                    intArrayOf(1, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0)
                )
            )
        )

        val COLORS = mapOf(
            TetrominoType.I to Color.parseColor("#00F5FF"),
            TetrominoType.O to Color.parseColor("#FFE600"),
            TetrominoType.T to Color.parseColor("#FF6B35"),
            TetrominoType.S to Color.parseColor("#39FF14"),
            TetrominoType.Z to Color.parseColor("#FF3131"),
            TetrominoType.J to Color.parseColor("#4D9FFF"),
            TetrominoType.L to Color.parseColor("#FFB347")
        )

        fun random(): Tetromino {
            val types = TetrominoType.values()
            return Tetromino(types[types.indices.random()])
        }
    }

    fun getShape(): Array<IntArray> {
        return SHAPES[type]!![rotation % 4]
    }

    fun getColor(): Int = COLORS[type] ?: Color.WHITE

    fun rotated(direction: Int = 1): Tetromino {
        val newRotation = ((rotation + direction) % 4 + 4) % 4
        return copy(rotation = newRotation)
    }

    fun getCells(): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        val shape = getShape()
        for (row in shape.indices) {
            for (col in shape[row].indices) {
                if (shape[row][col] != 0) {
                    cells.add(Pair(x + col, y + row))
                }
            }
        }
        return cells
    }
}
