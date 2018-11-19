package fi.metatavu.acgpanel.device

import org.junit.Assert.*
import org.junit.Test
import java.util.*

private fun checksum(input: String): Int {
    var sum = 0
    for (byte in input.toByteArray(Charsets.US_ASCII)) {
        sum = sum xor byte.toInt()
    }
    return sum
}

class MessageReaderTest {
    val queue = ArrayDeque<Byte>()
    val reader = object : MessageReader() {
        override fun read(): Byte {
            return queue.poll()
        }
        override fun available(): Boolean {
            return queue.isNotEmpty()
        }
    }

    @Test
    fun testAcknowledgement() {
        try {
            val nosum = "\u00020;0;1;0;"
            val checksum = checksum(nosum)
            val input = "${nosum}${checksum};\n".toByteArray(Charsets.US_ASCII)
            queue.addAll(input.asIterable())
            val expected = Acknowledgement(0, 0)
            val actual = reader.readMessage()
            assertEquals(expected, actual)
        } finally {
            queue.clear()
        }
    }

}