package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._

object UartUtils {

    def reverse(data: UInt, width: UInt): UInt = {
        // Assume the maximum width is that of data.
        val maxWidth = data.getWidth

        // Precompute the reversed value for each possible width (from 0 up to maxWidth).
        val lookup: Seq[UInt] = Seq.tabulate(maxWidth + 1) { w =>
            if (w == 0) {
                0.U(maxWidth.W)
            } else {
                // Extract the lower w bits, reverse them and then pad with zeros.
                val reversed =
                    VecInit((0 until w).map(i => data(i)).reverse).asUInt
                Cat(0.U((maxWidth - w).W), reversed)
            }
        }

        // Build mapping for each width value
        val mapping: Seq[(UInt, UInt)] = lookup.zipWithIndex.map {
            case (rev, idx) =>
                (idx.U, rev)
        }

        // Use the curried form of MuxLookup - notice the second parameter list for mapping
        MuxLookup[UInt](width, 0.U(maxWidth.W))(mapping)
    }
}
