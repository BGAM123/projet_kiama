package com.docuai.ai.security;

import com.docuai.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffre/déchiffre les clés API des fournisseurs IA stockées dans
 * {@code ai_model_config.cle_api_chiffree} (saisies depuis l'admin UI,
 * {@code AiConfigService.update}) — AES-256-GCM avec une clé maîtresse unique
 * ({@code docuai.ai.credentials-encryption-key}, jamais en base), pour que la
 * compromission de la base seule ne suffise pas à récupérer les clés en clair.
 * <p>
 * Format stocké : {@code Base64(IV[12] || ciphertext || tag[16])} — IV
 * aléatoire à chaque chiffrement (GCM l'exige pour rester sûr), préfixé au
 * résultat plutôt que stocké à part pour garder une seule colonne texte.
 * <p>
 * Si aucune clé maîtresse n'est configurée, le service reste inerte
 * ({@link #isConfigured()} à {@code false}) : les fournisseurs continuent de
 * fonctionner via {@code docuai.ai.<fournisseur>.api-key} (variables
 * d'environnement, repli historique) — seule la saisie d'une clé réelle
 * depuis l'UI est refusée tant que la clé maîtresse n'est pas définie.
 */
@Component
public class ApiKeyCipherService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCipherService(AiProperties properties) {
        this.secretKey = buildKey(properties.getCredentialsEncryptionKey());
    }

    public boolean isConfigured() {
        return secretKey != null;
    }

    /** @throws IllegalStateException si aucune clé maîtresse n'est configurée. */
    public String encrypt(String plainText) {
        requireConfigured();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du chiffrement de la clé API.", e);
        }
    }

    /** @throws IllegalStateException si aucune clé maîtresse n'est configurée, ou si le déchiffrement échoue (clé maîtresse changée entre-temps, donnée corrompue). */
    public String decrypt(String encoded) {
        requireConfigured();
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(payload, GCM_IV_LENGTH, payload.length - GCM_IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Impossible de déchiffrer la clé API stockée (clé maîtresse invalide ou changée).", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du déchiffrement de la clé API.", e);
        }
    }

    private void requireConfigured() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "docuai.ai.credentials-encryption-key absente — impossible de chiffrer/déchiffrer une clé API stockée en base.");
        }
    }

    private static SecretKeySpec buildKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "docuai.ai.credentials-encryption-key doit être une clé AES-256 encodée en Base64 (32 octets décodés) — "
                            + "générez-en une avec `openssl rand -base64 32`.");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
