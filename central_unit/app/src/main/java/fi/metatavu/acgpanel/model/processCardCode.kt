package fi.metatavu.acgpanel.model

private const val CODE_LENGTH = 15

@ExperimentalUnsignedTypes
internal fun processCardCode(
    type: CardReaderType,
    cardCode: String
): List<String> {
    return when (type) {
        CardReaderType.ACCESS_7C ->
            listOf(
                cardCode
                    .drop(1)
                    .take(CODE_LENGTH)
            )
        CardReaderType.ACCESS_7AH -> // 24 data bits + 2 (discarded) parity bits
            listOf(
                cardCode
                    .drop(1).take(24)
                    .toInt(2).toString()
                    .padEnd(CODE_LENGTH, '0')
            )
        CardReaderType.SCHNEIDER_HID -> {
            val binary = (cardCode
                .padEnd(35, '0')
                .toULong(2)
                    xor 0x400000000UL)
            val hex = binary
                .toString(16)
                .toUpperCase()
            listOf(
                hex
                    .padStart(10, '0')
                    .padEnd(CODE_LENGTH, '0'),
                hex
                    .padStart(9, '0')
                    .padEnd(CODE_LENGTH, '0')
            )
        }
        CardReaderType.IR6090B -> {
            val hex = String(
                charArrayOf(
                    cardCode[9],
                    cardCode[10],
                    cardCode[3],
                    cardCode[4],
                    cardCode[5],
                    cardCode[6]
                )
            )
            listOf(hex.toInt(16).toString(10).padEnd(CODE_LENGTH, '0'))
        }
    }
}