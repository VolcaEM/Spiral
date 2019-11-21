package info.spiralframework.osl.drills.lin

import info.spiralframework.osl.OpenSpiralLanguageParser
import info.spiralframework.osl.drills.DrillHead
import info.spiralframework.formats.game.hpa.DR1
import info.spiralframework.formats.game.hpa.DR2
import info.spiralframework.formats.scripting.lin.AnimationEntry
import info.spiralframework.formats.common.scripting.lin.LinEntry
import org.parboiled.Action
import org.parboiled.Rule
import org.parboiled.support.Var
import kotlin.reflect.KClass

object LinAnimationDrill : DrillHead<LinEntry> {
    val cmd: String = "LIN-ANIMATION"

    val NUMERAL_REGEX = "\\d+".toRegex()

    override val klass: KClass<LinEntry> = LinEntry::class

    override fun OpenSpiralLanguageParser.syntax(): Rule {
        val unk1 = Var<MutableList<Int>>(ArrayList(5))

        return Sequence(
                clearTmpStack(cmd),

                Sequence(
                        FirstOf("Animation", "Animate", "Flash"),
                        pushDrillHead(cmd, this@LinAnimationDrill),
                        Separator(),
                        AnimationID(),
                        pushTmpFromStack(cmd),
                        pushTmpFromStack(cmd),
                        Action<Any> { unk1.get().clear(); true },
                        Optional(
                                CommaSeparator(),
                                NTimes(
                                        5,
                                        Sequence(
                                                RuleWithVariables(OneOrMore(Digit())),
                                                Action<Any> { unk1.get().add(pop().toString().toIntOrNull() ?: return@Action false) }
                                        ),
                                        CommaSeparator()
                                )
                        ),
                        CommaSeparator(),
                        Action<Any> {
                            val unkList = unk1.get()

                            if (unkList.size == 5) {
                                unkList.forEach { unk -> pushTmp(cmd, unk) }
                            } else {
                                for (i in 0 until 5) {
                                    pushTmp(cmd, 0)
                                }
                            }

                            return@Action true
                        },
                        FirstOf(
                                Sequence(
                                        "Hide",
                                        pushTmpAction(cmd, 255)
                                ),
                                Sequence(
                                        RuleWithVariables(OneOrMore(Digit())),
                                        pushTmpFromStack(cmd)
                                )
                        )
                ),

                pushStackWithHead(cmd)
        )
    }

    override fun operate(parser: OpenSpiralLanguageParser, rawParams: Array<Any>): LinEntry {
        val majorID = rawParams[0].toString().toInt()
        val minorID = rawParams[1].toString().toInt()

        val unk1 = rawParams[2].toString().toInt()
        val unk2 = rawParams[3].toString().toInt()
        val unk3 = rawParams[4].toString().toInt()
        val unk4 = rawParams[5].toString().toInt()
        val unk5 = rawParams[6].toString().toInt()

        val component = rawParams[7].toString().toInt()

        return when (parser.hopesPeakGame) {
            DR1 -> AnimationEntry((majorID shl 8) or minorID, unk1, unk2, unk3, unk4, unk5, component)
            DR2 -> AnimationEntry((majorID shl 8) or minorID, unk1, unk2, unk3, unk4, unk5, component)
            else -> TODO("Animation is not documented for ${parser.hopesPeakGame}")
        }
    }
}
