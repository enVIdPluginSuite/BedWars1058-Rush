package dev.etran.bedwarsrush.compat

import org.bukkit.block.Block

object Blocks {
    fun place(block: Block, spec: BlockSpec) {
        block.type = spec.material
        if (spec.data.toInt() != 0 && ServerVersion.current.isLegacyMaterials) legacySetData(block, spec.data)
    }

    /** Reads what is needed to clone [block] later (e.g. a coloured wool the player just placed). */
    fun specOf(block: Block): BlockSpec =
        if (ServerVersion.current.isLegacyMaterials) BlockSpec(block.type, legacyData(block)) else BlockSpec(block.type, 0)

    // Block#getData/setData are deprecated, and gone from newer APIs, so they are only ever touched on 1.8 - 1.12.
    @Suppress("DEPRECATION")
    private fun legacySetData(block: Block, data: Byte) {
        block.data = data
    }

    @Suppress("DEPRECATION")
    private fun legacyData(block: Block): Byte = block.data
}
