import graphics.scenery.KeyManager
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class KeyManagerTests {

    @Test
    fun testKeyCreation() {
        val tmpDirectory = Files.createTempDirectory("KeyManagerTest")
        val km = KeyManager()

        km.getOwnKeyPair(tmpDirectory)

        assert(tmpDirectory.resolve("privateKey.pem").exists())
        assert(tmpDirectory.resolve("publicKey.pem").exists())

        tmpDirectory.toFile().deleteRecursively()
    }

    @Test
    fun testKeyIngestion() {
        val tmpDirectory = Files.createTempDirectory("KeyManagerTest")
        val km = KeyManager()

        // this will generate a new key pair
        km.getOwnKeyPair(tmpDirectory)
        // this will re-read the generated pair
        km.getOwnKeyPair(tmpDirectory)

        assert(tmpDirectory.resolve("privateKey.pem").exists())
        assert(tmpDirectory.resolve("publicKey.pem").exists())

        tmpDirectory.toFile().deleteRecursively()
    }

    @Test
    fun testSigning() {
        val tmpDirectory = Files.createTempDirectory("KeyManagerTest")
        val km = KeyManager()

        val kp = km.getOwnKeyPair(tmpDirectory)

        val data = ByteArray(512)
        SecureRandom.getInstanceStrong().nextBytes(data)

        val signature = KeyManager.sign(kp.private, data)

        assertTrue { KeyManager.verifySignature(kp.public, data, signature) }

        tmpDirectory.toFile().deleteRecursively()
    }
}