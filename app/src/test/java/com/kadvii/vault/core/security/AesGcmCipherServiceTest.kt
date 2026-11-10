package com.kadvii.vault.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

class AesGcmCipherServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cipherService: AesGcmCipherService
    private lateinit var testKey: SecretKeySpec

    @Before
    fun setUp() {
        cipherService = AesGcmCipherService()
        val random = SecureRandom()
        val keyBytes = ByteArray(32) // AES-256
        random.nextBytes(keyBytes)
        testKey = SecretKeySpec(keyBytes, "AES")
    }

    @Test
    fun roundtrip_encryptAndDecryptStream_preservesPlaintextExactly() {
        val originalText = "TopSecretMediaData_12345!@#$%^&*()_+~`|}{[]:;?><,./"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedOut = ByteArrayOutputStream()
        cipherService.encryptStream(ByteArrayInputStream(originalBytes), encryptedOut, testKey)
        val cipherBytes = encryptedOut.toByteArray()

        assertTrue(cipherBytes.size > originalBytes.size) // Has 12-byte IV + 16-byte GCM tag

        val decryptedOut = ByteArrayOutputStream()
        cipherService.decryptStream(ByteArrayInputStream(cipherBytes), decryptedOut, testKey)

        assertArrayEquals(originalBytes, decryptedOut.toByteArray())
    }

    @Test
    fun ivUniqueness_tenThousandIvs_haveZeroCollisions() {
        val count = 10_000
        val ivSet = HashSet<String>(count)

        for (i in 0 until count) {
            val iv = cipherService.generateIv()
            val hex = iv.joinToString("") { "%02x".format(it) }
            val added = ivSet.add(hex)
            assertTrue("Collision detected in IV generation at index $i", added)
        }

        assertEquals(count, ivSet.size)
    }

    @Test
    fun tampering_singleBitFlipInCiphertext_throwsSecurityException() {
        val originalBytes = "DataToTamperWith".toByteArray(Charsets.UTF_8)
        val encryptedOut = ByteArrayOutputStream()
        cipherService.encryptStream(ByteArrayInputStream(originalBytes), encryptedOut, testKey)
        val cipherBytes = encryptedOut.toByteArray()

        // Flip one bit in the middle of ciphertext
        val tamperedIndex = cipherBytes.size / 2
        cipherBytes[tamperedIndex] = (cipherBytes[tamperedIndex].toInt() xor 0x01).toByte()

        try {
            val decryptedOut = ByteArrayOutputStream()
            cipherService.decryptStream(ByteArrayInputStream(cipherBytes), decryptedOut, testKey)
            fail("Expected SecurityException on tampered ciphertext, but succeeded")
        } catch (e: SecurityException) {
            // Expected
            assertTrue(e.message!!.contains("verification failed", ignoreCase = true) || e.message!!.contains("altered", ignoreCase = true))
        }
    }

    @Test
    fun atomicFileWrite_successfulEncryption_createsTargetFile() {
        val srcFile = tempFolder.newFile("sample.jpg")
        srcFile.writeBytes(ByteArray(2048) { it.toByte() })

        val destFile = File(tempFolder.root, "sample.enc")
        cipherService.encryptFileAtomically(srcFile, destFile, testKey)

        assertTrue(destFile.exists())
        val tmpFile = File(tempFolder.root, "sample.enc.tmp")
        assertFalse(tmpFile.exists()) // Temp file removed after rename
    }

    @Test
    fun emptyFile_encryptionAndDecryption_roundtripSuccessful() {
        val emptyBytes = ByteArray(0)
        val encryptedOut = ByteArrayOutputStream()
        cipherService.encryptStream(ByteArrayInputStream(emptyBytes), encryptedOut, testKey)
        val cipherBytes = encryptedOut.toByteArray()

        assertEquals(12 + 16, cipherBytes.size) // 12-byte IV + 16-byte GCM tag

        val decryptedOut = ByteArrayOutputStream()
        cipherService.decryptStream(ByteArrayInputStream(cipherBytes), decryptedOut, testKey)
        assertArrayEquals(emptyBytes, decryptedOut.toByteArray())
    }

    @Test
    fun largeFileStreaming_decryptFileToFileAtomically_matchesOriginal() {
        // 512 KB payload
        val original = ByteArray(512 * 1024) { (it % 128).toByte() }
        val srcFile = tempFolder.newFile("large_test.dat")
        srcFile.writeBytes(original)

        val encFile = File(tempFolder.root, "large_test.enc")
        cipherService.encryptFileAtomically(srcFile, encFile, testKey)

        val decFile = File(tempFolder.root, "large_test.dec")
        cipherService.decryptFileToFileAtomically(encFile, decFile, testKey)

        assertTrue(decFile.exists())
        assertEquals(srcFile.length(), decFile.length())
        assertArrayEquals(original, decFile.readBytes())
    }

    @Test
    fun corruptedFile_incompleteIv_throwsSecurityException() {
        val incomplete = ByteArray(8) // less than 12 bytes
        try {
            val decryptedOut = ByteArrayOutputStream()
            cipherService.decryptStream(ByteArrayInputStream(incomplete), decryptedOut, testKey)
            fail("Expected SecurityException when IV is incomplete")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("incomplete", ignoreCase = true))
        }
    }
}
