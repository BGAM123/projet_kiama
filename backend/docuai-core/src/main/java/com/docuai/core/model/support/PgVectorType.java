package com.docuai.core.model.support;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

/**
 * Mapping Hibernate 6 manuel de {@code document_chunk.embedding} (colonne
 * pgvector {@code vector(1536)}, RAG du Bloc 5) vers un {@code float[]} Java.
 * <p>
 * Hibernate ORM 6 n'a pas de {@code SqlTypes} natif pour pgvector
 * (contrairement à JSONB, cf. {@link com.docuai.core.model.DocumentStructure}).
 * Plutôt que de dépendre de l'enregistrement du type {@code com.pgvector.PGvector}
 * sur chaque connexion JDBC (nécessiterait un hook sur le pool Hikari, non
 * vérifiable sans build/DB locale), ce {@link UserType} lit/écrit directement
 * la représentation texte pgvector ("[0.1,0.2,...]") via {@link PGobject}
 * (driver {@code org.postgresql}, déjà sur le classpath runtime via
 * docuai-api) — fonctionne quel que soit l'état d'enregistrement du type côté
 * connexion.
 */
public class PgVectorType implements UserType<float[]> {

    private static final String PG_TYPE_NAME = "vector";

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Object raw = rs.getObject(position);
        if (raw == null) {
            return null;
        }
        String text = (raw instanceof PGobject pgObject) ? pgObject.getValue() : raw.toString();
        return parse(text);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }
        PGobject pgObject = new PGobject();
        pgObject.setType(PG_TYPE_NAME);
        pgObject.setValue(format(value));
        st.setObject(index, pgObject);
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }

    @Override
    public float[] replace(float[] detached, float[] managed, Object owner) {
        return deepCopy(detached);
    }

    private static float[] parse(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].strip());
        }
        return result;
    }

    private static String format(float[] value) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(value[i]));
        }
        return sb.append(']').toString();
    }
}
