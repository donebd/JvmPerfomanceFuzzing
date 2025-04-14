package core.mutation.strategy.common

import soot.Body
import soot.jimple.GotoStmt
import soot.jimple.IfStmt

/**
 * Вспомогательный класс для поиска блоков кода
 */
class BlockFinder(private val body: Body) {

    fun findBlocks(): List<Block> {
        val result = mutableListOf<Block>()
        findLoopBlocks(result)
        findIfElseBlocks(result)
        return result
    }

    private fun findLoopBlocks(blocks: MutableList<Block>) {
        val units = body.units.toList()

        for (i in units.indices) {
            val unit = units[i]
            if (unit is GotoStmt) {
                val target = unit.target
                val targetIndex = units.indexOf(target)

                if (targetIndex < i) {
                    blocks.add(Block(target, unit))
                }
            }
        }
    }

    private fun findIfElseBlocks(blocks: MutableList<Block>) {
        val units = body.units.toList()

        for (i in units.indices) {
            val unit = units[i]
            if (unit is IfStmt) {
                val target = unit.target
                val targetIndex = units.indexOf(target)

                if (targetIndex > i) {
                    blocks.add(Block(unit, units[targetIndex - 1]))
                }
            }
        }
    }
}

/**
 * Структура данных для представления блока кода
 */
data class Block(val startUnit: soot.Unit, val endUnit: soot.Unit)
