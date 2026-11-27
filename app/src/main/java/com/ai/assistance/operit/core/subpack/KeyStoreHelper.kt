package com.ai.assistance.operit.core.subpack

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.util.Base64
import java.util.Date

/** The signing identity used when exporting an APK: keystore file, passwords, alias and key password. */
data class SubpackSigningIdentity(
        val keyStoreFile: File,
        val keyStoreType: String,
        val keyStorePassword: String,
        val keyAlias: String,
        val keyPassword: String
)

/**
 * Keystore helper: one place for keystore loading, validation and JCE provider management.
 *
 * The signing identity is generated at random once per device and then persisted. This repository
 * used to ship fixed `jks.jks` / `pkcs12.keystore` files in assets with the password and alias
 * hardcoded to `android` / `androidkey`: the private key was published together with the APK, every
 * user's exported app shared one signing identity, and anyone could build an APK that Android
 * accepts as an update to the same app.
 */
class KeyStoreHelper {
    companion object {
        private const val TAG = "KeyStoreHelper"

        private const val PREFS_NAME = "subpack_signing"
        private const val PREF_KEY_PASSWORD = "keystore_password"

        private const val KEYSTORE_FILE_NAME = "subpack_signing.p12"
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val KEY_ALIAS = "operit-subpack"
        private const val KEY_ALGORITHM = "RSA"
        private const val KEY_SIZE_BITS = 2048
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val CERT_SUBJECT_DN = "CN=Operit Subpack, OU=Operit, O=Operit"
        private const val CERT_SERIAL_BITS = 64
        private const val CERT_VALIDITY_MILLIS = 30L * 365 * 24 * 60 * 60 * 1000
        private const val CERT_CLOCK_SKEW_MILLIS = 24L * 60 * 60 * 1000
        private const val PASSWORD_BYTES = 24

        /** File names older versions used when copying the bundled keystores into filesDir. */
        private val LEGACY_KEYSTORE_FILE_NAMES = listOf("pkcs12.keystore", "jks.jks")

        // 保存注册的BouncyCastle提供者引用
        private var bcProvider: Provider? = null

        /**
         * 注册BouncyCastle提供者
         * @return 是否成功注册
         */
        @JvmStatic
        fun registerBouncyCastleProvider(): Boolean {
            try {
                // 先移除再添加，避免重复添加导致的问题
                Security.removeProvider("BC")

                // 创建新的BouncyCastle提供者实例
                val provider = BouncyCastleProvider()

                // 添加到安全提供者列表首位确保优先级
                val position = Security.insertProviderAt(provider, 1)

                // 保存引用
                bcProvider = provider

                return position > 0
            } catch (e: Exception) {
                AppLogger.e(TAG, "注册BouncyCastle提供程序失败: ${e.message}", e)
                return false
            }
        }

        /**
         * 获取密钥库实例
         * @param keyStoreType 密钥库类型 (PKCS12, JKS等)
         * @return 密钥库实例或null
         */
        @JvmStatic
        fun getKeyStoreInstance(keyStoreType: String): KeyStore? {
            try {
                // 对于PKCS12类型，确保BouncyCastle提供者已注册并在第一位
                if (keyStoreType == "PKCS12") {
                    registerBouncyCastleProvider()

                    // 尝试直接通过类型获取，因为BC现在是第一位提供者
                    return KeyStore.getInstance(keyStoreType)
                } else {
                    // 其他类型直接获取
                    return KeyStore.getInstance(keyStoreType)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取${keyStoreType}密钥库实例失败: ${e.message}", e)
                return null
            }
        }

        /**
         * 验证密钥库文件是否有效
         * @param file 密钥库文件
         * @param type 密钥库类型
         * @param password 密钥库密码
         * @return 是否有效
         */
        @JvmStatic
        fun validateKeystore(file: File, type: String, password: String): Boolean {
            try {
                // 对于PKCS12类型，确保提供者已正确注册
                if (type == "PKCS12") {
                    registerBouncyCastleProvider()
                }

                // 获取密钥库实例
                val keyStore = getKeyStoreInstance(type) ?: return false

                // 加载密钥库
                FileInputStream(file).use { input ->
                    keyStore.load(input, password.toCharArray())

                    // 检查是否包含至少一个别名
                    return keyStore.aliases().hasMoreElements()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "$type 密钥库验证失败: ${e.message}")
                return false
            }
        }

        /**
         * Return this device's export signing identity; the first call generates an RSA key pair and
         * a self-signed certificate and writes them into the app-private directory.
         *
         * The password is random and lives in the app's private SharedPreferences, i.e. inside the
         * same sandbox boundary as the keystore file. The identity is reused on this device, so apps
         * exported repeatedly from the same device can still be installed over one another.
         *
         * @param context application context
         * @return the signing identity
         */
        @JvmStatic
        fun getOrCreateSigningIdentity(context: Context): SubpackSigningIdentity {
            registerBouncyCastleProvider()
            removeLegacySharedKeyStores(context)

            val keyStoreFile = File(context.filesDir, KEYSTORE_FILE_NAME)
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedPassword = preferences.getString(PREF_KEY_PASSWORD, null)

            if (storedPassword != null &&
                    keyStoreFile.isFile &&
                    validateKeystore(keyStoreFile, KEYSTORE_TYPE, storedPassword)
            ) {
                return signingIdentity(keyStoreFile, storedPassword)
            }

            val password = generatePassword()
            createKeyStore(keyStoreFile, password)
            preferences.edit().putString(PREF_KEY_PASSWORD, password).apply()
            AppLogger.i(TAG, "Generated this device's export signing identity: ${keyStoreFile.absolutePath}")
            return signingIdentity(keyStoreFile, password)
        }

        /**
         * Delete the shared keystores that older versions copied from assets into filesDir. Their
         * private keys were distributed publicly inside the APK, so keeping them on the device serves
         * no purpose.
         */
        private fun removeLegacySharedKeyStores(context: Context) {
            LEGACY_KEYSTORE_FILE_NAMES.forEach { name ->
                val legacy = File(context.filesDir, name)
                if (legacy.exists() && !legacy.delete()) {
                    AppLogger.w(TAG, "Could not delete a legacy shared keystore: ${legacy.absolutePath}")
                }
            }
        }

        private fun signingIdentity(keyStoreFile: File, password: String): SubpackSigningIdentity {
            return SubpackSigningIdentity(
                    keyStoreFile = keyStoreFile,
                    keyStoreType = KEYSTORE_TYPE,
                    keyStorePassword = password,
                    keyAlias = KEY_ALIAS,
                    keyPassword = password
            )
        }

        private fun generatePassword(): String {
            val bytes = ByteArray(PASSWORD_BYTES)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun createKeyStore(keyStoreFile: File, password: String) {
            val keyPair =
                    KeyPairGenerator.getInstance(KEY_ALGORITHM)
                            .apply { initialize(KEY_SIZE_BITS, SecureRandom()) }
                            .generateKeyPair()

            val subject = X500Name(CERT_SUBJECT_DN)
            val notBefore = Date(System.currentTimeMillis() - CERT_CLOCK_SKEW_MILLIS)
            val notAfter = Date(notBefore.time + CERT_VALIDITY_MILLIS)
            val serialNumber = BigInteger(CERT_SERIAL_BITS, SecureRandom()).setBit(0)

            val certificate =
                    JcaX509CertificateConverter()
                            .getCertificate(
                                    JcaX509v3CertificateBuilder(
                                                    subject,
                                                    serialNumber,
                                                    notBefore,
                                                    notAfter,
                                                    subject,
                                                    keyPair.public
                                            )
                                            .build(
                                                    JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                                                            .build(keyPair.private)
                                            )
                            )

            val passwordChars = password.toCharArray()
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            keyStore.load(null, null)
            keyStore.setKeyEntry(KEY_ALIAS, keyPair.private, passwordChars, arrayOf(certificate))
            keyStoreFile.outputStream().use { output -> keyStore.store(output, passwordChars) }
        }
    }
}
