package com.docuai.security.jwt;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Décode une paire de clés RSA depuis leur représentation PEM (telle que
 * stockée dans DOCUAI_JWT_PRIVATE_KEY / DOCUAI_JWT_PUBLIC_KEY, en une seule
 * ligne avec des "\n" littéraux à la place des retours à la ligne — cf.
 * .env.example).
 *
 * Format attendu pour la clé privée : PKCS#8 ("-----BEGIN PRIVATE KEY-----"),
 * PAS le PKCS#1 ("-----BEGIN RSA PRIVATE KEY-----") produit par défaut par
 * `openssl genrsa`. Générer la paire avec :
 *   openssl genpkey -algorithm RSA -out jwt_private.pem -pkeyopt rsa_keygen_bits:2048
 *   openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem
 * `genpkey` produit directement du PKCS#8, lisible nativement par
 * java.security.KeyFactory sans dépendance supplémentaire (pas besoin de
 * Bouncy Castle).
 */
public final class PemKeyReader {

    private PemKeyReader() {
    }

    public static RSAPrivateKey readPrivateKey(String pem) {
        byte[] der = decode(pem, "PRIVATE KEY");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Clé privée RSA illisible. Attendu : PEM PKCS#8 ('-----BEGIN PRIVATE KEY-----'). "
                            + "Générez-la avec : openssl genpkey -algorithm RSA -out jwt_private.pem -pkeyopt rsa_keygen_bits:2048",
                    e);
        }
    }

    public static RSAPublicKey readPublicKey(String pem) {
        byte[] der = decode(pem, "PUBLIC KEY");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clé publique RSA illisible (attendu : PEM X.509 '-----BEGIN PUBLIC KEY-----').", e);
        }
    }

    private static byte[] decode(String pem, String label) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(
                    "Clé RSA manquante (" + label + "). Vérifiez DOCUAI_JWT_PRIVATE_KEY / DOCUAI_JWT_PUBLIC_KEY dans .env "
                            + "(voir README backend, section génération des clés RS256).");
        }
        String base64 = pem
                .replace("\\n", "\n")
                .replaceAll("-----BEGIN (RSA )?" + label + "-----", "")
                .replaceAll("-----END (RSA )?" + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
