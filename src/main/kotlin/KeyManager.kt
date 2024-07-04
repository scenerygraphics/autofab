package graphics.scenery

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.*


@OptIn(ExperimentalEncodingApi::class)
class KeyManager {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass.simpleName)
        const val PRIVATE_KEY_BEGIN = "-----BEGIN ECDSA PRIVATE KEY-----\n"
        const val PRIVATE_KEY_END = "\n-----END ECDSA PRIVATE KEY-----\n"

        const val PUBLIC_KEY_BEGIN = "-----BEGIN ECDSA PUBLIC KEY-----\n"
        const val PUBLIC_KEY_END = "\n-----END ECDSA PUBLIC KEY-----\n"

        fun getPublicKey(data: String): PublicKey {
            val actualData = data.substringAfter(PUBLIC_KEY_BEGIN).substringBeforeLast(PUBLIC_KEY_END)
            val publicKeyData = Base64.decode(actualData)
            val publicSpec = X509EncodedKeySpec(publicKeyData)
            return KeyFactory.getInstance("EC").generatePublic(publicSpec)
        }

        fun getPublicKey(file: Path): PublicKey {
            return getPublicKey(file.readText())
        }

        fun getPrivateKey(file: Path): PrivateKey {
            val privateKeyData = Base64.decode(file.readText().substringAfter(PRIVATE_KEY_BEGIN).substringBeforeLast(PRIVATE_KEY_END))
            val privateSpec = PKCS8EncodedKeySpec(privateKeyData)
            return KeyFactory.getInstance("EC").generatePrivate(privateSpec)
        }

        fun PrivateKey.storeAsBase64(file: Path) {
            val privateKeyOut = file.outputStream()
            privateKeyOut.bufferedWriter().use { writer ->
                writer.write(PRIVATE_KEY_BEGIN)
                writer.write(Base64.encode(this.encoded))
                writer.write(PRIVATE_KEY_END)
            }
            privateKeyOut.close()
        }

        fun PublicKey.storeAsBase64(file: Path) {
            val publicKeyOut = file.outputStream()
            publicKeyOut.bufferedWriter().use { writer ->
                writer.write(PUBLIC_KEY_BEGIN)
                writer.write(Base64.encode(this.encoded))
                writer.write(PUBLIC_KEY_END)
            }
        }

        fun generateKeyPair(privateKeyFile: Path, publicKeyFile: Path): KeyPair {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            val keyPair = kpg.generateKeyPair()

            keyPair.private.storeAsBase64(privateKeyFile)
            keyPair.public.storeAsBase64(publicKeyFile)

            return keyPair
        }

        fun sign(privateKey: PrivateKey, data: ByteArray): String {
            val ecdsa = Signature.getInstance("SHA256withECDSA")
            ecdsa.initSign(privateKey)
            ecdsa.update(data)

            val signature = ecdsa.sign()
            return Base64.encode(signature)
        }

        fun verifySignature(publicKey: PublicKey, data: ByteArray, signature: String): Boolean {
            val sign = Signature.getInstance("SHA256withECDSA")
            sign.initVerify(publicKey)
            sign.update(data)
            return sign.verify(Base64.decode(signature))
        }
    }

    fun getOwnKeyPair(configDirectory: Path): KeyPair {
        val privateKeyFile = configDirectory.resolve("privateKey.pem")
        val publicKeyFile = configDirectory.resolve("publicKey.pem")

        val keyPair: KeyPair

        if(privateKeyFile.exists() && publicKeyFile.exists()) {
            logger.info("Reading key pair from $privateKeyFile and $publicKeyFile")
            keyPair = KeyPair(getPublicKey(publicKeyFile), getPrivateKey(privateKeyFile))
        } else {
            logger.info("No pre-existing key pair found, generating new key pair at $privateKeyFile and $publicKeyFile")
            keyPair = generateKeyPair(privateKeyFile, publicKeyFile)
            logger.info("New key pair created")
        }

        return keyPair
    }
}