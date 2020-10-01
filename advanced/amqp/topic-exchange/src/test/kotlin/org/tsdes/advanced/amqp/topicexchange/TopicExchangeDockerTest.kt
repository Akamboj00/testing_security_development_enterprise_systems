package org.tsdes.advanced.amqp.topicexchange


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Created by arcuri82 on 09-Aug-17.
 */
@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest
@ContextConfiguration(initializers = [(TopicExchangeDockerTest.Companion.Initializer::class)])
class TopicExchangeDockerTest {

    companion object {

        class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

        @Container
        @JvmField
        val rabbitMQ = KGenericContainer("rabbitmq:3").withExposedPorts(5672)

        class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
            override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
                TestPropertyValues
                        .of("spring.rabbitmq.host=" + rabbitMQ.containerIpAddress,
                                "spring.rabbitmq.port=" + rabbitMQ.getMappedPort(5672))
                        .applyTo(configurableApplicationContext.environment)
            }
        }
    }

    @Autowired
    private lateinit var sender: Sender

    @Autowired
    private lateinit var messages: ReceivedMessages


    @Test
    fun testTopicExchange() {

        messages.reset(5)

        val textForNewsInNorwayBySmith = "fcdnsikfbjehfbjebjg"

        /*
            Recall the patterns in Application:
            X: smith.#
            Y: *.norway.*
         */

        sender.publish("smith", "usa", "sport", "a") // X
        sender.publish("smith", "norway", "politics", textForNewsInNorwayBySmith) // X and Y
        sender.publish("black", "norway", "politics", "c")  // Y
        sender.publish("white", "norway", "science", "d")   // Y
        sender.publish("white", "usa", "science", "d")      // none
        sender.publish("white", "germany", "sport", "e")    // none
        sender.publish("white", "germany", "politics", "e") // none


        val completed = messages.await(2)
        assertTrue(completed)

        assertEquals(2, messages.data.filter { it.contains("X") }.count())
        assertEquals(3, messages.data.filter { it.contains("Y") }.count())
        assertEquals(5, messages.data.size) // 2nd msg is in 2 queues, so 1 + 2 + 1 + 1  = 5
        assertEquals(2, messages.data.filter { it.contains(textForNewsInNorwayBySmith) }.count())
    }
}