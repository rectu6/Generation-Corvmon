package generations.gg.generations.core.generationscore.common.util

import com.cobblemon.mod.common.api.text.blue
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.text
import com.mojang.serialization.Codec
import generations.gg.generations.core.generationscore.common.battle.BattleSideData
import generations.gg.generations.core.generationscore.common.client.BattleConditionsOverlay.isActor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

val TEXT_CODEC = Codec.STRING.xmap(String::text) { it.serialize() }
val TEXT_STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(String::text) { it.serialize() }

fun MutableComponent.serialize(): String {
    val result = StringBuilder()

    // Iterate over the siblings of the component to build the formatted string.
    for (sibling in this.siblings) {
        if (sibling is MutableComponent) {
            result.append(extractFormattedText(sibling))
        } else {
            result.append(sibling.contents)
        }
    }

    return result.toString()
}

fun MutableComponent.colorSort(sideData: BattleSideData): MutableComponent {
    if (isActor == true) {
        if (Minecraft.getInstance().player?.name?.string == sideData.playerName) {
            this.withRgb(0x227ace).bold()
        } else this.withRgb(0xbe2f2f).bold()
    } else {
        if (sideData.side == "1") this.withRgb(0x227ace).bold() else this.withRgb(0xbe2f2f).bold()
    }
    return this
}

fun MutableComponent.withRgb(rgb: Int): MutableComponent {
    return this.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
}

private fun extractFormattedText(component: MutableComponent): String {
    val formattedText = StringBuilder()
    val text = component.contents
    val style = component.style

    // Append formatting codes based on the style.
    if (style.isBold) {
        formattedText.append("&l")
    }
    if (style.isItalic) {
        formattedText.append("&o")
    }
    if (style.isUnderlined) {
        formattedText.append("&n")
    }
    if (style.isStrikethrough) {
        formattedText.append("&m")
    }
    if (style.isObfuscated) {
        formattedText.append("&k")
    }

    // Append color code if present.
    style.color?.let {
        val chatFormatting = it.let { ChatFormatting.getByName(it.serialize()) }

        if(chatFormatting != null) formattedText.append(getColorCode(chatFormatting))
    }

    formattedText.append(text)

    // Append a reset code after the text to avoid style bleeding.
    formattedText.append("&r")

    // Recursively process any child components.
    for (child in component.siblings) {
        if (child is MutableComponent) {
            formattedText.append(extractFormattedText(child))
        } else {
            formattedText.append((child.contents as LiteralContents).text)
        }
    }

    return formattedText.toString()
}

private fun getColorCode(color: ChatFormatting): String {
    return when (color) {
        ChatFormatting.BLACK -> "&0"
        ChatFormatting.DARK_BLUE -> "&1"
        ChatFormatting.DARK_GREEN -> "&2"
        ChatFormatting.DARK_AQUA -> "&3"
        ChatFormatting.DARK_RED -> "&4"
        ChatFormatting.DARK_PURPLE -> "&5"
        ChatFormatting.GOLD -> "&6"
        ChatFormatting.GRAY -> "&7"
        ChatFormatting.DARK_GRAY -> "&8"
        ChatFormatting.BLUE -> "&9"
        ChatFormatting.GREEN -> "&a"
        ChatFormatting.AQUA -> "&b"
        ChatFormatting.RED -> "&c"
        ChatFormatting.LIGHT_PURPLE -> "&d"
        ChatFormatting.YELLOW -> "&e"
        ChatFormatting.WHITE -> "&f"
        else -> "" // Return an empty string if the color is not recognized.
    }
}
