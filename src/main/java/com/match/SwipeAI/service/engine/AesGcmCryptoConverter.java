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
        return ChatCryptoService.getInstance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return ChatCryptoService.getInstance().decrypt(dbData);
    }
}
