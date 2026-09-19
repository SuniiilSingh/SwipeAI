package com.match.SwipeAI.service.engine;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that automatically encrypts message content before writing to the
 * database table, and decrypts it when mapped back to the entity.
 */
@Converter
public class AesGcmCryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        // If client already encrypted the message via E2EE (starts with E2EE:), store verbatim
        if (attribute.startsWith("E2EE:")) {
            return attribute;
        }
        return ChatCryptoService.getInstance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // If message is E2EE encrypted, return verbatim to client for client-side decryption
        if (dbData.startsWith("E2EE:")) {
            return dbData;
        }
        return ChatCryptoService.getInstance().decrypt(dbData);
    }
}
