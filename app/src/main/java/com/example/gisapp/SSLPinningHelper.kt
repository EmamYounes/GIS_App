package com.example.gisapp

import android.content.Context
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class SSLPinningHelper {

    // The SHA256 fingerprint you extracted earlier
    private val validFingerprint =
        "7A:8A:7C:EF:7E:24:88:92:54:0B:40:2B:B1:29:A4:94:44:FA:C1:7C:BC:FA:8C:16:90:90:68:4D:DD:7D:0B:6C"

    fun pinCertificate(context: Context) {
        try {
            // Load the certificate from the raw resource (save your certificate in res/raw folder)
            val certificateInputStream: InputStream =
                context.resources.openRawResource(R.raw.unifiedmap_shj_ae) // .cer file in the res/raw folder

            // Convert the InputStream into an X509Certificate
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificate =
                certificateFactory.generateCertificate(certificateInputStream) as X509Certificate

            // Generate the fingerprint (SHA256)
            val messageDigest = MessageDigest.getInstance("SHA-256")
            val publicKey = certificate.publicKey
            val fingerprint = getFingerprint(publicKey, messageDigest)

            if (validFingerprint == fingerprint) {
                // Create a KeyStore containing the trusted certificate
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null, null)
                keyStore.setCertificateEntry("trusted", certificate)

                // Create a TrustManagerFactory and initialize it with the KeyStore
                val trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(keyStore)

                // Set up the SSLContext with the trust manager
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustManagerFactory.trustManagers, null)

                // Set the default SSLSocketFactory for all HttpsURLConnection requests
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            } else {
                throw Exception("Certificate fingerprint mismatch!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle error
        }
    }

    // Generate the SHA256 fingerprint from the public key
    private fun getFingerprint(
        publicKey: java.security.PublicKey,
        messageDigest: MessageDigest
    ): String {
        val keyBytes = publicKey.encoded
        val digestBytes = messageDigest.digest(keyBytes)
        return digestBytes.joinToString(":") { String.format("%02X", it) }
    }
}
