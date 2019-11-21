package info.spiralframework.formats.common.games

import info.spiralframework.formats.common.OpcodeMap
import info.spiralframework.formats.common.scripting.lin.LinEntry

/**
 * The Danganronpa Games all share similar properties, which can be accessed here
 * This is only used as a form of abstraction.
 */
interface DrGame {
    val names: Array<String>
    val identifier: String
        get() = names.firstOrNull() ?: "none"

    val steamID: String?

    /** Traits */

    interface ScriptOpcodeFactory<S> {
        fun entryFor(opcode: Int, rawArguments: IntArray): S
    }

    /** A game that supports lin scripts */
    interface LinScriptable {
        val linOpcodeMap: OpcodeMap<LinEntry>

        /** Name -> Internal ID */
        val linCharacterIdentifiers: Map<String, Int>

        /** Internal ID -> Name */
        val linCharacterIDs: Map<Int, String>

        val linItemNames: Array<String>

        /** A map of the colour to the internal clt number */
        val linColourCodes: Map<String, Int>
    }

    interface LinNonstopScriptable {
        val linNonstopOpcodeNames: OpcodeMap<String>
        val linNonstopSectionSize: Int
    }

    /** TODO: Figure out how to do this for V3 */
    interface LinTrialSupported {
        val trialCameraNames: Array<String>
        val evidenceNames: Array<String>
    }

    /** A game that supports word scripts */
    interface WordScriptable {
        val wrdOpcodeMap: OpcodeMap<String>

        /** Name -> Internal ID */
        val wrdCharacterIdentifiers: Map<String, String>

        /** Internal ID -> Name */
        val wrdCharacterIDs: Map<String, String>

        val wrdItemNames: Array<String>

        /** A map of the colour to the internal clt name */
        val wrdColourCodes: Map<String, String>
    }

    /** A game that has subfiles stored within pak archives. */
    interface PakMapped {
        val pakNames: Map<String, Array<String>>
    }
}