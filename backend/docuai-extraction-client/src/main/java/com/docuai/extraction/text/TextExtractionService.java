package com.docuai.extraction.text;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extraction de texte brut + détection du type MIME réel (magic bytes, pas
 * l'en-tête Content-Type envoyé par le navigateur, non fiable) via Apache
 * Tika. {@code AutoDetectParser} couvre .docx (POI), .pdf (PDFBox), .md/.txt
 * (parseur texte) — tous bundlés dans tika-parsers-standard-package.
 */
@Service
public class TextExtractionService {

    // Cap défensif : évite de charger en mémoire/JSON un texte de plusieurs
    // dizaines de Mo pour un simple aperçu d'extraction (le rawText n'est de
    // toute façon pas encore affiché côté frontend à ce stade — cf.
    // documents-types/import/page.tsx).
    private static final int MAX_CHARS = 500_000;

    public ExtractedText extract(byte[] content) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
        Metadata metadata = new Metadata();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            parser.parse(stream, handler, metadata, new ParseContext());
            String mimeType = metadata.get(Metadata.CONTENT_TYPE);
            return new ExtractedText(mimeType != null ? mimeType : "application/octet-stream", handler.toString());
        } catch (IOException | SAXException | TikaException e) {
            throw new TextExtractionException("Impossible d'extraire le contenu de ce fichier — il est peut-être corrompu ou dans un format non supporté.", e);
        }
    }
}
