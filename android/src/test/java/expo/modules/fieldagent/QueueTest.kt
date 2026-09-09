package expo.modules.fieldagent

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueTest {

    private lateinit var directory: File
    private lateinit var file: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("field-agent-queue").toFile()
        file = File(directory, "outbox.tsv")
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun queue(maxSize: Int = 10) = Queue(file, maxSize)

    private fun entry(id: String) = Queue.Entry(id, """{"client_id":"$id","lat":36.8,"lng":10.18}""")

    @Test
    fun `adds and reads back in order`() {
        val queue = queue()
        queue.add(entry("a"))
        queue.add(entry("b"))
        assertEquals(2, queue.size())
        assertEquals(listOf("a", "b"), queue.peek(10).map { it.clientId })
    }

    @Test
    fun `peek never returns more than asked`() {
        val queue = queue()
        repeat(5) { queue.add(entry("id-$it")) }
        assertEquals(2, queue.peek(2).size)
        assertEquals(0, queue.peek(0).size)
        assertEquals(5, queue.peek(100).size)
    }

    @Test
    fun `the cap drops the oldest points and reports how many`() {
        val queue = queue(maxSize = 3)
        repeat(3) { queue.add(entry("id-$it")) }
        assertEquals(1, queue.add(entry("id-3")))
        assertEquals(3, queue.size())
        assertEquals(listOf("id-1", "id-2", "id-3"), queue.peek(10).map { it.clientId })
    }

    @Test
    fun `removal is by id, so points queued during a flight survive`() {
        val queue = queue()
        queue.add(entry("a"))
        queue.add(entry("b"))
        val inFlight = queue.peek(2).map { it.clientId }
        // The service keeps recording while the batch is in the air.
        queue.add(entry("c"))
        assertEquals(2, queue.remove(inFlight))
        assertEquals(listOf("c"), queue.peek(10).map { it.clientId })
    }

    @Test
    fun `removing unknown ids changes nothing`() {
        val queue = queue()
        queue.add(entry("a"))
        assertEquals(0, queue.remove(listOf("inconnu")))
        assertEquals(1, queue.size())
    }

    @Test
    fun `survives a restart of the process`() {
        val first = queue()
        first.add(entry("a"))
        first.add(entry("b"))
        first.remove(listOf("a"))

        val reopened = queue()
        assertEquals(listOf("b"), reopened.peek(10).map { it.clientId })
    }

    @Test
    fun `a payload full of separators round-trips`() {
        val queue = queue()
        // org.json escapes control characters, but the queue must not rely on
        // the caller being careful either.
        val payload = """{"note":"ligne\tavec\nseparateurs"}"""
        queue.add(Queue.Entry("z", payload))
        assertEquals(payload, queue().peek(1).single().payload)
    }

    @Test
    fun `clear empties the file too`() {
        val queue = queue()
        queue.add(entry("a"))
        queue.clear()
        assertEquals(0, queue().size())
        assertTrue(file.readText().isEmpty())
    }

    @Test
    fun `a truncated file loses only its last line`() {
        val queue = queue()
        queue.add(entry("a"))
        queue.add(entry("b"))
        file.writeText(file.readText().dropLast(5))

        assertEquals(listOf("a"), queue().peek(10).map { it.clientId })
    }
}
